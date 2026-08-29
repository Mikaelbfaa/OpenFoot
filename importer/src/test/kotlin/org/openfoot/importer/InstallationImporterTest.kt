package org.openfoot.importer

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
    private val spain = 65
    private val angola = 5

    @Test
    fun `an installation of one club imports`() {
        val result = InstallationImporter.importFrom(installation(root(), listOf(team())))

        assertEquals(1, result.dataset.clubs.size)
        assertEquals("clube_bra", result.dataset.clubs.single().ref)
        assertEquals(1, result.dataset.clubs.single().squad.size)
    }

    @Test
    fun `a country is rated by the strongest club it holds`() {
        val result = InstallationImporter.importFrom(
            installation(
                root(),
                listOf(
                    team(ref = "grande_bra", country = brazil, level = 19),
                    team(ref = "pequeno_bra", country = brazil, level = 7),
                    team(ref = "unico_ang", country = angola, state = 0, level = 12),
                ),
            ),
        )

        assertEquals(19, result.dataset.country(brazil)?.level)
        assertEquals(12, result.dataset.country(angola)?.level)
    }

    @Test
    fun `a country holding no club falls back rather than being rated zero`() {
        val result = InstallationImporter.importFrom(
            installation(
                root(),
                listOf(team(ref = "clube_bra", squad = listOf(squadman(country = spain)))),
            ),
        )

        assertEquals(
            InstallationImporter.UNRATEABLE_COUNTRY_LEVEL,
            result.dataset.country(spain)?.level,
        )
        assertTrue(result.notes.any { it.contains("hold no club") }, result.notes.toString())
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
    fun `divisions come from the pyramid when one is configured`() {
        val result = InstallationImporter.importFrom(
            installation(
                root(),
                listOf(
                    team(ref = "forte_bra", level = 19),
                    team(ref = "fraco_bra", level = 8),
                ),
                pyramids = listOf(
                    ImportFixtures.Pyramid(
                        arrayListOf(
                            ImportFixtures.Tier(brazil, 1, 1),
                            ImportFixtures.Tier(brazil, 2, 1),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, result.dataset.clubs.single { it.ref == "forte_bra" }.division)
        assertEquals(2, result.dataset.clubs.single { it.ref == "fraco_bra" }.division)
    }

    @Test
    fun `no pyramid at all is reported rather than left to be noticed`() {
        val result = InstallationImporter.importFrom(installation(root(), listOf(team())))
        assertTrue(
            result.notes.any { it.contains("no league configuration") },
            result.notes.toString(),
        )
    }

    @Test
    fun `the no-division note states generation, growth and decline`() {
        val result = InstallationImporter.importFrom(installation(root(), listOf(team())))
        val note = result.notes.single { it.contains("no division") }
        assertTrue(note.contains("ceiling of 30"), note)
        assertTrue(note.contains("floor of 1"), note)
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
