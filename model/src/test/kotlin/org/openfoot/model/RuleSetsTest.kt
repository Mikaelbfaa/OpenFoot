package org.openfoot.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RuleSetsTest {

    /**
     * Ten deltas, each a defect of the original. Slot eighteen counting in
     * no line, home advantage applied with the wrong sign, one lineup
     * relaxation pass unreachable, the two card threshold overwrites of
     * section 3.15 item 5, switched off by putting their trigger counts out of
     * reach rather than by a flag, and the two substitution defects of section
     * 3.15 items 11 and 12: one side's change per pass swallowing the other
     * side's window, and the just came on check reading the home side's list
     * of arrivals whichever side it is protecting.
     *
     * The last three are section 3.15 items 4, 15 and 14. Item 4 is the
     * Velocidade defect of the assist draw, where the Pace trait pays the walk
     * pass two and the total pass one, and it is the only one of the ten that
     * is a nested copy rather than a flat argument, because the figure lives
     * on AssistRules. Item 15 is the missed penalty term of section 3.14 step
     * 3 multiplying the own goal counter, and item 14 is the minutes penalty
     * of step 10 reading the minute of the player's last event rather than his
     * time on the pitch; both differ in shape and not in value, so both are
     * strategy objects in the shape shotHomeRule already set.
     *
     * The wasted keeper draw of section 3.8 is deliberately not among them.
     * Section 3.15 does not name it a defect and section 3.8 states it as the
     * rule; repairing it would move a probability, about one score window in
     * eleven, rather than a rule; and the repair is not even determined, since
     * a window that may not be wasted has to either redraw or draw over ten
     * men instead of eleven and nothing says which. The argument is carried in
     * full by the docstring of RuleSets.MODERN and by docs/known-quirks.md.
     * OPEN-QUESTIONS item 44 records the wasted window itself and not this
     * decision, so it is not where to look for it.
     */
    @Test
    fun `the modern rules differ from the classic ones by ten named deltas`() {
        assertEquals(
            RuleSets.CLASSIC,
            RuleSets.MODERN.copy(
                id = RuleSetId.CLASSIC,
                attackSlots = 19..25,
                shotHomeRule = ClassicShotHomeRule,
                lineupRelaxationPasses = 2,
                manyRedsAtLeast = 2,
                anyInjuryAtLeast = 1,
                substitutingSidesPerPass = 1,
                scoreWindowArrivalsSide = listOf(TeamSide.HOME, TeamSide.HOME),
                assist = RuleSets.MODERN.assist.copy(paceWalkBonus = 2),
                missedPenaltyRule = ClassicMissedPenaltyRule,
                minutesPlayedRule = ClassicMinutesPlayedRule,
            ),
        )
    }

    /**
     * Section 3.15 item 4: the Pace trait is worth one to the sum the assist
     * draw is scaled against and two to the walk that looks for the winner,
     * so a player carrying it takes a share of the draw the sum never
     * accounted for. The modern rules make the two agree.
     *
     * The total figure is asserted for both rule sets and not only the walk
     * one, because a repair that moved the total down to match a walk of two
     * would satisfy an assertion on their equality alone while changing what
     * every Pace player is worth in the sum as well.
     */
    @Test
    fun `classic pays the pace trait more on the walk than on the total`() {
        assertEquals(1, RuleSets.CLASSIC.assist.paceTotalBonus)
        assertEquals(2, RuleSets.CLASSIC.assist.paceWalkBonus)
        assertEquals(1, RuleSets.MODERN.assist.paceTotalBonus)
        assertEquals(1, RuleSets.MODERN.assist.paceWalkBonus)
    }

    /**
     * Section 3.15 item 15, read off the two strategy objects directly.
     *
     * Three fixtures, because no two of them agree under both readings. A
     * missed penalty with no own goal is nought under the classic rule and one
     * charge under the modern one; a missed penalty with an own goal is one
     * charge under both, which is exactly why it cannot be the only fixture;
     * and three missed penalties with two own goals separates them again and
     * in the other direction, since the classic rule follows the own goals and
     * the modern one the penalties.
     */
    @Test
    fun `the classic missed penalty rule multiplies own goals and the modern one missed penalties`() {
        val charge = -1.2

        assertEquals(0.0, ClassicMissedPenaltyRule.adjust(1, 0, charge), 1e-12)
        assertEquals(charge, ModernMissedPenaltyRule.adjust(1, 0, charge), 1e-12)

        assertEquals(charge, ClassicMissedPenaltyRule.adjust(1, 1, charge), 1e-12)
        assertEquals(charge, ModernMissedPenaltyRule.adjust(1, 1, charge), 1e-12)

        assertEquals(2 * charge, ClassicMissedPenaltyRule.adjust(3, 2, charge), 1e-12)
        assertEquals(3 * charge, ModernMissedPenaltyRule.adjust(3, 2, charge), 1e-12)
    }

    /**
     * Section 3.15 item 14, read off the two strategy objects directly.
     *
     * The two arguments are deliberately unequal and neither is the smaller,
     * the larger or the first in both fixtures: a rule that always returned
     * the first argument, the smaller of the two or the larger of the two
     * would agree with one of the four assertions below and disagree with
     * another.
     */
    @Test
    fun `the classic minutes rule keeps the event figure and the modern one the actual`() {
        assertEquals(5, ClassicMinutesPlayedRule.minutes(eventDerived = 5, actual = 93))
        assertEquals(93, ModernMinutesPlayedRule.minutes(eventDerived = 5, actual = 93))
        assertEquals(88, ClassicMinutesPlayedRule.minutes(eventDerived = 88, actual = 12))
        assertEquals(12, ModernMinutesPlayedRule.minutes(eventDerived = 88, actual = 12))
    }

    /**
     * Section 3.15 item 11: one pass of the classic rules carries one side's
     * change and a pass of the modern ones carries both. Two is every side
     * there is, so the modern figure is no limit at all rather than a larger
     * one.
     */
    @Test
    fun `classic lets one side change per pass and modern both`() {
        assertEquals(1, RuleSets.CLASSIC.substitutingSidesPerPass)
        assertEquals(TeamSide.entries.size, RuleSets.MODERN.substitutingSidesPerPass)
    }

    /**
     * Section 3.15 item 12: the classic rules read the home side's list of
     * arrivals for both sides, so the home side is protected from taking off a
     * man it has just brought on and the away side is not; the modern rules
     * read each side's own list.
     */
    @Test
    fun `classic reads the home arrivals list for both sides`() {
        assertEquals(TeamSide.HOME, RuleSets.CLASSIC.arrivalsSideFor(TeamSide.HOME))
        assertEquals(TeamSide.HOME, RuleSets.CLASSIC.arrivalsSideFor(TeamSide.AWAY))
        assertEquals(TeamSide.HOME, RuleSets.MODERN.arrivalsSideFor(TeamSide.HOME))
        assertEquals(TeamSide.AWAY, RuleSets.MODERN.arrivalsSideFor(TeamSide.AWAY))
    }

    @Test
    fun `classic cannot reach the last lineup relaxation pass and modern can`() {
        assertEquals(2, RuleSets.CLASSIC.lineupRelaxationPasses)
        assertEquals(3, RuleSets.MODERN.lineupRelaxationPasses)
    }

    @Test
    fun `classic excludes slot eighteen from the attack line`() {
        assertEquals(19..25, RuleSets.CLASSIC.attackSlots)
        assertEquals(18..25, RuleSets.MODERN.attackSlots)
    }

    @Test
    fun `the line divisors are fixed and identical in both rule sets`() {
        listOf(RuleSets.CLASSIC, RuleSets.MODERN).forEach { rules ->
            assertEquals(5.0, rules.defenceDivisor)
            assertEquals(5.0, rules.midfieldDivisor)
            assertEquals(3.0, rules.attackDivisor)
        }
    }

    @Test
    fun `the shooter weight table covers slots zero to twenty five`() {
        val weights = RuleSets.CLASSIC.shooterSlotWeights
        assertEquals(26, weights.size)
        assertEquals(0, weights[0])
        assertEquals(0, weights[1])
        (2..9).forEach { assertEquals(1, weights[it], "slot $it") }
        assertEquals(8, weights[10])
        (11..13).forEach { assertEquals(4, weights[it], "slot $it") }
        (14..17).forEach { assertEquals(8, weights[it], "slot $it") }
        (18..25).forEach { assertEquals(22, weights[it], "slot $it") }
    }

    @Test
    fun `the marking bonus table is indexed by ordinal`() {
        val rules = RuleSets.CLASSIC
        assertEquals(0.0, rules.markingBonus(Marking.LIGHT))
        assertEquals(0.04, rules.markingBonus(Marking.HEAVY))
        assertEquals(0.08, rules.markingBonus(Marking.VERY_HEAVY))
    }

    @Test
    fun `the anti blowout ladder rises with goals already scored`() {
        val ladder = RuleSets.CLASSIC.antiBlowoutLadder
        assertEquals(listOf(3, 5, 6), ladder.map { it.goalsAtLeast })
        assertEquals(4.5, ladder[0].weights.goal)
        assertEquals(3.0, ladder[1].weights.goal)
        assertEquals(0.5, ladder[2].weights.goal)
    }

    @Test
    fun `classic home rule raises both non goal weights for the home side`() {
        val start = ShotMultipliers(1.0, 1.0)
        val home = ClassicShotHomeRule.adjust(start, HomeAdvantage.POSSESSOR_HOME, 0.1)
        assertEquals(1.1, home.saved, 1e-12)
        assertEquals(1.2, home.wide, 1e-12)
    }

    @Test
    fun `classic home rule lowers both non goal weights for the away side`() {
        val start = ShotMultipliers(1.0, 1.0)
        val away = ClassicShotHomeRule.adjust(start, HomeAdvantage.POSSESSOR_AWAY, 0.1)
        assertEquals(0.9, away.saved, 1e-12)
        assertEquals(0.8, away.wide, 1e-12)
    }

    @Test
    fun `classic home rule discards the wide weight it was given`() {
        val withDifferentWide = ShotMultipliers(saved = 1.0, wide = 7.0)
        val home = ClassicShotHomeRule.adjust(withDifferentWide, HomeAdvantage.POSSESSOR_HOME, 0.1)
        assertEquals(1.2, home.wide, 1e-12)
    }

    @Test
    fun `modern home rule keeps the wide weight and favours the home side`() {
        val start = ShotMultipliers(saved = 1.0, wide = 1.25)
        val home = ModernShotHomeRule.adjust(start, HomeAdvantage.POSSESSOR_HOME, 0.1)
        val away = ModernShotHomeRule.adjust(start, HomeAdvantage.POSSESSOR_AWAY, 0.1)
        assertEquals(0.9, home.saved, 1e-12)
        assertEquals(1.15, home.wide, 1e-12)
        assertEquals(1.1, away.saved, 1e-12)
        assertEquals(1.35, away.wide, 1e-12)
    }

    @Test
    fun `neither home rule touches a neutral ground shot`() {
        val start = ShotMultipliers(1.3, 0.7)
        assertSame(start, ClassicShotHomeRule.adjust(start, HomeAdvantage.NONE, 0.1))
        assertSame(start, ModernShotHomeRule.adjust(start, HomeAdvantage.NONE, 0.1))
    }
}
