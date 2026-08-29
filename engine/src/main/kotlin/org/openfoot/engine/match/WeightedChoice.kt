package org.openfoot.engine.match

import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

/**
 * Picks one outcome from base weights scaled by multipliers.
 *
 * Every probabilistic decision in the match engine goes through here, so the
 * contract is fixed and must not change, or saved replays from older versions
 * stop reproducing:
 *
 * The draw is u equals nextDouble times the total. The comparison is strict, so
 * a u that lands exactly on a cumulative boundary selects the following index.
 * Exactly one draw is consumed per call, always, whatever the outcome. A total
 * that is not positive returns the last index rather than throwing, because the
 * engine must never fail in the middle of a match.
 */
@SpecRef("3.5")
fun weightedPick(baseWeights: DoubleArray, multipliers: DoubleArray, rng: Rng): Int {
    require(baseWeights.isNotEmpty()) { "weightedPick needs at least one outcome" }
    require(baseWeights.size == multipliers.size) {
        "expected ${baseWeights.size} multipliers, got ${multipliers.size}"
    }

    var total = 0.0
    for (index in baseWeights.indices) {
        total += baseWeights[index] * multipliers[index]
    }

    val draw = rng.nextDouble() * total
    if (total <= 0.0) {
        return baseWeights.size - 1
    }

    var running = 0.0
    for (index in baseWeights.indices) {
        running += baseWeights[index] * multipliers[index]
        if (running > draw) {
            return index
        }
    }
    return baseWeights.size - 1
}

/**
 * Convenience for the case where the weights are already final.
 */
@SpecRef("3.5")
fun weightedPick(weights: DoubleArray, rng: Rng): Int =
    weightedPick(weights, DoubleArray(weights.size) { 1.0 }, rng)

/**
 * Picks one candidate from a list using two different weight functions: one to
 * sum the sorteable total, a different one to walk the same list looking for
 * the draw's target.
 *
 * This exists only because the assist draw (section 3.6, "Quem finaliza e quem
 * da assistencia") cannot be expressed with weightedPick above. Section 3.15
 * item 4 is CONFIRMADO: under the classic rules a player with Velocidade (and
 * none of the earlier characteristics) contributes plus one to the total pass
 * and plus two to the walk pass, an asymmetry that is a reproduced defect,
 * not a bug. Under the modern rules the two bonuses agree, both at plus one,
 * so no candidate's walk weight and total weight ever disagree there. Either
 * way this function must keep the two weight functions genuinely separate
 * rather than collapsed into one shared weight function: classic needs the
 * asymmetry expressed and modern needs it to stay expressible even though
 * modern never exercises it. Cite section 3.15 item 4 before touching
 * this again.
 *
 * The consequence the spec spells out, and which only the classic rules can
 * trigger: because the walk accumulates weight faster than the total that
 * bounds the draw, the walk's running sum reaches the draw's target earlier
 * than a symmetric walk would, so every candidate whose walk weight exceeds
 * its total weight steals the draw from candidates later in the list. With
 * enough of that excess ahead of a candidate, his slice of the draw range
 * is pushed past the total entirely and he becomes unreachable no matter
 * what the draw is. Under the modern rules no candidate's walk weight can
 * exceed its total weight, so this overrun, and the unreachable candidate
 * it produces, cannot happen there.
 *
 * The draw itself is a single call to nextDouble, exactly as in weightedPick:
 * one random value scaled by the total from the first pass, then compared
 * strictly against the walk's running sum so a draw landing exactly on a
 * cumulative boundary selects the following candidate. A total that is not
 * positive returns the last candidate without a second pass, and still
 * consumes exactly the one draw.
 */
@SpecRef("3.6")
fun <T> asymmetricWeightedPick(
    candidates: List<T>,
    totalWeight: (T) -> Double,
    walkWeight: (T) -> Double,
    rng: Rng,
): T {
    require(candidates.isNotEmpty()) { "asymmetricWeightedPick needs at least one candidate" }

    var total = 0.0
    for (candidate in candidates) {
        total += totalWeight(candidate)
    }

    val draw = rng.nextDouble() * total
    if (total <= 0.0) {
        return candidates.last()
    }

    var running = 0.0
    for (candidate in candidates) {
        running += walkWeight(candidate)
        if (running > draw) {
            return candidate
        }
    }
    return candidates.last()
}
