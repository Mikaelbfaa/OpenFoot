package org.openfoot.cli

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * loadDataset reads and decodes a dataset file, checking the schema version
 * before the strict decode so a version one file fails by version rather than
 * by an unknown key the strict decoder was never going to accept.
 */
class LoadDatasetTest {

    @Test
    fun `a version one file fails by version, not by unknown key`() {
        val path = createTempFile("openfoot", ".json")
        path.writeText(
            """
            {
              "version": 1,
              "countries": [
                {"index": 29, "name": "BRA", "level": 20, "continent": 1}
              ],
              "clubs": [
                {
                  "ref": "um", "name": "um", "country": 29, "level": 18,
                  "reputation": 4, "division": 1, "nationalTeam": false
                }
              ]
            }
            """.trimIndent(),
        )

        val error = assertFailsWith<CliError> { loadDataset(path.toString()) }
        assertTrue(error.message.orEmpty().contains("version 1"), error.message)
        assertFalse(error.message.orEmpty().lowercase().contains("unknown key"), error.message)
    }

    @Test
    fun `a missing file is refused by name`() {
        val missing = "${System.getProperty("java.io.tmpdir")}/nao-existe-${System.nanoTime()}.json"
        val error = assertFailsWith<CliError> { loadDataset(missing) }
        assertTrue(error.message.orEmpty().contains(missing), error.message)
    }

    @Test
    fun `garbage is refused as not usable`() {
        val path = createTempFile("openfoot", ".json")
        path.writeText("not json")

        val error = assertFailsWith<CliError> { loadDataset(path.toString()) }
        assertTrue(error.message.orEmpty().contains("not usable"), error.message)
    }
}
