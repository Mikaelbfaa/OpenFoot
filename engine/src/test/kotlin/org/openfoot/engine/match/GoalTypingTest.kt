package org.openfoot.engine.match

import org.openfoot.model.Designated
import org.openfoot.model.GoalType
import org.openfoot.model.PlayerId
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Section 3.7, taken apart draw by draw.
 *
 * Every expectation here is recomputed from the spec while the test is
 * written. The own goal weights in particular are added up by hand in the
 * comment above the fixture rather than read back out of the rule set, so a
 * table that was mistyped into the rule set fails here instead of agreeing
 * with itself.
 *
 * The fixtures deliberately avoid the shortcut a wrong implementation could
 * pass by: the man a case expects is almost never the first entry of the list
 * being drawn from, and never the heaviest, so an implementation that always
 * took the front of the list or always took the maximum weight fails every
 * one of them.
 */
class GoalTypingTest {

    private val rules = RuleSets.CLASSIC
    private val goalTypes = rules.goalTypes

    /**
     * The attacking eleven, formation four, one player per cell with his own
     * cell number as his identity so that a designation can be written as a
     * cell.
     */
    private fun attacking(designated: Designated = Designated.NONE, human: Boolean = false) =
        Lineups.side(
            Lineups.FORMATION_4_4_2.map { Lineups.player(it, strength = 50) },
            designated = designated,
            humanManaged = human,
        )

    /**
     * The side that concedes, with one player in each of the seven weight
     * bands of section 3.7's own goal draw and nobody else.
     *
     * In list order the cells are 1, 2, 3, 9, 10, 11, 14 and 25, which the
     * spec weights 1, 5, 18, 5, 1, 5, 1 and 1. The total is 37 and the
     * running sums are 1, 6, 24, 29, 30, 35, 36 and 37, which is what the
     * boundary draws below are written against.
     */
    private fun concedingBands(human: Boolean = false) = Lineups.side(
        OWN_GOAL_BAND_CELLS.map { Lineups.player(it, strength = 50) },
        context = Lineups.context(isHome = false),
        humanManaged = human,
    )

    /**
     * The attacking eleven plus one reserve who has not come on, named as
     * both designated players.
     *
     * He sits in the lineup list, as MatchSide allows a bench entry to, and
     * carries the unused substitute cell rather than a pitch one, so he is
     * found by identity and refused by cell.
     */
    private fun attackingWithBenchedDesignate() = Lineups.side(
        Lineups.FORMATION_4_4_2.map { Lineups.player(it, strength = 50) } +
            Lineups.player(slot = -1, strength = 50, id = 40),
        designated = Designated(taker = PlayerId(40), cornerTaker = PlayerId(40)),
    )

    /** A side with nobody at all in a pitch cell, so an own goal has no author to find. */
    private fun concedingNobody() = Lineups.side(
        listOf(
            Lineups.player(slot = -1, strength = 50, id = 30),
            Lineups.player(slot = -1, strength = 50, id = 31),
        ),
        context = Lineups.context(isHome = false),
    )

    private fun setupOf(home: MatchSide, away: MatchSide = concedingBands()) =
        MatchSetup(home = home, away = away, season = 1, rules = rules)

    private fun MatchSetup.finisher(): MatchPlayer = home.lineup.single { it.slot.value == 24 }

    private fun type(setup: MatchSetup, rng: ScriptedDraws): TypedGoal =
        typeGoal(setup, TeamSide.HOME, setup.finisher(), rng)

    private fun resolve(setup: MatchSetup, rng: ScriptedDraws): ResolvedGoal =
        resolveGoal(setup, TeamSide.HOME, setup.finisher(), minute = 30, rng = rng)

    private fun ResolvedGoal.goal(): MatchEvent.Goal = events.filterIsInstance<MatchEvent.Goal>().single()

    /**
     * The two open play bands, which are not one band: the whole run under
     * 900 and the short tail from 995 to the bound. Both ends of both are
     * checked, and every case is asserted to consume exactly two draws, the
     * type and section 3.6's no assister coin, which is what says the assist
     * really was reached.
     */
    @Test
    fun `both open play bands run to their own edges and reach the assist`() {
        for (draw in listOf(0, 899, 995, 999)) {
            val rng = ScriptedDraws(draw.toDouble(), NO_ASSIST_COIN)
            val typed = type(setupOf(attacking()), rng)
            assertEquals(GoalType.OPEN_PLAY, typed.type, "draw $draw")
            assertEquals(2, rng.draws, "draw $draw spends the type draw and the assist coin")
        }
    }

    /**
     * The three bands that draw nothing beyond the type itself, at both of
     * their edges each. A single draw is the proof that the assist was not
     * reached: section 3.7 draws it only for open play.
     */
    @Test
    fun `the penalty, free kick and olympic bands run to their own edges and draw nothing else`() {
        val bands = listOf(
            900 to GoalType.PENALTY,
            949 to GoalType.PENALTY,
            950 to GoalType.FREE_KICK,
            979 to GoalType.FREE_KICK,
            990 to GoalType.OLYMPIC,
            994 to GoalType.OLYMPIC,
        )
        for ((draw, expected) in bands) {
            val rng = ScriptedDraws(draw.toDouble())
            assertEquals(expected, type(setupOf(attacking()), rng).type, "draw $draw")
            assertEquals(1, rng.draws, "draw $draw draws only the type")
        }
    }

    /**
     * The own goal band at both edges. Two draws, the type and the author,
     * and no assist coin between them.
     */
    @Test
    fun `the own goal band runs to its own edges and draws an author instead of an assist`() {
        for (draw in listOf(980, 989)) {
            val rng = ScriptedDraws(draw.toDouble(), pick(0.5))
            val typed = type(setupOf(attacking()), rng)
            assertEquals(GoalType.OWN_GOAL, typed.type, "draw $draw")
            assertEquals(2, rng.draws, "draw $draw spends the type draw and the author draw")
        }
    }

    /**
     * The band table itself, transcribed from section 3.7's own prose rather
     * than read back off the rule set, so a mistyped threshold fails here.
     */
    @Test
    fun `the six bands cover the whole thousand exactly once`() {
        for (draw in 0 until goalTypes.drawBound) {
            val expected = when (draw) {
                in 0..899 -> GoalType.OPEN_PLAY
                in 900..949 -> GoalType.PENALTY
                in 950..979 -> GoalType.FREE_KICK
                in 980..989 -> GoalType.OWN_GOAL
                in 990..994 -> GoalType.OLYMPIC
                else -> GoalType.OPEN_PLAY
            }
            assertEquals(expected, goalTypes.typeOf(draw), "draw $draw")
        }
        assertEquals(1000, goalTypes.drawBound, "the draw is rand(1000)")
    }

    /**
     * Section 3.15 item 13's double count, one type at a time and end to end,
     * so that a call site multiplying by two of its own accord would show up
     * as the wrong number on the event rather than as nothing at all.
     */
    @Test
    fun `open play, a free kick and an olympic goal are worth two, a penalty and an own goal one`() {
        val twice = listOf(
            ScriptedDraws(0.0, NO_ASSIST_COIN) to GoalType.OPEN_PLAY,
            ScriptedDraws(950.0) to GoalType.FREE_KICK,
            ScriptedDraws(990.0) to GoalType.OLYMPIC,
        )
        for ((rng, expected) in twice) {
            val goal = resolve(setupOf(attacking()), rng).goal()
            assertEquals(expected, goal.type)
            assertEquals(2, goal.matchGoalCredits, "$expected is worth two match goals")
        }

        val once = listOf(
            ScriptedDraws(900.0) to GoalType.PENALTY,
            ScriptedDraws(980.0, pick(0.5)) to GoalType.OWN_GOAL,
        )
        for ((rng, expected) in once) {
            val goal = resolve(setupOf(attacking()), rng).goal()
            assertEquals(expected, goal.type)
            assertEquals(1, goal.matchGoalCredits, "$expected is worth one match goal")
        }
    }

    /** The same two counts read straight off the type, where they live. */
    @Test
    fun `the credits are a property of the type and split into the two increments`() {
        assertEquals(1, GoalType.OPEN_PLAY.typingCredit, "open play collects the typing increment")
        assertEquals(1, GoalType.FREE_KICK.typingCredit, "a free kick collects it")
        assertEquals(1, GoalType.OLYMPIC.typingCredit, "an olympic goal collects it")
        assertEquals(0, GoalType.PENALTY.typingCredit, "a penalty never collects the typing increment")
        assertEquals(0, GoalType.OWN_GOAL.typingCredit, "nor does an own goal")
        for (type in GoalType.entries) {
            assertEquals(1, type.scoringCredit, "$type collects the scoreboard increment")
            assertEquals(
                type.typingCredit + type.scoringCredit,
                type.matchGoalCredits,
                "$type total",
            )
        }
    }

    /**
     * A penalty redirected to the designated taker: the report changes hands
     * and the match goal does not. This is the case section 3.7 and open
     * question 57 exist for, so it is asserted on both halves at once rather
     * than only on the author.
     */
    @Test
    fun `a redirected penalty moves the author and leaves the match goal with the finisher`() {
        val setup = setupOf(attacking(DESIGNATED))
        val goal = resolve(setup, ScriptedDraws(900.0)).goal()

        assertEquals(16, goal.author?.slot?.value, "the designated taker is the author")
        assertSame(setup.finisher(), goal.scorer, "the drawn finisher keeps the match goal")
        assertEquals(1, goal.matchGoalCredits, "a penalty is worth one, to the finisher")
        assertEquals(TeamSide.HOME, goal.side)
    }

    /** The same split for a free kick, which is worth two rather than one. */
    @Test
    fun `a redirected free kick moves the author and leaves the match goal with the finisher`() {
        val setup = setupOf(attacking(DESIGNATED))
        val goal = resolve(setup, ScriptedDraws(950.0)).goal()

        assertEquals(16, goal.author?.slot?.value, "the designated taker is the author")
        assertSame(setup.finisher(), goal.scorer, "the drawn finisher keeps the match goal")
        assertEquals(2, goal.matchGoalCredits)
    }

    /** And for an olympic goal, which reads the corner taker rather than the free kick taker. */
    @Test
    fun `a redirected olympic goal moves the author and leaves the match goal with the finisher`() {
        val setup = setupOf(attacking(DESIGNATED))
        val goal = resolve(setup, ScriptedDraws(990.0)).goal()

        assertEquals(9, goal.author?.slot?.value, "the corner taker is the author, not the free kick taker")
        assertSame(setup.finisher(), goal.scorer, "the drawn finisher keeps the match goal")
        assertEquals(2, goal.matchGoalCredits)
    }

    /**
     * A designation only counts while the man is on the pitch. Section 5.6
     * derives it from the whole squad, so a taker who was left out is a real
     * state and not a broken fixture.
     */
    @Test
    fun `a designated player who is not on the pitch is not credited`() {
        val offPitch = Designated(taker = PlayerId(77), cornerTaker = PlayerId(78))
        val setup = setupOf(attacking(offPitch))

        val penalty = resolve(setup, ScriptedDraws(900.0)).goal()
        assertSame(setup.finisher(), penalty.author, "the finisher stays the author of the penalty")

        val olympic = resolve(setup, ScriptedDraws(990.0)).goal()
        assertSame(setup.finisher(), olympic.author, "and of the olympic goal")
    }

    /**
     * The other half of the same rule, and the harder half: a designated man
     * who is in the lineup list but not in a pitch cell.
     *
     * MatchSide's own docstring allows bench entries to sit in the lineup
     * list, and a reserve who has not come on carries the unused substitute
     * cell rather than a pitch one, so this is a shape the type permits
     * rather than an invented one. Checking identity alone would find him
     * and hand him the goal. The case above, where the designation names an
     * identity nobody in the list carries, cannot tell the two checks apart;
     * this one can, and it is what makes the pitch cell half of the
     * predicate load bearing rather than decorative.
     */
    @Test
    fun `a designated player sitting in the list off the pitch is not credited either`() {
        val setup = setupOf(attackingWithBenchedDesignate())

        val penalty = resolve(setup, ScriptedDraws(900.0)).goal()
        assertSame(setup.finisher(), penalty.author, "a benched taker does not take the penalty")

        val freeKick = resolve(setup, ScriptedDraws(950.0)).goal()
        assertSame(setup.finisher(), freeKick.author, "nor the free kick")

        val olympic = resolve(setup, ScriptedDraws(990.0)).goal()
        assertSame(setup.finisher(), olympic.author, "and a benched corner taker does not take the corner")
    }

    /**
     * Section 5.6 says the AI never sets a corner taker, so in practice every
     * olympic goal an AI club scores falls to the drawn finisher. The
     * fixture states that as the AI's own case, a side whose designation
     * carries a free kick taker and no corner taker at all.
     */
    @Test
    fun `an olympic goal by an AI side falls to the finisher, because no corner taker is ever set`() {
        val aiLike = Designated(taker = PlayerId(16), cornerTaker = null)
        val setup = setupOf(attacking(aiLike))
        val goal = resolve(setup, ScriptedDraws(990.0)).goal()

        assertSame(setup.finisher(), goal.author, "no corner taker means the finisher is the author")
        assertSame(setup.finisher(), goal.scorer)
        assertEquals(2, goal.matchGoalCredits)
    }

    /**
     * The own goal, where the two credits point at two different sides at
     * once: the author plays for the side that conceded and the match goal
     * belongs to a forward of the side that scored, who appears nowhere in
     * the report.
     */
    @Test
    fun `an own goal blames a defender and still gives the attacking finisher a match goal`() {
        val setup = setupOf(attacking(DESIGNATED))
        val goal = resolve(setup, ScriptedDraws(980.0, pick(10.0))).goal()

        assertEquals(GoalType.OWN_GOAL, goal.type)
        assertEquals(TeamSide.HOME, goal.side, "the goal still counts for the attacking side")
        assertEquals(3, goal.author?.slot?.value, "the author is a centre back of the conceding side")
        assertTrue(
            setup.away.lineup.any { it === goal.author },
            "the author must be one of the conceding side's own players",
        )
        assertSame(setup.finisher(), goal.scorer, "the attacking finisher still takes the match goal")
        assertEquals(1, goal.matchGoalCredits, "an own goal is worth one, to that finisher")
        assertNull(goal.assister, "an own goal never carries an assist")
    }

    /**
     * Every edge of the own goal weight table, from both sides, over a side
     * built with one player in each band.
     *
     * The running sums are 1, 6, 24, 29, 30, 35, 36 and 37, and the draw is
     * placed a fiftieth of a unit either side of each of the seven edges.
     * The margin is deliberately far tighter than half a unit, because
     * weightedPick scales the draw by the total it computes for itself: a
     * wrong weight moves the total as well as the edge, and a generous
     * margin absorbs both movements at once and passes. A fiftieth does not,
     * and a weight wrong by one anywhere in the table moves at least one of
     * these fourteen cases onto a different man. The expected man is the
     * front of the list in only one of the fourteen and the heaviest in only
     * two, so neither shortcut passes either.
     */
    @Test
    fun `the own goal draw walks the weights of section 3 7 at every band edge`() {
        val edges = listOf(
            1.0 to (1 to 2),
            6.0 to (2 to 3),
            24.0 to (3 to 9),
            29.0 to (9 to 10),
            30.0 to (10 to 11),
            35.0 to (11 to 14),
            36.0 to (14 to 25),
        )
        for ((edge, cells) in edges) {
            val (below, above) = cells
            assertEquals(
                below,
                ownGoalAuthorCell(edge - EDGE_MARGIN),
                "just below the running sum $edge the draw must still land on cell $below",
            )
            assertEquals(
                above,
                ownGoalAuthorCell(edge + EDGE_MARGIN),
                "just past the running sum $edge the draw must move on to cell $above",
            )
        }
    }

    /** The table itself, cell by cell, transcribed from section 3.7's own prose. */
    @Test
    fun `the own goal weight table matches section 3 7 cell by cell`() {
        for (cell in 1..25) {
            val expected = when (cell) {
                1 -> 1
                2 -> 5
                in 3..8 -> 18
                9 -> 5
                10 -> 1
                in 11..13 -> 5
                else -> 1
            }
            assertEquals(expected, goalTypes.ownGoalSlotWeights[cell], "cell $cell")
        }
        assertEquals(1..25, goalTypes.ownGoalEligibleSlots, "only pitch cells can be blamed")
    }

    /**
     * The order of section 3.7's patches, pinned by the one case where a
     * different order gives a different answer.
     *
     * An own goal whose conceding side has nobody on the pitch becomes an
     * open play goal. The assist is drawn before that patch, and only for a
     * goal drawn as open play, so this goal never gets one. An
     * implementation that drew the assist after the patches would find an
     * open play goal here and give it an assister; the draw count is what
     * proves the coin was never even tossed.
     */
    @Test
    fun `an own goal that falls back to open play has no assister, because the assist came first`() {
        val setup = setupOf(attacking(), concedingNobody())
        val rng = ScriptedDraws(980.0)
        val typed = type(setup, rng)

        assertEquals(GoalType.OPEN_PLAY, typed.type, "nobody to blame turns the own goal into open play")
        assertNull(typed.assister, "the assist was already skipped when the type changed")
        assertEquals(1, rng.draws, "only the type was drawn: no author, and no assist coin")
        assertSame(setup.finisher(), typed.author, "the finisher stays the author")
    }

    /**
     * The other half of the same argument: a goal drawn as open play does
     * toss the coin, in the same fixture, so the case above is the patch
     * order and not simply an assist that never happens here.
     */
    @Test
    fun `a goal drawn as open play does reach the assist in the same fixture`() {
        val setup = setupOf(attacking(), concedingNobody())
        val rng = ScriptedDraws(0.0, ASSIST_COIN, pick(0.0))
        val typed = type(setup, rng)

        assertEquals(GoalType.OPEN_PLAY, typed.type)
        assertNotNull(typed.assister, "the coin passed, so somebody was drawn")
        assertEquals(3, rng.draws, "the type, the coin and the weighted walk")
    }

    /**
     * The credit an own goal that fell back to open play carries.
     *
     * Section 3.15 item 13 takes the first increment at the start of the type
     * draw, before section 3.7's patches run, so it is the drawn type that
     * decides it. This goal was drawn as an own goal and collects nought
     * there, then one more when it reaches the score, for one in total,
     * even though the type on the event says open play.
     */
    @Test
    fun `the typing increment follows the type as drawn, not the type after the patches`() {
        val setup = setupOf(attacking(), concedingNobody())
        val goal = resolve(setup, ScriptedDraws(980.0)).goal()

        assertEquals(GoalType.OPEN_PLAY, goal.type)
        assertEquals(
            1,
            goal.matchGoalCredits,
            "drawn as an own goal, so the typing increment was nought and only the scoreboard one counts",
        )
    }

    /**
     * An AI versus AI penalty goes straight onto the scoreboard. This is the
     * control for the human sided case below: without it, a wiring that sent
     * every penalty to section 3.10 would pass that one.
     */
    @Test
    fun `an AI versus AI penalty is added to the score without section 3 10`() {
        val resolved = resolve(setupOf(attacking()), ScriptedDraws(900.0))

        assertTrue(resolved.scored, "the goal counts")
        assertTrue(resolved.onTarget)
        assertTrue(
            resolved.events.none { it is MatchEvent.InteractivePenalty },
            "no interactive penalty in an AI versus AI match",
        )
    }

    /**
     * A penalty in a match with a human managed club, converted. The goal is
     * worth one rather than two, because neither of section 3.15 item 13's
     * increments happens: section 3.10's viewer adds the only one there is.
     */
    @Test
    fun `a human sided penalty goes through section 3 10 and a conversion is worth one`() {
        val setup = setupOf(attacking(DESIGNATED, human = true))
        val resolved = resolve(setup, ScriptedDraws(900.0, CONVERTED_COIN))

        val kick = resolved.events.filterIsInstance<MatchEvent.InteractivePenalty>().single()
        assertEquals(16, kick.taker?.slot?.value, "the designated taker takes the kick")
        assertEquals(1, kick.keeper?.slot?.value, "the conceding side's keeper faces it")
        assertTrue(kick.scored)

        val goal = resolved.goal()
        assertTrue(resolved.scored, "a converted interactive penalty is still a goal")
        assertEquals(16, goal.author?.slot?.value)
        assertEquals(16, goal.scorer?.slot?.value, "section 3.10 gives its one goal to the man who took it")
        assertEquals(1, goal.matchGoalCredits)
    }

    /** A missed one is not a goal at all, and leaves the attempt as a shot that did not score. */
    @Test
    fun `a human sided penalty that is missed takes the goal off the scoreboard`() {
        val setup = setupOf(attacking(DESIGNATED, human = true))
        val resolved = resolve(setup, ScriptedDraws(900.0, MISSED_COIN, SAVED_MISS))

        assertTrue(!resolved.scored, "a missed penalty is not a goal")
        assertTrue(resolved.onTarget, "a saved penalty still counts on target")
        assertTrue(resolved.events.none { it is MatchEvent.Goal }, "and there is no goal to log")

        val kick = resolved.events.filterIsInstance<MatchEvent.InteractivePenalty>().single()
        assertTrue(kick.keeperSaved, "the keeper is credited with the save")
    }

    /** Section 3.7 says either side being human is enough, whoever the goal belongs to. */
    @Test
    fun `a penalty scored against the human side goes through section 3 10 too`() {
        val setup = setupOf(attacking(), concedingBands(human = true))
        val resolved = resolve(setup, ScriptedDraws(900.0, CONVERTED_COIN))

        assertEquals(
            1,
            resolved.events.filterIsInstance<MatchEvent.InteractivePenalty>().size,
            "the human side is the one conceding, and that is still a human sided match",
        )
    }

    /** Only a penalty takes the detour. A free kick in the same match does not. */
    @Test
    fun `no type other than a penalty is handed to section 3 10`() {
        val setup = setupOf(attacking(DESIGNATED, human = true))
        for (draw in listOf(0.0 to NO_ASSIST_COIN, 950.0 to null, 990.0 to null)) {
            val script = listOfNotNull(draw.first, draw.second).toDoubleArray()
            val resolved = resolve(setup, ScriptedDraws(*script))
            assertTrue(
                resolved.events.none { it is MatchEvent.InteractivePenalty },
                "type draw ${draw.first} must not reach section 3.10",
            )
            assertTrue(resolved.scored, "type draw ${draw.first} is a goal")
        }
    }

    /**
     * The whole match, wired. Every goal on the scoreboard has exactly one
     * Goal event behind it and every Goal event has a shot in the same
     * minute, which is what says the typing did not invent or lose one.
     */
    @Test
    fun `a played match logs one goal event per goal on the scoreboard`() {
        val home = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
        val away = Lineups.sideOfSlots(
            Lineups.FORMATION_4_4_2,
            strength = 50,
            context = Lineups.context(isHome = false),
        )
        val setup = MatchSetup(home = home, away = away, season = 1, rules = rules)

        var typed = 0
        for (seed in 1L..200L) {
            val report = simulateMatch(setup, SplitMix64Rng(seed))
            val goals = report.log.filterIsInstance<MatchEvent.Goal>()
            typed += goals.size
            assertEquals(
                report.homeGoals + report.awayGoals,
                goals.size,
                "seed $seed logged ${goals.size} goal events for ${report.homeGoals + report.awayGoals} goals",
            )
            assertTrue(
                report.log.none { it is MatchEvent.InteractivePenalty },
                "seed $seed has no human side, so no penalty may be handed to section 3.10",
            )
            for (goal in goals) {
                assertTrue(
                    report.log.any { it is MatchEvent.Shot && it.minute == goal.minute && it.scored },
                    "seed $seed logged a goal in minute ${goal.minute} with no scoring shot",
                )
            }
        }
        assertTrue(typed > 0, "two hundred matches must produce some goals to type at all")
    }

    /**
     * The same over a human sided match, which is the only way section 3.10's
     * interactive path is reached at all. Five per cent of goals type as
     * penalties, so a few hundred matches produce a handful of them, and each
     * one must leave the scoreboard agreeing with the log whichever way it
     * went.
     */
    @Test
    fun `a human sided match reaches section 3 10 and keeps the scoreboard honest`() {
        val home = Lineups.side(
            Lineups.FORMATION_4_4_2.map { Lineups.player(it, strength = 50) },
            humanManaged = true,
        )
        val away = Lineups.sideOfSlots(
            Lineups.FORMATION_4_4_2,
            strength = 50,
            context = Lineups.context(isHome = false),
        )
        val setup = MatchSetup(home = home, away = away, season = 1, rules = rules)

        var kicks = 0
        var converted = 0
        for (seed in 1L..400L) {
            val report = simulateMatch(setup, SplitMix64Rng(seed))
            val penalties = report.log.filterIsInstance<MatchEvent.InteractivePenalty>()
            kicks += penalties.size
            converted += penalties.count { it.scored }

            assertEquals(
                report.homeGoals + report.awayGoals,
                report.log.filterIsInstance<MatchEvent.Goal>().size,
                "seed $seed",
            )
            assertEquals(
                report.homeGoals,
                report.stats.home.goals,
                "seed $seed home scoreboard against the shot log",
            )
            assertEquals(
                report.awayGoals,
                report.stats.away.goals,
                "seed $seed away scoreboard against the shot log",
            )
            assertEquals(
                report.clock.totalMinutes,
                report.stats.home.shots + report.stats.away.shots +
                    report.stats.home.tackles + report.stats.away.tackles +
                    report.stats.home.misplacedPasses + report.stats.away.misplacedPasses,
                "seed $seed must still produce exactly one countable event a minute",
            )
        }
        assertTrue(kicks > 0, "four hundred human sided matches must reach section 3.10 at least once")
        assertTrue(converted < kicks, "and must miss at least one of them")
    }

    /** The cell the own goal draw lands on for a given position along the running sum. */
    private fun ownGoalAuthorCell(position: Double): Int {
        val defending = concedingBands()
        val author = drawOwnGoalAuthor(defending, rules, ScriptedDraws(pick(position)))
        return assertNotNull(author).slot.value
    }

    /** Turns a position along the 37 unit running sum into the draw weightedPick consumes. */
    private fun pick(position: Double) = position / OWN_GOAL_BAND_TOTAL

    private companion object {

        /** One cell in each of section 3.7's seven own goal weight bands, in list order. */
        val OWN_GOAL_BAND_CELLS = listOf(1, 2, 3, 9, 10, 11, 14, 25)

        /** Their weights added up by hand: 1 + 5 + 18 + 5 + 1 + 5 + 1 + 1. */
        const val OWN_GOAL_BAND_TOTAL = 37.0

        /**
         * How far either side of a running sum the boundary draws sit, in
         * units of that sum.
         */
        const val EDGE_MARGIN = 0.02

        /** Cell 16 takes free kicks and penalties, cell 9 takes corners. */
        val DESIGNATED = Designated(taker = PlayerId(16), cornerTaker = PlayerId(9))

        /** rand(100) above 80 is section 3.6's nineteen per cent with no assister. */
        const val NO_ASSIST_COIN = 81.0

        /** And anything at 80 or under goes on to the weighted walk. */
        const val ASSIST_COIN = 0.0

        /**
         * Section 3.10 compares rand(1..100) against the threshold, which is
         * 70 for two players carrying nothing, so the raw draw of 69 is the
         * coin of 70 that converts and 70 is the 71 that does not.
         */
        const val CONVERTED_COIN = 69.0

        const val MISSED_COIN = 70.0

        /** The first of section 3.10's seven miss outcomes, the ones that credit a save. */
        const val SAVED_MISS = 0.0
    }
}
