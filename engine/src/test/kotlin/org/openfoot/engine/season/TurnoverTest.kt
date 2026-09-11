package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
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
