package org.openfoot.engine.season

import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.World
import org.openfoot.engine.world.clubKey
import org.openfoot.engine.world.pyramidTiebreak
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Rng
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng

/**
 * One match already played, kept exactly as it was recorded: the calendar
 * date it fell on, the competition it belongs to, the scheduled fixture it
 * answers and the result. Section 1.10 keeps a season's whole history this
 * way, a flat list rather than one nested inside each competition, so a
 * reader who wants everything a club played this year, across every
 * competition, filters one list instead of walking several.
 */
@SpecRef("1.10")
data class PlayedMatch(val date: CalendarDate, val competition: String, val match: ScheduledMatch, val result: Result)

/**
 * What a competition leaves behind the moment it closes, per section 5.5: the
 * date it closed on, which competition, what kind it was and its final
 * order. The kind and the order are carried here rather than read back off
 * the competition later, because a closed competition's own state does not
 * change again and a reader of season history should not have to hold a
 * reference to it to know what it decided.
 */
@SpecRef("5.5")
data class CompetitionClose(val date: CalendarDate, val key: String, val kind: CompetitionKind, val finalOrder: List<String>)

/**
 * What a later plan runs on every calendar Sunday of section 4.5: contract
 * expiry, market movement, evolution, and everything else this plan does not
 * build. NONE is what this plan passes everywhere, so a season plays a whole
 * year with every Sunday firing into a tick that changes nothing.
 */
@SpecRef("4.5")
fun interface WeeklyTick {
    fun apply(state: SeasonState, sunday: CalendarDate): SeasonState

    companion object {
        val NONE = WeeklyTick { state, _ -> state }
    }
}

/**
 * A whole season as a value, per section 1.10: every club's state, every
 * competition's state, the calendar it plays over, the history of what has
 * been played and closed so far, and a cursor into the calendar naming the
 * next date to play.
 *
 * number and year are carried apart because they answer different questions.
 * number is which season of a career this is, the count a promotion or a
 * prestige rule reads; year is which real calendar year the schedule is laid
 * over, which is what CalendarDate arithmetic needs and what the club's
 * contracts and market will read once those exist.
 *
 * seed is kept for the same reason World.seed is: a season that cannot say
 * where it came from cannot be reported as a bug. playRound rebuilds the
 * whole season rng root from this seed and number on every call rather than
 * carrying a live Rng on the state, because Rng is not a value: two SeasonState
 * instances holding the same generator object would not compare equal even
 * when they represent the same point in the same season, and equality is what
 * lets a test assert two replays of one seed are identical.
 *
 * dateIndex is the cursor and today reads it against the calendar; finished is
 * true once it has walked off the end. lastTick remembers the last Sunday the
 * weekly tick actually fired on, which is what lets playRound find exactly the
 * Sundays it has not yet fired without keeping a running list of every Sunday
 * of the year.
 *
 * reserves is the country reserve of section 1.12, one queue per country
 * whose league is active, keyed by country index: the clubs of that country
 * that sit outside every division, head first. Section 1.12 makes it a queue
 * carried from one season into the next rather than a fresh reading of club
 * levels, since the pyramid is built once at world creation and never
 * rebuilt: the last division's promoted clubs leave from the head and its
 * relegated clubs join the tail. It is data on the state for that reason, set
 * once by openingSeason and moved only by nextSeason, and by the build of the
 * Brazilian fourth division, which takes the clubs it seats out of Brazil's
 * queue.
 *
 * states is every state championship division's membership and every state
 * reserve queue of FORMAT-SPEC's state files, carried the same way: seeded
 * once by stateSetup for season one, moved at every turnover by
 * stateTurnover, and read by buildCompetitions to build the state
 * competitions of the season it belongs to.
 *
 * fourth is present while Brazil's fourth division is fed by the state
 * championships, section 1.9's special case: it is not seated by level but
 * built when the season's last state competition closes. It carries the
 * division's configured size and the door, the clubs the third division
 * relegated at the previous turnover, which head the division's queue.
 *
 * Equality holds all the way down for the reason given for seed: every
 * field is a value, down to each competition's qualification rule, which is
 * data, a Qualifiers value, rather than a function, since a function
 * compares by identity. Two seasons opened from one seed and played the
 * same way compare equal, competitions and state memberships included.
 */
@SpecRef("1.10")
data class SeasonState(
    val number: Int,
    val year: Int,
    val seed: Long,
    val dataset: WorldDataset,
    val clubs: Map<String, ClubState>,
    val competitions: Map<String, Competition>,
    val schedule: SeasonSchedule,
    val played: List<PlayedMatch>,
    val closed: List<CompetitionClose>,
    val dateIndex: Int,
    val lastTick: CalendarDate?,
    val reserves: Map<Int, List<String>>,
    val states: StateSetup,
    val fourth: BrazilianFourth?,
) {
    /** True once the cursor has walked past the last date the schedule laid. */
    @SpecRef("1.10")
    val finished: Boolean get() = dateIndex >= schedule.dates.size

    /** The next date to play, or null once the season has finished. */
    @SpecRef("1.10")
    val today: CalendarDate? get() = schedule.dates.getOrNull(dateIndex)

    fun club(key: String): ClubState = clubs[key] ?: throw IllegalArgumentException("no club $key in this season")

    fun withClub(state: ClubState): SeasonState = copy(clubs = clubs + (state.key to state))

    fun withCompetition(competition: Competition): SeasonState = copy(competitions = competitions + (competition.key to competition))
}

/**
 * The opening state of season one over a generated world: clubs fresh,
 * competitions built, schedule laid.
 *
 * Every club of the world starts with ClubState.fresh: no history, a clean
 * discipline record and full energy, per section 1.10's own account of what a
 * new season begins with. Its competitions are buildCompetitions' own, called
 * here with number fixed at one because this function only ever opens the
 * first season of a career; see that function's docstring for what
 * activeLeagues selects and how each competition's own rng stream is forked.
 * The country reserves of section 1.12 are seeded here, once, by
 * openingReserves, and the state memberships, once, by stateSetup.
 *
 * Section 1.9's special case of Brazil applies from season one: with the
 * state championships on, the fourth division is not filled by level. The
 * world generation does not know the state championships and seats a fourth
 * division by level all the same, so when Brazil is active, the option is on
 * and the pyramid seated a fourth division, its clubs are taken out of it
 * here: they stand without a division and join the tail of Brazil's reserve
 * queue in pyramid order, and the fourth is built, at its seated size, when
 * the season's last state competition closes, as in every later season
 * (OPEN-QUESTIONS item 113). Its dates are reserved in the schedule from its
 * configured shape meanwhile.
 */
@SpecRef("1.10")
fun openingSeason(world: World, dataset: WorldDataset, activeLeagues: Set<Int>, year: Int, seed: Long): SeasonState {
    val seated = world.clubs.map { ClubState.fresh(it) }
    val number = 1
    val worldRng = SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)
    val states = stateSetup(seated, dataset) { ref -> pyramidTiebreak(worldRng, ref) }
    val levelSeatedFourth = if (fedByStates(dataset, activeLeagues)) {
        seated.filter { it.country == Country.BRAZIL && it.standing == Standing.InDivision(BrazilianFourth.DIVISION) }
    } else {
        emptyList()
    }
    val fourth = if (levelSeatedFourth.isEmpty()) null else BrazilianFourth(size = levelSeatedFourth.size, door = emptyList())
    val movedOut = levelSeatedFourth.map { it.key }.toSet()
    val clubs = seated.map { if (it.key in movedOut) it.copy(standing = Standing.WithoutDivision) else it }
    val reserves = openingReserves(seated, dataset, activeLeagues, seed).toMutableMap()
    if (fourth != null) {
        val queue = requireNotNull(reserves[Country.BRAZIL]) { "Brazil seated a fourth division and holds no reserve queue" }
        reserves[Country.BRAZIL] = queue + pyramidOrder(levelSeatedFourth, worldRng)
    }
    val competitions = buildCompetitions(number, clubs, dataset, activeLeagues, seed, states)

    return SeasonState(
        number = number,
        year = year,
        seed = seed,
        dataset = dataset,
        clubs = clubs.associateBy { it.key },
        competitions = competitions.associateBy { it.key },
        schedule = SeasonSchedule.build(year, competitions + listOfNotNull(fourth?.reserved(dataset, number, seed))),
        played = emptyList(),
        closed = emptyList(),
        dateIndex = 0,
        lastTick = null,
        reserves = reserves,
        states = states,
        fourth = fourth,
    ).withBrazilianFourthIfDue()
}

/**
 * True when Brazil's fourth division, should one exist, is fed by the state
 * championships rather than seated by level, per section 1.9: Brazil's league
 * is active and the dataset's state championship option is on.
 */
@SpecRef("1.9")
internal fun fedByStates(dataset: WorldDataset, activeLeagues: Set<Int>): Boolean =
    Country.BRAZIL in activeLeagues && dataset.options.playStateChampionships

/**
 * The opening queue of every country reserve of section 1.12, one per country
 * of activeLeagues whose pyramid seated at least one division.
 *
 * Section 1.12 orders the reserve as section 1.9 orders the pyramid: level
 * descending, ties broken by the per club draw the world generation already
 * made (pyramidTiebreak, read off the world's own stream so the reserve
 * agrees with the pyramid that left these clubs without a division), and,
 * past that, by reference for a total order. This is the only place club
 * levels are read for the reserve: 1.12 says the pyramid is never rebuilt and
 * no level is read again after world creation, so every later season moves
 * this queue by the swap alone. A country whose league is active but whose
 * clubs were too few to seat any division has no pyramid, and no queue.
 */
@SpecRef("1.12")
internal fun openingReserves(clubs: List<ClubState>, dataset: WorldDataset, activeLeagues: Set<Int>, seed: Long): Map<Int, List<String>> {
    val worldRng = SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)
    val reserves = LinkedHashMap<Int, List<String>>()
    for (country in activeLeagues.sorted()) {
        if (leagueDivisions(country, clubs, dataset).isEmpty()) continue
        reserves[country] = pyramidOrder(clubs.filter { it.country == country && it.standing == Standing.WithoutDivision }, worldRng)
    }
    return reserves
}

/**
 * The given clubs in section 1.9's pyramid order: level descending, ties
 * broken by the per club draw the world generation made from worldRng, and,
 * past that, by reference for a total order.
 */
@SpecRef("1.9")
private fun pyramidOrder(clubs: List<ClubState>, worldRng: Rng): List<String> =
    clubs
        .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { pyramidTiebreak(worldRng, it.key) }.thenBy { it.key })
        .map { it.key }

/**
 * Builds every competition a season plays this year, per section 1.10: a
 * league competition per division and a national cup for every country of
 * activeLeagues that fields one, and a state championship competition per
 * division of states, the season's carried state memberships. Factored out of
 * openingSeason so that the turnover of section 1.12 can rebuild the
 * following season's competitions the same way, with a season number past
 * one and a club list that has already moved between divisions.
 *
 * A league division is read off the clubs' standings, so a Brazilian fourth
 * division fed by the state championships, whose clubs stand without a
 * division until the season's last state competition closes, is not built
 * here; withBrazilianFourthIfDue builds it then, from the same stream this
 * function would have forked for it.
 *
 * Every competition's own rng is forked off one season root by its own key,
 * seasonFixturesRoot(seed, number).fork(clubKey(key)), the pattern playRound
 * itself reads competition streams from.
 *
 * The cup and the state competitions are handed the dataset options they
 * would be shaped by, the novo formato option of 1.13 and the real groups
 * option of FORMAT-SPEC load rule six, only so each can say, in its
 * approximations, when the format the option asks for is not the one built.
 */
@SpecRef("1.10")
internal fun buildCompetitions(
    number: Int,
    clubs: List<ClubState>,
    dataset: WorldDataset,
    activeLeagues: Set<Int>,
    seed: Long,
    states: StateSetup,
): List<Competition> {
    val root = seasonFixturesRoot(seed, number)
    val competitions = ArrayList<Competition>()
    for (country in activeLeagues.sorted()) {
        leagueDivisions(country, clubs, dataset).forEach { division ->
            competitions += leagueCompetition(division, root.fork(clubKey("league:$country:${division.division}")))
        }
        nationalCup(country, clubs, dataset.options.newCupFormat, root.fork(clubKey("cup:$country")))?.let { competitions += it }
    }
    states.divisions.forEach { division ->
        competitions += stateCompetition(division, dataset.options.realStateGroups, root.fork(clubKey(stateCompetitionKey(division))))
    }
    return competitions
}

/**
 * The root every competition stream of one season forks from by the
 * competition's key: SplitMix64Rng(seed).fork(SeedDomain.SEASON).fork(number).fork(SeedDomain.FIXTURES).
 * It depends on the seed and the season number alone, so a competition built
 * in the middle of a season draws exactly what it would have drawn at the
 * season's start.
 */
@SpecRef("0")
internal fun seasonFixturesRoot(seed: Long, number: Int): Rng =
    SplitMix64Rng(seed).fork(SeedDomain.SEASON).fork(number.toLong()).fork(SeedDomain.FIXTURES)
