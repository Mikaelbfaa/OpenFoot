package org.openfoot.engine.match

import org.openfoot.model.GoalType
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide

/**
 * One thing that happened in one minute of a match.
 *
 * The log is what a match returns, and everything else is read out of it.
 * Statistics are derived rather than accumulated beside it so that no pair of
 * counters can disagree, and the live viewer of a later version replays the
 * list rather than re-deriving a timeline the engine already knew.
 *
 * Only the events the engine can currently produce are declared, so a case
 * nothing can construct is never a branch nothing can test.
 */
@SpecRef("3.13")
sealed interface MatchEvent {

    /** The minute the event happened in, counted from nought. */
    val minute: Int

    /** The side the event is credited to. */
    val side: TeamSide

    /**
     * An attempt on goal.
     *
     * The shooter is null only when the side has no player on the pitch at
     * all, which the engine never actually reaches. Section 3.6's draw falls
     * back to the last player of the pitch lineup whenever the exclusions
     * leave nobody eligible, so a shot is never cancelled either.
     *
     * A scored shot must be on target. toStats reads a side's goals as a
     * subset of its shots on target, so an off target goal would let goals
     * exceed on target in the derived statistics, which section 3.13's own
     * figures never allow. events() never builds one, since GOAL always
     * pairs onTarget true with scored true, but nothing else stops a hand
     * built Shot from doing so.
     *
     * The shooter is a MatchPlayer reference, and MatchPlayer is not a data
     * class, so two of them are equal only when they are the same object. A
     * substitution builds a new MatchPlayer for the same man standing in a
     * different cell, so once substitutions exist one man can appear in this
     * log under more than one object. Anything rolling a per player figure out
     * of the log, a top scorer or a shot count, must group by shooter.id,
     * which is his index in the squad and stable for the whole match, and
     * never by the object.
     */
    @SpecRef("3.6")
    data class Shot(
        override val minute: Int,
        override val side: TeamSide,
        val shooter: MatchPlayer?,
        val onTarget: Boolean,
        val scored: Boolean,
    ) : MatchEvent {
        init {
            require(!scored || onTarget) { "a scored shot must be on target" }
        }
    }

    /**
     * A goal, as section 3.7 typed it.
     *
     * side is the side the goal counts for, in every case including an own
     * goal: the author of an own goal plays for side.opponent and the goal
     * still belongs to side. Nothing here is read by the statistics of
     * section 3.13, which take the goal from the Shot event of the same
     * minute; this is the record of who did it and of what it is worth.
     *
     * author and scorer are two different credits and the whole point of
     * this event is that they can disagree. author is what the report prints
     * and what the season scoring chart credits, and it is the designated
     * taker of a redirected penalty, free kick or olympic goal, or a
     * defender of the conceding side for an own goal. scorer is the finisher
     * section 3.6c drew, and he is who owns the match goal counter that
     * section 3.14 reads; he keeps it through every redirection, including
     * the own goal, where he collects a match goal without appearing in the
     * report at all. See OPEN-QUESTIONS item 57.
     *
     * matchGoalCredits is how many times scorer's match counter moved for
     * this goal, and it is carried rather than derived from the type because
     * the two can disagree. Section 3.15 item 13's first increment is taken
     * from the type as drawn rather than from the type after section 3.7's
     * patches, and a converted interactive penalty is credited once by
     * section 3.10's viewer rather than by either of item 13's increments.
     * Anything summing a player's rating reads this number and never
     * multiplies a goal by anything of its own.
     *
     * An own goal credits the season scoring chart to nobody at all, which
     * is a fact about the chart rather than about this event: the author is
     * recorded here because the report names him and because section 3.14
     * charges him 1,5.
     *
     * author and scorer are nullable for the same unreachable reason
     * Shot.shooter is: a side with nobody at all on the pitch has no
     * finisher to draw and therefore nobody to credit.
     *
     * Only the two fields that come from somewhere other than section 3.7
     * carry a section of their own, which is how every other event in this
     * file and every carrier in the match package is annotated: a property
     * whose section is the class's own inherits it, and one that reaches
     * outside names where it reaches. matchGoalCredits is section 3.15 item
     * 13's and assister is section 3.6's.
     */
    @SpecRef("3.7")
    data class Goal(
        override val minute: Int,
        override val side: TeamSide,
        val type: GoalType,
        val author: MatchPlayer?,
        val scorer: MatchPlayer?,
        @property:SpecRef("3.15") val matchGoalCredits: Int,
        @property:SpecRef("3.6") val assister: MatchPlayer?,
    ) : MatchEvent {
        init {
            require(assister == null || type == GoalType.OPEN_PLAY) {
                "section 3.7 draws an assist only for an open play goal, not for a $type"
            }
            require(matchGoalCredits >= 0) {
                "a goal cannot be worth $matchGoalCredits to the match goal counter"
            }
        }
    }

    /**
     * One penalty taken through section 3.10's interactive path.
     *
     * Logged for every such kick, converted or not, because both outcomes
     * carry a counter section 3.14 reads and neither can be recovered from
     * the Shot of the same minute. A conversion is followed by a Goal event
     * in the same minute; a miss is not, and the miss is itself the taker's
     * missed penalty counter, the one section 3.15 item 15's rating term is
     * gated on.
     *
     * keeperSaved marks the three of section 3.10's seven miss outcomes that
     * credit the goalkeeper with a penalty saved, section 3.14 step 6's plus
     * 1,2. The other four misses credit him with nothing, and two of those
     * four still count as shots on target.
     *
     * side is the side that was awarded the penalty, so the keeper plays for
     * side.opponent. Both players are nullable: the taker for the
     * unreachable reason Shot.shooter is, and the keeper because a side that
     * loses its goalkeeper with an empty bench really does play on with that
     * cell empty.
     *
     * No property carries a section of its own, unlike Goal above, and that
     * is not an omission: every field here is section 3.10's, including
     * keeperSaved, whose counter section 3.10 defines and section 3.14 only
     * later reads.
     */
    @SpecRef("3.10")
    data class InteractivePenalty(
        override val minute: Int,
        override val side: TeamSide,
        val taker: MatchPlayer?,
        val keeper: MatchPlayer?,
        val scored: Boolean,
        val keeperSaved: Boolean,
    ) : MatchEvent {
        init {
            require(!(scored && keeperSaved)) {
                "a converted penalty cannot also be a save"
            }
        }
    }

    /** A tackle, credited to the side that did not have the ball. */
    @SpecRef("3.5")
    data class Tackle(override val minute: Int, override val side: TeamSide) : MatchEvent

    /** A misplaced pass, credited to the side that had the ball. */
    @SpecRef("3.5")
    data class MisplacedPass(override val minute: Int, override val side: TeamSide) : MatchEvent

    /**
     * A possession duel won.
     *
     * Logged separately from who had the ball, because section 3.5 alternates
     * possession unconditionally every tick while the percentage the original
     * displays counts duel wins. Folding the two together would make the
     * displayed number unrecoverable.
     */
    @SpecRef("3.5")
    data class PossessionWon(override val minute: Int, override val side: TeamSide) : MatchEvent

    /**
     * A yellow card.
     *
     * Section 3.8's suspension rule counts a sending off for a second yellow
     * as a yellow too, so a second booking is logged here as well as under
     * SendingOff. Anything counting a player's yellows counts these events and
     * never has to special case the dismissal.
     */
    @SpecRef("3.8")
    data class Booking(
        override val minute: Int,
        override val side: TeamSide,
        val player: MatchPlayer,
    ) : MatchEvent

    /**
     * A dismissal, of either kind.
     *
     * Section 3.8 calls a second yellow an event distinct from a direct red,
     * and the two differ in what they cost the player afterwards: a direct red
     * draws a ban of one to ten matches, a second yellow is a single match. The
     * consequence on the pitch is the same, so this is one event with a flag
     * rather than two, and the flag is what the post round rules of v0.3 will
     * read.
     */
    @SpecRef("3.8")
    data class SendingOff(
        override val minute: Int,
        override val side: TeamSide,
        val player: MatchPlayer,
        val secondYellow: Boolean,
    ) : MatchEvent

    /**
     * An injury, with how long it keeps the player out.
     *
     * The days are computed inside the match because section 3.8's formula
     * reads the player's energy, which only the match knows. The permanent
     * strength loss is carried rather than applied for the opposite reason:
     * strength lives on the squad, which the match deliberately cannot reach,
     * so the number is reported and the season applies it.
     */
    @SpecRef("3.8")
    data class Injury(
        override val minute: Int,
        override val side: TeamSide,
        val player: MatchPlayer,
        val days: Int,
        val permanentStrengthLoss: Int,
    ) : MatchEvent {
        init {
            require(days >= 0) { "an injury cannot last $days days" }
            require(permanentStrengthLoss >= 0) { "a strength loss cannot be negative" }
        }
    }

    /**
     * One player replaced by another.
     *
     * The player coming on carries the cell he is filling, not the minus one a
     * reserve sits with, because every aggregate of section 3.4 reads the cell.
     * He is therefore a different MatchPlayer object from the one on the bench,
     * with the same identity; see Shot above on why per player figures group by
     * identity and never by object.
     */
    @SpecRef("3.8")
    data class Substitution(
        override val minute: Int,
        override val side: TeamSide,
        val off: MatchPlayer,
        val on: MatchPlayer,
        val reason: SubstitutionReason,
    ) : MatchEvent {
        init {
            require(off.id != on.id) { "${off.id} cannot replace himself" }
        }
    }
}

/**
 * Why the AI made a substitution.
 *
 * The four windows of section 3.8 plus the two forced ones. Carried on the
 * event rather than inferred from the minute, because two windows can fall on
 * the same minute and a reader of the log should not have to guess which fired.
 */
@SpecRef("3.8")
enum class SubstitutionReason {
    INJURY,
    SENDING_OFF,
    HALF_TIME,
    CHASING,
    TIREDNESS,
}

/**
 * Reads the counters of section 3.13 out of a log.
 *
 * On target is goals plus saves, so shots is always on target plus wide. Both
 * are counted rather than one derived from the other, because the original
 * counts them separately and a future defect may make them disagree.
 *
 * Fouls are never touched. Section 3.13 documents the counter as one the
 * original declares and never increments, so every match reports nought.
 *
 * Section 3.13's panel has no card column and no injury column, so Booking,
 * SendingOff, Injury and Substitution are logged and counted nowhere here.
 *
 * Section 3.7's Goal and section 3.10's InteractivePenalty are counted
 * nowhere here either, and for a stronger reason than the four above: the
 * shot they belong to has already been counted. A goal is the Shot of the
 * same minute carrying scored, and an interactive penalty is that same Shot
 * carrying whatever section 3.10 decided, so counting either again would
 * report more attempts than the match had minutes.
 */
@SpecRef("3.13")
fun List<MatchEvent>.toStats(): MatchStats {
    var home = SideStats()
    var away = SideStats()

    fun update(side: TeamSide, change: (SideStats) -> SideStats) {
        if (side == TeamSide.HOME) home = change(home) else away = change(away)
    }

    for (event in this) {
        when (event) {
            is MatchEvent.Shot -> update(event.side) {
                it.copy(
                    goals = it.goals + if (event.scored) 1 else 0,
                    shots = it.shots + 1,
                    onTarget = it.onTarget + if (event.onTarget) 1 else 0,
                    wide = it.wide + if (event.onTarget) 0 else 1,
                )
            }

            is MatchEvent.Tackle -> update(event.side) { it.copy(tackles = it.tackles + 1) }

            is MatchEvent.MisplacedPass -> update(event.side) {
                it.copy(misplacedPasses = it.misplacedPasses + 1)
            }

            is MatchEvent.PossessionWon -> update(event.side) {
                it.copy(possessionsWon = it.possessionsWon + 1)
            }

            is MatchEvent.Booking, is MatchEvent.SendingOff,
            is MatchEvent.Injury, is MatchEvent.Substitution -> Unit

            is MatchEvent.Goal, is MatchEvent.InteractivePenalty -> Unit
        }
    }

    return MatchStats(home, away)
}
