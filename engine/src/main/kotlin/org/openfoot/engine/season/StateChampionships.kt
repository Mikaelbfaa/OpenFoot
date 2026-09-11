package org.openfoot.engine.season

import org.openfoot.dataset.WorldDataset
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

/**
 * One row of FORMAT-SPEC's formula table for the Brazilian state
 * championships: how many clubs a division of this shape holds, how many
 * groups it deals them into (nought for a single table), how many of a
 * group, or of the whole table when there are no groups, go through to the
 * knockout, whether the league phase is played home and away, and how many
 * clubs the state relegates out of the division below it.
 */
@SpecRef("FORMAT-SPEC, formula")
data class StatePreset(val teams: Int, val groups: Int, val qualifiers: Int, val twoTurns: Boolean, val relegated: Int)

/**
 * The eleven presets of FORMAT-SPEC's formula table, indexed exactly as the
 * state file's preset field does: zero to ten. Only presets 7 and 10 deal
 * groups; every other preset is a single table.
 */
@SpecRef("FORMAT-SPEC, formula")
val STATE_PRESETS: List<StatePreset> = listOf(
    StatePreset(6, 0, 2, true, 2),
    StatePreset(8, 0, 4, true, 2),
    StatePreset(10, 0, 4, false, 2),
    StatePreset(11, 0, 4, false, 2),
    StatePreset(12, 0, 4, false, 2),
    StatePreset(12, 0, 8, false, 2),
    StatePreset(14, 0, 8, false, 2),
    StatePreset(16, 4, 2, false, 2),
    StatePreset(16, 0, 4, false, 2),
    StatePreset(16, 0, 8, false, 2),
    StatePreset(20, 4, 2, false, 4),
)

/**
 * One division of one state's championship, shaped for this season: which
 * state and which division number, the preset that fixes its size and
 * format, whether a level knockout tie falls to penalties or to the
 * original's defective tiebreak, the two legged flag of each knockout round
 * from the state file, and the clubs the queue handed this division, best
 * placed first.
 */
@SpecRef("FORMAT-SPEC, ces")
data class StateDivision(
    val state: Int,
    val division: Int,
    val preset: StatePreset,
    val penalties: Boolean,
    val twoLeggedRounds: List<Boolean>,
    val clubs: List<String>,
)

/**
 * One season's whole reading of the state files: every division that will
 * be played, and, per state, the clubs its queue could not seat in a
 * division, kept in queue order for a later plan's lower amateur rounds to
 * read.
 */
@SpecRef("FORMAT-SPEC, ces")
data class StateSetup(val divisions: List<StateDivision>, val reserve: Map<Int, List<String>>)

/**
 * Turns the dataset's state championship entries into the divisions one
 * season plays, per FORMAT-SPEC's "Campeonatos estaduais" section.
 *
 * Nothing is built when the dataset option is off. Otherwise every Brazilian
 * club (state championships are Brazil only, FORMAT-SPEC "estados") is
 * grouped by its state, and a state with fewer than six clubs never fields a
 * championship at all; six is both the eligibility floor and the smallest
 * preset. Within an eligible state the clubs form one queue, ranked by level
 * descending, ties broken by the same per club draw the pyramid itself uses
 * (assemblePyramids' tiebreak, exposed from Pyramid.kt as pyramidTiebreak
 * for this reason), and, past that, by reference for a total order.
 *
 * Up to four divisions are filled from the queue while at least six clubs
 * remain in it: a division reads the first state file entry naming its
 * state and division number, if any, and takes that entry's preset only
 * when the preset's team count does not exceed T, the state's total club
 * count counted once at the start rather than the clubs still left in the
 * queue. FORMAT-SPEC's own check reads that way, against the total rather
 * than the remaining count, and OPEN-QUESTIONS item 69 records it as the
 * literal, if surprising, rule this plan reproduces: a state can lose a
 * division's shape to a later division's shortfall. Without a matching
 * entry, or when the entry's preset does not fit, the division falls to the
 * default format: preset zero, penalties on, every knockout round two
 * legged. A division takes the first preset.teams clubs of the queue.
 *
 * Whatever the queue could not seat in a division becomes that state's
 * reserve, in queue order, for a later plan's lower rounds to draw on.
 *
 * The Sao Paulo real groups option of load rule 6, which can replace the
 * dealt groups of preset 7 with the state's own recorded regional groups,
 * is out of this plan; it is left as an implementation item for the plan
 * that adds it (see Task 10).
 */
@SpecRef("FORMAT-SPEC, ces")
fun stateSetup(clubs: List<ClubState>, dataset: WorldDataset, tiebreak: (String) -> Int): StateSetup {
    if (!dataset.options.playStateChampionships) return StateSetup(emptyList(), emptyMap())
    val byState = clubs
        .filter { it.country == Country.BRAZIL && it.club.entry.state != null }
        .groupBy { it.club.entry.state!! }
        .toSortedMap()

    val divisions = ArrayList<StateDivision>()
    val reserve = mutableMapOf<Int, List<String>>()
    for ((state, members) in byState) {
        if (members.size < MINIMUM_STATE_CLUBS) continue
        val total = members.size
        var queue = members
            .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { tiebreak(it.key) }.thenBy { it.key })
            .map { it.key }
        for (division in 1..MAX_STATE_DIVISIONS) {
            if (queue.size < MINIMUM_STATE_CLUBS) break
            val entry = dataset.stateChampionships.firstOrNull { it.state == state && it.division == division }
            val configured = entry?.let { STATE_PRESETS[it.preset] }
            val shaped = if (entry != null && configured != null && configured.teams <= total) {
                StateDivision(state, division, configured, entry.penaltiesTiebreak, entry.twoLeggedRounds, queue.take(configured.teams))
            } else {
                StateDivision(state, division, STATE_PRESETS[0], penalties = true, twoLeggedRounds = listOf(true, true, true), clubs = queue.take(STATE_PRESETS[0].teams))
            }
            divisions += shaped
            queue = queue.drop(shaped.clubs.size)
        }
        if (queue.isNotEmpty()) reserve[state] = queue
    }
    return StateSetup(divisions, reserve)
}

/**
 * Builds the competition one state division plays: a league phase over the
 * division's clubs, feeding a knockout of the preset's qualifiers.
 *
 * A groupless preset plays a single table, section 1.3's ordinary league,
 * for preset.twoTurns's turn count; its qualifiers are the top of the
 * overall table, seeded one through the qualifier count in table order.
 *
 * Presets 7 and 10 deal the division's clubs into four groups first,
 * shuffledOrder giving the deal a fair random order and the k-th dealt club
 * going to group k modulo the group count, and the groups then play only
 * across each other, never within, which is RoundRobinPhase.grouped with
 * gamesInsideGroup false. Their qualifiers are each group's top two, and
 * FORMAT-SPEC's bracket keeps a group's own pair together through the
 * quarter final: firstRoundTies' eight side bracket pairs seeds (2,7),
 * (4,5), (1,8) and (3,6), so assigning a group's first place and second
 * place the seeds of one of those four pairs, group by group, makes every
 * quarter final an internal affair of its own group and leaves the semi
 * finals to cross group A with B and group C with D, exactly as
 * FORMAT-SPEC's "1o do grupo x 2o do grupo" quarter finals read. This
 * builds those entrants sorted by seed once a league phase has been played,
 * inside the qualifiers function the competition calls when it advances.
 *
 * The knockout's field is fixed from the preset before it has any entrants,
 * because a competition must know its round and leg count ahead of the
 * league phase finishing; it is seeded only once the league phase's results
 * are in. The competition's key names the state and the division number so
 * two divisions of the same state, or the same division number of two
 * states, never collide.
 */
@SpecRef("FORMAT-SPEC, ces")
fun stateCompetition(division: StateDivision, rng: Rng): Competition {
    val preset = division.preset
    val turns = if (preset.twoTurns) 2 else 1
    val league = if (preset.groups == 0) {
        RoundRobinPhase.single(division.clubs, turns)
    } else {
        val dealt = shuffledOrder(division.clubs, rng)
        val groups = (0 until preset.groups).map { g -> dealt.filterIndexed { index, _ -> index % preset.groups == g } }
        RoundRobinPhase.grouped(groups, turns, gamesInsideGroup = false)
    }
    val qualifiers: (RoundRobinPhase, List<Result>) -> List<Entrant> = { phase, results ->
        if (phase.groups.size == 1) {
            phase.overallTable(results).take(preset.qualifiers).mapIndexed { i, row -> Entrant(row.key, i + 1) }
        } else {
            val seeds = GROUP_SEEDS
            phase.groups.indices.flatMap { g ->
                phase.groupTable(g, results).take(preset.qualifiers).mapIndexed { place, row -> Entrant(row.key, seeds[g][place]) }
            }.sortedBy { it.seed }
        }
    }
    return Competition(
        key = "state:${division.state}:${division.division}",
        kind = CompetitionKind.STATE,
        country = Country.BRAZIL,
        division = division.division,
        phases = listOf(
            Phase.League(league),
            Phase.Knockout(KnockoutPhase(emptyList(), division.twoLeggedRounds, division.penalties, field = preset.qualifiers * maxOf(1, preset.groups))),
        ),
        qualifiers = qualifiers,
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

/**
 * Seeds by group index and place within the group that put a group's first
 * and second in one of firstRoundTies' eight side pairs, group by group, so
 * every quarter final of the state bracket stays inside its own group.
 */
@SpecRef("FORMAT-SPEC, ces")
private val GROUP_SEEDS: List<List<Int>> = listOf(listOf(2, 7), listOf(4, 5), listOf(1, 8), listOf(3, 6))

/** The smallest club count a state fields a championship over, and the size of the smallest preset. */
@SpecRef("FORMAT-SPEC, ces")
private const val MINIMUM_STATE_CLUBS = 6

/** How many divisions the queue can fill, one championship per state per division number. */
@SpecRef("FORMAT-SPEC, ces")
private const val MAX_STATE_DIVISIONS = 4
