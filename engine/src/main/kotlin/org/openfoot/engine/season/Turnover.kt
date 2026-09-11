package org.openfoot.engine.season

import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.pyramidTiebreak
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.RuleSet
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng

/**
 * One boundary's worth of movement between two divisions of one country's
 * pyramid at the turnover of section 1.12: upper is the shallower division
 * of the pair, the one whose relegated clubs go down into the deeper one;
 * relegated names the clubs that leave upper for upper plus one, promoted
 * the clubs that leave the deeper division for upper. The last boundary of a
 * country's pyramid is not a division pair at all but the swap with the
 * reserve of section 1.9, where upper is the deepest division and promoted
 * are reserve clubs rather than members of any division.
 */
@SpecRef("1.12")
data class DivisionSwap(val country: Int, val upper: Int, val relegated: List<String>, val promoted: List<String>)

/**
 * Every swap one country's pyramid makes at the turnover of section 1.12,
 * read off the season's own closed tables rather than recomputed: the
 * division boundaries, top of the pyramid first, then the last division's
 * swap with the country's reserve.
 *
 * A division pair's boundary reads movement twice off the same LeagueDivision
 * shape leagueDivisions built from this season's own standings: the upper
 * division's relegated clubs first, with promotedCount nought since a
 * division's own relegation zone never depends on how many come up into it,
 * then the lower division's promoted clubs, with promotedCount set to how
 * many the upper division just sent down, since 1.12 promotes exactly as
 * many as the division above lost.
 *
 * The reserve is every club of the country sitting on Standing.WithoutDivision,
 * ordered the way section 1.9's own queue orders the pyramid: level
 * descending, ties broken by the same per club draw the pyramid itself used
 * (pyramidTiebreak, read off the world's own seed since the reserve's order
 * is a fact about the world and not about any one season), and, past that,
 * by reference for a total order. The swap moves the smaller of the last
 * division's relegation count and the reserve's own size, so a reserve too
 * small to fill every relegated place still swaps what it can rather than
 * refusing outright.
 */
@SpecRef("1.12")
fun divisionSwaps(state: SeasonState, country: Int): List<DivisionSwap> {
    val clubs = state.clubs.values.toList()
    val divisions = leagueDivisions(country, clubs, state.dataset)
    fun order(division: Int): List<String> =
        state.closed.firstOrNull { it.key == "league:$country:$division" }?.finalOrder
            ?: throw IllegalStateException("division $division of $country has not closed")

    val swaps = ArrayList<DivisionSwap>()
    for ((upper, lower) in divisions.zipWithNext()) {
        val down = movement(upper, order(upper.division), promotedCount = 0).relegated
        val up = movement(lower, order(lower.division), promotedCount = upper.relegated).promoted
        swaps += DivisionSwap(country, upper.division, down, up)
    }
    val last = divisions.last()
    val worldRng = SplitMix64Rng(state.seed).fork(SeedDomain.WORLDGEN)
    val reserve = clubs
        .filter { it.country == country && it.standing == Standing.WithoutDivision }
        .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { pyramidTiebreak(worldRng, it.key) }.thenBy { it.key })
        .map { it.key }
    val down = movement(last, order(last.division), promotedCount = 0).relegated
    val count = minOf(down.size, reserve.size)
    swaps += DivisionSwap(country, last.division, down.take(count), reserve.take(count))
    return swaps
}

/**
 * The Brazilian fourth division queue of section 1.12, walked from the
 * final orders of every state championship's first division, per state
 * priority tier: the reading that state championships feed the fourth
 * division a candidate queue rather than a fixed list of names, since the
 * states' own results change every season.
 *
 * The queue first takes every state's own champion, tier by tier in
 * STATE_TIERS' own order, since the tiers are 1.12's own priority ranking of
 * which states' champions matter most to the national pyramid. Once every
 * tier's first place has been offered, the queue deepens: tier one gives up
 * its second and third place before any other tier gives up its own second,
 * and every tier but tier one only ever deepens by one place per pass, so a
 * strong state's runners up are still favoured over a weak state's champion
 * once the first pass of champions is exhausted. This exact shape, tier one
 * two places deeper each pass against every other tier one place deeper, is
 * not spelled out in so many words by section 1.12's own prose; it is this
 * reading's own bet at what "queue by tier, then by place" means there, kept
 * as OPEN-QUESTIONS item 81 and INFERIDO rather than read fact.
 *
 * A state that never played a division one championship, or whose final
 * order runs out of names before the queue does, simply stops contributing;
 * the queue keeps walking every other state until it has gathered size names
 * or every state's order is exhausted, whichever comes first. A name already
 * in the queue, which cannot happen from one state's own order but guards
 * against a caller handing two closes for the same state, is skipped rather
 * than duplicated.
 */
@SpecRef("1.12")
fun stateChampionsQueue(closes: List<CompetitionClose>, stateOf: (String) -> Int?, size: Int): List<String> {
    val orders = closes
        .filter { it.kind == CompetitionKind.STATE && it.key.endsWith(":1") }
        .mapNotNull { close -> close.finalOrder.firstOrNull()?.let { stateOf(it) }?.let { it to close.finalOrder } }
        .toMap()
    val queue = ArrayList<String>()
    fun take(tier: List<Int>, place: Int) {
        for (state in tier) {
            if (queue.size >= size) return
            val name = orders[state]?.getOrNull(place) ?: continue
            if (name !in queue) queue += name
        }
    }
    STATE_TIERS.forEach { take(it, 0) }
    var depth = 1
    while (queue.size < size && depth < MAX_QUEUE_DEPTH) {
        take(STATE_TIERS[0], depth)
        take(STATE_TIERS[0], depth + 1)
        STATE_TIERS.drop(1).forEach { take(it, depth) }
        depth++
    }
    return queue
}

/**
 * The next season built from a finished one, per section 1.4: every swap of
 * divisionSwaps applied to the standings, the Brazilian fourth division
 * rebuilt from the state champions' queue when it applies, prestige decayed
 * and promoted at the turnover of section 5.5, every player record reset
 * bare but for an injury still running, and a fresh set of competitions and
 * schedule built from the moved clubs.
 *
 * rules is unused by this plan; nextSeason keeps it as a parameter because
 * the next plan's aging, retirement and youth intake run at this same
 * turnover and read the rule set for their own MODERN fields, and adding the
 * parameter now keeps this function's shape stable across that plan rather
 * than growing an extra argument into every caller a second time.
 */
@Suppress("UNUSED_PARAMETER")
@SpecRef("1.4")
fun nextSeason(state: SeasonState, activeLeagues: Set<Int>, rules: RuleSet): SeasonState {
    require(state.finished) { "season ${state.number} has not finished" }
    val clubs = state.clubs.toMutableMap()
    for (country in activeLeagues.sorted()) {
        val divisions = leagueDivisions(country, clubs.values.toList(), state.dataset)
        if (divisions.isEmpty()) continue
        val rebuiltFourth = if (country == Country.BRAZIL) rebuildBrazilianFourth(state, clubs, divisions) else false
        for (swap in divisionSwaps(state, country)) {
            if (rebuiltFourth && (swap.upper == THIRD_DIVISION || swap.upper == FOURTH_DIVISION)) continue
            val isReserve = swap.upper == divisions.last().division
            swap.relegated.forEach { key ->
                clubs[key] = clubs.getValue(key).copy(standing = if (isReserve) Standing.WithoutDivision else Standing.InDivision(swap.upper + 1))
            }
            swap.promoted.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.InDivision(swap.upper)) }
        }
    }
    val moved = clubs.values.map { club ->
        club.copy(
            prestige = state.club(club.key).let { it.prestige.decayed(it.inLeague).promoted() },
            records = club.records.map { PlayerRecord(injuredUntil = it.injuredUntil) },
        )
    }
    val number = state.number + 1
    val competitions = buildCompetitions(number, moved, state.dataset, activeLeagues, state.seed)
    return SeasonState(
        number = number,
        year = state.year + 1,
        seed = state.seed,
        dataset = state.dataset,
        clubs = moved.associateBy { it.key },
        competitions = competitions.associateBy { it.key },
        schedule = SeasonSchedule.build(state.year + 1, competitions),
        played = emptyList(),
        closed = emptyList(),
        dateIndex = 0,
        lastTick = null,
    )
}

/**
 * The Brazilian fourth division's own turnover, per section 1.12: when the
 * pyramid seats a fourth division, and state championships are on, the
 * boundary rule of divisionSwaps never applies between the third division
 * and the fourth. Instead the third division's relegated clubs go straight
 * into the fourth, and the rest of the fourth's membership is refilled from
 * stateChampionsQueue, headed by those same relegated clubs so the queue
 * never names one of them twice, sized to leave the fourth division exactly
 * as many clubs as it already held. Every club the fourth division held
 * before that is not one of the third's relegated falls to Standing.WithoutDivision,
 * the country's reserve, the same place any relegated-with-nowhere-to-go
 * club of 1.9 lands.
 *
 * The fourth division also takes no part in the last division's swap with
 * the country's reserve that divisionSwaps otherwise builds for whichever
 * division sits deepest: 1.12 is explicit that the fourth division carries
 * no table of its own candidates from one season to the next, so there is no
 * "last division general table" left for that swap to read once this
 * function has already replaced the whole of the fourth's membership.
 *
 * Returns true when it rebuilt the fourth division, so the generic
 * per-boundary loop of nextSeason knows to skip both the boundary against
 * the third division and the reserve swap that would otherwise apply to the
 * fourth as the country's deepest division, rather than run divisionSwaps'
 * ordinary swaps over either a second time; false when there is no fourth
 * division, or state championships are off, and the generic rule is left to
 * run exactly as it always has.
 *
 * Season one's own fourth division is left exactly as the pyramid seated it:
 * this function is only ever called from the second season's turnover
 * onward, since nextSeason is the function that turns a finished season into
 * the next one and season one is never itself the product of a turnover.
 * That the fourth division is rebuilt at all only from the second season on,
 * rather than every season including whichever one first seats it, is this
 * plan's own bet, recorded under OPEN-QUESTIONS item 113.
 */
@SpecRef("1.12")
private fun rebuildBrazilianFourth(state: SeasonState, clubs: MutableMap<String, ClubState>, divisions: List<LeagueDivision>): Boolean {
    if (!state.dataset.options.playStateChampionships) return false
    val fourth = divisions.firstOrNull { it.division == FOURTH_DIVISION } ?: return false
    val third = divisions.firstOrNull { it.division == THIRD_DIVISION } ?: return false
    val thirdOrder = state.closed.firstOrNull { it.key == "league:${Country.BRAZIL}:$THIRD_DIVISION" }?.finalOrder
        ?: throw IllegalStateException("division $THIRD_DIVISION of Brazil has not closed")
    val relegatedOfThird = movement(third, thirdOrder, promotedCount = 0).relegated
    val queue = stateChampionsQueue(state.closed, { key -> state.club(key).club.entry.state }, size = fourth.clubs.size - relegatedOfThird.size)
    val incoming = relegatedOfThird + queue
    val outgoing = fourth.clubs.filterNot { it in incoming }
    incoming.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.InDivision(FOURTH_DIVISION)) }
    outgoing.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.WithoutDivision) }
    return true
}

@SpecRef("1.12")
private const val THIRD_DIVISION = 3

@SpecRef("1.12")
private const val FOURTH_DIVISION = 4

/** The five priority tiers of Brazilian states of 1.12, by state index of FORMAT-SPEC. */
@SpecRef("1.12")
private val STATE_TIERS: List<List<Int>> = listOf(
    listOf(25, 18, 10, 22),
    listOf(17, 23, 4, 15, 5),
    listOf(8, 1, 13, 11, 9, 19, 14),
    listOf(24, 0, 16, 12, 2, 6),
    listOf(7, 26, 20, 3, 21),
)

@SpecRef("1.12")
private const val MAX_QUEUE_DEPTH = 20
