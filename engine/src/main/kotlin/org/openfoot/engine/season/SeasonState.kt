package org.openfoot.engine.season

import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.World
import org.openfoot.engine.world.clubKey
import org.openfoot.engine.world.pyramidTiebreak
import org.openfoot.model.CompetitionKind
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
 */
@SpecRef("1.10")
fun openingSeason(world: World, dataset: WorldDataset, activeLeagues: Set<Int>, year: Int, seed: Long): SeasonState {
    val clubs = world.clubs.map { ClubState.fresh(it) }
    val number = 1
    val competitions = buildCompetitions(number, clubs, dataset, activeLeagues, seed)

    return SeasonState(
        number = number,
        year = year,
        seed = seed,
        dataset = dataset,
        clubs = clubs.associateBy { it.key },
        competitions = competitions.associateBy { it.key },
        schedule = SeasonSchedule.build(year, competitions),
        played = emptyList(),
        closed = emptyList(),
        dateIndex = 0,
        lastTick = null,
    )
}

/**
 * Builds every competition a season plays this year, per section 1.10: a
 * league competition per division and a national cup for every country of
 * activeLeagues that fields one, and a state championship competition per
 * division the state file setup lays out. Factored out of openingSeason so
 * that the turnover of section 1.12 can rebuild the following season's
 * competitions the same way, with a season number past one and a club list
 * that has already moved between divisions.
 *
 * Every competition's own rng is forked off one season root by its own key,
 * SplitMix64Rng(seed).fork(SeedDomain.SEASON).fork(number).fork(SeedDomain.FIXTURES).fork(clubKey(key)),
 * the pattern playRound itself reads competition streams from. The pyramid's
 * own tie break stream, SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN), is kept
 * separate from that root on purpose: it is the same stream generateWorld
 * already drew the standings from, and stateSetup's tie break has to agree
 * with the pyramid's own ordering or the two would rank a state's clubs two
 * different ways from the same seed, in every season and not only the first.
 */
@SpecRef("1.10")
internal fun buildCompetitions(number: Int, clubs: List<ClubState>, dataset: WorldDataset, activeLeagues: Set<Int>, seed: Long): List<Competition> {
    val root = SplitMix64Rng(seed).fork(SeedDomain.SEASON).fork(number.toLong()).fork(SeedDomain.FIXTURES)
    val worldRng = SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)
    val competitions = ArrayList<Competition>()
    for (country in activeLeagues.sorted()) {
        leagueDivisions(country, clubs, dataset).forEach { division ->
            competitions += leagueCompetition(division, root.fork(clubKey("league:$country:${division.division}")))
        }
        nationalCup(country, clubs, root.fork(clubKey("cup:$country")))?.let { competitions += it }
    }
    stateSetup(clubs, dataset) { ref -> pyramidTiebreak(worldRng, ref) }.divisions.forEach { division ->
        competitions += stateCompetition(division, root.fork(clubKey("state:${division.state}:${division.division}")))
    }
    return competitions
}
