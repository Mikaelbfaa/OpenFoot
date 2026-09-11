package org.openfoot.engine.season

import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef

/**
 * One match a competition has scheduled: which phase and round it belongs
 * to, the fixture, and the leg number for a knockout tie (one for a league
 * fixture or a single legged tie). Section 1.10 keeps the schedule as a flat
 * list of these rather than nesting phases and rounds, so the season loop
 * and the calendar can read one match at a time without knowing which kind
 * of phase produced it.
 */
@SpecRef("1.10")
data class ScheduledMatch(val phase: Int, val round: Int, val fixture: Fixture, val leg: Int = 1)

/**
 * A league phase: one or more groups, each playing a round robin, either
 * kept to its own group or crossed with the others. Every domestic
 * competition that is not purely a knockout is built from one of these,
 * per section 1.11.
 *
 * A single group is the ordinary league of section 1.3. Several groups
 * playing inside themselves is the group stage of a cup competition; several
 * groups playing across each other and never within is the state
 * championship's cross group turn (presets 7 and 10).
 */
@SpecRef("1.11")
data class RoundRobinPhase(
    val groups: List<List<String>>,
    val turns: Int,
    val gamesInsideGroup: Boolean,
    val rounds: List<Round>,
) {
    /** Every participant of every group, in group order. */
    val participants: List<String> get() = groups.flatten()

    /**
     * The table of one group alone, read from the results that were played
     * between two of its own members. A result naming a side outside the
     * group, for instance a cross group fixture, plays no part in that
     * group's own table.
     */
    fun groupTable(group: Int, results: List<Result>): List<TableRow> {
        val members = groups[group].toSet()
        return standings(groups[group], results.filter { it.home in members && it.away in members })
    }

    /** The table of every participant together, regardless of group. */
    fun overallTable(results: List<Result>): List<TableRow> = standings(participants, results)

    companion object {
        /** The ordinary league of section 1.3: one group, everyone in it. */
        @SpecRef("1.3")
        fun single(order: List<String>, turns: Int): RoundRobinPhase =
            RoundRobinPhase(listOf(order), turns, gamesInsideGroup = true, rounds = roundRobin(order, turns))

        /**
         * A league phase over several groups. With games inside the group,
         * each group runs its own round robin and the groups advance in
         * step: round r of the phase is round r of every group at once,
         * groups of different sizes contributing nothing once their own
         * rounds run out. With games across groups, every side meets every
         * side of the other groups once a turn and never its own group.
         */
        @SpecRef("1.11")
        fun grouped(groups: List<List<String>>, turns: Int, gamesInsideGroup: Boolean): RoundRobinPhase {
            val rounds = if (gamesInsideGroup) inStep(groups.map { roundRobin(it, turns) }) else crossGroups(groups, turns)
            return RoundRobinPhase(groups, turns, gamesInsideGroup, rounds)
        }

        private fun inStep(perGroup: List<List<Round>>): List<Round> {
            val length = perGroup.maxOf { it.size }
            return (0 until length).map { r -> Round(perGroup.flatMap { it.getOrNull(r)?.fixtures ?: emptyList() }) }
        }

        /**
         * The cross group turn of the state championship presets (7 and 10):
         * the circle method is drawn once over every participant of every
         * group concatenated, and any fixture the circle drew between two
         * sides of the same group is dropped, since those never happen in
         * this arrangement; a round left with no fixtures once its same
         * group pairing is removed is dropped too.
         *
         * The original reads a fixed table for this case rather than
         * deriving it from the circle method, and the spec does not publish
         * that table (OPEN-QUESTIONS item 75). This construction, the plain
         * circle over the concatenated list with same group pairings
         * stripped out, is this reading's own bet at reproducing the
         * required property, that every side meets every side outside its
         * group exactly once a turn and never a member of its own group; it
         * is INFERIDO, not read from the original.
         */
        @SpecRef("1.11")
        private fun crossGroups(groups: List<List<String>>, turns: Int): List<Round> {
            val groupOf = groups.flatMapIndexed { g, members -> members.map { it to g } }.toMap()
            return roundRobin(groups.flatten(), turns)
                .map { round -> Round(round.fixtures.filter { groupOf.getValue(it.home) != groupOf.getValue(it.away) }) }
                .filter { it.fixtures.isNotEmpty() }
        }
    }
}

/**
 * A knockout phase: a field of entrants, seeded or not yet seeded, and how
 * many legs each round is played over. The field size is kept apart from
 * the entrant count because a phase further down a competition's phase list
 * has no entrants until the phase before it finishes and hands over its
 * qualifiers, yet the schedule needs to know how many rounds and legs are
 * coming before that happens; the field property carries that size on its
 * own.
 */
@SpecRef("FORMAT-SPEC, ces")
data class KnockoutPhase(
    val entrants: List<Entrant>,
    val legsPerRound: List<Boolean>,
    val penalties: Boolean,
    val field: Int = entrants.size,
) {
    init {
        require(field >= 2 && field and (field - 1) == 0) { "a knockout of $field sides" }
        require(entrants.isEmpty() || entrants.size == field) { "${entrants.size} entrants for a field of $field" }
    }

    /**
     * How many rounds a field of this size plays before a single side is
     * left. Qualified as this.field rather than the bare identifier: field
     * is a contextual keyword inside a property getter, reading the backing
     * field of the property being defined, so a bare reference here would
     * mean rounds' own backing field rather than the constructor property
     * of the same name.
     */
    val rounds: Int get() = Integer.numberOfTrailingZeros(this.field)

    /**
     * Whether the given round, zero based, is played over two legs. A round
     * past the end of the legs per round list repeats its last entry, so a
     * caller only has to name where the pattern changes.
     */
    fun twoLegged(round: Int): Boolean = legsPerRound.getOrElse(round) { legsPerRound.lastOrNull() ?: false }

    /**
     * The ties of the given round. Round zero pairs the entrants as the
     * private opening ties helper decides; every later round pairs the
     * winners of the round before it, read from outcomes, with
     * nextRoundTies.
     */
    fun ties(round: Int, resultsSoFar: List<Result>, rules: RuleSet, rng: Rng): List<Tie> {
        if (round == 0) return openingTies()
        return nextRoundTies(outcomes(round - 1, resultsSoFar, rules, rng).map { it.winner })
    }

    /**
     * The opening round's pairings. A field of two, four or eight is a
     * state championship bracket and reads FORMAT-SPEC's fixed table through
     * firstRoundTies. Any other field size is not a state preset; section
     * 1.13 seeds it by strength instead, the best entrant against the worst,
     * the second best against the second worst, and so on.
     */
    @SpecRef("1.13")
    private fun openingTies(): List<Tie> = when (entrants.size) {
        2, 4, 8 -> firstRoundTies(entrants)
        else -> (0 until entrants.size / 2).map { Tie(entrants[it], entrants[entrants.size - 1 - it]) }
    }

    /**
     * Settles every tie of the given round from the legs recorded for it.
     * A single elimination knockout plays two sides against each other in
     * one round only, so filtering the round's results to the legs whose two
     * sides the tie holds is exact: no other tie of the same round can share
     * a leg with this one. Each tie forks its own stream off the round and
     * its own index within it, forking the round number and then the tie
     * index, so playing the ties of a round in a different order, or adding
     * a tie, never moves another tie's shootout draw.
     *
     * The fork is taken lazily, only at the point resolveTie actually reads
     * from the forked stream to draw the abstract shootout of section 3.10.
     * Most ties are decided on legs won or on aggregate and never touch the
     * generator at all, so forking eagerly for every tie of a round would
     * derive a stream that is then thrown away unused; it would also ask a
     * scripted test generator that carries no values for this round to fork
     * a child it never draws from.
     */
    fun outcomes(round: Int, results: List<Result>, rules: RuleSet, rng: Rng): List<TieOutcome> {
        val twoLegged = twoLegged(round)
        return ties(round, results, rules, rng).mapIndexed { index, tie ->
            val legs = results.filter { tie.holds(it.home) && tie.holds(it.away) }
            resolveTie(tie, legs, twoLegged, penalties, rules, LazyFork(rng, round.toLong(), index.toLong()))
        }
    }

    /**
     * The merit order FORMAT-SPEC gives a finished knockout: the champion
     * first, then the runner up, then the sides eliminated in each earlier
     * round in the order their ties were played, the latest round first.
     */
    @SpecRef("FORMAT-SPEC, ces")
    fun meritOrder(results: List<Result>, rules: RuleSet, rng: Rng): List<String> {
        val order = ArrayList<String>()
        for (round in rounds - 1 downTo 0) {
            val ties = ties(round, results, rules, rng)
            val outcomes = outcomes(round, results, rules, rng)
            if (round == rounds - 1) order += outcomes.single().winner.key
            ties.zip(outcomes).forEach { (tie, outcome) ->
                order += if (outcome.winner == tie.higher) tie.lower.key else tie.higher.key
            }
        }
        return order
    }
}

/**
 * An Rng that defers forking its origin until the first time it is actually
 * asked for a value. Used by KnockoutPhase.outcomes so that a tie settled
 * without ever consulting randomness never forks a child stream at all,
 * which keeps a scripted test generator that carries no values honest about
 * what a formula actually draws, and avoids deriving and discarding a
 * stream nobody reads from in the ordinary case.
 */
private class LazyFork(origin: Rng, vararg tags: Long) : Rng {
    private val forked: Rng by lazy { tags.fold(origin) { rng, tag -> rng.fork(tag) } }

    override fun nextBits(): Long = forked.nextBits()

    override fun nextInt(bound: Int): Int = forked.nextInt(bound)

    override fun nextDouble(): Double = forked.nextDouble()

    override fun fork(tag: Long): Rng = forked.fork(tag)
}

/**
 * The two shapes a competition's phase list is built from: a league phase
 * played as one or more groups, or a knockout phase played as ties over
 * legs. Competition walks a list of these, one after another.
 */
sealed interface Phase {
    data class League(val phase: RoundRobinPhase) : Phase
    data class Knockout(val phase: KnockoutPhase) : Phase
}
