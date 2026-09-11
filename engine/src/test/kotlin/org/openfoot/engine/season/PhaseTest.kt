package org.openfoot.engine.season

import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(4, phase.rounds.size)
        assertTrue(phase.rounds.all { it.fixtures.size == 4 })
        val fixtures = phase.rounds.flatMap { it.fixtures }
        assertEquals(16, fixtures.size)
        assertTrue(fixtures.none { f -> groups.any { g -> f.home in g && f.away in g } })
        assertEquals(16, fixtures.map { setOf(it.home, it.away) }.toSet().size)
    }

    @Test
    fun `preset 7 plays twelve rounds of eight, every side once a round`() {
        val sixteen = (1..16).map { "c$it" }
        val groups = listOf(sixteen.take(4), sixteen.drop(4).take(4), sixteen.drop(8).take(4), sixteen.drop(12))
        val phase = RoundRobinPhase.grouped(groups, turns = 1, gamesInsideGroup = false)
        assertEquals(12, phase.rounds.size)
        assertTrue(phase.rounds.all { it.fixtures.size == 8 })
        phase.rounds.forEach { round ->
            val sides = round.fixtures.flatMap { listOf(it.home, it.away) }
            assertEquals(sixteen.toSet(), sides.toSet())
            assertEquals(sides.size, sides.toSet().size)
        }
        assertTrue(phase.rounds.flatMap { it.fixtures }.none { f -> groups.any { g -> f.home in g && f.away in g } })
        assertEquals(96, phase.rounds.flatMap { it.fixtures }.map { setOf(it.home, it.away) }.toSet().size)
    }

    @Test
    fun `preset 10 plays fifteen rounds of ten, every side once a round`() {
        val twenty = (1..20).map { "c$it" }
        val groups = listOf(twenty.take(5), twenty.drop(5).take(5), twenty.drop(10).take(5), twenty.drop(15))
        val phase = RoundRobinPhase.grouped(groups, turns = 1, gamesInsideGroup = false)
        assertEquals(15, phase.rounds.size)
        assertTrue(phase.rounds.all { it.fixtures.size == 10 })
        phase.rounds.forEach { round ->
            val sides = round.fixtures.flatMap { listOf(it.home, it.away) }
            assertEquals(twenty.toSet(), sides.toSet())
            assertEquals(sides.size, sides.toSet().size)
        }
        assertEquals(150, phase.rounds.flatMap { it.fixtures }.map { setOf(it.home, it.away) }.toSet().size)
    }

    @Test
    fun `cross group play refuses an odd group count or unequal group sizes`() {
        val threeGroups = listOf(eight.take(2), eight.drop(2).take(2), eight.drop(4).take(2))
        assertFailsWith<IllegalArgumentException> { RoundRobinPhase.grouped(threeGroups, turns = 1, gamesInsideGroup = false) }

        val unequal = listOf(eight.take(3), eight.drop(3))
        assertFailsWith<IllegalArgumentException> { RoundRobinPhase.grouped(unequal, turns = 1, gamesInsideGroup = false) }
    }

    @Test
    fun `a knockout of eight plays the state bracket over two legs and reads its merit order`() {
        val entrants = eight.mapIndexed { i, key -> Entrant(key, i + 1) }
        val phase = KnockoutPhase(entrants, legsPerRound = listOf(true, true, true), penalties = true)
        assertEquals(3, phase.rounds)
        val ties = phase.ties(0, emptyList(), RuleSets.CLASSIC, SplitMix64Rng(1))
        assertEquals(listOf("c2" to "c7", "c4" to "c5", "c1" to "c8", "c3" to "c6"), ties.map { it.higher.key to it.lower.key })

        // The higher seed wins every tie on the return after a drawn first leg.
        val firstRound = ties.flatMap { listOf(Result(it.lower.key, it.higher.key, 0, 0), Result(it.higher.key, it.lower.key, 1, 0)) }
        val outcomes = phase.outcomes(0, firstRound, RuleSets.CLASSIC, SplitMix64Rng(1))
        assertEquals(listOf("c2", "c4", "c1", "c3"), outcomes.map { it.winner.key })
        assertTrue(outcomes.all { it.shootout == null })
    }

    @Test
    fun `a field that is not a state size seeds strong against weak`() {
        val entrants = (1..16).map { Entrant("c$it", it) }
        val ties = KnockoutPhase(entrants, listOf(false), penalties = true).ties(0, emptyList(), RuleSets.CLASSIC, SplitMix64Rng(1))
        assertEquals("c1" to "c16", ties.first().higher.key to ties.first().lower.key)
        assertEquals("c8" to "c9", ties.last().higher.key to ties.last().lower.key)
    }
}
