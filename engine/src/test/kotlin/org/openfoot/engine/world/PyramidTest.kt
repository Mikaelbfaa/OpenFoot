package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PyramidTest {

    private fun club(ref: String, country: Int, level: Int) = ClubEntry(
        ref = ref,
        name = ref,
        country = country,
        level = level,
        reputation = 3,
        stadium = "st",
        capacity = 1000,
        coach = "c",
        coachCountry = country,
        squad = emptyList(),
    )

    private fun dataset(clubs: List<ClubEntry>, leagues: List<LeagueConfigEntry> = emptyList()) =
        WorldDataset(
            countries = clubs.map { it.country }.distinct().map {
                CountryEntry(index = it, name = "pais $it", level = 15, continent = 1)
            },
            clubs = clubs,
            leagues = leagues,
        )

    private fun rng() = SplitMix64Rng(42L).fork(org.openfoot.model.SeedDomain.WORLDGEN)

    @Test
    fun `an active country of twenty two clubs gets one division of twenty and two leftovers`() {
        val clubs = (1..22).map { club("c$it", country = 50, level = 20 - it % 10) }
        val standings = assemblePyramids(dataset(clubs), activeLeagues = setOf(50), rng())
        assertEquals(22, standings.size)
        assertEquals(20, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(2, standings.values.count { it == Standing.WithoutDivision })
    }

    @Test
    fun `a configuration that fits overrides the default step`() {
        val clubs = (1..22).map { club("c$it", country = 50, level = 20 - it % 10) }
        val config = LeagueConfigEntry(
            country = 50, division = 1, teamCount = 12,
            relegated = 2, turns = 3, penaltiesTiebreak = false,
        )
        val standings = assemblePyramids(dataset(clubs, listOf(config)), setOf(50), rng())
        assertEquals(12, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(10, standings.values.count { it == Standing.InDivision(2) })
    }

    @Test
    fun `a configuration too large for the remaining clubs falls back to the default`() {
        val clubs = (1..15).map { club("c$it", country = 50, level = 10) }
        val config = LeagueConfigEntry(
            country = 50, division = 1, teamCount = 20,
            relegated = 4, turns = 2, penaltiesTiebreak = false,
        )
        val standings = assemblePyramids(dataset(clubs, listOf(config)), setOf(50), rng())
        assertEquals(14, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(1, standings.values.count { it == Standing.WithoutDivision })
    }

    @Test
    fun `below ten clubs no further division is created`() {
        val clubs = (1..29).map { club("c$it", country = 50, level = 10) }
        val standings = assemblePyramids(dataset(clubs), setOf(50), rng())
        assertEquals(20, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(9, standings.values.count { it == Standing.WithoutDivision })
    }

    @Test
    fun `a country that is not active keeps its top fifteen on the reputation path`() {
        // ClubEntry.LEVEL_RANGE is 6..20, so level cannot equal it for it in 1..5.
        // c1 gets the unique minimum and c20 the unique maximum; everyone else
        // ties in the middle, which keeps the two assertions below true no
        // matter how the seed-derived tie break orders the tied group.
        val clubs = (1..20).map { club("c$it", country = 60, level = if (it == 20) 20 else if (it == 1) 6 else 12) }
        val standings = assemblePyramids(dataset(clubs), activeLeagues = emptySet(), rng())
        assertEquals(15, standings.size)
        assertTrue(standings.values.all { it == Standing.ByReputation })
        assertTrue(standings.containsKey("c20"))
        assertFalse(standings.containsKey("c1"))
    }

    @Test
    fun `a country below the candidate threshold is never a league even when asked`() {
        val clubs = (1..9).map { club("c$it", country = 60, level = 10) }
        val standings = assemblePyramids(dataset(clubs), activeLeagues = setOf(60), rng())
        assertEquals(9, standings.size)
        assertTrue(standings.values.all { it == Standing.ByReputation })
    }

    @Test
    fun `the five big countries need sixteen clubs to be a league`() {
        val germany = 3
        val clubs = (1..15).map { club("c$it", country = germany, level = 10) }
        val standings = assemblePyramids(dataset(clubs), setOf(germany), rng())
        assertTrue(standings.values.all { it == Standing.ByReputation })

        val sixteen = (1..16).map { club("d$it", country = germany, level = 10) }
        val active = assemblePyramids(dataset(sixteen), setOf(germany), rng())
        assertEquals(16, active.values.count { it == Standing.InDivision(1) })
    }

    @Test
    fun `the tie break is drawn from the seed and does not depend on dataset order`() {
        val clubs = (1..25).map { club("c$it", country = 50, level = 10) }
        val forward = assemblePyramids(dataset(clubs), setOf(50), rng())
        val backward = assemblePyramids(dataset(clubs.reversed()), setOf(50), rng())
        assertEquals(forward, backward)
    }

    @Test
    fun `a different seed can order equal levels differently`() {
        val clubs = (1..25).map { club("c$it", country = 50, level = 10) }
        val a = assemblePyramids(dataset(clubs), setOf(50), rng())
        val b = assemblePyramids(
            dataset(clubs), setOf(50),
            SplitMix64Rng(43L).fork(org.openfoot.model.SeedDomain.WORLDGEN),
        )
        assertTrue(a != b, "25 equal-level clubs ordering identically across seeds is wrong")
    }
}
