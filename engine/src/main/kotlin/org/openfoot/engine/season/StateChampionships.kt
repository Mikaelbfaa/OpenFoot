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
 * of its clubs go down at the end of the season: FORMAT-SPEC's "Rebaixados e
 * promovidos" reads the relegation count from this column, never from the
 * nRebaixados field of the state file entry.
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
 * Every state championship division of one season and every state's
 * reserve. divisions lists the divisions state by state, ascending, and
 * within a state from division one down. reserve is, per state, the state
 * reserve queue of FORMAT-SPEC load rule 7: the clubs of the state left
 * after its last division, head first, which feed promotion into that last
 * division at the end of the season. A state whose queue seated every club
 * holds no reserve entry.
 *
 * stateSetup seeds this once, for season one; every later season carries it
 * forward through stateTurnover, so a membership is never re-read from club
 * levels after world creation.
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
 * literal, if surprising, rule this version reproduces: a state can lose a
 * division's shape to a later division's shortfall. Without a matching
 * entry, or when the entry's preset does not fit, the division falls to the
 * default format: preset zero, penalties on, every knockout round two
 * legged. A division takes the first preset.teams clubs of the queue.
 *
 * Whatever the queue could not seat in a division becomes that state's
 * reserve, in queue order, the state reserve queue of load rule 7.
 *
 * This is the season one seeder only. A later season's divisions come from
 * the previous season's memberships moved by stateTurnover, never from a
 * fresh reading of club levels.
 *
 * The Sao Paulo real groups option of load rule 6, which can replace the
 * dealt groups of preset 7 with the state's own recorded regional groups,
 * is not built; stateCompetition says so on the division it concerns, and
 * OPEN-QUESTIONS item 118 records it among the deferred formats.
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
 * The state memberships of the next season, per FORMAT-SPEC's "Rebaixados e
 * promovidos": setup is the season just played, tableOf gives a division's
 * first phase overall table and meritOf its merit list, both by the
 * division's competition key.
 *
 * Every state is moved on its own, its boundaries processed from division
 * one down, one at a time, as section 1.12 processes a pyramid. A division
 * relegates the preset's relegated count, the last clubs of its first phase
 * overall table, which is one shared table even for a grouped preset: the
 * relegation is never read group by group. The division below sends up as
 * many clubs, the head of its merit list (champion, runner up, then the
 * knockout's earlier losers in bracket order, completed by the table). The
 * two lists trade places only when they are the same size, which the
 * distributed presets always give.
 *
 * The last division trades with the state reserve as a queue: out go the
 * last min(relegated, reserve size) clubs of its relegation zone, still in
 * table order, and in come as many clubs from the head of the reserve;
 * the relegated join the reserve's tail.
 *
 * A club that has already gone up out of a division at this turnover is no
 * longer in it when its relegation zone is read, so a club that won a
 * division's knockout from the foot of its table goes up and is not also
 * sent down; the zone is then the last clubs of the table among those still
 * there. Processing a state's boundaries from the top down is itself a bet:
 * the spec gives that order for the national pyramid of section 1.12, not
 * for state divisions, and OPEN-QUESTIONS item 123 records carrying it over,
 * INFERIDO.
 *
 * A division's new membership is its kept clubs in their previous
 * membership order, then the clubs arriving from above in their table
 * order, then the clubs arriving from below in merit order, or from the
 * reserve in queue order. The order matters because a grouped preset deals
 * its clubs k modulo the group count; it is OPEN-QUESTIONS item 123's bet,
 * INFERIDO.
 */
@SpecRef("FORMAT-SPEC, ces")
fun stateTurnover(setup: StateSetup, tableOf: (String) -> List<String>, meritOf: (String) -> List<String>): StateSetup {
    val divisions = ArrayList<StateDivision>()
    val reserves = mutableMapOf<Int, List<String>>()
    val states = (setup.divisions.map { it.state } + setup.reserve.keys).distinct().sorted()
    for (state in states) {
        val own = setup.divisions.filter { it.state == state }.sortedBy { it.division }
        var reserve = setup.reserve[state].orEmpty()
        val leaving = own.map { mutableSetOf<String>() }
        val fromAbove = own.map { ArrayList<String>() }
        val fromBelow = own.map { ArrayList<String>() }
        for (index in own.indices) {
            val division = own[index]
            val key = stateCompetitionKey(division)
            val zone = tableOf(key).filter { it !in leaving[index] }.takeLast(division.preset.relegated)
            if (index + 1 < own.size) {
                val lower = own[index + 1]
                val up = meritOf(stateCompetitionKey(lower)).take(division.preset.relegated)
                if (up.size == zone.size) {
                    leaving[index] += zone
                    fromBelow[index] += up
                    leaving[index + 1] += up
                    fromAbove[index + 1] += zone
                }
            } else {
                val count = minOf(zone.size, reserve.size)
                val down = zone.takeLast(count)
                leaving[index] += down
                fromBelow[index] += reserve.take(count)
                reserve = reserve.drop(count) + down
            }
        }
        own.forEachIndexed { index, division ->
            val kept = division.clubs.filter { it !in leaving[index] }
            divisions += division.copy(clubs = kept + fromAbove[index] + fromBelow[index])
        }
        if (reserve.isNotEmpty()) reserves[state] = reserve
    }
    return StateSetup(divisions, reserves)
}

/** The key of the competition one state division plays, naming the state and the division number. */
@SpecRef("FORMAT-SPEC, ces")
internal fun stateCompetitionKey(division: StateDivision): String = "state:${division.state}:${division.division}"

/**
 * Builds the competition one state division plays: a league phase over the
 * division's clubs, feeding a knockout of the preset's qualifiers.
 *
 * A groupless preset plays a single table, section 1.3's ordinary league,
 * over shuffledOrder(division.clubs, rng) for preset.twoTurns's turn count:
 * section 1.3 says every round robin shuffles its participants uniformly
 * before the circle is drawn, and a single table state division is no
 * exception. Its qualifiers are the top of the overall table, seeded one
 * through the qualifier count in table order, Qualifiers.OverallTable.
 *
 * Presets 7 and 10 deal the division's clubs into four groups instead, and
 * that deal draws nothing: FORMAT-SPEC's "Carga na criacao do mundo" load
 * rule six deals a grouped preset straight from the queue, already ordered
 * by level, the k-th club of the queue to group k modulo the group count,
 * and SIMULATION-SPEC section 1.3 is explicit that cross group play, games
 * inside the group off, draws no order of its own. So division.clubs, which
 * is the queue's own order, is dealt directly, with no shuffle and rng left
 * untouched on this path; the groups then play only across each other,
 * never within, which is RoundRobinPhase.grouped with gamesInsideGroup
 * false. Their qualifiers are each group's top two, Qualifiers.PerGroup,
 * seeded by groupedSeeds, the one seeding a grouped national league's final
 * phase shares. For four groups of two it reads the eight side bracket of
 * firstRoundTies, pairs (2,7), (4,5), (1,8) and (3,6), and gives each
 * group's first and second place the seeds of one pair, group by group, so
 * every quarter final is an internal affair of its own group and the semi
 * finals cross group A with B and group C with D, exactly as FORMAT-SPEC's
 * "1o do grupo x 2o do grupo" quarter finals read. The competition applies
 * the rule once the league phase has been played.
 *
 * The knockout's field is fixed from the preset before it has any entrants,
 * because a competition must know its round and leg count ahead of the
 * league phase finishing; it is seeded only once the league phase's results
 * are in. The competition's key names the state and the division number so
 * two divisions of the same state, or the same division number of two
 * states, never collide.
 *
 * realStateGroups is the dataset option of FORMAT-SPEC load rule six. With
 * it on, Sao Paulo's first division on preset 7 would seat the state's
 * recorded regional groups when every listed club is present; this version
 * always deals the queue, so that division lists
 * Approximation.REAL_STATE_GROUPS_IGNORED in its approximations, whether or
 * not every listed club is there, OPEN-QUESTIONS item 118's announced
 * fallback. No other division reads the option.
 */
@SpecRef("FORMAT-SPEC, ces")
fun stateCompetition(division: StateDivision, realStateGroups: Boolean, rng: Rng): Competition {
    val preset = division.preset
    val turns = if (preset.twoTurns) 2 else 1
    val league = if (preset.groups == 0) {
        RoundRobinPhase.single(shuffledOrder(division.clubs, rng), turns)
    } else {
        val groups = (0 until preset.groups).map { g -> division.clubs.filterIndexed { index, _ -> index % preset.groups == g } }
        RoundRobinPhase.grouped(groups, turns, gamesInsideGroup = false)
    }
    val qualifiers = if (preset.groups == 0) Qualifiers.OverallTable(preset.qualifiers) else Qualifiers.PerGroup(preset.qualifiers)
    val realGroupsDue = realStateGroups && division.state == SAO_PAULO && division.division == 1 && preset == STATE_PRESETS[REAL_GROUPS_PRESET]
    return Competition(
        key = stateCompetitionKey(division),
        kind = CompetitionKind.STATE,
        country = Country.BRAZIL,
        division = division.division,
        phases = listOf(
            Phase.League(league),
            Phase.Knockout(KnockoutPhase(emptyList(), division.twoLeggedRounds, division.penalties, field = preset.qualifiers * maxOf(1, preset.groups))),
        ),
        qualifiers = qualifiers,
        approximations = if (realGroupsDue) listOf(Approximation.REAL_STATE_GROUPS_IGNORED.text) else emptyList(),
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

/** The smallest club count a state fields a championship over, and the size of the smallest preset. */
@SpecRef("FORMAT-SPEC, ces")
private const val MINIMUM_STATE_CLUBS = 6

/** How many divisions the queue can fill, one championship per state per division number. */
@SpecRef("FORMAT-SPEC, ces")
private const val MAX_STATE_DIVISIONS = 4

/** Sao Paulo's state index, the one state whose first division load rule six can seat by its real groups. */
@SpecRef("FORMAT-SPEC, ces")
private const val SAO_PAULO = 25

/** The preset index whose dealt groups load rule six replaces with Sao Paulo's real groups. */
@SpecRef("FORMAT-SPEC, ces")
private const val REAL_GROUPS_PRESET = 7
