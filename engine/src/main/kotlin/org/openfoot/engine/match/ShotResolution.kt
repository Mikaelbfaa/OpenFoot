package org.openfoot.engine.match

import org.openfoot.model.HomeAdvantage
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.ShotBaseWeights
import org.openfoot.model.ShotMultipliers
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.bfRound
import kotlin.math.max

/** What became of a shot. */
@SpecRef("3.6c")
enum class ShotOutcome {
    GOAL,
    SAVED,
    WIDE,
}

/**
 * Everything the shot formula reads.
 *
 * The reputation gap is the defending side's reputation minus the possessing
 * side's, so a positive value means the side shooting is the underdog.
 */
@SpecRef("3.6c")
data class ShotInputs(
    val shooterRating: Double,
    val keeperRating: Double,
    val possessorAttack: Double,
    val defenderDefence: Double,
    val goalsAlreadyScored: Int,
    val reputationGap: Int,
    val advantage: HomeAdvantage,
    val humanInvolved: Boolean,
    val defenderCentrebacks: Int,
    val divisor: Double,
)

/**
 * Resolves one shot into a goal, a save or a miss.
 *
 * At even strength on neutral ground about one shot in ten is a goal. The base
 * weights then worsen sharply as the shooting side piles up goals, which is an
 * explicit brake on blowouts: after six the conversion rate is under one
 * percent.
 *
 * Home advantage is applied through a rule set strategy rather than inline,
 * because the original's version is defective in two ways at once and the
 * modern rules have to be able to repair it without touching this function.
 */
@SpecRef("3.6c")
fun shotOutcome(inputs: ShotInputs, rules: RuleSet, rng: Rng): ShotOutcome {
    var saved = 1.0 + strengthDifference(inputs.keeperRating, inputs.shooterRating, inputs.divisor)
    val wide = 1.0 + strengthDifference(
        inputs.defenderDefence,
        inputs.possessorAttack,
        inputs.divisor,
    )

    if (inputs.humanInvolved) {
        when (inputs.defenderCentrebacks) {
            0 -> saved = bfRound(saved * rules.savedNoCentrebackFactor).toDouble()
            1 -> saved = bfRound(saved * rules.savedOneCentrebackFactor).toDouble()
        }
    }

    val base = shotBaseWeights(inputs.goalsAlreadyScored, inputs.reputationGap, rules)
    val adjusted = rules.shotHomeRule.adjust(
        ShotMultipliers(saved, wide),
        inputs.advantage,
        rules.shotHomeDelta,
    )

    val floor = rules.duelWeightFloor
    val weights = doubleArrayOf(base.goal, base.saved, base.wide)
    val multipliers = doubleArrayOf(1.0, max(adjusted.saved, floor), max(adjusted.wide, floor))

    return when (weightedPick(weights, multipliers, rng)) {
        0 -> ShotOutcome.GOAL
        1 -> ShotOutcome.SAVED
        else -> ShotOutcome.WIDE
    }
}

/**
 * Picks the base weights for a shot.
 *
 * The anti blowout ladder is walked in order and the last rung whose threshold
 * is met wins. The outclassed override is then applied last, so a side that is
 * two goals up against a much stronger opponent is pulled back to the middle
 * rung even if it has scored six.
 */
@SpecRef("3.6c")
internal fun shotBaseWeights(
    goalsAlreadyScored: Int,
    reputationGap: Int,
    rules: RuleSet,
): ShotBaseWeights {
    var weights = rules.shotBaseWeights
    for (step in rules.antiBlowoutLadder) {
        if (goalsAlreadyScored >= step.goalsAtLeast) {
            weights = step.weights
        }
    }
    if (goalsAlreadyScored >= rules.outclassedGoalsAtLeast &&
        reputationGap >= rules.outclassedReputationGap
    ) {
        weights = rules.outclassedWeights
    }
    return weights
}

/**
 * Shot resolution for a real pairing.
 *
 * The elvis branch below, substituting rules.missingShooterRating for a null
 * shooter, is unreachable today. Section 3.6's finisher draw, corrected
 * against the original, now falls back to the last player of the pitch
 * lineup whenever it finds nobody eligible, so selectShooter only ever hands
 * back null when a side has no player on the pitch at all, a case nothing in
 * this engine constructs. The branch and rules.missingShooterRating stay
 * regardless, because this figure's own text in section 3.6c, "sh =
 * B(finalizador), 0.1 se nenhum", has not itself been verified against the
 * original the way section 3.6's finisher draw has. Dropping the constant
 * now would be changing behaviour nobody has confirmed yet rather than
 * reading it off a settled spec.
 */
@SpecRef("3.6c")
fun shotOutcome(
    setup: MatchSetup,
    possessor: TeamSide,
    shooter: MatchPlayer?,
    goalsAlreadyScored: Int,
    rng: Rng,
): ShotOutcome {
    val attackingSide = setup.side(possessor)
    val defendingSide = setup.side(possessor.opponent)
    val attacking = lineAggregates(attackingSide, setup.rules)
    val defending = lineAggregates(defendingSide, setup.rules)

    return shotOutcome(
        ShotInputs(
            shooterRating = shooter?.let { attackingSide.ratingOf(it) }
                ?: setup.rules.missingShooterRating,
            keeperRating = defending.keeper,
            possessorAttack = attacking.attack,
            defenderDefence = defending.defence,
            goalsAlreadyScored = goalsAlreadyScored,
            reputationGap = defendingSide.reputation - attackingSide.reputation,
            advantage = setup.advantageFor(possessor),
            humanInvolved = setup.hasHumanSide,
            defenderCentrebacks = centrebackCount(defendingSide, setup.rules),
            divisor = differenceDivisor(setup.season, DuelKind.SHOT, setup.rules),
        ),
        setup.rules,
        rng,
    )
}
