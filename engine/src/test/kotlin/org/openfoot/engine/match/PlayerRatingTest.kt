package org.openfoot.engine.match

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.RuleSet
import org.openfoot.model.RuleSets
import org.openfoot.model.Slot
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Section 3.14's eleven ordered steps, pinned one at a time and then pinned
 * as an order.
 *
 * Nothing here plays a match. ratePlayer is a pure function of one player,
 * one hand built tally, one side's match and one generator, which is what
 * lets a single step be isolated: every fixture below is chosen so that the
 * ten steps it is not testing contribute exactly nothing.
 *
 * The default context is a one all draw with level possession and level
 * tackle counters. Level counters fire neither clause of step 2 and neither
 * clause of step 4 and spend no draw at all, and conceding exactly one goal
 * is the one scoreline step 7 charges nothing for, so a player in a cell
 * above thirteen reads nothing but the base table under it. A player in a
 * defensive cell still pays step 7's unconditional tax draw, which is why so
 * many fixtures below hand in one scripted value they expect to be spent on
 * nothing.
 *
 * Every strength here is one of 30, 50, 91 and the band edges, and every
 * expected figure was computed from the spec's own numbers rather than read
 * off a run. Where a fixture could have been satisfied by a shortcut, the
 * shortcut is named in the test's own docstring: the base table has two equal
 * cells, the goals conceded chain has no rung for three, the rewarded and
 * punished bands of step 4 differ by one cell at each end, and the two
 * chances that look alike are one in three and one in four.
 */
class PlayerRatingTest {

    private val rules = RuleSets.CLASSIC

    /**
     * A one all draw in which neither counter separates the sides.
     *
     * One goal conceded rather than none, because a clean sheet would pay
     * every defensive cell step 7's bonus and because two or more would
     * charge it, and one is the single scoreline that does neither.
     */
    private fun context(
        goalsFor: Int = 1,
        goalsAgainst: Int = 1,
        possessionsWon: Int = 40,
        opponentPossessionsWon: Int = 40,
        tackles: Int = 10,
        opponentTackles: Int = 10,
        opponentShots: Int = 0,
    ) = SideRatingContext(
        goalsFor = goalsFor,
        goalsAgainst = goalsAgainst,
        possessionsWon = possessionsWon,
        opponentPossessionsWon = opponentPossessionsWon,
        tackles = tackles,
        opponentTackles = opponentTackles,
        opponentShots = opponentShots,
    )

    private fun rate(
        player: MatchPlayer,
        tally: PlayerTally = PlayerTally(),
        context: SideRatingContext = context(),
        rng: ScriptedInts = ScriptedInts(),
    ): PlayerRating = ratePlayer(player, tally, context, rules, rng)

    /**
     * The same rating under a rule set of the caller's choosing.
     *
     * Only the two rule set aware tests below use this; every other fixture in
     * this file reads the classic rules through rate above, since section
     * 3.14's eleven steps are otherwise identical under both.
     */
    private fun rateWith(
        rules: RuleSet,
        player: MatchPlayer,
        tally: PlayerTally = PlayerTally(),
        context: SideRatingContext = context(),
        rng: ScriptedInts = ScriptedInts(),
    ): PlayerRating = ratePlayer(player, tally, context, rules, rng)

    private fun assertRating(expected: Double, actual: PlayerRating, message: String) =
        assertEquals(expected, actual.value, TOLERANCE, message)

    /**
     * A natural forward standing in a central forward cell.
     *
     * Cell 20 is outside every band section 3.14 reads except step 5's, which
     * has no band at all, so this is the player who reads the base table and
     * the event counters and nothing else.
     */
    private fun forward(strength: Int = 50, star: Boolean = false, topWorld: Boolean = false) =
        Lineups.player(slot = 20, strength = strength, star = star, topWorld = topWorld)

    /** A natural goalkeeper in the goalkeeper's cell. */
    private fun keeper(strength: Int = 91) = Lineups.player(slot = 1, strength = strength)

    private fun midfielder(
        slot: Int,
        strength: Int = 50,
        style: PlayerStyle = PlayerStyle.OFFENSIVE,
        firstTrait: Trait = Lineups.NEUTRAL_TRAITS.first,
        secondTrait: Trait = Lineups.NEUTRAL_TRAITS.second,
    ) = Lineups.player(
        slot = slot,
        strength = strength,
        position = Position.MIDFIELDER,
        style = style,
        firstTrait = firstTrait,
        secondTrait = secondTrait,
    )

    /**
     * Every cell of the base table, read by a player who reaches no other
     * step at all.
     *
     * All twelve are asserted and not a sample of them, because two of the
     * twelve are equal: a win is worth six for the weakest band and six again
     * for the next one up, so a table that had lost its second row would
     * still satisfy any fixture that only tested one of the two. The three
     * scorelines are one all, two one and one two, so the result is decided
     * by the comparison and never by the goals for alone.
     */
    @Test
    fun `the base table is pinned on every one of its twelve cells`() {
        val cells = listOf(
            Triple(30, 5.5, "weakest band, draw"),
            Triple(60, 5.8, "second band, draw"),
            Triple(90, 6.2, "third band, draw"),
            Triple(91, 6.8, "strongest band, draw"),
        )
        for ((strength, expected, label) in cells) {
            val rng = ScriptedInts()
            assertRating(expected, rate(forward(strength), context = context(1, 1), rng = rng), label)
            assertEquals(0, rng.draws, "$label must spend no draw")
        }

        val wins = listOf(
            Triple(30, 6.0, "weakest band, win"),
            Triple(60, 6.0, "second band, win"),
            Triple(90, 6.7, "third band, win"),
            Triple(91, 7.2, "strongest band, win"),
        )
        for ((strength, expected, label) in wins) {
            assertRating(expected, rate(forward(strength), context = context(2, 1)), label)
        }

        val losses = listOf(
            Triple(30, 5.0, "weakest band, loss"),
            Triple(60, 5.2, "second band, loss"),
            Triple(90, 5.5, "third band, loss"),
            Triple(91, 6.0, "strongest band, loss"),
        )
        for ((strength, expected, label) in losses) {
            assertRating(expected, rate(forward(strength), context = context(1, 2)), label)
        }
    }

    /**
     * The band edges, read on both sides of each of the three of them.
     *
     * Thirty belongs to the first band and thirty one to the second, sixty to
     * the second and sixty one to the third, ninety to the third and ninety
     * one to the fourth. Every one of those is a bracket that reaches up to
     * and includes its own figure, so a table written with strict comparisons
     * would move all six.
     */
    @Test
    fun `the strength brackets include their own upper figure`() {
        val edges = listOf(
            30 to 5.5,
            31 to 5.8,
            60 to 5.8,
            61 to 6.2,
            90 to 6.2,
            91 to 6.8,
        )
        for ((strength, expected) in edges) {
            assertRating(expected, rate(forward(strength)), "strength $strength on a draw")
        }
    }

    /**
     * Step 1, on a player whose cell asks for a position he does not have.
     *
     * A midfielder in a central forward cell is out of position and that cell
     * is not the goalkeeper's, so he pays the first penalty and not the
     * second.
     */
    @Test
    fun `a player in the wrong cell pays one and a half`() {
        val displaced = Lineups.player(slot = 20, strength = 50, position = Position.MIDFIELDER)

        assertRating(4.3, rate(displaced), "five point eight less one point five")
    }

    /**
     * Step 1's second penalty, which only the goalkeeper's cell charges.
     *
     * The absolute figure is asserted alongside the difference, because the
     * goalkeeper's cell also drags in the whole of step 6 and a test that
     * only compared two men in that cell would pass with step 6 missing
     * entirely. The natural keeper reads six point eight less step 6's one
     * point three; the forward standing in front of him reads three less
     * again.
     */
    @Test
    fun `a player displaced into the goal pays three rather than one and a half`() {
        val naturalKeeper = keeper()
        val displaced = Lineups.player(slot = 1, strength = 91, position = Position.FORWARD)

        val kept = rate(naturalKeeper)
        val stranded = rate(displaced)

        assertRating(5.5, kept, "six point eight, less step six's one point three")
        assertRating(2.5, stranded, "the same, less one point five twice over")
        assertEquals(
            3.0,
            kept.value - stranded.value,
            TOLERANCE,
            "the cell charges the plain penalty and the in goal penalty on top of it",
        )
    }

    /**
     * A cell of nought or less is a cell nobody has, and section 3.14 hands
     * out a default by natural position and rates the player in it.
     *
     * Minus one is the value the original leaves on an unused substitute and
     * nought is a player who was never given a cell, and both take the same
     * road. The five defaults are asserted through the rating's own slot,
     * which is where this engine reports the value the original writes back
     * onto the player.
     */
    @Test
    fun `a player with no cell is given the default for his natural position`() {
        val defaults = listOf(
            Triple(Position.GOALKEEPER, 0, 1),
            Triple(Position.FULLBACK, 0, 2),
            Triple(Position.CENTREBACK, -1, 7),
            Triple(Position.MIDFIELDER, 0, 15),
            Triple(Position.FORWARD, -1, 23),
        )
        for ((position, cell, expected) in defaults) {
            val player = Lineups.player(slot = cell, strength = 50, position = position)
            val rated = rate(player, rng = ScriptedInts(1, 1, 1, 1, 1))
            assertEquals(Slot(expected), rated.slot, "$position with cell $cell")
        }
    }

    /**
     * The trap in step 1: having had no cell counts as out of position in its
     * own right, so a goalkeeper handed the goalkeeper's cell by the default
     * table is charged for being out of position AND charged again for the
     * cell he was handed.
     *
     * Both halves matter. A reading that only charged a player whose natural
     * position differs from his cell's would give this man nothing at all,
     * and a reading that charged him once would give him one point five.
     */
    @Test
    fun `a keeper with no cell is charged three for the goal he was defaulted into`() {
        val cellless = Lineups.player(slot = 0, strength = 91, position = Position.GOALKEEPER)

        val rated = rate(cellless)

        assertEquals(Slot(1), rated.slot, "the default for a goalkeeper is the goalkeeper's cell")
        assertRating(2.5, rated, "six point eight, less three for step one, less one point three for step six")
        assertRating(5.5, rate(keeper()), "the same keeper with a cell of his own pays neither penalty")
    }

    /**
     * Step 2's two branches, and the draw that decides how much each pays.
     *
     * The chance pays LESS here. A draw of nought is the one in three that
     * takes the reduced figure and every other draw takes the full one, which
     * is the opposite of step 4 below, and a test that only ever scripted
     * nought could not tell the two apart.
     */
    @Test
    fun `the possession terms pay the full figure two times in three`() {
        val ahead = context(possessionsWon = 41, opponentPossessionsWon = 40)
        val behind = context(possessionsWon = 40, opponentPossessionsWon = 41)

        assertRating(6.6, rate(midfielder(15), context = ahead, rng = ScriptedInts(1)), "full bonus")
        assertRating(6.6, rate(midfielder(15), context = ahead, rng = ScriptedInts(2)), "full bonus again")
        assertRating(6.1, rate(midfielder(15), context = ahead, rng = ScriptedInts(0)), "reduced bonus")
        assertRating(5.0, rate(midfielder(15), context = behind, rng = ScriptedInts(1)), "full penalty")
        assertRating(5.5, rate(midfielder(15), context = behind, rng = ScriptedInts(0)), "reduced penalty")
    }

    /**
     * Level possession counters fire neither clause of step 2 and spend
     * nothing, which is what makes every other fixture in this file able to
     * ignore the step.
     */
    @Test
    fun `level possession counters spend no draw and pay nothing`() {
        val rng = ScriptedInts()

        assertRating(5.8, rate(midfielder(15), rng = rng), "the base alone")
        assertEquals(0, rng.draws, "a comparison that separates nobody must not draw")
    }

    /**
     * The step 2 style term tests the derived sub role alone.
     *
     * Section 3.14 names a slot range twice, in steps 4 and 7, and names the
     * derived style of section 4.3 here instead. The cell has already been
     * checked by the ten to seventeen band and is not consulted again, and
     * the natural position is not consulted at all. Two of the three
     * consequences are asserted here: an attacking midfield cell pays a
     * defensive style, and a holding cell pays an offensive one nothing. The
     * third, the wing back cells, is the one a position test would get wrong
     * and has a test of its own below.
     *
     * Cell 12 is in the defensive band as well, so it pays step 7's tax draw;
     * the scripted one is spent on that and hits nothing.
     */
    @Test
    fun `the step two style term follows the derived sub role and not the cell`() {
        val ahead = context(possessionsWon = 41, opponentPossessionsWon = 40)

        val defensiveHighUp = midfielder(15, style = PlayerStyle.DEFENSIVE)
        val playmakerLowDown = midfielder(12, style = PlayerStyle.OFFENSIVE)

        assertRating(
            6.9,
            rate(defensiveHighUp, context = ahead, rng = ScriptedInts(1)),
            "a defensive style in an attacking midfield cell still collects the term",
        )
        assertRating(
            6.6,
            rate(playmakerLowDown, context = ahead, rng = ScriptedInts(1, 1)),
            "an offensive style in a holding cell collects nothing for it",
        )
    }

    /**
     * The wing back cells, which is where a test on the natural position and
     * a test on the derived style stop agreeing.
     *
     * Section 3.2 makes cells 10 and 17 demand a fullback, so no midfielder
     * ever stands in either of them. A term that asked for a midfielder as
     * well as for a defensive style would therefore never pay in those two
     * cells at all, and the man who is supposed to collect it, a defensive
     * fullback, would be excluded by the half of the test that has no
     * business being there.
     *
     * Both branches are asserted, because the term appears at both ends of
     * the possession comparison and at two different figures. Under a shape
     * that also asked for a midfielder these fixtures read 6.6 and 5.0
     * instead, so this test fails outright against it rather than agreeing
     * with both.
     *
     * The two cells differ in what else they spend. Cell ten sits inside the
     * defensive band and inside step 7's tax band, so it draws twice, and
     * cell seventeen is above both and draws once. The extra scripted value
     * is spent on the tax and hits nothing, which is also why the two cells
     * come out at the same figure.
     */
    @Test
    fun `a defensive fullback in a wing back cell collects the style term`() {
        val ahead = context(possessionsWon = 41, opponentPossessionsWon = 40)
        val behind = context(possessionsWon = 40, opponentPossessionsWon = 41)
        val wingBack = { cell: Int ->
            Lineups.player(
                slot = cell,
                strength = 50,
                position = Position.FULLBACK,
                style = PlayerStyle.DEFENSIVE,
            )
        }

        assertRating(6.9, rate(wingBack(10), context = ahead, rng = ScriptedInts(1, 1)), "cell ten with the ball")
        assertRating(6.9, rate(wingBack(17), context = ahead, rng = ScriptedInts(1)), "cell seventeen with the ball")
        assertRating(4.5, rate(wingBack(10), context = behind, rng = ScriptedInts(1, 1)), "cell ten without it")
        assertRating(4.5, rate(wingBack(17), context = behind, rng = ScriptedInts(1)), "cell seventeen without it")
    }

    /**
     * An offensive fullback in the same two cells collects nothing, so the
     * test above cannot be satisfied by a term that pays every wing back.
     */
    @Test
    fun `an offensive fullback in a wing back cell collects nothing`() {
        val ahead = context(possessionsWon = 41, opponentPossessionsWon = 40)
        val wingBack = Lineups.player(
            slot = 10,
            strength = 50,
            position = Position.FULLBACK,
            style = PlayerStyle.OFFENSIVE,
        )

        assertRating(6.6, rate(wingBack, context = ahead, rng = ScriptedInts(1, 1)), "the possession bonus alone")
    }

    /**
     * The style penalty on the losing side of the comparison, which is a
     * different figure from the bonus rather than its mirror image.
     */
    @Test
    fun `a defensive style without the ball is charged half a point more`() {
        val behind = context(possessionsWon = 40, opponentPossessionsWon = 41)
        val defensive = midfielder(15, style = PlayerStyle.DEFENSIVE)

        assertRating(4.5, rate(defensive, context = behind, rng = ScriptedInts(1)), "less zero point eight and zero point five")
    }

    /**
     * The characteristic bonus of step 2 reads the FIRST characteristic only,
     * and is paid only to the side that had more of the ball.
     */
    @Test
    fun `only the first characteristic earns the passing bonus and only with the ball`() {
        val ahead = context(possessionsWon = 41, opponentPossessionsWon = 40)
        val behind = context(possessionsWon = 40, opponentPossessionsWon = 41)

        val passerFirst = midfielder(15, firstTrait = Trait.PASSING, secondTrait = Trait.STAMINA)
        val playmakerFirst = midfielder(15, firstTrait = Trait.PLAYMAKING, secondTrait = Trait.STAMINA)
        val passerSecond = midfielder(15, firstTrait = Trait.STAMINA, secondTrait = Trait.PASSING)

        assertRating(7.1, rate(passerFirst, context = ahead, rng = ScriptedInts(1)), "Passe first")
        assertRating(7.1, rate(playmakerFirst, context = ahead, rng = ScriptedInts(1)), "Armacao first")
        assertRating(6.6, rate(passerSecond, context = ahead, rng = ScriptedInts(1)), "Passe second earns nothing")
        assertRating(5.0, rate(passerFirst, context = behind, rng = ScriptedInts(1)), "nothing without the ball")
    }

    /**
     * A midfielder outside the midfield cells reads nothing of step 2, not
     * even the draw.
     */
    @Test
    fun `a cell outside the midfield band reads nothing of step two`() {
        val ahead = context(possessionsWon = 41, opponentPossessionsWon = 40)
        val displaced = Lineups.player(slot = 20, strength = 50, position = Position.MIDFIELDER)
        val rng = ScriptedInts()

        assertRating(4.3, rate(displaced, context = ahead, rng = rng), "the base less step one only")
        assertEquals(0, rng.draws, "no cell outside ten to seventeen may draw here")
    }

    /**
     * Step 3, whose six terms every one multiply a counter rather than fire
     * once.
     *
     * The goal figure is per credit and not per goal event: the tally already
     * carries section 3.15 item 13's double count, so a match goal counter of
     * two is one open play goal and is worth one point eight.
     */
    @Test
    fun `every event term multiplies its own counter`() {
        val strong = forward(91)

        assertRating(8.6, rate(strong, PlayerTally(matchGoals = 2)), "two credits at zero point nine")
        assertRating(5.3, rate(strong, PlayerTally(ownGoals = 1)), "less one point five")
        assertRating(6.4, rate(strong, PlayerTally(yellowCards = 2)), "less zero point two twice")
        assertRating(6.0, rate(strong, PlayerTally(redCards = 1)), "less zero point eight")
        assertRating(8.0, rate(strong, PlayerTally(assists = 3)), "plus zero point four three times")
    }

    /**
     * Section 3.15 item 15, reproduced under the classic rules and repaired
     * under the modern ones. This half of the pair is the classic reading and
     * is what fails if the defect is ever removed from CLASSIC rather than
     * repaired beside it.
     *
     * The term is switched on by a missed penalty and multiplies the OWN GOAL
     * counter. Three fixtures are needed to pin that and not something that
     * merely agrees with it once: a player who missed penalties and scored no
     * own goal loses nothing at all, a player who did both is charged one
     * point two per own goal, and multiplying the missed penalties instead
     * would give the third fixture a different answer from the second.
     */
    @Test
    fun `the missed penalty term multiplies the own goal counter`() {
        val strong = forward(91)

        assertRating(
            6.8,
            rate(strong, PlayerTally(missedPenalties = 2)),
            "two missed penalties and no own goal cost nothing whatever",
        )
        assertRating(
            4.1,
            rate(strong, PlayerTally(missedPenalties = 1, ownGoals = 1)),
            "one own goal charged at one point five and again at one point two",
        )
        assertRating(
            4.1,
            rate(strong, PlayerTally(missedPenalties = 3, ownGoals = 1)),
            "the number of penalties missed changes nothing, only that one was",
        )
        assertRating(
            3.2,
            rate(strong, PlayerTally(matchGoals = 2, missedPenalties = 1, ownGoals = 2)),
            "two own goals charged at three and at two point four, over one open play goal",
        )
    }

    /**
     * Section 3.15 item 15 repaired: the modern term multiplies the counter it
     * is named for.
     *
     * The two fixtures the brief for this repair names are both here, and a
     * third is not, deliberately. A player with one missed penalty and one own
     * goal comes to the same 4,1 under both rule sets, since the classic
     * reading charges one own goal once and the modern reading charges one
     * penalty once, so that fixture proves nothing at all and is left out of
     * the discriminating pair rather than dressed up as evidence.
     *
     * What does discriminate is a player who missed a penalty and scored no
     * own goal, where the classic reading charges nothing and the modern one
     * charges the term once, and a player whose two counters differ, where the
     * classic reading follows the own goals and the modern one the penalties.
     * The last fixture separates them in the other direction, the modern
     * charge being the heavier of the two, so a reading that simply always
     * charged more or always charged less would fail one of the two.
     *
     * Every figure below sits clear of step 11's floor of 2,0, which is why
     * the open play goal is in the last two fixtures: without it the classic
     * figure would land under the floor, both rule sets would publish 2,0 and
     * the difference this test exists to show would be clamped away.
     */
    @Test
    fun `the modern missed penalty term multiplies the missed penalties`() {
        val strong = forward(91)

        assertRating(
            6.8,
            rateWith(RuleSets.CLASSIC, strong, PlayerTally(missedPenalties = 1)),
            "the classic term is switched on and multiplies nought own goals",
        )
        assertRating(
            5.6,
            rateWith(RuleSets.MODERN, strong, PlayerTally(missedPenalties = 1)),
            "the modern term charges one point two for the one penalty missed",
        )

        assertRating(
            3.2,
            rateWith(RuleSets.CLASSIC, strong, PlayerTally(matchGoals = 2, missedPenalties = 1, ownGoals = 2)),
            "two own goals charged at three and again at two point four",
        )
        assertRating(
            4.4,
            rateWith(RuleSets.MODERN, strong, PlayerTally(matchGoals = 2, missedPenalties = 1, ownGoals = 2)),
            "two own goals charged at three and one penalty at one point two",
        )

        assertRating(
            5.9,
            rateWith(RuleSets.CLASSIC, strong, PlayerTally(matchGoals = 2, missedPenalties = 3, ownGoals = 1)),
            "the classic charge follows the single own goal whatever the penalties",
        )
        assertRating(
            3.5,
            rateWith(RuleSets.MODERN, strong, PlayerTally(matchGoals = 2, missedPenalties = 3, ownGoals = 1)),
            "the modern charge follows the three penalties",
        )
    }

    /**
     * Step 5, which is paid to everybody and reads a counter of its own.
     *
     * The counter is shots the opposing goalkeeper saved and not shots on
     * target, so a goal pays only step 3 and nothing here. See
     * OPEN-QUESTIONS item 52.
     */
    @Test
    fun `saved shots pay three tenths each and a goal pays nothing here`() {
        assertRating(
            7.0,
            rate(forward(), PlayerTally(shotsSavedByKeeper = 4)),
            "four saved shots at zero point three",
        )
        assertRating(
            7.6,
            rate(forward(), PlayerTally(matchGoals = 2)),
            "an open play goal is worth its two credits and no shot bonus",
        )
    }

    /**
     * Step 4, whose chance pays MORE, the opposite of step 2.
     *
     * Cell 5 is a centre back cell, inside both the rewarded band and the
     * punished one, and the third scripted draw of each fixture is step 7's
     * tax rather than anything of step 4's.
     */
    @Test
    fun `winning the tackle count pays the smaller figure two times in three`() {
        val ahead = context(tackles = 11, opponentTackles = 10)
        val centreBack = Lineups.player(slot = 5, strength = 50)

        assertRating(6.4, rate(centreBack, context = ahead, rng = ScriptedInts(1, 1, 1)), "plain bonus")
        assertRating(6.7, rate(centreBack, context = ahead, rng = ScriptedInts(0, 1, 1)), "raised bonus")
        assertRating(7.0, rate(centreBack, context = ahead, rng = ScriptedInts(1, 0, 1)), "plain bonus and the band bonus")
    }

    /**
     * The rewarded band and the punished band are different, and a lateral is
     * the cell that proves it.
     *
     * Cell 2 sits inside two to nine and outside three to eight, so it is
     * paid for a tackle count won and charged nothing extra for one lost. The
     * draw counts are asserted as well as the figures, because a band that
     * had been widened to cover the lateral would spend a draw here even in
     * the matches where it happened to miss.
     */
    @Test
    fun `a lateral is rewarded for a tackle count won and never punished for one lost`() {
        val ahead = context(tackles = 11, opponentTackles = 10)
        val behind = context(tackles = 10, opponentTackles = 11)
        val lateral = Lineups.player(slot = 2, strength = 50)

        val won = ScriptedInts(1, 0, 1)
        assertRating(7.0, rate(lateral, context = ahead, rng = won), "the plain bonus and the band bonus")
        assertEquals(3, won.draws, "the main draw, the rewarded band draw and the tax")

        val lost = ScriptedInts(1)
        assertRating(5.3, rate(lateral, context = behind, rng = lost), "the flat penalty and nothing else")
        assertEquals(1, lost.draws, "only the tax, since the punished band starts at cell three")
    }

    /**
     * The punished band charges a centre back on a chance of one in four,
     * where every reward is one in three.
     *
     * A draw of three is inside the bound of four and outside a bound of
     * three, so scripting it both proves the chance is a quarter, by missing,
     * and would have thrown outright against a third.
     */
    @Test
    fun `a centre back is punished on one chance in four`() {
        val behind = context(tackles = 10, opponentTackles = 11)
        val centreBack = Lineups.player(slot = 5, strength = 50)

        assertRating(4.7, rate(centreBack, context = behind, rng = ScriptedInts(0, 1)), "the punishment landed")
        assertRating(5.3, rate(centreBack, context = behind, rng = ScriptedInts(3, 1)), "a draw of three misses")
    }

    /**
     * The other half of the same proof: a draw of three offered to a one in
     * three chance is outside its bound and the scripted generator refuses
     * it, so the rewards cannot quietly be quarters either.
     */
    @Test
    fun `a draw of three is outside the bound every reward chance uses`() {
        val ahead = context(tackles = 11, opponentTackles = 10)
        val centreBack = Lineups.player(slot = 5, strength = 50)

        assertFailsWith<IllegalStateException>("the main tackle draw must be bounded by three") {
            rate(centreBack, context = ahead, rng = ScriptedInts(3, 1, 1))
        }
    }

    /**
     * The holding cells are the one band step 4 reads on both sides of the
     * comparison, and their two chances are a third and a quarter.
     *
     * Cell 12 is in the midfield band as well, but the possession counters
     * are level here so step 2 spends nothing.
     */
    @Test
    fun `a holding cell is paid and charged on both sides of the tackle count`() {
        val ahead = context(tackles = 11, opponentTackles = 10)
        val behind = context(tackles = 10, opponentTackles = 11)
        val holding = midfielder(12)

        val won = ScriptedInts(1, 0, 1)
        assertRating(7.0, rate(holding, context = ahead, rng = won), "plain bonus and holding bonus")
        assertEquals(3, won.draws, "the main draw, the holding draw and the tax")

        val lost = ScriptedInts(0, 1)
        assertRating(4.7, rate(holding, context = behind, rng = lost), "flat penalty and holding penalty")
        assertEquals(2, lost.draws, "the holding draw and the tax, with no main draw on this side")
    }

    /**
     * The goalkeeper's cell is inside step 4's band and inside none of its
     * three sub bands, so he takes the comparison and nothing else.
     */
    @Test
    fun `the keeper takes the tackle comparison and none of its band terms`() {
        val ahead = context(tackles = 11, opponentTackles = 10)
        val rng = ScriptedInts(1)

        assertRating(6.1, rate(keeper(), context = ahead, rng = rng), "six point eight, plus zero point six, less one point three")
        assertEquals(1, rng.draws, "the main draw alone, and no tax for cell one")
    }

    /**
     * A cell above thirteen reads nothing of step 4 at all.
     */
    @Test
    fun `a cell outside the defensive band reads nothing of step four`() {
        val ahead = context(tackles = 11, opponentTackles = 10)
        val rng = ScriptedInts()

        assertRating(5.8, rate(forward(), context = ahead, rng = rng), "the base alone")
        assertEquals(0, rng.draws, "no cell above thirteen may draw here")
    }

    /**
     * Step 6's goals conceded chain, read as a chain.
     *
     * Three conceded is the case that matters. The chain tests five, then
     * four, then two, and never three, so three costs exactly what two costs;
     * a table written as a partition with a rung of its own for three would
     * give a different figure. Six conceded is asserted for the same reason
     * at the other end, since the top rung reaches upwards without limit.
     *
     * Five shots on target faced in every fixture, so the empty afternoon
     * penalty stays out of the way.
     */
    @Test
    fun `the goals conceded chain has no rung for three`() {
        val busyEnough = { conceded: Int -> PlayerTally(shotsOnTargetFaced = 5, goalsConceded = conceded) }

        assertRating(8.0, rate(keeper(), busyEnough(0)), "a clean sheet pays one")
        assertRating(6.5, rate(keeper(), busyEnough(1)), "one conceded costs half")
        assertRating(6.0, rate(keeper(), busyEnough(2)), "two conceded cost one")
        assertRating(6.0, rate(keeper(), busyEnough(3)), "three cost what two cost, since no rung names three")
        assertRating(5.5, rate(keeper(), busyEnough(4)), "four cost one and a half")
        assertRating(5.0, rate(keeper(), busyEnough(5)), "five cost two")
        assertRating(5.0, rate(keeper(), busyEnough(9)), "and nine cost two as well")
    }

    /**
     * The rest of step 6, read one term at a time.
     *
     * A goalkeeper who faced nothing at all is charged the empty afternoon
     * penalty on top of the clean sheet reward the chain has already paid
     * him, so he finishes the step below where he started rather than above
     * it.
     */
    @Test
    fun `the keeper terms are read one by one`() {
        assertRating(5.5, rate(keeper(), PlayerTally()), "nothing faced: minus zero point eight, plus one, minus one and a half")
        assertRating(
            8.4,
            rate(keeper(), PlayerTally(shotsOnTargetFaced = 5, savedPenalties = 2, goalsConceded = 3)),
            "two penalties saved at one point two each",
        )
    }

    /**
     * The only tier of the busy chain that can ever fire, and the two above
     * it that were not ported.
     *
     * The count is the opposing side's shots, which lives on the match, and
     * the keeper's own tally is held fixed at five on target and five
     * conceded throughout so that nothing but the tier can move the answer.
     *
     * More than ten is a strict comparison, so ten pays nothing and eleven
     * pays two tenths. Sixteen and twenty one are the first counts past the
     * two tiers section 3.15 item 16 says are unreachable, and each must be
     * worth exactly what eleven is worth and not a tenth more, which is what
     * proves neither tier was ported.
     */
    @Test
    fun `only the first busy tier fires and the two above it were not ported`() {
        val tally = PlayerTally(shotsOnTargetFaced = 5, goalsConceded = 5)
        val shotAt = { shots: Int -> context(opponentShots = shots) }

        assertRating(5.0, rate(keeper(), tally, shotAt(10)), "ten shots faced pays no busy bonus")
        assertRating(5.2, rate(keeper(), tally, shotAt(11)), "eleven pays the busy bonus")
        assertRating(5.2, rate(keeper(), tally, shotAt(16)), "sixteen pays exactly what eleven pays")
        assertRating(5.2, rate(keeper(), tally, shotAt(21)), "and so does twenty one")
    }

    /**
     * The busy tier counts every shot the other side took and not the
     * goalkeeper's own count of the ones on target.
     *
     * Both fixtures are deliberately impossible as matches, which is what
     * makes them discriminating: a goalkeeper cannot face twelve shots on
     * target out of three shots, and the two numbers can only be told apart
     * by a fixture in which they disagree. Reading the on target counter
     * would pay the first and refuse the second, which is the exact opposite
     * of what section 3.14 asks for, and every honest fixture in this file
     * would have agreed with both readings.
     */
    @Test
    fun `the busy tier reads the side's shots and not the keeper's on target count`() {
        assertRating(
            9.4,
            rate(keeper(), PlayerTally(shotsOnTargetFaced = 12), context(opponentShots = 3)),
            "twelve on target out of three shots pays no busy bonus",
        )
        assertRating(
            7.6,
            rate(keeper(), PlayerTally(shotsOnTargetFaced = 2), context(opponentShots = 20)),
            "two on target out of twenty shots does pay it",
        )
    }

    /**
     * Step 6 belongs to the goalkeeper's cell alone.
     */
    @Test
    fun `an outfielder reads none of the keeper terms`() {
        val tally = PlayerTally(shotsOnTargetFaced = 12, savedPenalties = 1, goalsConceded = 4)

        assertRating(5.8, rate(forward(), tally), "the base alone, with every keeper counter ignored")
    }

    /**
     * Step 6 reads the goalkeeper's own goals conceded and step 7 reads the
     * side's, and they are two different counters.
     *
     * A goalkeeper who came on at the interval and let nothing in behind a
     * side that had already conceded three is the case that separates them:
     * step 6 pays him for his own clean sheet and step 7 charges him three
     * tenths for the side's three.
     */
    @Test
    fun `the keeper is paid on his own sheet and charged on his side's`() {
        val leaky = context(goalsFor = 0, goalsAgainst = 3)
        val tally = PlayerTally(shotsOnTargetFaced = 4, goalsConceded = 0)

        assertRating(
            6.7,
            rate(keeper(), tally, context = leaky),
            "six for a defeat, less zero point eight, plus zero point eight, plus one, less zero point three",
        )
    }

    /**
     * Step 7's clean sheet clause, whose second bonus covers two to nine and
     * whose third covers eleven to thirteen.
     *
     * Cell one collects the flat bonus and neither band, cell five collects
     * the flat bonus and the two to nine band with no draw at all, and cell
     * twelve collects the flat bonus and draws for the eleven to thirteen
     * band.
     */
    @Test
    fun `a clean sheet pays the defensive cells by band`() {
        val clean = context(goalsFor = 1, goalsAgainst = 0)

        val keeperRng = ScriptedInts()
        assertRating(
            7.5,
            rate(keeper(strength = 50), PlayerTally(shotsOnTargetFaced = 4), context = clean, rng = keeperRng),
            "six for a win, plus step six's one point zero, plus step seven's flat zero point five",
        )
        assertEquals(0, keeperRng.draws, "cell one is in no band of step seven and pays no tax")

        val centreBack = ScriptedInts(1)
        assertRating(
            7.0,
            rate(Lineups.player(slot = 5, strength = 50), context = clean, rng = centreBack),
            "six for a win, plus zero point five and zero point five",
        )
        assertEquals(1, centreBack.draws, "the two to nine bonus is unconditional, so only the tax draws")

        val holding = ScriptedInts(0, 1)
        assertRating(
            7.0,
            rate(midfielder(12), context = clean, rng = holding),
            "six for a win, plus zero point five, plus the holding band on a hit",
        )
        assertEquals(2, holding.draws, "the holding band draw and the tax")
    }

    /**
     * Step 7's goals against clause, which starts at two and then charges for
     * every goal including the first.
     */
    @Test
    fun `conceding one costs nothing and conceding two costs two tenths`() {
        val centreBack = { Lineups.player(slot = 5, strength = 50) }

        assertRating(5.8, rate(centreBack(), context = context(1, 1), rng = ScriptedInts(1)), "one conceded is free")
        assertRating(5.0, rate(centreBack(), context = context(0, 2), rng = ScriptedInts(1)), "five point two less zero point two")
        assertRating(4.9, rate(centreBack(), context = context(0, 3), rng = ScriptedInts(1)), "five point two less zero point three")
    }

    /**
     * The tax of step 7, which is charged on nothing at all.
     *
     * It is drawn for whether the side kept a clean sheet or shipped three,
     * and it is drawn for a cell that has already been paid and for one that
     * has already been charged. The band is then pinned at all four of its
     * edges: cell one sits below it and cell fourteen above the whole step,
     * and cell thirteen is the last cell it reaches.
     */
    @Test
    fun `the tax is charged to cells two to thirteen on nothing whatever`() {
        val clean = context(goalsFor = 1, goalsAgainst = 0)
        val leaky = context(goalsFor = 0, goalsAgainst = 3)

        assertRating(
            6.6,
            rate(Lineups.player(slot = 5, strength = 50), context = clean, rng = ScriptedInts(0)),
            "seven point zero less the tax",
        )
        assertRating(
            4.5,
            rate(Lineups.player(slot = 5, strength = 50), context = leaky, rng = ScriptedInts(0)),
            "four point nine less the tax",
        )

        val belowTheBand = ScriptedInts()
        assertRating(4.5, rate(keeper(strength = 50), context = context(1, 1), rng = belowTheBand), "cell one is below the tax band")
        assertEquals(0, belowTheBand.draws, "so it draws nothing")

        val topOfTheBand = ScriptedInts(0)
        assertRating(
            5.4,
            rate(midfielder(13), context = context(1, 1), rng = topOfTheBand),
            "cell thirteen is the last cell the tax reaches",
        )
        assertEquals(1, topOfTheBand.draws, "and it drew for it")

        val aboveTheBand = ScriptedInts()
        assertRating(5.8, rate(midfielder(14), context = context(1, 1), rng = aboveTheBand), "cell fourteen is above step seven")
        assertEquals(0, aboveTheBand.draws, "so it draws nothing")
    }

    /**
     * The tax is one chance in three, proved the way step 4's quarter was: a
     * draw of three is outside its bound and the scripted generator refuses
     * it.
     */
    @Test
    fun `the tax is one chance in three`() {
        assertFailsWith<IllegalStateException>("the tax draw must be bounded by three") {
            rate(Lineups.player(slot = 5, strength = 50), rng = ScriptedInts(3))
        }
    }

    /**
     * Step 8's two badges, which are cumulative.
     *
     * Section 4.10 makes a red star a plain star as well, so the red star
     * fixture is the realistic one and is worth a full point rather than six
     * tenths.
     */
    @Test
    fun `a star is worth four tenths and a red star a full point`() {
        assertRating(6.2, rate(forward(star = true)), "a plain star")
        assertRating(6.4, rate(forward(topWorld = true)), "the red star badge on its own")
        assertRating(6.8, rate(forward(star = true, topWorld = true)), "a red star, who is always a star as well")
    }

    /**
     * Step 10's two rungs, which are exclusive and both strict.
     *
     * Fifteen minutes is charged the smaller penalty and fourteen the larger;
     * forty five minutes is charged nothing and forty four is charged the
     * smaller one.
     */
    @Test
    fun `the minutes penalty has two exclusive rungs and both are strict`() {
        val played = { minutes: Int -> PlayerTally(minutesPlayed = minutes) }
        val strong = forward(91)

        assertRating(4.7, rate(strong, played(14), context = context(2, 1)), "under fifteen costs two and a half")
        assertRating(5.7, rate(strong, played(15), context = context(2, 1)), "fifteen exactly costs one and a half")
        assertRating(5.7, rate(strong, played(44), context = context(2, 1)), "forty four costs one and a half")
        assertRating(7.2, rate(strong, played(45), context = context(2, 1)), "forty five costs nothing")
        assertRating(7.2, rate(strong, played(90), context = context(2, 1)), "and ninety costs nothing")
    }

    /**
     * The order of step 9's cap and step 10's minutes penalty.
     *
     * This fixture is worth twelve point six before either. The cap first
     * gives ten and then eight point five; the penalty first would give
     * eleven point one and then ten. Only the first of those two answers can
     * be produced by the order section 3.14 states, and the two differ by one
     * and a half, so this test fails outright against a swapped order rather
     * than agreeing with both.
     */
    @Test
    fun `the cap is applied before the minutes penalty and not after it`() {
        val scored = PlayerTally(matchGoals = 6, minutesPlayed = 40)

        assertRating(
            8.5,
            rate(forward(91), scored, context = context(3, 1)),
            "seven point two and five point four, capped at ten, then less one point five",
        )
    }

    /**
     * The order of step 8's badges and step 9's cap.
     *
     * This fixture is worth nine point four before the badges and ten point
     * four after them. Capping after the badges gives ten; capping before
     * them would leave ten point four standing, since nothing after step 9
     * caps anything again.
     */
    @Test
    fun `the cap is applied after the star bonuses and not before them`() {
        val scored = PlayerTally(matchGoals = 2, assists = 1)

        assertRating(
            10.0,
            rate(forward(91, star = true, topWorld = true), scored, context = context(3, 1)),
            "nine point four plus a full point for a red star, capped at ten",
        )
    }

    /**
     * The order of step 10's minutes penalty and step 11's floor.
     *
     * This fixture is worth two point seven before either, which is already
     * above the floor. The penalty first takes it to nought point two and the
     * floor then lifts it to two, where the short appearance rule zeroes it;
     * the floor first would find nothing to do and the penalty would leave
     * nought point two standing. Nought and nought point two are two
     * different answers and only one of them is section 3.14's.
     */
    @Test
    fun `the floor is applied after the minutes penalty and not before it`() {
        val brief = PlayerTally(ownGoals = 1, redCards = 1, minutesPlayed = 10)

        assertRating(
            0.0,
            rate(forward(30), brief, context = context(0, 1)),
            "five less two point three, less two point five, floored at two, then zeroed",
        )
    }

    /**
     * Step 11's tail, which asks two questions and not one.
     *
     * A player is zeroed only when he played under twenty minutes AND came to
     * rest exactly on the floor. Nineteen minutes on the floor is zeroed;
     * twenty minutes on the same floor is not; and nineteen minutes well
     * above the floor is not either. A reading that zeroed every short
     * appearance would fail the third, and one that zeroed everybody who
     * reached the floor would fail the second.
     */
    @Test
    fun `only a short appearance that stopped exactly on the floor is zeroed`() {
        val onTheFloor = { minutes: Int -> PlayerTally(ownGoals = 1, redCards = 1, minutesPlayed = minutes) }

        assertRating(0.0, rate(forward(30), onTheFloor(19), context = context(0, 1)), "nineteen minutes on the floor")
        assertRating(2.0, rate(forward(30), onTheFloor(20), context = context(0, 1)), "twenty minutes is not short enough")
        assertRating(
            5.7,
            rate(forward(91), PlayerTally(minutesPlayed = 19), context = context(2, 1)),
            "nineteen minutes well clear of the floor keeps its rating",
        )
    }

    /**
     * Step 9's patch for a negative rating, which cannot be observed in the
     * published mark and is implemented all the same.
     *
     * The patch replaces a negative rating with one, and step 11's floor is
     * two, so every value the patch can produce is lifted to the floor
     * immediately afterwards. A patch to nought, to one or to two therefore
     * publishes the same figure, and no fixture can tell them apart. What is
     * pinned here is the part that IS observable: a rating driven far below
     * nought comes back at the floor rather than staying negative, and a
     * short appearance in the same state is zeroed by the same rule that
     * zeroes any other player who stopped on the floor.
     *
     * That invisibility is a property of the classic figures and not of the
     * rule. It holds only while the floor sits above the patch, and no test
     * in this file varies the rule set, so a rule set that lowered the floor
     * below one would expose the patch at once and would need a fixture of
     * its own. The field is carried honestly for that reason rather than
     * folded into the floor.
     */
    @Test
    fun `a rating driven below nought comes back at the floor`() {
        val disastrous = PlayerTally(ownGoals = 3, missedPenalties = 1, redCards = 1)

        assertRating(2.0, rate(forward(30), disastrous, context = context(0, 1)), "minus three point nine, patched and floored")
        assertRating(
            0.0,
            rate(forward(30), disastrous.copy(minutesPlayed = 10), context = context(0, 1)),
            "the same man off after ten minutes has no rating at all",
        )
    }

    /**
     * The order the probabilistic terms consume the stream, pinned draw by
     * draw.
     *
     * A defensive midfielder in cell eleven, in a match his side had more of
     * the ball in, won the tackle count in and kept a clean sheet in, is the
     * one player who reaches all five chances of section 3.14 at once: step
     * 2's possession draw, step 4's main draw and its holding band draw, step
     * 7's holding band draw and step 7's tax.
     *
     * Each fixture below scripts a hit in exactly one position and a miss in
     * the other four, and the five totals are all different from each other:
     * seven point seven, eight point five, eight point eight, eight point
     * seven and seven point eight. Because no two of them agree, moving any
     * draw to any other position in the order changes at least one of the
     * five answers, which is what makes this a pin on the order and not on
     * the sum.
     */
    @Test
    fun `the five chances are consumed in one order and no other`() {
        val everything = context(
            goalsFor = 1,
            goalsAgainst = 0,
            possessionsWon = 41,
            opponentPossessionsWon = 40,
            tackles = 11,
            opponentTackles = 10,
        )
        val defensiveMidfielder = midfielder(11, style = PlayerStyle.DEFENSIVE)

        val expected = listOf(
            7.7 to "the first draw is step two's possession figure",
            8.5 to "the second is step four's main tackle figure",
            8.8 to "the third is step four's holding band",
            8.7 to "the fourth is step seven's holding band",
            7.8 to "the fifth is step seven's tax",
        )
        for ((position, cell) in expected.withIndex()) {
            val script = IntArray(expected.size) { if (it == position) 0 else 1 }
            val rng = ScriptedInts(*script)
            assertRating(cell.first, rate(defensiveMidfielder, context = everything, rng = rng), cell.second)
            assertEquals(expected.size, rng.draws, "every one of the five chances must be drawn")
        }
    }

    /**
     * The same player, with every chance hit and every chance missed, so that
     * the five terms are pinned at both extremes as well as one at a time.
     */
    @Test
    fun `the five chances at both extremes`() {
        val everything = context(
            goalsFor = 1,
            goalsAgainst = 0,
            possessionsWon = 41,
            opponentPossessionsWon = 40,
            tackles = 11,
            opponentTackles = 10,
        )
        val defensiveMidfielder = midfielder(11, style = PlayerStyle.DEFENSIVE)

        assertRating(
            8.7,
            rate(defensiveMidfielder, context = everything, rng = ScriptedInts(0, 0, 0, 0, 0)),
            "reduced possession bonus, raised tackle bonus, both bands, and the tax",
        )
        assertRating(
            8.2,
            rate(defensiveMidfielder, context = everything, rng = ScriptedInts(1, 1, 1, 1, 1)),
            "full possession bonus, plain tackle bonus, neither band, and no tax",
        )
    }

    private companion object {

        /**
         * Ratings are sums of tenths, which no binary double represents
         * exactly, so every figure here is compared within a tolerance far
         * finer than the smallest term the section has. The one place an
         * exact comparison is load bearing, step 11 asking whether a rating
         * came to rest on the floor, is exact by construction: the floor
         * itself is what put it there.
         */
        const val TOLERANCE = 1e-9
    }
}
