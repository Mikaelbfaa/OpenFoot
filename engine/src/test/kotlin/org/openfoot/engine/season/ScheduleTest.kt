package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(38, SeasonSchedule.roundCount(league("l", twenty)))
        assertEquals(8, SeasonSchedule.roundCount(cup("k", twenty.take(16))))
        assertEquals(12, SeasonSchedule.roundCount(state("s", twenty.take(6))))
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
}
