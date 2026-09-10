package org.openfoot.engine.season

import org.openfoot.engine.world.ScriptedInts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The retirement test of section 4.11: the discounts, the ladder and the roll. */
class RetirementTest {

    @Test
    fun `the effective age discounts a star by one, world class by three and a keeper by three`() {
        assertEquals(35, effectiveRetirementAge(35, star = false, topWorld = false, goalkeeper = false))
        assertEquals(34, effectiveRetirementAge(35, star = true, topWorld = false, goalkeeper = false))
        assertEquals(32, effectiveRetirementAge(35, star = false, topWorld = true, goalkeeper = false))
        assertEquals(28, effectiveRetirementAge(35, star = true, topWorld = true, goalkeeper = true))
    }

    @Test
    fun `the ladder is read on the effective age`() {
        val expected = mapOf(
            31 to 0, 32 to 1, 33 to 10, 34 to 10, 35 to 45, 36 to 70, 37 to 85, 38 to 85,
            39 to 95, 40 to 97, 41 to 98, 42 to 98, 43 to 99, 48 to 99, 49 to 100,
        )
        for ((age, chance) in expected) {
            assertEquals(chance, retirementChance(age), "effective age $age")
        }
    }

    @Test
    fun `the test is only taken past thirty two, on the real age, and draws once`() {
        val untouched = ScriptedInts()
        assertFalse(retires(32, star = false, topWorld = false, goalkeeper = false, rng = untouched))
        assertEquals(0, untouched.draws)

        // A roll is randRange(1, 100), scripted as rand(100) plus one: 44
        // gives a roll of 45, at the 45 per cent rung of a thirty five year
        // old, and retires; 45 gives 46 and does not.
        assertTrue(retires(35, star = false, topWorld = false, goalkeeper = false, rng = ScriptedInts(44)))
        assertFalse(retires(35, star = false, topWorld = false, goalkeeper = false, rng = ScriptedInts(45)))

        // A keeper of thirty five faces the odds of thirty two: one in a hundred.
        assertTrue(retires(35, star = false, topWorld = false, goalkeeper = true, rng = ScriptedInts(0)))
        assertFalse(retires(35, star = false, topWorld = false, goalkeeper = true, rng = ScriptedInts(1)))
    }
}
