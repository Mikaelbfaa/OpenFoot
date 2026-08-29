package org.openfoot.cli

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
    }
}
