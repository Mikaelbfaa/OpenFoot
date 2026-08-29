package org.openfoot.engine.match

import org.openfoot.model.GoalType
import org.openfoot.model.PlayerId
import org.openfoot.model.RuleSets
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Section 3.14's per player counters, pinned against hand built logs.
 *
 * No fixture here plays a match. toPlayerTallies is a pure fold over a log a
 * test writes by hand, matching the discipline of MatchEventTest and TickTest
 * rather than of the golden vector or the sanity checks, which read a real
 * simulation instead.
 *
 * The clock every test shares is forty minutes a first half and fifty a
 * second, chosen to be nothing like the forty five and forty five a hasty
 * reading of section 3.1 might hard code, and every minute below is picked so
 * that the four minutes formulas of section 3.14 give four different answers:
 * a fixture whose expected figure is also the smallest, the largest or the
 * first one computed cannot tell a real rule from a shortcut that happens to
 * agree with it on that one case.
 */
class PlayerTallyTest {

    private val clock = MatchClock(firstHalfMinutes = 40, secondHalfMinutes = 50)

    private fun keeper(id: Int) = Lineups.player(slot = 1, strength = 50, id = id)

    private fun outfield(id: Int, slot: Int) = Lineups.player(slot = slot, strength = 50, id = id)

    private fun tallies(
        home: List<MatchPlayer>,
        away: List<MatchPlayer>,
        log: List<MatchEvent>,
    ): PlayerTallies = log.toPlayerTallies(home, away, RuleSets.CLASSIC, clock)

    /**
     * Both starting elevens and every substitute who came on are keys; a man
     * who never left the bench is not a key at all, which is how this fold
     * tells "not rated" apart from "rated nought" without a sentinel value.
     */
    @Test
    fun `a benched player is absent while an untouched starter is present at nought`() {
        val homeKeeper = keeper(1)
        val homeStriker = outfield(2, slot = 20)
        val benched = outfield(3, slot = 26)
        val away = listOf(keeper(1), outfield(2, slot = 20))

        val result = tallies(home = listOf(homeKeeper, homeStriker), away = away, log = emptyList())

        assertFalse(PlayerId(3) in result.home, "a player who never came on must not be a key at all")
        assertEquals(PlayerTally(), result.home.getValue(PlayerId(2)), "an untouched starter is rated nought")
        assertEquals(90, result.home.getValue(PlayerId(2)).minutesPlayed, "the untouched default is ninety")
        assertTrue(benched.id == PlayerId(3), "sanity: the benched fixture is the one left out")
    }

    /**
     * A goal's own matchGoalCredits is what moves the scorer's counter, not
     * one point per Goal event, which is section 3.15 item 13's double count
     * for an open play goal read straight rather than deduplicated.
     */
    @Test
    fun `match goals sum matchGoalCredits rather than counting Goal events`() {
        val scorer = outfield(7, slot = 20)
        val home = listOf(keeper(1), scorer)
        val away = listOf(keeper(1))
        val log = listOf(
            MatchEvent.Goal(10, TeamSide.HOME, GoalType.OPEN_PLAY, scorer, scorer, 2, null),
            MatchEvent.Goal(50, TeamSide.HOME, GoalType.PENALTY, scorer, scorer, 1, null),
        )

        val result = tallies(home, away, log)

        assertEquals(3, result.home.getValue(PlayerId(7)).matchGoals, "two plus one, not two goals")
    }

    /**
     * An own goal's scorer is the attacking finisher who is credited nowhere
     * in the report; its author is the blamed defender of the conceding side.
     * The two counters this fold keeps for them, matchGoals and ownGoals, must
     * land on the two different men MatchEvent.Goal's own docstring names, and
     * on the two different sides.
     */
    @Test
    fun `an own goal credits the attacking scorer and charges the defending author`() {
        val attackingFinisher = outfield(4, slot = 20)
        val blamedDefender = outfield(9, slot = 5)
        val home = listOf(keeper(1), attackingFinisher)
        val away = listOf(keeper(1), blamedDefender)
        val goal = MatchEvent.Goal(
            minute = 30,
            side = TeamSide.HOME,
            type = GoalType.OWN_GOAL,
            author = blamedDefender,
            scorer = attackingFinisher,
            matchGoalCredits = 1,
            assister = null,
        )

        val result = tallies(home, away, listOf(goal))

        val scorerTally = result.home.getValue(PlayerId(4))
        assertEquals(1, scorerTally.matchGoals, "the attacking finisher still owns the match goal")
        assertEquals(0, scorerTally.ownGoals, "he is not the one an own goal charges")

        val authorTally = result.away.getValue(PlayerId(9))
        assertEquals(0, authorTally.matchGoals, "the blamed defender scored no match goal")
        assertEquals(1, authorTally.ownGoals, "he is the one section 3.14 charges minus one point five for")
        assertEquals(
            30,
            authorTally.minutesPlayed,
            "the credited author is the protagonist of a goal, whichever side he plays for",
        )
    }

    /**
     * A free kick or a penalty redirected to a designated taker splits author
     * from scorer the other way round from an own goal: the report and the
     * minutes rule follow the taker, while the finisher who was actually drawn
     * still owns the match goal counter and, absent some other event of his
     * own, is left at the ninety minute default.
     */
    @Test
    fun `a redirected goal moves the taker's minutes and the finisher's match goals separately`() {
        val designatedTaker = outfield(11, slot = 15)
        val drawnFinisher = outfield(12, slot = 20)
        val home = listOf(keeper(1), designatedTaker, drawnFinisher)
        val away = listOf(keeper(1))
        val goal = MatchEvent.Goal(
            minute = 22,
            side = TeamSide.HOME,
            type = GoalType.FREE_KICK,
            author = designatedTaker,
            scorer = drawnFinisher,
            matchGoalCredits = 2,
            assister = null,
        )

        val result = tallies(home, away, listOf(goal))

        assertEquals(22, result.home.getValue(PlayerId(11)).minutesPlayed, "the taker is the credited author")
        assertEquals(0, result.home.getValue(PlayerId(11)).matchGoals, "he did not draw the finisher's role")
        assertEquals(2, result.home.getValue(PlayerId(12)).matchGoals, "the finisher keeps the match goal")
        assertEquals(
            90,
            result.home.getValue(PlayerId(12)).minutesPlayed,
            "with no event of his own the finisher stays at the default",
        )
    }

    /**
     * Item 52's confirmed reading: the shooter's own counter for a shot the
     * opposing keeper saved rises only on the ordinary saved branch. A goal
     * does not add to it, an off target shot does not add to it, and neither
     * does the one Shot of a minute that also carries an InteractivePenalty,
     * because that shot belongs to section 3.10's own path instead.
     */
    @Test
    fun `shots the opposing keeper saved excludes goals, wide shots and interactive penalties`() {
        val shooter = outfield(6, slot = 20)
        val keeperPlayer = keeper(1)
        val taker = outfield(8, slot = 15)
        val home = listOf(keeperPlayer)
        val away = listOf(keeper(1), shooter, taker)
        val log = listOf(
            MatchEvent.Shot(1, TeamSide.AWAY, shooter, onTarget = true, scored = false),
            MatchEvent.Shot(2, TeamSide.AWAY, shooter, onTarget = true, scored = true),
            MatchEvent.Shot(3, TeamSide.AWAY, shooter, onTarget = false, scored = false),
            MatchEvent.Shot(4, TeamSide.AWAY, taker, onTarget = true, scored = false),
            MatchEvent.InteractivePenalty(4, TeamSide.AWAY, taker, keeperPlayer, scored = false, keeperSaved = true),
        )

        val result = tallies(home, away, log)

        assertEquals(1, result.away.getValue(PlayerId(6)).shotsSavedByKeeper, "only the first shot counts")
        assertEquals(
            0,
            result.away.getValue(PlayerId(8)).shotsSavedByKeeper,
            "the interactive penalty's own shot is excluded",
        )
    }

    /**
     * The goalkeeper's own two general counters, on target shots faced and
     * goals conceded, are fed by the ordinary Shot and Goal events of whoever
     * is standing in his cell, and a goal adds to both at once: it is on
     * target by MatchEvent.Shot's own invariant, and it is a goal besides.
     */
    @Test
    fun `the facing keeper's shots faced and goals conceded both read the ordinary events`() {
        val homeKeeperPlayer = keeper(1)
        val awayShooter = outfield(21, slot = 20)
        val home = listOf(homeKeeperPlayer)
        val away = listOf(keeper(1), awayShooter)
        val log = listOf(
            MatchEvent.Shot(1, TeamSide.AWAY, awayShooter, onTarget = true, scored = false),
            MatchEvent.Shot(2, TeamSide.AWAY, awayShooter, onTarget = true, scored = true),
            MatchEvent.Goal(2, TeamSide.AWAY, GoalType.OPEN_PLAY, awayShooter, awayShooter, 2, null),
            MatchEvent.Shot(3, TeamSide.AWAY, awayShooter, onTarget = false, scored = false),
        )

        val result = tallies(home, away, log)

        val homeKeeperTally = result.home.getValue(PlayerId(1))
        assertEquals(2, homeKeeperTally.shotsOnTargetFaced, "the save and the goal are both on target")
        assertEquals(1, homeKeeperTally.goalsConceded, "exactly one goal went in")
    }

    /**
     * Unlike the shooter's own shotsSavedByKeeper, the facing keeper's own
     * shotsOnTargetFaced does count a Shot that went through section 3.10's
     * interactive path, because section 3.10 itself says a converted kick and
     * five of its seven miss outcomes are on target. This is a different claim
     * from item 52's, argued from section 3.10 rather than from symmetry with
     * the team level statistics, and it needs its own fixture: nothing above
     * puts an InteractivePenalty in front of the keeper this counter reads.
     *
     * The accompanying Shot is included because that is how the real engine
     * always presents an interactive penalty: TickOutcome.events builds the
     * Shot for a goal tick from the resolution's own onTarget and scored
     * flags before the InteractivePenalty and any Goal that follow it in the
     * same minute, and shotsOnTargetFaced is read off the Shot event, not off
     * InteractivePenalty directly.
     */
    @Test
    fun `the facing keeper's shots faced counts an interactive penalty too`() {
        val homeKeeperPlayer = keeper(1)
        val takerPlayer = outfield(15, slot = 20)
        val home = listOf(homeKeeperPlayer)
        val away = listOf(keeper(1), takerPlayer)
        val log = listOf(
            MatchEvent.Shot(40, TeamSide.AWAY, takerPlayer, onTarget = true, scored = false),
            MatchEvent.InteractivePenalty(40, TeamSide.AWAY, takerPlayer, homeKeeperPlayer, false, keeperSaved = true),
        )

        val result = tallies(home, away, log)

        assertEquals(
            1,
            result.home.getValue(PlayerId(1)).shotsOnTargetFaced,
            "the saved kick is on target and section 3.10 says so directly",
        )
    }

    /**
     * A Shot is the most frequent event in the log, and item 1 of the review
     * that followed this fold's first draft is what makes this its own test
     * rather than an incidental assertion buried in another one: nothing named
     * above exercises a Shot at all, so nothing above would notice if a later
     * change quietly wired one into the minutes rule.
     */
    @Test
    fun `a shot never moves the shooter's own minutesPlayed`() {
        val shooter = outfield(95, slot = 20)
        val home = listOf(keeper(1))
        val away = listOf(keeper(1), shooter)
        val log = listOf(
            MatchEvent.Shot(1, TeamSide.AWAY, shooter, onTarget = true, scored = false),
            MatchEvent.Shot(2, TeamSide.AWAY, shooter, onTarget = false, scored = false),
            MatchEvent.Shot(70, TeamSide.AWAY, shooter, onTarget = true, scored = true),
        )

        val result = tallies(home, away, log)

        assertEquals(
            90,
            result.away.getValue(PlayerId(95)).minutesPlayed,
            "a Shot is neither a protagonist nor a supporting event of section 3.14's own list",
        )
    }

    /**
     * The two interactive penalty counters section 3.10 defines and section
     * 3.14 step 6 reads: a save moves the keeper's savedPenalties and his
     * minutes as a supporting player, a plain miss moves only the taker's
     * missedPenalties and touches the keeper's minutes not at all.
     */
    @Test
    fun `saved and missed interactive penalties credit different men and only one moves minutes`() {
        val takerPlayer = outfield(14, slot = 20)
        val keeperPlayer = keeper(1)
        val home = listOf(keeper(1))
        val away = listOf(keeperPlayer, takerPlayer)
        val log = listOf(
            MatchEvent.InteractivePenalty(5, TeamSide.AWAY, takerPlayer, keeperPlayer, false, keeperSaved = true),
            MatchEvent.InteractivePenalty(6, TeamSide.AWAY, takerPlayer, keeperPlayer, false, keeperSaved = false),
        )

        val result = tallies(home, away, log)

        val awayTaker = result.away.getValue(PlayerId(14))
        assertEquals(2, awayTaker.missedPenalties, "both kicks missed the scoreboard")

        val homeKeeper = result.home.getValue(PlayerId(1))
        assertEquals(1, homeKeeper.savedPenalties, "only the first kick was actually saved")
        assertEquals(98 - 5, homeKeeper.minutesPlayed, "the saved kick makes him a supporting player at minute five")
    }

    /**
     * A booking and a dismissal both name the carded man as the protagonist,
     * and a dismissal counts every time it is logged, whether it followed a
     * second yellow or came from a straight red: section 3.14 reads vermelhos
     * off SendingOff events and does not care which branch produced one.
     */
    @Test
    fun `bookings and dismissals both credit the carded player as a protagonist`() {
        val player = outfield(13, slot = 15)
        val home = listOf(keeper(1), player)
        val away = listOf(keeper(1))
        val log = listOf(
            MatchEvent.Booking(8, TeamSide.HOME, player),
            MatchEvent.Booking(52, TeamSide.HOME, player),
            MatchEvent.SendingOff(52, TeamSide.HOME, player, secondYellow = true),
        )

        val result = tallies(home, away, log)

        val tally = result.home.getValue(PlayerId(13))
        assertEquals(2, tally.yellowCards, "both bookings count, including the one behind the second yellow")
        assertEquals(1, tally.redCards, "one SendingOff event is one red card")
        assertEquals(48 + (52 - 40), tally.minutesPlayed, "the last event, the dismissal, is what wins")
    }

    /**
     * An assist is a supporting player's own event, distinct from the goal it
     * rode in on: the scorer and the assister of the same Goal move by two
     * unrelated formulas, protagonist for nobody here and supporting for the
     * assister alone, since no author is set on this fixture's goal.
     */
    @Test
    fun `an assist credits the assister as a supporting player`() {
        val scorer = outfield(16, slot = 20)
        val assister = outfield(17, slot = 15)
        val home = listOf(keeper(1), scorer, assister)
        val away = listOf(keeper(1))
        val goal = MatchEvent.Goal(72, TeamSide.HOME, GoalType.OPEN_PLAY, scorer, scorer, 2, assister)

        val result = tallies(home, away, listOf(goal))

        val assisterTally = result.home.getValue(PlayerId(17))
        assertEquals(1, assisterTally.assists, "one assist")
        assertEquals(50 - (72 - 40), assisterTally.minutesPlayed, "a supporting player in the second half")
    }

    /**
     * The four cells of section 3.14's minutes rule, pinned with four minutes
     * that are none of them the smallest, the largest or the first computed,
     * so that a formula which only happens to agree with a shortcut on an
     * edge case is still caught.
     */
    @Test
    fun `the minutes rule gives four different answers for the four combinations of role and half`() {
        val protagonistFirstHalf = outfield(21, slot = 15)
        val protagonistSecondHalf = outfield(22, slot = 15)
        val supportingFirstHalf = outfield(23, slot = 15)
        val supportingSecondHalf = outfield(24, slot = 15)
        val home = listOf(
            keeper(1),
            protagonistFirstHalf,
            protagonistSecondHalf,
            supportingFirstHalf,
            supportingSecondHalf,
        )
        val away = listOf(keeper(1))
        val log = listOf(
            MatchEvent.Booking(12, TeamSide.HOME, protagonistFirstHalf),
            MatchEvent.Booking(63, TeamSide.HOME, protagonistSecondHalf),
            MatchEvent.Goal(9, TeamSide.HOME, GoalType.OPEN_PLAY, null, null, 0, supportingFirstHalf),
            MatchEvent.Goal(75, TeamSide.HOME, GoalType.OPEN_PLAY, null, null, 0, supportingSecondHalf),
        )

        val result = tallies(home, away, log)

        assertEquals(12, result.home.getValue(PlayerId(21)).minutesPlayed, "protagonist, first half")
        assertEquals(71, result.home.getValue(PlayerId(22)).minutesPlayed, "protagonist, second half")
        assertEquals(89, result.home.getValue(PlayerId(23)).minutesPlayed, "supporting, first half")
        assertEquals(15, result.home.getValue(PlayerId(24)).minutesPlayed, "supporting, second half")
    }

    /**
     * The consequence OPEN-QUESTIONS item 53 calls out by name: a first half
     * scorer is treated as having left the match when he scored, so his own
     * minutesPlayed is the minute of the goal itself rather than ninety.
     */
    @Test
    fun `a first half scorer is treated as having left when he scored`() {
        val earlyScorer = outfield(31, slot = 20)
        val laterScorer = outfield(32, slot = 20)
        val home = listOf(keeper(1), earlyScorer, laterScorer)
        val away = listOf(keeper(1))
        val log = listOf(
            MatchEvent.Goal(10, TeamSide.HOME, GoalType.OPEN_PLAY, earlyScorer, earlyScorer, 2, null),
            MatchEvent.Goal(20, TeamSide.HOME, GoalType.OPEN_PLAY, laterScorer, laterScorer, 2, null),
        )

        val result = tallies(home, away, log)

        assertEquals(
            10,
            result.home.getValue(PlayerId(31)).minutesPlayed,
            "before minute fifteen, which section 3.14 step ten fines two point five",
        )
        assertEquals(
            20,
            result.home.getValue(PlayerId(32)).minutesPlayed,
            "minute twenty, which section 3.14 step ten fines one point five",
        )
    }

    /**
     * minutesPlayed is always the last event, whichever role it casts the
     * player in, and never the first, the largest or a fixed preference for
     * one role over the other.
     */
    @Test
    fun `the last event wins over an earlier one whatever role either was`() {
        val cardedThenScored = outfield(41, slot = 15)
        val scoredThenAssisted = outfield(42, slot = 20)
        val home = listOf(keeper(1), cardedThenScored, scoredThenAssisted)
        val away = listOf(keeper(1))
        val log = listOf(
            MatchEvent.Booking(5, TeamSide.HOME, cardedThenScored),
            MatchEvent.Goal(70, TeamSide.HOME, GoalType.OPEN_PLAY, cardedThenScored, cardedThenScored, 2, null),
            MatchEvent.Goal(6, TeamSide.HOME, GoalType.OPEN_PLAY, scoredThenAssisted, scoredThenAssisted, 2, null),
            MatchEvent.Goal(80, TeamSide.HOME, GoalType.OPEN_PLAY, null, null, 0, scoredThenAssisted),
        )

        val result = tallies(home, away, log)

        assertEquals(
            48 + (70 - 40),
            result.home.getValue(PlayerId(41)).minutesPlayed,
            "the later goal, not the earlier booking",
        )
        assertEquals(
            50 - (80 - 40),
            result.home.getValue(PlayerId(42)).minutesPlayed,
            "the later assist, not the earlier goal",
        )
    }

    /**
     * A substitute is added to the tally the moment he arrives, and the
     * outgoing and incoming men move by the protagonist and the supporting
     * formula respectively, section 3.14's "saida em substituicao" and
     * "entrada em substituicao".
     */
    @Test
    fun `a substitution adds the arrival and moves both men's minutes`() {
        val startingForward = outfield(51, slot = 20)
        val reserveForward = outfield(52, slot = 20)
        val home = listOf(keeper(1), startingForward)
        val away = listOf(keeper(1))
        val log = listOf(
            MatchEvent.Substitution(65, TeamSide.HOME, startingForward, reserveForward, SubstitutionReason.TIREDNESS),
        )

        val result = tallies(home, away, log)

        assertTrue(PlayerId(52) in result.home, "the arrival is now a key of the tally")
        assertEquals(48 + (65 - 40), result.home.getValue(PlayerId(51)).minutesPlayed, "the man who left")
        assertEquals(50 - (65 - 40), result.home.getValue(PlayerId(52)).minutesPlayed, "the man who came on")
    }

    /**
     * Whoever stands in a side's goal changes when a Substitution moves a
     * fresh arrival into the keeper's own cell, and every shot and goal
     * before that minute belongs to the man who has since left it.
     */
    @Test
    fun `a goalkeeper substitution redirects later shots and goals to his replacement`() {
        val originalKeeper = keeper(1)
        val reserveKeeper = keeper(9)
        val awayShooter = outfield(61, slot = 20)
        val home = listOf(originalKeeper)
        val away = listOf(keeper(1), awayShooter)
        val log = listOf(
            MatchEvent.Shot(3, TeamSide.AWAY, awayShooter, onTarget = true, scored = false),
            MatchEvent.Substitution(20, TeamSide.HOME, originalKeeper, reserveKeeper, SubstitutionReason.INJURY),
            MatchEvent.Shot(25, TeamSide.AWAY, awayShooter, onTarget = true, scored = false),
        )

        val result = tallies(home, away, log)

        assertEquals(1, result.home.getValue(PlayerId(1)).shotsOnTargetFaced, "only the shot before the swap")
        assertEquals(1, result.home.getValue(PlayerId(9)).shotsOnTargetFaced, "only the shot after the swap")
    }

    /**
     * A keeper dismissed or injured with nobody to bring on leaves the cell
     * empty, and this fold must not invent a man to blame a later shot on.
     * Section 3.14's own list of protagonist and supporting events never
     * names an Injury on its own, so it must not move minutesPlayed either
     * when the injured man is not the subject of a Substitution afterwards.
     */
    @Test
    fun `a keeper who leaves without a replacement stops facing anybody and keeps his own minutes`() {
        val onlyKeeper = keeper(1)
        val awayShooter = outfield(71, slot = 20)
        val home = listOf(onlyKeeper)
        val away = listOf(keeper(1), awayShooter)
        val log = listOf(
            MatchEvent.Injury(15, TeamSide.HOME, onlyKeeper, days = 20, permanentStrengthLoss = 1),
            MatchEvent.Shot(60, TeamSide.AWAY, awayShooter, onTarget = true, scored = false),
        )

        val result = tallies(home, away, log)

        val keeperTally = result.home.getValue(PlayerId(1))
        assertEquals(0, keeperTally.shotsOnTargetFaced, "nobody stands in for him")
        assertEquals(90, keeperTally.minutesPlayed, "the injury alone is neither role the rule names")
    }

    /**
     * PlayerId is only unique inside its own squad, so the same number names
     * a different man on each side. The two tallies must never let one side's
     * counters leak into the other's entry for the same numeric id, and that
     * has to hold for the paths that resolve a side through side.opponent, the
     * keeper maps, and not only for the ones that stay on event.side directly:
     * this project has already shipped that exact defect once, in section
     * 3.15 item 12's arrivals list.
     */
    @Test
    fun `identical ids on both sides keep separate counters, including through side opponent`() {
        val homeFour = outfield(4, slot = 20)
        val awayFour = outfield(4, slot = 20)
        val homeKeeperPlayer = keeper(1)
        val awayKeeperPlayer = keeper(1)
        val home = listOf(homeKeeperPlayer, homeFour)
        val away = listOf(awayKeeperPlayer, awayFour)
        val log = listOf(
            MatchEvent.Goal(18, TeamSide.HOME, GoalType.OPEN_PLAY, homeFour, homeFour, 2, null),
        )

        val result = tallies(home, away, log)

        assertEquals(2, result.home.getValue(PlayerId(4)).matchGoals, "the home side's own id four scored")
        assertEquals(0, result.away.getValue(PlayerId(4)).matchGoals, "the away side's id four did not")

        assertEquals(
            1,
            result.away.getValue(PlayerId(1)).goalsConceded,
            "the away keeper, id one, is the one who conceded a home goal",
        )
        assertEquals(
            0,
            result.home.getValue(PlayerId(1)).goalsConceded,
            "the home keeper, the same numeric id one, conceded nothing",
        )
    }

    /**
     * touch's own guard: an event naming a player who is neither a starter
     * nor an arrival is a malformed log, and this fold fails loudly on it
     * rather than inventing an entry that would silently hide the bug.
     */
    @Test
    fun `an event naming a player who was never tallied fails rather than inventing him`() {
        val neverTallied = outfield(80, slot = 26)
        val home = listOf(keeper(1))
        val away = listOf(keeper(1))
        val log = listOf(MatchEvent.Booking(1, TeamSide.HOME, neverTallied))

        assertFailsWith<IllegalStateException> { tallies(home, away, log) }
    }

    /**
     * A goal that only reaches the score through an own goal's blame or a
     * redirection can leave a nullable field null; this fold must skip
     * crediting nobody rather than throw when that is exactly what section
     * 3.7 hands it.
     */
    @Test
    fun `a goal with no assister and no author credits only the scorer`() {
        val scorer = outfield(90, slot = 20)
        val home = listOf(keeper(1), scorer)
        val away = listOf(keeper(1))
        val goal = MatchEvent.Goal(40, TeamSide.HOME, GoalType.OPEN_PLAY, null, scorer, 2, null)

        val result = tallies(home, away, listOf(goal))

        assertEquals(2, result.home.getValue(PlayerId(90)).matchGoals, "the scorer is still credited")
        assertNull(result.home[PlayerId(scorer.id.value + 1000)], "sanity: no phantom entry was created")
    }

    /**
     * PlayerTallies.of and its two defaults are never exercised by
     * toPlayerTallies itself, since the fold always builds both maps by hand,
     * so a caller building or reading one directly needs its own pin.
     */
    @Test
    fun `PlayerTallies defaults to two empty maps and of reads the right one`() {
        val empty = PlayerTallies()

        assertTrue(empty.home.isEmpty(), "home defaults to empty")
        assertTrue(empty.away.isEmpty(), "away defaults to empty")

        val populated = PlayerTallies(
            home = mapOf(PlayerId(1) to PlayerTally(matchGoals = 3)),
            away = mapOf(PlayerId(1) to PlayerTally(matchGoals = 5)),
        )

        assertEquals(3, populated.of(TeamSide.HOME).getValue(PlayerId(1)).matchGoals, "of(HOME) reads home")
        assertEquals(5, populated.of(TeamSide.AWAY).getValue(PlayerId(1)).matchGoals, "of(AWAY) reads away")
    }
}
