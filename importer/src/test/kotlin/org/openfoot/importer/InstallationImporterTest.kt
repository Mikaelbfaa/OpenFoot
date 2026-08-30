package org.openfoot.importer

import org.openfoot.dataset.CountryEntry
import org.openfoot.importer.ImportFixtures.installation
import org.openfoot.importer.ImportFixtures.squadman
import org.openfoot.importer.ImportFixtures.team
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The importer end to end, against directories the test lays out itself.
 *
 * The country level derivation is the interesting part. The original keeps that
 * table in code the clean room rule puts out of reach, so it is derived from the
 * clubs a country holds, and the derivation needs pinning.
 */
class InstallationImporterTest {

    private fun root(): File = createTempDirectory("openfoot").toFile()

    private val brazil = 29

    @Test
    fun `an installation of one club imports`() {
        val result = InstallationImporter.importFrom(installation(root(), listOf(team())))

        assertEquals(1, result.dataset.clubs.size)
        assertEquals("clube_bra", result.dataset.clubs.single().ref)
        assertEquals(1, result.dataset.clubs.single().squad.size)
    }

    @Test
    fun `a country is rated from the embedded table`() {
        val result = InstallationImporter.importFrom(
            installation(root(), listOf(team(ref = "clube_bra"))),
        )

        // "clube_bra" carries country 29 by default, Brazil in the table, whose
        // row is level 20, continent 1 (South America). The club's own level
        // (18 by default, well below 20) plays no part now that the table
        // supplies the country level directly.
        assertEquals(
            CountryEntry(index = brazil, name = "BRA", level = 20, continent = 1),
            result.dataset.country(brazil),
        )
    }

    @Test
    fun `a country outside the table falls back rather than being rated zero`() {
        val outsideTable = 300
        val result = InstallationImporter.importFrom(
            installation(
                root(),
                listOf(team(ref = "clube_bra", squad = listOf(squadman(country = outsideTable)))),
            ),
        )

        assertEquals(
            InstallationImporter.UNRATEABLE_COUNTRY_LEVEL,
            result.dataset.country(outsideTable)?.level,
        )
        assertTrue(
            result.notes.any { it.contains("outside the embedded table") },
            result.notes.toString(),
        )
    }

    @Test
    fun `the five paying countries are flagged and nobody else is`() {
        val result = InstallationImporter.importFrom(
            installation(
                root(),
                listOf(
                    team(ref = "alemao_ale", country = 3, state = 0),
                    team(ref = "ingles_ing", country = 97, state = 0),
                    team(ref = "brasileiro_bra", country = brazil),
                ),
            ),
        )

        assertTrue(result.dataset.country(3)?.majorLeague == true)
        assertTrue(result.dataset.country(97)?.majorLeague == true)
        assertTrue(result.dataset.country(brazil)?.majorLeague == false)
    }

    @Test
    fun `a country is named after the suffix its clubs use`() {
        val result = InstallationImporter.importFrom(
            installation(root(), listOf(team(ref = "clube_bra", country = brazil))),
        )
        assertEquals("BRA", result.dataset.country(brazil)?.name)
    }

    @Test
    fun `a club reference with no suffix still yields a usable country name`() {
        val result = InstallationImporter.importFrom(
            installation(root(), listOf(team(ref = "semsufixo", country = brazil))),
        )
        assertTrue(result.dataset.country(brazil)?.name?.isNotBlank() == true)
    }

    @Test
    fun `the options file is read when it is there`() {
        val result = InstallationImporter.importFrom(
            installation(
                root(),
                listOf(team()),
                options = ImportFixtures.Options(
                    habilidadeIndividual = true,
                    salarioMensal = false,
                    velocidade = 4,
                ),
            ),
        )

        assertTrue(result.dataset.options.individualAbilities)
        assertTrue(!result.dataset.options.monthlyWages)
    }

    @Test
    fun `a missing options file falls back to what the original ships with`() {
        val result = InstallationImporter.importFrom(installation(root(), listOf(team())))

        assertTrue(!result.dataset.options.individualAbilities)
        assertTrue(result.dataset.options.monthlyWages)
        assertTrue(result.notes.any { it.contains("options") }, result.notes.toString())
    }

    @Test
    fun `league configurations reach the dataset`() {
        val result = InstallationImporter.importFrom(
            installation(
                root(),
                listOf(team(ref = "forte_bra", level = 19), team(ref = "fraco_bra", level = 8)),
                pyramids = listOf(
                    ImportFixtures.Pyramid(
                        arrayListOf(
                            ImportFixtures.Tier(pais = brazil, divisao = 1, nTimes = 18),
                            ImportFixtures.Tier(pais = brazil, divisao = 2, nTimes = 18),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(result.dataset.leagues.isNotEmpty(), result.dataset.leagues.toString())
        val first = result.dataset.leagues.first()
        assertEquals(brazil, first.country)
        assertEquals(1, first.division)
    }

    @Test
    fun `no pyramid at all is only informational, since the pyramid generator has embedded defaults`() {
        val result = InstallationImporter.importFrom(installation(root(), listOf(team())))
        val note = result.notes.single { it.contains("no league configuration") }
        assertTrue(note.contains("embedded defaults of section 1.9"), note)
    }

    @Test
    fun `a directory that is not an installation is refused`() {
        assertFailsWith<IllegalArgumentException> { InstallationImporter.importFrom(root()) }
    }

    @Test
    fun `an unreadable team file costs that club and no other`() {
        val directory = installation(root(), listOf(team(ref = "bom_bra")))
        File(directory, "teams/lixo.ban").writeBytes(byteArrayOf(1, 2, 3, 4))

        val result = InstallationImporter.importFrom(directory)

        assertEquals(listOf("bom_bra"), result.dataset.clubs.map { it.ref })
        assertTrue(result.notes.any { it.contains("lixo.ban") }, result.notes.toString())
    }
}
