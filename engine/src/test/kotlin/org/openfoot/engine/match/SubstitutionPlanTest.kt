package org.openfoot.engine.match

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The minutes each side plans to make a change in, drawn once per match for
 * both sides together.
 *
 * Every minute here is counted inside the second half, which is the only half
 * section 3.8 lets the AI substitute in.
 *
 * Section 3.8 draws the two sides' minutes from one shuffle of each pool and
 * section 3.15 item 8 adds that each side reads fixed, distinct positions of
 * it. So the shuffles come first, whole, and only then does each side spend
 * its own coins deciding how much of its own slice it keeps.
 *
 * The scripted tests below all script one whole pair of plans, in the fixed
 * draw order matchSubstitutionPlans consumes:
 *
 * 1 to 6. six chasing minutes, as offsets into nineteen to thirty eight, each
 *     redrawn when it repeats one already drawn; the home side reads the first
 *     three and the away side the last three
 * 7 to 10. four minutes of the thirty six to forty two pool, the same way, the
 *     home side reading the first two and the away side the last two
 * 11 to 14. four minutes of the sixteen to thirty five pool
 * 15 to 18. four minutes of the five to fifteen pool
 * 19 to 22. four minutes of the forty three to forty seven window
 * 23. the home side's third chasing minute's coin, out of a hundred, which
 *     takes the third position of its slice under sixty nine
 * 24. the home side's routine pool selector, out of a hundred
 * 25. the home side's first late coin, which takes the first position of its
 *     late block under seventy nine
 * 26. the home side's second late coin, which takes the second position under
 *     forty nine
 * 27. the home side's interval coin, which swaps under fifty
 * 28 to 32. the same five coins for the away side, in the same order
 *
 * The three routine pools are all shuffled whether or not a selector later
 * reads them, which is what makes the positions fixed and what the original
 * does when it reshuffles all five pools at the start of every match.
 *
 * ScriptedInts throws when a draw is asked for that the script does not have
 * and counts the ones it made, so a script that is exactly the right length is
 * itself the assertion that the order above is the order the code uses.
 */
class SubstitutionPlanTest {

    /**
     * The layout itself: one shuffle per pool, the home side reading the
     * leading positions and the away side the ones behind them. Every minute
     * asserted here is distinct from every other, so no assertion could pass
     * by landing on a shared first element.
     */
    @Test
    fun `each side reads its own slice of one shuffled pool`() {
        val plans = plans(*POOLS, *QUIET, *QUIET)

        assertEquals(listOf(19, 20), plans.home.chasing)
        assertEquals(listOf(22, 23), plans.away.chasing)
        assertEquals(listOf(36, 37), plans.home.routine)
        assertEquals(listOf(38, 39), plans.away.routine)
    }

    /**
     * The third chasing minute arrives on a coin under sixty nine. Sixty eight
     * buys one and sixty nine does not, which pins the boundary from both
     * sides; the sixty nine per cent section 3.8 prints is exactly the sixty
     * nine draws of a hundred below it.
     *
     * It is the third position of the side's own slice, and both sides are
     * asserted, so neither side's extra minute is being read out of the
     * other's block.
     */
    @Test
    fun `a coin under sixty nine buys a side a third chasing minute`() {
        val homeBuys = plans(*POOLS, *coins(extra = 68), *QUIET)
        assertEquals(listOf(19, 20, 21), homeBuys.home.chasing)
        assertEquals(listOf(22, 23), homeBuys.away.chasing)

        val awayBuys = plans(*POOLS, *QUIET, *coins(extra = 68))
        assertEquals(listOf(19, 20), awayBuys.home.chasing)
        assertEquals(listOf(22, 23, 24), awayBuys.away.chasing)
    }

    /**
     * The away side's block sits at a fixed offset rather than after whatever
     * the home side happened to keep: the home coin that refuses the third
     * chasing minute leaves position two unread by anybody, and the away side
     * still starts at position three.
     *
     * This is the difference between reading fixed positions and consuming a
     * pool in turn, and it is the reading section 3.15 item 8 states.
     */
    @Test
    fun `a refused home coin does not hand its minute to the away side`() {
        val kept = plans(*POOLS, *coins(extra = 68), *QUIET)
        val refused = plans(*POOLS, *coins(extra = 69), *QUIET)

        assertEquals(kept.away.chasing, refused.away.chasing)
        assertEquals(listOf(22, 23), refused.away.chasing)
    }

    /**
     * The chasing window is nineteen to thirty eight inclusive: offset nought
     * is nineteen and offset nineteen is thirty eight, so both ends of the
     * window are reachable and neither runs past it.
     */
    @Test
    fun `the chasing window runs from nineteen to thirty eight`() {
        val plans = plans(*offsets(0, 19, 1, 2, 3, 4), *ROUTINE_POOLS, *QUIET, *QUIET)
        assertEquals(listOf(19, 38), plans.home.chasing)
    }

    /**
     * The six chasing minutes are drawn without replacement across both sides,
     * and a repeat costs one further draw rather than being quietly dropped.
     * The second draw repeats the first and the third draw, offset five, is
     * the one that lands.
     */
    @Test
    fun `a repeated chasing minute is drawn again`() {
        val script = ScriptedInts(*offsets(0, 0, 5, 1, 2, 3, 4), *ROUTINE_POOLS, *QUIET, *QUIET)
        val plans = matchSubstitutionPlans(script, RULES)

        assertEquals(listOf(19, 24), plans.home.chasing)
        assertEquals(listOf(21, 22), plans.away.chasing)
        assertEquals(TOTAL_DRAWS + 1, script.draws)
    }

    /**
     * The routine pool is chosen by one draw out of a hundred, and all three
     * of section 3.8's bands are pinned on both of their boundaries: above
     * ninety takes five to fifteen, above fifty takes sixteen to thirty five,
     * and anything else takes thirty six to forty two.
     *
     * The offsets in POOLS are nought and one in every pool, so the two
     * minutes named here are that pool's first two minutes, and the three
     * bands are told apart by which window they land in.
     */
    @Test
    fun `the routine pool table maps every band to its window`() {
        assertEquals(listOf(36, 37), homeRoutine(selector = 0))
        assertEquals(listOf(36, 37), homeRoutine(selector = 50))
        assertEquals(listOf(16, 17), homeRoutine(selector = 51))
        assertEquals(listOf(16, 17), homeRoutine(selector = 90))
        assertEquals(listOf(5, 6), homeRoutine(selector = 91))
        assertEquals(listOf(5, 6), homeRoutine(selector = 99))
    }

    /**
     * Each side draws its own selector, so the two can sit in different pools
     * in the same match, and then the disjointness of the three windows is
     * what keeps their minutes apart rather than the shuffle.
     */
    @Test
    fun `the two sides pick their routine pools separately`() {
        val plans = plans(*POOLS, *coins(selector = 51), *coins(selector = 91))

        assertEquals(listOf(16, 17), plans.home.routine)
        assertEquals(listOf(7, 8), plans.away.routine)
    }

    /**
     * The four minutes of a routine pool are drawn without replacement across
     * both sides, so a repeat inside the pool costs one further draw. The
     * second draw of the thirty six to forty two pool repeats the first and
     * the third, offset three, is the one that lands.
     */
    @Test
    fun `a repeated routine minute is drawn again`() {
        val script = ScriptedInts(
            *CHASING_POOL,
            *offsets(0, 0, 3, 1, 2),
            *POOL_MIDDLE,
            *POOL_EARLY,
            *LATE_POOL,
            *QUIET,
            *QUIET,
        )
        val plans = matchSubstitutionPlans(script, RULES)

        assertEquals(listOf(36, 39), plans.home.routine)
        assertEquals(listOf(37, 38), plans.away.routine)
        assertEquals(TOTAL_DRAWS + 1, script.draws)
    }

    /**
     * Two more routine minutes can follow from forty three to forty seven, one
     * on a coin under seventy nine and one on a coin under forty nine. Seventy
     * eight and forty eight buy them; seventy nine and forty nine, in the
     * quiet coin block every other test uses, do not.
     *
     * They stay in draw order rather than being sorted, and the away side's
     * two come from its own block of that window rather than from the home
     * side's.
     */
    @Test
    fun `the two late coins each buy a minute from forty three to forty seven`() {
        val plans = plans(*POOLS, *coins(late = LATE_BOTH), *coins(late = LATE_BOTH))

        assertEquals(listOf(36, 37, 43, 44), plans.home.routine)
        assertEquals(listOf(38, 39, 45, 46), plans.away.routine)
    }

    /**
     * Each late coin owns a position of the side's late block, so the second
     * coin buys the second position even when the first coin refused the
     * first. That is the same fixed positioning the chasing block has, applied
     * inside one side rather than between the two sides.
     */
    @Test
    fun `each late coin buys its own position of the block`() {
        assertEquals(listOf(36, 37, 43), plans(*POOLS, *coins(late = LATE_FIRST), *QUIET).home.routine)
        assertEquals(listOf(36, 37, 44), plans(*POOLS, *coins(late = LATE_SECOND), *QUIET).home.routine)
    }

    /**
     * The whole late window is reachable: offsets nought and four put the home
     * side's block at forty three and forty seven, pinning both ends.
     */
    @Test
    fun `the late window runs from forty three to forty seven`() {
        val plans = plans(
            *CHASING_POOL,
            *POOL_LATE,
            *POOL_MIDDLE,
            *POOL_EARLY,
            *offsets(0, 4, 1, 2),
            *coins(late = LATE_BOTH),
            *QUIET,
        )
        assertEquals(listOf(36, 37, 43, 47), plans.home.routine)
    }

    /**
     * The forty three to forty seven window is drawn without replacement over
     * both sides too, and it is the tightest of the five: four of its five
     * minutes are spoken for, so it is the one place a collision is at all
     * common. The second draw repeats the first and the third, offset one, is
     * the one that lands.
     */
    @Test
    fun `a repeated late minute is drawn again`() {
        val script = ScriptedInts(
            *CHASING_POOL,
            *POOL_LATE,
            *POOL_MIDDLE,
            *POOL_EARLY,
            *offsets(0, 0, 1, 2, 3),
            *coins(late = LATE_BOTH),
            *coins(late = LATE_BOTH),
        )
        val plans = matchSubstitutionPlans(script, RULES)

        assertEquals(listOf(36, 37, 43, 44), plans.home.routine)
        assertEquals(listOf(38, 39, 45, 46), plans.away.routine)
        assertEquals(TOTAL_DRAWS + 1, script.draws)
    }

    /**
     * The interval's coin is the last draw of a side's block and swaps under
     * fifty, so forty nine swaps and fifty does not. Each side has its own.
     */
    @Test
    fun `the interval coin swaps under fifty`() {
        val plans = plans(*POOLS, *coins(interval = 49), *coins(interval = 50))
        assertTrue(plans.home.halfTimeSwap)
        assertFalse(plans.away.halfTimeSwap)
    }

    /**
     * The order above is the whole order, and the count does not depend on a
     * single coin: a pair of plans that buys nothing and a pair that buys
     * everything both make exactly thirty two draws. That is the shuffles
     * being drawn whole before any coin is read, which is what fixes the
     * positions.
     *
     * ScriptedInts throws on a thirty third, so the exact length of each
     * script pins the count from above and the assertion pins it from below.
     */
    @Test
    fun `a pair of plans makes the same draws whatever the coins buy`() {
        val lean = ScriptedInts(*POOLS, *QUIET, *QUIET)
        matchSubstitutionPlans(lean, RULES)
        assertEquals(TOTAL_DRAWS, lean.draws)

        val full = ScriptedInts(
            *POOLS,
            *coins(extra = 68, late = LATE_BOTH, interval = 49),
            *coins(extra = 68, late = LATE_BOTH, interval = 49),
        )
        matchSubstitutionPlans(full, RULES)
        assertEquals(TOTAL_DRAWS, full.draws)
    }

    /**
     * Section 3.15 item 8's own consequence, over many seeds: the two sides
     * never share a chasing minute and never share a routine minute.
     *
     * It holds for two different reasons and both are exercised here. Two
     * chasing minutes, or two routine minutes out of the same pool, are
     * distinct positions of one shuffle. Two routine minutes out of different
     * pools are in windows that do not overlap at all.
     */
    @Test
    fun `the two sides never share a chasing minute or a routine minute`() {
        for (seed in 1L..500L) {
            val plans = matchSubstitutionPlans(SplitMix64Rng(seed), RULES)

            assertTrue(
                plans.home.chasing.none { it in plans.away.chasing },
                "seed $seed shared a chasing minute: ${plans.home.chasing} and ${plans.away.chasing}",
            )
            assertTrue(
                plans.home.routine.none { it in plans.away.routine },
                "seed $seed shared a routine minute: ${plans.home.routine} and ${plans.away.routine}",
            )
        }
    }

    /**
     * The guarantee above is per pool and not across pools, and this is the
     * half that has to survive: a routine minute of one side may still fall on
     * a chasing minute of the other, because those come from two shuffles that
     * know nothing of each other and from windows that overlap - nineteen to
     * thirty eight against sixteen to thirty five and thirty six to forty two.
     *
     * Section 3.15 item 11 has nothing left to bite on if this stops
     * happening, and nearly nothing if it merely becomes rare, so the floor is
     * a rate rather than a single case. Measured over these five hundred
     * seeds it is 124, a shade under a quarter of matches, and the floor of
     * ninety sits about three and a half standard deviations below that on a
     * binomial of five hundred at a quarter. So a change that reorders the
     * draws without touching the property still passes, while a real thinning
     * of the overlap fails here, naming item 11, instead of quietly leaving
     * DisciplineChainTest's shared minute test as the only thing that knows.
     */
    @Test
    fun `a routine minute of one side keeps falling on a chasing minute of the other`() {
        val shared = (1L..500L).count { seed ->
            val plans = matchSubstitutionPlans(SplitMix64Rng(seed), RULES)
            plans.home.routine.any { it in plans.away.chasing } ||
                plans.away.routine.any { it in plans.home.chasing }
        }

        assertTrue(
            shared >= CROSS_POOL_FLOOR,
            "only $shared of five hundred matches put a routine minute of one side on a chasing " +
                "minute of the other, against a floor of $CROSS_POOL_FLOOR and a measured 124, " +
                "so section 3.15 item 11 has almost nothing left to fire on",
        )
    }

    @Test
    fun `chasing minutes fall inside the window and never repeat`() {
        for (seed in 1L..200L) {
            for (plan in bothPlans(seed)) {
                assertTrue(plan.chasing.all { it in 19..38 }, "seed $seed")
                assertEquals(plan.chasing.size, plan.chasing.toSet().size, "seed $seed")
                assertTrue(plan.chasing.size in 2..3, "seed $seed")
            }
        }
    }

    @Test
    fun `routine minutes come from one pool and never repeat`() {
        for (seed in 1L..200L) {
            for (plan in bothPlans(seed)) {
                val fromPool = plan.routine.take(2)
                assertTrue(
                    fromPool.all { it in 5..15 } ||
                        fromPool.all { it in 16..35 } ||
                        fromPool.all { it in 36..42 },
                    "seed $seed drew $fromPool from more than one pool",
                )
                assertTrue(plan.routine.drop(2).all { it in 43..47 }, "seed $seed")
                assertTrue(plan.routine.size in 2..4, "seed $seed")
                assertEquals(plan.routine.size, plan.routine.toSet().size, "seed $seed")
            }
        }
    }

    /**
     * The fifty one per cent band is the majority one, and the nine per cent
     * band is the rare one, which is the shape of the table read back out of
     * two thousand plans rather than off its boundaries.
     */
    @Test
    fun `the late pool is the most common and the early pool the rarest`() {
        val plans = (1L..2000L).map { matchSubstitutionPlans(SplitMix64Rng(it), RULES).home }
        val late = plans.count { it.routine.first() in 36..42 }
        val early = plans.count { it.routine.first() in 5..15 }
        assertTrue(late > plans.size / 2, "the fifty one per cent pool came up $late times")
        assertTrue(early < plans.size / 5, "the nine per cent pool came up $early times")
    }

    /**
     * The interval coin comes up heads about half the time over five hundred
     * matches, which is the fifty per cent of section 3.8 read back out of the
     * stream rather than off its boundary. The away side's coin is counted
     * with the home side's, since it is a draw of its own.
     */
    @Test
    fun `the interval coin is close to even over many plans`() {
        val plans = (1L..500L).flatMap { bothPlans(it) }
        val heads = plans.count { it.halfTimeSwap }
        assertTrue(heads in 400..600, "the fifty per cent coin came up $heads times in ${plans.size}")
    }

    private companion object {
        val RULES = RuleSets.CLASSIC

        /**
         * The six offsets of the chasing shuffle, each one free, so that the
         * home side reads nineteen, twenty and twenty one and the away side
         * twenty two, twenty three and twenty four.
         */
        val CHASING_POOL = intArrayOf(0, 1, 2, 3, 4, 5)

        /** The four offsets of the thirty six to forty two pool. */
        val POOL_LATE = intArrayOf(0, 1, 2, 3)

        /** The four offsets of the sixteen to thirty five pool. */
        val POOL_MIDDLE = intArrayOf(0, 1, 2, 3)

        /** The four offsets of the five to fifteen pool. */
        val POOL_EARLY = intArrayOf(0, 1, 2, 3)

        /** The four offsets of the forty three to forty seven window. */
        val LATE_POOL = intArrayOf(0, 1, 2, 3)

        /**
         * The three routine pools in the order the band table declares them,
         * then the late window: every shuffle but the chasing one.
         */
        val ROUTINE_POOLS = POOL_LATE + POOL_MIDDLE + POOL_EARLY + LATE_POOL

        /** Every shuffle of a match, in order, with no collision anywhere. */
        val POOLS = CHASING_POOL + ROUTINE_POOLS

        /** How many draws a pair of plans makes when nothing collides. */
        const val TOTAL_DRAWS = 32

        /**
         * The fewest of five hundred matches that may put a routine minute of
         * one side on a chasing minute of the other before section 3.15 item
         * 11 counts as dead. The measured figure is 124; this is the floor
         * under it, not the figure itself.
         */
        const val CROSS_POOL_FLOOR = 90

        /** A coin that refuses the third chasing minute. */
        const val NO_EXTRA_CHASING = 69

        /** A selector that lands in the thirty six to forty two band. */
        const val SELECTOR_LATE_POOL = 0

        /** Two coins that refuse both late minutes. */
        val LATE_NEITHER = intArrayOf(79, 49)

        /** Two coins that buy the first late minute only. */
        val LATE_FIRST = intArrayOf(78, 49)

        /** Two coins that buy the second late minute only. */
        val LATE_SECOND = intArrayOf(79, 48)

        /** Two coins that buy both late minutes. */
        val LATE_BOTH = intArrayOf(78, 48)

        /** A coin that refuses the interval swap. */
        const val NO_INTERVAL_SWAP = 50

        /**
         * One side's five coins, in the order it draws them, each defaulting
         * to the value that buys nothing.
         */
        fun coins(
            extra: Int = NO_EXTRA_CHASING,
            selector: Int = SELECTOR_LATE_POOL,
            late: IntArray = LATE_NEITHER,
            interval: Int = NO_INTERVAL_SWAP,
        ): IntArray = intArrayOf(extra, selector) + late + intArrayOf(interval)

        /** The coin block of a side that buys nothing at all. */
        val QUIET = coins()

        /** Offsets written out where a test varies one pool's shuffle. */
        fun offsets(vararg values: Int): IntArray = values

        fun plans(vararg draws: Int): MatchSubstitutionPlans =
            matchSubstitutionPlans(ScriptedInts(*draws), RULES)

        fun bothPlans(seed: Long): List<SubstitutionPlan> =
            matchSubstitutionPlans(SplitMix64Rng(seed), RULES).let { listOf(it.home, it.away) }

        /**
         * The home side's two pool minutes under the given selector, with
         * every other coin refusing.
         */
        fun homeRoutine(selector: Int): List<Int> =
            plans(*POOLS, *coins(selector = selector), *QUIET).home.routine
    }
}
