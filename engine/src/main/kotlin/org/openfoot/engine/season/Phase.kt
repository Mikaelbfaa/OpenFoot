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
         * every side plays a side of every other group once a turn and never
         * a member of its own group, and every side plays exactly once each
         * round. FORMAT-SPEC publishes that shape: preset 7, four groups of
         * four, plays twelve rounds of eight games; preset 10, four groups
         * of five, plays fifteen rounds of ten games. Both are (group count
         * minus one) times group size rounds, each of (group count times
         * group size) divided by two games, which this construction matches
         * by design.
         *
         * The groups themselves are paired group by group with the circle
         * method over the group indices, one turn, which gives group count
         * minus one group rounds, each a perfect matching of the groups.
         * Every group pairing of a group round then expands into group size
         * match rounds: in match round r, counting from nought, side i of
         * the group listed home in the pairing meets side (i + r) modulo
         * the group size of the group listed away, for every side i of the
         * home group, home rights going to the home group's side when i + r
         * is even and to the away group's side otherwise. As r runs over
         * every match round, each side of the home group meets every side
         * of the away group exactly once, alternating home rights. All of a
         * group round's pairings share the same match round numbering, so
         * the phase's round for match round r of group round g holds, for
         * every pairing of that group round, its own r-th expansion; that
         * is what keeps every side occupied at most once per round.
         *
         * The original reads a fixed table for this case rather than
         * deriving it from a formula, and the spec does not publish that
         * table (OPEN-QUESTIONS item 75); only the round count and game
         * count per round are published, not the pairing table itself. This
         * construction, the circle method over the groups combined with a
         * Latin square inside each paired group, is this reading's own bet
         * at filling in the unpublished table while matching every property
         * FORMAT-SPEC does publish; it is INFERIDO, not read from the
         * original.
         */
        @SpecRef("1.11")
        private fun crossGroups(groups: List<List<String>>, turns: Int): List<Round> {
            require(groups.size >= 2 && groups.size % 2 == 0) {
                "cross group play needs an even number of groups, and there are ${groups.size}"
            }
            val size = groups.first().size
            require(groups.all { it.size == size }) {
                "cross group play needs every group the same size, and these groups are sized ${groups.map { it.size }}"
            }

            val firstTurn = crossGroupsFirstTurn(groups, size)
            val secondTurn = firstTurn.map { round -> Round(round.fixtures.map { Fixture(it.away, it.home) }) }
            return (1..turns).flatMap { turn -> if (turn % 2 == 1) firstTurn else secondTurn }
        }

        private fun crossGroupsFirstTurn(groups: List<List<String>>, size: Int): List<Round> {
            val groupPairings = roundRobin(groups.indices.map { it.toString() }, turns = 1)
            return groupPairings.flatMap { groupRound ->
                (0 until size).map { r ->
                    Round(
                        groupRound.fixtures.flatMap { pairing ->
                            val home = groups[pairing.home.toInt()]
                            val away = groups[pairing.away.toInt()]
                            (0 until size).map { i ->
                                val j = Math.floorMod(i + r, size)
                                if ((i + r) % 2 == 0) Fixture(home[i], away[j]) else Fixture(away[j], home[i])
                            }
                        },
                    )
                }
            }
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
     */
    fun outcomes(round: Int, results: List<Result>, rules: RuleSet, rng: Rng): List<TieOutcome> {
        val twoLegged = twoLegged(round)
        return ties(round, results, rules, rng).mapIndexed { index, tie ->
            val legs = results.filter { tie.holds(it.home) && tie.holds(it.away) }
            resolveTie(tie, legs, twoLegged, penalties, rules, rng.fork(round.toLong()).fork(index.toLong()))
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
 * The two shapes a competition's phase list is built from: a league phase
 * played as one or more groups, or a knockout phase played as ties over
 * legs. Competition walks a list of these, one after another.
 */
sealed interface Phase {
    data class League(val phase: RoundRobinPhase) : Phase
    data class Knockout(val phase: KnockoutPhase) : Phase
}
