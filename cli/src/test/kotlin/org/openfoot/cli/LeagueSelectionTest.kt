package org.openfoot.cli

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * parseLeagues turns the --leagues flag into the set generateWorld consumes,
 * so what matters here is name resolution and the two defaults: absent means
 * Brazil, and "all" means every country the dataset holds.
 */
class LeagueSelectionTest {

    private fun twoCountryDataset() = WorldDataset(
        countries = listOf(
            CountryEntry(index = 29, name = "BRA", level = 20),
            CountryEntry(index = 65, name = "ESP", level = 20),
        ),
        clubs = listOf(
            ClubEntry(ref = "um", name = "um", country = 29, level = 18, reputation = 4),
        ),
    )

    @Test
    fun `absent means brazil, matching the original's pre ticked box`() {
        assertEquals(setOf(Country.BRAZIL), parseLeagues(null, twoCountryDataset()))
    }

    @Test
    fun `named countries resolve by their dataset name`() {
        assertEquals(setOf(29, 65), parseLeagues("BRA,ESP", twoCountryDataset()))
    }

    @Test
    fun `all selects every country the dataset holds`() {
        assertEquals(setOf(29, 65), parseLeagues("all", twoCountryDataset()))
    }

    @Test
    fun `an unknown name is refused by name`() {
        assertFailsWith<CliError> { parseLeagues("BRA,XYZ", twoCountryDataset()) }
    }
}
