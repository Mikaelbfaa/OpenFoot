package org.openfoot.engine.match

import org.openfoot.model.Position
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait

/**
 * Draws the player who takes the shot.
 *
 * Two exclusions apply independently, and both are required: the occupant of
 * slot 1 is out regardless of what he plays, and every player whose natural
 * position is keeper is out regardless of which cell he stands in, so a keeper
 * fielded outfield is excluded too. The bench is excluded by the same slot
 * filter, before weighting rather than after, because the characteristic
 * bonuses are added to the cell weight: a substitute striker has a base weight
 * of zero but would still pick up four for finishing and could take shots from
 * the bench.
 *
 * When the exclusions leave nobody, the draw falls back to the last player of
 * the side's pitch lineup in list order, which can be the goalkeeper himself
 * if he is the only man left on the pitch; the exclusions above only ever
 * apply to the draw, not to this fallback. Null is returned only when the
 * side has no player on the pitch at all, a case the engine never actually
 * reaches.
 */
@SpecRef("3.6")
fun selectShooter(side: MatchSide, rules: RuleSet, rng: Rng): MatchPlayer? {
    val candidates = side.lineup.filter {
        it.slot.value in rules.shooterEligibleSlots && it.naturalPosition != Position.GOALKEEPER
    }
    if (candidates.isEmpty()) {
        return side.lineup.lastOrNull { it.slot.isPitch }
    }
    val weights = DoubleArray(candidates.size) { shooterWeight(candidates[it], rules).toDouble() }
    return candidates[weightedPick(weights, rng)]
}

/**
 * How likely a player is to be the one who shoots.
 *
 * Forwards outweigh defenders by twenty two to one before any bonus. Finishing
 * is checked first and wins outright, so a player with both finishing and
 * heading takes the finishing bonus only. A centre back who heads gets a second
 * bonus on top, which is what puts defenders on the end of set pieces.
 */
@SpecRef("3.6")
internal fun shooterWeight(player: MatchPlayer, rules: RuleSet): Int {
    val slot = player.slot.value
    var weight = rules.shooterSlotWeights.getOrElse(slot) { 0 }

    if (player.hasTrait(Trait.FINISHING)) {
        weight += rules.shooterFinishingBonus
    } else if (player.hasTrait(Trait.HEADING)) {
        weight += rules.shooterHeadingBonus
        if (slot in rules.centrebackSlots) {
            weight += rules.shooterHeadingDefenderBonus
        }
    }

    return weight
}
