package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
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

class RoundLoopTest {

    private fun squad(club: String) = buildList {
        add(WorldFixtures.player(name = "$club g1", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        add(WorldFixtures.player(name = "$club g2", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        repeat(3) { add(WorldFixtures.player(name = "$club z$it", position = Position.CENTREBACK, first = Trait.MARKING, second = Trait.TACKLING)) }
        repeat(3) { add(WorldFixtures.player(name = "$club l$it", position = Position.FULLBACK, first = Trait.PACE, second = Trait.CROSSING)) }
        repeat(6) { add(WorldFixtures.player(name = "$club m$it", position = Position.MIDFIELDER, first = Trait.PASSING, second = Trait.PLAYMAKING)) }
        repeat(4) { add(WorldFixtures.player(name = "$club a$it", position = Position.FORWARD, first = Trait.FINISHING, second = Trait.HEADING)) }
    }

    private val data = WorldFixtures.dataset(
        clubs = (1..12).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it), squad = squad("c$it")) },
    ).copy(leagues = listOf(LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true)))

    private fun opening(seed: Long) = openingSeason(generateWorld(data, seed, setOf(Country.BRAZIL)), data, setOf(Country.BRAZIL), 2026, seed)

    @Test
    fun `the opening season holds one division of ten, a cup of eight and a schedule for both`() {
        val state = opening(1)
        assertEquals(setOf("league:29:1", "cup:29"), state.competitions.keys)
        assertEquals(10, state.competitions.getValue("league:29:1").participants.size)
        assertEquals(8, state.competitions.getValue("cup:29").participants.size)
        assertEquals(12, state.clubs.size)
        assertEquals(CalendarDate.seasonStart(2026).plusDays(7 * 12), state.today)
    }

    @Test
    fun `a round plays every scheduled match, records results and moves the date`() {
        val before = opening(1)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        assertEquals(5, after.played.size)
        assertTrue(after.played.all { it.date == before.today && it.competition == "league:29:1" })
        assertEquals(1, after.dateIndex)
        val league = after.competitions.getValue("league:29:1")
        assertEquals(5, league.results[0].size)
        assertEquals(after.played.map { it.result }, league.results[0])
    }

    @Test
    fun `a match leaves its marks on the records of both clubs`() {
        val before = opening(2)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        val match = after.played.first()
        val home = after.club(match.result.home)
        assertTrue(home.records.count { it.appearances == 1 } >= 11, "the starters appeared")
        assertTrue(home.records.count { it.namedSinceTick } >= home.records.count { it.appearances == 1 }, "the bench was named")
        assertTrue(home.records.filter { it.appearances == 1 }.all { it.ratingCount == 1 })
        val away = after.club(match.result.away)
        assertTrue(home.records.sumOf { it.goals } <= match.result.homeGoals, "goals credit the scorers, own goals aside")
    }

    @Test
    fun `the post round recovers everyone of a competition that played, played or not`() {
        val before = opening(3)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        val rested = after.clubs.values.filter { it.standing is org.openfoot.engine.world.Standing.WithoutDivision }
        assertTrue(rested.all { club -> club.records.all { it.energy == 100 } }, "clubs outside the round are untouched")
        val played = after.club(after.played.first().result.home)
        assertTrue(played.records.all { it.energy in 1..100 })
    }

    /**
     * The round right after the first plays the cup's midweek fixture, three
     * days after the league's opening Sunday, and crosses no new Sunday on
     * its own; the round after that one lands back on a Sunday, the second
     * league round, which is where the next pending Sunday actually fires.
     */
    @Test
    fun `pending Sundays reach the tick in order before the day's matches`() {
        val before = opening(4)
        val seen = ArrayList<CalendarDate>()
        val tick = WeeklyTick { state, sunday -> seen += sunday; state }
        val after = playRound(before, RuleSets.CLASSIC, tick)
        assertEquals(13, seen.size, "the first league Sunday is week twelve, and every Sunday from the start fires once")
        assertTrue(seen.zipWithNext().all { (a, b) -> a.daysUntil(b) == 7 })
        assertEquals(before.today, seen.last())
        assertEquals(seen.last(), after.lastTick)
        val afterCup = playRound(after, RuleSets.CLASSIC, WeeklyTick { state, sunday -> seen += sunday; state })
        assertEquals(13, seen.size, "the cup's midweek date is not itself a Sunday and crosses none")
        val next = playRound(afterCup, RuleSets.CLASSIC, WeeklyTick { state, sunday -> seen += sunday; state })
        assertEquals(14, seen.size)
        assertEquals(next.today?.plusDays(-7), seen.last())
    }

    @Test
    fun `a whole season finishes, closes every competition and crediting prestige to the champions`() {
        val end = playSeason(opening(5), RuleSets.CLASSIC, WeeklyTick.NONE)
        assertTrue(end.finished)
        assertEquals(setOf("league:29:1", "cup:29"), end.closed.map { it.key }.toSet())
        assertTrue(end.competitions.values.all { it.finished })
        val leagueClose = end.closed.single { it.kind == CompetitionKind.NATIONAL_LEAGUE }
        assertEquals(10, leagueClose.finalOrder.size)
        val cupClose = end.closed.single { it.kind == CompetitionKind.NATIONAL_CUP }
        fun cupPrize(key: String): Long = when (key) {
            cupClose.finalOrder[0] -> 300L
            cupClose.finalOrder[1] -> 50L
            else -> 0L
        }
        val champion = leagueClose.finalOrder[0]
        val runnerUp = leagueClose.finalOrder[1]
        assertEquals(500 + cupPrize(champion), end.club(champion).prestige.balance)
        assertEquals(90 + cupPrize(runnerUp), end.club(runnerUp).prestige.balance)
        assertEquals(45 + 14, end.played.size, "forty five league matches and a cup of eight over two legs")
    }

    @Test
    fun `the same seed plays the same season`() {
        val once = playSeason(opening(6), RuleSets.CLASSIC, WeeklyTick.NONE)
        val twice = playSeason(opening(6), RuleSets.CLASSIC, WeeklyTick.NONE)
        assertEquals(once.played, twice.played)
        assertEquals(once.clubs.mapValues { it.value.records }, twice.clubs.mapValues { it.value.records })
    }

    /**
     * The controller ruling for this task tests postRound through its own
     * internal seam rather than steering a whole simulated cup to an actual
     * elimination: doing that reliably, against a randomly decided knockout,
     * would take well past the forty line budget the ruling allows, for a
     * fact the pure function already proves on its own. A club with no match
     * today (appeared null, an eliminated cup participant on a round the cup
     * still plays) keeps a suspended man suspended, since served() is never
     * called on him, while every man still recovers energy, since recovery
     * reads only the age and the played flag and never the discipline.
     */
    @Test
    fun `a club with no match today keeps a suspension but still recovers energy`() {
        val club = opening(8).club("c01")
        val tired = club.withRecord(0) { it.copy(energy = 40, discipline = DisciplineRecord(yellows = 3)) }
        val afterBye = tired.postRound(appeared = null)
        assertTrue(afterBye.records[0].discipline.suspended, "no match today, so nothing serves the suspension")
        assertTrue(afterBye.records[0].energy > 40, "a bye still recovers energy")

        val afterPlayed = tired.postRound(appeared = emptySet())
        assertTrue(!afterPlayed.records[0].discipline.suspended, "the club played, so the suspended man serves")
    }
}
