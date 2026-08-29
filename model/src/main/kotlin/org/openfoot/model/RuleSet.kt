package org.openfoot.model

/** Base weights for a two way duel. */
@SpecRef("3.6a")
data class DuelBaseWeights(val first: Double, val second: Double)

/** Base weights for the three shot outcomes, in the order goal, saved, wide. */
@SpecRef("3.6c")
data class ShotBaseWeights(val goal: Double, val saved: Double, val wide: Double)

/** One rung of the anti blowout ladder. */
@SpecRef("3.6c")
data class AntiBlowoutStep(val goalsAtLeast: Int, val weights: ShotBaseWeights)

/** The two non goal multipliers of a shot, before the base weights are applied. */
@SpecRef("3.6c")
data class ShotMultipliers(val saved: Double, val wide: Double)

/**
 * Every number the assist draw of section 3.6 reads, gathered in one value
 * object rather than left flat on RuleSet, which is already near its own
 * guidance of about sixty properties.
 *
 * eligibleSlots and slotWeights cover the pitch cells one to twenty five, the
 * same one to one indexing shooterSlotWeights above uses: slotWeights is read
 * by slot number directly, with index zero a padding entry that is never
 * reached because eligibleSlots excludes it.
 *
 * fullbackSlots names the two cells the spec calls laterais for this draw,
 * slots two and nine, and only those two. This is not the same set as
 * Position.FULLBACK's natural position, and it is not the same set as the
 * slot range that asks for a fullback in Slot.requiredPosition, which also
 * includes the wing back cells ten and seventeen. Those two wing back cells
 * take the plain seventeen to twenty five weight of ten in slotWeights and
 * none of the lateral bonuses below; the spec's own weight table marks only
 * two and nine as laterais and gives ten its own separate table entry.
 *
 * passingBonus through crossingFullbackBonus are read in a fixed order by a
 * chain that stops at its first match, exactly mirroring the finisher bonus
 * chain of shooterWeight above: Passing first, then Playmaking, then
 * Dribbling, then Pace, then Crossing. paceTotalBonus and paceWalkBonus are
 * deliberately unequal, one and two, which is section 3.15 item 4's
 * Velocidade defect reproduced through asymmetricWeightedPick; every other
 * bonus here applies identically to the total pass and the walk pass.
 *
 * heavyMarkingFullbackBonus applies on top of whichever branch above fired,
 * to any of the two lateral slots, only when the side's own marking equals
 * Marking.HEAVY. Marking's three values are LIGHT, HEAVY, VERY_HEAVY in that
 * ordinal order, so Pesada is the middle one and not the hardest setting;
 * VERY_HEAVY earns no bonus here at all.
 */
@SpecRef("3.6")
data class AssistRules(
    @property:SpecRef("3.6") val noAssistThreshold: Int,
    @property:SpecRef("3.6") val eligibleSlots: IntRange,
    @property:SpecRef("3.6") val slotWeights: List<Int>,
    @property:SpecRef("3.6") val fullbackSlots: List<Int>,
    @property:SpecRef("3.6") val passingBonus: Int,
    @property:SpecRef("3.6") val passingPlaymakingBonus: Int,
    @property:SpecRef("3.6") val playmakingBonus: Int,
    @property:SpecRef("3.6") val playmakingDribblingBonus: Int,
    @property:SpecRef("3.6") val dribblingBonus: Int,
    @property:SpecRef("3.6") val dribblingPaceBonus: Int,
    @property:SpecRef("3.15") val paceTotalBonus: Int,
    @property:SpecRef("3.15") val paceWalkBonus: Int,
    @property:SpecRef("3.6") val paceFullbackBonus: Int,
    @property:SpecRef("3.6") val crossingBonus: Int,
    @property:SpecRef("3.6") val crossingFullbackBonus: Int,
    @property:SpecRef("3.12") val heavyMarkingFullbackBonus: Int,
)

/**
 * How home advantage reaches the two non goal weights of a shot.
 *
 * This is the one place in the engine that needs a strategy object rather than
 * a constant, because the classic and modern behaviours differ in shape and not
 * only in value. The classic implementation carries two defects of the original
 * at once: the sign is inverted, so the home side converts worse, and the wide
 * weight is derived from the save weight, which destroys the defence versus
 * attack term in every match played on non neutral ground.
 */
@SpecRef("3.6c")
fun interface ShotHomeRule {
    fun adjust(multipliers: ShotMultipliers, advantage: HomeAdvantage, delta: Double): ShotMultipliers
}

/**
 * Every tunable number of the simulation, gathered in one value object.
 *
 * A difference between rule sets is expressed as a different value here, never
 * as a branch in the engine. The order of preference when a new divergence
 * appears is a constant, then a table, then a strategy object, and a boolean
 * only as a last resort.
 *
 * The class is flat on purpose. Nesting would make a delta read as
 * CLASSIC.copy(lines = CLASSIC.lines.copy(...)) and the whole point is that a
 * delta is one named argument. Split it up mechanically when it outgrows about
 * sixty fields.
 *
 * No property may be an array. Equality is what proves that MODERN differs from
 * CLASSIC only by the documented deltas, and arrays compare by identity.
 */
@SpecRef("3.4")
data class RuleSet(
    val id: RuleSetId,

    @property:SpecRef("3.4") val keeperSlot: Int,
    @property:SpecRef("3.4") val defenceSlots: IntRange,
    @property:SpecRef("3.4") val midfieldSlots: IntRange,
    @property:SpecRef("3.4") val attackSlots: IntRange,
    @property:SpecRef("3.6b") val centrebackSlots: IntRange,

    @property:SpecRef("3.4") val defenceTake: Int,
    @property:SpecRef("3.4") val defenceDivisor: Double,
    @property:SpecRef("3.4") val defenceMinimum: Int,
    @property:SpecRef("3.4") val midfieldTake: Int,
    @property:SpecRef("3.4") val midfieldDivisor: Double,
    @property:SpecRef("3.4") val midfieldMinimum: Int,
    @property:SpecRef("3.4") val attackTake: Int,
    @property:SpecRef("3.4") val attackDivisor: Double,

    @property:SpecRef("3.4") val shorthandedLineRating: Double,
    @property:SpecRef("3.4") val emptyAttackRating: Double,
    @property:SpecRef("3.4") val missingKeeperRating: Double,
    @property:SpecRef("3.4") val keeperOutOfPositionFactor: Double,
    @property:SpecRef("3.12") val markingMidfieldBonus: List<Double>,

    @property:SpecRef("3.5") val differenceDivisor: Double,
    @property:SpecRef("3.5") val lateDifferenceDivisor: Double,
    @property:SpecRef("3.5") val lateShotDifferenceDivisor: Double,
    @property:SpecRef("3.5") val compressionFirstSeason: Int,

    @property:SpecRef("3.6a") val possessionBaseWeights: DuelBaseWeights,
    @property:SpecRef("3.6a") val homeDuelBonus: Double,
    @property:SpecRef("3.6a") val duelWeightFloor: Double,

    @property:SpecRef("3.6b") val chanceBaseWeights: DuelBaseWeights,
    @property:SpecRef("3.6b") val emptyLineDuelWeight: Double,
    @property:SpecRef("3.6b") val chanceNoCentrebackWeight: Double,
    @property:SpecRef("3.6b") val chanceOneCentrebackWeight: Double,

    @property:SpecRef("3.6c") val shotBaseWeights: ShotBaseWeights,
    @property:SpecRef("3.6c") val antiBlowoutLadder: List<AntiBlowoutStep>,
    @property:SpecRef("3.6c") val outclassedGoalsAtLeast: Int,
    @property:SpecRef("3.6c") val outclassedReputationGap: Int,
    @property:SpecRef("3.6c") val outclassedWeights: ShotBaseWeights,
    @property:SpecRef("3.6c") val shotHomeDelta: Double,
    @property:SpecRef("3.6c") val shotHomeRule: ShotHomeRule,
    @property:SpecRef("3.6c") val savedNoCentrebackFactor: Double,
    @property:SpecRef("3.6c") val savedOneCentrebackFactor: Double,
    @property:SpecRef("3.6c") val missingShooterRating: Double,

    @property:SpecRef("3.6") val shooterSlotWeights: List<Int>,
    @property:SpecRef("3.6") val shooterEligibleSlots: IntRange,
    @property:SpecRef("3.6") val shooterFinishingBonus: Int,
    @property:SpecRef("3.6") val shooterHeadingBonus: Int,
    @property:SpecRef("3.6") val shooterHeadingDefenderBonus: Int,

    @property:SpecRef("3.6") val assist: AssistRules,

    @property:SpecRef("3.9") val energyDrainInterval: Int,
    @property:SpecRef("3.9") val energyCostByAge: List<Pair<Int, Int>>,
    @property:SpecRef("3.9") val keeperExemptHalf: Half,

    @property:SpecRef("3.8") val discipline: DisciplineRates,
    @property:SpecRef("3.8") val injuryRules: InjuryRules,
    @property:SpecRef("3.8") val substitutions: SubstitutionRules,
    @property:SpecRef("3.15") val manyYellowsAtLeast: Int,
    @property:SpecRef("3.15") val manyYellowsFactor: Int,
    @property:SpecRef("3.15") val manyRedsAtLeast: Int,
    @property:SpecRef("3.15") val redOverwriteFactor: Int,
    @property:SpecRef("3.15") val anyInjuryAtLeast: Int,
    @property:SpecRef("3.15") val injuryOverwriteFactor: Int,
    @property:SpecRef("3.15") val substitutingSidesPerPass: Int,
    @property:SpecRef("3.15") val scoreWindowArrivalsSide: List<TeamSide>,

    @property:SpecRef("3.15") val lineupRelaxationPasses: Int,
    @property:SpecRef("5.4") val benchTemplate: List<Int>,
) {
    /**
     * Midfield bonus for a marking setting. Indexed by ordinal so the table
     * stays data rather than a when chain.
     */
    fun markingBonus(marking: Marking): Double = markingMidfieldBonus[marking.ordinal]

    /**
     * Whose list of arrivals a score window's "do not take off the man who
     * just came on" check reads, when the window being run belongs to the
     * given side.
     *
     * Section 3.15 item 12 says the original compares the side's own index
     * against a value it never holds, so the list consulted is always the
     * home side's, whichever side the window belongs to. That makes this a
     * table of one entry per side rather than the identity it looks like it
     * ought to be, and it is indexed by ordinal for the same reason
     * markingBonus above is: the divergence stays data instead of becoming a
     * branch on which rule set is running.
     */
    @SpecRef("3.15")
    fun arrivalsSideFor(team: TeamSide): TeamSide = scoreWindowArrivalsSide[team.ordinal]
}
