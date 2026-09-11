package org.openfoot.cli

import org.openfoot.engine.season.WeeklyTick
import org.openfoot.engine.season.nextSeason
import org.openfoot.engine.season.openingSeason
import org.openfoot.engine.season.playSeason
import org.openfoot.engine.season.seasonMovements
import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.RuleSets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The up and down lines of the season printout, the design's "quem subiu e
 * desceu": every league division's block ends with them, naming the clubs
 * the turnover moves out of it, and they are read from the same
 * seasonMovements the turnover applies.
 *
 * GoldenWorld's one division of ten relegates two by the embedded default of
 * section 1.9, and its reserve holds one club, clube-11, so section 1.12's
 * swap moves the smaller count: one club down, the last of the table, and
 * clube-11 into the division. Nothing sits above division one, so its up
 * line is empty. The cup is no division and prints neither line.
 */
class SeasonMovementsPrintTest {

    private val activeLeagues = setOf(GoldenWorld.fixCountry.index)

    private val played by lazy {
        val world = generateWorld(GoldenWorld.dataset, seed = 42L, activeLeagues = activeLeagues)
        playSeason(openingSeason(world, GoldenWorld.dataset, activeLeagues, year = 2026, seed = 42L), RuleSets.CLASSIC, WeeklyTick.NONE)
    }

    private fun block(printed: String, header: String): List<String> {
        val lines = printed.lines()
        val at = lines.indexOfFirst { it.startsWith("  $header ") }
        assertTrue(at >= 0, "no competition $header in:\n$printed")
        return lines.drop(at + 1).takeWhile { it.startsWith("    ") }
    }

    @Test
    fun `a division's block ends with the clubs the turnover moves up and down`() {
        val league = block(describeSeason(played), "league:701:1")
        assertEquals(listOf("    up:", "    down: clube-09"), league.takeLast(2))
        assertEquals(listOf("clube-09"), seasonMovements(played).departuresOf("league:701:1")?.down)

        val next = nextSeason(played, activeLeagues, RuleSets.CLASSIC)
        assertEquals(Standing.WithoutDivision, next.club("clube-09").standing)
        assertEquals(Standing.InDivision(1), next.club("clube-11").standing)
    }

    @Test
    fun `a competition that is no division prints no up or down line`() {
        val cup = block(describeSeason(played), "cup:701")
        assertTrue(cup.none { it.startsWith("    up:") || it.startsWith("    down:") }, cup.toString())
    }
}
