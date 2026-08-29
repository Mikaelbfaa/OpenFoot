package org.openfoot.engine.match

import org.openfoot.engine.lineup.Formations
import org.openfoot.model.Attr
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Designated
import org.openfoot.model.Marking
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.Side
import org.openfoot.model.Slot
import org.openfoot.model.Trait

/**
 * Fixtures for the match engine tests.
 *
 * Individual abilities are off by default, so a player's rating is exactly his
 * strength divided by ten and every expected value in a test can be worked out
 * by hand. Default characteristics are ones that carry no shooter bonus, so a
 * test that cares about bonuses has to ask for them.
 */
object Lineups {

    val NEUTRAL_TRAITS = Trait.STAMINA to Trait.CROSSING

    fun context(
        kind: CompetitionKind = CompetitionKind.FRIENDLY,
        individualAbilities: Boolean = false,
        reputation: Int = 5,
        country: Int = 65,
        continent: Int = Country.EUROPE_CONTINENT,
        isHome: Boolean = true,
        homeReputation: Int = 5,
        awayReputation: Int = 5,
    ) = StrengthContext(
        kind = kind,
        useIndividualAbilities = individualAbilities,
        sideReputation = reputation,
        sideCountry = country,
        sideContinent = continent,
        isHomeSide = isHome,
        homeReputation = homeReputation,
        awayReputation = awayReputation,
    )

    /**
     * A player who is in position for his cell unless a position is forced.
     *
     * side and style default to Side.RIGHT and PlayerStyle.OFFENSIVE, plain
     * fixed values rather than anything derived from the slot or the position,
     * because MatchPlayer's own constructor takes neither as a default and a
     * fixture that quietly picked one for a caller would hide the same gap the
     * production type is deliberately built to expose. A test that exercises
     * the section 5.4 fit search, rather than its no-fit-found catch-all, must
     * state both explicitly.
     */
    fun player(
        slot: Int,
        strength: Int,
        id: Int = slot,
        age: Int = 25,
        position: Position? = null,
        side: Side = Side.RIGHT,
        style: PlayerStyle = PlayerStyle.OFFENSIVE,
        firstTrait: Trait = NEUTRAL_TRAITS.first,
        secondTrait: Trait = NEUTRAL_TRAITS.second,
        star: Boolean = false,
        topWorld: Boolean = false,
        abilities: IntArray = IntArray(Attr.COUNT),
        representsSideCountry: Boolean = false,
    ): MatchPlayer {
        val cell = Slot(slot)
        return MatchPlayer(
            id = PlayerId(id),
            slot = cell,
            naturalPosition = position ?: cell.requiredPosition ?: Position.MIDFIELDER,
            age = age,
            strength = strength,
            abilities = abilities,
            firstTrait = firstTrait,
            secondTrait = secondTrait,
            star = star,
            topWorld = topWorld,
            side = side,
            style = style,
            representsSideCountry = representsSideCountry,
        )
    }

    fun side(
        players: List<MatchPlayer>,
        marking: Marking = Marking.LIGHT,
        context: StrengthContext = context(),
        humanManaged: Boolean = false,
        designated: Designated = Designated.NONE,
    ) = MatchSide(
        lineup = players,
        marking = marking,
        context = context,
        designated = designated,
        isHumanManaged = humanManaged,
    )

    /** Builds one player per slot, all at the same strength, in list order. */
    fun sideOfSlots(
        slots: List<Int>,
        strength: Int,
        marking: Marking = Marking.LIGHT,
        context: StrengthContext = context(),
        designated: Designated = Designated.NONE,
    ) = side(slots.map { player(it, strength) }, marking, context, designated = designated)

    /** Slot list of formation 4, the four four two the AI picks most often. */
    val FORMATION_4_4_2 = Formations.byId(4).slots.map { it.value }

    /** Slot list of formation 10, the three four three that uses slot eighteen. */
    val FORMATION_3_4_3 = Formations.byId(10).slots.map { it.value }
}
