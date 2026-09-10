package org.openfoot.engine.season

import org.openfoot.engine.match.SideState
import kotlin.test.Test
import kotlin.test.assertEquals

/** The three recovery ladders of section 3.9, read at their edges. */
class RecoveryTest {

    @Test
    fun `an AI club's player who played recovers by the AI ladder`() {
        assertEquals(20, weeklyRecovery(20, played = true, humanManaged = false))
        assertEquals(30, weeklyRecovery(21, played = true, humanManaged = false))
        assertEquals(50, weeklyRecovery(26, played = true, humanManaged = false))
        assertEquals(52, weeklyRecovery(32, played = true, humanManaged = false))
        assertEquals(42, weeklyRecovery(37, played = true, humanManaged = false))
    }

    @Test
    fun `a human club's player who played recovers less`() {
        assertEquals(13, weeklyRecovery(20, played = true, humanManaged = true))
        assertEquals(24, weeklyRecovery(25, played = true, humanManaged = true))
        assertEquals(37, weeklyRecovery(31, played = true, humanManaged = true))
        assertEquals(40, weeklyRecovery(36, played = true, humanManaged = true))
        assertEquals(30, weeklyRecovery(40, played = true, humanManaged = true))
    }

    @Test
    fun `a player who did not play recovers by age alone`() {
        for (human in listOf(false, true)) {
            assertEquals(30, weeklyRecovery(19, played = false, humanManaged = human))
            assertEquals(30, weeklyRecovery(25, played = false, humanManaged = human))
            assertEquals(35, weeklyRecovery(26, played = false, humanManaged = human))
            assertEquals(35, weeklyRecovery(44, played = false, humanManaged = human))
            assertEquals(30, weeklyRecovery(45, played = false, humanManaged = human))
        }
    }

    @Test
    fun `recovery stops at full energy`() {
        assertEquals(SideState.FULL_ENERGY, recover(90, 50))
        assertEquals(72, recover(20, 52))
    }
}
