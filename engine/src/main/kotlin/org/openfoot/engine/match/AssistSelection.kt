package org.openfoot.engine.match

import org.openfoot.model.AssistRules
import org.openfoot.model.Marking
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait
import org.openfoot.model.rand

/**
 * Draws who is credited with the assist, for a goal the caller has already
 * decided to treat as one that can carry one.
 *
 * Section 3.7 says the assist is drawn only for open play goals, and before
 * the patches of that section's items 2 and 3 that can turn another type into
 * open play, so a goal that only becomes open play because of a patch never
 * gets an assister. None of that ordering lives here: this function knows
 * nothing about goal types or patches, and it is up to whoever calls it, the
 * goal type resolution of section 3.7, to call it only when it is supposed
 * to and never to call it twice for the same goal.
 *
 * The finisher is excluded from the draw by identity, not by slot or natural
 * position, because he is the only exclusion the spec names: everybody else
 * fielded is eligible, keeper included.
 *
 * The spec never says what happens when excluding the finisher leaves no
 * candidate at all, a case that in practice needs a side reduced to the
 * finisher alone. Rather than let asymmetricWeightedPick throw on an empty
 * list and take a match down over an event the spec already treats as
 * ordinary, that case is read as no assister, the same outcome the coin
 * above already produces nineteen per cent of the time.
 */
@SpecRef("3.6")
fun selectAssister(side: MatchSide, finisher: MatchPlayer, rules: RuleSet, rng: Rng): MatchPlayer? {
    if (noAssistDraw(rng, rules)) {
        return null
    }
    val candidates = assistCandidates(side, finisher, rules)
    if (candidates.isEmpty()) {
        return null
    }
    return pickAssister(candidates, side, rules, rng)
}

/**
 * The coin that decides whether this goal gets an assister at all.
 *
 * rand(100) > 80 is the draws 81 to 99, nineteen of the hundred equally
 * likely outcomes, so nineteen per cent of open play goals have no assister
 * and the other eighty one per cent, draws 0 to 80 inclusive, go on to the
 * weighted draw below. This is the only draw this whole selection makes when
 * the coin fires; nothing else is read from rng in that case.
 */
@SpecRef("3.6")
internal fun noAssistDraw(rng: Rng, rules: RuleSet): Boolean =
    rng.rand(100) > rules.assist.noAssistThreshold

/**
 * Every fielded player who could be credited, in lineup order.
 *
 * The only exclusion is the finisher himself, checked by identity rather
 * than by slot, so the keeper is eligible whenever he is not the one who
 * shot, at the weight of one the table gives slot one. A cell outside one to
 * twenty five, meaning an empty slot or a bench cell, is never eligible: the
 * spec's weight table has nothing to say about a player who was not on the
 * pitch for the goal.
 */
@SpecRef("3.6")
internal fun assistCandidates(side: MatchSide, finisher: MatchPlayer, rules: RuleSet): List<MatchPlayer> =
    side.lineup.filter { it.slot.value in rules.assist.eligibleSlots && it !== finisher }

/**
 * Walks an already filtered, already known non empty candidate list with the
 * asymmetric draw of section 3.15 item 4.
 *
 * The two weight functions handed to asymmetricWeightedPick differ in
 * exactly one place, the Pace branch of assistWeight below, which is the
 * whole content of the Velocidade defect: everything else this function
 * reads is identical whether it is summing the total or walking for the
 * winner.
 */
@SpecRef("3.6")
internal fun pickAssister(candidates: List<MatchPlayer>, side: MatchSide, rules: RuleSet, rng: Rng): MatchPlayer =
    asymmetricWeightedPick(
        candidates,
        totalWeight = { assistWeight(it, side, rules, walk = false) },
        walkWeight = { assistWeight(it, side, rules, walk = true) },
        rng = rng,
    )

/**
 * One candidate's weight in the assist draw, either the total pass or the
 * walk pass depending on walk.
 *
 * Three layers are added together: the cell weight straight off the slot
 * table, a characteristic bonus that picks the first matching branch of the
 * chain documented on AssistRules and stops there, and a flat bonus for
 * either of the two lateral cells when the side marks Pesada. The marking
 * bonus is the same in both passes; only the characteristic bonus can differ
 * between them, and only through the Pace branch.
 */
@SpecRef("3.6")
internal fun assistWeight(player: MatchPlayer, side: MatchSide, rules: RuleSet, walk: Boolean): Double {
    val assist = rules.assist
    val slotWeight = assist.slotWeights.getOrElse(player.slot.value) { 0 }
    val isFullback = player.slot.value in assist.fullbackSlots
    val characteristicBonus = assistCharacteristicBonus(player, assist, isFullback, walk)
    val markingBonus = if (isFullback && side.marking == Marking.HEAVY) {
        assist.heavyMarkingFullbackBonus
    } else {
        0
    }
    return (slotWeight + characteristicBonus + markingBonus).toDouble()
}

/**
 * The characteristic chain of section 3.6, stopping at its first match.
 *
 * Passing is checked before Playmaking, Playmaking before Dribbling,
 * Dribbling before Pace, Pace before Crossing, and a player who matches none
 * of the five carries no characteristic bonus at all. Each branch after the
 * first two asks about the player's first characteristic specifically, not
 * either of his two, for its own extra bonus: a player whose second
 * characteristic would have qualified does not get it.
 */
@SpecRef("3.6")
private fun assistCharacteristicBonus(
    player: MatchPlayer,
    assist: AssistRules,
    isFullback: Boolean,
    walk: Boolean,
): Int = when {
    player.hasTrait(Trait.PASSING) -> {
        var bonus = assist.passingBonus
        if (player.hasTrait(Trait.PLAYMAKING)) {
            bonus += assist.passingPlaymakingBonus
        }
        bonus
    }
    player.hasTrait(Trait.PLAYMAKING) -> {
        var bonus = assist.playmakingBonus
        if (player.firstTrait == Trait.DRIBBLING) {
            bonus += assist.playmakingDribblingBonus
        }
        bonus
    }
    player.hasTrait(Trait.DRIBBLING) -> {
        var bonus = assist.dribblingBonus
        if (player.firstTrait == Trait.PACE) {
            bonus += assist.dribblingPaceBonus
        }
        bonus
    }
    player.hasTrait(Trait.PACE) -> {
        var bonus = if (walk) assist.paceWalkBonus else assist.paceTotalBonus
        if (isFullback) {
            bonus += assist.paceFullbackBonus
        }
        bonus
    }
    player.hasTrait(Trait.CROSSING) -> {
        var bonus = assist.crossingBonus
        if (isFullback) {
            bonus += assist.crossingFullbackBonus
        }
        bonus
    }
    else -> 0
}
