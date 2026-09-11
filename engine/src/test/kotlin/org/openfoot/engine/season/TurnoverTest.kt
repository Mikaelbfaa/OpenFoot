package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.engine.world.pyramidTiebreak
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.SeedDomain
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TurnoverTest {

    private fun squad(club: String) = buildList {
        add(WorldFixtures.player(name = "$club g1", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        add(WorldFixtures.player(name = "$club g2", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        repeat(3) { add(WorldFixtures.player(name = "$club z$it", position = Position.CENTREBACK, first = Trait.MARKING, second = Trait.TACKLING)) }
        repeat(3) { add(WorldFixtures.player(name = "$club l$it", position = Position.FULLBACK, first = Trait.PACE, second = Trait.CROSSING)) }
        repeat(6) { add(WorldFixtures.player(name = "$club m$it", position = Position.MIDFIELDER, first = Trait.PASSING, second = Trait.PLAYMAKING)) }
        repeat(4) { add(WorldFixtures.player(name = "$club a$it", position = Position.FORWARD, first = Trait.FINISHING, second = Trait.HEADING)) }
    }

    /** Twenty two clubs: ten in the first division, ten in the second, two in the reserve. */
    private val data = WorldFixtures.dataset(
        clubs = (1..22).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = minOf(20, maxOf(6, 21 - it / 2)), squad = squad("c$it")) },
    ).copy(
        leagues = listOf(
            LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true),
            LeagueConfigEntry(country = Country.BRAZIL, division = 2, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true),
        ),
    )

    private fun closedSeason(seed: Long): SeasonState {
        val opening = openingSeason(generateWorld(data, seed, setOf(Country.BRAZIL)), data, setOf(Country.BRAZIL), 2026, seed)
        return playSeason(opening, RuleSets.CLASSIC, WeeklyTick.NONE)
    }

    @Test
    fun `swaps read the closed tables, two boundaries and the reserve`() {
        val end = closedSeason(1)
        val swaps = divisionSwaps(end, Country.BRAZIL)
        assertEquals(listOf(1, 2), swaps.map { it.upper })
        val first = end.closed.single { it.key == "league:29:1" }.finalOrder
        val second = end.closed.single { it.key == "league:29:2" }.finalOrder
        assertEquals(first.takeLast(2), swaps[0].relegated)
        assertEquals(second.take(2), swaps[0].promoted)
        assertEquals(second.takeLast(2), swaps[1].relegated)
        assertEquals(2, swaps[1].promoted.size)
        assertTrue(swaps[1].promoted.all { end.club(it).standing is Standing.WithoutDivision })
    }

    @Test
    fun `the next season moves the clubs, decays prestige and starts clean`() {
        val end = closedSeason(2)
        val next = nextSeason(end, setOf(Country.BRAZIL), RuleSets.CLASSIC)
        val swaps = divisionSwaps(end, Country.BRAZIL)
        assertEquals(2, next.number)
        assertEquals(2027, next.year)
        swaps[0].relegated.forEach { assertEquals(Standing.InDivision(2), next.club(it).standing) }
        swaps[0].promoted.forEach { assertEquals(Standing.InDivision(1), next.club(it).standing) }
        swaps[1].promoted.forEach { assertEquals(Standing.InDivision(2), next.club(it).standing) }
        swaps[1].relegated.forEach { assertEquals(Standing.WithoutDivision, next.club(it).standing) }
        val champion = end.closed.single { it.key == "league:29:1" }.finalOrder[0]
        assertEquals(end.club(champion).prestige.decayed(inLeague = true).promoted(), next.club(champion).prestige)
        assertTrue(next.clubs.values.all { club -> club.records.all { it.appearances == 0 && it.goals == 0 && it.energy == 100 } })
        assertTrue(next.played.isEmpty() && next.closed.isEmpty() && next.dateIndex == 0)
        assertEquals(10, next.competitions.getValue("league:29:1").participants.size)
        assertTrue(next.competitions.getValue("league:29:1").participants.containsAll(swaps[0].promoted))
    }

    /**
     * Two divisions of ten over a reserve of the given size: the twenty
     * divisioned clubs sit on levels twenty down to eleven, and every reserve
     * club on level six, so a club relegated into the reserve always outranks
     * every club already waiting there, which is what a queue must ignore.
     */
    private fun twoDivisions(reserve: Int) = WorldFixtures.dataset(
        clubs = (1..20 + reserve).map { index ->
            val ref = "p${index.toString().padStart(2, '0')}"
            WorldFixtures.club(ref = ref, level = if (index <= 20) 20 - (index - 1) / 2 else 6, squad = squad(ref))
        },
    ).copy(
        leagues = listOf(
            LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true),
            LeagueConfigEntry(country = Country.BRAZIL, division = 2, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true),
        ),
    )

    private fun opening(dataset: WorldDataset, seed: Long) =
        openingSeason(generateWorld(dataset, seed, setOf(Country.BRAZIL)), dataset, setOf(Country.BRAZIL), 2026, seed)

    private fun play(state: SeasonState) = playSeason(state, RuleSets.CLASSIC, WeeklyTick.NONE)

    private fun turn(state: SeasonState) = nextSeason(state, setOf(Country.BRAZIL), RuleSets.CLASSIC)

    @Test
    fun `a reserve of one relegates the last placed club and keeps the second last up`() {
        val end = play(opening(twoDivisions(reserve = 1), 3))
        val second = end.closed.single { it.key == "league:29:2" }.finalOrder
        val swap = divisionSwaps(end, Country.BRAZIL).last()
        assertEquals(listOf(second.last()), swap.relegated)
        assertEquals(1, swap.promoted.size)
        val next = turn(end)
        assertEquals(Standing.WithoutDivision, next.club(second.last()).standing)
        assertEquals(Standing.InDivision(2), next.club(second[second.size - 2]).standing)
        assertEquals(Standing.InDivision(2), next.club(swap.promoted.single()).standing)
    }

    @Test
    fun `a strong relegated club waits at the tail of the queue behind the clubs already there`() {
        val start = opening(twoDivisions(reserve = 4), 4)
        val waiting = start.clubs.values.filter { it.standing == Standing.WithoutDivision }.map { it.key }.toSet()
        val end1 = play(start)
        val first = divisionSwaps(end1, Country.BRAZIL).last()
        val end2 = play(turn(end1))
        val second = divisionSwaps(end2, Country.BRAZIL).last()
        assertEquals(waiting - first.promoted.toSet(), second.promoted.toSet())
        assertTrue(first.relegated.none { it in second.promoted })
        val third = turn(end2)
        first.relegated.forEach { assertEquals(Standing.WithoutDivision, third.club(it).standing) }
        assertEquals(start.reserves.getValue(Country.BRAZIL).drop(2), second.promoted)
        assertEquals(first.relegated + second.relegated, third.reserves.getValue(Country.BRAZIL))
    }

    @Test
    fun `the opening reserve queue is the pyramid's own order of the clubs it left without a division`() {
        val start = opening(twoDivisions(reserve = 4), 6)
        val worldRng = SplitMix64Rng(6).fork(SeedDomain.WORLDGEN)
        val expected = start.clubs.values
            .filter { it.standing == Standing.WithoutDivision }
            .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { pyramidTiebreak(worldRng, it.key) }.thenBy { it.key })
            .map { it.key }
        assertEquals(4, expected.size)
        assertEquals(mapOf(Country.BRAZIL to expected), start.reserves)
    }

    @Test
    fun `three seasons keep every club in exactly one place and every division its size`() {
        val data = twoDivisions(reserve = 4)
        var state = opening(data, 7)
        repeat(3) {
            val divisions = leagueDivisions(Country.BRAZIL, state.clubs.values.toList(), data)
            assertEquals(listOf(10, 10), divisions.map { it.clubs.size })
            val reserve = state.reserves.getValue(Country.BRAZIL)
            assertEquals(4, reserve.size)
            assertEquals(state.clubs.keys.sorted(), (divisions.flatMap { it.clubs } + reserve).sorted())
            assertEquals(state.clubs.values.filter { it.standing == Standing.WithoutDivision }.map { it.key }.toSet(), reserve.toSet())
            state = turn(play(state))
        }
    }

    /**
     * Four Brazilian divisions of ten over a reserve of four, with no club
     * belonging to a state, so the fourth division is rebuilt at the turnover
     * from an empty state champions' queue and drops most of its members.
     */
    private fun fourDivisions() = WorldFixtures.dataset(
        clubs = (1..44).map { index ->
            val ref = "b${index.toString().padStart(2, '0')}"
            WorldFixtures.club(ref = ref, level = if (index <= 40) 20 - (index - 1) / 4 else 6, squad = squad(ref))
        },
    ).copy(
        leagues = (1..4).map {
            LeagueConfigEntry(country = Country.BRAZIL, division = it, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true)
        },
    )

    @Test
    fun `clubs the Brazilian fourth division drops join the tail of the reserve queue in final order`() {
        val data = fourDivisions()
        assertTrue(data.options.playStateChampionships)
        val start = opening(data, 8)
        val end = play(start)
        val fourth = end.closed.single { it.key == "league:29:4" }.finalOrder
        val next = turn(end)
        val dropped = fourth.filter { next.club(it).standing == Standing.WithoutDivision }
        assertTrue(dropped.isNotEmpty())
        assertEquals(start.reserves.getValue(Country.BRAZIL) + dropped, next.reserves.getValue(Country.BRAZIL))
        assertEquals(next.clubs.values.filter { it.standing == Standing.WithoutDivision }.map { it.key }.toSet(), next.reserves.getValue(Country.BRAZIL).toSet())
    }

    @Test
    fun `the turnover refuses a season whose Sundays were not all fired`() {
        var stepped = opening(twoDivisions(reserve = 2), 5)
        while (!stepped.finished) stepped = playRound(stepped, RuleSets.CLASSIC, WeeklyTick.NONE)
        val refused = assertFailsWith<IllegalArgumentException> { turn(stepped) }
        assertTrue(refused.message!!.contains("Sundays"), refused.message)
        assertTrue(refused.message!!.contains("playSeason"), refused.message)
        assertEquals(2, turn(play(opening(twoDivisions(reserve = 2), 5))).number)
    }

    @Test
    fun `the state champions queue walks the tiers place by place`() {
        val closes = listOf(25, 18, 17, 8, 24, 7).map { state ->
            CompetitionClose(CalendarDate(2026, 4, 5), "state:$state:1", CompetitionKind.STATE, listOf("$state-1", "$state-2", "$state-3"))
        }
        val stateOf: (String) -> Int? = { it.substringBefore('-').toInt() }
        val queue = stateChampionsQueue(closes, stateOf, size = 10)
        assertEquals(listOf("25-1", "18-1", "17-1", "8-1", "24-1", "7-1", "25-2", "18-2", "25-3", "18-3"), queue)
        assertEquals(18, stateChampionsQueue(closes, stateOf, size = 40).size)
    }

    @Test
    fun `the fourth division rebuild skips divisioned and already chosen clubs`() {
        val relegatedOfThird = listOf("r1", "r2")
        val queue = listOf("d1", "r1", "q1", "q2", "q3", "q4")
        val excluded = setOf("d1")
        val members = rebuiltFourth(relegatedOfThird, queue, excluded, size = 5)
        assertEquals(listOf("r1", "r2", "q1", "q2", "q3"), members)
    }

    @Test
    fun `a short queue leaves the fourth division short rather than failing`() {
        val members = rebuiltFourth(listOf("r1"), listOf("q1"), emptySet(), size = 5)
        assertEquals(listOf("r1", "q1"), members)
    }
}
