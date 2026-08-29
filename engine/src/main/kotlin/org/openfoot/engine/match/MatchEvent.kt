package org.openfoot.engine.match

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
 * Only the events the engine can currently produce are declared. Cards,
 * injuries and substitutions are declared below; goal typing is still
 * outstanding and arrives with the code that produces it, because a case
 * nothing can construct is a branch nothing can test.
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
        }
    }

    return MatchStats(home, away)
}
