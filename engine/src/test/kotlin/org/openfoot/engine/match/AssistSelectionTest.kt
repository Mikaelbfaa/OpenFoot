package org.openfoot.engine.match

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.Marking
import org.openfoot.model.Rng
import org.openfoot.model.RuleSets
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Section 3.6's assist draw, taken apart.
 *
 * Lineups.player defaults to Stamina and Crossing, and Crossing is itself one
 * of this draw's bonus characteristics, so every fixture below that cares
 * about the plain slot weight or about one specific branch of the chain
 * states both characteristics explicitly through neutralPlayer, which hands
 * out Stamina and Tackling, neither of which this draw rewards at all.
 */
class AssistSelectionTest {

    private val rules = RuleSets.CLASSIC
    private val lightSide = Lineups.side(emptyList(), marking = Marking.LIGHT)
    private val heavySide = Lineups.side(emptyList(), marking = Marking.HEAVY)
    private val veryHeavySide = Lineups.side(emptyList(), marking = Marking.VERY_HEAVY)

    private fun neutralPlayer(slot: Int, id: Int = slot) =
        Lineups.player(slot, 50, id = id, firstTrait = Trait.STAMINA, secondTrait = Trait.TACKLING)

    /**
     * Every one of the twenty five pitch slots against the spec's own table,
     * read on a characteristic-neutral player so only the cell weight shows.
     * The map's own size assertion is what proves no slot from one to
     * twenty five was left out of the fixture.
     */
    @Test
    fun `cell weights follow the spec table for every pitch slot`() {
        val expected = mapOf(
            1 to 1,
            2 to 10,
            3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2,
            9 to 10,
            10 to 10,
            11 to 4, 12 to 4, 13 to 4,
            14 to 20, 15 to 20, 16 to 20,
            17 to 10, 18 to 10, 19 to 10, 20 to 10, 21 to 10,
            22 to 10, 23 to 10, 24 to 10, 25 to 10,
        )
        assertEquals(25, expected.size, "the fixture must cover every pitch slot exactly once")
        for ((slot, weight) in expected) {
            val player = neutralPlayer(slot)
            assertEquals(weight.toDouble(), assistWeight(player, lightSide, rules, walk = false), "slot $slot, total pass")
            assertEquals(weight.toDouble(), assistWeight(player, lightSide, rules, walk = true), "slot $slot, walk pass")
        }
    }

    @Test
    fun `passing alone adds ten`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.PASSING, secondTrait = Trait.TACKLING)
        assertEquals(14.0, assistWeight(player, lightSide, rules, walk = false))
        assertEquals(14.0, assistWeight(player, lightSide, rules, walk = true))
    }

    @Test
    fun `passing with playmaking adds fifteen`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.PASSING, secondTrait = Trait.PLAYMAKING)
        assertEquals(19.0, assistWeight(player, lightSide, rules, walk = false))
    }

    @Test
    fun `playmaking alone adds two`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.PLAYMAKING, secondTrait = Trait.TACKLING)
        assertEquals(6.0, assistWeight(player, lightSide, rules, walk = false))
        assertEquals(6.0, assistWeight(player, lightSide, rules, walk = true))
    }

    @Test
    fun `playmaking with dribbling as the first characteristic adds two more`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.DRIBBLING, secondTrait = Trait.PLAYMAKING)
        assertEquals(8.0, assistWeight(player, lightSide, rules, walk = false))
    }

    @Test
    fun `playmaking with dribbling only as the second characteristic does not add the extra two`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.PLAYMAKING, secondTrait = Trait.DRIBBLING)
        assertEquals(6.0, assistWeight(player, lightSide, rules, walk = false))
    }

    @Test
    fun `dribbling alone adds two`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.DRIBBLING, secondTrait = Trait.TACKLING)
        assertEquals(6.0, assistWeight(player, lightSide, rules, walk = false))
        assertEquals(6.0, assistWeight(player, lightSide, rules, walk = true))
    }

    @Test
    fun `dribbling with pace as the first characteristic adds two more`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.PACE, secondTrait = Trait.DRIBBLING)
        assertEquals(8.0, assistWeight(player, lightSide, rules, walk = false))
    }

    @Test
    fun `dribbling with pace only as the second characteristic does not add the extra two`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.DRIBBLING, secondTrait = Trait.PACE)
        assertEquals(6.0, assistWeight(player, lightSide, rules, walk = false))
    }

    @Test
    fun `pace alone adds one to the total pass and two to the walk pass`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.PACE, secondTrait = Trait.TACKLING)
        assertEquals(5.0, assistWeight(player, lightSide, rules, walk = false))
        assertEquals(6.0, assistWeight(player, lightSide, rules, walk = true))
    }

    @Test
    fun `pace on a lateral cell adds the fullback bonus to both passes`() {
        val player = Lineups.player(2, 50, firstTrait = Trait.PACE, secondTrait = Trait.TACKLING)
        assertEquals(13.0, assistWeight(player, lightSide, rules, walk = false))
        assertEquals(14.0, assistWeight(player, lightSide, rules, walk = true))
    }

    @Test
    fun `crossing alone adds five`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.CROSSING, secondTrait = Trait.TACKLING)
        assertEquals(9.0, assistWeight(player, lightSide, rules, walk = false))
        assertEquals(9.0, assistWeight(player, lightSide, rules, walk = true))
    }

    @Test
    fun `crossing on a lateral cell adds two more`() {
        val player = Lineups.player(2, 50, firstTrait = Trait.CROSSING, secondTrait = Trait.TACKLING)
        assertEquals(17.0, assistWeight(player, lightSide, rules, walk = false))
    }

    @Test
    fun `none of the five characteristics adds nothing`() {
        val player = neutralPlayer(11)
        assertEquals(4.0, assistWeight(player, lightSide, rules, walk = false))
    }

    /**
     * The branch chain stops at its first match. This player carries Passing,
     * which fires first, and Crossing, which would fire on its own if Passing
     * were absent; only the passing bonus of ten must show, never ten plus
     * five.
     */
    @Test
    fun `passing and crossing together take only the passing branch`() {
        val player = Lineups.player(11, 50, firstTrait = Trait.PASSING, secondTrait = Trait.CROSSING)
        assertEquals(14.0, assistWeight(player, lightSide, rules, walk = false))
    }

    /**
     * Same trap, one branch later: Playmaking fires before Crossing, and the
     * player's first characteristic is Playmaking itself rather than
     * Dribbling, so no sub-bonus applies either. Only plus two must show.
     */
    @Test
    fun `playmaking and crossing together take only the playmaking branch`() {
        val player = Lineups.player(2, 50, firstTrait = Trait.PLAYMAKING, secondTrait = Trait.CROSSING)
        assertEquals(12.0, assistWeight(player, lightSide, rules, walk = false))
    }

    @Test
    fun `heavy marking adds twenty to a lateral cell`() {
        val player = neutralPlayer(2)
        assertEquals(10.0, assistWeight(player, lightSide, rules, walk = false))
        assertEquals(30.0, assistWeight(player, heavySide, rules, walk = false))
        assertEquals(30.0, assistWeight(player, heavySide, rules, walk = true))
    }

    @Test
    fun `heavy marking adds twenty to the other lateral cell too`() {
        val player = neutralPlayer(9)
        assertEquals(30.0, assistWeight(player, heavySide, rules, walk = false))
    }

    /**
     * Pesada is the middle marking value, ordinal one, not the hardest.
     * Muito pesada, ordinal two, earns no assist weight bonus at all, and
     * getting the encoding backwards would make this test pass by accident
     * only if the bonus were also wrongly wired to the hardest ordinal.
     */
    @Test
    fun `very heavy marking does not add the twenty`() {
        val player = neutralPlayer(2)
        assertEquals(10.0, assistWeight(player, veryHeavySide, rules, walk = false))
    }

    @Test
    fun `heavy marking does not touch a non lateral cell`() {
        val centreback = neutralPlayer(5)
        assertEquals(2.0, assistWeight(centreback, heavySide, rules, walk = false))
    }

    /**
     * Slot ten asks for a natural fullback under Slot.requiredPosition, but
     * the spec's own weight table marks only two and nine as laterais and
     * gives slot ten its own separate entry, so neither the heavy marking
     * bonus nor the characteristic lateral bonuses may reach it.
     */
    @Test
    fun `slot ten is not treated as lateral by the marking bonus`() {
        val wingback = neutralPlayer(10)
        assertEquals(10.0, assistWeight(wingback, heavySide, rules, walk = false))
    }

    @Test
    fun `slot ten is not treated as lateral by the pace fullback bonus`() {
        val wingback = Lineups.player(10, 50, firstTrait = Trait.PACE, secondTrait = Trait.TACKLING)
        assertEquals(11.0, assistWeight(wingback, lightSide, rules, walk = false))
        assertEquals(12.0, assistWeight(wingback, lightSide, rules, walk = true))
    }

    /**
     * rand(100) > 80 is the draws 81 to 99 inclusive: nineteen values, not
     * twenty. Every one of those nineteen values is exercised here, alongside
     * the entire opposite side of the boundary, so the count is pinned from
     * both directions rather than sampled.
     */
    @Test
    fun `the coin suppresses the assist for every draw from eighty one to ninety nine`() {
        for (draw in 81..99) {
            assertTrue(noAssistDraw(ScriptedInts(draw), rules), "draw $draw should have suppressed the assist")
        }
    }

    @Test
    fun `the coin allows the assist for every draw from zero to eighty`() {
        for (draw in 0..80) {
            assertFalse(noAssistDraw(ScriptedInts(draw), rules), "draw $draw should have allowed the assist")
        }
    }

    @Test
    fun `the coin consumes exactly one draw`() {
        val rng = ScriptedInts(0)
        noAssistDraw(rng, rules)
        assertEquals(1, rng.draws)
    }

    @Test
    fun `the keeper is a candidate at weight one`() {
        val keeper = neutralPlayer(1)
        val finisher = neutralPlayer(20)
        val side = Lineups.side(listOf(keeper, finisher))
        val candidates = assistCandidates(side, finisher, rules)
        assertTrue(candidates.any { it.id == keeper.id })
        assertEquals(1.0, assistWeight(keeper, side, rules, walk = false))
    }

    @Test
    fun `the finisher is excluded from the candidates by identity`() {
        val finisher = neutralPlayer(20)
        val other = neutralPlayer(11)
        val side = Lineups.side(listOf(finisher, other))
        val candidates = assistCandidates(side, finisher, rules)
        assertEquals(listOf(other.id), candidates.map { it.id })
    }

    @Test
    fun `a bench cell is never a candidate`() {
        val finisher = neutralPlayer(20)
        val benched = neutralPlayer(30)
        val side = Lineups.side(listOf(finisher, benched))
        val candidates = assistCandidates(side, finisher, rules)
        assertTrue(candidates.isEmpty())
    }

    /**
     * ScriptedRng carries no scripted double at all, so a bug that let this
     * walk two equally weighted candidates would fail the test by exception
     * rather than by chance.
     */
    @Test
    fun `pickAssister consumes exactly one draw and stops on the first candidate whose walk passes the target`() {
        val a = neutralPlayer(11, id = 1)
        val b = neutralPlayer(12, id = 2)
        val side = Lineups.side(listOf(a, b), marking = Marking.LIGHT)
        val rng = ScriptedRng(0.9)
        val winner = pickAssister(listOf(a, b), side, rules, rng)
        assertEquals(b.id, winner.id)
        assertEquals(1, rng.draws)
    }

    /**
     * Pins that pickAssister really sums totalWeight and walks walkWeight,
     * rather than reading the same function twice under different names,
     * which every other test in this file would still pass if the two
     * arguments were swapped at the asymmetricWeightedPick call site: every
     * other fixture here is characteristic neutral in the walk pass, so a
     * swap changes nothing they check.
     *
     * A carries Pace on a non lateral cell, slot eleven: total weight four
     * plus one is five, walk weight four plus two is six. B is neutral on
     * slot twelve: both weights are four. The total the draw is scaled
     * against is always the sum of totalWeight, five plus four is nine, so a
     * draw of 0.61 times nine is 5.49 under either reading.
     *
     * A walk that read totalWeight for both passes would accumulate five
     * after A, which 5.49 clears, so it would move on and accumulate nine
     * after B, which 5.49 also clears, landing on B. The real walk
     * accumulates walkWeight, six after A, which 5.49 does not clear, so it
     * stops on A instead. The two readings disagree only because the walk
     * pass genuinely reads walkWeight and not totalWeight a second time, so
     * swapping the totalWeight and walkWeight arguments in pickAssister's
     * call to asymmetricWeightedPick makes this test pick B and fail.
     */
    @Test
    fun `pickAssister reads walkWeight for the walk, not totalWeight twice`() {
        val a = Lineups.player(11, 50, id = 1, firstTrait = Trait.PACE, secondTrait = Trait.TACKLING)
        val b = neutralPlayer(12, id = 2)
        val side = Lineups.side(listOf(a, b), marking = Marking.LIGHT)
        val rng = ScriptedRng(0.61)

        val winner = pickAssister(listOf(a, b), side, rules, rng)

        assertEquals(a.id, winner.id)
    }

    @Test
    fun `the coin firing returns no assister and draws nothing else`() {
        val finisher = neutralPlayer(20)
        val other = neutralPlayer(11)
        val side = Lineups.side(listOf(finisher, other))
        val rng = IntsThenDoubles(ints = intArrayOf(81))

        assertNull(selectAssister(side, finisher, rules, rng))
        assertEquals(listOf("int"), rng.calls)
    }

    /**
     * Keeper at weight one and a slot eighteen forward at weight ten total
     * eleven. A draw of 0.99 scales to 10.89, which the keeper's running
     * total of one does not clear but the forward's running total of eleven
     * does, so the forward wins. The call log pins both the count and the
     * order: the coin's nextInt first, then exactly one nextDouble for the
     * walk.
     */
    @Test
    fun `the coin allowing the assist draws one int then one double, in that order`() {
        val keeper = neutralPlayer(1)
        val finisher = neutralPlayer(20)
        val forward = neutralPlayer(18)
        val side = Lineups.side(listOf(keeper, finisher, forward))
        val rng = IntsThenDoubles(ints = intArrayOf(0), doubles = doubleArrayOf(0.99))

        val winner = selectAssister(side, finisher, rules, rng)

        assertEquals(forward.id, winner?.id)
        assertEquals(listOf("int", "double"), rng.calls)
    }

    @Test
    fun `excluding the finisher leaving nobody returns no assister without drawing the pick`() {
        val finisher = neutralPlayer(20)
        val side = Lineups.side(listOf(finisher))
        val rng = IntsThenDoubles(ints = intArrayOf(0))

        assertNull(selectAssister(side, finisher, rules, rng))
        assertEquals(listOf("int"), rng.calls)
    }
}

/**
 * A generator that hands out a fixed sequence of ints for nextInt and a
 * separate fixed sequence of doubles for nextDouble, and records which of the
 * two was called and in what order.
 *
 * ScriptedInts and ScriptedRng are deliberately single purpose elsewhere in
 * this suite, but selectAssister genuinely draws through both idioms in one
 * call, the coin through rand and the walk through asymmetricWeightedPick's
 * nextDouble, so pinning the order between them needs a double for the day
 * wiring test only, scoped to this file the way DisciplineChainTest scopes
 * its own Streams.
 */
private class IntsThenDoubles(
    private val ints: IntArray = IntArray(0),
    private val doubles: DoubleArray = DoubleArray(0),
) : Rng {

    val calls = mutableListOf<String>()
    private var intIndex = 0
    private var doubleIndex = 0

    override fun nextInt(bound: Int): Int {
        check(intIndex < ints.size) { "ran out of scripted ints after $intIndex draws" }
        val value = ints[intIndex++]
        check(value in 0 until bound) { "scripted int $value is outside the bound $bound" }
        calls += "int"
        return value
    }

    override fun nextDouble(): Double {
        check(doubleIndex < doubles.size) { "ran out of scripted doubles after $doubleIndex draws" }
        val value = doubles[doubleIndex++]
        calls += "double"
        return value
    }

    override fun nextBits(): Long = throw UnsupportedOperationException("nextBits is not scripted")

    override fun fork(tag: Long): Rng = throw UnsupportedOperationException("fork is not scripted")
}
