package org.openfoot.engine.season

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Section 1.3's round robin: the worked example pinned match by match, the
 * mirrored second turn, the repeated third and fourth, and the properties
 * every schedule has to have.
 */
class FixturesTest {

    private val six = listOf("T1", "T2", "T3", "T4", "T5", "T6")

    private fun Round.text() = fixtures.joinToString(" ") { "${it.home}-${it.away}" }

    @Test
    fun `the six team example of section 1 3 comes out round by round`() {
        val rounds = roundRobin(six, turns = 2)
        assertEquals(
            listOf(
                "T1-T6 T2-T5 T3-T4",
                "T6-T4 T5-T3 T1-T2",
                "T2-T6 T3-T1 T4-T5",
                "T6-T5 T1-T4 T2-T3",
                "T3-T6 T4-T2 T5-T1",
                "T6-T1 T5-T2 T4-T3",
                "T4-T6 T3-T5 T2-T1",
                "T6-T2 T1-T3 T5-T4",
                "T5-T6 T4-T1 T3-T2",
                "T6-T3 T2-T4 T1-T5",
            ),
            rounds.map { it.text() },
        )
    }

    @Test
    fun `every pair meets once a turn and every side hosts half its matches over two turns`() {
        val rounds = roundRobin(six, turns = 2)
        val firstTurn = rounds.take(5).flatMap { it.fixtures }
        assertEquals(15, firstTurn.map { setOf(it.home, it.away) }.toSet().size)
        for (side in six) {
            val played = rounds.flatMap { it.fixtures }.filter { it.home == side || it.away == side }
            assertEquals(10, played.size, side)
            assertEquals(5, played.count { it.home == side }, "$side at home")
        }
        for (round in rounds) {
            assertEquals(six.toSet(), round.fixtures.flatMap { listOf(it.home, it.away) }.toSet())
        }
    }

    @Test
    fun `three and four turns repeat the first two rather than drawing again`() {
        val two = roundRobin(six, 2)
        val four = roundRobin(six, 4)
        assertEquals(two + two, four)
        assertEquals(two + two.take(5), roundRobin(six, 3))
        assertEquals(two.take(5), roundRobin(six, 1))
    }

    @Test
    fun `a twenty side league has nineteen rounds a turn with balanced home rights`() {
        val order = (1..20).map { "c$it" }
        val rounds = roundRobin(order, 2)
        assertEquals(38, rounds.size)
        for (side in order) {
            assertEquals(19, rounds.flatMap { it.fixtures }.count { it.home == side }, "$side at home")
        }
    }

    @Test
    fun `odd sizes, repeats and bad turn counts are refused`() {
        assertFailsWith<IllegalArgumentException> { roundRobin(six.take(5), 2) }
        assertFailsWith<IllegalArgumentException> { roundRobin(six.take(5) + "T1", 2) }
        assertFailsWith<IllegalArgumentException> { roundRobin(six, 0) }
        assertFailsWith<IllegalArgumentException> { roundRobin(six, 5) }
    }

    @Test
    fun `the default turns follow the size table`() {
        assertEquals(4, defaultTurns(8))
        assertEquals(4, defaultTurns(10))
        assertEquals(3, defaultTurns(12))
        assertEquals(3, defaultTurns(14))
        assertEquals(2, defaultTurns(16))
        assertEquals(2, defaultTurns(20))
        assertEquals(1, defaultTurns(26))
        assertEquals(1, defaultTurns(36))
    }

    @Test
    fun `the shuffle is a permutation that depends on the seed and not on the listing`() {
        val once = shuffledOrder(six, SplitMix64Rng(3))
        assertEquals(six.toSet(), once.toSet())
        assertEquals(once, shuffledOrder(six, SplitMix64Rng(3)))
        assertTrue((1..20).any { shuffledOrder(six, SplitMix64Rng(it.toLong())) != six })

        // Scripted from the back: the last position swaps with index 0, then
        // the fourth with itself, and so on, one draw per position but the first.
        assertEquals(listOf("T6", "T2", "T3", "T4", "T5", "T1"), shuffledOrder(six, ScriptedInts(0, 4, 3, 2, 1)))
    }
}
