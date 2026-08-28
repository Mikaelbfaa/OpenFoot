package org.openfoot.engine.match

import org.openfoot.model.PlayerId
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide

/**
 * One man who has come on, and the side he came on for.
 *
 * The side is carried beside the identity rather than left implicit in which
 * side's list the entry sits in, because these lists are read across sides. A
 * PlayerId is the player's index into the squad his lineup was picked from, so
 * it is unique inside one squad and not between two: the same number names one
 * man in the home squad and a different man in the away squad, and both squads
 * are indexed from the same small range.
 *
 * Section 3.15 item 12 is what makes that matter. It has the away side's
 * window consult the home side's list of arrivals, and says the consequence is
 * that the away side has no protection at all. Compared as bare numbers, a
 * home arrival whose squad index happened to equal an away starter's would
 * match, and the away side would be handed a redraw it is not supposed to
 * have, on a man chosen by nothing but a collision of two squad indices; the
 * redraw would spend a draw and shift the away side's stream as well.
 * Comparing an Arrival makes a cross side match impossible by construction, so
 * the away side reads the wrong list and is therefore never protected, which
 * is the defect section 3.15 describes rather than a weaker version of it.
 */
@SpecRef("3.8")
data class Arrival(
    @property:SpecRef("3.8") val side: TeamSide,
    @property:SpecRef("3.8") val id: PlayerId,
)

/**
 * What one side carries through a match that is not on the pitch.
 *
 * The players on the pitch live in MatchSide, because that is what every line
 * aggregate and every duel reads. What is kept here is everything the pitch
 * does not show: who is on the bench, how much energy each player has left,
 * how many times each has been booked, and how many substitutions are gone.
 *
 * Energy covers the bench as well as the pitch. A substitute comes on with the
 * energy he has, and section 3.9 is explicit that only players on the pitch
 * are drained, so the two cannot be kept in one list.
 *
 * The substitution plan is here too, because it is a fact about one side that
 * the pitch does not show: the minutes that side means to make a change in,
 * drawn once at kick off and then read by every minute of the second half.
 * Carrying it rather than redrawing it is what makes the pools mean anything,
 * since a plan redrawn each minute would be a per minute coin instead.
 *
 * arrivals is everybody this side has brought on so far, in the order they
 * came on, whatever brought them on: a score window, a routine minute, the
 * sacrifice after a dismissal and the replacement of an injured man all append
 * to it. Section 3.8's two score windows read it to avoid taking off a man who
 * has only just come on, and section 3.15 item 12 makes whose list is read a
 * rule set's choice rather than always the side's own. It is carried here
 * rather than folded back out of the log for the same reason goalsBy and
 * DisciplineCounts are: a score window would otherwise walk the whole log
 * every time it opened.
 *
 * It holds identities rather than players. A man who comes on is a different
 * MatchPlayer object standing in the cell he inherited, and MatchEvent.Shot's
 * own docstring already warns that one man can appear under more than one
 * object once a match has substitutions in it, so anything that has to
 * recognise him later has to ask by identity. That identity is an Arrival and
 * not a bare PlayerId, for the reason Arrival's own docstring gives.
 *
 * Every map here is ordered. An unordered map would make a match depend on
 * iteration order, which is the one thing this engine may never do.
 */
@SpecRef("3.9")
data class SideState(
    val bench: List<MatchPlayer> = emptyList(),
    @property:SpecRef("3.9") val energy: Map<PlayerId, Int> = emptyMap(),
    @property:SpecRef("3.8") val bookings: Map<PlayerId, Int> = emptyMap(),
    @property:SpecRef("3.8") val substitutionsUsed: Int = 0,
    @property:SpecRef("3.8") val arrivals: List<Arrival> = emptyList(),
    @property:SpecRef("3.8") val plan: SubstitutionPlan = SubstitutionPlan.NONE,
) {
    companion object {
        /** Section 3.9 starts every player at a hundred. */
        @SpecRef("3.9")
        const val FULL_ENERGY = 100
    }
}

/**
 * A match in progress.
 *
 * The setup carries the two sides as they stand this minute rather than as
 * they were named, which is what lets a sending off or a substitution change
 * what a later tick compares without any formula below needing to know that
 * anything changed.
 *
 * This is a value, and a minute of play is a function from one to the next.
 * That is the same shape the statistics fold already had, and it is what lets
 * a test build a state in the middle of a match and assert one minute of it
 * without playing the eighty before.
 *
 * The discipline counters are carried rather than counted back out of the log
 * for the same reason goalsBy is: section 3.8 reads all three every minute to
 * adjust that minute's thresholds, and walking the whole log each time would
 * make a match quadratic in its own length.
 */
@SpecRef("3.5")
data class MatchState(
    val setup: MatchSetup,
    val home: SideState,
    val away: SideState,
    val log: List<MatchEvent> = emptyList(),
    val possessor: TeamSide,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    @property:SpecRef("3.8") val counts: DisciplineCounts = DisciplineCounts(),
) {
    fun of(side: TeamSide): SideState = if (side == TeamSide.HOME) home else away

    fun with(side: TeamSide, state: SideState): MatchState =
        if (side == TeamSide.HOME) copy(home = state) else copy(away = state)

    /**
     * How many goals the given side has scored so far.
     *
     * Carried on the state rather than counted back out of the log, because
     * section 3.6c reads this figure every tick and walking the whole log
     * each time would make one match quadratic in its own length.
     */
    @SpecRef("3.6c")
    fun goalsBy(side: TeamSide): Int = if (side == TeamSide.HOME) homeGoals else awayGoals
}

/**
 * The state a match starts in.
 *
 * Every player named, on the pitch or on the bench, starts on full energy,
 * unbooked, with every substitution still available.
 *
 * Identities must be distinct within a side, because energy and bookings are
 * keyed by them and a collision would silently give two players one record.
 * This is checked once here rather than on every write, since a squad cannot
 * gain a player mid match.
 *
 * The two substitution plans are parameters rather than drawn here, because
 * drawing needs a generator and this function deliberately takes none: it is
 * the one place a state can be built by hand for a test without any
 * randomness at all. simulateMatch draws them from the match's own stream and
 * hands them in. They default to the empty plan, so a caller who has no
 * generator gets a side that never substitutes of its own accord rather than
 * one that substitutes on minutes nobody chose.
 */
@SpecRef("3.9")
fun initialState(
    setup: MatchSetup,
    startingPossessor: TeamSide,
    homeBench: List<MatchPlayer> = emptyList(),
    awayBench: List<MatchPlayer> = emptyList(),
    homePlan: SubstitutionPlan = SubstitutionPlan.NONE,
    awayPlan: SubstitutionPlan = SubstitutionPlan.NONE,
): MatchState = MatchState(
    setup = setup,
    home = sideState(setup.home.lineup, homeBench, homePlan),
    away = sideState(setup.away.lineup, awayBench, awayPlan),
    possessor = startingPossessor,
)

@SpecRef("3.9")
private fun sideState(
    onPitch: List<MatchPlayer>,
    bench: List<MatchPlayer>,
    plan: SubstitutionPlan,
): SideState {
    val energy = LinkedHashMap<PlayerId, Int>()
    for (player in onPitch + bench) {
        require(!energy.containsKey(player.id)) {
            "two players in one squad share ${player.id}, and energy and bookings are kept by " +
                "identity, so one of them would have no record of his own"
        }
        energy[player.id] = SideState.FULL_ENERGY
    }
    return SideState(bench = bench, energy = energy, plan = plan)
}

/**
 * How much has already gone wrong in this match.
 *
 * Match wide rather than per side, because section 3.8's own gloss on the
 * overwrites names the match: after the first injury of the match the card
 * rate collapses for both sides at once. A sending off for a second yellow
 * counts in the yellows column and not in sendingsOff, since only a direct
 * red feeds the overwrite that reads sendingsOff; see OPEN-QUESTIONS item 39.
 *
 * Each of the three counts an attempt, not an event: it moves the instant its
 * own roll matches, before the risk group is even drawn, and it stays moved
 * even when that group's cells hold nobody and nothing at all reaches the
 * log. This is confirmed original behaviour, not a bug in this engine, so a
 * match's counters can legitimately run ahead of what its log shows, and a
 * reader who finds one of these three larger than the matching count of log
 * entries should look here before suspecting a double count. See section 3.8,
 * the paragraph beginning "Os tres contadores que essas sobrescritas leem".
 *
 * Carried on MatchState rather than counted back out of the log, for the same
 * reason goalsBy is: minuteThresholds reads all three every minute, and
 * walking the log each time would make a match quadratic in its own length.
 * It stays a value of its own rather than three fields on MatchState so that
 * minuteThresholds can still be tested without building a whole match state.
 *
 * The three counters can therefore only be checked against the log as a lower
 * bound, not an equality: a fold of the log's own events undercounts exactly
 * where an attempt found an empty risk group. DisciplineChainTest's whole
 * match test folds the log and asserts the two agree at fixed seeds precisely
 * because none of those seeds' attempts happens to land on an empty group;
 * the empty group case is instead pinned on its own with a scripted draw.
 */
@SpecRef("3.8")
data class DisciplineCounts(
    @property:SpecRef("3.8") val yellows: Int = 0,
    @property:SpecRef("3.8") val sendingsOff: Int = 0,
    @property:SpecRef("3.8") val injuries: Int = 0,
)
