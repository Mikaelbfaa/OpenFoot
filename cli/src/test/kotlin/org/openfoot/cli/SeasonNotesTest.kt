package org.openfoot.cli

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.season.Approximation
import org.openfoot.engine.season.WeeklyTick
import org.openfoot.engine.season.openingSeason
import org.openfoot.engine.season.playSeason
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.RuleSets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The note lines of the season printout: a competition built over a format
 * this version approximates says so right under its own header, one line a
 * note, in the order the competition lists them, and a competition built as
 * configured prints none.
 *
 * GoldenWorld's division one is configured here with section 1.11's Serie C
 * sentinel and a relegation playoff of section 1.12, two notes on one
 * league, while its cup of eleven clubs sits far below the novo formato
 * threshold of section 1.13 and carries none of its own.
 */
class SeasonNotesTest {

    private val played by lazy {
        val fix = GoldenWorld.fixCountry.index
        val config = LeagueConfigEntry(
            country = fix, division = 1, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true,
            knockoutQualifiers = LeagueConfigEntry.SERIE_C_FORMAT, directRelegated = 1,
        )
        val dataset = GoldenWorld.dataset.copy(leagues = listOf(config))
        val world = generateWorld(dataset, seed = 42L, activeLeagues = setOf(fix))
        playSeason(openingSeason(world, dataset, setOf(fix), year = 2026, seed = 42L), RuleSets.CLASSIC, WeeklyTick.NONE)
    }

    private fun linesUnder(printed: String, header: String): List<String> {
        val lines = printed.lines()
        val at = lines.indexOfFirst { it.startsWith("  $header ") }
        assertTrue(at >= 0, "no competition $header in:\n$printed")
        return lines.drop(at + 1)
    }

    @Test
    fun `an approximated league prints its notes under its header, before its table`() {
        val under = linesUnder(describeSeason(played), "league:701:1")
        assertEquals(
            listOf(Approximation.SERIE_C_AS_FLAT_LEAGUE, Approximation.PLAYOFFS_AS_DIRECT_MOVEMENT).map { "    note: ${it.text}" },
            under.take(2),
        )
        assertTrue(under[2].trimStart().startsWith("pos"), under[2])
    }

    @Test
    fun `a competition built as configured prints no note`() {
        val printed = describeSeason(played)
        assertTrue(linesUnder(printed, "cup:701").first().trimStart().startsWith("final order"))
        assertEquals(2, printed.lines().count { it.startsWith("    note: ") })
    }

    /**
     * The printer reads whatever notes a competition carries, so a cup
     * carrying the novo formato note and the grouped league notes prints
     * each of them, in its own order, under the cup's header.
     */
    @Test
    fun `every note a competition carries is printed in its order`() {
        val cup = played.competitions.getValue("cup:701")
        val notes = listOf(Approximation.NEW_CUP_FORMAT_AS_STANDARD, Approximation.BEST_THIRDS_IGNORED).map { it.text }
        val printed = describeSeason(played.withCompetition(cup.copy(approximations = notes)))
        assertEquals(notes.map { "    note: $it" }, linesUnder(printed, "cup:701").take(2))
    }
}
