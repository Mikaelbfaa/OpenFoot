package org.openfoot.engine.season

import org.openfoot.engine.match.Lineups
import org.openfoot.engine.match.MatchEvent
import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.PlayerId
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Section 3.8 between matches: the yellow count, the ban ladder of a direct
 * red, what a second yellow adds, and how a suspension is served.
 */
class SuspensionsTest {

    private val player = Lineups.player(slot = 5, strength = 50, id = 7)
    private val other = Lineups.player(slot = 6, strength = 50, id = 8)

    @Test
    fun `three yellows suspend and serving clears them`() {
        val record = DisciplineRecord.CLEAN.booked().booked()
        assertFalse(record.suspended)
        val third = record.booked()
        assertTrue(third.suspended)
        assertEquals(DisciplineRecord.CLEAN, third.served())
    }

    @Test
    fun `a ban suspends one match at a time, and yellows are served before the ban`() {
        val both = DisciplineRecord(yellows = 3, ban = 2)
        assertTrue(both.suspended)
        val afterOne = both.served()
        assertEquals(DisciplineRecord(yellows = 0, ban = 2), afterOne)
        assertTrue(afterOne.suspended)
        assertEquals(DisciplineRecord(yellows = 0, ban = 1), afterOne.served())
        assertEquals(DisciplineRecord.CLEAN, afterOne.served().served())
        assertEquals(DisciplineRecord.CLEAN, DisciplineRecord.CLEAN.served())
    }

    @Test
    fun `the direct red ladder is read at its exact boundaries`() {
        val expected = mapOf(0 to 1, 699 to 1, 700 to 2, 899 to 2, 900 to 3, 969 to 3, 970 to 5, 990 to 5, 991 to 10, 999 to 10)
        for ((draw, matches) in expected) {
            assertEquals(matches, directRedBan(ScriptedInts(draw)), "draw $draw")
        }
    }

    @Test
    fun `a second yellow is a booking plus one match, counted once`() {
        val log = listOf(
            MatchEvent.Booking(10, TeamSide.HOME, player),
            MatchEvent.Booking(40, TeamSide.HOME, player),
            MatchEvent.SendingOff(40, TeamSide.HOME, player, secondYellow = true),
        )
        val draws = ScriptedInts()
        val records = emptyMap<PlayerId, DisciplineRecord>().afterMatch(log, TeamSide.HOME, draws)

        assertEquals(DisciplineRecord(yellows = 2, ban = 1), records.getValue(player.id))
        assertEquals(0, draws.draws, "a second yellow draws nothing")
    }

    @Test
    fun `a direct red draws its ban and only the side asked for is read`() {
        val log = listOf(
            MatchEvent.Booking(5, TeamSide.AWAY, other),
            MatchEvent.SendingOff(30, TeamSide.HOME, player, secondYellow = false),
            MatchEvent.SendingOff(60, TeamSide.HOME, other, secondYellow = false),
        )
        val draws = ScriptedInts(950, 100)
        val records = mapOf(player.id to DisciplineRecord(yellows = 1)).afterMatch(log, TeamSide.HOME, draws)

        assertEquals(DisciplineRecord(yellows = 1, ban = 3), records.getValue(player.id))
        assertEquals(DisciplineRecord(yellows = 0, ban = 1), records.getValue(other.id))
        assertEquals(2, draws.draws)
        assertEquals(
            emptyMap(),
            emptyMap<PlayerId, DisciplineRecord>().afterMatch(log.take(1), TeamSide.HOME, ScriptedInts()),
        )
    }
}
