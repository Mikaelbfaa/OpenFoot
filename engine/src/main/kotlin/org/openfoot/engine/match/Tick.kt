package org.openfoot.engine.match

import org.openfoot.model.Rng
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide

/** What one tick of the engine produced. */
@SpecRef("3.5")
enum class TickEvent {
    GOAL,
    SAVE,
    WIDE,
    TACKLE,
    MISPLACED_PASS,
}

/**
 * One tick, resolved.
 *
 * The side in possession and the side that won the duel are both carried,
 * because they are different questions and the statistics need both. Possession
 * says who the tick belonged to and decides who a shot, a tackle or a misplaced
 * pass is credited to. The duel winner is what the displayed possession
 * percentage counts, and it is not folded into possessor because section 3.5
 * alternates possession unconditionally at the end of every tick regardless of
 * who won the duel, and that alternation is the caller's job, not this one's.
 *
 * A tackle always belongs to the side that was not the possessor, and every
 * other event, shot outcomes and the misplaced pass alike, belongs to the
 * possessor. That pairing is fixed by the enum itself, so there is no separate
 * field to get wrong.
 *
 * The shooter is null exactly when the tick produced no shot: a lost
 * possession duel, or a chance duel that did not come off. Section 3.6's own
 * draw falls back to the last player of the pitch lineup whenever it finds
 * nobody eligible, so a shot only ever carries a null shooter when the
 * possessing side has no player on the pitch at all, which the engine never
 * actually reaches; isShot does not imply a non null shooter, only the other
 * direction holds.
 */
@SpecRef("3.5")
data class TickOutcome(
    val possessor: TeamSide,
    val possessionWinner: TeamSide,
    val event: TickEvent,
    @property:SpecRef("3.6") val shooter: MatchPlayer? = null,
) {
    val isShot: Boolean
        get() = event == TickEvent.GOAL || event == TickEvent.SAVE || event == TickEvent.WIDE
}

/**
 * One tick of the engine, worth about a minute of play.
 *
 * The draw count varies with the path: two for a lost possession duel, three
 * for a chance that does not come off, four for a shot. That variance is why
 * the simulation gives every minute its own stream rather than threading one
 * generator through the match.
 *
 * The goal count passed in is the possessing side's own, not the opponent's.
 * Section 3.6c worsens the conversion of a side that has already scored, which
 * is the original's brake on blowouts, so passing the opponent's total would
 * invert the brake instead of applying it.
 *
 * Possession is not alternated here. This reports who won the duel and leaves
 * the alternation to the loop, because section 3.5 alternates unconditionally
 * every tick while the displayed possession percentage counts duel wins, and
 * folding the two together would make the duel winner unrecoverable by the
 * caller.
 */
@SpecRef("3.5")
fun playTick(
    setup: MatchSetup,
    possessor: TeamSide,
    goalsScoredByPossessor: Int,
    rng: Rng,
): TickOutcome {
    val winner = possessionDuel(setup, possessor, rng)
    if (winner != possessor) {
        return TickOutcome(possessor, winner, looseBall(rng))
    }
    if (chanceDuel(setup, possessor, rng) == ChanceOutcome.NO_SHOT) {
        return TickOutcome(possessor, winner, looseBall(rng))
    }

    val shooter = selectShooter(setup.side(possessor), setup.rules, rng)
    val event = when (shotOutcome(setup, possessor, shooter, goalsScoredByPossessor, rng)) {
        ShotOutcome.GOAL -> TickEvent.GOAL
        ShotOutcome.SAVED -> TickEvent.SAVE
        ShotOutcome.WIDE -> TickEvent.WIDE
    }
    return TickOutcome(possessor, winner, event, shooter)
}

/**
 * A tick that produced no shot ends evenly in a tackle by the side that was not
 * the possessor, or in a misplaced pass by the possessor itself. The credit is
 * fixed by the event returned, not by a separate field: TACKLE always means the
 * non possessor, MISPLACED_PASS always means the possessor.
 *
 * Section 3.5 writes this coin twice, once for a lost possession duel and once
 * for a chance that does not come off, and both times it is the same even
 * split, so it is one function and one draw. It goes through the weighted pick
 * primitive so that the boundary convention is decided in exactly one place.
 */
@SpecRef("3.5")
private fun looseBall(rng: Rng): TickEvent =
    if (weightedPick(EVEN_COIN, rng) == 0) TickEvent.TACKLE else TickEvent.MISPLACED_PASS

@SpecRef("3.5")
private val EVEN_COIN = doubleArrayOf(1.0, 1.0)
