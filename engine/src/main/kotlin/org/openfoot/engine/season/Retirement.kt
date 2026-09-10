package org.openfoot.engine.season

import org.openfoot.model.Rng
import org.openfoot.model.SpecRef
import org.openfoot.model.randRange

/**
 * The retirement test of section 4.11, run at the turnover for every
 * professional past the test's floor.
 *
 * The test reads an effective age, the real one discounted for a star, a
 * world class player and a goalkeeper, so a keeper of thirty five faces the
 * odds of a man of thirty two. The chance ladder is then read on that
 * effective age and one roll of a hundred decides. The spec says the test is
 * only taken above thirty two, and that floor reads the real age: it is what
 * keeps a thirty two year old out of the ladder's first rung, which only an
 * older man's discounted age can reach.
 *
 * The roll retires when it lands at or below the chance, so a chance of one
 * is one roll in a hundred; a ladder rung of a hundred is certain. Whether
 * the club is human does not enter here: section 4.11 says a human club only
 * hears the wish and keeps the man, and that is a decision for the caller
 * that owns the club, not for the test.
 */
@SpecRef("4.11")
fun retires(age: Int, star: Boolean, topWorld: Boolean, goalkeeper: Boolean, rng: Rng): Boolean {
    if (age <= RETIREMENT_TEST_FLOOR) return false
    val chance = retirementChance(effectiveRetirementAge(age, star, topWorld, goalkeeper))
    return rng.randRange(1, ROLL_MAX) <= chance
}

@SpecRef("4.11")
internal fun effectiveRetirementAge(age: Int, star: Boolean, topWorld: Boolean, goalkeeper: Boolean): Int =
    age - (if (star) STAR_DISCOUNT else 0) - (if (topWorld) TOP_WORLD_DISCOUNT else 0) -
        (if (goalkeeper) GOALKEEPER_DISCOUNT else 0)

/** The chance ladder of section 4.11, in whole per cent, on the effective age. */
@SpecRef("4.11")
internal fun retirementChance(effectiveAge: Int): Int = when {
    effectiveAge < 32 -> 0
    effectiveAge == 32 -> 1
    effectiveAge <= 34 -> 10
    effectiveAge == 35 -> 45
    effectiveAge == 36 -> 70
    effectiveAge <= 38 -> 85
    effectiveAge == 39 -> 95
    effectiveAge == 40 -> 97
    effectiveAge <= 42 -> 98
    effectiveAge <= 48 -> 99
    else -> 100
}

@SpecRef("4.11")
private const val RETIREMENT_TEST_FLOOR = 32

@SpecRef("4.11")
private const val ROLL_MAX = 100

@SpecRef("4.10")
private const val STAR_DISCOUNT = 1

@SpecRef("4.10")
private const val TOP_WORLD_DISCOUNT = 3

@SpecRef("4.11")
private const val GOALKEEPER_DISCOUNT = 3
