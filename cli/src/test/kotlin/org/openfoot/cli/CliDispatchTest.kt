package org.openfoot.cli

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CliDispatchTest {
    @Test
    fun `help exits zero`() {
        assertEquals(0, dispatch(arrayOf("help")))
    }

    @Test
    fun `no arguments print usage and exit one`() {
        assertEquals(1, dispatch(emptyArray()))
    }

    @Test
    fun `an unknown subcommand exits one`() {
        assertEquals(1, dispatch(arrayOf("bogus")))
    }

    @Test
    fun `a subcommand missing its options exits one rather than crashing`() {
        assertEquals(1, dispatch(arrayOf("worldgen")))
        assertEquals(1, dispatch(arrayOf("callup")))
        assertEquals(1, dispatch(arrayOf("callup", "--dataset", "x.json", "--seed", "1")))
        assertEquals(1, dispatch(arrayOf("season")))
        assertEquals(1, dispatch(arrayOf("season", "--dataset", "x.json")))
    }

    @Test
    fun `a season count below one exits one`() {
        assertEquals(1, dispatch(arrayOf("season", "--dataset", "x.json", "--seed", "1", "--seasons", "0")))
    }

    /**
     * The same refusal over a dataset that loads, so the exit comes from the
     * season count itself and not from a missing file: zero or a negative
     * count would otherwise play nothing, print nothing and exit zero.
     */
    @Test
    fun `a season count below one is refused over a usable dataset`() {
        val file = File.createTempFile("golden-world", ".json")
        file.deleteOnExit()
        file.writeText(Json.encodeToString(GoldenWorld.dataset))
        assertEquals(1, dispatch(arrayOf("season", "--dataset", file.path, "--seed", "1", "--seasons", "0")))
        assertEquals(1, dispatch(arrayOf("season", "--dataset", file.path, "--seed", "1", "--seasons", "-2")))
    }
}
