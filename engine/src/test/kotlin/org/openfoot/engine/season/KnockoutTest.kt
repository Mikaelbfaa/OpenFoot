package org.openfoot.engine.season

import org.openfoot.engine.match.ScriptedRng
import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The knockout rules of FORMAT-SPEC's state championship section: the
 * bracket by placing, home rights by placing and by leg, and how a tie is
 * settled, including the second listed side going through when penalties
 * are off.
 */
class KnockoutTest {

    private fun entrants(count: Int) = (1..count).map { Entrant("c$it", it) }

    @Test
    fun `the brackets pair by placing in the spec's tie order`() {
        assertEquals(listOf("c1" to "c2"), firstRoundTies(entrants(2)).map { it.higher.key to it.lower.key })
        assertEquals(
            listOf("c1" to "c4", "c2" to "c3"),
            firstRoundTies(entrants(4)).map { it.higher.key to it.lower.key },
        )
        assertEquals(
            listOf("c2" to "c7", "c4" to "c5", "c1" to "c8", "c3" to "c6"),
            firstRoundTies(entrants(8)).map { it.higher.key to it.lower.key },
        )
        assertFailsWith<IllegalArgumentException> { firstRoundTies(entrants(6)) }
        assertFailsWith<IllegalArgumentException> { firstRoundTies(entrants(4).reversed()) }
    }

    /**
     * The grouped state presets' bracket, FORMAT-SPEC's quarter finals of
     * first against second inside groups A to D: the shared seeding must give
     * exactly the seeds the presets always had, so no state championship
     * moves.
     */
    @Test
    fun `four groups of two take the state presets' group seeds`() {
        assertEquals(listOf(listOf(2, 7), listOf(4, 5), listOf(1, 8), listOf(3, 6)), groupedSeeds(groups = 4, perGroup = 2))
    }

    /**
     * Two groups of four, pinned literally: the eight side bracket's pairs
     * (2,7) and (4,5) go to group A and (1,8) and (3,6) to group B, so in
     * each group place one meets place four and place two meets place three.
     */
    @Test
    fun `two groups of four take the literal seeds of the eight side bracket`() {
        assertEquals(listOf(listOf(2, 4, 5, 7), listOf(1, 3, 6, 8)), groupedSeeds(groups = 2, perGroup = 4))
    }

    @Test
    fun `one group seeds its places in table order`() {
        assertEquals(listOf((1..8).toList()), groupedSeeds(groups = 1, perGroup = 8))
        assertEquals(listOf((1..16).toList()), groupedSeeds(groups = 1, perGroup = 16))
    }

    /**
     * Whatever the shape, with the better placed side of every tie going
     * through: every round before one club a group remains pairs two clubs
     * of one group, the first round place p against place q plus one minus
     * p, and the round after pairs the group survivors A with B, C with D,
     * and so on.
     */
    @Test
    fun `grouped seeds keep every tie inside its group until one club a group remains`() {
        val shapes = listOf(2 to 2, 4 to 2, 2 to 4, 4 to 4, 8 to 4, 4 to 8, 2 to 16, 8 to 1, 16 to 2)
        for ((groups, perGroup) in shapes) {
            val shape = "$groups groups of $perGroup"
            val seeds = groupedSeeds(groups, perGroup)
            assertTrue(seeds.all { row -> row.zipWithNext().all { (better, worse) -> better < worse } }, "$shape: a better place holds a better seed")
            val where = seeds
                .flatMapIndexed { group, row -> row.mapIndexed { place, seed -> "s$seed" to (group to place + 1) } }
                .toMap()
            val entrants = (1..groups * perGroup).map { Entrant("s$it", it) }
            var ties = KnockoutPhase(entrants, listOf(false), penalties = true).ties(0, emptyList(), RuleSets.CLASSIC, SplitMix64Rng(1))
            if (perGroup > 1) {
                ties.forEach { tie ->
                    assertEquals(perGroup + 1, where.getValue(tie.higher.key).second + where.getValue(tie.lower.key).second, shape)
                }
            }
            repeat(Integer.numberOfTrailingZeros(perGroup)) {
                ties.forEach { tie ->
                    assertEquals(where.getValue(tie.higher.key).first, where.getValue(tie.lower.key).first, "$shape: ${tie.higher.key} against ${tie.lower.key}")
                }
                ties = nextRoundTies(ties.map { it.higher })
            }
            assertEquals(
                (0 until groups step 2).map { setOf(it, it + 1) },
                ties.map { setOf(where.getValue(it.higher.key).first, where.getValue(it.lower.key).first) },
                shape,
            )
            assertTrue(ties.all { where.getValue(it.higher.key).second == 1 && where.getValue(it.lower.key).second == 1 }, shape)
        }
    }

    @Test
    fun `the next round pairs consecutive winners, better placed holding the rights`() {
        val winners = listOf(Entrant("c7", 7), Entrant("c4", 4), Entrant("c1", 1), Entrant("c6", 6))
        val ties = nextRoundTies(winners)
        assertEquals(listOf("c4" to "c7", "c1" to "c6"), ties.map { it.higher.key to it.lower.key })
        assertFailsWith<IllegalArgumentException> { nextRoundTies(winners.take(3)) }
    }

    @Test
    fun `home rights follow the placing and the leg`() {
        val tie = Tie(Entrant("melhor", 1), Entrant("pior", 4))
        assertEquals("melhor", tie.host(1, twoLegged = false).key)
        assertEquals("pior", tie.host(1, twoLegged = true).key)
        assertEquals("melhor", tie.host(2, twoLegged = true).key)
        assertEquals("pior", tie.listedSecond(twoLegged = false).key)
        assertEquals("melhor", tie.listedSecond(twoLegged = true).key)
        assertFailsWith<IllegalArgumentException> { Tie(Entrant("a", 3), Entrant("b", 2)) }
    }

    private val tie = Tie(Entrant("melhor", 1), Entrant("pior", 4))

    @Test
    fun `more legs won decides before the aggregate`() {
        // The worse placed side wins the first leg by one and loses the
        // return by three: one leg each, aggregate to the better placed.
        val split = listOf(Result("pior", "melhor", 1, 0), Result("melhor", "pior", 3, 0))
        assertEquals("melhor", resolveTie(tie, split, true, true, RuleSets.CLASSIC, ScriptedInts()).winner.key)

        // Two draws and a win in legs cannot happen; one win and one draw
        // beats a better aggregate the other way.
        val oneWin = listOf(Result("pior", "melhor", 2, 1), Result("melhor", "pior", 0, 0))
        assertEquals("pior", resolveTie(tie, oneWin, true, true, RuleSets.CLASSIC, ScriptedInts()).winner.key)
    }

    @Test
    fun `a level tie goes to the shootout at the deciding match's host`() {
        val level = listOf(Result("pior", "melhor", 1, 0), Result("melhor", "pior", 1, 0))
        // The 3.10 shootout draws two numbers; equal rolls favour the home
        // side, which at the return is the better placed host.
        val homeWins = resolveTie(tie, level, true, true, RuleSets.CLASSIC, ScriptedInts(3, 3))
        assertEquals("melhor", homeWins.winner.key)
        assertEquals(TeamSide.HOME, homeWins.shootout?.winner)

        val awayWins = resolveTie(tie, level, true, true, RuleSets.CLASSIC, ScriptedInts(2, 5))
        assertEquals("pior", awayWins.winner.key)

        val single = listOf(Result("melhor", "pior", 2, 2))
        assertEquals("pior", resolveTie(tie, single, false, true, RuleSets.CLASSIC, ScriptedInts(2, 5)).winner.key)
    }

    @Test
    fun `with penalties off the side listed second goes through, worse placed in a single leg`() {
        val single = listOf(Result("melhor", "pior", 1, 1))
        val outcome = resolveTie(tie, single, false, false, RuleSets.CLASSIC, ScriptedRng())
        assertEquals("pior", outcome.winner.key)
        assertNull(outcome.shootout)

        val level = listOf(Result("pior", "melhor", 0, 0), Result("melhor", "pior", 2, 2))
        assertEquals("melhor", resolveTie(tie, level, true, false, RuleSets.CLASSIC, ScriptedRng()).winner.key)
    }

    @Test
    fun `legs must be played the way the tie hosts them`() {
        assertFailsWith<IllegalArgumentException> {
            resolveTie(tie, listOf(Result("pior", "melhor", 1, 0)), false, true, RuleSets.CLASSIC, ScriptedInts())
        }
        assertFailsWith<IllegalArgumentException> {
            resolveTie(tie, listOf(Result("melhor", "pior", 1, 0)), true, true, RuleSets.CLASSIC, ScriptedInts())
        }
    }
}
