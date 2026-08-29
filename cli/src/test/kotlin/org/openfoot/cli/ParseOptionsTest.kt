package org.openfoot.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParseOptionsTest {
    @Test
    fun `flags and values parse into pairs`() {
        val options = parseOptions(listOf("--dataset", "base.json", "--seed", "42"))
        assertEquals(mapOf("--dataset" to "base.json", "--seed" to "42"), options)
    }

    @Test
    fun `a bare token is refused`() {
        assertFailsWith<CliError> { parseOptions(listOf("base.json")) }
    }

    @Test
    fun `a trailing flag without a value is refused`() {
        assertFailsWith<CliError> { parseOptions(listOf("--seed")) }
    }

    /**
     * Last wins is behavior nobody chose on purpose. The test pins it so a
     * change to it is loud rather than silent, not because it is endorsed.
     */
    @Test
    fun `a repeated flag keeps the last value`() {
        val options = parseOptions(listOf("--seed", "1", "--seed", "2"))
        assertEquals(mapOf("--seed" to "2"), options)
    }
}
