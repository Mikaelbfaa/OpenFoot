package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The prestige balance of section 5.5: decay, promotion and the title table. */
class PrestigeTest {

    @Test
    fun `decay takes the rung's amount and drops the rung past its floor`() {
        assertEquals(Prestige(5, -6_000), Prestige(5, 0).decayed())
        assertEquals(Prestige(5, -90_000), Prestige(5, -84_000).decayed())
        assertEquals(Prestige(4, -90_001), Prestige(5, -84_001).decayed())
        assertEquals(Prestige(3, -9_001), Prestige(4, -8_401).decayed())
        assertEquals(Prestige(2, -1_001), Prestige(3, -951).decayed())
        assertEquals(Prestige(2, -5), Prestige(2, 0).decayed())
        assertEquals(Prestige(1, 0), Prestige(1, 0).decayed())
        assertEquals(Prestige(0, 0), Prestige(0, 0).decayed())
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
    fun `the title table pays champions and runners up, scaled outside Europe above a thousand`() {
        assertEquals(500, titlePrestige(CompetitionKind.NATIONAL_LEAGUE, champion = true, european = false, division = 1))
        assertEquals(90, titlePrestige(CompetitionKind.NATIONAL_LEAGUE, champion = false, european = true, division = 1))
        assertEquals(50, titlePrestige(CompetitionKind.NATIONAL_LEAGUE, champion = true, european = true, division = 2))
        assertEquals(5_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = true, european = true))
        assertEquals(3_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = true, european = false))
        assertEquals(1_000, titlePrestige(CompetitionKind.CONTINENTAL_PRIMARY, champion = false, european = false))
        assertEquals(24_000, titlePrestige(CompetitionKind.CLUB_WORLD_CUP, champion = true, european = false))
        assertEquals(0, titlePrestige(CompetitionKind.RECOPA, champion = false, european = true))
        assertEquals(0, titlePrestige(CompetitionKind.FRIENDLY, champion = true, european = true))
    }
}
