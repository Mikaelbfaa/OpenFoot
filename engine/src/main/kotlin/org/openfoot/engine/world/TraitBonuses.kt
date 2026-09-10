package org.openfoot.engine.world

import org.openfoot.model.Attr
import org.openfoot.model.Position
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait
import org.openfoot.model.rand

/**
 * Adds what a player's two characteristics are worth on top of his generated
 * abilities, in place.
 *
 * A bonus fires when either characteristic matches, so carrying the same one
 * twice is worth exactly as much as carrying it once. That is the spec being
 * explicit about a case the data does contain.
 *
 * Bonuses are applied in the order the spec paragraph lists them, which fixes
 * the draw order. The outfield list applies to outfield players and the
 * goalkeeper list to goalkeepers, chosen by position rather than by which
 * characteristics happen to be carried: section 4.12's synthetic players are
 * generated with both characteristics still at index zero, and the spec is
 * explicit that this earns a keeper the positioning bonus and an outfielder
 * nothing at all, which is only true if the position picks the list.
 *
 * The goalkeeper list reads the two characteristics separately, unlike the
 * outfield one where either matching fires the bonus once: a keeper's first
 * characteristic is worth a larger draw than the second, and a keeper with
 * positioning and rushing out collects both technique terms.
 *
 * The ceiling of section 4.1 is applied last. Nothing world creation can
 * produce currently reaches it, since a player carries at most two
 * characteristics and starts well short, but the ceiling is the rule and
 * growth will bring players to it later.
 */
@SpecRef("4.2")
fun applyTraitBonuses(
    abilities: IntArray,
    position: Position,
    firstTrait: Trait,
    secondTrait: Trait,
    qualitySeed: Int,
    band: Int,
    rng: Rng,
) {
    require(abilities.size == Attr.COUNT) {
        "expected ${Attr.COUNT} abilities, got ${abilities.size}"
    }

    if (position == Position.GOALKEEPER) {
        applyGoalkeeperBonuses(abilities, firstTrait, secondTrait, rng)
    } else {
        applyOutfieldBonuses(abilities, position, firstTrait, secondTrait, qualitySeed, band, rng)
    }

    for (index in abilities.indices) {
        abilities[index] = minOf(abilities[index], ABILITY_CEILING)
    }
}

/**
 * The goalkeeper list of section 4.2, in the order the spec writes it: the
 * two positioning characteristics, then reflexes, then penalty saving, each
 * tested on the first characteristic and then on the second.
 */
@SpecRef("4.2")
private fun applyGoalkeeperBonuses(
    abilities: IntArray,
    firstTrait: Trait,
    secondTrait: Trait,
    rng: Rng,
) {
    fun positional(trait: Trait) = trait == Trait.POSITIONING || trait == Trait.RUSHING_OUT

    if (positional(firstTrait)) {
        abilities[Attr.TECHNIQUE] += 2 + rng.rand(5)
    }
    if (positional(secondTrait)) {
        abilities[Attr.TECHNIQUE] += rng.rand(2)
    }
    if (firstTrait == Trait.REFLEXES) {
        abilities[Attr.PACE] += 2 + rng.rand(5)
    }
    if (secondTrait == Trait.REFLEXES) {
        abilities[Attr.PACE] += rng.rand(2)
    }
    if (firstTrait == Trait.PENALTY_SAVING) {
        abilities[Attr.GOALKEEPING] += 1 + rng.rand(3)
    }
    if (secondTrait == Trait.PENALTY_SAVING) {
        abilities[Attr.GOALKEEPING] += rng.rand(2)
    }
}

/**
 * The outfield list of section 4.2. A bonus fires when either characteristic
 * matches, so carrying the same one twice is worth exactly as much as
 * carrying it once, which the spec states for a case the data does contain.
 */
@SpecRef("4.2")
private fun applyOutfieldBonuses(
    abilities: IntArray,
    position: Position,
    firstTrait: Trait,
    secondTrait: Trait,
    qualitySeed: Int,
    band: Int,
    rng: Rng,
) {
    fun has(trait: Trait) = firstTrait == trait || secondTrait == trait

    /**
     * Forwards read the club quality seed where everyone else reads the band,
     * but only for the two creative characteristics.
     */
    val creativeBase = if (position == Position.FORWARD) qualitySeed else band

    if (has(Trait.PLAYMAKING)) {
        abilities[Attr.PLAYMAKING] += creativeBase + rng.rand(5)
        abilities[Attr.PASSING] += creativeBase + rng.rand(5)
    }
    if (has(Trait.HEADING)) {
        abilities[Attr.FINISHING] += 2 + rng.rand(3)
    }
    if (has(Trait.CROSSING)) {
        abilities[Attr.PASSING] += 2 + rng.rand(3)
    }
    if (has(Trait.TACKLING)) {
        abilities[Attr.TACKLING] += band + rng.rand(3)
    }
    if (has(Trait.DRIBBLING)) {
        abilities[Attr.TECHNIQUE] += band + rng.rand(3)
    }
    if (has(Trait.FINISHING)) {
        abilities[Attr.FINISHING] += band + rng.rand(3)
    }
    if (has(Trait.MARKING)) {
        abilities[Attr.TACKLING] += band + rng.rand(5)
    }
    if (has(Trait.PASSING)) {
        abilities[Attr.PASSING] += creativeBase + rng.rand(2)
    }
    if (has(Trait.STAMINA)) {
        abilities[Attr.TACKLING] += 3 + rng.rand(3)
    }
    if (has(Trait.PACE)) {
        abilities[Attr.PACE] += qualitySeed + rng.rand(3)
    }
}

/** Abilities run zero to one hundred. */
@SpecRef("4.1")
internal const val ABILITY_CEILING = 100
