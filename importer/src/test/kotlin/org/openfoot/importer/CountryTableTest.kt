package org.openfoot.importer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the transcribed country table against the values SIMULATION-SPEC
 * section 4.4.1 publishes: the row count, a spread of spot checks covering
 * every level-20 country plus one level-19 country and the three reserved
 * indices, and the level scale the whole table is supposed to respect.
 */
class CountryTableTest {
    @Test
    fun `the table carries all two hundred and twenty four countries`() {
        assertEquals(224, CountryTable.rows.size)
    }

    @Test
    fun `spot checks against the published table hold`() {
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[3])
        assertEquals(CountryTable.Row(level = 20, continent = 1), CountryTable.rows[11])
        assertEquals(CountryTable.Row(level = 20, continent = 1), CountryTable.rows[29])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[65])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[72])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[97])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[104])
        assertEquals(CountryTable.Row(level = 19, continent = 0), CountryTable.rows[154])
        assertEquals(-1, CountryTable.rows[135]?.continent)
        assertEquals(-1, CountryTable.rows[204]?.continent)
        assertEquals(-1, CountryTable.rows[207]?.continent)
    }

    @Test
    fun `levels stay inside the scale the spec publishes`() {
        assertEquals(emptyList(), CountryTable.rows.filterValues { it.level !in 11..20 }.keys.toList())
    }
}
