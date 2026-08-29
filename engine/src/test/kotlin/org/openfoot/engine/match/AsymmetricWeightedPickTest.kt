package org.openfoot.engine.match

import org.openfoot.model.SplitMix64Rng
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private data class WeightedCandidate(val label: String, val totalW: Double, val walkW: Double)

class AsymmetricWeightedPickTest {

    @Test
    fun `a walk bonus ahead in the list steals the draw from a later candidate a symmetric walk would have picked`() {
        val before = WeightedCandidate("before", totalW = 5.0, walkW = 5.0)
        val bonused = WeightedCandidate("bonused", totalW = 6.0, walkW = 7.0)
        val after = WeightedCandidate("after", totalW = 8.0, walkW = 8.0)
        val candidates = listOf(before, bonused, after)

        val winner = asymmetricWeightedPick(
            candidates,
            totalWeight = { it.totalW },
            walkWeight = { it.walkW },
            rng = ScriptedRng(11.5 / 19.0),
        )

        assertEquals(bonused, winner)
    }

    @Test
    fun `the same draw would have picked the later candidate under a symmetric walk`() {
        val before = WeightedCandidate("before", totalW = 5.0, walkW = 5.0)
        val bonused = WeightedCandidate("bonused", totalW = 6.0, walkW = 7.0)
        val after = WeightedCandidate("after", totalW = 8.0, walkW = 8.0)
        val candidates = listOf(before, bonused, after)

        val symmetricWinner = asymmetricWeightedPick(
            candidates,
            totalWeight = { it.totalW },
            walkWeight = { it.totalW },
            rng = ScriptedRng(11.5 / 19.0),
        )

        assertEquals(after, symmetricWinner)
        assertNotEquals(bonused, symmetricWinner)
    }

    @Test
    fun `enough walk bonus ahead of the last candidate makes him unreachable for every draw in range`() {
        val bonused = WeightedCandidate("bonused", totalW = 1.0, walkW = 2.0)
        val last = WeightedCandidate("last", totalW = 1.0, walkW = 1.0)
        val candidates = listOf(bonused, last)

        val draws = listOf(0.0, 0.001, 0.3, 0.6, 0.9, 0.999999)
        for (draw in draws) {
            val winner = asymmetricWeightedPick(
                candidates,
                totalWeight = { it.totalW },
                walkWeight = { it.walkW },
                rng = ScriptedRng(draw),
            )
            assertEquals(bonused, winner, "draw $draw unexpectedly reached the last candidate")
        }
    }

    @Test
    fun `a symmetric walk would have reached the last candidate for the upper half of that same range`() {
        val bonused = WeightedCandidate("bonused", totalW = 1.0, walkW = 2.0)
        val last = WeightedCandidate("last", totalW = 1.0, walkW = 1.0)
        val candidates = listOf(bonused, last)

        val winner = asymmetricWeightedPick(
            candidates,
            totalWeight = { it.totalW },
            walkWeight = { it.totalW },
            rng = ScriptedRng(0.9),
        )

        assertEquals(last, winner)
    }

    /**
     * Mirrors weightedPick's own boundary test: the walk pass compares with
     * strict greater than, so a draw landing exactly on a candidate's
     * cumulative boundary selects the following candidate rather than that
     * one. Fixture B's unreachability claim above depends on this exact
     * comparison staying strict; if it ever became greater than or equal to,
     * the last candidate there would stop being unreachable and this test,
     * not that one, is what would catch it directly.
     */
    @Test
    fun `a draw exactly on a boundary selects the following candidate`() {
        val a = WeightedCandidate("a", totalW = 1.0, walkW = 1.0)
        val b = WeightedCandidate("b", totalW = 1.0, walkW = 1.0)

        val winner = asymmetricWeightedPick(
            listOf(a, b),
            totalWeight = { it.totalW },
            walkWeight = { it.walkW },
            rng = ScriptedRng(0.5),
        )

        assertEquals(b, winner)
    }

    /**
     * Mirrors weightedPick's own empirical test, driven by SplitMix64Rng
     * rather than a scripted value, but chosen so the asymmetry is visible
     * in the measured distribution rather than merely present.
     *
     * Three candidates, walk in list order, all equal by total weight except
     * bonused, who carries a small excess of walk weight over total weight
     * (12 against 11, out of a total of 31). Since the walk process gives an
     * inner candidate a slice of the draw range equal to its own walk
     * weight, bonused's true selection probability is walkW / totalSum,
     * 12 / 31, about 38.71 per cent, well above his fair share of the total,
     * totalW / totalSum, 11 / 31, about 35.48 per cent.
     *
     * Measured over 100000 draws from seed 7: bonused won 38531 times, a
     * share of 0.38531, within 0.006 of the 0.387097 the walk share
     * predicts and about 3 percentage points above the 0.354839 fair share,
     * far outside sampling noise at this sample size. That gap is the
     * asymmetry actually showing up in the numbers, not just present in the
     * formula.
     */
    @Test
    fun `the walk bonus is measurably over-selected against its own share of the total`() {
        val before = WeightedCandidate("before", totalW = 10.0, walkW = 10.0)
        val bonused = WeightedCandidate("bonused", totalW = 11.0, walkW = 12.0)
        val after = WeightedCandidate("after", totalW = 10.0, walkW = 10.0)
        val candidates = listOf(before, bonused, after)

        val rng = SplitMix64Rng(7)
        val draws = 100_000
        var bonusedWins = 0
        repeat(draws) {
            if (asymmetricWeightedPick(candidates, { it.totalW }, { it.walkW }, rng) == bonused) {
                bonusedWins++
            }
        }
        val share = bonusedWins.toDouble() / draws

        val totalSum = candidates.sumOf { it.totalW }
        val fairShareByTotal = bonused.totalW / totalSum
        val actualShareByWalk = bonused.walkW / totalSum

        assertTrue(
            abs(share - actualShareByWalk) < 0.006,
            "expected about $actualShareByWalk, measured $share",
        )
        assertTrue(
            share - fairShareByTotal > 0.02,
            "expected the walk bonus to inflate the share well above the fair total-weight " +
                "share of $fairShareByTotal, measured $share",
        )
    }

    @Test
    fun `exactly one draw is consumed per call`() {
        val candidates = listOf(
            WeightedCandidate("a", 1.0, 1.0),
            WeightedCandidate("b", 1.0, 1.0),
        )
        val rng = ScriptedRng(0.1, 0.9)
        asymmetricWeightedPick(candidates, { it.totalW }, { it.walkW }, rng)
        assertEquals(1, rng.draws)
        asymmetricWeightedPick(candidates, { it.totalW }, { it.walkW }, rng)
        assertEquals(2, rng.draws)
    }

    @Test
    fun `a zero total returns the last candidate without throwing`() {
        val candidates = listOf(
            WeightedCandidate("a", 0.0, 0.0),
            WeightedCandidate("b", 0.0, 0.0),
            WeightedCandidate("c", 0.0, 0.0),
        )
        val rng = ScriptedRng(0.7)
        val winner = asymmetricWeightedPick(candidates, { it.totalW }, { it.walkW }, rng)
        assertEquals(candidates.last(), winner)
        assertEquals(1, rng.draws)
    }

    @Test
    fun `an empty candidate list is rejected`() {
        val error = runCatching {
            asymmetricWeightedPick(
                emptyList<WeightedCandidate>(),
                totalWeight = { it.totalW },
                walkWeight = { it.walkW },
                rng = ScriptedRng(0.5),
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "expected an argument error, got $error")
    }
}
