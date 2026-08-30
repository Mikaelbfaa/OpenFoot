package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every expected number here is worked out by hand from the block in section
 * 4.4, with the draws scripted so there is no sampling involved.
 *
 * The worked base case throughout is a club of level eighteen in the first
 * division of a strong country: mapped level nineteen, strength base twenty,
 * and no country scaling.
 */
class InitialStrengthTest {

    private val firstDivision = ClubBands.bands(division = 1, reputation = 0)

    private fun strength(
        clubLevel: Int = 18,
        bands: GenerationBands = firstDivision,
        countryLevel: Int = 20,
        starter: Boolean = false,
        star: Boolean = false,
        vararg draws: Int,
    ) = initialStrength(clubLevel, bands, countryLevel, starter, star, ScriptedInts(*draws))

    @Test
    fun `a squad player is the mapped level plus the base plus the spread`() {
        assertEquals(39, strength(draws = intArrayOf(0)))
        assertEquals(40, strength(draws = intArrayOf(1)))
        assertEquals(41, strength(draws = intArrayOf(2)))
    }

    @Test
    fun `a starter takes eight plus a further draw`() {
        assertEquals(47, strength(starter = true, draws = intArrayOf(0, 0)))
        assertEquals(48, strength(starter = true, draws = intArrayOf(0, 1)))
    }

    @Test
    fun `a star takes nine plus a wider draw`() {
        assertEquals(48, strength(star = true, draws = intArrayOf(0, 0)))
        assertEquals(50, strength(star = true, draws = intArrayOf(0, 2)))
    }

    @Test
    fun `the starter and star bonuses both apply and draw in that order`() {
        assertEquals(58, strength(starter = true, star = true, draws = intArrayOf(1, 0, 1)))
    }

    @Test
    fun `a squad player draws once and a starring starter draws three times`() {
        val quiet = ScriptedInts(0)
        initialStrength(18, firstDivision, 20, starter = false, star = false, rng = quiet)
        assertEquals(1, quiet.draws)

        val loud = ScriptedInts(0, 0, 0)
        initialStrength(18, firstDivision, 20, starter = true, star = true, rng = loud)
        assertEquals(3, loud.draws)
    }

    @Test
    fun `a weak country scales a good club by three quarters`() {
        assertEquals(29, strength(countryLevel = 10, draws = intArrayOf(0)))
    }

    @Test
    fun `a weak country scales a small club harder`() {
        assertEquals(18, strength(clubLevel = 8, countryLevel = 10, draws = intArrayOf(0)))
    }

    @Test
    fun `a strong country still penalises a club below level ten`() {
        assertEquals(20, strength(clubLevel = 8, countryLevel = 20, draws = intArrayOf(0)))
    }

    @Test
    fun `a strong country leaves a club of level ten or more alone`() {
        assertEquals(1.0, countryScale(clubLevel = 10, countryLevel = 20))
        assertEquals(1.0, countryScale(clubLevel = 20, countryLevel = 20))
    }

    @Test
    fun `the weak country threshold is tested at both sides`() {
        assertEquals(0.75, countryScale(clubLevel = 18, countryLevel = WEAK_COUNTRY_CEILING))
        assertEquals(1.0, countryScale(clubLevel = 18, countryLevel = WEAK_COUNTRY_CEILING + 1))
    }

    @Test
    fun `the lowest rungs of both scaling ladders are unreachable from the data`() {
        for (level in ClubEntry.LEVEL_RANGE) {
            val weak = countryScale(level, countryLevel = WEAK_COUNTRY_CEILING)
            val strong = countryScale(level, countryLevel = WEAK_COUNTRY_CEILING + 1)
            assertTrue(weak >= 0.65, "club level $level reached the 0.40 rung, which needs level five")
            assertTrue(
                strong >= 0.70,
                "club level $level reached a rung below 0.70, which needs level four or less",
            )
        }
    }

    @Test
    fun `no player can be born anywhere near the strength ceiling`() {
        val strongest = initialStrength(
            clubLevel = ClubEntry.LEVEL_RANGE.last,
            bands = ClubBands.bands(division = null, reputation = 5, reputationPath = true),
            countryLevel = 20,
            starter = true,
            star = true,
            rng = ScriptedInts(2, 1, 2),
        )
        assertEquals(69, strongest)
        assertTrue(
            strongest < 100,
            "the ceiling of 100 is inert at world creation and only binds once players grow",
        )
    }
}
