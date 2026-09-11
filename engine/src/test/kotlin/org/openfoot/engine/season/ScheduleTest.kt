package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScheduleTest {

    private fun league(key: String, clubs: List<String>, turns: Int = 2) = Competition(
        key, CompetitionKind.NATIONAL_LEAGUE, 29, 1,
        listOf(Phase.League(RoundRobinPhase.single(clubs, turns))), { _, _ -> emptyList() }, listOf(emptyList()), 0, 0,
    )

    private fun cup(key: String, clubs: List<String>) = Competition(
        key, CompetitionKind.NATIONAL_CUP, 29, null,
        listOf(Phase.Knockout(KnockoutPhase(clubs.mapIndexed { i, c -> Entrant(c, i + 1) }, List(7) { true }, true))),
        { _, _ -> emptyList() }, listOf(emptyList()), 0, 0,
    )

    private fun state(key: String, clubs: List<String>) = Competition(
        key, CompetitionKind.STATE, 29, 1,
        listOf(Phase.League(RoundRobinPhase.single(clubs, 2)), Phase.Knockout(KnockoutPhase(emptyList(), listOf(true, true, true), true, field = 2))),
        { phase, results -> phase.overallTable(results).take(2).mapIndexed { i, r -> Entrant(r.key, i + 1) } }, listOf(emptyList()), 0, 0,
    )

    private val twenty = (1..20).map { "c$it" }

    @Test
    fun `round counts add league rounds and knockout legs`() {
        assertEquals(38, league("l", twenty).datedRounds)
        assertEquals(8, cup("k", twenty.take(16)).datedRounds)
        assertEquals(12, state("s", twenty.take(6)).datedRounds)
    }

    @Test
    fun `leagues play Sundays from late March, cups Wednesdays every fortnight, states twice a week from January`() {
        val schedule = SeasonSchedule.build(2026, listOf(league("l", twenty), cup("k", twenty.take(16)), state("s", twenty.take(6))))
        val start = CalendarDate.seasonStart(2026)
        assertEquals(start, schedule.start)
        val leagueDates = schedule.slots.filter { it.competition == "l" }.map { it.date }
        assertEquals(38, leagueDates.size)
        assertEquals(start.plusDays(7 * 12), leagueDates.first())
        assertTrue(leagueDates.all { it.isSunday })
        val cupDates = schedule.slots.filter { it.competition == "k" }.map { it.date }
        assertEquals(start.plusDays(7 * 12 + 3), cupDates.first())
        assertEquals(14, cupDates[0].daysUntil(cupDates[1]))
        val stateDates = schedule.slots.filter { it.competition == "s" }.map { it.date }
        assertEquals(start, stateDates[0])
        assertEquals(start.plusDays(3), stateDates[1])
        assertEquals(start.plusDays(7), stateDates[2])
    }

    @Test
    fun `no competition of one country shares a date with another of a different weekday policy`() {
        val schedule = SeasonSchedule.build(2026, listOf(league("l", twenty), cup("k", twenty.take(16)), state("s", twenty.take(6))))
        for (date in schedule.dates) {
            val keys = schedule.on(date)
            assertTrue(keys.size == 1 || keys.toSet() == setOf("l"), "$date holds $keys")
        }
        assertTrue(schedule.dates.zipWithNext().all { (a, b) -> a < b })
    }

    private fun sides(count: Int) = (1..count).map { "s$it" }

    private val standardCup get() = cup("k", twenty.take(16))

    /**
     * The calendar of 2026, worked out by hand. The season starts on Sunday
     * 4 January; week twelve's Sunday is 29 March and the last Sunday of the
     * year is 27 December, forty league Sundays. Week twelve's Wednesday is
     * 1 April and the last Wednesday of the year is 30 December, forty
     * Wednesdays, of which a sixteen side cup played over two legs a round
     * takes eight, weeks twelve, fourteen and so on to twenty six. That
     * leaves thirty two free Wednesdays and seventy two league dates.
     */
    private val sundays2026 = 40
    private val leagueCapacity2026 = 72

    private fun assertRefusedByName(error: IllegalArgumentException, key: String, rounds: Int, available: Int) {
        val message = error.message ?: ""
        assertTrue(key in message, message)
        assertTrue("needs $rounds rounds" in message, message)
        assertTrue("$available dates available" in message, message)
    }

    /**
     * Every even club count from ten to thirty six (the round robin of
     * section 1.3 builds even leagues only) and every turn count the dataset
     * allows, one to four, played as the only league of the season beside a
     * sixteen side cup: the league either gets a valid calendar, strictly
     * increasing dates inside the year and none on a cup date, or is refused
     * with the message that names it, and which of the two happens is decided
     * by the seventy two dates the year has for a league.
     */
    @Test
    fun `every league shape the dataset allows is scheduled or refused by name`() {
        val end = CalendarDate.seasonEnd(2026)
        for (clubs in 10..36 step 2) {
            for (turns in 1..4) {
                val league = league("l", sides(clubs), turns)
                val rounds = (clubs - 1) * turns
                if (rounds > leagueCapacity2026) {
                    val error = assertFailsWith<IllegalArgumentException> { SeasonSchedule.build(2026, listOf(league, standardCup)) }
                    assertRefusedByName(error, "l", rounds, leagueCapacity2026)
                    continue
                }
                val schedule = SeasonSchedule.build(2026, listOf(league, standardCup))
                val leagueDates = schedule.slots.filter { it.competition == "l" }.map { it.date }
                val cupDates = schedule.slots.filter { it.competition == "k" }.map { it.date }.toSet()
                assertEquals(rounds, leagueDates.size, "$clubs clubs, $turns turns")
                assertTrue(leagueDates.zipWithNext().all { (a, b) -> a < b }, "$clubs clubs, $turns turns")
                assertTrue(leagueDates.all { it.year == 2026 && it <= end }, "$clubs clubs, $turns turns")
                assertTrue(leagueDates.none { it in cupDates }, "$clubs clubs, $turns turns")
            }
        }
    }

    @Test
    fun `a twenty two and a twenty four club league fit, their extra dates free Wednesdays`() {
        val first = league("l1", sides(22))
        val second = league("l2", (1..24).map { "t$it" })
        val schedule = SeasonSchedule.build(2026, listOf(first, second, standardCup))
        val cupDates = schedule.slots.filter { it.competition == "k" }.map { it.date }.toSet()
        val firstDates = schedule.slots.filter { it.competition == "l1" }.map { it.date }
        val secondDates = schedule.slots.filter { it.competition == "l2" }.map { it.date }
        assertEquals(42, firstDates.size)
        assertEquals(46, secondDates.size)
        assertEquals(secondDates.take(42), firstDates)
        assertEquals(sundays2026, secondDates.count { it.isSunday })
        val extra = secondDates.filterNot { it.isSunday }
        assertEquals(6, extra.size)
        assertTrue(extra.all { it.weekday == 3 && it !in cupDates }, "$extra")
        assertEquals(CalendarDate(2026, 4, 8), extra.first())
        assertTrue(secondDates.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `a world whose longest league fits in the Sundays plays Sundays only`() {
        val schedule = SeasonSchedule.build(2026, listOf(league("l1", twenty), league("l2", (1..16).map { "t$it" }), standardCup))
        val dates = schedule.slots.filter { it.competition.startsWith("l") }.map { it.date }
        assertTrue(dates.all { it.isSunday })
    }

    @Test
    fun `a state championship that would run past week twelve is refused by name`() {
        val error = assertFailsWith<IllegalArgumentException> { SeasonSchedule.build(2026, listOf(state("s", sides(14)))) }
        assertRefusedByName(error, "s", 28, 24)
    }

    @Test
    fun `a league beyond the year's capacity is refused by name`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SeasonSchedule.build(2026, listOf(league("l", sides(36), turns = 4), standardCup))
        }
        assertRefusedByName(error, "l", 140, leagueCapacity2026)
    }

    @Test
    fun `a cup beyond the year's capacity is refused by name`() {
        val huge = Competition(
            "big", CompetitionKind.NATIONAL_CUP, 29, null,
            listOf(Phase.Knockout(KnockoutPhase(emptyList(), List(11) { true }, true, field = 2048))),
            { _, _ -> emptyList() }, listOf(emptyList()), 0, 0,
        )
        val error = assertFailsWith<IllegalArgumentException> { SeasonSchedule.build(2026, listOf(huge)) }
        assertRefusedByName(error, "big", 22, 20)
    }

    @Test
    fun `a competition built after the schedule keeps the first of its reserved dates and hands the rest back`() {
        val reserved = league("league:29:4", twenty.take(8), turns = 1)
        val schedule = SeasonSchedule.build(2026, listOf(league("l", twenty), reserved))
        val slots = schedule.slots.filter { it.competition == "league:29:4" }
        assertEquals(7, slots.size)
        val fitted = schedule.fitted(league("league:29:4", twenty.take(6), turns = 1))
        assertEquals(slots.take(5), fitted.slots.filter { it.competition == "league:29:4" })
        assertEquals(schedule.slots.filter { it.competition == "l" }, fitted.slots.filter { it.competition == "l" })
        val refused = assertFailsWith<IllegalArgumentException> { schedule.fitted(league("league:29:4", twenty.take(10), turns = 1)) }
        assertTrue(refused.message!!.contains("league:29:4"), refused.message)
        assertTrue(refused.message!!.contains("9 rounds"), refused.message)
    }
}
