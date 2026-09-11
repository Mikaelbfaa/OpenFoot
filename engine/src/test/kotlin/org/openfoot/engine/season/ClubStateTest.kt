package org.openfoot.engine.season

import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.engine.match.SideState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClubStateTest {

    private val world = generateWorld(WorldFixtures.dataset(), 1, activeLeagues = emptySet())
    private val state = ClubState.fresh(world.clubs.single())
    private val today = CalendarDate(2026, 1, 4)

    @Test
    fun `a fresh club has every man fit, fresh and at the club's reputation`() {
        assertEquals(state.squad.size, state.records.size)
        assertTrue(state.records.all { it.canPlay(today, LEAGUE) && it.energy == SideState.FULL_ENERGY })
        assertEquals(state.club.entry.reputation, state.reputation)
        assertEquals(state.club.entry.ref, state.key)
        assertEquals(null, state.representedCountry)
        assertEquals(state.club.standing, state.standing)
    }

    @Test
    fun `availability reads suspension, injury and energy off the record`() {
        val changed = state
            .withRecord(0) { it.withDiscipline(LEAGUE, it.disciplineIn(LEAGUE).banned(1)) }
            .withRecord(0) { it.copy(energy = 40) }
        val availability = changed.availability(today, LEAGUE).of(0, changed.squad[0])
        assertFalse(availability.canPlay)
        assertTrue(changed.availability(today, "cup:29").of(0, changed.squad[0]).canPlay, "the ban is the league's alone")
        assertEquals(40, availability.energy)
        assertEquals(40, changed.designationEnergy.of(0, changed.squad[0]))
    }

    @Test
    fun `an injury keeps a man out until its date and not on it`() {
        val record = PlayerRecord(injuredUntil = CalendarDate(2026, 1, 20))
        assertTrue(record.injured(CalendarDate(2026, 1, 19)))
        assertFalse(record.injured(CalendarDate(2026, 1, 20)))
        assertFalse(record.canPlay(CalendarDate(2026, 1, 4), LEAGUE))
    }

    @Test
    fun `a clean record is kept as an absent key`() {
        val record = PlayerRecord().withDiscipline(LEAGUE, DisciplineRecord(yellows = 1))
        assertEquals(DisciplineRecord(yellows = 1), record.disciplineIn(LEAGUE))
        assertEquals(DisciplineRecord.CLEAN, record.disciplineIn("cup:29"))
        assertEquals(PlayerRecord(), record.withDiscipline(LEAGUE, DisciplineRecord.CLEAN))
    }

    private companion object {
        const val LEAGUE = "league:29:1"
    }
}
