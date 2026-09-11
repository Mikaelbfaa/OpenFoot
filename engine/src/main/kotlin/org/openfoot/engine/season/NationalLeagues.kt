package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.Standing
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

/**
 * One country's division for this season: the clubs the pyramid seated
 * there, read off their standings, and the configured tier from the dataset
 * that governs its format, when one matches. A null config means the
 * division is built from the embedded default of 1.9 rather than a cfg
 * entry.
 */
@SpecRef("1.9")
data class LeagueDivision(val country: Int, val division: Int, val config: LeagueConfigEntry?, val clubs: List<String>) {
    /**
     * The number relegated: the configured count, or the embedded default
     * of 1.9 when no configuration matched, four on the twenty club step and
     * two on every other step.
     */
    @SpecRef("1.9")
    val relegated: Int get() = config?.relegated ?: if (clubs.size == TWENTY_CLUB_STEP) DEFAULT_RELEGATED_TWENTY else DEFAULT_RELEGATED

    /** The configured turn count, or the size based default of section 1.3. */
    @SpecRef("1.3")
    val turns: Int get() = config?.turns ?: defaultTurns(clubs.size)
}

/**
 * The divisions of one country this season, read from the clubs' standings
 * rather than from the dataset's club list, because a season moves clubs
 * between divisions by changing a ClubState's standing, and the world's own
 * GeneratedClub.standing never follows: leagueDivisions must be handed the
 * season's own club states for its answer to reflect the current season
 * rather than the world the season started from.
 *
 * A club without a division, Standing.WithoutDivision or Standing.ByReputation,
 * takes no part in any division; only Standing.InDivision clubs are grouped,
 * by division number ascending, and each division keeps the dataset's own
 * club order within it.
 */
@SpecRef("1.9")
fun leagueDivisions(country: Int, clubs: List<ClubState>, dataset: WorldDataset): List<LeagueDivision> =
    clubs
        .filter { it.country == country }
        .mapNotNull { state -> (state.standing as? Standing.InDivision)?.let { it.division to state.key } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (division, refs) ->
            LeagueDivision(country, division, dataset.leagues.firstOrNull { it.country == country && it.division == division }, refs)
        }

/**
 * The competition a division plays this season, per 1.11.
 *
 * The clubs are shuffled once, section 1.3's draw, before any round robin is
 * built from them; a division without groups, or without a matching
 * configuration at all, plays that shuffled order as one flat league phase.
 *
 * A configured division with groups deals the shuffled order into
 * config.groups groups by position modulo the group count and plays them
 * with RoundRobinPhase.grouped, passing config.gamesInsideGroup through.
 * Section 1.11's own prose reads as if the cross group games are played
 * whenever jogosDentroGrupo is on, on top of each group's own round robin,
 * rather than instead of it; this plan keeps to the flat reading the
 * RoundRobinPhase.grouped interface already gives (with games inside the
 * group, only the groups' own round robins are played, never a cross group
 * game, matching section 1.3's own single-league engine), and records the
 * apparent conflict between that reading and 1.11's wording as an open
 * question for the spec sweep rather than resolving it silently here.
 *
 * When config.knockoutQualifiers is a plain count from one to sixty four,
 * a knockout phase follows the groups: the qualifiers function takes that
 * many from each group's own table, or from the overall table across every
 * group when config.qualifyByOverallTable is on, and seeds the knockout
 * field in group order then table place, so the strongest of the whole
 * picture meets the weakest. Section 1.11 does not publish a seeding table
 * for this particular final phase the way it does for the state
 * championship presets, so this seeding order is INFERIDO, a declared bet
 * rather than a read fact, kept only because it is the seeding
 * nextRoundTies and the knockout phase already give every other final
 * phase in this codebase. The final phase's legs are two legged throughout:
 * the distributed cfg carries no legs array for this phase the way the
 * state championship one does, section 1.11 says only that it follows the
 * estadual convention, and two legs is this reading's own bet at that
 * convention, likewise INFERIDO.
 *
 * config.knockoutQualifiers equal to LeagueConfigEntry.SERIE_C_FORMAT
 * selects the hand written Brazilian Serie C format of 1.11 (a flat first
 * phase, then two groups of four playing a second phase, then a two legged
 * final between the group winners) rather than an ordinary qualifier count;
 * this plan does not build that dedicated format and treats the sentinel as
 * a flat league with no final phase instead, a deferral this docstring
 * records rather than hides. The sixty eight club preliminary knockout that
 * 1.11 describes ahead of the Brazilian fourth division's groups, when the
 * candidate queue overflows the configured field by four, is likewise not
 * built here and is deferred the same way. A configured division whose club
 * count does not divide into config.groups equal, even sized groups, or
 * whose final phase field would not be a power of two, is refused rather
 * than built: that shape is outside what this version builds.
 */
@SpecRef("1.11")
fun leagueCompetition(division: LeagueDivision, rng: Rng): Competition {
    val order = shuffledOrder(division.clubs, rng)
    val config = division.config
    val groups = config?.groups ?: 0
    val phases = ArrayList<Phase>()
    val qualifiers: (RoundRobinPhase, List<Result>) -> List<Entrant>
    if (groups == 0 || config == null) {
        phases += Phase.League(RoundRobinPhase.single(order, division.turns))
        qualifiers = { _, _ -> emptyList() }
    } else {
        val label = "league:${division.country}:${division.division}"
        require(order.size % groups == 0 && (order.size / groups) % 2 == 0) {
            "$label deals ${order.size} clubs into $groups groups, which is not an equal, even split; that shape is outside what this version builds"
        }
        val dealt = (0 until groups).map { g -> order.filterIndexed { index, _ -> index % groups == g } }
        phases += Phase.League(RoundRobinPhase.grouped(dealt, division.turns, config.gamesInsideGroup))
        val perGroup = config.knockoutQualifiers
        if (perGroup in 1..MAX_KNOCKOUT_FIELD) {
            val field = perGroup * groups
            require(field >= 2 && field and (field - 1) == 0) {
                "$label would seed a final phase field of $field, which is not a power of two; that shape is outside what this version builds"
            }
            phases += Phase.Knockout(KnockoutPhase(emptyList(), legsPerRound = List(LeagueConfigEntry.PLAYOFF_ROUNDS) { true }, penalties = true, field = field))
        }
        qualifiers = { phase, results ->
            val picked = if (config.qualifyByOverallTable) {
                phase.overallTable(results).take(perGroup * groups).map { it.key }
            } else {
                phase.groups.indices.flatMap { g -> phase.groupTable(g, results).take(perGroup).map { it.key } }
            }
            picked.mapIndexed { i, key -> Entrant(key, i + 1) }
        }
    }
    return Competition(
        key = "league:${division.country}:${division.division}",
        kind = CompetitionKind.NATIONAL_LEAGUE,
        country = division.country,
        division = division.division,
        phases = phases,
        qualifiers = qualifiers,
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

/**
 * The relegated and the promoted of a finished division, read off its final
 * order. Relegated is worst first read as last first: the last relegated
 * keys of the order, closest to the cut first; promoted is the first
 * promotedCount keys, best first. The order itself already carries merit
 * order for a division that ended in a knockout, per Competition.finalOrder,
 * so movement never needs to know whether the division had a final phase.
 *
 * A division whose config.relegatedByGroup is set would, per 1.12, read
 * its relegation zone group by group rather than off one shared table; the
 * only distributed division that sets it is the Brazilian first division,
 * which carries no groups and so the flag is moot there, and no distributed
 * grouped division sets it. This plan therefore always reads the shared
 * final order, matching every distributed case.
 *
 * A division whose config.directRelegated is less than config.relegated
 * decides part of its movement by the promotion and relegation playoffs of
 * 1.12 rather than straight off the table; this plan treats every relegated
 * or promoted club as direct and defers the playoff mechanism, recording it
 * here rather than building it silently into the direct reading.
 */
@SpecRef("1.12")
data class Movement(val relegated: List<String>, val promoted: List<String>)

@SpecRef("1.12")
fun movement(division: LeagueDivision, finalOrder: List<String>, promotedCount: Int): Movement =
    Movement(relegated = finalOrder.takeLast(division.relegated), promoted = finalOrder.take(promotedCount))

@SpecRef("1.9")
private const val TWENTY_CLUB_STEP = 20

@SpecRef("1.9")
private const val DEFAULT_RELEGATED_TWENTY = 4

@SpecRef("1.9")
private const val DEFAULT_RELEGATED = 2

/** The largest final phase a configured league can ask for before the value is a sentinel of 1.11. */
@SpecRef("1.11")
private const val MAX_KNOCKOUT_FIELD = 64
