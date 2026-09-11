package org.openfoot.engine.season

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pure date arithmetic checked against dates whose weekday is a matter of
 * record, so that the season start of section 0 lands on a real Sunday on
 * every machine.
 */
class CalendarDateTest {

    @Test
    fun `known weekdays come out right`() {
        assertEquals(CalendarDate.SUNDAY, CalendarDate(2000, 1, 2).weekday)
        assertEquals(6, CalendarDate(2000, 1, 1).weekday, "the first of January 2000 was a Saturday")
        assertEquals(4, CalendarDate(2026, 1, 1).weekday, "the first of January 2026 is a Thursday")
        assertEquals(4, CalendarDate(1970, 1, 1).weekday, "the Unix epoch was a Thursday")
        assertEquals(4, CalendarDate(2024, 2, 29).weekday, "the leap day of 2024 was a Thursday")
    }

    @Test
    fun `the season starts on the first Sunday of January`() {
        assertEquals(CalendarDate(2026, 1, 4), CalendarDate.seasonStart(2026))
        assertEquals(CalendarDate(2023, 1, 1), CalendarDate.seasonStart(2023))
        assertEquals(CalendarDate(2027, 1, 3), CalendarDate.seasonStart(2027))
        assertTrue(CalendarDate.seasonStart(2028).isSunday)
    }

    @Test
    fun `the last Sunday of a year falls on or just before the thirty first of December`() {
        assertEquals(CalendarDate(2026, 12, 27), CalendarDate.lastSunday(2026))
        assertEquals(CalendarDate(2028, 12, 31), CalendarDate.lastSunday(2028))
        assertTrue(CalendarDate.lastSunday(2027).isSunday)
        assertTrue(CalendarDate.lastSunday(2027).plusDays(7).year == 2028)
    }

    @Test
    fun `adding days crosses months, years and leap days`() {
        assertEquals(CalendarDate(2024, 3, 1), CalendarDate(2024, 2, 28).plusDays(2))
        assertEquals(CalendarDate(2025, 3, 1), CalendarDate(2025, 2, 28).plusDays(1))
        assertEquals(CalendarDate(2027, 1, 1), CalendarDate(2026, 12, 31).plusDays(1))
        assertEquals(CalendarDate(2026, 12, 31), CalendarDate(2027, 1, 1).plusDays(-1))
        assertEquals(366, CalendarDate(2024, 1, 1).daysUntil(CalendarDate(2025, 1, 1)))
        assertEquals(-30, CalendarDate(2026, 5, 1).daysUntil(CalendarDate(2026, 4, 1)))
    }

    @Test
    fun `dates order and print plainly`() {
        assertTrue(CalendarDate(2026, 1, 4) < CalendarDate(2026, 1, 11))
        assertEquals("2026-01-04", CalendarDate(2026, 1, 4).toString())
        assertEquals(CalendarDate(2026, 7, 19), CalendarDate.fromOrdinal(CalendarDate(2026, 7, 19).ordinal))
        assertFailsWith<IllegalArgumentException> { CalendarDate(2025, 2, 29) }
        assertFailsWith<IllegalArgumentException> { CalendarDate(2026, 13, 1) }
    }
}
