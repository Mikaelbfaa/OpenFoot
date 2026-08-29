package org.openfoot.engine.match

import org.openfoot.model.GoalType
import org.openfoot.model.PlayerId
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.rand

/**
 * Section 3.7's answer for one goal: what the goal was, who the report says
 * scored it, and who the match goal counter actually moves for.
 *
 * The two are genuinely different questions and this type keeps them apart
 * because the original's answers differ. author is the event: it is the name
 * the match report prints and the name the season scoring chart credits.
 * scorer is the finisher drawn by section 3.6c, and he is who owns the match
 * goal counter that section 3.14 turns into 0,9 a goal. A penalty, a free
 * kick or an olympic goal redirected to a designated player moves author and
 * leaves scorer where it was; an own goal moves author to a defender of the
 * side that conceded and still leaves scorer with the attacking finisher, who
 * collects a match goal that appears nowhere in the report at all. See
 * OPEN-QUESTIONS item 57.
 *
 * typingCredits is the first of section 3.15 item 13's two increments, the
 * one taken at the start of the type draw. It is deliberately computed from
 * the type as drawn rather than from the type after the patches, because that
 * is where the original takes it: an own goal whose author draw comes back
 * empty becomes an open play goal afterwards and still carries the own goal's
 * credit of nought. The second increment is the type's own scoringCredit and
 * is added by whoever puts the goal on the scoreboard, which is why it is not
 * folded in here.
 *
 * assister is filled only for a goal that was drawn as open play. A goal that
 * only became open play because a patch made it one never has one, which is a
 * consequence of the order the patches run in rather than a rule of its own.
 */
@SpecRef("3.7")
internal data class TypedGoal(
    val type: GoalType,
    val author: MatchPlayer?,
    val scorer: MatchPlayer?,
    @property:SpecRef("3.15") val typingCredits: Int,
    @property:SpecRef("3.6") val assister: MatchPlayer?,
)

/**
 * Draws what kind of goal this was and applies section 3.7's four patches to
 * it, in the order section 3.7 states.
 *
 * The order is load bearing and is not an implementation choice. The assist is
 * drawn first, before the olympic and own goal patches, so a goal that only
 * turns into open play because the own goal patch found nobody to blame
 * arrives at the end of this function with no assister at all. Reordering the
 * assist after the patches would silently give that goal one.
 *
 * The draws this function makes, in order, all from the generator handed in:
 *
 * 1. rand(drawBound), once and always, which is the type;
 * 2. the assist, but only when the drawn type is open play and a finisher was
 *    drawn at all: one draw for section 3.6's no assister coin and a second
 *    for the weighted walk when the coin passes and anybody is eligible;
 * 3. the own goal author, one weighted draw, but only when the drawn type is
 *    an own goal and the side that conceded has anybody on the pitch.
 *
 * Neither the olympic patch nor the penalty and free kick patch draws
 * anything: both are a lookup of a designation that was decided when the squad
 * was built.
 *
 * The olympic branch section 3.7 describes as unreachable is deliberately not
 * ported. It would turn an olympic goal into an open play one when the drawn
 * player is a goalkeeper, and section 3.6's finisher draw already excludes
 * every natural keeper, so nothing can reach it. Section 3.15 item 16 names it
 * as one of three such branches and says not to port any of them.
 *
 * A null finisher is the unreachable case section 3.6 documents, a side with
 * nobody on the pitch at all. The whole authorship collapses to null with it
 * and the assist is not drawn, since section 3.6's own draw excludes the
 * finisher by identity and has no meaning without one.
 */
@SpecRef("3.7")
internal fun typeGoal(
    setup: MatchSetup,
    scoringSide: TeamSide,
    finisher: MatchPlayer?,
    rng: Rng,
): TypedGoal {
    val rules = setup.rules
    val attacking = setup.side(scoringSide)
    val defending = setup.side(scoringSide.opponent)

    val drawn = rules.goalTypes.typeOf(rng.rand(rules.goalTypes.drawBound))

    val assister = if (drawn == GoalType.OPEN_PLAY && finisher != null) {
        selectAssister(attacking, finisher, rules, rng)
    } else {
        null
    }

    var type = drawn
    var author = finisher

    if (type == GoalType.OLYMPIC) {
        author = attacking.onPitch(attacking.designated.cornerTaker) ?: author
    }

    if (type == GoalType.OWN_GOAL) {
        val blamed = drawOwnGoalAuthor(defending, rules, rng)
        if (blamed == null) {
            type = GoalType.OPEN_PLAY
        } else {
            author = blamed
        }
    }

    if (type == GoalType.PENALTY || type == GoalType.FREE_KICK) {
        author = attacking.onPitch(attacking.designated.taker) ?: author
    }

    return TypedGoal(
        type = type,
        author = author,
        scorer = finisher,
        typingCredits = drawn.typingCredit,
        assister = assister,
    )
}

/**
 * The player of the side that conceded whom the report blames for an own goal,
 * or null when that side has nobody on the pitch to blame.
 *
 * The weights are section 3.7's own and are nothing like the finisher's: the
 * six centre back cells carry eighteen each against one for a forward, so an
 * own goal almost always reads as a defender's mistake. A cell outside one to
 * twenty five is filtered out before weighting rather than given a weight of
 * nought, the same way section 3.6's two draws filter, so a bench entry can
 * never be blamed.
 *
 * Null is what section 3.7 calls the side returning nobody, and it is the
 * caller's job to turn the goal into an open play one when it happens.
 */
@SpecRef("3.7")
internal fun drawOwnGoalAuthor(defending: MatchSide, rules: RuleSet, rng: Rng): MatchPlayer? {
    val goalTypes = rules.goalTypes
    val candidates = defending.lineup.filter { it.slot.value in goalTypes.ownGoalEligibleSlots }
    if (candidates.isEmpty()) {
        return null
    }
    val weights = DoubleArray(candidates.size) {
        goalTypes.ownGoalSlotWeights.getOrElse(candidates[it].slot.value) { 0 }.toDouble()
    }
    return candidates[weightedPick(weights, rng)]
}

/**
 * The fielded player carrying the given identity, or null when nobody on the
 * pitch does.
 *
 * A designation names a squad index and survives a player being left out, so
 * both halves of the question have to be asked: section 3.7 credits a
 * designated player only while he is actually on the pitch. Bench entries are
 * excluded by the cell rather than by the bench list, so a reserve who has
 * come on is found and one who has not is not.
 */
@SpecRef("3.7")
private fun MatchSide.onPitch(id: PlayerId?): MatchPlayer? =
    if (id == null) null else lineup.firstOrNull { it.id == id && it.slot.isPitch }

/**
 * What a goal tick turned into once section 3.7 had typed it: whether the goal
 * reached the scoreboard, whether the attempt counts as on target, and the
 * events the log takes from it.
 *
 * scored and onTarget are separate from the shot the tick already resolved
 * because section 3.7's penalty in a human sided match can take both of them
 * away again. Everything else that reaches this point scored, and did so on
 * target, exactly as the tick said.
 */
@SpecRef("3.7")
internal data class ResolvedGoal(
    val scored: Boolean,
    @property:SpecRef("3.13") val onTarget: Boolean,
    @property:SpecRef("3.13") val events: List<MatchEvent>,
)

/**
 * Types a goal and settles what the match does with it.
 *
 * A goal in an AI versus AI match, and every goal of any other type, goes
 * straight onto the scoreboard with the two credits of section 3.15 item 13
 * behind it. A goal that types as a penalty in a match with a human managed
 * club on either side does not: section 3.7 hands it to section 3.10's
 * interactive penalty instead, and that is what decides whether it is a goal
 * at all. Nothing is lost by the detour, because the same condition sends the
 * match to the viewer in the first place, so either the interactive penalty
 * confirms the goal or the viewer adds it.
 *
 * The interactive path is the one place where the match goal credit does not
 * follow the drawn finisher. Section 3.10 gives the single goal it adds to the
 * batedor, the man who actually took the kick, and that is the author section
 * 3.7 has just decided on: the designated taker when he is on the pitch and
 * the drawn finisher when he is not. OPEN-QUESTIONS item 51 records the
 * arithmetic, one credit rather than two, because the viewer increments once
 * and the typing increment a penalty never gets is nought.
 */
@SpecRef("3.7")
internal fun resolveGoal(
    setup: MatchSetup,
    scoringSide: TeamSide,
    finisher: MatchPlayer?,
    minute: Int,
    rng: Rng,
): ResolvedGoal {
    val typed = typeGoal(setup, scoringSide, finisher, rng)
    if (typed.type == GoalType.PENALTY && setup.hasHumanSide) {
        return resolveInteractivePenalty(setup, scoringSide, typed, minute, rng)
    }
    val goal = MatchEvent.Goal(
        minute = minute,
        side = scoringSide,
        type = typed.type,
        author = typed.author,
        scorer = typed.scorer,
        matchGoalCredits = typed.typingCredits + typed.type.scoringCredit,
        assister = typed.assister,
    )
    return ResolvedGoal(scored = true, onTarget = true, events = listOf(goal))
}

/**
 * Sends a penalty type goal from a human sided match through section 3.10.
 *
 * The keeper is whoever stands in the keeper's cell of the side that conceded,
 * and he can legitimately be nobody: a side that loses its goalkeeper with an
 * empty bench plays the rest of the match with that cell empty, which is the
 * same state section 3.4's missing keeper rating already covers. Section 3.10
 * never says what such a penalty is worth, so the reading here is that an
 * absent keeper moves no threshold and is credited with no save.
 *
 * The interactive penalty replaces the tick's own shot rather than adding a
 * second one. Section 3.10 says a converted penalty counts as a shot and a
 * shot on target and that a missed one counts as a shot, which is exactly one
 * attempt, and the tick has already recorded one; counting both would put more
 * attempts in a match than it has minutes.
 */
@SpecRef("3.10")
private fun resolveInteractivePenalty(
    setup: MatchSetup,
    scoringSide: TeamSide,
    typed: TypedGoal,
    minute: Int,
    rng: Rng,
): ResolvedGoal {
    val defending = setup.side(scoringSide.opponent)
    val keeper = defending.lineup.firstOrNull { it.slot.value == setup.rules.keeperSlot }
    val outcome = interactivePenalty(typed.author, keeper, setup.rules, rng)

    val kick = MatchEvent.InteractivePenalty(
        minute = minute,
        side = scoringSide,
        taker = typed.author,
        keeper = keeper,
        scored = outcome.scored,
        keeperSaved = outcome.keeperCreditedWithSave,
    )
    if (!outcome.scored) {
        return ResolvedGoal(scored = false, onTarget = outcome.onTarget, events = listOf(kick))
    }

    val goal = MatchEvent.Goal(
        minute = minute,
        side = scoringSide,
        type = typed.type,
        author = typed.author,
        scorer = typed.author,
        matchGoalCredits = INTERACTIVE_PENALTY_CREDIT,
        assister = null,
    )
    return ResolvedGoal(scored = true, onTarget = true, events = listOf(kick, goal))
}

/**
 * How many match goals a converted interactive penalty is worth to the man who
 * took it.
 *
 * One, and not the two an open play goal is worth. Section 3.7's typing
 * increment skips every penalty, and the increment that rides along with the
 * scoreboard is skipped too because section 3.7 never adds this goal to the
 * score; the single credit here is the one section 3.10 says the viewer adds
 * when the kick goes in.
 */
@SpecRef("3.10")
private const val INTERACTIVE_PENALTY_CREDIT = 1
