package org.openfoot.model

/**
 * One row of section 3.14's base table: what a draw, a win and a defeat are
 * worth to a player of one strength band, before a single adjustment.
 *
 * The three are stored side by side rather than as three parallel tables
 * because the original reads exactly one of them per player and the row is
 * what a reader of the spec's own table sees. Two cells of the table happen
 * to be equal, the win of the weakest band and the win of the next one up,
 * which is why forResult below picks by comparison and never by which figure
 * is the largest.
 */
@SpecRef("3.14")
data class RatingBase(
    @property:SpecRef("3.14") val draw: Double,
    @property:SpecRef("3.14") val win: Double,
    @property:SpecRef("3.14") val loss: Double,
) {
    /**
     * The cell of this row the match result selects, from the point of view
     * of the side the player belongs to.
     *
     * Kept here rather than in the engine so that the convention for a level
     * scoreline, which is the draw cell and never the loss one, is stated
     * once beside the numbers it selects.
     */
    @SpecRef("3.14")
    fun forResult(goalsFor: Int, goalsAgainst: Int): Double = when {
        goalsFor > goalsAgainst -> win
        goalsFor < goalsAgainst -> loss
        else -> draw
    }
}

/**
 * Section 3.14 step 2, which is read only for a player standing in a midfield
 * cell and only when the two sides' possession counters differ.
 *
 * slots is this step's own band and is deliberately a field here rather than
 * a reuse of RuleSet.midfieldSlots, following the precedent AssistRules set:
 * every draw and every adjustment of the spec carries its own slot table, and
 * two tables that agree today are still two tables. RuleSet.midfieldSlots is
 * read by section 3.4's line aggregate and nothing says the two must move
 * together.
 *
 * moreBonus and moreReducedBonus are the two halves of one draw rather than
 * two separate terms: with the better of the two possession counters a player
 * takes moreBonus, or moreReducedBonus instead on a one in three chance. The
 * pair below, lessPenalty and lessReducedPenalty, is the same shape with the
 * signs reversed, and the reduced figure is the milder one on both sides of
 * the comparison. That is the opposite of DefensiveRatingRules.moreRaisedBonus
 * below, where the one in three chance pays more rather than less, and the two
 * must not be made to look alike.
 *
 * holdingMoreBonus and holdingLessPenalty go to whoever's derived sub role of
 * section 4.3 came out defensive, tested alone once the slots band above has
 * already been applied. Neither the cell nor the natural position is
 * consulted: an attacking midfielder with a defensive style at 14 to 16
 * collects it, a playmaker at 12 does not, and in the two wing back cells 10
 * and 17, which demand a fullback, it is a defensive FULLBACK who collects
 * it. The old name for the pair was the volante bonus, which is an
 * approximation of the typical position rather than the condition; see
 * OPEN-QUESTIONS item 60.
 *
 * firstTraitBonus is paid only on the winning side of the possession
 * comparison, and only when the FIRST of the player's two characteristics is
 * Passe or Armacao. The second characteristic earns nothing at all here, so a
 * player with Armacao second is worth no more than one with neither.
 */
@SpecRef("3.14")
data class MidfieldRatingRules(
    @property:SpecRef("3.14") val slots: IntRange,
    @property:SpecRef("3.14") val moreBonus: Double,
    @property:SpecRef("3.14") val moreReducedBonus: Double,
    @property:SpecRef("3.14") val holdingMoreBonus: Double,
    @property:SpecRef("3.14") val firstTraitBonus: Double,
    @property:SpecRef("3.14") val lessPenalty: Double,
    @property:SpecRef("3.14") val lessReducedPenalty: Double,
    @property:SpecRef("3.14") val holdingLessPenalty: Double,
)

/**
 * Section 3.14 step 3, the six terms that read the player's own counters.
 *
 * Every one of them multiplies a counter rather than firing once, so a player
 * who scored twice collects goalBonus twice.
 *
 * missedPenaltyCharge is the one figure of the six that does not name the
 * counter it multiplies, because which counter that is depends on the rule
 * set. It is section 3.15 item 15, and RuleSet.missedPenaltyRule is what
 * settles it: the classic reading switches the charge on by the player having
 * missed at least one interactive penalty and then multiplies his own goals,
 * so it is nought for everybody except the player who, in one match, missed a
 * penalty and scored an own goal, and that player is punished twice over for
 * the own goal; the modern reading multiplies the penalties he missed. The
 * charge itself is the same minus one point two either way, which is why it
 * stays a plain constant here and only what it multiplies is a strategy. See
 * OPEN-QUESTIONS item 54.
 */
@SpecRef("3.14")
data class PlayerEventRatingRules(
    @property:SpecRef("3.15") val goalBonus: Double,
    @property:SpecRef("3.14") val ownGoalPenalty: Double,
    @property:SpecRef("3.15") val missedPenaltyCharge: Double,
    @property:SpecRef("3.14") val yellowCardPenalty: Double,
    @property:SpecRef("3.14") val redCardPenalty: Double,
    @property:SpecRef("3.14") val assistBonus: Double,
)

/**
 * Section 3.14 step 4, read for a player in a defensive cell and only when
 * the two sides' tackle counters differ.
 *
 * moreBonus and moreRaisedBonus are one draw and not two terms: the side with
 * the better tackle count pays moreBonus, or the larger moreRaisedBonus
 * instead on a one in three chance. The chance pays MORE here and pays LESS
 * in MidfieldRatingRules above, which is not a slip in either place.
 *
 * rewardSlots and punishSlots are deliberately different bands. The reward
 * runs from the first fullback cell to the last, laterais included, and the
 * punishment covers the centre backs alone, so a lateral is paid for a side
 * that wins the tackle count and charged nothing for a side that loses it.
 * holdingSlots is the same band on both sides of the comparison and is a
 * third band again, which is why all three are separate fields rather than
 * one reused twice.
 *
 * rewardBonus, holdingBonus, punishPenalty and holdingPenalty each fire on a
 * chance of their own rather than automatically. The two rewards are one in
 * three and the two punishments are one in four, so the bands are not the
 * only asymmetry in this step.
 */
@SpecRef("3.14")
data class DefensiveRatingRules(
    @property:SpecRef("3.14") val slots: IntRange,
    @property:SpecRef("3.14") val moreBonus: Double,
    @property:SpecRef("3.14") val moreRaisedBonus: Double,
    @property:SpecRef("3.14") val rewardSlots: IntRange,
    @property:SpecRef("3.14") val rewardBonus: Double,
    @property:SpecRef("3.14") val holdingSlots: IntRange,
    @property:SpecRef("3.14") val holdingBonus: Double,
    @property:SpecRef("3.14") val lessPenalty: Double,
    @property:SpecRef("3.14") val punishSlots: IntRange,
    @property:SpecRef("3.14") val punishPenalty: Double,
    @property:SpecRef("3.14") val holdingPenalty: Double,
)

/**
 * Section 3.14 step 6, the clause only the man in the goalkeeper's cell
 * reaches.
 *
 * Every figure here but one reads a counter section 3.14 keeps for a
 * goalkeeper alone, so a keeper substituted at the interval carries only the
 * share of the match he actually kept.
 *
 * busyShotsAbove is the exception, and it reads the match instead. Section
 * 3.14 switches wording inside its own sentence, from "chute no alvo sofrido"
 * for the per shot bonus above to "se o adversario chutou" for this one, and
 * section 3.15 item 16 names the two tiers stacked above it "chutes sofridos"
 * rather than chutes no alvo. Two places in the spec therefore say the same
 * thing: this tier counts every shot the opposing side took and not only the
 * ones that were on target. The difference is systematic rather than
 * marginal, since section 3.16 puts a side at about thirteen to sixteen shots
 * a match and well under eleven of those on target, so read one way nearly
 * every goalkeeper in the game collects this and read the other way almost
 * none do.
 *
 * One consequence follows from reading the match rather than the keeper: a
 * goalkeeper who came on at the interval is paid on the whole match's shots
 * even though every other figure here counts only his own half. That is what
 * the spec says and it is not softened here.
 *
 * It is a strict lower bound: the bonus is paid for more than that many shots
 * and not for exactly that many. The original has two further tiers above it,
 * at more than fifteen and more than twenty, sitting behind this one in an
 * else chain that can never reach them. They are section 3.15 item 16 and are
 * not ported at all, so there is no field for either and no rule set can
 * switch them on.
 *
 * conceded is written as an else chain rather than as a partition, with each
 * band running to the top of the range and the first match winning, which is
 * exactly how the original's chain resolves. Reading it that way is what
 * makes three goals conceded cost the same as two: the chain tests five, then
 * four, then two, and there is no rung for three at all.
 *
 * noShotsFacedPenalty is charged to a keeper who faced nothing on target, on
 * top of the clean sheet reward the conceded chain has already paid him. A
 * keeper with nothing to do therefore ends step 6 half a point down rather
 * than up.
 */
@SpecRef("3.14")
data class KeeperRatingRules(
    @property:SpecRef("3.14") val basePenalty: Double,
    @property:SpecRef("3.14") val shotOnTargetBonus: Double,
    @property:SpecRef("3.10") val savedPenaltyBonus: Double,
    @property:SpecRef("3.14") val busyShotsAbove: Int,
    @property:SpecRef("3.14") val busyBonus: Double,
    @property:SpecRef("3.14") val conceded: List<Band<Double>>,
    @property:SpecRef("3.14") val noShotsFacedPenalty: Double,
)

/**
 * Section 3.14 step 7, read for a player in a defensive cell and paid or
 * charged on the scoreline rather than on anything he did.
 *
 * slots repeats step 4's band as a field of its own for the reason
 * MidfieldRatingRules.slots does: two clauses that read the same range today
 * are still two clauses.
 *
 * concededFrom is where the goals against penalty starts biting, and it
 * starts at two. A side that conceded exactly one goal pays nothing here at
 * all, which is a genuine step in the original and not a rounding of the per
 * goal rate.
 *
 * taxPenalty is the strangest number in the whole section. It is charged on a
 * one in three chance to every cell in taxedSlots, unconditionally: not on
 * the scoreline, not on the tackle count, not on anything the player or his
 * side did. Every defender and every holding midfielder in the game pays it
 * in one match out of three.
 */
@SpecRef("3.14")
data class CleanSheetRatingRules(
    @property:SpecRef("3.14") val slots: IntRange,
    @property:SpecRef("3.14") val bonus: Double,
    @property:SpecRef("3.14") val rewardSlots: IntRange,
    @property:SpecRef("3.14") val rewardBonus: Double,
    @property:SpecRef("3.14") val holdingSlots: IntRange,
    @property:SpecRef("3.14") val holdingBonus: Double,
    @property:SpecRef("3.14") val concededFrom: Int,
    @property:SpecRef("3.14") val concededPenaltyPerGoal: Double,
    @property:SpecRef("3.14") val taxedSlots: IntRange,
    @property:SpecRef("3.14") val taxPenalty: Double,
)

/**
 * Section 3.14 steps 9 to 11, the four clamps that close the rating, in the
 * order they are applied.
 *
 * The order is the whole content of these three steps and none of the four
 * commutes with the others. cap comes first, then negativeReplacement, then
 * the minutes penalty, then floor. A rating that would have exceeded ten is
 * cut to ten before a later term can take it back down; a rating that came
 * out negative becomes negativeReplacement, which is one and not nought and
 * not the floor of two; and only then does the minutes penalty run, so a
 * player patched up to one and then charged the short appearance penalty ends
 * below nought again and is rescued a second time by floor rather than by the
 * patch.
 *
 * shortMinutes and partialMinutes are the two rungs of step 10, tested in
 * that order and exclusive: under shortMinutes costs shortPenalty and nothing
 * else, and only a player at or above it and under partialMinutes pays
 * partialPenalty.
 *
 * noRatingMinutes and noRating are step 11's tail. A player who played fewer
 * than noRatingMinutes and whose rating came to rest exactly on floor is
 * given noRating, which the original means as "no rating at all" rather than
 * as a very bad one. It is a genuine nought and is not the same thing as a
 * substitute who never came on, who is not rated at all and appears in no
 * result of section 3.14.
 *
 * negativeReplacement cannot be observed in the published mark under the
 * classic figures, and that is a fact about those figures rather than about
 * the rule. It is one and the floor is two, and nothing between step 9 and
 * step 11 can raise a rating, so every value the patch can produce is lifted
 * to the floor immediately afterwards and a patch to nought, to one or to two
 * publishes the same figure. A rule set that lowered floor below
 * negativeReplacement would expose the difference at once, which is why the
 * field is carried honestly rather than folded into the floor.
 */
@SpecRef("3.14")
data class RatingLimits(
    @property:SpecRef("3.14") val cap: Double,
    @property:SpecRef("3.14") val negativeReplacement: Double,
    @property:SpecRef("3.14") val shortMinutes: Int,
    @property:SpecRef("3.14") val shortPenalty: Double,
    @property:SpecRef("3.14") val partialMinutes: Int,
    @property:SpecRef("3.14") val partialPenalty: Double,
    @property:SpecRef("3.14") val floor: Double,
    @property:SpecRef("3.14") val noRatingMinutes: Int,
    @property:SpecRef("3.14") val noRating: Double,
)

/**
 * Every number section 3.14's post match rating reads, gathered in one value
 * object for the reason AssistRules, GoalTypeRules and PenaltyRules already
 * are: RuleSet is well past its own guidance of about sixty flat properties,
 * and a divergence here should be one named argument either way.
 *
 * base is read by the player's own strength, with pick and never with bound,
 * the same way InjuryRules.ageTerms is read: it is a bracket table and not a
 * draw table, and its last bracket reaches Int.MAX_VALUE, so asking it for a
 * draw bound would overflow.
 *
 * defaultSlotByPosition is indexed by Position.ordinal and is the cell a
 * player is treated as standing in when his own cell is nought or less. The
 * five figures are one keeper cell, one lateral cell, one centre back cell,
 * one midfield cell and one forward cell, and the original writes the chosen
 * value back onto the player, so it is a lasting change and not a local
 * substitution.
 *
 * thirdChanceIn and quarterChanceIn are the denominators of every chance in
 * the section: one in three for the possession pair of step 2, for the tackle
 * pair and the two rewards of step 4, and for both of step 7's chances, and
 * one in four for step 4's two punishments. They are two fields rather than
 * eight because the spec writes one in three six times and one in four twice,
 * and a rule set that wanted to soften one of them would want to soften all
 * of its siblings with it.
 *
 * savedShotBonus is step 5 and is paid to every rated player, not only to a
 * forward. It multiplies the shooter's own count of shots the opposing keeper
 * SAVED, which is not the same counter as the side's shots on target: a goal
 * pays nothing here and a shot that missed pays nothing either. See
 * OPEN-QUESTIONS item 52.
 *
 * starBonus and topWorldBonus are two independent sums and not a chain, so a
 * player carrying both badges is a full point up before any clamp. That is
 * the same reading section 4.9's market value and section 4.8's salary
 * already take of the same table, where the red star multiplier is applied on
 * top of the plain star one rather than instead of it.
 *
 * The two are read as two flags rather than one derived from the other,
 * because section 4.10's implication holds only on the way in. A promotion to
 * red star at the end of a season lights the red flag alone, so a promoted
 * player is worth nought point six a match where a player who arrived from a
 * squad file is worth a full point; that is section 3.15 item 18 and
 * OPEN-QUESTIONS item 62, and it lives in season turnover rather than here.
 */
@SpecRef("3.14")
data class RatingRules(
    @property:SpecRef("3.14") val base: List<Band<RatingBase>>,
    @property:SpecRef("3.14") val defaultSlotByPosition: List<Int>,
    @property:SpecRef("3.14") val thirdChanceIn: Int,
    @property:SpecRef("3.14") val quarterChanceIn: Int,
    @property:SpecRef("3.14") val outOfPositionPenalty: Double,
    @property:SpecRef("3.14") val outOfPositionInGoalPenalty: Double,
    @property:SpecRef("3.14") val midfield: MidfieldRatingRules,
    @property:SpecRef("3.14") val events: PlayerEventRatingRules,
    @property:SpecRef("3.14") val defending: DefensiveRatingRules,
    @property:SpecRef("3.14") val savedShotBonus: Double,
    @property:SpecRef("3.14") val keeper: KeeperRatingRules,
    @property:SpecRef("3.14") val cleanSheet: CleanSheetRatingRules,
    @property:SpecRef("4.10") val starBonus: Double,
    @property:SpecRef("4.10") val topWorldBonus: Double,
    @property:SpecRef("3.14") val limits: RatingLimits,
) {
    /**
     * The cell a player with no cell of his own is treated as standing in.
     *
     * Read by ordinal for the same reason RuleSet.markingBonus is: the table
     * stays data instead of becoming a when chain that a new position would
     * have to be remembered in.
     */
    @SpecRef("3.14")
    fun defaultSlotFor(position: Position): Int = defaultSlotByPosition[position.ordinal]
}
