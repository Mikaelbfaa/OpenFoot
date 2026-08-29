package org.openfoot.model

/**
 * How section 3.7 describes a goal, and how many times that description moves
 * the drawn finisher's match goal counter.
 *
 * The counter this counts is the match one, the figure section 3.14 reads to
 * award 0,9 a goal. It is not the season scoring chart, which is built from
 * the events instead and credits the displayed author exactly once.
 *
 * The two credits are separate properties because the original increments the
 * match counter at two different moments and only one of them is
 * unconditional. typingCredit is the increment section 3.15 item 13 places at
 * the start of the type draw, which every drawn type collects except a
 * penalty and an own goal. scoringCredit is the increment that rides along
 * with adding the goal to the score, which every type collects and which is
 * skipped only when the goal never reaches the score at all: the penalty of a
 * human sided match, which section 3.7 hands to section 3.10's interactive
 * penalty instead.
 *
 * matchGoalCredits is their sum, so a goal that does reach the score is worth
 * two to the counter for open play, a free kick and an olympic goal, and one
 * for a penalty in an AI versus AI match and for an own goal. That is why an
 * open play goal is worth 1,8 of rating and not 0,9. See OPEN-QUESTIONS item
 * 51.
 *
 * The credits live on the type rather than on the code that scores a goal, so
 * that no call site can double a goal on its own and no reader has to find
 * both increments to know what a type is worth.
 */
@SpecRef("3.7")
enum class GoalType(
    @property:SpecRef("3.15") val typingCredit: Int,
    @property:SpecRef("3.15") val scoringCredit: Int,
) {
    OPEN_PLAY(1, 1),
    PENALTY(0, 1),
    FREE_KICK(1, 1),
    OWN_GOAL(0, 1),
    OLYMPIC(1, 1),
    ;

    /**
     * What one goal of this type is worth to the drawn finisher's match goal
     * counter, once it has actually been added to the score.
     */
    @SpecRef("3.15")
    val matchGoalCredits: Int get() = typingCredit + scoringCredit
}
