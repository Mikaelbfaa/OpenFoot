package org.openfoot.validation

import org.openfoot.engine.match.MatchEvent
import org.openfoot.engine.match.MatchReport
import org.openfoot.engine.match.SubstitutionReason
import org.openfoot.engine.match.simulateMatch
import org.openfoot.model.RuleSets
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What SanityCheckTest cannot see.
 *
 * SanityCheckTest is the section 3.16 comparison, and both of its fixtures
 * play with an empty bench, which is deliberate: section 3.16's own figures
 * describe sides that never replace anybody, and giving those two fixtures a
 * bench would stop them measuring what section 3.16 states. The cost is that
 * every figure in that class is completely blind to section 3.8's
 * substitutions. An empty bench fails canSubstitute, so both plans are blanked
 * at kick off, no window ever opens, and the whole substitution half of
 * section 3.8 could be deleted without moving a single number there.
 *
 * This class is the other half. It plays the same two equivalent sides over
 * the same sample with a full bench each and measures what only a bench can
 * reach: how many changes each side makes, how often it spends all five, and
 * how section 3.8's four windows and its two forced changes divide that total
 * between them. Every
 * band below is nought when the mechanism it names is switched off, so each is
 * a tripwire on one window rather than on substitutions in general.
 *
 * Its players are also of eleven different ages rather than all twenty five,
 * which is what reaches the age brackets of sections 3.9 and 3.8. The one that
 * matters here is section 3.8's duration of nought, which registers no injury
 * at all and is drawable only at twenty or under.
 *
 * The two sides are equivalent in everything except the ground, so the gap
 * between the home and the away figures below is section 3.8's own doing and
 * measures two defects at once. Section 3.15 item 11 has a home side that
 * actually changed somebody swallow the away side's window in the same minute,
 * and section 3.8 holds the two sides to different scores in both of its score
 * windows, the home side changing a goal down where the visitor waits for two
 * and chasing a draw where the visitor settles for it. Both push the same way,
 * and the home side makes about six tenths of a change a match more than the
 * away side does.
 */
class BenchedSanityCheckTest {

    @Test
    fun `the home side makes about three and nine tenths substitutions`() {
        val subs = MATCHES.mean { it.substitutionsBy(TeamSide.HOME) }
        assertTrue(subs in HOME_SUBSTITUTIONS, "home substitutions averaged $subs")
    }

    @Test
    fun `the away side makes about three and three tenths, fewer than the home side`() {
        val home = MATCHES.mean { it.substitutionsBy(TeamSide.HOME) }
        val away = MATCHES.mean { it.substitutionsBy(TeamSide.AWAY) }
        assertTrue(away in AWAY_SUBSTITUTIONS, "away substitutions averaged $away")
        assertTrue(
            away < home,
            "section 3.15 item 11 and section 3.8's two score thresholds both favour the home " +
                "side, so $away must sit under $home",
        )
    }

    @Test
    fun `a side spends all five in about a third of its matches`() {
        val spent = MATCHES.sumOf { report ->
            TeamSide.entries.count { report.substitutionsBy(it) == MAX_PER_SIDE }
        }
        val share = spent.toDouble() / (MATCHES.size * TeamSide.entries.size)
        assertTrue(share in ALL_FIVE_SPENT, "a side spent all five in $share of its matches")
    }

    /**
     * Each of the five reasons separately, so that one which stopped firing
     * altogether fails on its own line rather than being absorbed by the
     * total.
     *
     * Five reasons is not five windows. Section 3.8 names four windows, and
     * they reach the log under three of these reasons, because a routine
     * minute and the late minutes added to it are both taken off the same
     * tiredness scan. The other two reasons are section 3.8's forced changes,
     * which are not windows at all: they have no minute of their own, they
     * ignore the half of the clock, and they are the smallest here by a factor
     * of twenty.
     *
     * That count of four is a reading rather than something section 3.8 states
     * plainly: its own paragraph says three voluntary windows and then lists
     * two. See OPEN-QUESTIONS item 50, which carries the reconciliation and the
     * competing reading. Nothing measured here moves under either.
     */
    @Test
    fun `every one of section 3 8 s five substitution reasons fires at its own rate`() {
        for ((reason, band) in RATE_BY_REASON) {
            val rate = MATCHES.mean { report ->
                report.log.count { it is MatchEvent.Substitution && it.reason == reason }
            }
            assertTrue(rate in band, "$reason averaged $rate a match")
        }
    }

    @Test
    fun `no side ever spends more than its five`() {
        MATCHES.forEachIndexed { index, report ->
            for (team in TeamSide.entries) {
                assertTrue(
                    report.substitutionsBy(team) <= MAX_PER_SIDE,
                    "$team spent ${report.substitutionsBy(team)} in match $index",
                )
            }
        }
    }

    /**
     * Section 3.8 says a duration of nought is not an injury at all: the man
     * leaves the pitch and is replaced like anybody else, but nothing is
     * logged and no days are ever charged to him.
     *
     * The assertion is an exact nought rather than a band, because the rule is
     * exact. The band beside it is what makes the nought mean something: the
     * duration of nought is drawable only at twenty or under, so a fixture
     * that never injured a young player would satisfy the first assertion by
     * never reaching the case at all. Over this sample about a quarter of the
     * injuries that do get logged fall on the nineteen and twenty year olds in
     * cells 11 and 13, which is the group section 3.8's injury table weights
     * most heavily, so the case is reached thousands of times.
     */
    @Test
    fun `no injury of no duration is ever logged`() {
        val logged = MATCHES.sumOf { report -> report.log.count { it is MatchEvent.Injury } }
        val young = MATCHES.sumOf { report ->
            report.log.count { it is MatchEvent.Injury && it.player.age <= YOUNGEST_INJURY_TERM }
        }
        val instant = MATCHES.sumOf { report ->
            report.log.count { it is MatchEvent.Injury && it.days == 0 }
        }
        assertEquals(0, instant, "injuries of no duration reached the log")
        val share = young.toDouble() / logged
        assertTrue(share in YOUNG_INJURY_SHARE, "$young of $logged logged injuries were young, $share")
    }

    /**
     * Every figure above is a mean over twenty thousand matches at fixed
     * seeds, so it means nothing unless the same seeds always play the same
     * matches. This is what says they do.
     *
     * Compared by rendering rather than by MatchReport equality, which is what
     * SanityCheckTest's own replay test can afford and this one cannot. A
     * substitution builds a new MatchPlayer for the man coming on, standing in
     * the cell he inherited, and MatchPlayer is deliberately reference equal,
     * so two runs of the identical match put different objects in their logs
     * and the two reports compare unequal however faithfully the second
     * reproduced the first. The rendering below names every player by his
     * identity instead, which is his index in the squad and stable across
     * runs, and it carries every field of every event, so a replay that agreed
     * on the rendering and disagreed on anything a reader of a match can see
     * is not possible.
     */
    @Test
    fun `the whole benched sample replays identically`() {
        val again = play()
        assertEquals(MATCHES.size, again.size)
        again.forEachIndexed { index, report ->
            assertEquals(MATCHES[index].rendered(), report.rendered(), "match $index")
        }
    }

    private companion object {

        /**
         * The same twenty thousand matches at the same seeds SanityCheckTest
         * uses, so that a figure here and a figure there are read off the same
         * draws and differ only by what the bench changed.
         */
        @SpecRef("3.16")
        const val SAMPLE = 20000L

        /** Section 3.8 gives each side five changes. Read, never written down. */
        @SpecRef("3.8")
        val MAX_PER_SIDE = RuleSets.CLASSIC.substitutions.maxPerSide

        /**
         * The oldest age section 3.8's injury duration lets draw a nought.
         * Read off the rule set's own first age term for the same reason
         * MAX_PER_SIDE is.
         */
        @SpecRef("3.8")
        val YOUNGEST_INJURY_TERM = RuleSets.CLASSIC.injuryRules.ageTerms.first().draws.last

        /**
         * Measured 3.89385 a match. Three windows of section 3.8 feed it and
         * two forced changes top it up; the split is in RATE_BY_REASON below.
         *
         * The ceiling of five is what keeps this well under the sum of the
         * windows a side draws minutes for: a side that has spent its five is
         * turned away by canSubstitute before the minute is even looked at,
         * and it spends them in more than four matches in ten.
         */
        @SpecRef("3.8")
        val HOME_SUBSTITUTIONS = 3.75..4.05

        /**
         * Measured 3.28515 a match, six tenths of a change under the home
         * side.
         *
         * Two things of section 3.8 put it there and neither is a strength
         * gap, since the two sides are identical in everything but the ground.
         * The score windows hold the away side to a worse score before it will
         * act, one goal against two at the interval and a defeat against a
         * draw in a chasing minute; and section 3.15 item 11 throws away the
         * away side's window outright in any minute the home side has already
         * changed somebody in.
         */
        @SpecRef("3.8")
        val AWAY_SUBSTITUTIONS = 3.15..3.45

        /**
         * Measured 0.31295 of all side matches, 8230 of the home side's twenty
         * thousand and 4288 of the away side's.
         *
         * The two halves of that split are the same asymmetry AWAY_SUBSTITUTIONS
         * describes, arriving at the ceiling instead of at the mean.
         */
        @SpecRef("3.8")
        val ALL_FIVE_SPENT = 0.295..0.330

        /**
         * Each window's own rate a match, both sides summed, as measured.
         *
         * Tiredness at 4.45345 is the largest by far, because its minutes are
         * the only ones with no score condition on them at all: a routine
         * minute changes somebody whenever the scan finds a tired man, whatever
         * the score. Chasing at 2.3217 comes next and is gated on the score.
         * The interval at 0.16765 is one window a match rather than several and
         * carries both a score condition and a coin.
         *
         * The two forced changes are last and are not windows at all. A
         * dismissal at 0.1253 and an injury at 0.1109 are section 3.8's
         * Consequences and ignore the half of the clock entirely. Neither runs
         * at the rate of the event that forces it, and for the dismissal the
         * gap is one rule rather than an exhausted bench. The AI keeps its
         * shape only when the vacated cell is at or below
         * sendingOffSacrificeMaxSlot, which the classic rules put at 13, so a
         * man sent off from cells 14 to 17 or 19 to 24 costs his side a player
         * and buys it nothing at all, and formation four stands four of its
         * eleven in those cells. Measured over this sample: 3236 dismissals
         * produced 2506 changes, so 22.6 per cent produced none, and 662 of
         * the 3236, which is 20.5 per cent, fell above cell 13. That rule is
         * almost the whole gap and a side with nobody left to bring on is the
         * remaining two points. The injury carries no such rule and is
         * replaced 96.8 per cent of the time.
         *
         * A map rather than five constants so the test walks them, and ordered,
         * because the failure message names the entry it stopped on.
         */
        @SpecRef("3.8")
        val RATE_BY_REASON: Map<SubstitutionReason, ClosedFloatingPointRange<Double>> = mapOf(
            SubstitutionReason.TIREDNESS to 4.30..4.61,
            SubstitutionReason.CHASING to 2.24..2.41,
            SubstitutionReason.HALF_TIME to 0.158..0.178,
            SubstitutionReason.SENDING_OFF to 0.118..0.133,
            SubstitutionReason.INJURY to 0.104..0.118,
        )

        /**
         * Measured 598 of the 2291 logged injuries, which is 0.261.
         *
         * Section 3.8's injury table gives the group of cells 10 to 13 a 29.8
         * per cent share, the largest of the seven, and this fixture stands its
         * nineteen and twenty year olds in exactly those two cells. The share
         * measured sits a little under that group's own weight because a young
         * man who is injured is replaced by a reserve who is usually older, so
         * the group ages as a match goes on.
         */
        @SpecRef("3.8")
        val YOUNG_INJURY_SHARE = 0.24..0.28

        /**
         * The one fixture MATCHES is played against, held so that the replay
         * test can replay the same object rather than an equal one. See that
         * test's own docstring.
         */
        @SpecRef("3.8")
        val BENCHED: EqualSides.BenchedFixture by lazy { EqualSides.benchedFixture() }

        @SpecRef("3.8")
        val MATCHES: List<MatchReport> by lazy { play() }

        fun play(): List<MatchReport> = (1L..SAMPLE).map {
            simulateMatch(BENCHED.setup, SplitMix64Rng(it), BENCHED.homeBench, BENCHED.awayBench)
        }

        fun MatchReport.substitutionsBy(team: TeamSide): Int =
            log.count { it is MatchEvent.Substitution && it.side == team }

        /**
         * A whole match written out with every player named by identity.
         *
         * Every field of every event is carried, so two matches with the same
         * rendering are the same match as far as anything can observe. The
         * shooter is the one nullable one, and a tick with nobody eligible to
         * shoot renders as null rather than being dropped, since section 3.6c
         * lets that happen and a replay that turned it into a real shooter
         * would have to be caught.
         */
        fun MatchReport.rendered(): String {
            val events = log.joinToString(";") { event ->
                when (event) {
                    is MatchEvent.Shot ->
                        "shot ${event.minute} ${event.side} ${event.shooter?.id?.value} " +
                            "${event.onTarget} ${event.scored}"

                    is MatchEvent.Tackle -> "tackle ${event.minute} ${event.side}"
                    is MatchEvent.MisplacedPass -> "pass ${event.minute} ${event.side}"
                    is MatchEvent.PossessionWon -> "duel ${event.minute} ${event.side}"
                    is MatchEvent.Booking ->
                        "booking ${event.minute} ${event.side} ${event.player.id.value}"

                    is MatchEvent.SendingOff ->
                        "off ${event.minute} ${event.side} ${event.player.id.value} " +
                            "${event.secondYellow}"

                    is MatchEvent.Injury ->
                        "injury ${event.minute} ${event.side} ${event.player.id.value} " +
                            "${event.days} ${event.permanentStrengthLoss}"

                    is MatchEvent.Substitution ->
                        "sub ${event.minute} ${event.side} ${event.off.id.value} " +
                            "${event.on.id.value} ${event.on.slot.value} ${event.reason}"
                }
            }
            return "$startingPossessor ${clock.firstHalfMinutes} ${clock.secondHalfMinutes} " +
                "$homeGoals $awayGoals $events"
        }

        fun List<MatchReport>.mean(of: (MatchReport) -> Int): Double =
            sumOf { of(it).toDouble() } / size
    }
}
