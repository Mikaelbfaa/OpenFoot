package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every expected value here is read straight off the tables of sections 4.2 and
 * 4.4, not from the output of the code under test.
 */
class ClubBandsTest {

    @Test
    fun `levels up to fifteen map to themselves`() {
        for (level in 1..15) {
            assertEquals(level, ClubBands.mappedLevel(level), "level $level")
        }
    }

    @Test
    fun `the mapping accelerates above fifteen exactly as the table says`() {
        assertEquals(17, ClubBands.mappedLevel(16))
        assertEquals(18, ClubBands.mappedLevel(17))
        assertEquals(19, ClubBands.mappedLevel(18))
        assertEquals(21, ClubBands.mappedLevel(19))
        assertEquals(25, ClubBands.mappedLevel(20))
    }

    @Test
    fun `the tail of the mapping continues by five to level twenty five`() {
        assertEquals(26, ClubBands.mappedLevel(21))
        assertEquals(27, ClubBands.mappedLevel(22))
        assertEquals(28, ClubBands.mappedLevel(23))
        assertEquals(29, ClubBands.mappedLevel(24))
        assertEquals(30, ClubBands.mappedLevel(25))
    }

    @Test
    fun `the quality seed is the mapped level less four`() {
        assertEquals(2, ClubBands.qualitySeed(6))
        assertEquals(11, ClubBands.qualitySeed(15))
        assertEquals(13, ClubBands.qualitySeed(16))
        assertEquals(21, ClubBands.qualitySeed(20))
    }

    @Test
    fun `no club level the dataset admits can reach the quality seed guard`() {
        for (level in ClubEntry.LEVEL_RANGE) {
            assertTrue(
                ClubBands.mappedLevel(level) > 4,
                "level $level maps to ${ClubBands.mappedLevel(level)}, which would take the " +
                    "unreachable branch of the quality seed and produce a step downwards",
            )
        }
    }

    @Test
    fun `league bands follow the division table`() {
        assertEquals(GenerationBands(20, 7), ClubBands.bands(division = 1, reputation = 0))
        assertEquals(GenerationBands(15, 3), ClubBands.bands(division = 2, reputation = 0))
        assertEquals(GenerationBands(5, 1), ClubBands.bands(division = 3, reputation = 0))
    }

    @Test
    fun `any division outside the top three lands on the weakest pair`() {
        assertEquals(GenerationBands(1, 1), ClubBands.bands(division = 0, reputation = 5))
        assertEquals(GenerationBands(1, 1), ClubBands.bands(division = 4, reputation = 5))
        assertEquals(GenerationBands(1, 1), ClubBands.bands(division = 9, reputation = 5))
    }

    @Test
    fun `a club of unknown division takes the weakest pair, not its reputation`() {
        assertEquals(GenerationBands(1, 1), ClubBands.bands(division = null, reputation = 5))
    }

    @Test
    fun `reputation decides the bands on the reputation path`() {
        assertEquals(GenerationBands(22, 7), ClubBands.bands(division = null, reputation = 5, reputationPath = true))
        assertEquals(GenerationBands(15, 4), ClubBands.bands(division = null, reputation = 4, reputationPath = true))
    }

    @Test
    fun `reputations one to three are indistinguishable and zero is weaker`() {
        val low = ClubBands.bands(division = null, reputation = 1, reputationPath = true)
        assertEquals(low, ClubBands.bands(division = null, reputation = 2, reputationPath = true))
        assertEquals(low, ClubBands.bands(division = null, reputation = 3, reputationPath = true))
        assertEquals(GenerationBands(strengthBase = 5, abilityBand = 1), low)
        assertEquals(
            GenerationBands(strengthBase = 1, abilityBand = 1),
            ClubBands.bands(division = null, reputation = 0, reputationPath = true),
        )
    }

    @Test
    fun `a club in a league ignores its reputation`() {
        assertEquals(
            ClubBands.bands(division = 1, reputation = 0),
            ClubBands.bands(division = 1, reputation = 5),
        )
    }
}
