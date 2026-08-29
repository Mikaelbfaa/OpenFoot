package org.openfoot.engine.match

import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.randRange

/**
 * One played match.
 *
 * The log is the record and the statistics are read out of it, so a reader who
 * wants to know what happened and a reader who wants to know how often are
 * looking at the same facts.
 *
 * The clock is carried because the length varies and a reader of the
 * statistics needs to know what they are out of. The starting possessor is
 * carried because possession alternates from it deterministically, so it is
 * the only extra fact needed to say whose tick any minute was.
 *
 * The two lineups are the elevens the match kicked off with and are carried
 * for section 3.14, which cannot be produced from a log alone: the rating
 * covers both starting elevens and every substitute who came on, and only the
 * first half of that is knowable from the log. MatchSetup's own two lineups
 * are no substitute for these, because they are the elevens at the final
 * whistle rather than at kick off. leavePitch drops a departed man from a
 * lineup outright, so a caller handing the post match setup to section 3.14
 * would drop every man who was substituted or sent off and would label every
 * arrival a starter. That miswiring produces wrong ratings rather than an
 * exception, which is why the report carries the right lists itself instead
 * of asking a caller to remember which of the two it holds. They are taken
 * from the setup before the first minute is played, at the one moment the two
 * readings agree.
 *
 * Neither list is a defensive copy. MatchPlayer is immutable and simulateMatch
 * takes the list the caller built, so the two share a reference, exactly as
 * MatchSide.lineup already does.
 */
@SpecRef("3.1")
data class MatchReport(
    val clock: MatchClock,
    val log: List<MatchEvent>,
    val homeGoals: Int,
    val awayGoals: Int,
    val startingPossessor: TeamSide,
    @property:SpecRef("3.14") val homeLineup: List<MatchPlayer>,
    @property:SpecRef("3.14") val awayLineup: List<MatchPlayer>,
) {
    /**
     * Computed once here rather than on every read, because a caller that
     * prints a scoreboard asks for it repeatedly and the log is walked in
     * full each time.
     */
    @SpecRef("3.13")
    val stats: MatchStats = log.toStats()
}

/**
 * Plays a whole match.
 *
 * Section 3.1 draws who kicks off with the ball and both halves' stoppage once,
 * then walks the minutes. Section 3.5 alternates possession after every single
 * tick regardless of what happened in it, so each side is on the ball for half
 * the match and the displayed possession percentage comes from the duel counter
 * instead.
 *
 * The rng argument is a seed source, not a stream to be consumed. This function
 * forks it exactly once, at SeedDomain.MATCH, and never reads from it again;
 * every draw the match makes comes from children of that one fork. The result
 * therefore depends only on the generator's origin seed, never on how many
 * values it had already produced before this call, so passing the SAME Rng
 * instance to two calls of simulateMatch replays the identical match twice. A
 * caller that simulates several matches from one generator, such as a whole
 * round of fixtures, must fork a fresh child per match, for example
 * seasonRng.fork(matchId), rather than pass that one instance to every call, or
 * the whole round comes out as one match repeated.
 *
 * Every minute draws from its own stream, derived from the minute index. A fork
 * depends only on the origin seed and the tag and never on how much the parent
 * has produced, so the number of draws one minute makes cannot move the next
 * one. That guarantee is about stream position only, and section 3.8 is the
 * reason it is not the whole story: a sending off, an injury or a substitution
 * mutates the lineup, so what a later tick's duels compare changes even though
 * the stream position feeding that tick has not moved at all. Both properties
 * hold together. A match replays exactly from its seed, because every draw and
 * every lineup change is a function of that seed; and a change to section 3.8
 * moves the results of matches recorded before it, because it changes what the
 * later ticks are comparing.
 *
 * A human sided match is not routed away here. The original sends any match
 * with a human managed club to its live viewer instead of simulating it
 * automatically, and that viewer does not exist in this project yet, so
 * nothing currently calls simulateMatch that way. Calling it with a human
 * sided setup in the meantime is not nonsensical: the anti exploit rules of
 * sections 3.6b and 3.6c legitimately read MatchSetup.hasHumanSide regardless
 * of how the match was reached, so this function does not reject the case.
 *
 * The two bench parameters default to empty. A side with an empty bench is
 * legal and plays on with ten after a dismissal or an injury, which is what
 * section 3.4's fixed divisors then punish; it simply never substitutes.
 */
@SpecRef("3.1")
fun simulateMatch(
    setup: MatchSetup,
    rng: Rng,
    homeBench: List<MatchPlayer> = emptyList(),
    awayBench: List<MatchPlayer> = emptyList(),
): MatchReport {
    val homeLineup = setup.home.lineup
    val awayLineup = setup.away.lineup
    val played = playMatch(setup, rng, homeBench, awayBench)
    return MatchReport(
        clock = played.clock,
        log = played.state.log,
        homeGoals = played.state.homeGoals,
        awayGoals = played.state.awayGoals,
        startingPossessor = played.startingPossessor,
        homeLineup = homeLineup,
        awayLineup = awayLineup,
    )
}

/**
 * A match as it stood at the final whistle, before anything was read out of it.
 *
 * MatchReport deliberately carries only what a reader of a match needs, and
 * section 3.8's three counters are not that: they are the running totals the
 * thresholds are adjusted by, and the log is where the events themselves are
 * recorded. This value exists so that a test can still reach them and check
 * that the two agree, which is the one assertion that catches a counter
 * drifting away from the log it is supposed to summarise.
 */
@SpecRef("3.1")
internal data class PlayedMatch(
    val clock: MatchClock,
    val startingPossessor: TeamSide,
    val state: MatchState,
)

/**
 * Plays the whole match and hands back the state rather than the report.
 *
 * The draws of a match, in the order this function makes them:
 *
 * 1. the starting possessor and both halves' stoppage, from SETUP_STREAM
 * 2. both sides' substitution plans, from SUBSTITUTION_PLAN_STREAM itself, in
 *    one draw for the match, because section 3.8 takes the two sides' minutes
 *    out of the same shuffle and neither plan can be drawn without the other
 * 3. then every minute in turn, each from its own child of the match stream
 *
 * Forking never consumes, so none of these can shift another. That is what
 * lets section 3.8 be added without moving the clock or the kick off, both of
 * which are drawn from SETUP_STREAM exactly as they were before it landed.
 */
@SpecRef("3.1")
internal fun playMatch(
    setup: MatchSetup,
    rng: Rng,
    homeBench: List<MatchPlayer>,
    awayBench: List<MatchPlayer>,
): PlayedMatch {
    val matchRng = rng.fork(SeedDomain.MATCH)
    val setupRng = matchRng.fork(SETUP_STREAM)

    val startingPossessor =
        if (setupRng.randRange(0, 1) == 0) TeamSide.HOME else TeamSide.AWAY
    val clock = matchClock(setupRng)

    val plans = plansFor(
        setup = setup,
        homeBench = homeBench,
        awayBench = awayBench,
        rng = matchRng.fork(SUBSTITUTION_PLAN_STREAM),
    )
    var state = initialState(
        setup = setup,
        startingPossessor = startingPossessor,
        homeBench = homeBench,
        awayBench = awayBench,
        homePlan = plans.home,
        awayPlan = plans.away,
    )
    for (minute in 0 until clock.totalMinutes) {
        state = playMinute(state, minute, clock, matchRng.fork(minute.toLong()))
    }

    return PlayedMatch(clock = clock, startingPossessor = startingPossessor, state = state)
}

/**
 * The match's two plans, with the empty plan for a side that can never act on
 * one.
 *
 * A plan is a list of minutes at which the AI means to bring a reserve on, so
 * a side that may not be substituted at all has no use for one. The condition
 * is canSubstitute, the same one runSubstitutionWindow turns a side away by
 * before it looks at the minute, called rather than restated so that the two
 * cannot drift apart. Nobody has used a substitution yet at kick off, so the
 * count this passes in is zero, which is simply true rather than a stand in
 * for anything. Blanking a plan therefore changes no result at all.
 *
 * The pair is drawn whenever either side can act on one, and blanked afterwards
 * for a side that cannot. Since section 3.8 takes both sides' minutes out of
 * one shuffle, skipping the draw for one side alone would move the other side's
 * minutes, which is a coupling a side's empty bench must not have. Only a match
 * in which neither side can substitute skips the draw, and then nothing is left
 * to move.
 *
 * Nor can the skip move any other draw. The whole pair is drawn from a stream
 * of its own, forked from the match by SUBSTITUTION_PLAN_STREAM, and a fork
 * depends only on the origin seed and the tag and never on how much has been
 * taken from a sibling. Not drawing the pair therefore leaves every minute's
 * chain and every minute's tick exactly where they were.
 */
@SpecRef("3.8")
private fun plansFor(
    setup: MatchSetup,
    homeBench: List<MatchPlayer>,
    awayBench: List<MatchPlayer>,
    rng: Rng,
): MatchSubstitutionPlans {
    val maxPerSide = setup.rules.substitutions.maxPerSide
    val homeCan = canSubstitute(setup.home, homeBench, substitutionsUsed = 0, maxPerSide = maxPerSide)
    val awayCan = canSubstitute(setup.away, awayBench, substitutionsUsed = 0, maxPerSide = maxPerSide)
    if (!homeCan && !awayCan) {
        return MatchSubstitutionPlans.NONE
    }

    val drawn = matchSubstitutionPlans(rng, setup.rules)
    return MatchSubstitutionPlans(
        home = if (homeCan) drawn.home else SubstitutionPlan.NONE,
        away = if (awayCan) drawn.away else SubstitutionPlan.NONE,
    )
}

/**
 * One minute of a match.
 *
 * Section 3.5 alternates possession after every single tick regardless of what
 * happened in it, so the alternation is unconditional and lives here rather
 * than inside the tick, which reports the duel winner instead.
 *
 * A minute is the drain, then section 3.8's roll, then the tick, and finally
 * section 3.7's typing of whatever goal the tick produced. The first three
 * are the order section 3.8 states: it runs once per minute, before the tick
 * of play. The drain comes first of those three because the roll reads
 * energy, both for the tiredness scan that picks who a routine substitution
 * takes off and for the injury duration, and section 3.9 drains the minute
 * before either is decided.
 *
 * The typing comes last because section 3.7 is explicit that it happens once
 * the shot has already been resolved as a goal and the finisher already
 * drawn, and because it is the typing that decides whether the goal reaches
 * the scoreboard at all.
 *
 * The order of the roll and the tick is what makes a card bite in the same
 * minute it was shown: a player sent off at minute sixty is already off the
 * pitch when that minute's duels read the line aggregates. Energy influences
 * no probability the tick reads, so where the drain sits relative to the tick
 * cannot change anything on its own.
 *
 * Internal rather than private so a test can hand it a state from the middle
 * of a match and assert one minute of it without playing the eighty before.
 */
@SpecRef("3.5")
internal fun playMinute(
    state: MatchState,
    minute: Int,
    clock: MatchClock,
    rng: Rng,
): MatchState {
    val rolled = state.drainEnergy(minute, clock).disciplineMinute(minute, clock, rng)
    val possessor = rolled.possessor

    val outcome = playTick(
        setup = rolled.setup,
        possessor = possessor,
        goalsScoredByPossessor = rolled.goalsBy(possessor),
        rng = rng.fork(PLAY_STREAM),
    )

    val goal = if (outcome.event == TickEvent.GOAL) {
        resolveGoal(
            setup = rolled.setup,
            scoringSide = possessor,
            finisher = outcome.shooter,
            minute = minute,
            rng = rng.fork(GOAL_STREAM),
        )
    } else {
        null
    }

    val scored = goal != null && goal.scored
    return rolled.copy(
        log = rolled.log + outcome.events(minute, goal),
        possessor = possessor.opponent,
        homeGoals = rolled.homeGoals + if (scored && possessor == TeamSide.HOME) 1 else 0,
        awayGoals = rolled.awayGoals + if (scored && possessor == TeamSide.AWAY) 1 else 0,
    )
}

/**
 * Turns one tick into the events it produced.
 *
 * The duel winner is always logged, whatever else happened, because it is the
 * number the original displays as possession and it is decided every tick. A
 * tackle belongs to the side that did not have the ball; everything else
 * belongs to the side that did.
 *
 * A goal tick carries section 3.7's resolution alongside it, because the
 * shot's own two flags are no longer the tick's to decide: a penalty in a
 * human sided match can come back missed, and then the same attempt is a shot
 * that did not score and may not even have been on target. The resolution's
 * own events, the goal and any interactive penalty, follow the shot in the
 * same minute.
 *
 * A goal tick handed no resolution is a wiring fault and is refused here
 * rather than absorbed. There is no sensible fallback: guessing the shot
 * scored on target would log a goal on the scoreboard with no goal event
 * behind it and no author credited to anybody, which is the one shape a
 * reader of the log can neither detect nor act on. The resolution is null
 * for every tick that produced no goal and only for those.
 *
 * Internal rather than private so a test can hand it built TickOutcome values
 * and pin the crediting rules without needing a whole match to reach every
 * combination of event and possessor.
 */
@SpecRef("3.13")
internal fun TickOutcome.events(minute: Int, goal: ResolvedGoal?): List<MatchEvent> {
    val duel = MatchEvent.PossessionWon(minute, possessionWinner)
    val rest = when (event) {
        TickEvent.GOAL -> {
            val resolved = requireNotNull(goal) {
                "a goal tick must carry section 3.7's resolution of the goal it produced"
            }
            listOf(
                MatchEvent.Shot(
                    minute,
                    possessor,
                    shooter,
                    onTarget = resolved.onTarget,
                    scored = resolved.scored,
                ),
            ) + resolved.events
        }

        TickEvent.SAVE ->
            listOf(MatchEvent.Shot(minute, possessor, shooter, onTarget = true, scored = false))

        TickEvent.WIDE ->
            listOf(MatchEvent.Shot(minute, possessor, shooter, onTarget = false, scored = false))

        TickEvent.TACKLE -> listOf(MatchEvent.Tackle(minute, possessor.opponent))

        TickEvent.MISPLACED_PASS -> listOf(MatchEvent.MisplacedPass(minute, possessor))
    }
    return listOf(duel) + rest
}

/**
 * The stream the once per match draws come from.
 *
 * Declared internal, not private, so SeedStreamsTest can assert it stays
 * distinct from every other fixed stream and outside the range a minute index
 * can reach, without needing a copy of the literal in the test.
 *
 * Section 3.8 was added without touching this one draw for draw, so the clock
 * and the starting possessor of a match recorded before it are the clock and
 * the starting possessor of the same match today.
 */
@SpecRef("3.1")
internal const val SETUP_STREAM = 0x5E7DL

/**
 * The stream section 3.8's per minute chain draws from: the victim side, the
 * three rolls, the risk group, the player and an injury's duration.
 *
 * A child of the minute's own generator, taken before the tick's. Nothing in
 * the chain reads the play stream and nothing in the tick reads this one, so
 * the number of draws a card costs cannot move a duel.
 */
@SpecRef("3.8")
internal const val DISCIPLINE_STREAM = 0xD15CL

/**
 * The stream both sides' substitution plans are drawn from, once per match.
 *
 * Forked off the match generator rather than off a minute's, because the plans
 * are drawn at kick off and read by every minute of the second half.
 *
 * Read directly, with no second fork. Until section 3.15 item 8's shared
 * shuffle landed, this stream was forked once more by the side's ordinal, one
 * child for the home plan and one for the away plan, so that the two sides were
 * independent; they are not independent any more, and one draw over this stream
 * produces both plans.
 *
 * The two child tags that freed up, nought and one, are reserved and have no
 * caller. They are named here rather than left silently free so that whatever
 * next wants a child of this stream picks a tag outside that pair: a career
 * recorded today would replay differently against an engine that had quietly
 * given nought and one a new meaning, and the failure would look like a bug in
 * the match rather than like a stream that had moved.
 */
@SpecRef("3.8")
internal const val SUBSTITUTION_PLAN_STREAM = 0x5B1AL

/**
 * The stream a minute's substitution windows draw from, one child per side.
 *
 * A sibling of DISCIPLINE_STREAM under the same minute rather than a child of
 * it, so that a minute in which the chain fired and a minute in which it did
 * not leave the other's draws exactly where they were.
 */
@SpecRef("3.8")
internal const val SUBSTITUTION_STREAM = 0x5BEDL

/**
 * The stream one tick draws from.
 *
 * Declared internal for the same reason as SETUP_STREAM: SeedStreamsTest reads
 * it to assert the reservation holds.
 */
@SpecRef("3.5")
internal const val PLAY_STREAM = 0x71CBL

/**
 * The stream section 3.7's goal typing draws from: the type itself, the
 * assist, the own goal author and, in a human sided match, section 3.10's
 * interactive penalty.
 *
 * A sibling of PLAY_STREAM under the same minute rather than a child of it,
 * for the reason SUBSTITUTION_STREAM is a sibling of DISCIPLINE_STREAM: a
 * minute in which a goal was typed and a minute in which none was leave the
 * tick's own draws in exactly the same place. That is what let section 3.7
 * land without moving a single figure of a match recorded before it, even
 * though it adds draws inside the minute.
 */
@SpecRef("3.7")
internal const val GOAL_STREAM = 0x60A1L

/**
 * The stream section 3.14's post match rating draws from: the reduced
 * possession term of step 2, the raised tackle term and the four banded terms
 * of step 4, and both of step 7's chances.
 *
 * A child of the match rather than of a minute, because a rating is computed
 * once the whistle has gone and belongs to no minute at all. Borrowing the
 * last minute's stream would have tied every rating in the match to how long
 * that match happened to run, and reusing GOAL_STREAM would have made a
 * rating move whenever a goal in that minute drew one value more or fewer.
 *
 * It is reserved here, beside the six streams that came before it, for the
 * reason SETUP_STREAM and GOAL_STREAM are: SeedStreamsTest reads every one of
 * them and asserts they stay pairwise distinct and outside the range a minute
 * index can reach, so a future tag that collided would fail there rather than
 * silently make two independent draws identical.
 *
 * Nothing that happens in a match reads it, because a rating feeds nothing
 * back into the match it describes. Adding it therefore cannot move a goal, a
 * shot or a clock, which is what lets the golden vectors stay still.
 */
@SpecRef("3.14")
internal const val RATING_STREAM = 0x2A7EL
