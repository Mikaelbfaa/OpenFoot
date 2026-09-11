package org.openfoot.engine.season

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhaseTest {

    private val eight = (1..8).map { "c$it" }

    @Test
    fun `a single group phase is the round robin of section 1 3`() {
        val phase = RoundRobinPhase.single(eight, turns = 2)
        assertEquals(roundRobin(eight, 2), phase.rounds)
        assertEquals(eight, phase.overallTable(emptyList()).map { it.key })
    }

    @Test
    fun `groups playing inside advance in step and read their own tables`() {
        val phase = RoundRobinPhase.grouped(listOf(eight.take(4), eight.drop(4)), turns = 1, gamesInsideGroup = true)
        assertEquals(3, phase.rounds.size)
        assertEquals(4, phase.rounds[0].fixtures.size)
        val results = listOf(Result("c1", "c2", 3, 0))
        assertEquals("c1", phase.groupTable(0, results).first().key)
        assertEquals(eight.drop(4), phase.groupTable(1, results).map { it.key })
        assertEquals("c1", phase.overallTable(results).first().key)
    }

    @Test
    fun `cross group play never pairs two sides of one group and covers every pair once a turn`() {
        val groups = listOf(eight.take(4), eight.drop(4))
        val phase = RoundRobinPhase.grouped(groups, turns = 1, gamesInsideGroup = false)
        val fixtures = phase.rounds.flatMap { it.fixtures }
        assertEquals(16, fixtures.size)
        assertTrue(fixtures.none { f -> groups.any { g -> f.home in g && f.away in g } })
        assertEquals(16, fixtures.map { setOf(it.home, it.away) }.toSet().size)
    }

    @Test
    fun `a knockout of eight plays the state bracket over two legs and reads its merit order`() {
        val entrants = eight.mapIndexed { i, key -> Entrant(key, i + 1) }
        val phase = KnockoutPhase(entrants, legsPerRound = listOf(true, true, true), penalties = true)
        assertEquals(3, phase.rounds)
        val ties = phase.ties(0, emptyList(), RuleSets.CLASSIC, SplitMix64Rng(1))
        assertEquals(listOf("c2" to "c7", "c4" to "c5", "c1" to "c8", "c3" to "c6"), ties.map { it.higher.key to it.lower.key })

        // The higher seed wins every tie on the return after a drawn first leg, so nothing is drawn.
        val firstRound = ties.flatMap { listOf(Result(it.lower.key, it.higher.key, 0, 0), Result(it.higher.key, it.lower.key, 1, 0)) }
        val outcomes = phase.outcomes(0, firstRound, RuleSets.CLASSIC, ScriptedInts())
        assertEquals(listOf("c2", "c4", "c1", "c3"), outcomes.map { it.winner.key })
    }

    @Test
    fun `a field that is not a state size seeds strong against weak`() {
        val entrants = (1..16).map { Entrant("c$it", it) }
        val ties = KnockoutPhase(entrants, listOf(false), penalties = true).ties(0, emptyList(), RuleSets.CLASSIC, SplitMix64Rng(1))
        assertEquals("c1" to "c16", ties.first().higher.key to ties.first().lower.key)
        assertEquals("c8" to "c9", ties.last().higher.key to ties.last().lower.key)
    }
}
