package org.openfoot.engine.match

import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.Slot
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.rand

/**
 * Which side suffers this minute's roll.
 *
 * The one thing in the whole engine that resembles refereeing bias: the away
 * side is drawn more often than the home side, which is why it collects more
 * cards and more injuries over a season without any other rule saying so.
 */
@SpecRef("3.8")
internal fun victimSide(rng: Rng, rules: RuleSet): TeamSide =
    if (rng.rand(VICTIM_DRAW_BOUND) > rules.discipline.victimHomeThreshold) {
        TeamSide.HOME
    } else {
        TeamSide.AWAY
    }

/**
 * Which of the three phases of its own half a minute sits in.
 *
 * Counted inside the half rather than from kick off. Section 3.8 gives one
 * table per half with three phases each, and counting across the match would
 * leave the second half permanently in the last phase, making four of the six
 * cells of every table unreachable. See OPEN-QUESTIONS item 38.
 */
@SpecRef("3.8")
internal fun disciplinePhase(minute: Int, clock: MatchClock, rules: RuleSet): Int {
    val intoHalf = clock.intoHalf(minute)
    return rules.discipline.phaseBounds.count { intoHalf >= it }
}

@SpecRef("3.8")
private const val VICTIM_DRAW_BOUND = 100

/** This minute's three thresholds, after every adjustment section 3.8 names. */
@SpecRef("3.8")
internal data class MinuteThresholds(val yellow: Int, val red: Int, val injury: Int)

/**
 * This minute's three card and injury thresholds for the victim's side.
 *
 * Order matters and is the spec's own: the base table cell, then the
 * victim's marking relief on the yellow threshold only, then the more than
 * five yellows doubling, then the two or more sendings off overwrite, then
 * the one or more injuries overwrite, each later step replacing rather than
 * compounding with the one before it, so a match that has already had a
 * sending off and an injury has its doubling erased rather than multiplied
 * further.
 *
 * Section 3.15 item 5 also names a branch for more than ten yellows, on top
 * of these four. It is deliberately not ported: the same item's own note
 * calls that branch unreachable in the original, so there is nothing here
 * for it to repair or reproduce. A later reader who does not find it should
 * read this as the reason, not as an oversight.
 */
@SpecRef("3.8")
internal fun minuteThresholds(
    minute: Int,
    clock: MatchClock,
    victim: MatchSide,
    counts: DisciplineCounts,
    rules: RuleSet,
): MinuteThresholds {
    val half = clock.halfOf(minute)
    val phase = disciplinePhase(minute, clock, rules)
    val rates = rules.discipline

    val red = rates.red.of(half).of(phase)
    val injury = rates.injury.of(half).of(phase)

    var yellow = rates.yellow.of(half).of(phase) + rates.markingRelief(victim.marking)
    if (counts.yellows >= rules.manyYellowsAtLeast) {
        yellow *= rules.manyYellowsFactor
    }
    if (counts.sendingsOff >= rules.manyRedsAtLeast) {
        yellow = rules.redOverwriteFactor * red
    }
    if (counts.injuries >= rules.anyInjuryAtLeast) {
        yellow = rules.injuryOverwriteFactor * injury
    }

    return MinuteThresholds(yellow, red, injury)
}

/**
 * Section 3.8's whole per minute roll, run once before every tick.
 *
 * The chain resolves at the first thing that matches and then stops, which is
 * the spec's own order: yellow, then direct red, then injury, then the AI's
 * substitution window. A minute that produces a card never rolls for an
 * injury, and a minute that produces anything at all never opens a chasing or
 * a routine window.
 *
 * The interval window is the exception and is opened whatever the chain did.
 * Section 3.8 gates the chain's fourth branch on the second half and the fifth
 * minute of it, and the interval sits at the nought'th minute of the second
 * half, so the interval cannot be that fourth branch: it could never fire
 * there. It is the separate paragraph section 3.8 opens with "No intervalo",
 * and a mechanism of its own is not gated by the chain's outcome. Reading it
 * as the fourth branch would silently cost every interval window that happened
 * to fall in a minute with a card in it, which is a reading under which part
 * of the rule can never run rather than a reading of the rule.
 *
 * The draws, in the order the stream produces them:
 *
 * 1. the victim side, rand(100) against the home threshold
 * 2. no draw for the thresholds, which are read off the tables
 * 3. the yellow roll, rand of this minute's yellow threshold
 * 4. on a miss, the red roll, rand of this minute's red threshold
 * 5. on a miss, the injury roll, rand of this minute's injury threshold
 * 6. on a hit, the risk group, one rand against that event's own table
 * 7. then the player, one rand over whoever stands in the group's cells
 * 8. for an injury only, the three duration draws of injuryOutcome
 *
 * Steps one to seven come from DISCIPLINE_STREAM, a child of this minute's own
 * generator. The substitution windows draw from SUBSTITUTION_STREAM, a sibling
 * of it, so that a minute in which nothing fired cannot move a card's draw in
 * any other minute and a card cannot move a substitution's.
 *
 * The chain resolves at the first roll that matches, not at the first event
 * that reaches the log. A roll that matches and then draws a risk group whose
 * cells nobody stands in logs nothing at all, and the minute still ends there
 * rather than falling through to the next roll or to the window. That is the
 * spec's own wording, and it is only reachable at all on a side already short
 * of players. The matching roll still counts as an attempt in DisciplineCounts
 * even then; see that type's own docstring for why.
 *
 * The window opens for both sides independently rather than only for the side
 * this minute's victim draw landed on. Section 3.8 writes it as the fourth
 * branch of the victim chain, but every side draws its own minutes, and under
 * the literal reading a side's own minute would only fire when it happened to
 * be drawn as that minute's victim as well. See OPEN-QUESTIONS item 43 and the
 * docstring on runSubstitutionWindow, which carries the arithmetic.
 *
 * Internal rather than private so a test can hand it a state from the middle
 * of a match with a scripted generator and pin the order above draw by draw.
 */
@SpecRef("3.8")
internal fun MatchState.disciplineMinute(minute: Int, clock: MatchClock, rng: Rng): MatchState {
    val chain = rng.fork(DISCIPLINE_STREAM)
    val team = victimSide(chain, setup.rules)
    val thresholds = minuteThresholds(minute, clock, setup.side(team), counts, setup.rules)

    val resolved = when {
        chain.rand(thresholds.yellow) == EVENT_FIRES ->
            countedYellow().book(team, minute, chain)
        chain.rand(thresholds.red) == EVENT_FIRES ->
            countedSendingOff().sendOff(team, minute, chain)
        chain.rand(thresholds.injury) == EVENT_FIRES ->
            countedInjury().injure(team, minute, chain)
        else -> null
    }

    if (resolved != null && !clock.isInterval(minute)) {
        return resolved
    }
    return (resolved ?: this).openSubstitutionWindows(minute, clock, rng)
}

/**
 * The three counters below move the moment their own roll matches, not the
 * moment an event reaches the log.
 *
 * Section 3.8 says outright that they are incremented even when the risk
 * group drawn afterwards holds nobody and no card or injury happens. book,
 * sendOff and injure therefore never touch DisciplineCounts themselves; the
 * increment always happens here, on the state the chain hands them, before
 * drawRiskGroup is ever called. See DisciplineCounts's own docstring.
 */
@SpecRef("3.8")
private fun MatchState.countedYellow(): MatchState = copy(counts = counts.copy(yellows = counts.yellows + 1))

/** See countedYellow. Only a direct red reaches this branch of the chain. */
@SpecRef("3.8")
private fun MatchState.countedSendingOff(): MatchState =
    copy(counts = counts.copy(sendingsOff = counts.sendingsOff + 1))

/** See countedYellow. */
@SpecRef("3.8")
private fun MatchState.countedInjury(): MatchState = copy(counts = counts.copy(injuries = counts.injuries + 1))

/**
 * A booking, and the dismissal it becomes when it is the player's second.
 *
 * Both events are logged for a second yellow, but only the yellow counter
 * moves for it: section 3.8's suspension rule counts a sending off for a
 * second yellow as a yellow as well, but the three threshold overwrites read
 * a sendingsOff counter that only a direct red feeds. See OPEN-QUESTIONS item
 * 39. The yellow counter itself is not touched here at all; it was already
 * moved by disciplineMinute's countedYellow the moment the roll matched, which
 * is what makes it count the attempt rather than the card.
 *
 * A group whose cells nobody stands in logs no card at all, but the attempt
 * already counted. drawVictim skips its own draw in that case rather than
 * making it and throwing it away, so an unusual shape does not shift the rest
 * of the stream.
 */
@SpecRef("3.8")
private fun MatchState.book(team: TeamSide, minute: Int, rng: Rng): MatchState {
    val group = drawRiskGroup(setup.rules.discipline.yellowRisk, rng)
    val player = drawVictim(setup.side(team), group, setup.rules, rng) ?: return this

    val side = of(team)
    val booked = (side.bookings[player.id] ?: 0) + 1
    val carded = with(team, side.copy(bookings = side.bookings + (player.id to booked))).copy(
        log = log + MatchEvent.Booking(minute, team, player),
    )

    return if (booked < BOOKINGS_BEFORE_DISMISSAL) {
        carded
    } else {
        carded.dismiss(team, player, minute, secondYellow = true)
    }
}

/**
 * A direct red, which section 3.8 draws from a table of its own.
 *
 * The sendingsOff counter is not touched here: disciplineMinute's
 * countedSendingOff already moved it the moment the red roll matched, so it
 * counts the attempt rather than the card, and a group whose cells nobody
 * stands in still leaves it moved even though dismiss never runs.
 */
@SpecRef("3.8")
private fun MatchState.sendOff(team: TeamSide, minute: Int, rng: Rng): MatchState {
    val group = drawRiskGroup(setup.rules.discipline.redRisk, rng)
    val player = drawVictim(setup.side(team), group, setup.rules, rng) ?: return this
    return dismiss(team, player, minute, secondYellow = false)
}

/**
 * What a dismissal of either kind costs the side.
 *
 * The player leaves and is not replaced: a side reduced to ten stays at ten
 * for the rest of the match, which is what makes section 3.4's fixed divisors
 * bite. What follows is the shape keeping rule and not a replacement for him.
 *
 * Neither counter is touched here. A second yellow's yellow was already moved
 * by book's caller when the yellow roll matched, and a direct red's sendingsOff
 * was already moved by sendOff's caller when the red roll matched; a second
 * yellow never moves sendingsOff at all, by design, since only a direct red
 * feeds the overwrite that counter drives.
 */
@SpecRef("3.8")
private fun MatchState.dismiss(
    team: TeamSide,
    player: MatchPlayer,
    minute: Int,
    secondYellow: Boolean,
): MatchState = copy(
    log = log + MatchEvent.SendingOff(minute, team, player, secondYellow),
).leavePitch(team, player).sacrificeFor(team, player.slot, minute)

/**
 * Section 3.8's shape keeping rule after a dismissal from the back.
 *
 * A cell at or below sendingOffSacrificeMaxSlot is the keeper, the defence or
 * the holding midfield, and losing anybody from there leaves a hole section
 * 3.4 punishes twice over, once in the line that lost him and once in the
 * chance duel that reads it. The AI answers by taking a forward off as well
 * and putting the most suitable reserve into the vacated cell, so the side is
 * still down to ten but is short at the top of the pitch instead of at the
 * back. A cell above the boundary costs the side nothing but the man.
 *
 * It is one of the five substitutions, so a side that has spent them keeps its
 * hole, and it never happens to a human managed side, which section 3.8 says
 * is never substituted automatically.
 *
 * Nothing here draws. Section 3.8 names the cells to look in and the search of
 * section 5.4 decides who comes on, so the lineup's own order settles both.
 *
 * The forward is only taken off once somebody has been found to come on. A
 * cell nobody on the bench may fill, which under section 3.8's keeper rule is
 * the keeper's and only the keeper's, would otherwise cost the side a forward
 * and give it nothing back. See OPEN-QUESTIONS item 41.
 */
@SpecRef("3.8")
private fun MatchState.sacrificeFor(team: TeamSide, cell: Slot, minute: Int): MatchState {
    val subs = setup.rules.substitutions
    val side = setup.side(team)
    val sideState = of(team)
    if (cell.value > subs.sendingOffSacrificeMaxSlot) {
        return this
    }
    if (!canSubstitute(side, sideState.bench, sideState.substitutionsUsed, subs.maxPerSide)) {
        return this
    }
    val off = sacrificeTarget(side, setup.rules) ?: return this
    val on = chooseReplacement(this, team, cell) ?: return this
    return substitute(team, off, on, cell, minute, SubstitutionReason.SENDING_OFF)
}

/**
 * An injury: the event, its length, and the replacement it forces.
 *
 * The three duration draws are made as soon as a victim is known, before
 * anything is decided about who replaces him, so that the stream a match
 * consumes never depends on what the bench happens to hold.
 *
 * Unlike a dismissal, an injury is replaced rather than absorbed, and the
 * replacement takes the injured man's own cell. A side with nobody to bring
 * on, a side that has spent its five and a human managed side all play on with
 * ten instead, and the keeper's cell may still end up empty, which is the one
 * case chooseReplacement can refuse. See OPEN-QUESTIONS item 41.
 *
 * The injuries counter is not touched here: disciplineMinute's countedInjury
 * already moved it the moment the injury roll matched, so it counts the
 * attempt rather than the injury, and a group whose cells nobody stands in
 * still leaves it moved even though nobody is ever hurt.
 */
@SpecRef("3.8")
private fun MatchState.injure(team: TeamSide, minute: Int, rng: Rng): MatchState {
    val side = setup.side(team)
    val sideState = of(team)
    val group = drawRiskGroup(setup.rules.discipline.injuryRisk, rng)
    val player = drawVictim(side, group, setup.rules, rng) ?: return this
    val outcome = injuryOutcome(
        age = player.age,
        energy = sideState.energy.getValue(player.id),
        rules = setup.rules,
        rng = rng,
    )

    val hurt = copy(
        log = log + MatchEvent.Injury(
            minute = minute,
            side = team,
            player = player,
            days = outcome.days,
            permanentStrengthLoss = outcome.permanentStrengthLoss,
        ),
    )

    if (!canSubstitute(
            side,
            sideState.bench,
            sideState.substitutionsUsed,
            setup.rules.substitutions.maxPerSide,
        )
    ) {
        return hurt.leavePitch(team, player)
    }
    val on = chooseReplacement(hurt, team, player.slot) ?: return hurt.leavePitch(team, player)
    return hurt.substitute(team, player, on, player.slot, minute, SubstitutionReason.INJURY)
}

/**
 * The fourth branch of the chain: each side's own substitution window.
 *
 * Both sides are offered one, in a fixed order, each from a stream of its own
 * derived from its side ordinal, so that whether the home side substitutes
 * cannot move what the away side draws in the same minute.
 *
 * Every gate on the window lives inside runSubstitutionWindow, including the
 * first half and the fifth minute of the half. This function adds none of its
 * own: it decides only whether to offer the window at all, which it does in
 * every minute the chain left empty and in the interval whatever the chain
 * did.
 */
@SpecRef("3.8")
private fun MatchState.openSubstitutionWindows(
    minute: Int,
    clock: MatchClock,
    rng: Rng,
): MatchState {
    val windows = rng.fork(SUBSTITUTION_STREAM)
    var state = this
    for (team in TeamSide.entries) {
        state = state.runSubstitutionWindow(
            team = team,
            plan = state.of(team).plan,
            minute = minute,
            clock = clock,
            rng = windows.fork(team.ordinal.toLong()),
        )
    }
    return state
}

/**
 * The draw that means an event happened.
 *
 * Section 3.8 writes every one of its three rolls as rand(N) == 1, so the
 * value is one rather than nought. Both give a probability of exactly one in
 * N under this project's rand, which returns nought to N minus one, and the
 * literal transcription is the one that stays readable beside the spec. The
 * severity table of section 3.8's own injury duration is transcribed the same
 * way, as a band that covers one alone.
 */
@SpecRef("3.8")
private const val EVENT_FIRES = 1

/** Section 3.8 turns a player's second booking into a dismissal. */
@SpecRef("3.8")
private const val BOOKINGS_BEFORE_DISMISSAL = 2
