package org.openfoot.cli

import org.openfoot.engine.world.callUpNationalTeam
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.CompetitionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The callup command's pieces below the printout: how a side reference
 * resolves, which competition two sides meet in, and that the description
 * is stable between runs.
 */
class CallUpTest {

    private val world = generateWorld(GoldenWorld.dataset, seed = 9L, activeLeagues = setOf(GoldenWorld.fixCountry.index))

    @Test
    fun `the same call-up describes identically every time`() {
        val once = describeCallUp(callUpNationalTeam(world, GoldenWorld.dataset, GoldenWorld.repCountry.index), GoldenWorld.dataset)
        val twice = describeCallUp(callUpNationalTeam(world, GoldenWorld.dataset, GoldenWorld.repCountry.index), GoldenWorld.dataset)
        assertEquals(once, twice)
        assertTrue(once.startsWith("country   Fixture Reputacao  level 20  reputation 5\n"), once)
        assertTrue(once.contains("  free agent\n"), once)
    }

    @Test
    fun `a national prefix resolves to a call-up and a bare ref to a club`() {
        val national = resolveCompetitor("national:Fixture Ativo", world, GoldenWorld.dataset)
        val club = resolveCompetitor("clube-01", world, GoldenWorld.dataset)

        assertEquals("national:701", national.key)
        assertEquals("clube-01", club.key)
        assertEquals(GoldenWorld.fixCountry.index, national.representedCountry)
        assertEquals(null, club.representedCountry)
    }

    @Test
    fun `country names resolve without regard to case and unknown ones fail by name`() {
        assertEquals(GoldenWorld.repCountry.index, resolveCountry("fixture reputacao", GoldenWorld.dataset))
        val failure = assertFailsWith<CliError> { resolveCountry("Narnia", GoldenWorld.dataset) }
        assertTrue(failure.message!!.contains("NARNIA"), failure.message)
        assertFailsWith<CliError> { resolveCompetitor("national:Narnia", world, GoldenWorld.dataset) }
        assertFailsWith<CliError> { resolveCompetitor("clube-99", world, GoldenWorld.dataset) }
    }

    @Test
    fun `two clubs play a friendly, two national teams a national team match, and a mix is refused`() {
        val club = resolveCompetitor("clube-01", world, GoldenWorld.dataset)
        val other = resolveCompetitor("clube-02", world, GoldenWorld.dataset)
        val brazil = resolveCompetitor("national:Fixture Ativo", world, GoldenWorld.dataset)
        val spain = resolveCompetitor("national:Fixture Reputacao", world, GoldenWorld.dataset)

        assertEquals(CompetitionKind.FRIENDLY, competitionKindFor(club, other))
        assertEquals(CompetitionKind.NATIONAL_TEAM, competitionKindFor(brazil, spain))
        assertFailsWith<CliError> { competitionKindFor(club, spain) }
        assertFailsWith<CliError> { competitionKindFor(brazil, other) }
    }
}
