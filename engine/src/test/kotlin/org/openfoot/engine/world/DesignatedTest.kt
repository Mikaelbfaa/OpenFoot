package org.openfoot.engine.world

import org.openfoot.engine.lineup.Availability
import org.openfoot.engine.lineup.Formations
import org.openfoot.engine.lineup.fillEleven
import org.openfoot.model.PlayerId
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Side
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Section 5.6's designated free kick and penalty taker, derived from a whole
 * professional squad rather than from any single match.
 *
 * Every fixture below is built so the expected winner is neither the first
 * entry of the squad list nor the strongest player in it. Either coincidence
 * would let a fixture pass under "take the first" or "take the strongest"
 * instead of the rule actually under test, so every squad here carries a
 * stronger or earlier-listed player who fails the rule for a specific reason
 * the test names.
 */
class DesignatedTest {

    private fun player(
        position: Position = Position.MIDFIELDER,
        strength: Int = 50,
        starter: Boolean = false,
        firstTrait: Trait = Trait.PASSING,
        secondTrait: Trait = Trait.STAMINA,
    ) = Player(
        name = "Jogador",
        age = 25,
        country = 3,
        position = position,
        side = Side.RIGHT,
        firstTrait = firstTrait,
        secondTrait = secondTrait,
        starter = starter,
        star = false,
        topWorld = false,
        talent = 5,
        style = playerStyle(position, firstTrait, secondTrait),
        strength = strength,
        abilities = List(7) { strength },
        contractDays = 700,
        salary = 1000L,
        marketValue = 100_000L,
    )

    @Test
    fun `the first titular whose first characteristic is Finalizacao wins over a stronger non titular`() {
        val squad = listOf(
            player(position = Position.FORWARD, strength = 90, starter = false, firstTrait = Trait.FINISHING),
            player(position = Position.FORWARD, strength = 55, starter = true, firstTrait = Trait.FINISHING),
        )

        val result = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD)

        assertEquals(
            PlayerId(1),
            result.taker,
            "the stronger player has the right trait but is not titular by the data flag, so the " +
                "weaker titular is the only branch one candidate",
        )
    }

    @Test
    fun `Finalizacao as a second characteristic does not qualify a player for branch one`() {
        val squad = listOf(
            player(
                position = Position.MIDFIELDER,
                strength = 90,
                starter = true,
                firstTrait = Trait.PASSING,
                secondTrait = Trait.FINISHING,
            ),
            player(position = Position.FORWARD, strength = 50, starter = true, firstTrait = Trait.FINISHING),
        )

        val result = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD)

        assertEquals(
            PlayerId(1),
            result.taker,
            "the stronger titular carries Finalizacao only as his second characteristic, which does " +
                "not count, so the weaker titular with it first is the only branch one candidate",
        )
    }

    @Test
    fun `branch two picks the strongest titular non keeper when nobody qualifies for branch one`() {
        val squad = listOf(
            player(position = Position.FORWARD, strength = 95, starter = false, firstTrait = Trait.FINISHING),
            player(position = Position.MIDFIELDER, strength = 80, starter = true, firstTrait = Trait.PASSING),
            player(position = Position.CENTREBACK, strength = 60, starter = true, firstTrait = Trait.TACKLING),
        )

        val result = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD)

        assertEquals(
            PlayerId(1),
            result.taker,
            "the strongest player has the right trait but is not titular, so branch one finds " +
                "nobody; branch two then takes the stronger of the two titular non keepers",
        )
    }

    @Test
    fun `branch three picks the first non keeper ignoring the titular flag when no titular non keeper exists`() {
        val squad = listOf(
            player(position = Position.GOALKEEPER, strength = 95, starter = true, firstTrait = Trait.REFLEXES),
            player(position = Position.FORWARD, strength = 40, starter = false, firstTrait = Trait.TACKLING),
            player(position = Position.MIDFIELDER, strength = 60, starter = false, firstTrait = Trait.PASSING),
        )

        val result = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD)

        assertEquals(
            PlayerId(2),
            result.taker,
            "the only titular player in the squad is a natural keeper, so branches one and two find " +
                "nobody; branch three then ignores the titular flag and takes the strongest non " +
                "keeper of the two left",
        )
    }

    @Test
    fun `a squad of nothing but goalkeepers leaves the taker unset`() {
        val squad = listOf(
            player(position = Position.GOALKEEPER, strength = 80, starter = true, firstTrait = Trait.REFLEXES),
            player(position = Position.GOALKEEPER, strength = 60, starter = false, firstTrait = Trait.REFLEXES),
        )

        val result = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD)

        assertNull(result.taker, "no branch of section 5.6 ever hands the ball to a keeper")
    }

    @Test
    fun `energy breaks a strength tie and list order decides nothing`() {
        val squad = listOf(
            player(position = Position.FORWARD, strength = 70, starter = true, firstTrait = Trait.FINISHING),
            player(position = Position.FORWARD, strength = 70, starter = true, firstTrait = Trait.FINISHING),
        )
        val energy = DesignationEnergy { index, _ -> if (index == 0) 40 else 90 }

        val result = deriveDesignated(squad, energy)

        assertEquals(
            PlayerId(1),
            result.taker,
            "both players tie on strength and on every branch one condition, so only the fresher " +
                "one, who is listed second, should win",
        )
    }

    @Test
    fun `a titular by the data flag still wins the designation even when the matchday eleven leaves him out`() {
        val strongFillers = listOf(
            player(position = Position.GOALKEEPER, strength = 90, starter = false, firstTrait = Trait.REFLEXES),
            player(position = Position.CENTREBACK, strength = 90, starter = false, firstTrait = Trait.MARKING),
            player(position = Position.CENTREBACK, strength = 90, starter = false, firstTrait = Trait.MARKING),
            player(position = Position.FULLBACK, strength = 90, starter = false, firstTrait = Trait.CROSSING),
            player(position = Position.FULLBACK, strength = 90, starter = false, firstTrait = Trait.CROSSING),
            player(position = Position.MIDFIELDER, strength = 90, starter = false, firstTrait = Trait.PASSING),
            player(position = Position.MIDFIELDER, strength = 90, starter = false, firstTrait = Trait.PASSING),
            player(position = Position.MIDFIELDER, strength = 90, starter = false, firstTrait = Trait.PASSING),
            player(position = Position.MIDFIELDER, strength = 90, starter = false, firstTrait = Trait.PASSING),
            player(position = Position.FORWARD, strength = 90, starter = false, firstTrait = Trait.HEADING),
            player(position = Position.FORWARD, strength = 90, starter = false, firstTrait = Trait.HEADING),
        )
        val benchedTitular =
            player(position = Position.FORWARD, strength = 20, starter = true, firstTrait = Trait.FINISHING)
        val squad = strongFillers + benchedTitular
        val benchedId = PlayerId(strongFillers.size)

        val eleven = fillEleven(squad, Formations.byId(4), RuleSets.CLASSIC, Availability.FULL_SQUAD)
        assertTrue(
            eleven.none { it.id == benchedId },
            "eleven stronger players fill every cell of the formation ahead of him",
        )

        val result = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD)

        assertEquals(
            benchedId,
            result.taker,
            "the pool for section 5.6 is the whole squad, not the matchday eleven, so being left out " +
                "of the lineup does not cost him the designation",
        )
    }

    @Test
    fun `the corner taker is never filled for a squad generated for the AI`() {
        val squad = listOf(
            player(position = Position.FORWARD, strength = 70, starter = true, firstTrait = Trait.FINISHING),
        )

        val result = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD)

        assertNull(
            result.cornerTaker,
            "section 5.6 says the AI never fills the corner taker; only a human manager does, " +
                "through a path this engine does not expose yet",
        )
    }
}
