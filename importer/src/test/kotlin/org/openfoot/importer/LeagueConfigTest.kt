package org.openfoot.importer

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.importer.ImportFixtures.bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reads a national league configuration file into [LeagueConfigEntry] rows.
 *
 * Division membership is no longer worked out here: the pyramid generator of
 * section 1.9, exercised by PyramidTest in the engine module, consumes these
 * entries directly. This module's job stops at decoding the file.
 */
class LeagueConfigTest {

    @Test
    fun `a configuration yields one entry per tier`() {
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(pais = 29, divisao = 1, nTimes = 20),
                        ImportFixtures.Tier(pais = 29, divisao = 2, nTimes = 20),
                        ImportFixtures.Tier(pais = 29, divisao = 3, nTimes = 20),
                    ),
                ),
            ),
        )
        assertEquals(3, entries.size)
        assertEquals(29, entries.first().country)
        assertEquals(1, entries.first().division)
        assertEquals(20, entries.first().teamCount)
    }

    @Test
    fun `a tier with no teams is not a tier`() {
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(pais = 29, divisao = 1, nTimes = 20),
                        ImportFixtures.Tier(pais = 29, divisao = 2, nTimes = 0),
                    ),
                ),
            ),
        )
        assertEquals(1, entries.size)
        assertEquals(1, entries.single().division)
    }

    @Test
    fun `something that is not a configuration is refused`() {
        assertFailsWith<IllegalArgumentException> {
            LeagueConfigReader.read(bytes(ImportFixtures.Tier(pais = 29, divisao = 1, nTimes = 20)))
        }
    }

    @Test
    fun `a tier is read with relegation clamp and resolved turns`() {
        // A 10 team tier claiming 4 relegated is clamped to 2, per FORMAT-SPEC's
        // "nRebaixados > 2 com nTimes <= 10 e grampeado para 2 na carga". The
        // same tier declares formula 4, which resolves to 4 turns for a 10 team
        // league per section 1.3 (the ESP third division shape: 10 times,
        // formula=4 -> 4 turnos).
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(pais = 65, divisao = 3, nTimes = 10, nRebaixados = 4, formula = 4),
                    ),
                ),
            ),
        )
        val entry = entries.single()
        assertEquals(65, entry.country)
        assertEquals(3, entry.division)
        assertEquals(10, entry.teamCount)
        assertEquals(2, entry.relegated)
        assertEquals(4, entry.turns)
    }

    @Test
    fun `formula 4 is not an override, a 12 team league keeps the default 3 turns`() {
        // FORMAT-SPEC's formula field only overrides the default for 10, 12
        // or 14 team leagues when it is 2 or 3; 4 is not an override, so a
        // 12 team tier with formula 4 falls through to the default of 3.
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(pais = 65, divisao = 2, nTimes = 12, formula = 4),
                    ),
                ),
            ),
        )
        assertEquals(3, entries.single().turns)
    }

    @Test
    fun `desempate zero means penalties on and one means off`() {
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(pais = 65, divisao = 1, nTimes = 20, desempate = 0),
                        ImportFixtures.Tier(pais = 29, divisao = 1, nTimes = 20, desempate = 1),
                    ),
                ),
            ),
        )
        val byCountry = entries.associateBy { it.country }
        assertEquals(true, byCountry.getValue(65).penaltiesTiebreak)
        assertEquals(false, byCountry.getValue(29).penaltiesTiebreak)
    }

    @Test
    fun `the group and playoff fields are read, with the flat defaults when absent`() {
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(
                            pais = 29, divisao = 4, nTimes = 64, nRebaixados = 4,
                            nGrupos = 8, numeroTimesMataMata = 4, rebaixadosDireto = 4,
                        ),
                        ImportFixtures.Tier(
                            pais = 65, divisao = 2, nTimes = 22, nRebaixados = 3,
                            rebaixadosDireto = 2, vagasSobemPeloMataMata = 1,
                            duasVoltasplayoffReb = booleanArrayOf(true, false, false),
                            duasVoltasMataMataSobe = booleanArrayOf(true, true, false),
                        ),
                        ImportFixtures.Tier(pais = 29, divisao = 3, nTimes = 20, nRebaixados = 4, numeroTimesMataMata = 1020),
                    ),
                ),
            ),
        )
        val brazilFour = entries[0]
        assertEquals(8, brazilFour.groups)
        assertEquals(4, brazilFour.knockoutQualifiers)
        assertTrue(brazilFour.gamesInsideGroup)
        assertEquals(4, brazilFour.directRelegated)

        val spainTwo = entries[1]
        assertEquals(2, spainTwo.directRelegated)
        assertEquals(1, spainTwo.promotionPlayoffPlaces)
        assertEquals(listOf(true, false, false), spainTwo.relegationPlayoffLegs)
        assertEquals(listOf(true, true, false), spainTwo.promotionPlayoffLegs)

        assertEquals(LeagueConfigEntry.SERIE_C_FORMAT, entries[2].knockoutQualifiers)
    }

    @Test
    fun `the group flags are read as true when a configuration sets them`() {
        val entry = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(
                            pais = 29, divisao = 4, nTimes = 64, nRebaixados = 4, nGrupos = 8, numeroTimesMataMata = 4,
                            rebaixadoPeloGrupo = true, classificaPeloGeral = true, melhoresTerceiros = true,
                        ),
                    ),
                ),
            ),
        ).single()
        assertTrue(entry.relegatedByGroup)
        assertTrue(entry.qualifyByOverallTable)
        assertTrue(entry.bestThirds)
    }

    @Test
    fun `a promotion playoff count outside nought to two is read as nought`() {
        val entries = LeagueConfigReader.read(
            bytes(ImportFixtures.Pyramid(arrayListOf(ImportFixtures.Tier(pais = 65, divisao = 2, nTimes = 22, vagasSobemPeloMataMata = 5)))),
        )
        assertEquals(0, entries.single().promotionPlayoffPlaces)
    }

    @Test
    fun `rebaixadosDireto is read only from a version 22 configuration`() {
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(pais = 65, divisao = 2, nTimes = 22, nRebaixados = 3, rebaixadosDireto = 1, versaoArquivo = 21),
                        ImportFixtures.Tier(pais = 29, divisao = 2, nTimes = 22, nRebaixados = 3, rebaixadosDireto = 2, versaoArquivo = 22),
                    ),
                ),
            ),
        )
        assertEquals(3, entries[0].directRelegated)
        assertEquals(2, entries[1].directRelegated)
    }
}
