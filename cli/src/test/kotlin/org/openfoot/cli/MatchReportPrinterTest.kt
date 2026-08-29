package org.openfoot.cli

import org.openfoot.engine.match.MatchClock
import org.openfoot.engine.match.MatchEvent
import org.openfoot.engine.match.MatchReport
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The output is compared with a diff between runs, so nothing in it may read a
 * clock, a locale or a hash order. What is asserted here is that it says the
 * things a reader needs and that two calls on one report are identical.
 */
class MatchReportPrinterTest {

    /**
     * A match of four minutes: the home side wins three possession duels and
     * scores once, the away side wins one and shoots wide. Small enough that
     * every number in the output can be checked by hand.
     *
     * Both starting lineups are empty. They are what section 3.14's rating
     * reads and the printed report is section 3.13's statistics and the log,
     * neither of which names a player at all here, so there is nothing for a
     * lineup to change in this output.
     */
    private fun report(): MatchReport {
        val log = listOf(
            MatchEvent.PossessionWon(0, TeamSide.HOME),
            MatchEvent.Shot(0, TeamSide.HOME, shooter = null, onTarget = true, scored = true),
            MatchEvent.PossessionWon(1, TeamSide.AWAY),
            MatchEvent.Shot(1, TeamSide.AWAY, shooter = null, onTarget = false, scored = false),
            MatchEvent.PossessionWon(2, TeamSide.HOME),
            MatchEvent.Tackle(2, TeamSide.AWAY),
            MatchEvent.PossessionWon(3, TeamSide.HOME),
            MatchEvent.MisplacedPass(3, TeamSide.AWAY),
        )
        return MatchReport(
            clock = MatchClock(firstHalfMinutes = 2, secondHalfMinutes = 2),
            log = log,
            homeGoals = 1,
            awayGoals = 0,
            startingPossessor = TeamSide.HOME,
            homeLineup = emptyList(),
            awayLineup = emptyList(),
        )
    }

    /**
     * A line split on runs of whitespace, so an assertion can pin which words
     * a line carries and in what order without pinning the column the report
     * lines them up in.
     */
    private fun fields(line: String): List<String> = line.trim().split(Regex("\\s+"))

    /**
     * The two header lines are pinned as whole lines rather than by asking
     * whether the club names appear somewhere in the output. Both refs are
     * printed a second time further down, one on each side's statistics line,
     * so contains(ref) stays true with the header deleted outright: before
     * this test asserted lines, removing both header lines left the whole of
     * the cli test suite green.
     */
    @Test
    fun `the header lines name the home club and then the away club`() {
        val text = describe(report(), homeRef = "Flamengo_bra", awayRef = "Santos_bra")
        val lines = text.lines()

        assertEquals(
            listOf("home", "Flamengo_bra"),
            fields(lines[0]),
            "the first line labels the home club and names it, was:\n$text",
        )
        assertEquals(
            listOf("away", "Santos_bra"),
            fields(lines[1]),
            "and the second does the same for the away club, was:\n$text",
        )
    }

    @Test
    fun `the score line carries the score`() {
        val text = describe(report(), homeRef = "Flamengo_bra", awayRef = "Santos_bra")

        assertTrue(
            text.contains("1 x 0"),
            "the fixture's score is one nil, and \"1 x 0\" is the exact separator only a correct " +
                "score line writes between the two goal counts. A bare \"1\" would also pass with " +
                "the score line wrong or missing, because it already appears unconditionally " +
                "elsewhere in this fixture's output: home shots, home on target, away wide, away " +
                "tackles and away misplaced passes are all 1. Was:\n$text",
        )
    }

    @Test
    fun `the same report describes identically twice`() {
        val once = describe(report(), "Flamengo_bra", "Santos_bra")
        val twice = describe(report(), "Flamengo_bra", "Santos_bra")

        assertEquals(once, twice, "the output is compared with a diff between runs")
    }

    @Test
    fun `the possession line is the duel share and not the tick share`() {
        val text = describe(report(), "Flamengo_bra", "Santos_bra")

        assertTrue(
            text.contains("75"),
            "the home side won three of four duels; the tick share would be fifty, " +
                "because section 3.5 alternates unconditionally. Was:\n$text",
        )
    }

    @Test
    fun `a goalless match still prints its shot counts`() {
        val base = report()
        val goalless = base.copy(
            log = base.log.filterNot { it is MatchEvent.Shot },
            homeGoals = 0,
            awayGoals = 0,
        )
        val text = describe(goalless, "Flamengo_bra", "Santos_bra")

        assertTrue(
            text.contains("  Flamengo_bra  shots 0  on target 0  wide 0"),
            "with every Shot event stripped and both goal counts zeroed, the home side's shots, " +
                "on target and wide must all read nought. The club name belongs in the expected " +
                "run because the run on its own is ambiguous: it is printed once per side, so a " +
                "regression that dropped or corrupted one side's line would still be matched by " +
                "the other side's zeroes. A bare \"0\" would be weaker still, because the base " +
                "fixture already contains a 0 in the score and in away's on target count. " +
                "Was:\n$text",
        )
        assertTrue(
            text.contains("  Santos_bra  shots 0  on target 0  wide 0"),
            "and the away side reads nought in the same three fields on its own line, which is " +
                "the half of this assertion the other half cannot stand in for. Was:\n$text",
        )
    }
}
