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
 * A division of an odd club count is refused up front, with a message that
 * names the division and its count: the dataset allows an odd team count,
 * but section 1.3 records the round robin's odd path as unreachable for
 * every configured league, and this version builds no bye.
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
 * rather than instead of it; this keeps to the flat reading the
 * RoundRobinPhase.grouped interface already gives (with games inside the
 * group, only the groups' own round robins are played, never a cross group
 * game, matching section 1.3's own single-league engine), and OPEN-QUESTIONS
 * item 119 records the conflict between that reading and 1.11's wording.
 *
 * When config.knockoutQualifiers is a plain count from one to sixty four, a
 * knockout phase follows the groups, played by the state championship
 * engine that section 1.11 says the league reuses, and its qualification
 * rule is data, a Qualifiers value. Without the overall table option the
 * qualifiers are each group's own top config.knockoutQualifiers,
 * Qualifiers.PerGroup, seeded by groupedSeeds, the very seeding the grouped
 * state presets use: the opening rounds stay inside each group, place p
 * against place q plus one minus p for q qualifiers a group, until one club
 * a group remains, and the group survivors then meet in bracket order, A
 * against B, C against D, and so on. FORMAT-SPEC pairs the grouped presets'
 * quarter finals inside the group, first against second, groups A to D in
 * order, and 1.11 has the league reuse that; 1.11's own parenthetical,
 * first of each group against second of the next, contradicts both, and
 * OPEN-QUESTIONS item 120 records the FORMAT-SPEC reading winning, INFERIDO
 * for more than two qualifiers a group. With config.qualifyByOverallTable
 * on, 1.11 ignores the groups: the top of the overall table goes through,
 * seeded in table order, strong against weak, Qualifiers.OverallTable. In
 * every tie the better placed side hosts the return leg, FORMAT-SPEC's rule,
 * which Tie carries. Every round of the final phase is two legged,
 * FINAL_PHASE_TWO_LEGGED, OPEN-QUESTIONS item 115's bet.
 *
 * config.knockoutQualifiers equal to LeagueConfigEntry.SERIE_C_FORMAT
 * selects the hand written Brazilian Serie C format of 1.11 (a flat first
 * phase, then two groups of four playing a second phase, then a two legged
 * final between the group winners) rather than an ordinary qualifier count.
 * This version does not build that dedicated format: it plays the sentinel
 * as a flat league with no final phase, and says so. The sixty eight club
 * preliminary knockout that 1.11 describes ahead of the Brazilian fourth
 * division's groups is likewise not built; preliminary is true when the
 * caller, the fourth's build at state close, found that the preliminary was
 * due, and the division then seats its configured field directly and says
 * so. directRelegation is true when section 1.12 forces this division's
 * relegation direct, Brazil's third while the states feed the fourth, and a
 * relegation playoff configured for it is then no approximation; directPromotion
 * is the same for a promotion playoff, true when this division takes no part
 * in the normal swap at all, Brazil's fourth while the states feed it, section
 * 1.12's own words that it has no access playoff to the third either. Every
 * such fallback is listed in the competition's approximations, by
 * leagueApproximations, and OPEN-QUESTIONS item 118 records them all. A
 * configured division whose club count does not divide into config.groups
 * equal, even sized groups, or whose final phase field would not be a power
 * of two, is refused rather than built: that shape is outside what this
 * version builds.
 */
@SpecRef("1.11")
fun leagueCompetition(
    division: LeagueDivision,
    rng: Rng,
    preliminary: Boolean = false,
    directRelegation: Boolean = false,
    directPromotion: Boolean = false,
): Competition {
    val key = "league:${division.country}:${division.division}"
    require(division.clubs.size % 2 == 0) {
        "$key holds ${division.clubs.size} clubs, and an odd count plays no round robin of section 1.3; that shape is outside what this version builds"
    }
    val order = shuffledOrder(division.clubs, rng)
    val config = division.config
    val groups = config?.groups ?: 0
    val phases = ArrayList<Phase>()
    var qualifiers: Qualifiers = Qualifiers.None
    if (groups == 0 || config == null) {
        phases += Phase.League(RoundRobinPhase.single(order, division.turns))
    } else {
        require(order.size % groups == 0 && (order.size / groups) % 2 == 0) {
            "$key deals ${order.size} clubs into $groups groups, which is not an equal, even split; that shape is outside what this version builds"
        }
        val dealt = (0 until groups).map { g -> order.filterIndexed { index, _ -> index % groups == g } }
        phases += Phase.League(RoundRobinPhase.grouped(dealt, division.turns, config.gamesInsideGroup))
        val perGroup = config.knockoutQualifiers
        if (perGroup in 1..MAX_KNOCKOUT_FIELD) {
            val field = perGroup * groups
            require(field >= 2 && field and (field - 1) == 0) {
                "$key would seed a final phase field of $field, which is not a power of two; that shape is outside what this version builds"
            }
            phases += Phase.Knockout(KnockoutPhase(emptyList(), legsPerRound = listOf(FINAL_PHASE_TWO_LEGGED), penalties = true, field = field))
            qualifiers = if (config.qualifyByOverallTable) Qualifiers.OverallTable(field) else Qualifiers.PerGroup(perGroup)
        }
    }
    return Competition(
        key = key,
        kind = CompetitionKind.NATIONAL_LEAGUE,
        country = division.country,
        division = division.division,
        phases = phases,
        qualifiers = qualifiers,
        approximations = leagueApproximations(config, preliminary, directRelegation, directPromotion),
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

/**
 * The formats of a configured division this version replaces by a generic
 * fallback, as the texts of Approximation in its declaration order, per
 * OPEN-QUESTIONS item 118:
 *
 * the Serie C sentinel of 1.11, whenever config.knockoutQualifiers is
 * LeagueConfigEntry.SERIE_C_FORMAT; the sixty eight club preliminary of
 * 1.11, whenever the caller says it was due; the playoffs of 1.12, whenever
 * config.directRelegated is below config.relegated, unless directRelegation
 * says section 1.12 forces that division's relegation direct, or
 * config.promotionPlayoffPlaces is above nought, unless directPromotion says
 * this division takes no part in the normal swap at all and so has no access
 * playoff of its own either; and, on a division with
 * groups only, config.bestThirds and config.relegatedByGroup. Without groups
 * there are no thirds to rank across groups and one shared table is every
 * group's table, so those two flags change nothing there and earn no note:
 * Brazil's distributed division one sets relegatedByGroup without groups.
 * A division without a configuration is the embedded
 * default of 1.9, which is built exactly, and notes only a due preliminary.
 */
@SpecRef("1.11")
internal fun leagueApproximations(
    config: LeagueConfigEntry?,
    preliminary: Boolean,
    directRelegation: Boolean = false,
    directPromotion: Boolean = false,
): List<String> {
    val grouped = config != null && config.groups > 0
    return Approximation.entries.filter { note ->
        when (note) {
            Approximation.SERIE_C_AS_FLAT_LEAGUE -> config?.knockoutQualifiers == LeagueConfigEntry.SERIE_C_FORMAT
            Approximation.PRELIMINARY_NOT_PLAYED -> preliminary
            Approximation.PLAYOFFS_AS_DIRECT_MOVEMENT ->
                config != null &&
                    ((config.hasRelegationPlayoff && !directRelegation) || (config.promotionPlayoffPlaces > 0 && !directPromotion))
            Approximation.BEST_THIRDS_IGNORED -> grouped && config.bestThirds
            Approximation.RELEGATION_BY_GROUP_IGNORED -> grouped && config.relegatedByGroup
            Approximation.NEW_CUP_FORMAT_AS_STANDARD, Approximation.REAL_STATE_GROUPS_IGNORED -> false
        }
    }.map { it.text }
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
 * one distributed division that sets it, Brazil's first, carries no groups,
 * so the flag is moot there, and no distributed grouped division sets it.
 * This version always reads the shared final order, matching every
 * distributed case, and a grouped division that sets the flag says so in
 * its approximations.
 *
 * A division whose config.directRelegated is less than config.relegated,
 * or whose config.promotionPlayoffPlaces is above nought, decides part of
 * its movement by the promotion and relegation playoffs of 1.12 rather than
 * straight off the table; this version treats every relegated or promoted
 * club as direct and defers the playoff mechanism, and such a division says
 * so in its approximations. OPEN-QUESTIONS item 118 records both fallbacks.
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

/**
 * Whether each round of a grouped league's final phase is two legged. The
 * phase lists this one flag, and KnockoutPhase repeats a list's last entry
 * for every later round, so the whole phase is two legged and its dated
 * rounds, two a round, are counted by KnockoutPhase.datedRounds like every
 * other knockout's. Section 1.11 gives this phase the state championship
 * engine without a legs array of its own; two legs throughout is
 * OPEN-QUESTIONS item 115's bet, INFERIDO. It is not the playoff legs of
 * section 1.12, which belong to another mechanism.
 */
@SpecRef("1.11")
private const val FINAL_PHASE_TWO_LEGGED = true
