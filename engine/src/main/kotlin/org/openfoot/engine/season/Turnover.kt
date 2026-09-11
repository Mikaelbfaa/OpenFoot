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
 * The Brazilian fourth division's own turnover while the state
 * championships feed it, section 1.12's special case, as data: promoted
 * names the fourth's best clubs, which go up into the third; door the
 * third's relegated clubs, which stand without a division and head next
 * season's fourth division queue; released every other club of the old
 * fourth, which stands without a division too. Each list keeps the final
 * order it was read from.
 */
@SpecRef("1.12")
data class FourthTurnover(val promoted: List<String>, val door: List<String>, val released: List<String>)

/**
 * What the turnover does to one division's clubs, keyed by the division's
 * competition key: up names the clubs that leave it for the division above,
 * down the clubs that leave it downwards, into the division below, a
 * reserve, or the door of the Brazilian fourth, each in the order the
 * turnover moves them. The top division's up is always empty, and a club
 * arriving from a reserve belongs to no division and so to no departures.
 */
@SpecRef("1.12")
data class Departures(val key: String, val up: List<String>, val down: List<String>)

/**
 * Every movement of one finished season's turnover, computed once by
 * seasonMovements and read by both nextSeason, which applies it, and the
 * season printout, which names it.
 *
 * swaps are the national boundaries the turnover applies, country by country
 * ascending and each from the top of the pyramid down, the reserve swap
 * last; fourth is the Brazilian fourth division's own turnover while the
 * states feed it, and null otherwise; states is the state turnover, the next
 * state memberships together with the swaps that made them. departures
 * restates all of it per division, league divisions first, country then
 * division, then state divisions, state then division.
 */
@SpecRef("1.12")
data class SeasonMovements(
    val swaps: List<DivisionSwap>,
    val fourth: FourthTurnover?,
    val states: StateTurnover,
    val departures: List<Departures>,
) {
    /** The departures of the division that plays the competition of the given key, or null when that competition is no division. */
    fun departuresOf(key: String): Departures? = departures.firstOrNull { it.key == key }
}

/**
 * The whole turnover of a finished season, section 1.12 for the national
 * pyramids and FORMAT-SPEC's "Rebaixados e promovidos" for the states, as
 * data rather than as a changed season: the one function nextSeason applies
 * and the season printout reads, so what is printed as moving is what moves.
 *
 * The countries moved are those whose clubs stand in a division this
 * season, which are the active leagues that seated a pyramid. Each one's
 * boundaries are divisionSwaps, read off the season's closed tables. While
 * the states feed Brazil's fourth division, Brazil's boundaries from the
 * third down do not swap: the third relegates straight to the door and the
 * fourth goes through fourthTurnover instead. The states then move by
 * stateTurnover, reading each state division's first phase overall table
 * off its finished competition and its merit list off its close.
 */
@SpecRef("1.12")
fun seasonMovements(state: SeasonState): SeasonMovements {
    require(state.finished) { "season ${state.number} has not finished, and its turnover moves nobody yet" }
    val clubs = state.clubs.values.toList()
    val swaps = ArrayList<DivisionSwap>()
    val departures = ArrayList<Departures>()
    var fourth: FourthTurnover? = null
    val countries = clubs.filter { it.standing is Standing.InDivision }.map { it.country }.distinct().sorted()
    for (country in countries) {
        val fed = state.fourth.feeds(country)
        val own = divisionSwaps(state, country).filter { !fed || it.upper < THIRD_DIVISION }
        val turn = if (fed) fourthTurnover(state) else null
        swaps += own
        if (turn != null) fourth = turn
        for (division in leagueDivisions(country, clubs, state.dataset).map { it.division }) {
            val up = when {
                turn != null && division == BrazilianFourth.DIVISION -> turn.promoted
                else -> own.firstOrNull { it.upper == division - 1 }?.promoted.orEmpty()
            }
            val down = when {
                turn != null && state.fourth.relegatesToTheDoor(country, division) -> turn.door
                turn != null && division == BrazilianFourth.DIVISION -> turn.released
                else -> own.firstOrNull { it.upper == division }?.relegated.orEmpty()
            }
            departures += Departures("league:$country:$division", up, down)
        }
    }
    val states = stateTurnover(
        state.states,
        tableOf = { key -> state.stateCompetitionTable(key) },
        meritOf = { key -> state.closed.firstOrNull { it.key == key }?.finalOrder ?: throw IllegalStateException("$key has not closed") },
    )
    for (division in state.states.divisions) {
        fun swap(upper: Int) = states.swaps.firstOrNull { it.state == division.state && it.upper == upper }
        departures += Departures(
            stateCompetitionKey(division),
            up = swap(division.division - 1)?.promoted.orEmpty(),
            down = swap(division.division)?.relegated.orEmpty(),
        )
    }
    return SeasonMovements(swaps, fourth, states, departures)
}

/**
 * True when this season's Brazilian fourth division, the receiver, is fed
 * by the state championships and the given country is Brazil: section
 * 1.12's special case, under which the fourth does not take part in the
 * normal swap against the third.
 */
@SpecRef("1.12")
internal fun BrazilianFourth?.feeds(country: Int): Boolean = this != null && country == Country.BRAZIL

/**
 * True when the given division is Brazil's third while the states feed the
 * fourth: section 1.12 says the engine forces rebaixadosDireto to
 * nRebaixados there, so the third relegates its clubs directly, to the door
 * of the next fourth, and no playoff decides any of it. The turnover sends
 * the third's relegated to the door by this condition, and the third's
 * competition notes no relegation playoff by the same one.
 */
@SpecRef("1.12")
internal fun BrazilianFourth?.relegatesToTheDoor(country: Int, division: Int): Boolean = feeds(country) && division == THIRD_DIVISION

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
 * The next season built from a finished one, per section 1.4: every movement
 * of seasonMovements applied, the national swaps and the Brazilian fourth
 * division's own turnover to the standings and the reserve queues, the
 * state turnover's next memberships carried as they are, prestige decayed
 * and promoted at the turnover of section 5.5, every player record reset
 * bare but for an injury still running, and a fresh set of competitions and
 * schedule built from the moved clubs. seasonMovements is the only place
 * the moves are decided, so the season printout, which names them from the
 * same function, never disagrees with what this function does.
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
 * order. While the states feed Brazil's fourth, Brazil's queue instead takes
 * the door clubs and then the fourth's released clubs at its tail, so it
 * keeps holding exactly the Brazilian clubs without a division; the next
 * season's build of the fourth takes out of it every club it seats. A
 * country whose league seated no pyramid keeps no queue, and a country that
 * seats divisions without a queue is refused by name, since its reserve
 * would otherwise silently never go up.
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
    val movements = seasonMovements(state)
    val clubs = state.clubs.toMutableMap()
    fun stand(keys: List<String>, standing: Standing) = keys.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = standing) }
    val reserves = LinkedHashMap<Int, List<String>>()
    for (country in movements.swaps.map { it.country }.distinct()) {
        var reserve = requireNotNull(state.reserves[country]) { "country $country seats divisions and carries no reserve queue" }
        val last = leagueDivisions(country, state.clubs.values.toList(), state.dataset).last().division
        for (swap in movements.swaps.filter { it.country == country }) {
            val intoReserve = swap.upper == last
            stand(swap.relegated, if (intoReserve) Standing.WithoutDivision else Standing.InDivision(swap.upper + 1))
            stand(swap.promoted, Standing.InDivision(swap.upper))
            if (intoReserve) reserve = reserve.drop(swap.promoted.size) + swap.relegated
        }
        val fourth = movements.fourth
        if (fourth != null && state.fourth.feeds(country)) {
            stand(fourth.promoted, Standing.InDivision(THIRD_DIVISION))
            stand(fourth.door + fourth.released, Standing.WithoutDivision)
            reserve = reserve + fourth.door + fourth.released
        }
        reserves[country] = reserve
    }
    val states = movements.states.next
    val moved = clubs.values.map { club ->
        club.copy(
            prestige = state.club(club.key).let { it.prestige.decayed(it.inLeague).promoted() },
            records = club.records.map { PlayerRecord(injuredUntil = it.injuredUntil) },
        )
    }
    val number = state.number + 1
    val carried = state.fourth?.copy(door = movements.fourth?.door.orEmpty())
    val competitions = buildCompetitions(number, moved, state.dataset, activeLeagues, state.seed, states, carried)
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
 * Section 1.11 puts a preliminary knockout ahead of the groups of a fourth
 * configured at PRELIMINARY_CONFIGURED_SIZE when the candidates reach
 * PRELIMINARY_SUPPLY. The supply is counted by the one walk that seats the
 * division, rebuiltFourth run to the larger of the two sizes, whose first
 * size names are exactly the members, so the count follows the same skips
 * as the membership and no second queue rule exists. This version does not
 * play the preliminary: the first size candidates are seated, and the
 * division lists Approximation.PRELIMINARY_NOT_PLAYED in its
 * approximations, OPEN-QUESTIONS item 118's announced fallback.
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
    val supply = rebuiltFourth(fourth.door, stateQueue, stateless, excluded, maxOf(fourth.size, PRELIMINARY_SUPPLY))
    val members = supply.take(fourth.size)
    require(members.size >= 2 && members.size % 2 == 0) {
        "${BrazilianFourth.KEY} gathered ${members.size} clubs of the ${fourth.size} configured, and an odd or single club field cannot be played"
    }
    val competition = leagueCompetition(
        LeagueDivision(Country.BRAZIL, BrazilianFourth.DIVISION, BrazilianFourth.configuration(dataset), members),
        BrazilianFourth.fourthRng(seed, number),
        preliminary = fourth.size == PRELIMINARY_CONFIGURED_SIZE && supply.size >= PRELIMINARY_SUPPLY,
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

/**
 * The Brazilian fourth division's own turnover, per section 1.12's special
 * case, read off the season's closed tables; nextSeason applies it once
 * every other boundary of Brazil's pyramid has moved.
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
 * The released clubs, every club of the old fourth that does not go up,
 * are printed as the fourth's down line: they leave the division for the
 * reserve, although many of them come back through the next season's state
 * queue.
 */
@SpecRef("1.12")
private fun fourthTurnover(state: SeasonState): FourthTurnover {
    val divisions = leagueDivisions(Country.BRAZIL, state.clubs.values.toList(), state.dataset)
    val third = divisions.first { it.division == THIRD_DIVISION }
    val fourth = divisions.firstOrNull { it.division == BrazilianFourth.DIVISION }
        ?: throw IllegalStateException("Brazil's fourth division was never built this season")
    fun order(division: Int): List<String> =
        state.closed.firstOrNull { it.key == "league:${Country.BRAZIL}:$division" }?.finalOrder
            ?: throw IllegalStateException("division $division of Brazil has not closed")
    val fourthOrder = order(BrazilianFourth.DIVISION)
    val door = movement(third, order(THIRD_DIVISION), promotedCount = 0).relegated
    val promoted = movement(fourth, fourthOrder, promotedCount = door.size).promoted
    return FourthTurnover(promoted = promoted, door = door, released = fourthOrder.filter { it !in promoted })
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

/** The configured size of a fourth division for which section 1.11 describes the preliminary knockout. */
@SpecRef("1.11")
private const val PRELIMINARY_CONFIGURED_SIZE = 64

/** The candidate count at which section 1.11's preliminary knockout is due for a fourth of the configured size above. */
@SpecRef("1.11")
private const val PRELIMINARY_SUPPLY = 68

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
