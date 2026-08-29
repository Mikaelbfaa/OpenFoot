package org.openfoot.model

/**
 * Every number section 3.10's two independent paths read, gathered in one
 * value object rather than eleven flat properties of RuleSet, the same
 * grouping DisciplineRates and AssistRules already follow.
 *
 * The AI shootout and the interactive penalty share nothing but this
 * property and the spec section both come from: neither formula reads a
 * field the other owns.
 *
 * shootoutRollMin and shootoutRollMax bound both of the shootout's two
 * draws, so a shootout roll is randRange(shootoutRollMin, shootoutRollMax)
 * called twice.
 *
 * interactiveBaseThreshold is the taker's conversion chance out of a
 * hundred before any modifier. takerFinishingOrTopWorldBonus is added once
 * when the taker has Finalizacao or is a red star, topWorld true; a red star
 * taker collects it once even though both halves of that check are true for
 * him. takerStarBonus is added once more, independently, whenever the taker
 * is any kind of star, red included, since a red star is always a star too.
 * keeperPenaltySavingPenalty, keeperTopWorldPenalty and keeperStarPenalty
 * subtract on the same independent basis for the goalkeeper facing the kick.
 *
 * missSavedOutcomes, missWideOutcomes and missOnTargetOutcomes are the
 * shares of the seven equally likely branches a missed penalty can land on:
 * three credit the goalkeeper with a penalty saved, two go off target, and
 * two stay on target crediting nobody. missOutcomeCount, their sum, is the
 * rand(N) bound of that second draw, so the three shares and the bound can
 * never disagree with each other.
 */
@SpecRef("3.10")
data class PenaltyRules(
    @property:SpecRef("3.10") val shootoutRollMin: Int,
    @property:SpecRef("3.10") val shootoutRollMax: Int,
    @property:SpecRef("3.10") val interactiveBaseThreshold: Int,
    @property:SpecRef("3.10") val takerFinishingOrTopWorldBonus: Int,
    @property:SpecRef("3.10") val takerStarBonus: Int,
    @property:SpecRef("3.10") val keeperPenaltySavingPenalty: Int,
    @property:SpecRef("3.10") val keeperTopWorldPenalty: Int,
    @property:SpecRef("3.10") val keeperStarPenalty: Int,
    @property:SpecRef("3.10") val missSavedOutcomes: Int,
    @property:SpecRef("3.10") val missWideOutcomes: Int,
    @property:SpecRef("3.10") val missOnTargetOutcomes: Int,
) {
    /** The rand(N) bound of the miss draw, the sum of its three shares. */
    val missOutcomeCount: Int get() = missSavedOutcomes + missWideOutcomes + missOnTargetOutcomes
}
