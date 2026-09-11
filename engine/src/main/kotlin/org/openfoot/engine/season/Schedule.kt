package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.SpecRef

/**
 * One dated round of one competition: the calendar date of section 0 it is
 * played on and the key of the competition that plays it. The season
 * schedule is a flat list of these in date order, and a date holding several
 * of them is a day on which each of those competitions plays its next round,
 * which is how section 1.10 groups on one day the rounds of every
 * competition whose turn falls there.
 */
@SpecRef("1.10")
data class Slot(val date: CalendarDate, val competition: String)

/**
 * The calendar of a season, binding each dated round of each competition to
 * a date of the year. The number of dates a competition takes is its own
 * datedRounds, the same count its round counter closes it by, so the
 * calendar and the close cannot disagree.
 *
 * The weekday policy is the one INFERIDO under items 74 and 110. State
 * championships play twice a week, Sunday and Wednesday, from the first
 * Sunday of January, and must end before week twelve. National cups play
 * Wednesdays every fortnight from week twelve, and every country's cup takes
 * the same Wednesdays. National leagues start on the Sunday of week twelve.
 *
 * Section 1.10 confirms that every national league of the world shares the
 * same "Nacional" dates, so the league dates are one list built once per
 * season and every league plays its rounds on the first R dates of that
 * list, R being its own dated rounds. The list is the Sundays from week
 * twelve through the last Sunday of the year; when the longest league needs
 * more rounds than those Sundays, the extra rounds take, in chronological
 * order from week twelve, the Wednesdays that carry no cup date, and the
 * merged list is kept in date order. The overflow onto free Wednesdays is
 * item 110's bet, INFERIDO. A season whose longest league fits in the
 * Sundays plays its leagues on Sundays only.
 *
 * A competition that needs more dates than its policy has in the year, a
 * state championship past week twelve, a league past the Sundays and the
 * free Wednesdays, a cup past the last fortnightly Wednesday, is refused
 * with an IllegalArgumentException naming the competition, the rounds it
 * needs and the dates available, before any match of the season is played;
 * that refusal is item 110's bet too. Every date laid falls inside the year.
 *
 * The policies keep the invariant of item 72, no club plays twice in a day:
 * the states end before any league or cup date, a league Wednesday is never
 * a cup Wednesday, and two leagues sharing a date never share a club, since
 * a club sits in one division only.
 */
@SpecRef("1.10")
data class SeasonSchedule(val year: Int, val start: CalendarDate, val slots: List<Slot>) {
    val dates: List<CalendarDate> get() = slots.map { it.date }.distinct().sorted()

    fun on(date: CalendarDate): List<String> = slots.filter { it.date == date }.map { it.competition }

    companion object {
        @SpecRef("1.10")
        fun build(year: Int, competitions: List<Competition>): SeasonSchedule {
            val start = CalendarDate.seasonStart(year)
            val end = CalendarDate.seasonEnd(year)
            val stateDates = stateDates(start)
            val cupDates = cupDates(start, end)
            val cupDatesInUse = cupDates.take(longest(competitions, CompetitionKind.NATIONAL_CUP)).toSet()
            val leagueDates = leagueDates(start, end, cupDatesInUse, longest(competitions, CompetitionKind.NATIONAL_LEAGUE))
            val slots = ArrayList<Slot>()
            for (competition in competitions) {
                val available = when (competition.kind) {
                    CompetitionKind.STATE -> stateDates
                    CompetitionKind.NATIONAL_LEAGUE -> leagueDates
                    CompetitionKind.NATIONAL_CUP -> cupDates
                    else -> throw IllegalArgumentException("no schedule policy for ${competition.kind} in this version")
                }
                val rounds = competition.datedRounds
                require(rounds <= available.size) {
                    "${competition.key} needs $rounds rounds, and the $year calendar has ${available.size} dates available for it"
                }
                available.take(rounds).forEach { slots += Slot(it, competition.key) }
            }
            return SeasonSchedule(year, start, slots.sortedWith(compareBy<Slot> { it.date }.thenBy { it.competition }))
        }

        private fun longest(competitions: List<Competition>, kind: CompetitionKind): Int =
            competitions.filter { it.kind == kind }.maxOfOrNull { it.datedRounds } ?: 0

        private fun sunday(start: CalendarDate, week: Int): CalendarDate = start.plusDays(DAYS_IN_WEEK * week)

        private fun wednesday(start: CalendarDate, week: Int): CalendarDate = start.plusDays(DAYS_IN_WEEK * week + WEDNESDAY_OFFSET)

        /** The Sunday and the Wednesday of every week before the league's first week, in date order. */
        @SpecRef("1.10")
        private fun stateDates(start: CalendarDate): List<CalendarDate> =
            (0 until LEAGUE_FIRST_WEEK).flatMap { listOf(sunday(start, it), wednesday(start, it)) }

        /** Every other Wednesday from the league's first week, through the end of the year. */
        @SpecRef("1.10")
        private fun cupDates(start: CalendarDate, end: CalendarDate): List<CalendarDate> =
            generateSequence(LEAGUE_FIRST_WEEK) { it + CUP_FORTNIGHT }.map { wednesday(start, it) }.takeWhile { it <= end }.toList()

        /**
         * The shared league date list: every Sunday from the league's first
         * week through the end of the year, merged in date order with as many
         * Wednesdays outside cupDatesInUse, the earliest first, as the longest
         * league needs beyond those Sundays. When the longest league needs more
         * than all of them, the list holds every one and the refusal in build
         * reports its size.
         */
        @SpecRef("1.10")
        private fun leagueDates(start: CalendarDate, end: CalendarDate, cupDatesInUse: Set<CalendarDate>, longest: Int): List<CalendarDate> {
            val weeks = generateSequence(LEAGUE_FIRST_WEEK) { it + 1 }
            val sundays = weeks.map { sunday(start, it) }.takeWhile { it <= end }.toList()
            val wednesdays = weeks.map { wednesday(start, it) }.takeWhile { it <= end }
                .filter { it !in cupDatesInUse }
                .take(maxOf(0, longest - sundays.size))
                .toList()
            return (sundays + wednesdays).sorted()
        }
    }
}

/**
 * The seven day week of section 0, whose Sunday tick paces the season. The
 * schedule counts weeks from the season's first Sunday with it. This is
 * calendar arithmetic rather than a bet: every weekday choice built on it is
 * item 110's.
 */
@SpecRef("0")
private const val DAYS_IN_WEEK = 7

/**
 * Three days after Sunday, the Wednesday on which state championships play
 * their midweek round, national cups play, and a league plays the rounds the
 * Sundays cannot hold. The value is item 110's bet, INFERIDO, not read from
 * the original.
 */
@SpecRef("1.10")
private const val WEDNESDAY_OFFSET = 3

/**
 * The week, counted from nought at the season's first Sunday, whose Sunday
 * opens the national league and whose Wednesday opens the national cup, and
 * before which every state championship must end. The value is item 110's
 * bet, INFERIDO, standing in for the interleaving item 74 leaves open.
 */
@SpecRef("1.10")
private const val LEAGUE_FIRST_WEEK = 12

/**
 * The national cup plays every second Wednesday, one date a fortnight. The
 * value is item 110's bet, INFERIDO, not read from the original.
 */
@SpecRef("1.10")
private const val CUP_FORTNIGHT = 2
