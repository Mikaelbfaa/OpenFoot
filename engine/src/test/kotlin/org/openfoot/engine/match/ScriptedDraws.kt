package org.openfoot.engine.match

import org.openfoot.model.Rng

/**
 * A generator that hands out one scripted sequence carrying both kinds of
 * draw, in the order the formula makes them.
 *
 * Section 3.7 is the first part of the engine that mixes the two inside a
 * single decision: the type comes out of rand(1000), which is an integer
 * draw, and the assist and the own goal author come out of weighted picks,
 * which are double draws. ScriptedInts and ScriptedRng each throw on the
 * other kind, so neither can script a whole goal on its own, and splitting
 * one goal across two generators would lose the property that matters most
 * here, which is that the draws happen in exactly one order.
 *
 * Every value is written as a double. A value read as an integer must be a
 * whole number inside the bound the formula asked for, and a value read as a
 * double must be a probability in nought until one, so a script that has
 * slipped out of step with the formula fails here rather than quietly
 * producing a different draw.
 *
 * draws counts both kinds together, and running out throws. Asserting it
 * pins a formula's draw count from both directions: a formula that stopped
 * drawing something runs the count short, and one that drew something extra
 * runs out.
 */
class ScriptedDraws(private vararg val values: Double) : Rng {

    var draws: Int = 0
        private set

    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        val value = next()
        val whole = value.toInt()
        check(whole.toDouble() == value) {
            "scripted draw $value was read as an integer, so it has to be a whole number"
        }
        check(whole in 0 until bound) {
            "scripted draw $whole is outside the bound $bound the formula asked for"
        }
        return whole
    }

    override fun nextDouble(): Double {
        val value = next()
        check(value >= 0.0 && value < 1.0) {
            "scripted draw $value was read as a probability, so it has to sit in nought until one"
        }
        return value
    }

    override fun nextBits(): Long = throw UnsupportedOperationException("nextBits is not scripted")

    override fun fork(tag: Long): Rng = throw UnsupportedOperationException("fork is not scripted")

    private fun next(): Double {
        check(draws < values.size) { "ScriptedDraws ran out after $draws draws" }
        return values[draws++]
    }
}
