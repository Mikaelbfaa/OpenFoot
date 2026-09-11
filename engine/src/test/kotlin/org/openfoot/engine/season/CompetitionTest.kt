package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompetitionTest {

    private val six = (1..6).map { "c$it" }

    private fun leagueOnly() = Competition(
        key = "liga",
        kind = CompetitionKind.NATIONAL_LEAGUE,
        country = 29,
        division = 1,
        phases = listOf(Phase.League(RoundRobinPhase.single(six, turns = 1))),
        qualifiers = Qualifiers.None,
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )

    /** Every home side wins by one, so the table is decided by the fixture list alone. */
    private fun homeWins(matches: List<ScheduledMatch>) =
        matches.map { it to Result(it.fixture.home, it.fixture.away, 1, 0) }

    @Test
    fun `a league plays its rounds in order and finishes after the last`() {
        var competition = leagueOnly()
        val rules = RuleSets.CLASSIC
        val rng = SplitMix64Rng(1)
        repeat(5) {
            assertFalse(competition.finished)
            val matches = competition.nextMatches(rules, rng)
            assertEquals(3, matches.size)
            assertEquals(it, matches.first().round)
            competition = competition.recorded(homeWins(matches))
        }
        assertTrue(competition.finished)
        assertEquals(6, competition.finalOrder(rules, rng).size)
        assertEquals(15, competition.results.single().size)
        assertTrue(competition.nextMatches(rules, rng).isEmpty())
    }

    @Test
    fun `a league with a final phase carries its qualifiers into a two legged knockout`() {
        val phases = listOf(
            Phase.League(RoundRobinPhase.single(six, turns = 1)),
            Phase.Knockout(KnockoutPhase(emptyList(), legsPerRound = listOf(true, false), penalties = true, field = 4)),
        )
        var competition = leagueOnly().copy(
            phases = phases,
            qualifiers = Qualifiers.OverallTable(4),
        )
        val rules = RuleSets.CLASSIC
        val rng = SplitMix64Rng(2)
        repeat(5) { competition = competition.recorded(homeWins(competition.nextMatches(rules, rng))) }
        assertEquals(1, competition.phaseIndex)

        val firstLegs = competition.nextMatches(rules, rng)
        assertEquals(2, firstLegs.size)
        assertEquals(1, firstLegs.first().leg)
        competition = competition.recorded(homeWins(firstLegs))
        val returnLegs = competition.nextMatches(rules, rng)
        assertEquals(2, returnLegs.first().leg)
        assertEquals(firstLegs.map { it.fixture.away }, returnLegs.map { it.fixture.home })
        competition = competition.recorded(returnLegs.map { it to Result(it.fixture.home, it.fixture.away, 3, 0) })

        val final = competition.nextMatches(rules, rng)
        assertEquals(1, final.size)
        competition = competition.recorded(homeWins(final))
        assertTrue(competition.finished)
        val order = competition.finalOrder(rules, rng)
        assertEquals(final.single().fixture.home, order.first())
        assertEquals(6, order.size)
        assertEquals(6, order.toSet().size)
    }

    /**
     * A six side single turn league, five rounds, followed by a two side
     * final over two legs, two more: the competition's dated rounds are
     * seven, and it closes after exactly seven recorded rounds, the same
     * count the season schedule lays dates for.
     */
    @Test
    fun `a competition closes after exactly its dated rounds`() {
        var competition = Competition(
            key = "liga",
            kind = CompetitionKind.NATIONAL_LEAGUE,
            country = 29,
            division = 1,
            phases = listOf(
                Phase.League(RoundRobinPhase.single(six, turns = 1)),
                Phase.Knockout(KnockoutPhase(emptyList(), listOf(true), penalties = true, field = 2)),
            ),
            qualifiers = Qualifiers.OverallTable(2),
            results = listOf(emptyList()),
            phaseIndex = 0,
            roundIndex = 0,
        )
        assertEquals(7, competition.datedRounds)
        val rules = RuleSets.CLASSIC
        val rng = SplitMix64Rng(1)
        var recorded = 0
        while (!competition.finished) {
            competition = competition.recorded(homeWins(competition.nextMatches(rules, rng)))
            recorded++
        }
        assertEquals(7, recorded)
    }
}
