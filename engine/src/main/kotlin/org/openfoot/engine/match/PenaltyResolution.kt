package org.openfoot.engine.match

import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.rand
import org.openfoot.model.randRange

/**
 * Section 3.10's two independent penalty paths.
 *
 * Neither has a caller yet. The shootout waits for the knockout brackets of
 * v0.3, and the interactive penalty waits for the goal typing of section
 * 3.7, which is meant to hand a penalty-type goal here whenever a human
 * managed side is playing, on either side, instead of adding it straight to
 * the score.
 */

/**
 * One AI versus AI shootout, decided as an abstract disputa rather than kick
 * by kick.
 */
@SpecRef("3.10")
data class AiShootoutResult(val winner: TeamSide, val winnerGoals: Int, val loserGoals: Int)

/**
 * Resolves an AI shootout.
 *
 * x is drawn first, y second. The home side's tally is always x, and the
 * away side's is always one away from it, x - 1 or x + 1, so the score
 * comes entirely from x and the margin is never anything but one. y never
 * appears in either score; it exists only for the x greater than or equal
 * to y comparison that decides who wins, and a tie is read as a home win.
 *
 * Section 3.10's own prose adds that the winning visitor's score can reach
 * nine. That does not hold for this formula taken on its own: an away win
 * needs x less than y, and with y itself capped at eight that limits x to
 * seven, so the away side's winning score, x plus one, tops out at eight,
 * matching the home side's own ceiling exactly. Nine is only reachable if x
 * could be eight in that branch, which would need a y of nine, outside the
 * range the spec gives y. This function still implements the bolded formula
 * exactly as section 3.10 states it; the nine figure is flagged here as a
 * discrepancy in the spec's own descriptive aside, not silently corrected.
 *
 * Enumerating the full seven by seven grid of the two draws, rather than
 * sampling it, is what pins the documented 28 of 49 home share: 28 of the 49
 * pairs satisfy x greater than or equal to y.
 */
@SpecRef("3.10")
fun aiPenaltyShootout(rules: RuleSet, rng: Rng): AiShootoutResult {
    val x = rng.randRange(rules.penalties.shootoutRollMin, rules.penalties.shootoutRollMax)
    val y = rng.randRange(rules.penalties.shootoutRollMin, rules.penalties.shootoutRollMax)
    return if (x >= y) {
        AiShootoutResult(TeamSide.HOME, winnerGoals = x, loserGoals = x - 1)
    } else {
        AiShootoutResult(TeamSide.AWAY, winnerGoals = x + 1, loserGoals = x)
    }
}

/**
 * The two flags section 3.10 reads off an interactive penalty's taker.
 *
 * topWorld is section 4.10's estrela vermelha and, per that section,
 * implies star: a caller building this from a world Player should read
 * topWorld off Player.topWorld directly and never derive it from
 * Player.star alone, since Player.star already folds a red star player in.
 */
@SpecRef("3.10")
data class PenaltyTaker(val hasFinishing: Boolean, val star: Boolean, val topWorld: Boolean)

/** The three flags section 3.10 reads off the goalkeeper facing the kick. */
@SpecRef("3.10")
data class PenaltyKeeper(val hasPenaltySaving: Boolean, val star: Boolean, val topWorld: Boolean)

/**
 * What happened when a penalty-type goal, in a match with a human managed
 * side, went to the interactive path instead of straight onto the
 * scoreboard.
 *
 * scored and onTarget are named to match MatchEvent.Shot's own fields, so a
 * caller can feed them straight into a Shot event without translation.
 * scored is true only on a conversion. onTarget is true for the conversion
 * and, on a miss, false only for the off target branch.
 *
 * Section 3.10 itself only labels two of the seven miss outcomes as on
 * target, the two that credit nobody; it never says whether the three saved
 * outcomes belong on the on target side of that line too. Counting them as
 * on target here, so five of the seven rather than two, is this
 * implementation's own reading, not something section 3.10 states directly:
 * it leans on section 3.13's own definition of a shot on target as goals
 * plus saves, on the reasoning that a saved penalty is by construction a
 * shot the keeper touched. OPEN-QUESTIONS.md item 58 records both readings
 * for whoever checks this against the binary; if the alternative reading is
 * the right one, a human managed match's shots on target line runs three
 * sevenths of every missed interactive penalty too high, and nothing on
 * this branch would catch that on its own. A caller that feeds onTarget
 * straight into a Shot event is relying on this reading holding.
 *
 * keeperCreditedWithSave marks the one missed branch, three of the seven,
 * that section 3.10 credits to the goalkeeper's own penalty-saved counter,
 * section 3.14 step 6's plus 1.2. That counter is distinct from the
 * shooter's own counter for shots the opposing keeper saved, section 3.14
 * item 5, which credits whoever took the shot rather than the keeper and is
 * fed only by the ordinary section 3.6c shot resolution, never by this
 * function; the keeper's own generic terms live in step 6 alongside the
 * penalty-saved one. OPEN-QUESTIONS.md item 55 records the argument that
 * section 3.14's penalty-saved term looked unreachable, and its refutation;
 * this path is what makes it reachable, so this field is load bearing for
 * whichever later task builds the rating off it, not merely descriptive.
 *
 * Every miss, whichever of the three branches it lands on, is also the
 * taker's own missed-penalty counter: scored false is an exact proxy for
 * it, since section 3.10 gives no way to miss without adding to that
 * counter. Section 3.15 item 15 is the confirmed defect that reads this
 * same counter's own rating term wrong, gated on it but multiplying a
 * different counter entirely; reproducing that defect faithfully is a later
 * task's job, not this one, which only has to produce the counter.
 */
@SpecRef("3.10")
data class InteractivePenaltyResult(
    val scored: Boolean,
    val onTarget: Boolean,
    val keeperCreditedWithSave: Boolean,
)

/**
 * The taker's conversion chance out of a hundred, before the coin is
 * tossed.
 *
 * Every one of the five checks below is independent and none of them short
 * circuits another: a red star taker facing a red star keeper collects the
 * Finalizacao-or-red-star bonus once, ten, and the star bonus once more,
 * five, for fifteen total on the taker's side, while the keeper's red star
 * and star penalties both apply too, for a combined twenty five point swing
 * against him. That a red star player is always a star as well, section
 * 4.10, is exactly why the two taker checks and the two keeper checks are
 * written to combine rather than to treat the higher one as already
 * covering the lower.
 */
@SpecRef("3.10")
internal fun interactivePenaltyThreshold(
    taker: PenaltyTaker,
    keeper: PenaltyKeeper,
    rules: RuleSet,
): Int {
    val penalties = rules.penalties
    var threshold = penalties.interactiveBaseThreshold
    if (taker.hasFinishing || taker.topWorld) {
        threshold += penalties.takerFinishingOrTopWorldBonus
    }
    if (taker.star) {
        threshold += penalties.takerStarBonus
    }
    if (keeper.hasPenaltySaving) {
        threshold -= penalties.keeperPenaltySavingPenalty
    }
    if (keeper.topWorld) {
        threshold -= penalties.keeperTopWorldPenalty
    }
    if (keeper.star) {
        threshold -= penalties.keeperStarPenalty
    }
    return threshold
}

/**
 * Resolves one interactive penalty.
 *
 * The coin, rand(1..100) compared against the threshold, is drawn first and
 * unconditionally: exactly one draw, whatever the taker and keeper are made
 * of. A second draw, rand(N) over the miss table's three shares, is spent
 * only when the coin misses, so a conversion consumes exactly one draw from
 * rng for the whole call and a miss consumes exactly two.
 *
 * The miss draw reads its three shares in the order saved, then wide, then
 * on target without a save. The spec numbers only how many of the seven
 * outcomes fall into each share, never an order among them, so this
 * ordering is this implementation's own choice, made only to keep the three
 * ranges disjoint and exhaustive over rand(missOutcomeCount); nothing in the
 * spec is being read off decompiled draw order here.
 */
@SpecRef("3.10")
fun interactivePenalty(
    taker: PenaltyTaker,
    keeper: PenaltyKeeper,
    rules: RuleSet,
    rng: Rng,
): InteractivePenaltyResult {
    val threshold = interactivePenaltyThreshold(taker, keeper, rules)
    val coin = rng.randRange(COIN_MIN, COIN_MAX)
    if (coin <= threshold) {
        return InteractivePenaltyResult(scored = true, onTarget = true, keeperCreditedWithSave = false)
    }

    val penalties = rules.penalties
    val draw = rng.rand(penalties.missOutcomeCount)
    return when {
        draw < penalties.missSavedOutcomes ->
            InteractivePenaltyResult(scored = false, onTarget = true, keeperCreditedWithSave = true)
        draw < penalties.missSavedOutcomes + penalties.missWideOutcomes ->
            InteractivePenaltyResult(scored = false, onTarget = false, keeperCreditedWithSave = false)
        else ->
            InteractivePenaltyResult(scored = false, onTarget = true, keeperCreditedWithSave = false)
    }
}

@SpecRef("3.10")
private const val COIN_MIN = 1

@SpecRef("3.10")
private const val COIN_MAX = 100
