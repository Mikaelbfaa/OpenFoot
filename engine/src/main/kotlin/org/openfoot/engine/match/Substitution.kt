package org.openfoot.engine.match

import org.openfoot.model.Half
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.Slot
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.bound
import org.openfoot.model.chooseCandidate
import org.openfoot.model.pick
import org.openfoot.model.rand
import org.openfoot.model.randRange

/**
 * The same man standing in a different cell.
 *
 * MatchPlayer is not a data class, so there is no copy, and every field has to
 * be carried across by hand. The result is a different object with the same
 * identity, which is exactly what MatchEvent.Shot warns about: once a match
 * has substitutions in it, one man can appear in the log under more than one
 * object, and anything rolling a per player figure out of the log must group
 * by id and never by the object.
 */
@SpecRef("3.8")
fun MatchPlayer.movedTo(slot: Slot): MatchPlayer = MatchPlayer(
    id = id,
    slot = slot,
    naturalPosition = naturalPosition,
    age = age,
    strength = strength,
    abilities = abilities,
    firstTrait = firstTrait,
    secondTrait = secondTrait,
    side = side,
    style = style,
    representsSideCountry = representsSideCountry,
)

/**
 * Who comes on for a cell that has just been vacated, or nobody.
 *
 * The bench is ordered the way section 5.4 step 2 orders a squad, strongest
 * first and energy breaking ties, and then offered whole to the relaxed search
 * of section 5.4 step 3. That search takes the first candidate that fits, so
 * the ordering here is the only tie break there is: a weaker reserve who suits
 * the cell beats a stronger one who does not, and two who both suit it are
 * separated by strength.
 *
 * The keeper's cell carries no exception here. Section 3.8 is explicit that
 * the section 5.4 cascade is applied to it exactly as to any other cell: with
 * no keeper on the bench the cascade descends to a centre back, then a
 * fullback, a midfielder and a forward, and one of them plays in goal with
 * section 5.3's halving and section 3.4's round(GK x 0.2) on top of it. The
 * cell stays empty only when the bench itself is empty, the same as any other
 * cell. See OPEN-QUESTIONS item 41.
 *
 * The rule that used to live here, restricting the keeper's cell to a keeper
 * reserve, does exist in section 3.8 but runs the other way and belongs to a
 * different call site: it forbids a reserve keeper from replacing an injured
 * outfielder, not the cascade from filling the keeper's cell with an
 * outfielder. That refusal is applied in Discipline.kt's injure, after this
 * function has already answered which reserve fits.
 *
 * No draw is made here at all. Which reserve comes on is decided entirely by
 * the ordering and the fit.
 */
@SpecRef("3.8")
internal fun chooseReplacement(state: MatchState, team: TeamSide, cell: Slot): MatchPlayer? {
    val rules = state.setup.rules
    val side = state.of(team)
    val ordered = side.bench.sortedWith(
        compareByDescending<MatchPlayer> { it.strength }
            .thenByDescending { side.energy.getValue(it.id) },
    )
    return chooseCandidate(cell, ordered, rules)
}

/**
 * A player who leaves the match without being replaced.
 *
 * He does not join the bench. A sending off puts him out of the match, not
 * back among the reserves, and nothing else about the side changes: his energy
 * and his bookings stay where they are, because the record is kept by identity
 * and a later reader still asks about him.
 *
 * The eleven he leaves behind keep their order. Section 3.4 walks the lineup
 * in order and takes the first N that qualify for a line, so a reorder here
 * would silently change every aggregate for the rest of the match.
 */
@SpecRef("3.8")
internal fun MatchState.leavePitch(team: TeamSide, player: MatchPlayer): MatchState {
    val side = setup.side(team)
    return copy(setup = setup.with(team, side.withLineup(side.lineup.filter { it.id != player.id })))
}

/**
 * One player replaced by another: the state after, and the event.
 *
 * The man coming on is rebuilt into the vacated cell, because every aggregate
 * of section 3.4 reads the cell and a reserve sits with the minus one the
 * original leaves on an unused substitute. He is appended to the lineup rather
 * than dropped into the departed man's place, which is what the original's own
 * squad array does: a substitution there swaps two slot numbers and leaves the
 * array order alone, and a reserve sits after the eleven in that array. The
 * survivors keep their order either way, which is the part section 3.4 cannot
 * survive losing.
 *
 * Appending has one observable consequence and it is not neutral. Section 3.4
 * takes the first N of a line in list order, so on a lineup with more players
 * in a line's cells than that line's take, the arrival lands last and falls
 * outside the count while a man who was previously ignored moves into it. None
 * of the twelve formations of section 5.1 can reach that shape, but the manual
 * lineup screen of section 5.4 accepts any shape at all. See OPEN-QUESTIONS
 * item 45 for the arithmetic and for the competing reading, which inserts the
 * arrival at the index the departed man held.
 *
 * His energy is left exactly as it was. Section 3.9 drains only the players on
 * the pitch, so a substitute comes on with whatever he has been sitting on.
 */
@SpecRef("3.8")
internal fun MatchState.substitute(
    team: TeamSide,
    off: MatchPlayer,
    on: MatchPlayer,
    cell: Slot,
    minute: Int,
    reason: SubstitutionReason,
): MatchState {
    val arriving = on.movedTo(cell)
    val shortened = leavePitch(team, off)
    val side = shortened.setup.side(team)
    val sideState = shortened.of(team)
    return shortened.copy(
        setup = shortened.setup.with(team, side.withLineup(side.lineup + arriving)),
        log = shortened.log + MatchEvent.Substitution(minute, team, off, arriving, reason),
    ).with(
        team,
        sideState.copy(
            bench = sideState.bench.filter { it.id != on.id },
            substitutionsUsed = sideState.substitutionsUsed + 1,
        ),
    )
}

/**
 * The man the AI takes off to keep its shape after a defender is sent off.
 *
 * Section 3.8 calls it sacrificing a forward: it looks first among the forward
 * cells and only then among the attacking midfield ones, and takes the first
 * player it finds standing in either. The cell ranges are walked in the order
 * the rule set lists them, which is the order the spec writes them in, and a
 * side with nobody in either range has nobody to sacrifice.
 *
 * dismissedWasKeeper opens a third range, keeperSacrificeFallbackCells, tried
 * only after the first two have both come up empty and only when the man sent
 * off was himself the keeper. A dismissed outfielder in the same shape leaves
 * the AI with nobody to sacrifice at all; the wider search is section 3.8's
 * own exception for the one dismissal that costs a side its goalkeeper.
 *
 * No draw is made. Section 3.8 says which cells to look in and says nothing
 * about choosing between two players who both stand in them, so the lineup's
 * own order decides, the same order every aggregate of section 3.4 reads.
 */
@SpecRef("3.8")
internal fun sacrificeTarget(side: MatchSide, rules: RuleSet, dismissedWasKeeper: Boolean): MatchPlayer? {
    for (cells in rules.substitutions.sacrificeCells) {
        val found = side.lineup.firstOrNull { it.slot.value in cells }
        if (found != null) {
            return found
        }
    }
    if (!dismissedWasKeeper) {
        return null
    }
    return side.lineup.firstOrNull { it.slot.value in rules.substitutions.keeperSacrificeFallbackCells }
}

/**
 * The minutes one side plans to make a change in, drawn once per match.
 *
 * Every minute here is counted inside the second half, which is the only half
 * section 3.8 lets the AI substitute in, and is compared against
 * MatchClock.intoHalf rather than against a minute counted from kick off.
 *
 * chasing and routine are kept in draw order rather than sorted. Nothing reads
 * them positionally beyond a membership test, and sorting would throw away the
 * one thing that makes a scripted test able to say which draw produced which
 * minute.
 *
 * halfTimeSwap is the interval's fifty per cent coin, drawn here with the rest
 * of the plan rather than at the interval itself. It depends on nothing the
 * interval knows, so drawing it up front keeps a conditional draw out of the
 * per minute stream.
 */
@SpecRef("3.8")
data class SubstitutionPlan(
    @property:SpecRef("3.8") val chasing: List<Int>,
    @property:SpecRef("3.8") val routine: List<Int>,
    @property:SpecRef("3.8") val halfTimeSwap: Boolean,
) {
    companion object {

        /**
         * The plan of a side that will never make a change of its own accord.
         *
         * It is what SideState carries by default, so that a state built by
         * hand for a test of something else does not have to invent a plan,
         * and so that a caller who has not drawn one cannot accidentally get
         * a side that substitutes on minutes nobody chose. Every window of
         * section 3.8 asks whether this minute is one of its own, and an
         * empty plan answers no to all three.
         */
        @SpecRef("3.8")
        val NONE = SubstitutionPlan(chasing = emptyList(), routine = emptyList(), halfTimeSwap = false)
    }
}

/**
 * Draws one side's whole substitution plan.
 *
 * The draw order is fixed and is the order section 3.8 lists the pools in.
 * Written out draw by draw, because the next thing to read this is the per
 * minute roll and the order is what keeps a recorded match replayable:
 *
 * 1. the first chasing minute, rand over the nineteen to thirty eight window
 * 2. the second chasing minute, from the same window, redrawn on a collision
 * 3. the third chasing minute's coin, rand(100), which allows one below 69
 * 4. the third chasing minute, only when step 3 allowed it, redrawn as above
 * 5. the routine pool selector, rand(100) against the three band table
 * 6. the first routine minute, rand over the chosen pool
 * 7. the second routine minute, from the same pool, redrawn on a collision
 * 8. the first late coin, rand(100), which allows a minute below 79
 * 9. that late minute, only when step 8 allowed it, from 43 to 47
 * 10. the second late coin, rand(100), which allows a minute below 49
 * 11. that late minute, only when step 10 allowed it, redrawn on a collision
 * 12. the interval coin, rand(100), which swaps below 50
 *
 * Drawing without replacement is a list and a redraw rather than a set. A hash
 * container's iteration order would decide which minute a side ends up with,
 * and the redraw is what keeps the number of draws a collision costs visible
 * in the stream instead of hidden inside a shuffle.
 *
 * A plan is drawn fresh per side and per match, from that match's own stream.
 * Section 3.15 item 8 says the original's pools are static and shared, so
 * consecutive matches draw correlated minutes; that is the one named defect of
 * the original neither rule set here reproduces, because it is global mutable
 * state rather than a wrong number and copying it would make a match's result
 * depend on which matches ran before it. See OPEN-QUESTIONS item 42.
 */
@SpecRef("3.8")
internal fun substitutionPlan(rng: Rng, rules: RuleSet): SubstitutionPlan {
    val subs = rules.substitutions

    val chasing = mutableListOf<Int>()
    repeat(subs.chasingCount) {
        chasing += drawFresh(rng, subs.chasingWindow, chasing)
    }
    if (rng.rand(PERCENT_DRAW_BOUND) < subs.extraChasingPercent) {
        chasing += drawFresh(rng, subs.chasingWindow, chasing)
    }

    val pool = subs.routinePools.pick(rng.rand(subs.routinePools.bound()))
    val routine = mutableListOf<Int>()
    repeat(subs.routineCount) {
        routine += drawFresh(rng, pool, routine)
    }
    for (percent in subs.lateChancePercents) {
        if (rng.rand(PERCENT_DRAW_BOUND) < percent) {
            routine += drawFresh(rng, subs.lateWindow, routine)
        }
    }

    val halfTimeSwap = rng.rand(PERCENT_DRAW_BOUND) < subs.halfTimeSwapPercent
    return SubstitutionPlan(chasing = chasing, routine = routine, halfTimeSwap = halfTimeSwap)
}

/**
 * One minute from the window that is not already taken.
 *
 * Redraws until it lands on a free one, which is what section 3.8's "sem
 * reposicao" costs when it is written as a draw rather than as a shuffle.
 *
 * The redraws are capped and the fallback is the first free minute in scan
 * order, so the loop is total rather than merely very likely to end. The
 * windows are all wider than the number of minutes taken from them, so a free
 * minute always exists; what the cap removes is the dependence on the
 * generator eventually producing a different value, which is true of every
 * real generator and false of a degenerate one. Without the cap a caller
 * holding such a generator hangs the whole match instead of failing, and this
 * is reachable from simulateMatch for any side with a bench.
 *
 * The cap is the width of the window squared, and the width is read off the
 * window rather than written down, so a rule set that moves a window moves the
 * cap with it. Reaching the fallback needs every one of the capped draws to
 * land on a minute already taken, so its probability is the share taken raised
 * to the cap, and the tightest window is the one where that is largest.
 *
 * The tightest of the windows section 3.8 draws from is lateWindow, 43 to 47,
 * whose width is five and from which at most one minute is ever already taken.
 * At a cap of twenty five that is one fifth to the twenty fifth, about three
 * times ten to the minus eighteen. The narrowest routine pool, 36 to 42, gives
 * one seventh to the forty ninth, and the chasing window of 19 to 38 gives at
 * most two twentieths to the four hundredth. So the fallback is unreachable in
 * practice at every draw site and exists only as the totality guarantee.
 *
 * A cap of the width alone would not do. It leaves the late window at one
 * fifth to the fifth, about three in ten thousand plans, which is a real if
 * small bias towards the earlier minutes of that window.
 */
@SpecRef("3.8")
private fun drawFresh(rng: Rng, window: IntRange, taken: List<Int>): Int {
    val width = window.last - window.first + 1
    repeat(width * width) {
        val minute = rng.randRange(window.first, window.last)
        if (minute !in taken) {
            return minute
        }
    }
    return window.firstOrNull { it !in taken }
        ?: throw IllegalStateException("the window $window holds no minute outside $taken")
}

/**
 * Whether the interval's score is bad enough for the side to want a change.
 *
 * The one place in section 3.8 where the two sides are held to different
 * standards: the home side changes a goal down, the visitor waits for two. The
 * coin that section 3.8 puts on top of this is not read here; it is part of
 * the plan, drawn once per match.
 */
@SpecRef("3.8")
internal fun wantsHalfTimeSwap(state: MatchState, team: TeamSide, rules: RuleSet): Boolean =
    deficitOf(state, team) >= rules.substitutions.halfTimeDeficitFor(team)

/**
 * Whether a chasing minute's score is bad enough for the side to want a
 * change.
 *
 * The home side chases a draw as well as a defeat and the visitor settles for
 * the draw, which is the same table one rung lower: a deficit of nought is a
 * level game.
 */
@SpecRef("3.8")
internal fun wantsChasingSwap(state: MatchState, team: TeamSide, rules: RuleSet): Boolean =
    deficitOf(state, team) >= rules.substitutions.chasingDeficitFor(team)

/** How many goals the side is behind by, negative when it is ahead. */
@SpecRef("3.8")
private fun deficitOf(state: MatchState, team: TeamSide): Int =
    state.goalsBy(team.opponent) - state.goalsBy(team)

/**
 * The tired man a routine minute takes off, or nobody.
 *
 * Section 3.8 scans for the first non keeper under sixty energy, and after
 * minute forty of the half lifts the bar to ninety and starts the scan at a
 * drawn index instead of at the front. "Apos o minuto 40" is read strictly,
 * so minute forty itself still scans at sixty from the front; the lift is what
 * makes the closing minutes change somebody rather than nobody, and starting
 * it a minute early would be a guess with nothing behind it.
 *
 * The early scan makes no draw at all, which is why the index draw is inside
 * the late branch rather than made unconditionally: a side whose routine
 * minutes all fall early must not shift the stream for the side whose do not.
 *
 * The keeper is skipped whatever his energy. He is identified by the cell he
 * stands in, the same way section 3.9's drain exempts him, so a keeper
 * improvised into a line cell is scanned like anybody else and an outfielder
 * improvised into goal is skipped like a keeper.
 */
@SpecRef("3.8")
internal fun tirednessTarget(
    state: MatchState,
    team: TeamSide,
    intoHalf: Int,
    rng: Rng,
): MatchPlayer? {
    val rules = state.setup.rules
    val subs = rules.substitutions
    val lineup = state.setup.side(team).lineup
    if (lineup.isEmpty()) {
        return null
    }
    val late = intoHalf > subs.lateTirednessFromMinute
    val threshold = if (late) subs.lateTirednessThreshold else subs.tirednessThreshold
    val start = if (late) rng.rand(lineup.size) else 0
    val energy = state.of(team).energy

    for (step in lineup.indices) {
        val player = lineup[(start + step) % lineup.size]
        if (player.slot.value == rules.keeperSlot) {
            continue
        }
        if (energy.getValue(player.id) < threshold) {
            return player
        }
    }
    return null
}

/**
 * A player drawn uniformly from the whole lineup on the pitch, keeper
 * included.
 *
 * Section 3.8 calls the interval's change a "troca aleatoria" and then names
 * the chasing minute's change only as a "troca", in the next sentence and with
 * no scan of its own; the routine minute is the one that overrides the choice
 * with the tiredness scan. Both of the first two therefore draw an index over
 * the whole eleven, not over the ten outfielders, and this function does not
 * filter the keeper's cell out of that index: it draws exactly one player and
 * hands him back whoever he is. What a draw landing on the keeper means is not
 * this function's business. The caller reads the slot it gets back and, when
 * it names the keeper, wastes the window: no substitution and no second draw.
 * See OPEN-QUESTIONS item 44.
 */
@SpecRef("3.8")
internal fun randomLineupPlayer(side: MatchSide, rng: Rng): MatchPlayer? {
    val lineup = side.lineup
    if (lineup.isEmpty()) {
        return null
    }
    return lineup[rng.rand(lineup.size)]
}

/**
 * The AI's substitution window for one side in one minute.
 *
 * Opened for both sides independently. The chasing and routine windows are
 * minutes of play, and each opens only when that minute's discipline chain
 * produced no card and no injury; the interval window is not gated by the
 * chain's outcome at all, because it stands for a minute of play that never
 * happens, so no chain ever runs there for it to be gated by. See isInterval.
 * Section 3.8 lists the window as the fourth branch of the victim chain,
 * which would open it only for the side that minute's victim draw happened to
 * land on; but every side draws its own minutes, so a side's own minute would
 * fire only when it was also drawn as that minute's victim, leaving the per
 * side pools nearly dead. See OPEN-QUESTIONS item 43.
 *
 * Three windows in the order section 3.8 lists them. The interval is the first
 * minute of the second half, which is the only minute a per minute engine has
 * to stand for the break. Then the chasing minutes, then the routine ones; a
 * minute in both pools is a chasing minute, because that is the order the spec
 * puts them in. Everything before the fifth minute of the half other than the
 * interval is closed, which is section 3.8's own gate on the chain and is
 * exactly where the earliest routine pool starts.
 *
 * Nothing at all happens for a human managed side, for a side that has spent
 * its five, or for a side with an empty bench, and none of those cases makes a
 * draw. A window that opens but finds the score wrong makes no draw either.
 *
 * The interval and the chasing windows draw their man from randomLineupPlayer,
 * which runs over the whole eleven and does not filter the keeper's cell out.
 * When that draw names him the window is wasted here: nothing changes, nothing
 * is logged, and no second draw is made to replace the wasted one. Roughly one
 * in eleven of these windows dies this way, which is section 3.8's own cost and
 * not a bug in the draw. See OPEN-QUESTIONS item 44.
 *
 * The null branch on chooseReplacement below cannot be reached from here and
 * is kept as a guard rather than removed. The cascade of section 5.4 carries
 * no exception for the keeper's cell any more, so it ends in a catch all that
 * returns somebody for any cell whenever the bench is not empty, which
 * canSubstitute has already checked above. Removing the branch would mean
 * asserting that with a not null assertion two files away from the code that
 * makes it true, turning a case that cannot happen into a crash instead of a
 * minute in which nothing happens. The same guarantee now holds at every call
 * site chooseReplacement has, including the sending off's sacrifice and the
 * injury's forced change, both of which check canSubstitute first as well;
 * the one way a call to chooseReplacement can still come back empty handed is
 * the injury's own refusal in Discipline.kt's injure, which walks away from a
 * reserve keeper chooseReplacement already found rather than ever seeing it
 * return null.
 */
@SpecRef("3.8")
internal fun MatchState.runSubstitutionWindow(
    team: TeamSide,
    plan: SubstitutionPlan,
    minute: Int,
    clock: MatchClock,
    rng: Rng,
): MatchState {
    val rules = setup.rules
    val subs = rules.substitutions
    val side = setup.side(team)
    val sideState = of(team)
    if (!canSubstitute(side, sideState.bench, sideState.substitutionsUsed, subs.maxPerSide)) {
        return this
    }
    if (clock.halfOf(minute) != Half.SECOND) {
        return this
    }

    val intoHalf = clock.intoHalf(minute)
    val reason = when {
        intoHalf == INTERVAL_MINUTE ->
            if (plan.halfTimeSwap && wantsHalfTimeSwap(this, team, rules)) {
                SubstitutionReason.HALF_TIME
            } else {
                return this
            }

        intoHalf < subs.windowOpensFrom -> return this

        intoHalf in plan.chasing ->
            if (wantsChasingSwap(this, team, rules)) {
                SubstitutionReason.CHASING
            } else {
                return this
            }

        intoHalf in plan.routine -> SubstitutionReason.TIREDNESS

        else -> return this
    }

    val off = if (reason == SubstitutionReason.TIREDNESS) {
        tirednessTarget(this, team, intoHalf, rng) ?: return this
    } else {
        val drawn = randomLineupPlayer(side, rng) ?: return this
        if (drawn.slot.value == rules.keeperSlot) {
            return this
        }
        drawn
    }

    val on = chooseReplacement(this, team, off.slot) ?: return this
    return substitute(team, off, on, off.slot, minute, reason)
}

/**
 * Whether the AI may substitute for this side at all, before any minute is
 * considered.
 *
 * Section 3.8 says human managed sides are never substituted automatically, a
 * side with nobody on the bench has no reserve to bring on, and a side that
 * has already used all five of its changes has none left to spend, so none of
 * the three has any use for a window or for a plan of minutes to open one in.
 *
 * One definition, read by everything that needs it: the AI's window, the
 * shape keeping rule after a dismissal, the forced change after an injury and
 * the plan draw at kick off. Two copies of it could drift, and the drift would
 * be silent in the worst direction: a rule set that let an AI assisted human
 * side be substituted would open the window and find an empty plan, so the
 * feature would simply never fire. The count gate used to be restated beside
 * this one at every one of those call sites; folded in here, there is only one
 * place left for it to drift from.
 */
@SpecRef("3.8")
internal fun canSubstitute(
    side: MatchSide,
    bench: List<MatchPlayer>,
    substitutionsUsed: Int,
    maxPerSide: Int,
): Boolean = !side.isHumanManaged && bench.isNotEmpty() && substitutionsUsed < maxPerSide

/**
 * Whether this minute is the one that stands for the interval.
 *
 * Section 3.8 gives the AI a window at the break, which no minute of play
 * occupies. The first minute of the second half is the one a per minute engine
 * can hang it on, and it is below the chain's own gate of five minutes into
 * the half, so it cannot collide with a routine minute.
 *
 * Read by the chain as well as by the window itself. The interval is section
 * 3.8's own separate paragraph rather than the fourth branch of the victim
 * chain, which is gated on the fifth minute of the half and therefore could
 * never reach minute nought at all, so the chain has to know which minute this
 * is in order not to gate it.
 */
@SpecRef("3.8")
internal fun MatchClock.isInterval(minute: Int): Boolean =
    halfOf(minute) == Half.SECOND && intoHalf(minute) == INTERVAL_MINUTE

/**
 * The minute of the second half that stands for the interval.
 */
@SpecRef("3.8")
private const val INTERVAL_MINUTE = 0

/** The bound of every per cent draw section 3.8's substitution block makes. */
@SpecRef("3.8")
private const val PERCENT_DRAW_BOUND = 100
