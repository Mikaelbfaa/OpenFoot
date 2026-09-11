package org.openfoot.engine.season

import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NationalCupTest {

    private fun clubs(count: Int) = generateWorld(
        WorldFixtures.dataset(clubs = (1..count).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it)) }),
        2,
        activeLeagues = setOf(Country.BRAZIL),
    ).clubs.map { ClubState.fresh(it) }

    @Test
    fun `the bracket is the largest power of two that fits, never below eight`() {
        assertNull(nationalCup(Country.BRAZIL, clubs(7), newCupFormat = true, rng = SplitMix64Rng(1)))
        val ten = nationalCup(Country.BRAZIL, clubs(10), newCupFormat = true, rng = SplitMix64Rng(1))!!
        assertEquals(8, ten.participants.size)
        val eighteen = nationalCup(Country.BRAZIL, clubs(18), newCupFormat = true, rng = SplitMix64Rng(1))!!
        assertEquals(16, eighteen.participants.size)
    }

    @Test
    fun `the strong half meets the weak half, and the weak side hosts the first leg`() {
        val clubs = clubs(16)
        val cup = nationalCup(Country.BRAZIL, clubs, newCupFormat = true, rng = SplitMix64Rng(3))!!
        val strong = clubs.sortedByDescending { it.club.entry.level }.take(8).map { it.key }.toSet()
        val firstLegs = cup.nextMatches(RuleSets.CLASSIC, SplitMix64Rng(3))
        assertEquals(8, firstLegs.size)
        assertTrue(firstLegs.all { it.fixture.away in strong && it.fixture.home !in strong })
        assertEquals(1, firstLegs.first().leg)
    }

    @Test
    fun `the same seed draws the same cup`() {
        val clubs = clubs(12)
        assertEquals(
            nationalCup(Country.BRAZIL, clubs, newCupFormat = true, rng = SplitMix64Rng(8))!!.participants,
            nationalCup(Country.BRAZIL, clubs, newCupFormat = true, rng = SplitMix64Rng(8))!!.participants,
        )
    }

    /**
     * Section 1.13 runs the novo formato for a country of ninety one clubs or
     * more with the option on; this version builds the standard format there
     * and the cup says so. Ninety clubs, or the option off, is the standard
     * format by the spec itself, and carries no note.
     */
    @Test
    fun `a cup over the novo formato threshold with the option on notes the standard format`() {
        val ninetyOne = clubs(91)
        val over = nationalCup(Country.BRAZIL, ninetyOne, newCupFormat = true, rng = SplitMix64Rng(4))!!
        assertEquals(listOf(Approximation.NEW_CUP_FORMAT_AS_STANDARD.text), over.approximations)
        assertEquals(64, over.participants.size)
        assertEquals(emptyList(), nationalCup(Country.BRAZIL, ninetyOne, newCupFormat = false, rng = SplitMix64Rng(4))!!.approximations)
        assertEquals(emptyList(), nationalCup(Country.BRAZIL, clubs(90), newCupFormat = true, rng = SplitMix64Rng(4))!!.approximations)
    }
}
