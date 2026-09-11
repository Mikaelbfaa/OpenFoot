package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.SpecRef

@SpecRef("1.10")
data class Slot(val date: CalendarDate, val competition: String)

/**
 * The calendar of a season, binding each round of each competition to a date.
 * This implements the policy INFERIDO under item 74, in which state
 * championships play twice a week from the first Sunday of January, national
 * leagues play Sundays from late March, and national cups play Wednesdays
 * every fortnight. The dates never interleave in a way that would place two
 * different competitions on the same date unless both are league rounds of the
 * same week, which keeps the invariant of item 72: no club plays more than one
 * match in a single day, whether in the state championship, the national league
 * or the national cup.
 */
@SpecRef("1.10")
data class SeasonSchedule(val year: Int, val start: CalendarDate, val slots: List<Slot>) {
    val dates: List<CalendarDate> get() = slots.map { it.date }.distinct().sorted()

    fun on(date: CalendarDate): List<String> = slots.filter { it.date == date }.map { it.competition }

    companion object {
        @SpecRef("1.10")
        fun build(year: Int, competitions: List<Competition>): SeasonSchedule {
            val start = CalendarDate.seasonStart(year)
            val end = CalendarDate(year, 12, 31)
            val slots = ArrayList<Slot>()
            for (competition in competitions) {
                val dates = datesFor(competition.kind, start).take(roundCount(competition)).toList()
                require(dates.size == roundCount(competition) && dates.all { it <= end }) {
                    "${competition.key} does not fit the year $year"
                }
                if (competition.kind == CompetitionKind.STATE) {
                    require(dates.lastOrNull()?.let { it < start.plusDays(DAYS_IN_WEEK * LEAGUE_FIRST_WEEK) } ?: true) {
                        "${competition.key} runs past the league start, and states must end before it"
                    }
                }
                dates.forEach { slots += Slot(it, competition.key) }
            }
            return SeasonSchedule(year, start, slots.sortedWith(compareBy<Slot> { it.date }.thenBy { it.competition }))
        }

        fun roundCount(competition: Competition): Int = competition.phases.sumOf { phase ->
            when (phase) {
                is Phase.League -> phase.phase.rounds.size
                is Phase.Knockout -> (0 until phase.phase.rounds).sumOf { if (phase.phase.twoLegged(it)) 2 else 1 }
            }
        }

        @SpecRef("1.10")
        private fun datesFor(kind: CompetitionKind, start: CalendarDate): Sequence<CalendarDate> = when (kind) {
            CompetitionKind.STATE -> generateSequence(0) { it + 1 }.map { i ->
                val week = i / 2
                start.plusDays(DAYS_IN_WEEK * week + if (i % 2 == 0) 0 else WEDNESDAY_OFFSET)
            }
            CompetitionKind.NATIONAL_LEAGUE -> generateSequence(LEAGUE_FIRST_WEEK) { it + 1 }.map { start.plusDays(DAYS_IN_WEEK * it) }
            CompetitionKind.NATIONAL_CUP -> generateSequence(LEAGUE_FIRST_WEEK) { it + CUP_FORTNIGHT }.map { start.plusDays(DAYS_IN_WEEK * it + WEDNESDAY_OFFSET) }
            else -> throw IllegalArgumentException("no schedule policy for $kind in this version")
        }
    }
}

@SpecRef("1.10")
private const val DAYS_IN_WEEK = 7

@SpecRef("1.10")
private const val WEDNESDAY_OFFSET = 3

@SpecRef("1.10")
private const val LEAGUE_FIRST_WEEK = 12

@SpecRef("1.10")
private const val CUP_FORTNIGHT = 2
