package org.openfoot.engine.world

import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Section 4.3 is a chain of ordered tests rather than a table, and each test
 * reads a named characteristic, so these pin the order and the reach of every
 * test as much as the outcomes. The interesting cases are the ones where the
 * two characteristics pull in different directions, and the ones where a
 * characteristic sits in the position the original never reads.
 */
class PlayerStyleTest {

    private fun style(position: Position, first: Trait, second: Trait = Trait.STAMINA) =
        playerStyle(position, first, second)

    @Test
    fun `goalkeepers and centrebacks have no style to choose`() {
        for (trait in listOf(Trait.PACE, Trait.DRIBBLING, Trait.MARKING)) {
            assertEquals(
                PlayerStyle.DEFENSIVE,
                style(Position.CENTREBACK, trait),
                "centreback with $trait",
            )
        }
        assertEquals(PlayerStyle.DEFENSIVE, style(Position.GOALKEEPER, Trait.REFLEXES, Trait.POSITIONING))
    }

    @Test
    fun `a fullback with pace or crossing first is offensive`() {
        assertEquals(PlayerStyle.OFFENSIVE, style(Position.FULLBACK, Trait.PACE))
        assertEquals(PlayerStyle.OFFENSIVE, style(Position.FULLBACK, Trait.CROSSING))
    }

    @Test
    fun `a fullback with tackling or marking first is defensive whatever comes second`() {
        assertEquals(PlayerStyle.DEFENSIVE, style(Position.FULLBACK, Trait.TACKLING))
        assertEquals(PlayerStyle.DEFENSIVE, playerStyle(Position.FULLBACK, Trait.MARKING, Trait.PACE))
        assertEquals(PlayerStyle.DEFENSIVE, playerStyle(Position.FULLBACK, Trait.MARKING, Trait.CROSSING))
    }

    @Test
    fun `a fullback's second characteristic is read for pace but never for crossing`() {
        // The original's crossing test of the second characteristic reads the
        // first one again, so crossing second alone never makes a fullback
        // offensive, while pace second does.
        assertEquals(PlayerStyle.OFFENSIVE, playerStyle(Position.FULLBACK, Trait.STAMINA, Trait.PACE))
        assertEquals(PlayerStyle.DEFENSIVE, playerStyle(Position.FULLBACK, Trait.STAMINA, Trait.CROSSING))
        assertEquals(PlayerStyle.DEFENSIVE, playerStyle(Position.FULLBACK, Trait.HEADING, Trait.MARKING))
    }

    @Test
    fun `a fullback's creative clause reads only the first characteristic`() {
        for (trait in listOf(Trait.DRIBBLING, Trait.FINISHING, Trait.PASSING, Trait.PLAYMAKING)) {
            assertEquals(PlayerStyle.OFFENSIVE, style(Position.FULLBACK, trait), "fullback with $trait first")
            assertEquals(
                PlayerStyle.DEFENSIVE,
                playerStyle(Position.FULLBACK, Trait.STAMINA, trait),
                "fullback with $trait second only",
            )
        }
    }

    @Test
    fun `a fullback matching no test at all falls back to defensive`() {
        assertEquals(
            PlayerStyle.DEFENSIVE,
            playerStyle(Position.FULLBACK, Trait.HEADING, Trait.STAMINA),
        )
    }

    @Test
    fun `a midfielder reads both characteristics, the first one first`() {
        for (trait in listOf(Trait.PASSING, Trait.FINISHING, Trait.DRIBBLING, Trait.PLAYMAKING)) {
            assertEquals(PlayerStyle.OFFENSIVE, style(Position.MIDFIELDER, trait), "midfielder with $trait")
            assertEquals(
                PlayerStyle.OFFENSIVE,
                playerStyle(Position.MIDFIELDER, Trait.STAMINA, trait),
                "midfielder with $trait second",
            )
        }
        assertEquals(PlayerStyle.DEFENSIVE, style(Position.MIDFIELDER, Trait.TACKLING))
        assertEquals(PlayerStyle.DEFENSIVE, playerStyle(Position.MIDFIELDER, Trait.STAMINA, Trait.MARKING))
    }

    @Test
    fun `for a midfielder the first characteristic decides before the second is read`() {
        assertEquals(PlayerStyle.OFFENSIVE, playerStyle(Position.MIDFIELDER, Trait.PASSING, Trait.MARKING))
        assertEquals(PlayerStyle.DEFENSIVE, playerStyle(Position.MIDFIELDER, Trait.MARKING, Trait.PASSING))
    }

    @Test
    fun `a midfielder matching no test defaults to offensive`() {
        assertEquals(
            PlayerStyle.OFFENSIVE,
            playerStyle(Position.MIDFIELDER, Trait.HEADING, Trait.STAMINA),
        )
    }

    @Test
    fun `a forward reads only the first characteristic`() {
        assertEquals(PlayerStyle.DEFENSIVE, style(Position.FORWARD, Trait.TACKLING))
        assertEquals(PlayerStyle.DEFENSIVE, playerStyle(Position.FORWARD, Trait.MARKING, Trait.DRIBBLING))
        assertEquals(PlayerStyle.WINGER, style(Position.FORWARD, Trait.DRIBBLING))
        assertEquals(PlayerStyle.WINGER, style(Position.FORWARD, Trait.PACE))
        assertEquals(PlayerStyle.WINGER, style(Position.FORWARD, Trait.CROSSING))
        assertEquals(PlayerStyle.OFFENSIVE, playerStyle(Position.FORWARD, Trait.FINISHING, Trait.DRIBBLING))
        assertEquals(PlayerStyle.OFFENSIVE, playerStyle(Position.FORWARD, Trait.HEADING, Trait.MARKING))
    }

    @Test
    fun `a forward matching no test defaults to offensive`() {
        assertEquals(
            PlayerStyle.OFFENSIVE,
            playerStyle(Position.FORWARD, Trait.HEADING, Trait.STAMINA),
        )
    }

    @Test
    fun `a repeated trait decides the same way as a single one`() {
        assertEquals(
            playerStyle(Position.FULLBACK, Trait.PACE, Trait.STAMINA),
            playerStyle(Position.FULLBACK, Trait.PACE, Trait.PACE),
        )
    }
}
