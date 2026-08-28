package org.openfoot.engine.match

import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.bound
import org.openfoot.model.pick
import org.openfoot.model.rand
import org.openfoot.model.randRange

/**
 * How long an injury drawn this minute keeps its victim out, and what it
 * costs him for good.
 *
 * days is what the squad reads to know when the player is available again.
 * permanentStrengthLoss is the actual amount the player's current strength
 * drops by, already floored the way section 3.8 states: strength itself still
 * lives on the squad, which the engine cannot reach to write, but the floor
 * needs to know what the player's strength is before it can decide whether
 * subtracting permanentLossAmount would carry it below nought, so injuryOutcome
 * takes that strength in and reports the delta rather than the raw constant.
 * Whoever applies this to the squad's record only has to subtract it.
 */
internal data class InjuryOutcome(val days: Int, val permanentStrengthLoss: Int)

/**
 * Section 3.8's injury duration, the one place in the whole engine where
 * energy feeds back into an outcome, which section 3.9 calls out explicitly.
 *
 * The three draws, x, the long term offset and the severity, are made in that
 * order and unconditionally: every path through the age table costs the same
 * three draws, whatever the branch it takes, so the length of the random
 * stream a match consumes never depends on the ages of the players it injures.
 *
 * Section 3.8 also states that the permanent loss is applied before the
 * severity draw and that the severity draw is always made. Both already hold
 * here: the severity draw above is unconditional at every age, and the loss
 * below reads neither days nor severityBonus and makes no draw of its own, so
 * its place in the text cannot move the stream whichever side of the three
 * draws it sits on. Confirmed against the spec rather than reordered.
 *
 * permanentLossFloor is read only past permanentLossAge. Subtracting
 * permanentLossAmount from strength either lands at nought or above, in which
 * case that is the loss and the result stands, or it would go negative, in
 * which case the player's strength is clamped to the floor instead and the
 * loss reported is only enough to reach it. A strength of five losing five
 * lands exactly on nought and is not touched further; a strength of three
 * losing five would land on minus two and is clamped to the floor of one, a
 * loss of two rather than five.
 *
 * A strength already below permanentLossFloor is the one case this leaves
 * unguarded: a strength of nought would clamp to a floor of one and report a
 * loss of minus one, an increase reported as a loss, which is what section
 * 3.8's own arithmetic literally gives and not a divergence from it.
 * MatchEvent.Injury requires permanentStrengthLoss to be nought or above, so
 * that path would throw rather than clamp. Nothing generates a player with a
 * strength that low, and the spec states no floor of its own for strength to
 * lean on, so the case is left as a fact about the formula rather than
 * guarded against here.
 */
@SpecRef("3.8")
internal fun injuryOutcome(age: Int, energy: Int, strength: Int, rules: RuleSet, rng: Rng): InjuryOutcome {
    val injury = rules.injuryRules
    val x = rng.randRange(injury.shortTermDraw.first, injury.shortTermDraw.last)
    val y = injury.longTermOffset + rng.randRange(injury.longTermDraw.first, injury.longTermDraw.last)
    val severityBonus = injury.severity.pick(rng.rand(injury.severity.bound()))

    val term = injury.ageTerms.pick(age)
    val base = if (term.usesEnergyBase) injury.energyBase.pick(energy) else 0
    val longTerm = if (term.usesLongTerm) y else 0
    val days = base + x + term.constant + longTerm + severityBonus

    val permanentStrengthLoss = if (age >= injury.permanentLossAge) {
        val reduced = strength - injury.permanentLossAmount
        val flooredStrength = if (reduced < 0) injury.permanentLossFloor else reduced
        strength - flooredStrength
    } else {
        0
    }

    return InjuryOutcome(days, permanentStrengthLoss)
}
