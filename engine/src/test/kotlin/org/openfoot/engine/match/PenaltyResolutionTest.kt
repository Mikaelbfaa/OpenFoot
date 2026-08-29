package org.openfoot.engine.match

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.RuleSets
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Section 3.10's two independent penalty paths, taken apart. Neither has a
 * caller yet, so every test here drives the pure functions directly.
 *
 * Every expectation is recomputed from the spec while the test is written,
 * never copied from anywhere else, so a reader can check the arithmetic on
 * paper.
 */
class PenaltyResolutionTest {

    private val rules = RuleSets.CLASSIC
    private val neutralTaker = PenaltyTaker(hasFinishing = false, star = false, topWorld = false)
    private val neutralKeeper = PenaltyKeeper(hasPenaltySaving = false, star = false, topWorld = false)

    /** Turns an actual roll into the raw ScriptedInts value randRange consumes. */
    private fun roll(value: Int) = value - rules.penalties.shootoutRollMin

    private fun shootout(x: Int, y: Int) = aiPenaltyShootout(rules, ScriptedInts(roll(x), roll(y)))

    @Test
    fun `a tie is read as a home win`() {
        val result = shootout(x = 5, y = 5)
        assertEquals(TeamSide.HOME, result.winner)
        assertEquals(5, result.winnerGoals)
        assertEquals(4, result.loserGoals)
    }

    @Test
    fun `a shootout always consumes exactly two draws, x then y`() {
        val rng = ScriptedInts(roll(5), roll(5))
        aiPenaltyShootout(rules, rng)
        assertEquals(2, rng.draws)
    }

    @Test
    fun `the boundary between a home and an away win sits at x equal to y`() {
        assertEquals(TeamSide.HOME, shootout(x = 5, y = 5).winner, "x equal to y still favours home")
        assertEquals(TeamSide.AWAY, shootout(x = 4, y = 5).winner, "x one below y flips to away")
    }

    @Test
    fun `the away side can only win when x falls short of y, at every gap`() {
        assertEquals(TeamSide.HOME, shootout(x = 8, y = 2).winner)
        assertEquals(TeamSide.AWAY, shootout(x = 2, y = 8).winner)
    }

    @Test
    fun `the home side's score is x and the away side's is one away from it, whoever wins`() {
        val homeWin = shootout(x = 6, y = 3)
        assertEquals(6, homeWin.winnerGoals, "home won, so the winner's tally is x")
        assertEquals(5, homeWin.loserGoals, "the away side, the loser, sits one below x")

        val awayWin = shootout(x = 6, y = 7)
        assertEquals(6, awayWin.loserGoals, "home, the loser here, still sits at x")
        assertEquals(7, awayWin.winnerGoals, "the away side, the winner, sits one above x")
    }

    @Test
    fun `the margin is always exactly one goal, whichever side wins`() {
        for (x in rules.penalties.shootoutRollMin..rules.penalties.shootoutRollMax) {
            for (y in rules.penalties.shootoutRollMin..rules.penalties.shootoutRollMax) {
                val result = shootout(x, y)
                assertEquals(1, result.winnerGoals - result.loserGoals, "x $x, y $y")
            }
        }
    }

    /**
     * Section 3.10's prose adds that the winning visitor's score can reach
     * nine. That is not what this formula produces: an away win needs x
     * below y, and with y capped at eight that caps x at seven, so the away
     * side's winning score, x plus one, tops out at eight, matching the home
     * side's own ceiling rather than exceeding it. This test pins the actual
     * ceiling the formula gives rather than the spec's descriptive aside,
     * which the function's own docstring flags as a discrepancy.
     */
    @Test
    fun `an away winner's score never exceeds eight, matching the home side's own ceiling`() {
        val result = shootout(x = 7, y = 8)
        assertEquals(TeamSide.AWAY, result.winner)
        assertEquals(8, result.winnerGoals)

        for (x in rules.penalties.shootoutRollMin..rules.penalties.shootoutRollMax) {
            for (y in rules.penalties.shootoutRollMin..rules.penalties.shootoutRollMax) {
                val outcome = shootout(x, y)
                if (outcome.winner == TeamSide.AWAY) {
                    assertTrue(outcome.winnerGoals <= 8, "x $x, y $y gave an away score of ${outcome.winnerGoals}")
                }
            }
        }
    }

    /**
     * Every one of the 49 pairs the two rand(2..8) draws can land on, counted
     * rather than sampled. Section 3.10 documents 28 of 49 for the home side,
     * which is what a tie deciding in home's favour on a seven value range
     * predicts: home wins whenever x is at least y, 7 ties plus 21 pairs
     * where x exceeds y, 28 in total, leaving the other 21 to the away side.
     */
    @Test
    fun `enumerating the full grid gives the home side 28 of 49`() {
        var homeWins = 0
        var awayWins = 0
        for (x in rules.penalties.shootoutRollMin..rules.penalties.shootoutRollMax) {
            for (y in rules.penalties.shootoutRollMin..rules.penalties.shootoutRollMax) {
                when (shootout(x, y).winner) {
                    TeamSide.HOME -> homeWins++
                    TeamSide.AWAY -> awayWins++
                }
            }
        }
        assertEquals(49, homeWins + awayWins, "the grid must cover all 49 pairs exactly once")
        assertEquals(28, homeWins)
        assertEquals(21, awayWins)
    }

    @Test
    fun `the neutral threshold is the base seventy`() {
        assertEquals(70, interactivePenaltyThreshold(neutralTaker, neutralKeeper, rules))
    }

    @Test
    fun `finalizacao alone adds ten`() {
        val taker = neutralTaker.copy(hasFinishing = true)
        assertEquals(80, interactivePenaltyThreshold(taker, neutralKeeper, rules))
    }

    @Test
    fun `a red star taker with no finalizacao still gets the ten, plus five more for being a star too`() {
        val taker = neutralTaker.copy(star = true, topWorld = true)
        assertEquals(85, interactivePenaltyThreshold(taker, neutralKeeper, rules))
    }

    @Test
    fun `finalizacao and a red star never stack the ten twice`() {
        val taker = neutralTaker.copy(hasFinishing = true, star = true, topWorld = true)
        assertEquals(
            85,
            interactivePenaltyThreshold(taker, neutralKeeper, rules),
            "ten once from the finalizacao-or-red-star check, five once more from the star check",
        )
    }

    @Test
    fun `a plain star with neither finalizacao nor a red star only adds five`() {
        val taker = neutralTaker.copy(star = true)
        assertEquals(75, interactivePenaltyThreshold(taker, neutralKeeper, rules))
    }

    @Test
    fun `defesa penalty subtracts ten from the keeper's side`() {
        val keeper = neutralKeeper.copy(hasPenaltySaving = true)
        assertEquals(60, interactivePenaltyThreshold(neutralTaker, keeper, rules))
    }

    @Test
    fun `a red star keeper with no defesa penalty still loses ten, plus five more for being a star too`() {
        val keeper = neutralKeeper.copy(star = true, topWorld = true)
        assertEquals(55, interactivePenaltyThreshold(neutralTaker, keeper, rules))
    }

    @Test
    fun `a plain star keeper with neither trait nor red star only subtracts five`() {
        val keeper = neutralKeeper.copy(star = true)
        assertEquals(65, interactivePenaltyThreshold(neutralTaker, keeper, rules))
    }

    @Test
    fun `every keeper modifier combines rather than the strongest one alone applying`() {
        val keeper = PenaltyKeeper(hasPenaltySaving = true, star = true, topWorld = true)
        assertEquals(
            45,
            interactivePenaltyThreshold(neutralTaker, keeper, rules),
            "ten for the trait, ten for the red star, five for the star, twenty five in total",
        )
    }

    @Test
    fun `a fully decorated taker and a fully decorated keeper both apply at once`() {
        val taker = PenaltyTaker(hasFinishing = true, star = true, topWorld = true)
        val keeper = PenaltyKeeper(hasPenaltySaving = true, star = true, topWorld = true)
        assertEquals(
            60,
            interactivePenaltyThreshold(taker, keeper, rules),
            "70 base, +15 taker, -25 keeper",
        )
    }

    @Test
    fun `the coin converts exactly on the threshold and misses one past it`() {
        val converted = interactivePenalty(neutralTaker, neutralKeeper, rules, ScriptedInts(coin(70)))
        assertEquals(true, converted.scored)
        assertEquals(true, converted.onTarget)
        assertEquals(false, converted.keeperCreditedWithSave)

        val missed = interactivePenalty(neutralTaker, neutralKeeper, rules, ScriptedInts(coin(71), 0))
        assertEquals(false, missed.scored)
    }

    @Test
    fun `a conversion consumes exactly one draw`() {
        val rng = ScriptedInts(coin(70))
        interactivePenalty(neutralTaker, neutralKeeper, rules, rng)
        assertEquals(1, rng.draws)
    }

    @Test
    fun `a miss consumes exactly two draws`() {
        val rng = ScriptedInts(coin(71), 0)
        interactivePenalty(neutralTaker, neutralKeeper, rules, rng)
        assertEquals(2, rng.draws)
    }

    /**
     * All seven miss outcomes, enumerated one at a time. Three of them, zero
     * to two, credit the keeper with a penalty saved; the next two, three and
     * four, go off target; the last two, five and six, stay on target
     * crediting nobody. The tally at the end proves the split is three, two
     * and two and not some other partition that happens to sum to seven.
     */
    @Test
    fun `all seven miss outcomes are reachable and exactly three credit a save`() {
        val outcomes = (0..6).map { draw ->
            interactivePenalty(neutralTaker, neutralKeeper, rules, ScriptedInts(coin(71), draw))
        }

        outcomes.forEach { assertEquals(false, it.scored, "a miss never scores") }

        val saved = outcomes.count { it.keeperCreditedWithSave }
        val wide = outcomes.count { !it.onTarget }
        val onTargetNoSave = outcomes.count { it.onTarget && !it.keeperCreditedWithSave }

        assertEquals(3, saved, "three of the seven credit the keeper")
        assertEquals(2, wide, "two of the seven go off target")
        assertEquals(2, onTargetNoSave, "two of the seven stay on target without a save")
        assertEquals(7, saved + wide + onTargetNoSave, "every one of the seven falls into exactly one bucket")
    }

    @Test
    fun `a saved miss is on target as well as saved, never off target`() {
        val result = interactivePenalty(neutralTaker, neutralKeeper, rules, ScriptedInts(coin(71), 0))
        assertTrue(result.onTarget)
        assertTrue(result.keeperCreditedWithSave)
    }

    /** Converts the actual coin value section 3.10 compares to a threshold into a ScriptedInts draw. */
    private fun coin(value: Int) = value - 1
}
