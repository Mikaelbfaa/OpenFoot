package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.clubKey
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef

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
 * The reserve is the country's queue carried on SeasonState.reserves, never
 * a fresh reading of club levels: section 1.12 builds the pyramid once and
 * moves it only by the swap. The swap moves the smaller of the last
 * division's relegation count and the queue's own size. Out go that many of
 * the last clubs of the last division's final order, still in final order,
 * so a queue too short to fill every relegated place spares the best of the
 * relegation zone and never the worst; in come as many clubs from the head
 * of the queue. nextSeason then appends the relegated clubs to the queue's
 * tail, per 1.12's "os rebaixados vao para o fim da fila da reserva e os
 * promovidos saem do inicio".
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
    val reserve = requireNotNull(state.reserves[country]) { "country $country seats divisions and carries no reserve queue" }
    val down = movement(last, order(last.division), promotedCount = 0).relegated
    val count = minOf(down.size, reserve.size)
    swaps += DivisionSwap(country, last.division, down.takeLast(count), reserve.take(count))
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
 * divisionSwaps applied to the standings, the Brazilian fourth division's own
 * turnover when it is fed by the state championships, every state's
 * memberships moved by stateTurnover, prestige decayed and promoted at the
 * turnover of section 5.5, every player record reset bare but for an injury
 * still running, and a fresh set of competitions and schedule built from the
 * moved clubs.
 *
 * The card records start the new season empty because section 3.8 keeps them
 * per competition, each belonging to one competition of the season just
 * finished, and every competition is rebuilt here; section 1.4's per club
 * reset is only a resync of a cached club link and zeroes nothing. An injury
 * is a date on the calendar rather than a count, so it carries across. Both
 * are OPEN-QUESTIONS item 116, INFERIDO.
 *
 * Each country's reserve queue moves with its last division's swap: the
 * promoted clubs leave its head and the relegated join its tail in final
 * order. A country whose league is not among activeLeagues keeps no queue,
 * and a country that seats divisions without a queue is refused by name,
 * since its reserve would otherwise silently never go up.
 *
 * The state memberships move by FORMAT-SPEC's "Rebaixados e promovidos",
 * reading each state division's first phase overall table off its finished
 * competition and its merit list off its close.
 *
 * Section 0 fires the weekly tick on every Sunday of the year, so a season is
 * over only once its last Sunday has fired; playRound stops at the last
 * scheduled date and leaves the Sundays after it to playSeason, and the
 * turnover refuses a season that was stepped to its last round without them.
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
    val lastSunday = CalendarDate.lastSunday(state.year)
    require(state.lastTick == lastSunday) {
        "season ${state.number}'s Sundays were not all fired: the last fired was ${state.lastTick}, " +
            "the year's last is $lastSunday; playSeason fires them"
    }
    val clubs = state.clubs.toMutableMap()
    val reserves = LinkedHashMap<Int, List<String>>()
    val fourth = state.fourth?.takeIf { Country.BRAZIL in activeLeagues }
    var door = emptyList<String>()
    for (country in activeLeagues.sorted()) {
        val divisions = leagueDivisions(country, clubs.values.toList(), state.dataset)
        if (divisions.isEmpty()) continue
        var reserve = requireNotNull(state.reserves[country]) { "country $country seats divisions and carries no reserve queue" }
        val fedFourth = country == Country.BRAZIL && fourth != null
        for (swap in divisionSwaps(state, country)) {
            if (fedFourth && swap.upper >= THIRD_DIVISION) continue
            val isReserve = swap.upper == divisions.last().division
            swap.relegated.forEach { key ->
                clubs[key] = clubs.getValue(key).copy(standing = if (isReserve) Standing.WithoutDivision else Standing.InDivision(swap.upper + 1))
            }
            swap.promoted.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.InDivision(swap.upper)) }
            if (isReserve) reserve = reserve.drop(swap.promoted.size) + swap.relegated
        }
        if (fedFourth) {
            val turned = turnBrazilianFourth(state, clubs, reserve)
            door = turned.door
            reserve = turned.reserve
        }
        reserves[country] = reserve
    }
    val states = stateTurnover(
        state.states,
        tableOf = { key -> state.stateCompetitionTable(key) },
        meritOf = { key -> state.closed.firstOrNull { it.key == key }?.finalOrder ?: throw IllegalStateException("$key has not closed") },
    )
    val moved = clubs.values.map { club ->
        club.copy(
            prestige = state.club(club.key).let { it.prestige.decayed(it.inLeague).promoted() },
            records = club.records.map { PlayerRecord(injuredUntil = it.injuredUntil) },
        )
    }
    val number = state.number + 1
    val carried = fourth?.copy(door = door)
    val competitions = buildCompetitions(number, moved, state.dataset, activeLeagues, state.seed, states)
    return SeasonState(
        number = number,
        year = state.year + 1,
        seed = state.seed,
        dataset = state.dataset,
        clubs = moved.associateBy { it.key },
        competitions = competitions.associateBy { it.key },
        schedule = SeasonSchedule.build(state.year + 1, competitions + listOfNotNull(carried?.reserved(state.dataset, number, state.seed))),
        played = emptyList(),
        closed = emptyList(),
        dateIndex = 0,
        lastTick = null,
        reserves = reserves,
        states = states,
        fourth = carried,
    ).withBrazilianFourthIfDue()
}

/**
 * The first phase overall table of a finished state competition, by key,
 * the table FORMAT-SPEC's "Rebaixados e promovidos" reads the relegation
 * zone from, grouped preset or not.
 */
@SpecRef("FORMAT-SPEC, ces")
private fun SeasonState.stateCompetitionTable(key: String): List<String> {
    val competition = competitions[key] ?: throw IllegalStateException("$key was not played this season")
    check(competition.finished) { "$key has not finished" }
    val league = (competition.phases.first() as? Phase.League)?.phase ?: throw IllegalStateException("$key opens with no league phase")
    return league.overallTable(competition.results.first()).map { it.key }
}

/**
 * Brazil's fourth division while the state championships feed it, section
 * 1.9's special case: size is the configured size of the division, the one
 * the pyramid seated at world creation, and door is the clubs the third
 * division relegated at the previous turnover, which head the division's
 * queue for this season (empty in season one, which follows no turnover).
 */
@SpecRef("1.12")
data class BrazilianFourth(val size: Int, val door: List<String>) {
    /**
     * The competition the schedule reserves the fourth's dates from before
     * the division exists: the division's configured shape, the dataset's
     * configuration of Brazil's division four or the embedded default of
     * 1.9 when there is none, over size placeholder clubs, built by the same
     * leagueCompetition and read by the same datedRounds as the real
     * division will be. It is never played.
     */
    @SpecRef("1.10")
    internal fun reserved(dataset: WorldDataset, number: Int, seed: Long): Competition =
        leagueCompetition(
            LeagueDivision(Country.BRAZIL, DIVISION, configuration(dataset), (1..size).map { "reserved:$it" }),
            fourthRng(seed, number),
        )

    companion object {
        /** The division number of the fourth division. */
        @SpecRef("1.12")
        const val DIVISION = 4

        /** The key of the fourth division's league competition. */
        @SpecRef("1.12")
        internal val KEY = "league:${Country.BRAZIL}:$DIVISION"

        internal fun configuration(dataset: WorldDataset): LeagueConfigEntry? =
            dataset.leagues.firstOrNull { it.country == Country.BRAZIL && it.division == DIVISION }

        /** The very fork buildCompetitions takes for the fourth's key, so the build's timing moves no draw. */
        internal fun fourthRng(seed: Long, number: Int): Rng = seasonFixturesRoot(seed, number).fork(clubKey(KEY))
    }
}

/**
 * The Brazilian fourth division built when the season's last state
 * competition closes, per sections 1.9 and 1.12, or this season unchanged
 * when that build is not due: no fourth fed by the states, the fourth
 * already built, or a state competition still unfinished. A season with no
 * state competition at all is due at once.
 *
 * The division's clubs are rebuiltFourth over three candidate lists, in this
 * order: the door, the clubs the third relegated at the previous turnover;
 * this season's state queue, stateChampionsQueue over the state closes of
 * this season (the walk of item 81); and, per 1.12 step 3, the Brazilian
 * clubs seated in no state division this season, in the dataset's club
 * order, which are the clubs waiting in a state reserve and the clubs of a
 * state too small to hold a championship. A club of divisions one to three,
 * or already chosen, is skipped, and the list stops at the configured size
 * or when the candidates run out; 1.12 step 4 leaves a short list short.
 *
 * The competition is leagueCompetition over that list, from the fork
 * buildCompetitions would take for its key. The schedule has reserved the
 * dates of the configured shape; SeasonSchedule.fitted hands a short
 * division's unused dates back and refuses by name a division that would
 * need more. A short list that cannot be played at all, fewer than two
 * clubs, an odd count, or a count that does not deal into the configured
 * groups evenly, is refused by name, OPEN-QUESTIONS item 122's bet.
 *
 * Every club seated leaves Brazil's reserve queue and stands in division
 * four, so the queue keeps holding exactly the Brazilian clubs without a
 * division.
 */
@SpecRef("1.12")
internal fun SeasonState.withBrazilianFourthIfDue(): SeasonState {
    val fourth = fourth ?: return this
    if (BrazilianFourth.KEY in competitions) return this
    if (competitions.values.any { it.kind == CompetitionKind.STATE && !it.finished }) return this

    val excluded = clubs.values
        .filter { it.country == Country.BRAZIL && ((it.standing as? Standing.InDivision)?.division ?: Int.MAX_VALUE) < BrazilianFourth.DIVISION }
        .map { it.key }
        .toSet()
    val stateQueue = stateChampionsQueue(closed, { key -> club(key).club.entry.state }, size = Int.MAX_VALUE)
    val inStateDivision = states.divisions.flatMap { it.clubs }.toSet()
    val stateless = dataset.clubs
        .filter { it.country == Country.BRAZIL && it.ref in clubs && it.ref !in inStateDivision }
        .map { it.ref }
    val members = rebuiltFourth(fourth.door, stateQueue, stateless, excluded, fourth.size)
    require(members.size >= 2 && members.size % 2 == 0) {
        "${BrazilianFourth.KEY} gathered ${members.size} clubs of the ${fourth.size} configured, and an odd or single club field cannot be played"
    }
    val competition = leagueCompetition(
        LeagueDivision(Country.BRAZIL, BrazilianFourth.DIVISION, BrazilianFourth.configuration(dataset), members),
        BrazilianFourth.fourthRng(seed, number),
    )
    val queue = requireNotNull(reserves[Country.BRAZIL]) { "Brazil feeds its fourth division from the states and carries no reserve queue" }
    val seated = members.toSet()
    var built = copy(
        competitions = competitions + (competition.key to competition),
        schedule = schedule.fitted(competition),
        reserves = reserves + (Country.BRAZIL to queue.filter { it !in seated }),
    )
    members.forEach { key -> built = built.withClub(built.club(key).copy(standing = Standing.InDivision(BrazilianFourth.DIVISION))) }
    return built
}

/** What the fourth division's turnover leaves: the door for next season and Brazil's reserve queue. */
private data class FourthTurn(val door: List<String>, val reserve: List<String>)

/**
 * The Brazilian fourth division's own turnover, per section 1.12's special
 * case, run once every other boundary of Brazil's pyramid has moved.
 *
 * Section 1.12 says the fourth fed by the states "nao participa do swap
 * normal de fim de temporada contra a 3a divisao": the third relegates its
 * clubs directly and the fourth is rebuilt every season from the state
 * queue, carrying no table of candidates from one season to the next. So the
 * third's relegated clubs, by its final order, do not enter the fourth here:
 * they stand without a division and become the door, the head of next
 * season's fourth division queue. The fourth's own best clubs, by its final
 * order, as many as the third relegates, still go up into the third, which
 * keeps the third at its size; that promotion is OPEN-QUESTIONS item 121's
 * bet, INFERIDO, since 1.12 forbids only a playoff of access from the
 * fourth. Every other club of the old fourth stands without a division.
 *
 * Brazil's reserve queue takes the door clubs and then the rest of the old
 * fourth at its tail, each in final order, so it keeps holding exactly the
 * Brazilian clubs without a division; the next season's build takes out of
 * it every club it seats.
 */
@SpecRef("1.12")
private fun turnBrazilianFourth(state: SeasonState, clubs: MutableMap<String, ClubState>, reserve: List<String>): FourthTurn {
    val divisions = leagueDivisions(Country.BRAZIL, state.clubs.values.toList(), state.dataset)
    val third = divisions.first { it.division == THIRD_DIVISION }
    val fourth = divisions.firstOrNull { it.division == BrazilianFourth.DIVISION }
        ?: throw IllegalStateException("Brazil's fourth division was never built this season")
    fun order(division: Int): List<String> =
        state.closed.firstOrNull { it.key == "league:${Country.BRAZIL}:$division" }?.finalOrder
            ?: throw IllegalStateException("division $division of Brazil has not closed")
    val fourthOrder = order(BrazilianFourth.DIVISION)
    val relegatedOfThird = movement(third, order(THIRD_DIVISION), promotedCount = 0).relegated
    val promotedOfFourth = movement(fourth, fourthOrder, promotedCount = relegatedOfThird.size).promoted
    promotedOfFourth.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.InDivision(THIRD_DIVISION)) }
    relegatedOfThird.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.WithoutDivision) }
    val rest = fourthOrder.filter { it !in promotedOfFourth }
    rest.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.WithoutDivision) }
    return FourthTurn(door = relegatedOfThird, reserve = reserve + relegatedOfThird + rest)
}

/**
 * The fourth division's membership, per section 1.12's queue: the door
 * first, then the state queue, then the stateless clubs of step 3, each in
 * its own order, filled up to size. A name in excluded, a club already in
 * divisions one to three, and a name already chosen, such as a state
 * champion that is also one of the door clubs, are skipped. Candidates
 * running out leave the list short of size, step 4 of 1.12; this function
 * neither pads nor fails, so the membership rule is a plain function of its
 * own inputs, and withBrazilianFourthIfDue is where those inputs are read.
 */
@SpecRef("1.12")
internal fun rebuiltFourth(door: List<String>, stateQueue: List<String>, stateless: List<String>, excluded: Set<String>, size: Int): List<String> {
    val members = ArrayList<String>()
    for (name in door + stateQueue + stateless) {
        if (members.size >= size) break
        if (name in excluded || name in members) continue
        members += name
    }
    return members
}

@SpecRef("1.12")
private const val THIRD_DIVISION = 3

/** The five priority tiers of Brazilian states of 1.12, by state index of FORMAT-SPEC. */
@SpecRef("1.12")
private val STATE_TIERS: List<List<Int>> = listOf(
    listOf(25, 18, 10, 22),
    listOf(17, 23, 4, 15, 5),
    listOf(8, 1, 13, 11, 9, 19, 14),
    listOf(24, 0, 16, 12, 2, 6),
    listOf(7, 26, 20, 3, 21),
)

/**
 * How many places deep the state champions' queue walks each state's final
 * order at most: twenty, the largest nTimes of the state preset table of
 * FORMAT-SPEC (preset 10, twenty clubs in four groups), since no state's
 * first division can hold more clubs and so no final order is longer.
 */
@SpecRef("FORMAT-SPEC, formula")
private const val MAX_QUEUE_DEPTH = 20
