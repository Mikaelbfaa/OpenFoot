package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef

/**
 * One competition of the season, per section 1.10: an ordered list of
 * phases, played one after another, with its own round counter so it walks
 * itself to completion without help from the season loop. A league phase
 * hands the next phase its qualifiers through the qualifiers rule, the one
 * place a format's qualification rule lives, kept as data so that two
 * competitions built alike compare equal; every other reading of a
 * competition, its participants, its next matches, its final order, is
 * derived from the phase list and the results recorded so far rather than
 * kept alongside them. Competition is a value: recorded returns the next
 * state instead of mutating this one, the same way every other part of the
 * season is played.
 */
@SpecRef("1.10")
data class Competition(
    val key: String,
    val kind: CompetitionKind,
    val country: Int,
    val division: Int?,
    val phases: List<Phase>,
    val qualifiers: Qualifiers,
    val results: List<List<Result>>,
    val phaseIndex: Int,
    val roundIndex: Int,
) {
    /** Whether every phase has been played through. */
    val finished: Boolean get() = phaseIndex >= phases.size

    /**
     * How many dates the competition takes on the calendar of section 1.10,
     * every phase's dated rounds summed. The season schedule lays exactly
     * this many dates for the competition, and recorded closes each phase
     * after that phase's own dated rounds, so the calendar and the close
     * read one count.
     */
    @SpecRef("1.10")
    val datedRounds: Int get() = phases.sumOf { it.datedRounds }

    /** The sides that started the competition, read from its first phase. */
    val participants: List<String> get() = when (val first = phases.first()) {
        is Phase.League -> first.phase.participants
        is Phase.Knockout -> first.phase.entrants.map { it.key }
    }

    /**
     * The matches still to play in the current round: a league phase's round
     * as it stands, or one match per tie of a knockout phase's current leg.
     * An empty list once the competition has finished.
     */
    fun nextMatches(rules: RuleSet, rng: Rng): List<ScheduledMatch> {
        if (finished) return emptyList()
        return when (val phase = phases[phaseIndex]) {
            is Phase.League -> phase.phase.rounds[roundIndex].fixtures.map { ScheduledMatch(phaseIndex, roundIndex, it) }
            is Phase.Knockout -> {
                val (round, leg) = knockoutRoundAndLeg(phase.phase)
                val twoLegged = phase.phase.twoLegged(round)
                phase.phase.ties(round, results[phaseIndex], rules, rng).map { tie ->
                    ScheduledMatch(phaseIndex, roundIndex, Fixture(tie.host(leg, twoLegged).key, tie.visitor(leg, twoLegged).key), leg)
                }
            }
        }
    }

    /**
     * Reads a knockout phase's round index, which counts legs rather than
     * rounds since a two legged round takes two of them, back into the
     * bracket round and the one based leg within it.
     */
    private fun knockoutRoundAndLeg(phase: KnockoutPhase): Pair<Int, Int> {
        var remaining = roundIndex
        for (round in 0 until phase.rounds) {
            val legs = phase.legs(round)
            if (remaining < legs) return round to remaining + 1
            remaining -= legs
        }
        throw IllegalStateException("round index $roundIndex past the end of the knockout")
    }

    /**
     * Records the results of the current round's matches and returns the
     * competition advanced past them: to the next round of the same phase,
     * or, past the phase's last round, to the next phase, seeding a knockout
     * phase's entrants from the qualifiers rule applied to the phase
     * that just finished. Past the last phase the competition is finished.
     */
    fun recorded(matches: List<Pair<ScheduledMatch, Result>>): Competition {
        require(!finished) { "$key has finished" }
        require(matches.all { it.first.phase == phaseIndex && it.first.round == roundIndex }) { "results for another round of $key" }
        val extended = results.mapIndexed { i, list -> if (i == phaseIndex) list + matches.map { it.second } else list }
        if (roundIndex < phases[phaseIndex].datedRounds - 1) return copy(results = extended, roundIndex = roundIndex + 1)
        return advancePhase(extended)
    }

    private fun advancePhase(extended: List<List<Result>>): Competition {
        val next = phaseIndex + 1
        if (next >= phases.size) return copy(results = extended, phaseIndex = next, roundIndex = 0)
        val seeded = when (val coming = phases[next]) {
            is Phase.Knockout -> {
                val league = phases[phaseIndex] as? Phase.League
                    ?: throw IllegalStateException("$key: a knockout phase must follow a league phase")
                Phase.Knockout(coming.phase.copy(entrants = qualifiers.pick(league.phase, extended[phaseIndex])))
            }
            is Phase.League -> coming
        }
        return copy(
            phases = phases.mapIndexed { i, phase -> if (i == next) seeded else phase },
            results = extended + listOf(emptyList()),
            phaseIndex = next,
            roundIndex = 0,
        )
    }

    /**
     * The final order of a finished competition, per FORMAT-SPEC's merit
     * list: when the competition ends in a knockout, that phase's merit
     * order first, completed by the last league phase's overall table for
     * everyone the knockout never placed; when there is no knockout, the
     * table alone.
     */
    @SpecRef("FORMAT-SPEC, ces")
    fun finalOrder(rules: RuleSet, rng: Rng): List<String> {
        require(finished) { "$key has not finished" }
        val order = ArrayList<String>()
        val last = phases.last()
        if (last is Phase.Knockout) order += last.phase.meritOrder(results.last(), rules, rng)
        val leagueIndex = phases.indexOfLast { it is Phase.League }
        if (leagueIndex >= 0) {
            val league = (phases[leagueIndex] as Phase.League).phase
            league.overallTable(results[leagueIndex]).forEach { if (it.key !in order) order += it.key }
        }
        return order
    }
}

/**
 * The qualification rule a league phase hands the knockout that follows it,
 * per section 1.11 and FORMAT-SPEC's state presets: who goes through, and
 * with which seed. It is data rather than a function so that a Competition,
 * and with it a whole SeasonState, compares equal to another built the same
 * way; a function value compares by identity, so two seasons built from one
 * seed would never be equal.
 */
@SpecRef("1.11")
sealed interface Qualifiers {
    /** The entrants the finished league phase sends on, listed by seed from one. */
    fun pick(phase: RoundRobinPhase, results: List<Result>): List<Entrant>

    /** Nobody goes on: a league with no final phase, or a competition that opens with its knockout. */
    data object None : Qualifiers {
        override fun pick(phase: RoundRobinPhase, results: List<Result>): List<Entrant> = emptyList()
    }

    /**
     * The first count clubs of the overall table across every group, seeded
     * one to count in table order, so the knockout's opening pairs set the
     * strongest against the weakest: a single table state preset, which
     * FORMAT-SPEC pairs by position, and a grouped league with the overall
     * table option of section 1.11, which ignores the groups.
     */
    @SpecRef("1.11")
    data class OverallTable(val count: Int) : Qualifiers {
        override fun pick(phase: RoundRobinPhase, results: List<Result>): List<Entrant> =
            phase.overallTable(results).take(count).mapIndexed { index, row -> Entrant(row.key, index + 1) }
    }

    /**
     * The first perGroup places of every group's own table, seeded by
     * groupedSeeds so the knockout pairs inside each group until one club a
     * group remains: FORMAT-SPEC's grouped presets 7 and 10, and a grouped
     * national league of section 1.11, which reuses that engine.
     */
    @SpecRef("FORMAT-SPEC, ces")
    data class PerGroup(val perGroup: Int) : Qualifiers {
        override fun pick(phase: RoundRobinPhase, results: List<Result>): List<Entrant> {
            val seeds = groupedSeeds(phase.groups.size, perGroup)
            return phase.groups.indices.flatMap { group ->
                phase.groupTable(group, results).take(perGroup).mapIndexed { place, row -> Entrant(row.key, seeds[group][place]) }
            }.sortedBy { it.seed }
        }
    }
}
