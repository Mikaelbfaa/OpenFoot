package org.openfoot.importer

import org.openfoot.importer.ImportFixtures.bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reads a state championship configuration file into StateChampionshipEntry
 * rows. Which clubs play which division is not worked out here: FORMAT-SPEC's
 * load rules run at world creation, in the season, and this module's job stops
 * at decoding the file.
 */
class StateChampionshipTest {

    private fun file(vararg tiers: ImportFixtures.StateTier) =
        bytes(ImportFixtures.StateChampionships(arrayListOf(*tiers)))

    @Test
    fun `a file yields one entry per division in file order`() {
        val entries = StateChampionshipReader.read(
            file(
                ImportFixtures.StateTier(id = 25, divisao = 1, formula = 7),
                ImportFixtures.StateTier(id = 25, divisao = 2),
                ImportFixtures.StateTier(id = 25, divisao = 3),
                ImportFixtures.StateTier(id = 25, divisao = 4),
            ),
        )
        assertEquals(listOf(1, 2, 3, 4), entries.map { it.division })
        assertTrue(entries.all { it.state == 25 })
        assertEquals(7, entries.first().preset)
        assertEquals(0, entries.last().preset)
    }

    @Test
    fun `legs read two as two legged and anything else as a single leg`() {
        // The Sao Paulo shape, quarters and semis in one leg and the final in
        // two, and the Rio shape, whose unread third position holds a zero.
        val entries = StateChampionshipReader.read(
            file(
                ImportFixtures.StateTier(id = 25, divisao = 1, formula = 7, finaisIdaVolta = intArrayOf(1, 1, 2)),
                ImportFixtures.StateTier(id = 18, divisao = 1, formula = 4, finaisIdaVolta = intArrayOf(2, 2, 0)),
            ),
        )
        assertEquals(listOf(false, false, true), entries[0].twoLeggedRounds)
        assertEquals(listOf(true, true, false), entries[1].twoLeggedRounds)
    }

    @Test
    fun `desempate zero means penalties on and one means off`() {
        val entries = StateChampionshipReader.read(
            file(
                ImportFixtures.StateTier(id = 4, divisao = 1, desempate = 0),
                ImportFixtures.StateTier(id = 4, divisao = 2, desempate = 1),
            ),
        )
        assertEquals(true, entries[0].penaltiesTiebreak)
        assertEquals(false, entries[1].penaltiesTiebreak)
    }

    @Test
    fun `an entry the loader could never look up is dropped`() {
        // The editor writes -1 for a state it does not know, and a division
        // of zero is accepted by the editor but never consulted by the loader.
        val entries = StateChampionshipReader.read(
            file(
                ImportFixtures.StateTier(id = -1, divisao = 1),
                ImportFixtures.StateTier(id = 25, divisao = 0),
                ImportFixtures.StateTier(id = 25, divisao = 1),
            ),
        )
        assertEquals(1, entries.size)
        assertEquals(25, entries.single().state)
    }

    @Test
    fun `a preset outside the table is refused rather than carried`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            StateChampionshipReader.read(file(ImportFixtures.StateTier(id = 25, divisao = 1, formula = 11)))
        }
        assertTrue(failure.message!!.contains("preset 11"), failure.message)
    }

    @Test
    fun `a legs array of the wrong length is refused`() {
        assertFailsWith<IllegalArgumentException> {
            StateChampionshipReader.read(
                file(ImportFixtures.StateTier(id = 25, divisao = 1, finaisIdaVolta = intArrayOf(2, 2))),
            )
        }
    }

    @Test
    fun `something that is not a state configuration is refused`() {
        assertFailsWith<IllegalArgumentException> {
            StateChampionshipReader.read(bytes(ImportFixtures.StateTier(id = 25, divisao = 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            StateChampionshipReader.read(
                bytes(ImportFixtures.Pyramid(arrayListOf(ImportFixtures.Tier(pais = 29, divisao = 1, nTimes = 20)))),
            )
        }
    }
}
