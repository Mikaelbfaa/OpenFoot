package org.openfoot.engine.season

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Section 1.2: the table is read out of the results, and the one comparator
 * ranks it. Each criterion of the comparator is exercised where the ones
 * before it are level, so a swapped or missing criterion shows up by name.
 */
class StandingsTest {

    private val clubs = listOf("a", "b", "c", "d")

    @Test
    fun `a club that has not played has a row of zeros`() {
        val table = standings(clubs, emptyList())
        assertEquals(clubs, table.map { it.key })
        assertEquals(TableRow("a", 0, 0, 0, 0, 0, 0), table.first())
        assertEquals(0, table.first().draws)
        assertEquals(0, table.first().goalDifference)
    }

    @Test
    fun `a win is three points, a draw one, a loss none, and both sides are credited`() {
        val table = standings(clubs, listOf(Result("a", "b", 2, 0), Result("c", "d", 1, 1)))
        val byKey = table.associateBy { it.key }

        assertEquals(TableRow("a", 3, 1, 1, 0, 2, 0), byKey.getValue("a"))
        assertEquals(TableRow("b", 0, 1, 0, 1, 0, 2), byKey.getValue("b"))
        assertEquals(TableRow("c", 1, 1, 0, 0, 1, 1), byKey.getValue("c"))
        assertEquals(1, byKey.getValue("c").draws)
        assertEquals(TableRow("d", 1, 1, 0, 0, 1, 1), byKey.getValue("d"))
    }

    @Test
    fun `points rank first`() {
        val table = standings(clubs, listOf(Result("d", "a", 1, 0), Result("b", "c", 0, 0)))
        assertEquals(listOf("d", "b", "c", "a"), table.map { it.key })
    }

    @Test
    fun `wins break a tie on points before goal difference does`() {
        // b: one win and two losses, three points with a goal difference of
        // minus five; c: three draws, three points with a difference of nil.
        // Wins rank before difference, so b stays above c.
        val results = listOf(
            Result("b", "a", 1, 0),
            Result("b", "d", 0, 3),
            Result("d", "b", 3, 0),
            Result("c", "a", 0, 0),
            Result("c", "d", 1, 1),
            Result("a", "c", 2, 2),
        )
        val table = standings(clubs, results)
        assertEquals("b", table.first { it.points == 3 && it.wins == 1 }.key)
        assertEquals(listOf("d", "b", "c", "a").take(3), table.map { it.key }.take(3))
    }

    @Test
    fun `goal difference breaks a tie on points and wins, and goals for breaks that`() {
        // a and b each win once by two; a scored three, b scored two, so a is
        // above b on goals for. c and d each lost by two; c scored one, d
        // none, so c is above d on goals for. Both pairs are level on points,
        // wins and difference, and the fixture order would put b before a
        // and d after c, so only goals for can produce this order.
        val results = listOf(Result("b", "d", 2, 0), Result("a", "c", 3, 1))
        val table = standings(listOf("b", "a", "d", "c"), results)
        assertEquals(listOf("a", "b", "c", "d"), table.map { it.key })
    }

    @Test
    fun `a complete tie keeps the competition's own order`() {
        val results = listOf(Result("a", "b", 1, 1), Result("c", "d", 1, 1))
        assertEquals(listOf("a", "b", "c", "d"), standings(clubs, results).map { it.key })
        assertEquals(listOf("d", "c", "b", "a"), standings(clubs.reversed(), results).map { it.key })
    }

    @Test
    fun `a result naming a club outside the competition is refused`() {
        assertFailsWith<IllegalArgumentException> { standings(clubs, listOf(Result("a", "z", 1, 0))) }
        assertFailsWith<IllegalArgumentException> { standings(listOf("a", "a"), emptyList()) }
        assertFailsWith<IllegalArgumentException> { Result("a", "a", 1, 0) }
    }
}
