package org.openfoot.model

/**
 * Which set of rules a career runs under.
 *
 * This is the only file allowed to name a preset. Engine code takes a RuleSet
 * as a parameter and never asks which one it is, and an architecture test
 * enforces that.
 */
enum class RuleSetId {
    CLASSIC,
    MODERN,
}

/**
 * Home advantage as the original applies it to a shot.
 *
 * Two defects of the original live here together. The sign is inverted, so the
 * home side ends up with higher weights on both non goal outcomes and converts
 * worse than the visitor. And the wide weight is overwritten from the save
 * weight, which throws away the defence versus attack comparison in every match
 * that is not on neutral ground.
 *
 * Both are reproduced deliberately. See spec section 3.6c.
 */
@SpecRef("3.6c")
object ClassicShotHomeRule : ShotHomeRule {
    override fun adjust(
        multipliers: ShotMultipliers,
        advantage: HomeAdvantage,
        delta: Double,
    ): ShotMultipliers = when (advantage) {
        HomeAdvantage.NONE -> multipliers
        HomeAdvantage.POSSESSOR_HOME -> {
            val saved = multipliers.saved + delta
            ShotMultipliers(saved, saved + delta)
        }
        HomeAdvantage.POSSESSOR_AWAY -> {
            val saved = multipliers.saved - delta
            ShotMultipliers(saved, saved - delta)
        }
    }
}

/**
 * Home advantage applied the way the rest of the engine applies it: the side at
 * home gets lower weights on both non goal outcomes, so it converts better, and
 * the wide weight keeps the defence versus attack term it was computed from.
 */
@SpecRef("3.6c")
object ModernShotHomeRule : ShotHomeRule {
    override fun adjust(
        multipliers: ShotMultipliers,
        advantage: HomeAdvantage,
        delta: Double,
    ): ShotMultipliers = when (advantage) {
        HomeAdvantage.NONE -> multipliers
        HomeAdvantage.POSSESSOR_HOME ->
            ShotMultipliers(multipliers.saved - delta, multipliers.wide - delta)
        HomeAdvantage.POSSESSOR_AWAY ->
            ShotMultipliers(multipliers.saved + delta, multipliers.wide + delta)
    }
}

/**
 * The available rule sets.
 *
 * CLASSIC reproduces the original bug for bug and is the default. It is also
 * the only way to check the engine against the original, by comparing
 * statistical output, so it is not merely nostalgia.
 *
 * MODERN fixes what was clearly broken. It must stay a copy of CLASSIC with
 * only documented deltas, and a test asserts exactly that.
 */
object RuleSets {

    @SpecRef("3.4")
    val CLASSIC = RuleSet(
        id = RuleSetId.CLASSIC,

        keeperSlot = 1,
        defenceSlots = 2..9,
        midfieldSlots = 10..17,
        attackSlots = 19..25,
        centrebackSlots = 3..8,

        defenceTake = 5,
        defenceDivisor = 5.0,
        defenceMinimum = 3,
        midfieldTake = 5,
        midfieldDivisor = 5.0,
        midfieldMinimum = 3,
        attackTake = 3,
        attackDivisor = 3.0,

        shorthandedLineRating = 0.01,
        emptyAttackRating = 0.0,
        missingKeeperRating = 0.1,
        keeperOutOfPositionFactor = 0.2,
        markingMidfieldBonus = listOf(0.0, 0.04, 0.08),

        differenceDivisor = 8.0,
        lateDifferenceDivisor = 11.0,
        lateShotDifferenceDivisor = 10.0,
        compressionFirstSeason = 5,

        possessionBaseWeights = DuelBaseWeights(55.0, 45.0),
        homeDuelBonus = 0.3,
        duelWeightFloor = 0.2,

        chanceBaseWeights = DuelBaseWeights(50.0, 50.0),
        emptyLineDuelWeight = 0.1,
        chanceNoCentrebackWeight = 0.10,
        chanceOneCentrebackWeight = 0.05,

        shotBaseWeights = ShotBaseWeights(5.5, 35.55, 15.0),
        antiBlowoutLadder = listOf(
            AntiBlowoutStep(3, ShotBaseWeights(4.5, 40.55, 15.0)),
            AntiBlowoutStep(5, ShotBaseWeights(3.0, 40.55, 15.0)),
            AntiBlowoutStep(6, ShotBaseWeights(0.5, 40.55, 15.0)),
        ),
        outclassedGoalsAtLeast = 2,
        outclassedReputationGap = 2,
        outclassedWeights = ShotBaseWeights(3.0, 40.55, 15.0),
        shotHomeDelta = 0.1,
        shotHomeRule = ClassicShotHomeRule,
        savedNoCentrebackFactor = 0.2,
        savedOneCentrebackFactor = 0.4,
        missingShooterRating = 0.1,

        shooterSlotWeights = listOf(
            0, 0,
            1, 1, 1, 1, 1, 1, 1, 1,
            8,
            4, 4, 4,
            8, 8, 8, 8,
            22, 22, 22, 22, 22, 22, 22, 22,
        ),
        shooterEligibleSlots = 2..25,
        shooterFinishingBonus = 4,
        shooterHeadingBonus = 2,
        shooterHeadingDefenderBonus = 2,

        assist = AssistRules(
            noAssistThreshold = 80,
            eligibleSlots = 1..25,
            slotWeights = listOf(
                0,
                1,
                10, 2, 2, 2, 2, 2, 2, 10,
                10, 4, 4, 4, 20, 20, 20,
                10, 10, 10, 10, 10, 10, 10, 10, 10,
            ),
            fullbackSlots = listOf(2, 9),
            passingBonus = 10,
            passingPlaymakingBonus = 5,
            playmakingBonus = 2,
            playmakingDribblingBonus = 2,
            dribblingBonus = 2,
            dribblingPaceBonus = 2,
            paceTotalBonus = 1,
            paceWalkBonus = 2,
            paceFullbackBonus = 2,
            crossingBonus = 5,
            crossingFullbackBonus = 2,
            heavyMarkingFullbackBonus = 20,
        ),

        energyDrainInterval = 7,
        energyCostByAge = listOf(
            20 to 1,
            25 to 2,
            31 to 3,
            36 to 4,
            Int.MAX_VALUE to 5,
        ),
        keeperExemptHalf = Half.FIRST,

        discipline = DisciplineRates(
            victimHomeThreshold = 55,
            phaseBounds = listOf(15, 30),
            yellow = HalfThresholds(
                firstHalf = PhaseThresholds(70, 40, 30),
                secondHalf = PhaseThresholds(45, 40, 30),
            ),
            red = HalfThresholds(
                firstHalf = PhaseThresholds(1200, 900, 800),
                secondHalf = PhaseThresholds(800, 700, 550),
            ),
            injury = HalfThresholds(
                firstHalf = PhaseThresholds(1500, 1000, 800),
                secondHalf = PhaseThresholds(800, 600, 600),
            ),
            yellowMarkingRelief = listOf(30, 10, 0),
            riskGroupSlots = listOf(10..13, 14..17, 3..8, 2..3, 8..9, 19..24, 1..1),
            yellowRisk = listOf(
                Band(0..24, RiskGroup.G0),
                Band(25..39, RiskGroup.G1),
                Band(40..64, RiskGroup.G2),
                Band(65..72, RiskGroup.G3),
                Band(73..81, RiskGroup.G4),
                Band(82..84, RiskGroup.KEEPER),
                Band(85..99, RiskGroup.G5),
            ),
            redRisk = listOf(
                Band(0..0, RiskGroup.KEEPER),
                Band(1..79, RiskGroup.G0),
                Band(80..109, RiskGroup.G1),
                Band(110..159, RiskGroup.G2),
                Band(160..169, RiskGroup.G3),
                Band(170..189, RiskGroup.G4),
                Band(190..199, RiskGroup.G5),
            ),
            injuryRisk = listOf(
                Band(0..0, RiskGroup.KEEPER),
                Band(1..149, RiskGroup.G0),
                Band(150..249, RiskGroup.G1),
                Band(250..319, RiskGroup.G2),
                Band(320..359, RiskGroup.G3),
                Band(360..419, RiskGroup.G4),
                Band(420..499, RiskGroup.G5),
            ),
        ),
        injuryRules = InjuryRules(
            energyBase = listOf(
                Band(0..9, 5),
                Band(10..49, 1),
                Band(50..Int.MAX_VALUE, 0),
            ),
            shortTermDraw = 0..13,
            longTermDraw = 0..19,
            longTermOffset = 5,
            ageTerms = listOf(
                Band(Int.MIN_VALUE..20, InjuryTerm(usesEnergyBase = false, constant = 0, usesLongTerm = false)),
                Band(21..25, InjuryTerm(usesEnergyBase = true, constant = 1, usesLongTerm = false)),
                Band(26..30, InjuryTerm(usesEnergyBase = true, constant = 2, usesLongTerm = false)),
                Band(31..35, InjuryTerm(usesEnergyBase = true, constant = 3, usesLongTerm = false)),
                Band(36..45, InjuryTerm(usesEnergyBase = true, constant = 0, usesLongTerm = true)),
                Band(46..Int.MAX_VALUE, InjuryTerm(usesEnergyBase = true, constant = 10, usesLongTerm = true)),
            ),
            severity = listOf(
                Band(1..1, 70),
                Band(0..3, 40),
                Band(4..9, 20),
                Band(10..99, 0),
            ),
            permanentLossAge = 35,
            permanentLossAmount = 5,
            permanentLossFloor = 1,
        ),
        substitutions = SubstitutionRules(
            maxPerSide = 5,
            windowOpensFrom = 5,
            sacrificeCells = listOf(18..25, 14..17),
            keeperSacrificeFallbackCells = 2..25,
            sendingOffSacrificeMaxSlot = 13,
            chasingWindow = 19..38,
            chasingCount = 2,
            extraChasingPercent = 69,
            routinePools = listOf(
                Band(0..50, 36..42),
                Band(51..90, 16..35),
                Band(91..99, 5..15),
            ),
            routineCount = 2,
            lateWindow = 43..47,
            lateChancePercents = listOf(79, 49),
            halfTimeSwapPercent = 49,
            halfTimeDeficit = listOf(1, 2),
            chasingDeficit = listOf(0, 1),
            tirednessThreshold = 60,
            lateTirednessThreshold = 90,
            lateTirednessFromMinute = 40,
        ),
        manyYellowsAtLeast = 6,
        manyYellowsFactor = 2,
        manyRedsAtLeast = 2,
        redOverwriteFactor = 2,
        anyInjuryAtLeast = 1,
        injuryOverwriteFactor = 5,
        substitutingSidesPerPass = 1,
        scoreWindowArrivalsSide = listOf(TeamSide.HOME, TeamSide.HOME),

        lineupRelaxationPasses = 2,
        benchTemplate = listOf(1, 1, 2, 4, 4, 12, 15, 15, 20, 20, 23),
    )

    /**
     * Exactly seven deltas, each of them a defect of the original.
     *
     * The first two live in the aggregate and shot code: slot eighteen
     * counting in no line, and home advantage applied with the wrong sign and
     * overwriting the wide weight it was handed.
     *
     * The third lives in the automatic lineup. Section 3.2 describes three
     * relaxation passes and section 3.15 item 7 says a loop bound leaves one
     * of them unreachable, so classic runs two and modern runs all three. It
     * changes which eleven the AI fields, not how a match is played.
     *
     * The fourth and fifth live in section 3.8's card thresholds, both named
     * as defects by section 3.15 item 5: after two sendings off the yellow
     * threshold is overwritten to twice the red one, and after one injury it
     * is overwritten again to five times the injury one, both of which
     * collapse the booking rate for the rest of the match. Modern switches
     * both off by putting their trigger count out of reach, the same sentinel
     * idiom energyCostByAge already uses for its own fall through, rather than
     * through a flag. The doubling past five yellows is not named as a defect
     * and both rule sets keep it.
     *
     * The last two live in section 3.8's substitutions and are section 3.15's
     * items 11 and 12. Item 11 is the away side's window being swallowed by
     * the home side's: the two windows of one pass are run in one go, the home
     * side first, and a pass in which the home side actually changed somebody
     * never examines the away side's window at all. Classic allows one side
     * per pass and modern allows both, which is a count rather than a flag
     * because what the original limits is how many changes one pass can carry.
     * Item 12 is the just came on check reading the home side's list of
     * arrivals whichever side it is protecting, so the home side is never
     * asked to take off a man it has just brought on and the away side has no
     * protection whatever; modern points each side at its own list. The two
     * are separate deltas rather than one because they are separate defects
     * with separate effects: one costs the away side windows and the other
     * costs it the protection inside a window it does get.
     *
     * The wasted keeper draw of section 3.8 is deliberately not a third delta
     * here. This docstring is where that decision is argued, and
     * docs/known-quirks.md carries the same argument for a reader who is not
     * in the code; OPEN-QUESTIONS item 44 records the wasted window itself but
     * says nothing about rule sets, so it is not the authority for this.
     *
     * Three reasons. Section 3.15 does not name it: every delta above repairs
     * something that list calls a defect, and this is written in section 3.8
     * as the rule itself and confirmed as the original's own behaviour.
     * Repairing it would move a probability rather than a rule, roughly one
     * score window in eleven, which is the same kind of change as the fixed
     * line divisors below that this file already refuses to make. And the
     * repair is not even determined: a window that must not be wasted has to
     * either redraw or draw over the ten outfielders instead of the eleven,
     * and nothing in the spec says which, so modern would be inventing a rule
     * rather than removing a mistake.
     *
     * The fixed line divisors of five, five and three are deliberately not
     * changed here. That is a balance decision rather than a defect, and it
     * would need a lever of its own.
     */
    @SpecRef("3.15")
    val MODERN = CLASSIC.copy(
        id = RuleSetId.MODERN,
        attackSlots = 18..25,
        shotHomeRule = ModernShotHomeRule,
        lineupRelaxationPasses = 3,
        manyRedsAtLeast = Int.MAX_VALUE,
        anyInjuryAtLeast = Int.MAX_VALUE,
        substitutingSidesPerPass = 2,
        scoreWindowArrivalsSide = listOf(TeamSide.HOME, TeamSide.AWAY),
    )
}
