package org.openfoot.engine.season

import org.openfoot.model.SpecRef

/**
 * A calendar date, as section 0 needs it: the season is a list of real days
 * from the first of January to the thirty first of December, rounds sit on
 * days, injuries and contracts expire on dates, and the evolution tick fires
 * on Sundays.
 *
 * The engine may not read the platform's clock or its calendar library, so
 * the arithmetic is done here on the proleptic Gregorian calendar with day
 * numbers counted from an epoch. Everything is integer arithmetic on the
 * year, the month and the day, which is exactly what makes the same date
 * fall on the same weekday on every machine.
 */
@SpecRef("0")
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {
    init {
        require(month in 1..MONTHS) { "month $month" }
        require(day in 1..daysInMonth(year, month)) { "day $day of month $month in $year" }
    }

    /** Days since the epoch, the first of January of the year nought, for arithmetic between dates. */
    val ordinal: Int get() = daysBeforeYear(year) + daysBeforeMonth(year, month) + day - 1

    val isSunday: Boolean get() = weekday == SUNDAY

    /** Nought for Sunday through six for Saturday. */
    val weekday: Int get() = Math.floorMod(ordinal + EPOCH_WEEKDAY, DAYS_IN_WEEK)

    fun plusDays(days: Int): CalendarDate = fromOrdinal(ordinal + days)

    /** Whole days from this date until the other; negative when the other is earlier. */
    fun daysUntil(other: CalendarDate): Int = other.ordinal - ordinal

    override fun compareTo(other: CalendarDate): Int = ordinal.compareTo(other.ordinal)

    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {
        const val SUNDAY = 0

        /**
         * The first Sunday of January, which section 0 makes the start of a
         * season in the given year.
         */
        @SpecRef("0")
        fun seasonStart(year: Int): CalendarDate {
            val first = CalendarDate(year, 1, 1)
            return first.plusDays(Math.floorMod(SUNDAY - first.weekday, DAYS_IN_WEEK))
        }

        fun fromOrdinal(ordinal: Int): CalendarDate {
            var year = ordinal / AVERAGE_YEAR_LENGTH
            while (daysBeforeYear(year) > ordinal) year--
            while (daysBeforeYear(year + 1) <= ordinal) year++
            var remaining = ordinal - daysBeforeYear(year)
            var month = 1
            while (remaining >= daysInMonth(year, month)) {
                remaining -= daysInMonth(year, month)
                month++
            }
            return CalendarDate(year, month, remaining + 1)
        }

        fun isLeap(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        fun daysInYear(year: Int): Int = if (isLeap(year)) 366 else 365

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeap(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun daysBeforeYear(year: Int): Int {
            val y = year - 1
            return if (year >= 1) {
                y * 365 + y / 4 - y / 100 + y / 400 + 1
            } else {
                0
            }
        }

        private fun daysBeforeMonth(year: Int, month: Int): Int =
            (1 until month).sumOf { daysInMonth(year, it) }

        private const val MONTHS = 12
        private const val DAYS_IN_WEEK = 7

        /**
         * Calibrates the day count to the week: the second of January 2000
         * was a Sunday and is day 730121 of this count, seven times 104303,
         * so the count's multiples of seven are Sundays and no offset is needed.
         * CalendarDateTest pins that date and three others of record.
         */
        private const val EPOCH_WEEKDAY = 0

        private const val AVERAGE_YEAR_LENGTH = 365
    }
}
