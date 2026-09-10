package org.openfoot.engine.season

import org.openfoot.engine.match.SideState
import org.openfoot.model.SpecRef

/**
 * How much energy a player gets back after a round, per section 3.9.
 *
 * Three ladders by age: one for a man who played for an AI club, one for a
 * man who played for a human club, which recovers deliberately less, and one
 * for a man who did not play at all, which does not read who manages him.
 * The human ladder is carried from the start even though no club is human
 * yet, so the day one is, the difference lives here and not in a patch.
 *
 * The result is a gain, not a level: recover adds it and stops at full.
 */
@SpecRef("3.9")
fun weeklyRecovery(age: Int, played: Boolean, humanManaged: Boolean): Int = when {
    !played -> when {
        age < 20 -> 30
        age < 26 -> 30
        age < 33 -> 35
        age < 45 -> 35
        else -> 30
    }

    humanManaged -> when {
        age <= 20 -> 13
        age <= 25 -> 24
        age <= 31 -> 37
        age <= 36 -> 40
        else -> 30
    }

    else -> when {
        age <= 20 -> 20
        age <= 25 -> 30
        age <= 31 -> 50
        age <= 36 -> 52
        else -> 42
    }
}

/** Energy after a recovery, capped at the full hundred a match starts from. */
@SpecRef("3.9")
fun recover(energy: Int, gain: Int): Int = minOf(SideState.FULL_ENERGY, energy + gain)
