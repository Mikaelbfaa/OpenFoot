package org.openfoot.engine.match

import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.Rng
import org.openfoot.model.RuleSets
import org.openfoot.model.Slot
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The loop is exercised with a real generator, because what is worth asserting
 * here is that it holds together over a whole match: the counters agree with
 * each other, the length obeys the clock, and the same seed gives the same
 * match.
 */
class MatchSimulationTest {

    /**
     * Three reserves, one for each part of the pitch, so that whichever cell a
     * window vacates has somebody the search of section 5.4 can put in it.
     */
    private fun reserves(): List<MatchPlayer> = listOf(
        Lineups.player(
            slot = Slot.UNUSED_SUBSTITUTE.value,
            strength = 70,
            id = 30,
            position = Position.MIDFIELDER,
            style = PlayerStyle.DEFENSIVE,
        ),
        Lineups.player(
            slot = Slot.UNUSED_SUBSTITUTE.value,
            strength = 65,
            id = 31,
            position = Position.CENTREBACK,
            style = PlayerStyle.DEFENSIVE,
        ),
        Lineups.player(
            slot = Slot.UNUSED_SUBSTITUTE.value,
            strength = 60,
            id = 32,
            position = Position.FORWARD,
            style = PlayerStyle.OFFENSIVE,
        ),
    )

    private fun setup(homeStrength: Int = 50, awayStrength: Int = 50, season: Int = 1) =
        MatchSetup(
            home = Lineups.sideOfSlots(
                Lineups.FORMATION_4_4_2,
                homeStrength,
                context = Lineups.context(isHome = true),
            ),
            away = Lineups.sideOfSlots(
                Lineups.FORMATION_4_4_2,
                awayStrength,
                context = Lineups.context(isHome = false),
            ),
            season = season,
            rules = RuleSets.CLASSIC,
        )

    /**
     * fixture is built once and shared by both calls below, rather than one
     * MatchSetup per call. MatchPlayer is deliberately reference equal, not
     * value equal (see MatchSide.kt), so two independently built lineups
     * would make first and second compare unequal on their shooters even
     * when the match played out identically, and two independently built
     * lineups would equally make them compare unequal to each other even
     * when it did not: either way the assertion could not tell a genuine
     * divergence from an artefact of building two setups. One shared fixture
     * means every shooter the log can name is the same object on both runs,
     * so the comparison is honest about what the simulation actually did.
     */
    @Test
    fun `the same seed plays the same match`() {
        val fixture = setup()
        val first = simulateMatch(fixture, SplitMix64Rng(7))
        val second = simulateMatch(fixture, SplitMix64Rng(7))
        assertEquals(first, second)
    }

    /**
     * Shares one fixture across all forty seeds for the same reason as
     * above, in the other direction: with one setup per seed, results would
     * always come out pairwise unequal by shooter reference alone, no matter
     * what the forty seeds actually simulated, so toSet().size > 1 could
     * never fail. One shared fixture makes an actual collision possible
     * again, so the assertion is a genuine claim about the forty seeds.
     */
    @Test
    fun `a different seed plays a different match`() {
        val fixture = setup()
        val results = (1L..40L).map { simulateMatch(fixture, SplitMix64Rng(it)) }
        assertTrue(results.toSet().size > 1, "forty seeds produced one identical match")
    }

    /**
     * Finding 1. rng is a seed source that simulateMatch forks once and never
     * consumes further, so passing the SAME instance to two calls replays the
     * identical match both times: nothing about the first call's draws moves
     * what the second call sees. This is the footgun the docstring now warns
     * about, pinned so a future change that starts threading the parent's
     * state through the loop is caught here instead of by a silent repeated
     * fixture later.
     *
     * fixture is shared for the same reason as the same seed case above: two
     * independently built lineups would make first and second compare
     * unequal by shooter reference even when the replay was exact.
     */
    @Test
    fun `passing the same Rng instance to two calls replays the identical match`() {
        val fixture = setup()
        val sharedRng = SplitMix64Rng(11)

        val first = simulateMatch(fixture, sharedRng)
        val second = simulateMatch(fixture, sharedRng)

        assertEquals(first, second, "the same Rng instance must not make the second call diverge")
    }

    /**
     * Finding 1's other half: the correct pattern. A caller simulating several
     * matches from one generator, such as a round of fixtures, must fork a
     * fresh child per match rather than pass the parent instance twice, and
     * doing so does give different matches.
     *
     * fixture is shared for the same reason as the different seed case
     * above: with one setup per call, first != second would hold by shooter
     * reference alone regardless of whether forking a fresh child actually
     * changed anything, so the assertion could never fail. Sharing it makes
     * first != second a genuine claim about the fork.
     */
    @Test
    fun `forking a fresh child per match gives different matches`() {
        val fixture = setup()
        val seasonRng = SplitMix64Rng(11)

        val first = simulateMatch(fixture, seasonRng.fork(1L))
        val second = simulateMatch(fixture, seasonRng.fork(2L))

        assertTrue(first != second, "forking per match should not reproduce the same fixture twice")
    }

    @Test
    fun `the match runs exactly as many ticks as the clock says`() {
        repeat(200) { seed ->
            val result = simulateMatch(setup(), SplitMix64Rng(seed.toLong()))
            val ticks = result.stats.home.possessionsWon + result.stats.away.possessionsWon
            assertEquals(result.clock.totalMinutes, ticks, "seed $seed")
        }
    }

    @Test
    fun `every match lasts between ninety one and ninety seven minutes`() {
        repeat(500) { seed ->
            val minutes = simulateMatch(setup(), SplitMix64Rng(seed.toLong())).clock.totalMinutes
            assertTrue(minutes in 91..97, "seed $seed ran $minutes minutes")
        }
    }

    @Test
    fun `shots equal on target plus wide for both sides`() {
        repeat(200) { seed ->
            val stats = simulateMatch(setup(), SplitMix64Rng(seed.toLong())).stats
            listOf(stats.home, stats.away).forEach { side ->
                assertEquals(side.shots, side.onTarget + side.wide, "seed $seed")
            }
        }
    }

    @Test
    fun `goals never exceed shots on target`() {
        repeat(200) { seed ->
            val stats = simulateMatch(setup(), SplitMix64Rng(seed.toLong())).stats
            assertTrue(stats.home.goals <= stats.home.onTarget, "seed $seed")
            assertTrue(stats.away.goals <= stats.away.onTarget, "seed $seed")
        }
    }

    @Test
    fun `the scoreline agrees with the counters`() {
        repeat(200) { seed ->
            val result = simulateMatch(setup(), SplitMix64Rng(seed.toLong()))
            assertEquals(result.stats.home.goals, result.homeGoals, "seed $seed")
            assertEquals(result.stats.away.goals, result.awayGoals, "seed $seed")
        }
    }

    @Test
    fun `no side ever counts more than the whole match in tackles and passes`() {
        repeat(200) { seed ->
            val result = simulateMatch(setup(), SplitMix64Rng(seed.toLong()))
            val events = with(result.stats) {
                home.shots + away.shots +
                    home.tackles + away.tackles +
                    home.misplacedPasses + away.misplacedPasses
            }
            assertEquals(result.clock.totalMinutes, events, "seed $seed")
        }
    }

    @Test
    fun `fouls stay at zero over a whole match`() {
        val stats = simulateMatch(setup(), SplitMix64Rng(3)).stats
        assertEquals(0, stats.home.fouls)
        assertEquals(0, stats.away.fouls)
    }

    @Test
    fun `a much stronger side outshoots a much weaker one over many matches`() {
        val strong = (1L..300L).sumOf {
            simulateMatch(setup(homeStrength = 90, awayStrength = 30), SplitMix64Rng(it))
                .stats.home.shots
        }
        val weak = (1L..300L).sumOf {
            simulateMatch(setup(homeStrength = 90, awayStrength = 30), SplitMix64Rng(it))
                .stats.away.shots
        }
        assertTrue(strong > weak, "strong side took $strong shots against $weak")
    }

    /**
     * Finding 1's replacement for the tautological alternation test. That test
     * recomputed the expected possessor from startingPossessor and totalMinutes
     * alone, using the very formula the loop is supposed to follow, so it would
     * stay green even if the loop's alternation became conditional on the tick's
     * outcome. This test instead forces every tick in the whole match to score a
     * goal, and observes the resulting split.
     *
     * GOAL_WINNING_DRAW is derived below to sit under every boundary the shot,
     * chance and possession weightedPick calls can produce at two equal
     * strengths of fifty, so at this constant the possessor always wins the
     * possession duel, always works a chance, and always scores. Every tick is
     * therefore a goal for whoever is in possession that tick, and under
     * section 3.5's unconditional alternation the two sides take turns, so the
     * goals split between them differing by at most one. If alternation were
     * instead conditional on winning the duel, the possessor would never change
     * and one side would score every single minute while the other stayed at
     * zero, so this fails loudly under that regression.
     */
    @Test
    fun `every tick scores for whoever is in possession, and the goals split within one`() {
        val result = simulateMatch(setup(), FixedRng(GOAL_WINNING_DRAW))

        val homeGoals = result.stats.home.goals
        val awayGoals = result.stats.away.goals

        assertEquals(
            result.clock.totalMinutes,
            homeGoals + awayGoals,
            "expected every one of the ${result.clock.totalMinutes} ticks to score, " +
                "got $homeGoals home and $awayGoals away",
        )
        assertTrue(
            abs(homeGoals - awayGoals) <= 1,
            "unconditional alternation should split the goals within one of each " +
                "other, got $homeGoals home and $awayGoals away",
        )
        assertTrue(
            homeGoals > 0 && awayGoals > 0,
            "one side scored every tick and the other never did: $homeGoals-$awayGoals",
        )
    }

    /**
     * Finding 2a's decisive case, stated exactly as the review found it: a
     * TACKLE credited to the possessor instead of to the side that did not have
     * the ball would pass every other test in this file, because they all use
     * two equal strength sides where either crediting produces plausible
     * numbers. Ported from the record fold this project used to keep beside
     * the log onto events() plus toStats(), the pair that replaced it: this is
     * the only test in the suite that pins TickOutcome.events()'s own mapping
     * from a possessor to the side a tackle lands on, as distinct from
     * MatchEventTest, which starts from hand built MatchEvent values and never
     * touches events() at all. Both possessors are checked so a bug that is
     * symmetric in TeamSide, such as always crediting home, cannot hide behind
     * one case.
     */
    @Test
    fun `events credits a tackle to the side that did not have the ball`() {
        val awayHadTheBall = TickOutcome(TeamSide.HOME, TeamSide.HOME, TickEvent.TACKLE).events(0).toStats()
        assertEquals(0, awayHadTheBall.home.tackles, "the possessor must not be credited with the tackle")
        assertEquals(1, awayHadTheBall.away.tackles, "the non possessor should be credited with the tackle")

        val homeHadTheBall = TickOutcome(TeamSide.AWAY, TeamSide.AWAY, TickEvent.TACKLE).events(0).toStats()
        assertEquals(1, homeHadTheBall.home.tackles, "the non possessor should be credited with the tackle")
        assertEquals(0, homeHadTheBall.away.tackles, "the possessor must not be credited with the tackle")
    }

    /**
     * Finding 2a in full, ported the same way: every one of the five
     * TickEvent values, run through events() and toStats() for both
     * possessors, checked against exactly the side TickOutcome.events()'s own
     * docstring names. GOAL, SAVE, WIDE and MISPLACED_PASS all belong to the
     * possessor; TACKLE alone belongs to the side that did not have the ball.
     * Each case also checks that the other side's matching counter stayed at
     * zero, so a bug that credits both sides at once cannot hide either.
     */
    @Test
    fun `events credits every event to exactly the side its docstring names`() {
        for (possessor in listOf(TeamSide.HOME, TeamSide.AWAY)) {
            val defender = possessor.opponent

            val afterGoal = TickOutcome(possessor, possessor, TickEvent.GOAL).events(0).toStats()
            assertEquals(1, afterGoal.of(possessor).goals, "GOAL: possessor $possessor")
            assertEquals(1, afterGoal.of(possessor).shots, "GOAL: possessor $possessor")
            assertEquals(1, afterGoal.of(possessor).onTarget, "GOAL: possessor $possessor")
            assertEquals(0, afterGoal.of(defender).goals, "GOAL must not touch the defender: possessor $possessor")
            assertEquals(0, afterGoal.of(defender).shots, "GOAL must not touch the defender: possessor $possessor")

            val afterSave = TickOutcome(possessor, possessor, TickEvent.SAVE).events(0).toStats()
            assertEquals(1, afterSave.of(possessor).shots, "SAVE: possessor $possessor")
            assertEquals(1, afterSave.of(possessor).onTarget, "SAVE: possessor $possessor")
            assertEquals(0, afterSave.of(possessor).goals, "SAVE must not score: possessor $possessor")
            assertEquals(0, afterSave.of(defender).shots, "SAVE must not touch the defender: possessor $possessor")

            val afterWide = TickOutcome(possessor, possessor, TickEvent.WIDE).events(0).toStats()
            assertEquals(1, afterWide.of(possessor).shots, "WIDE: possessor $possessor")
            assertEquals(1, afterWide.of(possessor).wide, "WIDE: possessor $possessor")
            assertEquals(0, afterWide.of(defender).shots, "WIDE must not touch the defender: possessor $possessor")

            val afterTackle = TickOutcome(possessor, possessor, TickEvent.TACKLE).events(0).toStats()
            assertEquals(0, afterTackle.of(possessor).tackles, "TACKLE must not credit the possessor: $possessor")
            assertEquals(1, afterTackle.of(defender).tackles, "TACKLE: defender of $possessor")

            val afterMisplaced = TickOutcome(possessor, possessor, TickEvent.MISPLACED_PASS).events(0).toStats()
            assertEquals(1, afterMisplaced.of(possessor).misplacedPasses, "MISPLACED_PASS: possessor $possessor")
            assertEquals(
                0,
                afterMisplaced.of(defender).misplacedPasses,
                "MISPLACED_PASS must not touch the defender: possessor $possessor",
            )
        }
    }

    /**
     * Finding 4. Tick.kt's own docstring on playTick already argues the
     * goal count passed in must be the possessor's own, not the opponent's,
     * but that argument does not run: playTick takes whatever Int it is
     * handed, so a swap at the call site in simulateMatch is invisible to
     * every test that calls playTick directly. Only a whole match, run
     * through simulateMatch, can catch a swap there.
     *
     * LadderProofRng makes the home side win every duel and take every shot
     * on its own ticks, and lose the duel outright on the away side's ticks,
     * so away never manages a shot and its goal total sits at zero for the
     * whole match. Under the correct wiring, home's OWN goal total climbs as
     * it scores, and by the time it reaches five the anti blowout ladder has
     * tightened the goal weight enough that HOME_SHOT_DRAW no longer lands in
     * the goal bucket (worked out below), so home's total stops climbing
     * short of the number of ticks it played. Under the inverted wiring, home
     * would instead read AWAY's total, which never leaves zero, so the ladder
     * never engages and home scores on every single one of its ticks.
     */
    @Test
    fun `the anti blowout ladder caps the possessor's own goals, not the opponent's`() {
        val result = simulateMatch(setup(), LadderProofRng(scoring = false))

        assertEquals(
            TeamSide.HOME,
            result.startingPossessor,
            "LadderProofRng's nextInt always returns zero, so kickoff must go to HOME",
        )
        val homeTicks = (0 until result.clock.totalMinutes).count { it % 2 == 0 }

        assertEquals(0, result.stats.away.goals, "away loses the duel on every one of its ticks and never shoots")
        assertTrue(result.stats.home.goals > 0, "home must score at least once before the ladder can cap it")
        assertTrue(
            result.stats.home.goals < homeTicks,
            "the anti blowout ladder should have stopped home scoring on every one of its $homeTicks " +
                "ticks, but it finished with ${result.stats.home.goals} goals",
        )
    }

    /**
     * Finding 1 of the review of section 3.8's wiring, as an executable case.
     *
     * simulateMatch draws the match's pair of substitution plans as soon as
     * either side has a bench, and section 3.8 draws their minutes without
     * replacement by redrawing until it lands on a free one. FixedRng's nextInt
     * never changes, so an unbounded redraw against it never ends: before
     * drawFresh was capped, adding a bench to any case in this file hung the
     * whole build with no failing assertion to point at. It is capped at the
     * width of the window squared now and falls back on the first free minute,
     * so this finishes, and the double counts its own draws so that a future
     * unbounded loop fails here rather than hanging.
     *
     * The assertion that a substitution actually happened is what makes this a
     * test of the bench path rather than only of termination: with every draw
     * at nought the plan's routine minutes land late in the second half, by
     * which time section 3.9's drain has taken everybody under the tiredness
     * threshold that applies after the fortieth minute of the half.
     */
    @Test
    fun `a match with a bench finishes against a generator whose draws never change`() {
        val result = simulateMatch(setup(), FixedRng(GOAL_WINNING_DRAW), reserves(), reserves())

        val duels = result.stats.home.possessionsWon + result.stats.away.possessionsWon
        assertEquals(result.clock.totalMinutes, duels, "the match has to have run to the whistle")
        assertTrue(
            result.log.any { it is MatchEvent.Substitution },
            "the plan was drawn and never used, so this pins termination and nothing else",
        )
    }

    @Test
    fun `every minute logs exactly one possession duel`() {
        val report = simulateMatch(setup(), SplitMix64Rng(99L))
        val duels = report.log.count { it is MatchEvent.PossessionWon }
        assertEquals(report.clock.totalMinutes, duels, "one duel per minute")
    }

    @Test
    fun `the goals in the log are the goals in the score`() {
        val report = simulateMatch(setup(), SplitMix64Rng(99L))
        val scored = report.log.filterIsInstance<MatchEvent.Shot>().filter { it.scored }
        assertEquals(report.homeGoals, scored.count { it.side == TeamSide.HOME }, "home")
        assertEquals(report.awayGoals, scored.count { it.side == TeamSide.AWAY }, "away")
    }
}

/**
 * A draw so low it wins the first outcome of every weightedPick call the
 * engine makes at two equal strengths of fifty. Derived at the tightest point
 * the match can reach:
 *
 * The shot resolution's GOAL boundary at a fresh scoreline, possessor at home,
 * under CLASSIC, is base weight 5.5 against a total of 62.605 (5.5 * 1.0 +
 * 35.55 * 1.1 + 15.0 * 1.2, the 1.1 and 1.2 coming from the home shot rule's
 * plus one tenth on saved and plus two tenths on wide). That boundary only
 * gets tighter as the anti blowout ladder bites: once the possessing side has
 * scored six or more, CLASSIC's last ladder rung fixes the goal weight at 0.5
 * against a total of 63.105 (0.5 * 1.0 + 40.55 * 1.1 + 15.0 * 1.2), a boundary
 * of about 0.0079. No later rung exists, so that is the tightest the whole
 * match can ever produce, on either side, however many goals a side reaches
 * in a ninety one to ninety seven minute match. The possession duel's and the
 * chance duel's boundaries are far looser (about 0.61 and 0.57 for the side
 * at home, since both only add the home duel bonus on top of an even
 * strength comparison), so the shot ladder's floor governs.
 *
 * 0.001 sits safely under 0.0079, with roughly an eightfold margin, so the
 * possessor wins the possession duel, wins the chance duel and scores on
 * every tick, for both sides, for as many goals as either side can reach.
 */
private const val GOAL_WINNING_DRAW = 0.001

/**
 * Test double whose draws never move. nextDouble always returns the same
 * constant and fork always returns this same instance, so a whole match run
 * through it is fully determined by that one constant: every minuteRng, every
 * PLAY_STREAM fork and every duel draw inside a tick all resolve to it.
 * nextInt always returns zero, which keeps the once per match draws (the
 * starting possessor and both halves' stoppage) fixed too, so the match
 * length is deterministic as well.
 *
 * A generator that returns one value for every bound is not a generator, and
 * the engine is entitled to assume it will eventually see a different one. The
 * draw counter below is what keeps that assumption from turning into a hang:
 * a loop that redraws until the value changes would spin here forever and the
 * build would never finish, with no failing assertion to point at. The limit
 * is far above what any legal match draws, which is three once per match plus
 * about four a minute, and far below a loop that is not going to stop.
 */
private class FixedRng(private val value: Double) : Rng {

    private var draws = 0

    override fun nextBits(): Long = 0L

    override fun nextInt(bound: Int): Int {
        check(draws++ < DEGENERATE_DRAW_LIMIT) {
            "a double that returns nought for every bound was asked for more than " +
                "$DEGENERATE_DRAW_LIMIT integers, so something is looping on a draw that " +
                "will never change"
        }
        return 0
    }

    override fun nextDouble(): Double = value

    override fun fork(tag: Long): Rng = this
}

/**
 * How many integer draws a double that returns one constant will answer before
 * it decides it is being looped on.
 *
 * The play of a match draws three integers once and about four a minute over
 * at most ninety seven minutes, so four hundred or so, plus at most one a
 * minute for a side's substitution window.
 *
 * A bench costs far more than that against a double like this, and the reason
 * is worth stating because it is what sets the limit. Section 3.8 draws its
 * minutes without replacement, and against a generator whose value never
 * changes every draw after the first of a pool collides, so each of them burns
 * that pool's whole cap, which is the width squared. One match shuffles five
 * pools far enough for both sides to read them, twenty two minutes in all: six
 * of nineteen to thirty eight, four of each of the three routine pools and
 * four of forty three to forty seven. That is about three thousand eight
 * hundred draws for the pair of plans, once for the match rather than once a
 * side. A benched match through such a double therefore spends between four
 * thousand one hundred and four thousand two hundred draws in total, measured
 * rather than estimated, by bracketing this constant.
 *
 * Ten thousand leaves roughly twice that headroom and is still instantly below
 * anything unbounded. Raise it if a window in some rule set is ever widened
 * enough to matter, and measure again rather than guessing.
 */
private const val DEGENERATE_DRAW_LIMIT = 10_000

/**
 * Draw home reads for every call within one of its own ticks: possession duel,
 * chance duel, shooter selection and shot outcome all resolve to it.
 *
 * Individual abilities are off by default (see Lineups), so every player on
 * both sides rates strength divided by ten, 5.0 here, regardless of slot or
 * which one weightedPick's low draw happens to land on. Possessor and
 * defender are therefore always level, so strengthDifference is zero
 * everywhere and only the home duel bonus and the home shot rule's delta move
 * a weight away from its base multiplier of 1.0.
 *
 * That fixes the shot resolution's multipliers at saved 1.1, wide 1.2 (the
 * home shot rule adds delta 0.1 to saved, then delta 0.1 again on top for
 * wide), and CLASSIC's anti blowout ladder walks the goal weight down as
 * home's own goal count climbs:
 *
 *   goals 0 to 2: base 5.5, total 5.5 + 39.105 + 18.0 = 62.605, boundary 0.0879
 *   goals 3 to 4: base 4.5, total 4.5 + 44.605 + 18.0 = 67.105, boundary 0.0671
 *   goals 5:      base 3.0, total 3.0 + 44.605 + 18.0 = 65.605, boundary 0.0457
 *   goals 6 up:   base 0.5, total 0.5 + 44.605 + 18.0 = 63.105, boundary 0.0079
 *
 * 0.05 sits under the first two boundaries and over the third, so home scores
 * on every tick until its own total reaches five, then stops, well inside a
 * match that plays several dozen home ticks. The possession and chance duel
 * boundaries are far looser (about 0.61 and 0.57, only the home duel bonus on
 * an even strength comparison), so 0.05 clears both of those too and every
 * home tick reaches the shot.
 */
private const val HOME_SHOT_DRAW = 0.05

/**
 * Draw the away side reads for every call within one of its own ticks. 0.99
 * is safely above the possession duel's boundary of 0.5 (equal strength, no
 * home bonus away from home), so away loses the duel outright, every tick,
 * and never reaches a chance, a shooter or a shot. Its goal and shot counters
 * both therefore stay at zero for the whole match.
 */
private const val AWAY_LOSES_DUEL_DRAW = 0.99

/**
 * Finding 4's mutation proof double.
 *
 * Mirrors Rng.fork's real contract, that a child depends only on the origin
 * and the tag, by making a child's flavour depend only on this instance's
 * flavour and the tag's parity: even leaves it alone, odd flips it. Chaining
 * that rule down exactly the path simulateMatch forks (matchRng from
 * SeedDomain.MATCH, then minuteRng from the minute index, then playRng from
 * PLAY_STREAM) makes the flavour that finally reaches a tick's draws track
 * the minute's parity, because SeedDomain.MATCH is even and PLAY_STREAM is
 * odd: an even minute keeps this instance's starting flavour once, then
 * flips it once more for PLAY_STREAM; an odd minute flips it once for the
 * minute and flips it back for PLAY_STREAM. Starting from scoring = false
 * lands every even minute (HOME's ticks, since nextInt always returns zero
 * and kickoff therefore always goes to HOME) on scoring = true, and every odd
 * minute (AWAY's ticks) on scoring = false.
 */
private class LadderProofRng(private val scoring: Boolean) : Rng {

    private var draws = 0

    override fun nextBits(): Long = 0L

    /**
     * Nought for every bound, and capped for the same reason FixedRng's is: a
     * value that never changes must fail a loop that waits for it to change,
     * not hang one.
     */
    override fun nextInt(bound: Int): Int {
        check(draws++ < DEGENERATE_DRAW_LIMIT) {
            "a double that returns nought for every bound was asked for more than " +
                "$DEGENERATE_DRAW_LIMIT integers, so something is looping on a draw that " +
                "will never change"
        }
        return 0
    }

    override fun nextDouble(): Double = if (scoring) HOME_SHOT_DRAW else AWAY_LOSES_DUEL_DRAW

    override fun fork(tag: Long): Rng = LadderProofRng(if (tag % 2L == 0L) scoring else !scoring)
}
