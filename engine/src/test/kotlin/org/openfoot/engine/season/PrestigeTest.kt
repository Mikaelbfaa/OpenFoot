package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The prestige balance of section 5.5: decay, promotion and the title table. */
class PrestigeTest {

    @Test
    fun `decay takes the rung's amount and drops the rung past its floor`() {
        assertEquals(Prestige(5, -6_000), Prestige(5, 0).decayed(inLeague = true))
        assertEquals(Prestige(5, -90_000), Prestige(5, -84_000).decayed(inLeague = true))
        assertEquals(Prestige(4, -90_001), Prestige(5, -84_001).decayed(inLeague = true))
        assertEquals(Prestige(3, -9_001), Prestige(4, -8_401).decayed(inLeague = true))
        assertEquals(Prestige(2, -1_001), Prestige(3, -951).decayed(inLeague = true))
    }

    @Test
    fun `reputation two decays without ever dropping, and one and nought do not decay`() {
        assertEquals(Prestige(2, -1_005), Prestige(2, -1_000).decayed(inLeague = true))
        assertEquals(Prestige(1, 0), Prestige(1, 0).decayed(inLeague = true))
        assertEquals(Prestige(0, 0), Prestige(0, 0).decayed(inLeague = true))
    }

    @Test
    fun `the two lower rungs only decay for a club in a league`() {
        assertEquals(Prestige(3, 0), Prestige(3, 0).decayed(inLeague = false))
        assertEquals(Prestige(2, 0), Prestige(2, 0).decayed(inLeague = false))
        assertEquals(Prestige(5, -6_000), Prestige(5, 0).decayed(inLeague = false))
        assertEquals(Prestige(4, -600), Prestige(4, 0).decayed(inLeague = false))
    }

    @Test
    fun `promotion reads the thresholds and never lowers`() {
        assertEquals(5, Prestige.reputationFor(100_001))
        assertEquals(4, Prestige.reputationFor(100_000))
        assertEquals(4, Prestige.reputationFor(10_001))
        assertEquals(3, Prestige.reputationFor(1_001))
        assertEquals(2, Prestige.reputationFor(101))
        assertEquals(1, Prestige.reputationFor(11))
        assertEquals(0, Prestige.reputationFor(10))

        assertEquals(Prestige(3, 1_500), Prestige(1, 1_500).promoted())
        assertEquals(Prestige(5, 50), Prestige(5, 50).promoted())
    }

    @Test
    fun `an award moves only the balance`() {
        assertEquals(Prestige(2, 600), Prestige(2, 100).awarded(500))
        assertFailsWith<IllegalArgumentException> { Prestige(6, 0) }
    }

    @Test
    fun `the title table pays champions and runners up`() {
        val europe = Country.EUROPE_CONTINENT
        assertEquals(500, titlePrestige(CompetitionKind.NATIONAL_LEAGUE, champion = true, inLeague = true, continent = europe, division = 1))
        assertEquals(90, titlePrestige(CompetitionKind.NATIONAL_LEAGUE, champion = false, inLeague = true, continent = europe, division = 1))
        assertEquals(50, titlePrestige(CompetitionKind.NATIONAL_LEAGUE, champion = true, inLeague = true, continent = europe, division = 2))
        assertEquals(5_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = true, inLeague = true, continent = europe))
        assertEquals(0, titlePrestige(CompetitionKind.RECOPA, champion = false, inLeague = true, continent = europe))
        assertEquals(0, titlePrestige(CompetitionKind.FRIENDLY, champion = true, inLeague = true, continent = europe))
    }

    @Test
    fun `the discount hits only a reputation path club outside Europe and South America`() {
        val africa = 2
        assertEquals(3_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = true, inLeague = false, continent = africa))
        assertEquals(24_000, titlePrestige(CompetitionKind.CLUB_WORLD_CUP, champion = true, inLeague = false, continent = africa))
        assertEquals(1_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = false, inLeague = false, continent = africa))
        assertEquals(5_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = true, inLeague = true, continent = africa))
        assertEquals(5_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = true, inLeague = false, continent = Country.SOUTH_AMERICA_CONTINENT))
        assertEquals(5_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = true, inLeague = false, continent = Country.EUROPE_CONTINENT))
    }
}
