package org.openfoot.model

/**
 * One row of a section 3.8 table: the three phases of one half.
 *
 * A value in this table is the N of the spec's rand(N) == 1, so it is a
 * denominator and a larger number means a rarer event.
 */
@SpecRef("3.8")
data class PhaseThresholds(val early: Int, val middle: Int, val late: Int) {
    fun of(phase: Int): Int = when (phase) {
        0 -> early
        1 -> middle
        else -> late
    }
}

/** One whole section 3.8 table: three phases in each of the two halves. */
@SpecRef("3.8")
data class HalfThresholds(val firstHalf: PhaseThresholds, val secondHalf: PhaseThresholds) {
    fun of(half: Half): PhaseThresholds = if (half == Half.FIRST) firstHalf else secondHalf
}

/**
 * The six cell groups section 3.8 draws a card or an injury's victim from,
 * plus the keeper as a seventh group of his own.
 *
 * Named G0 to G5 rather than after a role, because the spec itself names them
 * that way and none of the six carries a role name of its own. KEEPER is kept
 * separate from G0 through G5 rather than folded into whichever group's cell
 * range happens to include slot one, matching the spec's own goleiro branch,
 * which every one of section 3.8's three distributions tests on its own.
 */
@SpecRef("3.8")
enum class RiskGroup { G0, G1, G2, G3, G4, G5, KEEPER }

/**
 * Everything section 3.8's per minute roll reads that no rule set changes.
 *
 * Grouped rather than flat, unlike most of RuleSet, because none of it is a
 * lever: the two documented defects of section 3.15 item 5 are the threshold
 * overwrites, which stay flat properties of RuleSet so that a MODERN delta is
 * still one named argument.
 */
@SpecRef("3.8")
data class DisciplineRates(
    @property:SpecRef("3.8") val victimHomeThreshold: Int,
    @property:SpecRef("3.8") val phaseBounds: List<Int>,
    @property:SpecRef("3.8") val yellow: HalfThresholds,
    @property:SpecRef("3.8") val red: HalfThresholds,
    @property:SpecRef("3.8") val injury: HalfThresholds,
    @property:SpecRef("3.12") val yellowMarkingRelief: List<Int>,
    @property:SpecRef("3.8") val riskGroupSlots: List<IntRange>,
    @property:SpecRef("3.8") val yellowRisk: List<Band<RiskGroup>>,
    @property:SpecRef("3.8") val redRisk: List<Band<RiskGroup>>,
    @property:SpecRef("3.8") val injuryRisk: List<Band<RiskGroup>>,
) {
    /**
     * The victim's marking relief on the yellow threshold. Indexed by ordinal,
     * like RuleSet.markingBonus, so the table stays data rather than a when
     * chain. The red and injury thresholds take no relief at all; section 3.8
     * applies this only to the yellow row.
     *
     * Section 3.8 also says a marking value outside 0 to 2 falls back to the
     * relief of 30, the LIGHT row. That branch has no code here and needs
     * none: Marking is a three member enum, LIGHT, HEAVY and VERY_HEAVY, whose
     * ordinal can only ever be nought, one or two, and yellowMarkingRelief is
     * built with exactly three entries in RuleSets.kt, so this index can never
     * fall outside the list. A guard here would be dead code defending against
     * a value the type system already makes unreachable, which is worse than
     * stating so in a docstring. Marking.ofOrdinal is the one place an out of
     * range ordinal could ever be produced, from data read off the outside
     * world, and it already throws rather than falling back to LIGHT; this
     * function is never reached with a value that call rejected.
     */
    fun markingRelief(marking: Marking): Int = yellowMarkingRelief[marking.ordinal]
}

/**
 * What one age bracket contributes to an injury's length.
 *
 * Section 3.8 writes six branches that differ in three independent ways: some
 * take the energy base and the youngest bracket does not, each adds a
 * constant of its own, and the two oldest add the long term draw on top of
 * the constant. Written out as three fields rather than six formulas, the
 * table becomes data and the arithmetic that combines them happens once, in
 * the engine's injuryOutcome.
 */
@SpecRef("3.8")
data class InjuryTerm(
    @property:SpecRef("3.8") val usesEnergyBase: Boolean,
    @property:SpecRef("3.8") val constant: Int,
    @property:SpecRef("3.8") val usesLongTerm: Boolean,
)

/**
 * Everything section 3.8's injury duration reads that no rule set changes.
 *
 * ageTerms is a draw table in shape only: it is read with Band.pick(age) and
 * never with Band.bound(), because its last band is the sentinel idiom
 * energyCostByAge already uses, reaching to Int.MAX_VALUE so that no age
 * falls through. severity is a genuine rand(100) draw table, and its bands
 * are deliberately not disjoint: section 3.8 writes it as an if chain, ==1
 * before <4 before <10, and draw nought falls through the first test and
 * lands on the second. That overlap is the spec's own and the band order is
 * load bearing; do not tidy it into a disjoint table.
 *
 * permanentLossFloor is what the post thirty five strength loss clamps to
 * when subtracting permanentLossAmount would carry the player below nought,
 * and only then: a strength that lands exactly on nought after the
 * subtraction is left there rather than raised to the floor. The engine's own
 * injuryOutcome is the reader; see its docstring for the arithmetic.
 */
@SpecRef("3.8")
data class InjuryRules(
    @property:SpecRef("3.8") val energyBase: List<Band<Int>>,
    @property:SpecRef("3.8") val shortTermDraw: IntRange,
    @property:SpecRef("3.8") val longTermDraw: IntRange,
    @property:SpecRef("3.8") val longTermOffset: Int,
    @property:SpecRef("3.8") val ageTerms: List<Band<InjuryTerm>>,
    @property:SpecRef("3.8") val severity: List<Band<Int>>,
    @property:SpecRef("3.8") val permanentLossAge: Int,
    @property:SpecRef("3.8") val permanentLossAmount: Int,
    @property:SpecRef("3.8") val permanentLossFloor: Int,
)

/**
 * Everything section 3.8's substitutions read that no rule set changes.
 *
 * Grouped like DisciplineRates above and for the same reason: none of it is a
 * lever, and the two rule sets carry identical values here. Section 3.15 does
 * name two defects in the substitution block, items 11 and 12, but both of
 * them live where DisciplineRates's own two do, as flat properties of RuleSet,
 * so that a MODERN delta stays one named argument rather than a nested copy.
 * See RuleSet.substitutingSidesPerPass and RuleSet.arrivalsSideFor.
 *
 * The two deficit tables are indexed by TeamSide.ordinal, like
 * RuleSet.markingBonus is indexed by Marking.ordinal, so that the one place
 * section 3.8 holds the two sides to different standards stays a table rather
 * than a when chain. A deficit is the opponent's goals minus the side's own,
 * so nought means level and one means a goal down.
 *
 * sendingOffSacrificeMaxSlot is the cell number at or below which a dismissal
 * costs the side a forward as well as the man himself. Section 3.8 writes it
 * as "slot <= 13", which is the keeper, the whole defence and the holding
 * midfield of section 3.4's own ranges, so a side that loses anybody from that
 * part of the pitch closes the gap by taking a forward off. It is a boundary
 * rather than a range because everything above it is left alone.
 *
 * keeperSacrificeFallbackCells is the third range sacrificeTarget tries, and it
 * is reachable only when the man sent off is the keeper himself. Section 3.8
 * names it as the exception to the ordinary two range search: with nobody in
 * sacrificeCells' two ranges, a dismissed keeper still costs the side a man
 * from anywhere in this range, cells two to twenty five, while a dismissed
 * outfielder in the same shape leaves the AI with nobody to sacrifice.
 *
 * routinePools is a genuine rand(100) draw table and is read with pick()
 * against bound(). Section 3.8 writes it as a descending if chain, greater
 * than ninety first, and it is transcribed here in ascending order instead.
 * That is safe only because the three bands are disjoint, unlike
 * InjuryRules.severity, whose overlapping bands make its order load bearing:
 * reversing a disjoint chain cannot change which band a draw lands in, and
 * ascending order is what lets bound() report a hundred rather than the fifty
 * one a descending list would.
 */
@SpecRef("3.8")
data class SubstitutionRules(
    @property:SpecRef("3.8") val maxPerSide: Int,
    @property:SpecRef("3.8") val windowOpensFrom: Int,
    @property:SpecRef("3.8") val sacrificeCells: List<IntRange>,
    @property:SpecRef("3.8") val keeperSacrificeFallbackCells: IntRange,
    @property:SpecRef("3.8") val sendingOffSacrificeMaxSlot: Int,
    @property:SpecRef("3.8") val chasingWindow: IntRange,
    @property:SpecRef("3.8") val chasingCount: Int,
    @property:SpecRef("3.8") val extraChasingPercent: Int,
    @property:SpecRef("3.8") val routinePools: List<Band<IntRange>>,
    @property:SpecRef("3.8") val routineCount: Int,
    @property:SpecRef("3.8") val lateWindow: IntRange,
    @property:SpecRef("3.8") val lateChancePercents: List<Int>,
    @property:SpecRef("3.8") val halfTimeSwapPercent: Int,
    @property:SpecRef("3.8") val halfTimeDeficit: List<Int>,
    @property:SpecRef("3.8") val chasingDeficit: List<Int>,
    @property:SpecRef("3.8") val tirednessThreshold: Int,
    @property:SpecRef("3.8") val lateTirednessThreshold: Int,
    @property:SpecRef("3.8") val lateTirednessFromMinute: Int,
) {
    /**
     * How far behind the side has to be at the interval before it considers a
     * change, and how far behind on a chasing minute. Both read the same way,
     * a deficit at or above the figure, which is what lets the chasing rule's
     * "loses or draws" for the home side be the nought of a table rather than
     * a second comparison of its own.
     */
    fun halfTimeDeficitFor(team: TeamSide): Int = halfTimeDeficit[team.ordinal]

    fun chasingDeficitFor(team: TeamSide): Int = chasingDeficit[team.ordinal]
}
