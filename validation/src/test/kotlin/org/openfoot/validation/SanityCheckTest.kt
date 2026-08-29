package org.openfoot.validation

import org.openfoot.engine.match.MatchEvent
import org.openfoot.engine.match.MatchRatings
import org.openfoot.engine.match.MatchReport
import org.openfoot.engine.match.MatchSetup
import org.openfoot.engine.match.playerRatings
import org.openfoot.engine.match.simulateMatch
import org.openfoot.model.GoalType
import org.openfoot.model.RuleSets
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Section 3.16 says what a faithful reimplementation must produce over many
 * matches between two equivalent sides. Every test below is a claim about the
 * assembled engine rather than about one formula, which is what makes this the
 * first real check that the parts fit together.
 *
 * Deterministic, so it cannot be flaky: the same seeds always play the same
 * twenty thousand matches. A failure here is a real regression, never noise.
 *
 * Every band was set around a measured value and then checked against what
 * section 3.16 predicts. Three of them do not agree with it, and each one is
 * recorded rather than papered over. The three discipline bands at the end
 * used to be a fourth, fifth and sixth disagreement and are not any more: see
 * the paragraph on them below.
 *
 * The tick count is 94, not the 92 of section 3.16, because section 3.1's own
 * stoppage draws average 94. See open question 28.
 *
 * The displayed possession share is 53.3 per cent, not 55. See open question
 * 29.
 *
 * The shot volumes and the goal averages fall short of section 3.16's figures
 * whenever the lineup does not fill every line to its fixed divisor, which a
 * four four two does not. See open question 30, and the last test in this file,
 * which reproduces section 3.16's figures exactly once the lines are full.
 *
 * Every figure below was re-measured when section 3.8's per minute roll was
 * wired into the match, and the movements it produced are small and all in one
 * direction. Not one band had to move to contain its new value, so none did;
 * what changed is the measurement each docstring quotes and the paragraph
 * naming the direction. The reasoning behind those movements is one argument
 * and is set out here once rather than nine times.
 *
 * Over this sample section 3.8 produces about 1.29 bookings, 0.17 dismissals
 * and 0.12 injuries per match. A booking changes nothing a tick reads. A
 * dismissal and an injury both take a player off the pitch, and both sides
 * play here with an empty bench, so nobody is ever replaced: about 0.28 men
 * leave per match and stay gone.
 *
 * That empty bench is deliberate, and it is also this class's blind spot. It
 * is the pairing section 3.16's own figures were taken against, so the fixture
 * keeps it; the cost is that no substitution of section 3.8 can ever fire
 * here, because an empty bench fails canSubstitute and both plans are blanked
 * at kick off. Not one figure below moves when the substitution rules change.
 * BenchedSanityCheckTest is the other half of the check and measures only what
 * a bench can reach.
 *
 * A missing player costs his own line one over its fixed divisor, because
 * section 3.4 divides by a constant rather than by the number of men left.
 * That cuts the side's own attack, which lowers its own shots, and cuts its own
 * defence, which raises its opponent's. Which of the two dominates is settled
 * by section 3.8's own risk group tables, and they put their weight at the
 * back: a direct red falls on cells 10 to 13 with probability 39.5 per cent,
 * on 3 to 8 with 25, on 14 to 17 with 15, on 8 to 9 with 10, on 2 to 3 with 5,
 * and on the forwards of 19 to 24 with only 5. The man who leaves is therefore
 * usually a defender or a midfielder and hardly ever a forward, so the
 * dominant effect is a weakened defence letting the opponent shoot more, and
 * the total shot count rises rather than falls.
 *
 * Which side gains is settled by the victim draw, section 3.8's one piece of
 * refereeing bias: the away side is drawn 56 per cent of the time and the home
 * side 44. Over this sample the home side collected 11299 of the 25700
 * bookings, which is 43.97 per cent, so the draw behaves as the spec says. The
 * away side therefore loses men more often, and every figure below moves the
 * way that implies: the home shot volume, the home goals, the home conversion
 * and the home possession share all rise slightly, and the away figures all
 * fall slightly.
 *
 * Section 3.7's goal typing then landed on top of all of that and moved
 * nothing at all, to the last digit of every figure below, which is the
 * outcome it had to have. The typing draws from a stream of its own, a
 * sibling of the tick's under the same minute, so it cannot move a draw the
 * tick makes; and it only decides who a goal is credited to, never whether
 * one was scored. Neither side here is human managed either, so section
 * 3.7's one path that can take a goal off the scoreboard, the penalty handed
 * to section 3.10, is out of reach. A goal count or a shot count moving with
 * that change would have been a wiring fault rather than a figure to
 * re-measure, and none did.
 *
 * The last three tests pin the discipline bullet of section 3.16 itself, and
 * they are the one part of this class that now agrees with it. They used to
 * be three disagreements: section 3.16 asked for two to three bookings, a
 * dismissal every eight to twelve matches and an injury every six to ten per
 * side, and this engine produced none of the three. Section 3.16 has since
 * been checked against the original and corrected, and what it asks for now is
 * what section 3.8's own tables give and what these three tests measure. Open
 * question 46 carries the whole argument and the arithmetic; the summary is
 * that the old figures described a chain drawn once per side per minute, and
 * the original draws a single victim side per minute, exactly as section 3.8
 * has always said.
 *
 * Two of the three moved slightly when the six corrections of section 3.8
 * landed on top of that, and both moves are the same correction: the counter
 * a second yellow used to feed is now fed only by a direct red, so the
 * overwrite that reads it fires less often, and the overwrite raises the
 * booking threshold. Fewer overwrites means more bookings, 1.28375 up to
 * 1.285, and more bookings means marginally more second yellows, one every
 * 6.038647342995169 matches down to one every 6.036824630244491. Direct reds
 * are untouched at 2463 of the sample, which is what that reading predicts.
 * The injury rate did not move at all, and could not have: the one correction
 * that touches injuries stops a duration of nought reaching the log, and a
 * duration of nought needs a player of twenty or under, which this fixture
 * does not have. BenchedSanityCheckTest does.
 */
class SanityCheckTest {

    @Test
    fun `a match runs about ninety four minutes`() {
        val ticks = MATCHES.mean { it.clock.totalMinutes }
        assertTrue(ticks in TICKS, "mean tick count was $ticks")
    }

    @Test
    fun `the home side takes about fifteen and a third shots`() {
        val shots = MATCHES.mean { it.stats.home.shots }
        assertTrue(shots in HOME_SHOTS, "home shots averaged $shots")
    }

    @Test
    fun `the away side takes about eleven and nine tenths shots`() {
        val shots = MATCHES.mean { it.stats.away.shots }
        assertTrue(shots in AWAY_SHOTS, "away shots averaged $shots")
    }

    @Test
    fun `the home side takes more shots than the away side`() {
        val home = MATCHES.mean { it.stats.home.shots }
        val away = MATCHES.mean { it.stats.away.shots }
        assertTrue(home > away, "home took $home shots against an away side on $away")
    }

    @Test
    fun `both sides score about a third over one goal`() {
        val home = MATCHES.mean { it.homeGoals }
        val away = MATCHES.mean { it.awayGoals }
        assertTrue(home in GOALS, "home goals averaged $home")
        assertTrue(away in GOALS, "away goals averaged $away")
    }

    @Test
    fun `the home shot volume advantage is cancelled by the worse conversion`() {
        val home = MATCHES.mean { it.homeGoals }
        val away = MATCHES.mean { it.awayGoals }
        assertTrue(
            abs(home - away) < GOAL_GAP_LIMIT,
            "the inverted home advantage should nearly cancel, got $home against $away",
        )
    }

    @Test
    fun `the home side converts worse than the away side`() {
        val homeRate = MATCHES.mean { it.homeGoals } / MATCHES.mean { it.stats.home.shots }
        val awayRate = MATCHES.mean { it.awayGoals } / MATCHES.mean { it.stats.away.shots }
        assertTrue(
            homeRate < awayRate,
            "the classic rules invert home advantage, so $homeRate must be under $awayRate",
        )
        assertTrue(homeRate in HOME_CONVERSION, "home conversion was $homeRate")
        assertTrue(awayRate in AWAY_CONVERSION, "away conversion was $awayRate")
    }

    @Test
    fun `the displayed possession favours the home side`() {
        val share = MATCHES.sumOf { it.stats.homePossessionShare() } / MATCHES.size
        assertTrue(share in POSSESSION_SHARE, "home possession share was $share")
    }

    @Test
    fun `every match is internally consistent`() {
        MATCHES.forEachIndexed { index, result ->
            val events = with(result.stats) {
                home.shots + away.shots + home.tackles + away.tackles +
                    home.misplacedPasses + away.misplacedPasses
            }
            assertEquals(result.clock.totalMinutes, events, "match $index")
        }
    }

    /**
     * Replays against EQUAL_SIDES_SETUP, the same MatchSetup instance MATCHES
     * was built from, rather than a fresh EqualSides.setup(). MatchPlayer is
     * deliberately reference equal, not value equal (see MatchSide.kt), so
     * two independently built lineups would make every match in again
     * compare unequal to its counterpart in MATCHES by shooter reference
     * alone, whatever the replay actually produced. Sharing the setup means
     * the only way this assertion fails is a genuine divergence.
     */
    @Test
    fun `the whole sample replays identically`() {
        val again = play(EQUAL_SIDES_SETUP)
        assertEquals(MATCHES.size, again.size)
        again.forEachIndexed { index, result ->
            assertEquals(MATCHES[index], result, "match $index")
        }
    }

    @Test
    fun `a match produces a couple of yellow cards`() {
        val yellows = MATCHES.mean { it.log.count { event -> event is MatchEvent.Booking } }
        assertTrue(yellows in YELLOWS, "yellows averaged $yellows")
    }

    @Test
    fun `a sending off is rare`() {
        val perMatch = MATCHES.mean { it.log.count { event -> event is MatchEvent.SendingOff } }
        assertTrue(1.0 / perMatch in MATCHES_PER_SENDING_OFF, "one every ${1.0 / perMatch} matches")
    }

    @Test
    fun `an injury is rarer still`() {
        val perSide = MATCHES.mean { it.log.count { event -> event is MatchEvent.Injury } } / 2
        assertTrue(1.0 / perSide in MATCHES_PER_INJURY, "one every ${1.0 / perSide} per side")
    }

    /**
     * The evidence behind open question 30.
     *
     * Section 3.16's shot volumes need the chance duel to compare an attack and
     * a defence of equal rating, which only happens when the defence has five
     * players over its divisor of five and the attack three over its divisor of
     * three. Put eleven players out that way and the figures of section 3.16
     * come back, rescaled from 46 possessions to the 47 that section 3.1
     * actually produces.
     *
     * That is what pins the shortfall in the four four two on section 3.4's
     * fixed divisors rather than on the assembly of the engine.
     */
    @Test
    fun `the figures of section 3 16 appear once every line sits on its divisor`() {
        val home = LINES_AT_DIVISOR.mean { it.stats.home.shots }
        val away = LINES_AT_DIVISOR.mean { it.stats.away.shots }
        val homeGoals = LINES_AT_DIVISOR.mean { it.homeGoals }
        val awayGoals = LINES_AT_DIVISOR.mean { it.awayGoals }
        assertTrue(home in FULL_LINE_HOME_SHOTS, "home shots averaged $home")
        assertTrue(away in FULL_LINE_AWAY_SHOTS, "away shots averaged $away")
        assertTrue(homeGoals in FULL_LINE_GOALS, "home goals averaged $homeGoals")
        assertTrue(awayGoals in FULL_LINE_GOALS, "away goals averaged $awayGoals")
    }

    /**
     * Section 3.7's own table, transcribed rather than derived: every drawn
     * type should land within a few tenths of a point of the percentage its
     * row names, open play's tail included in the open play share.
     */
    @Test
    fun `every goal type lands at about the share section 3 7 s table gives it`() {
        for ((type, band) in TYPE_SHARE) {
            val share = GOAL_EVENTS.count { it.type == type }.toDouble() / GOAL_EVENTS.size
            assertTrue(share in band, "$type was $share of ${GOAL_EVENTS.size} goals")
        }
    }

    /**
     * Section 3.6's coin is rand(100) > 80, so 81 per cent of the goals that
     * end up typed as open play should carry an assister.
     *
     * The population is the FINAL type, not the drawn one, because that is
     * what a reader of the log can see. The two can disagree in one direction
     * only: an own goal whose blamed defender draw comes back empty falls
     * back to open play by section 3.7's own patch, and that happens after
     * the assist coin was already skipped for it, so such a goal can never
     * carry an assister. DOWNGRADED_OWN_GOALS below counts exactly that case,
     * by the one mark it leaves on the event: a final open play goal is
     * ordinarily worth two match goal credits, and a goal that was drawn as
     * an own goal and only fell back to open play afterwards is worth one,
     * since the own goal's own typing credit is nought.
     */
    @Test
    fun `about eighty one per cent of open play goals carry an assister`() {
        val openPlay = GOAL_EVENTS.filter { it.type == GoalType.OPEN_PLAY }
        val share = openPlay.count { it.assister != null }.toDouble() / openPlay.size
        assertTrue(share in ASSIST_SHARE, "$share of ${openPlay.size} open play goals had an assister")
        assertEquals(
            DOWNGRADED_OWN_GOALS,
            openPlay.count { it.matchGoalCredits == 1 },
            "an open play goal worth one match goal credit is a downgraded own goal, and never has an " +
                "assister of its own",
        )
    }

    /**
     * The mean rating over every player section 3.14 rates, starters and
     * arrivals of both sides across the whole sample.
     */
    @Test
    fun `the mean rating sits where section 3 14 s base table and its adjustments put it`() {
        val mean = RATED_VALUES.sum() / RATED_VALUES.size
        assertTrue(mean in RATING_MEAN, "mean rating was $mean over ${RATED_VALUES.size} ratings")
    }

    /**
     * The share of ratings that come to rest exactly on the floor of 2,0.
     * Step 11's floor clamp runs before its zeroing clause, so a rating that
     * lands on the floor and also cleared the twenty minute mark stays
     * published at 2,0; one that lands on the floor without clearing it is
     * overwritten to nought and leaves this count. Every rating counted here
     * therefore cleared the twenty minute mark. This is distinct from the
     * exact nought share below: a rating can land on the floor from playing
     * the whole match badly, not only from a short appearance.
     */
    @Test
    fun `a share of ratings land exactly on the floor`() {
        val share = RATED_VALUES.count { it == RuleSets.CLASSIC.ratings.limits.floor }.toDouble() / RATED_VALUES.size
        assertTrue(share in RATING_FLOOR_SHARE, "$share of ${RATED_VALUES.size} ratings sat on the floor")
    }

    /**
     * The share of ratings that are a genuine nought: a player under twenty
     * minutes whose rating landed exactly on the floor, which section 3.14
     * step 11 turns into "no rating" rather than 2,0. Kept apart from a
     * benched reserve, who is not in RATED_VALUES at all because he is never
     * a key of MatchRatings; this share counts only players who did appear.
     */
    @Test
    fun `a share of ratings are a genuine nought for a short appearance on the floor`() {
        val share = RATED_VALUES.count { it == 0.0 }.toDouble() / RATED_VALUES.size
        assertTrue(share in RATING_ZERO_SHARE, "$share of ${RATED_VALUES.size} ratings were a genuine nought")
    }

    private companion object {

        /**
         * Twenty thousand matches. Large enough that the standard error of the
         * shot means is around two hundredths of a shot, which is an order of
         * magnitude under the narrowest band here, and cheap enough that the
         * whole class runs in a couple of seconds.
         */
        @SpecRef("3.16")
        const val SAMPLE = 20000L

        /**
         * Measured 93.99205. Section 3.1 predicts exactly 94: forty five
         * minutes a half plus a mean stoppage of one and of three. Section
         * 3.16 says 92, which this band deliberately excludes. See open
         * question 28.
         *
         * Unmoved by section 3.8, to the last digit, and it had to be: the
         * clock is drawn once from SETUP_STREAM, which section 3.8 neither
         * reads nor reorders. A movement here would have meant the seeding
         * changed rather than the match.
         */
        @SpecRef("3.1")
        val TICKS = 93.9..94.1

        /**
         * Measured 15.34915, against 15.30 derived from the tick count before
         * section 3.8: 46.996 possessions times 0.613733 for the possession
         * duel times 0.530435 for the chance duel. Section 3.16 says 16. See
         * open question 30.
         *
         * Up from 15.30865 with section 3.8, by four hundredths of a shot. The
         * away side is the victim of 56 per cent of the rolls and plays here
         * with an empty bench, so it is the side more often reduced to ten,
         * and the man it loses is usually a defender or a midfielder. The home
         * side is shooting at a defence that is a man short more often than
         * its own attack is.
         */
        @SpecRef("3.16")
        val HOME_SHOTS = 15.1..15.5

        /**
         * Measured 11.92575, against 11.89 derived the same way: 46.996 times
         * 0.55 times 0.46. Section 3.16 says 12.6. See open question 30.
         *
         * Down from 11.9297 with section 3.8, by four thousandths of a shot,
         * the other half of the victim draw's asymmetry: the away side loses
         * its own men more often than the home side loses theirs, so its own
         * attack is the one more often short. The movement is an order of
         * magnitude smaller than the home side's because the two effects on
         * this figure very nearly cancel.
         */
        @SpecRef("3.16")
        val AWAY_SHOTS = 11.75..12.10

        /**
         * Measured 1.33745 at home and 1.3225 away. Both are the shot volume
         * above times the conversion below. Section 3.16 says 1.4 for each,
         * and the difference is entirely the shot shortfall, since the
         * conversion rates do match. See open question 30.
         *
         * Home up from 1.3333 and away down from 1.32355 with section 3.8,
         * both of them following their own shot volumes and their own
         * conversion rates, which move the same way for the same reason.
         */
        @SpecRef("3.16")
        val GOALS = 1.28..1.38

        /**
         * Measured 0.01495. Section 3.6c predicts a near cancellation rather
         * than an exact one, so this is bounded rather than asserted equal, at
         * roughly four standard errors of the difference.
         *
         * Up from 0.00975 with section 3.8, and necessarily so: it is the gap
         * between two figures that moved in opposite directions. It is still
         * well inside the limit, which is unchanged.
         */
        @SpecRef("3.6c")
        const val GOAL_GAP_LIMIT = 0.05

        /**
         * Measured 0.0871351. The exact value with no goals yet scored is
         * 5.5 over 62.605, which is 0.087852, and the anti blowout ladder
         * pulls the average a little under it. Section 3.15 says 8.8 per cent,
         * so this one agrees.
         *
         * Up from 0.0870946 with section 3.8, by four hundred thousandths.
         * Section 3.8 changes no shot weight at all, so this moves only
         * because a side a man short concedes chances from better positions;
         * the movement is the smallest of any figure here.
         */
        @SpecRef("3.15")
        val HOME_CONVERSION = 0.085..0.089

        /**
         * Measured 0.1108945, against an exact 5.5 over 49.495, which is
         * 0.111122. Section 3.15 says 11.1 per cent, so this one agrees too,
         * and the pair of them is the inverted home advantage of the classic
         * rules.
         *
         * Down from 0.1109458 with section 3.8, the mirror of the home figure
         * above and for the mirror of its reason.
         */
        @SpecRef("3.15")
        val AWAY_CONVERSION = 0.109..0.113

        /**
         * Measured 0.5327066. Section 3.5's duel counter gives 53.3 per cent
         * for the home side. Section 3.16 says 55. The band excludes both 55
         * and the 50 that a share of ticks would give. See open question 29.
         *
         * Up from 0.5321090 with section 3.8. The possession duel compares the
         * two midfields, and the away side loses midfielders more often than
         * the home side does, so the home side wins more of them.
         */
        @SpecRef("3.5")
        val POSSESSION_SHARE = 0.525..0.540

        /**
         * Measured 16.34575, against 46.996 times 0.613733 times 0.565217,
         * which is 16.30. Section 3.16's 16, computed from 46 possessions,
         * would be 15.96.
         *
         * Up from 16.3187 with section 3.8, for the same reason as the four
         * four two's home figure and a little less strongly. This lineup's
         * midfield is already below section 3.4's minimum and collapsed to the
         * degenerate rating, so losing one of its two midfielders costs it
         * nothing at all and only the defenders and the forwards matter here.
         */
        @SpecRef("3.16")
        val FULL_LINE_HOME_SHOTS = 16.1..16.5

        /**
         * Measured 12.971, against 46.996 times 0.55 times 0.5, which is
         * 12.92. Section 3.16's 12.6 is the same arithmetic over 46
         * possessions rather than over the 47 section 3.1 produces.
         *
         * Up from 12.958 with section 3.8, which is the one figure here that
         * moves the opposite way to its four four two counterpart. The reason
         * is the midfield above: with the midfield already degenerate on both
         * sides, the only losses that count are a defender, which helps the
         * opponent, and a forward, which the risk groups make rare, so both
         * sides gain and neither loses.
         */
        @SpecRef("3.16")
        val FULL_LINE_AWAY_SHOTS = 12.80..13.15

        /**
         * Measured 1.42375 at home and 1.43705 away. Section 3.16 says 1.4.
         *
         * Both up with section 3.8, from 1.4212 and 1.43515, following the two
         * shot volumes above.
         */
        @SpecRef("3.16")
        val FULL_LINE_GOALS = 1.37..1.48

        /**
         * Measured 1.285. Section 3.8's own tables put the pre overwrite rate
         * near 1.5: a half spends about 15 minutes at phase nought, 15 at phase
         * one and 17 at phase two, and the effective threshold in each phase is
         * the table value plus the mean marking relief of 0.65 x 30 + 0.30 x 10
         * + 0.05 x 0 = 22.5 from section 3.12's draw. First half: 15/92.5 +
         * 15/62.5 + 17/52.5. Second half: 15/67.5 + 15/62.5 + 17/52.5. That sums
         * to about 1.51, and the overwrites that follow a second red or a first
         * injury only ever raise the threshold, which can only lower the count
         * further, so 1.285 measured under 1.51 predicted is consistent.
         *
         * Section 3.16, as corrected against the original, says about 1.3, and
         * this agrees with it. It used to say two to three, which this fell
         * well short of; see open question 46 for why that figure was wrong.
         *
         * Up from 1.28375 with the six corrections of section 3.8, because a
         * dismissal for a second yellow no longer feeds the counter the "two
         * reds" overwrite reads, and that overwrite raises the booking
         * threshold. Firing it less often leaves more bookings.
         */
        @SpecRef("3.16")
        val YELLOWS = 1.25..1.32

        /**
         * Measured one every 6.036824630244491 matches. Section 3.8's direct
         * red table alone gives 15/1200 + 15/900 + 17/800 + 15/800 + 15/700 +
         * 17/550, about 0.122 a match, or one every 8.2. But MatchEvent's own
         * documentation records that a second yellow logs a SendingOff as well
         * as a Booking, so this count is dismissals of both kinds, not direct
         * reds alone. That second path is real traffic on top of the 8.2
         * figure, and it is exactly why the measured rate is higher, not the
         * same.
         *
         * Over this sample that second path is 850 of the 3313 dismissals and
         * the direct reds are the other 2463.
         *
         * Section 3.16, as corrected against the original, says one every six,
         * and this agrees with it. It used to say one every eight to twelve,
         * which the measured rate was more frequent than the floor of; see
         * open question 46.
         *
         * More frequent than the 6.038647342995169 measured before the six
         * corrections of section 3.8, by one dismissal in twenty thousand
         * matches, and that one is a second yellow rather than a direct red:
         * the direct red count is unchanged at 2463 and only the bookings
         * moved, which is exactly what a correction confined to the booking
         * threshold's overwrites predicts.
         */
        @SpecRef("3.16")
        val MATCHES_PER_SENDING_OFF = 5.7..6.4

        /**
         * Measured one every 17.248814144027598 matches per side. Section
         * 3.8's own tables give 15/1500 + 15/1000 + 17/800 + 15/800 + 15/600 +
         * 17/600, about 0.118 a match across both sides, so about one every
         * 16.9 per side (0.059 a match per side), against no overwrite: the
         * limiarLesao threshold modifiers in section 3.8 only ever touch
         * limiarAmarelo, never the injury roll itself. The measured value
         * agrees with that derivation to within a percent.
         *
         * Section 3.16, as corrected against the original, says one every
         * seventeen per side, and this agrees with it. It used to say one every
         * six to ten, which this fell short of by nearly a factor of two; see
         * open question 46.
         *
         * Unmoved by the six corrections of section 3.8, to the last digit.
         * The only one of them that touches an injury stops a duration of
         * nought reaching the log, and section 3.8 draws a duration of nought
         * only for a player of twenty or under. Everybody here is twenty five,
         * so the case is out of reach and the count cannot have moved. It is
         * reachable in BenchedSanityCheckTest, which is where it is pinned.
         */
        @SpecRef("3.16")
        val MATCHES_PER_INJURY = 15.8..18.7

        /**
         * The one MatchSetup instance MATCHES is played against, held so that
         * the whole sample replays identically test can replay the same
         * fixture rather than an independently built one. MatchPlayer
         * compares by reference, not by value, so two separately built
         * lineups would make every replayed match compare unequal to the
         * original by shooter reference alone, regardless of what the replay
         * actually produced.
         */
        @SpecRef("3.16")
        val EQUAL_SIDES_SETUP: MatchSetup by lazy { EqualSides.setup() }

        /**
         * The sample: twenty thousand matches played with the four four two
         * lineup, which every test above except the last checks against
         * section 3.16's figures.
         */
        @SpecRef("3.16")
        val MATCHES: List<MatchReport> by lazy { play(EQUAL_SIDES_SETUP) }

        /**
         * The same sample size, played with the lineup whose defensive and
         * attacking lines exactly fill section 3.4's fixed divisors: five
         * defenders and three forwards.
         */
        @SpecRef("3.4")
        val LINES_AT_DIVISOR: List<MatchReport> by lazy { play(EqualSides.linesAtDivisorSetup()) }

        /**
         * Every Goal event of the MATCHES sample, home and away goals of both
         * sides folded together. About 1.3 goals a side a match over twenty
         * thousand matches puts this in the tens of thousands, which is what
         * makes even OLYMPIC's half a per cent band readable.
         */
        @SpecRef("3.7")
        val GOAL_EVENTS: List<MatchEvent.Goal> by lazy {
            MATCHES.flatMap { it.log }.filterIsInstance<MatchEvent.Goal>()
        }

        /**
         * Measured over 53199 goals: OPEN_PLAY 0.9079493975450666, PENALTY
         * 0.04791443448185116, FREE_KICK 0.029023101938006352, OWN_GOAL
         * 0.009793417169495668, OLYMPIC 0.00531964886558018.
         *
         * OPEN_PLAY's own band covers section 3.7's table read as 90 plus the
         * 0.5 tail, 90.5 per cent, and every other band covers its own row
         * exactly: 5, 3, 1 and 0.5. Every one of the five measured values sits
         * within about a tenth of its nominal figure, well inside a band set
         * at ten standard errors, so this is agreement rather than a finding:
         * the engine transcribes the table faithfully. Each band would fail
         * with the type's share collapsed to whatever the other bands
         * absorbed if that type's branch of typeOf stopped firing.
         */
        @SpecRef("3.7")
        val TYPE_SHARE: Map<GoalType, ClosedFloatingPointRange<Double>> = mapOf(
            GoalType.OPEN_PLAY to 0.895..0.920,
            GoalType.PENALTY to 0.038..0.058,
            GoalType.FREE_KICK to 0.021..0.037,
            GoalType.OWN_GOAL to 0.005..0.014,
            GoalType.OLYMPIC to 0.002..0.009,
        )

        /**
         * Measured 0.8107945840751936 of 48302 open play goals, against
         * section 3.6's rand(100) > 80, which is 81 per cent. Ten standard
         * errors wide, and it would collapse to nought if the coin were
         * never tossed at all.
         */
        @SpecRef("3.6")
        val ASSIST_SHARE = 0.792..0.830

        /**
         * Measured nought of the 48302 open play goals: every final open
         * play goal in this sample was worth two match goal credits, none
         * worth one. Section 3.7's own goal patch falls back to open play
         * only when the conceding side has nobody left at all to blame, on
         * ownGoalEligibleSlots = 1..25, which needs the whole eleven man
         * side gone from the pitch with nobody to replace them on this
         * fixture's empty bench. That is reachable in principle and
         * unreachable in practice over twenty thousand matches, and the
         * sample bears that out exactly: not one of the 521 own goals drawn
         * in this sample found an empty side to blame. This constant cannot
         * exercise the fallback branch itself, since a measured nought here
         * reads the same whether the branch is merely unreached or was
         * deleted outright; that branch, and its interaction with the
         * assist coin, is what GoalTypingTest's own scripted draws pin
         * directly, in its test an own goal that falls back to open play
         * has no assister, because the assist came first, the way
         * DisciplineChainTest covers BenchedSanityCheckTest's own rare edge.
         */
        @SpecRef("3.7")
        const val DOWNGRADED_OWN_GOALS = 0

        /**
         * Section 3.14's ratings for the whole MATCHES sample, one entry per
         * match, from the same seeds MATCHES itself was played at.
         *
         * playerRatings forks from the origin seed and never from a stream's
         * consumed state, so a fresh SplitMix64Rng of the same seed reproduces
         * the same ratings whether or not that seed's own generator already
         * played the match; MatchRatingsTest pins that directly.
         */
        @SpecRef("3.14")
        val RATINGS: List<MatchRatings> by lazy {
            MATCHES.mapIndexed { index, report ->
                report.playerRatings(RuleSets.CLASSIC, SplitMix64Rng(index + 1L))
            }
        }

        /**
         * Every individual rating of RATINGS, both sides of every match,
         * flattened for a mean and for the two share tests. Ordering carries
         * no meaning here, only the multiset of values.
         */
        @SpecRef("3.14")
        val RATED_VALUES: List<Double> by lazy {
            RATINGS.flatMap { it.home.values.map { rating -> rating.value } + it.away.values.map { rating -> rating.value } }
        }

        /**
         * Measured 6.100144090908976 over 440000 ratings, twenty two rated
         * players of both starting elevens across twenty thousand matches,
         * nobody arriving on this fixture's empty bench. The base table
         * alone puts a strength fifty player at 5.5 to 6.8 depending on
         * result and side, and every adjustment above and below that base
         * both raises and lowers it in roughly equal measure across the
         * whole squad, which is why the mean settles close to the middle of
         * the base table rather than drifting toward either clamp. The band
         * is a flat two tenths either side, an order of magnitude over what
         * a population of 440000 individually noisy ratings needs for its
         * mean to move at all.
         */
        @SpecRef("3.14")
        val RATING_MEAN = 6.0..6.2

        /**
         * Measured 7.795454545454545E-4 of 440000 ratings, 343 of them,
         * sitting exactly on the floor of 2,0. That is a player whose whole
         * eleven step sum landed at or below two before the floor caught it,
         * and who also cleared the twenty minute mark, since step 11's
         * zeroing clause overwrites every floor rating that stayed under it
         * and this count is read after that clause has already run.
         * SanityCheckTest's fixture has no bench, so an early event that
         * still leaves a player short of a full appearance can come from an
         * early sending off, an early own goal blamed on its author, or an
         * ordinary booking, since toPlayerTallies writes the same
         * protagonist minute for all three; bookings, at roughly 1,3 a
         * match, are the largest of the three sources in this fixture. It
         * can never come from a substitution, since this fixture plays with
         * no bench. Ten standard errors wide around the measured share, and
         * it would collapse to nought if step 11's floor clamp were removed,
         * since nothing below it could then survive without being lifted.
         */
        @SpecRef("3.14")
        val RATING_FLOOR_SHARE = 0.00035..0.00120

        /**
         * Measured 0.001884090909090909 of 440000 ratings, 829 of them, a
         * genuine nought: a player under twenty minutes whose rating came to
         * rest exactly on the floor. Step 11's zeroing clause runs after the
         * floor clamp and overwrites the floor value with nought, so every
         * one of these players is absent from the floor share above rather
         * than counted in it; the two shares are disjoint populations, which
         * is exactly why this one can be larger than the floor share despite
         * neither containing the other. Ten standard errors wide, and it
         * would collapse to nought if step 11's zeroing clause were removed,
         * leaving every one of these players published at 2,0 instead.
         */
        @SpecRef("3.14")
        val RATING_ZERO_SHARE = 0.0012..0.0026

        /**
         * One sample. The setup is immutable and carries no state, so building
         * it once and playing every seed against it gives the same matches as
         * rebuilding it per seed.
         */
        fun play(setup: MatchSetup): List<MatchReport> =
            (1L..SAMPLE).map { simulateMatch(setup, SplitMix64Rng(it)) }

        fun List<MatchReport>.mean(of: (MatchReport) -> Int): Double =
            sumOf { of(it).toDouble() } / size
    }
}
