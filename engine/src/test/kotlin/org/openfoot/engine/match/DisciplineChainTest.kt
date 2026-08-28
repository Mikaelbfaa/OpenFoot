package org.openfoot.engine.match

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.Rng
import org.openfoot.model.RuleSets
import org.openfoot.model.Slot
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Section 3.8's chain as a whole, rather than any of its parts.
 *
 * Every mechanism below is already pinned by a test of its own. What is
 * checked here is the wiring: which draws a minute makes, in which order, and
 * which of them a minute does not make because something earlier in the chain
 * already matched.
 *
 * The fixture is two four four twos of fifties under the classic rules, marking
 * light on both sides, on a clock of forty seven and forty six. In list order
 * the eleven stand in cells 1, 22, 24, 11, 13, 14, 16, 2, 9, 3, 5, and each
 * starter's identity is his cell number, so a scripted player draw can be read
 * off the formation by hand. The risk groups that matter here are g0, cells 10
 * to 13, which the formation fills with cells 11 and 13 in that order; g1,
 * cells 14 to 17, filled with 14 and 16; and g2, cells 3 to 8, filled with 3
 * and 5.
 *
 * Every minute the scripted tests use is minute 20 of the first half, whose
 * phase is the middle one, or minute 57, which is the tenth minute of the
 * second half and therefore in its first phase. That fixes the thresholds a
 * roll is made against: minute 20 rolls the yellow against 70, which is the
 * table's 40 plus the light marking's relief of 30, the red against 900 and
 * the injury against 1000; minute 57 rolls the yellow against 75, the red
 * against 800 and the injury against 800. A roll fires on a drawn 1, which is
 * section 3.8's own rand(N) == 1.
 */
class DisciplineChainTest {

    /**
     * The chain resolves at the first thing that matches and then stops.
     *
     * Four draws, and the scripted stream carries exactly four, so a fifth
     * would fail rather than pass quietly:
     *
     * 1. 56 for the victim side, which is above the home threshold of 55, so
     *    the home side is the victim
     * 2. 1 against the yellow threshold of 70, so a booking fires
     * 3. 0 for the risk group, which is inside the yellow table's first band
     *    and therefore g0, cells 10 to 13
     * 4. 0 for the player, the first of the two the home side has in that
     *    range, which is cell 11
     *
     * No red roll and no injury roll are made, which the exhausted stream and
     * the empty log beyond the one booking both say.
     */
    @Test
    fun `a booking ends the minute`() {
        val ints = ScriptedInts(56, 1, 0, 0)
        val after = state().disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(4, ints.draws, "a minute that books somebody makes exactly four draws")
        assertEquals(DisciplineCounts(yellows = 1), after.counts, "counts")

        val booking = after.log.single() as MatchEvent.Booking
        assertEquals(FIRST_HALF_MINUTE, booking.minute, "the minute the booking carries")
        assertEquals(TeamSide.HOME, booking.side, "the side drawn as this minute's victim")
        assertEquals(11, booking.player.slot.value, "the cell the drawn player stands in")

        assertEquals(mapOf(PlayerId(11) to 1), after.home.bookings, "home bookings")
        assertEquals(11, after.setup.home.lineup.size, "a booking takes nobody off the pitch")
        assertEquals(0, after.home.substitutionsUsed, "a booking spends no substitution")
    }

    /**
     * A player already booked is sent off rather than booked again, and both
     * events reach the log, because section 3.8's suspension rule counts a
     * sending off for a second yellow as a yellow as well. The sendingsOff
     * counter itself does not move, because only a direct red feeds the
     * overwrite that counter drives; the log is unchanged from before this
     * fix, only the counter feeding the threshold overwrite changes. See
     * section 3.8, the paragraph beginning "Os tres contadores que essas
     * sobrescritas leem", and OPEN-QUESTIONS item 39.
     *
     * The same four draws as the booking above, against a home side whose cell
     * 11 already carries one booking and a match that already carries one
     * yellow. The home bench is empty here, so the shape keeping rule of a
     * dismissal from cell 11 finds nobody to bring on and the side simply plays
     * on with ten; the cell 13 test below is the one that pins the sacrifice.
     */
    @Test
    fun `a second yellow logs a booking and a sending off`() {
        val ints = ScriptedInts(56, 1, 0, 0)
        val before = state(
            homeBench = emptyList(),
            homeBookings = mapOf(PlayerId(11) to 1),
            counts = DisciplineCounts(yellows = 1),
        )
        val after = before.disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(4, ints.draws, "a dismissal for a second yellow draws no more than a booking")
        assertEquals(
            DisciplineCounts(yellows = 2, sendingsOff = 0),
            after.counts,
            "a second yellow moves the yellow counter and leaves sendingsOff untouched",
        )

        val booking = after.log[0] as MatchEvent.Booking
        assertEquals(11, booking.player.slot.value, "the booking names the cell 11 player")
        val dismissal = after.log[1] as MatchEvent.SendingOff
        assertEquals(11, dismissal.player.slot.value, "the dismissal names the same player")
        assertTrue(dismissal.secondYellow, "the dismissal must be flagged as a second yellow")
        assertEquals(2, after.log.size, "a booking and a dismissal and nothing else")

        assertEquals(mapOf(PlayerId(11) to 2), after.home.bookings, "home bookings")
        assertEquals(10, after.setup.home.lineup.size, "the side is down to ten")
        assertNull(
            after.setup.home.lineup.firstOrNull { it.slot.value == 11 },
            "the dismissed player must be off the pitch",
        )
        assertEquals(0, after.home.substitutionsUsed, "an empty bench spends no substitution")
    }

    /**
     * A dismissal at the boundary cell costs the side a forward as well.
     *
     * Cell 13 is the highest cell section 3.8's shape keeping rule covers, so
     * the AI takes somebody from 18 to 25 off too and puts the most suitable
     * reserve into the vacated cell. Five draws:
     *
     * 1. 56 for the victim side, so the home side again
     * 2. 0 against the yellow threshold of 70, which is a miss
     * 3. 1 against the red threshold of 900, so a direct red fires
     * 4. 1 for the risk group, which is inside the red table's g0 band of 1 to
     *    79, cells 10 to 13
     * 5. 1 for the player, the second of the two the home side has in that
     *    range, which is cell 13
     *
     * Nothing after that draws at all. The forward is the first player in
     * lineup order standing in 18 to 25, which is cell 22, and the reserve is
     * decided by the cascade of section 5.4 rather than by strength: cell 13
     * asks for a defensive midfielder and the bench holds one.
     *
     * The side ends on ten with one substitution spent, which is the whole
     * point of the rule: it is not a replacement for the man sent off.
     */
    @Test
    fun `a sending off at cell thirteen costs a forward too`() {
        val ints = ScriptedInts(56, 0, 1, 1, 1)
        val after = state().disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(5, ints.draws, "the sacrifice itself draws nothing")
        assertEquals(DisciplineCounts(sendingsOff = 1), after.counts, "counts")

        val dismissal = after.log[0] as MatchEvent.SendingOff
        assertEquals(13, dismissal.player.slot.value, "the cell the dismissed player stands in")
        assertEquals(false, dismissal.secondYellow, "a direct red is not a second yellow")

        val swap = after.log[1] as MatchEvent.Substitution
        assertEquals(SubstitutionReason.SENDING_OFF, swap.reason, "the reason logged")
        assertEquals(22, swap.off.slot.value, "the first forward in lineup order comes off")
        assertEquals(MIDFIELD_RESERVE, swap.on.id, "the reserve who suits the vacated cell comes on")
        assertEquals(13, swap.on.slot.value, "he takes the cell the dismissed player left")
        assertEquals(2, after.log.size, "a dismissal and a substitution and nothing else")

        assertEquals(10, after.setup.home.lineup.size, "the side is down to ten")
        assertEquals(1, after.home.substitutionsUsed, "the sacrifice spends a substitution")
        assertNull(
            after.setup.home.lineup.firstOrNull { it.slot.value == 22 },
            "the sacrificed forward must be off the pitch",
        )
    }

    /**
     * A dismissal one cell above the boundary costs the side nothing but the
     * man.
     *
     * The same five draws as above except the risk group, which is 80 here and
     * therefore inside the red table's g1 band of 80 to 109, cells 14 to 17.
     * The player draw of 0 takes the first of the two the formation puts in
     * that range, cell 14, which is one above sendingOffSacrificeMaxSlot. The
     * bench is the same bench that answered the cell 13 case, so the only
     * difference between the two tests is the cell.
     */
    @Test
    fun `a sending off at cell fourteen costs only the player`() {
        val ints = ScriptedInts(56, 0, 1, 80, 0)
        val after = state().disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(5, ints.draws, "no draw is made once the cell is above the boundary")
        assertEquals(DisciplineCounts(sendingsOff = 1), after.counts, "counts")

        val dismissal = after.log.single() as MatchEvent.SendingOff
        assertEquals(14, dismissal.player.slot.value, "the cell the dismissed player stands in")

        assertEquals(10, after.setup.home.lineup.size, "the side is down to ten")
        assertEquals(0, after.home.substitutionsUsed, "no substitution is spent")
        assertEquals(3, after.home.bench.size, "the bench is untouched")
        assertTrue(
            after.setup.home.lineup.any { it.slot.value == 22 },
            "no forward is sacrificed for a cell above the boundary",
        )
    }

    /**
     * The third sacrifice range, wired end to end: a dismissed keeper on a
     * side with nobody in eighteen to twenty five or in fourteen to seventeen
     * still costs the side a man, from cells two to twenty five.
     *
     * Formation four cannot show this, since it occupies every one of section
     * 3.8's seven risk groups, so the lineup here is built by hand: cells 1,
     * 2, 9, 3, 4, 5, 6, 7, 8, 11 and 13, which is the same shape
     * SubstitutionTest's sacrificeTarget cases use directly. It carries the
     * keeper, six defenders and two holding midfielders, and nothing at all
     * from fourteen up.
     *
     * Five draws:
     *
     * 1. 56 for the victim side, so the home side
     * 2. 0 against the yellow threshold of 70, a miss
     * 3. 1 against the red threshold of 900, so a direct red fires
     * 4. 0 for the risk group, which is the red table's own band of nought to
     *    nought, the keeper
     * 5. 0 for the player, the only candidate the keeper's group ever offers,
     *    cell 1
     *
     * Nothing after that draws. Cell one is at or below
     * sendingOffSacrificeMaxSlot, so the shape keeping rule applies; the two
     * ordinary ranges hold nobody, and only because the dismissed man was the
     * keeper does the search fall back to the third range, cells two to
     * twenty five, and take cell two, the first man standing there. The
     * reserve who fills the keeper's own vacated cell is the bench centre
     * back, because the cascade of section 5.4 tries goalkeeper first, finds
     * none, and takes the centre back over the bench's midfielder and
     * forward.
     */
    @Test
    fun `a dismissed keeper reaches the third sacrifice range`() {
        val ints = ScriptedInts(56, 0, 1, 0, 0)
        val cells = listOf(1, 2, 9, 3, 4, 5, 6, 7, 8, 11, 13)
        val after = state(homeCells = cells).disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(5, ints.draws, "the sacrifice itself draws nothing")
        assertEquals(DisciplineCounts(sendingsOff = 1), after.counts, "counts")

        val dismissal = after.log[0] as MatchEvent.SendingOff
        assertEquals(1, dismissal.player.slot.value, "the keeper's own cell")

        val swap = after.log[1] as MatchEvent.Substitution
        assertEquals(SubstitutionReason.SENDING_OFF, swap.reason, "the reason logged")
        assertEquals(2, swap.off.slot.value, "the first man in the third range, cell two, is sacrificed")
        assertEquals(DEFENCE_RESERVE, swap.on.id, "the reserve who suits the vacated cell comes on")
        assertEquals(1, swap.on.slot.value, "he takes the cell the dismissed keeper left")
        assertEquals(2, after.log.size, "a dismissal and a substitution and nothing else")

        assertEquals(10, after.setup.home.lineup.size, "the side is down to ten")
        assertEquals(1, after.home.substitutionsUsed, "the sacrifice spends a substitution")
        assertNull(
            after.setup.home.lineup.firstOrNull { it.slot.value == 2 },
            "the sacrificed cell two player must be off the pitch",
        )
    }

    /**
     * The mirror of the case above, on the very same lineup: an outfielder's
     * dismissal never reaches the third range, so a side with nobody in the
     * two ordinary ranges sacrifices nobody at all.
     *
     * The same five draws except the risk group, which is 1 here and
     * therefore the red table's g0 band of 1 to 79, cells 10 to 13, and the
     * player draw, which takes the first of the lineup's two cells in that
     * range, cell 11.
     */
    @Test
    fun `an outfielder dismissed on the same lineup sacrifices nobody`() {
        val ints = ScriptedInts(56, 0, 1, 1, 0)
        val cells = listOf(1, 2, 9, 3, 4, 5, 6, 7, 8, 11, 13)
        val after = state(homeCells = cells).disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(5, ints.draws, "no draw is made once the search runs out of ranges")
        assertEquals(DisciplineCounts(sendingsOff = 1), after.counts, "counts")

        val dismissal = after.log.single() as MatchEvent.SendingOff
        assertEquals(11, dismissal.player.slot.value, "the cell the dismissed player stands in")

        assertEquals(10, after.setup.home.lineup.size, "the side is down to ten")
        assertEquals(0, after.home.substitutionsUsed, "an outfielder's dismissal reaches no third range")
        assertEquals(3, after.home.bench.size, "the bench is untouched")
    }

    /**
     * An injury is replaced, and its length costs the three duration draws
     * section 3.8 names, made before anything about the bench is decided.
     *
     * Nine draws:
     *
     * 1. 56 for the victim side, so the home side
     * 2. 0 against the yellow threshold of 70, a miss
     * 3. 0 against the red threshold of 900, a miss
     * 4. 1 against the injury threshold of 1000, so an injury fires
     * 5. 250 for the risk group, which is the first draw of the injury table's
     *    g2 band of 250 to 319, cells 3 to 8
     * 6. 1 for the player, the second of the two the home side has in that
     *    range, which is cell 5
     * 7. 3 for the short term term x
     * 8. 0 for the long term draw, which a twenty five year old does not use
     * 9. 50 for the severity, which is inside the band that adds nothing
     *
     * The length follows from the table rather than from this test: a twenty
     * five year old on a hundred energy takes the energy base of nought, the
     * constant of one for his bracket and no long term term at all, so four
     * days, and he is under thirty five so he loses no strength for good.
     *
     * The replacement is the bench centre back rather than the stronger bench
     * midfielder, because the cascade of section 5.4 walks the position before
     * it walks anything else and cell 5 asks for a centre back.
     */
    @Test
    fun `an injury is replaced and costs three duration draws`() {
        val ints = ScriptedInts(56, 0, 0, 1, 250, 1, 3, 0, 50)
        val after = state().disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(9, ints.draws, "an injury costs six chain draws and three duration draws")
        assertEquals(DisciplineCounts(injuries = 1), after.counts, "counts")

        val injury = after.log[0] as MatchEvent.Injury
        assertEquals(5, injury.player.slot.value, "the cell the injured player stands in")
        assertEquals(4, injury.days, "the days the table gives a twenty five year old on full energy")
        assertEquals(0, injury.permanentStrengthLoss, "nobody under thirty five loses strength for good")

        val swap = after.log[1] as MatchEvent.Substitution
        assertEquals(SubstitutionReason.INJURY, swap.reason, "the reason logged")
        assertEquals(5, swap.off.slot.value, "the injured player comes off")
        assertEquals(DEFENCE_RESERVE, swap.on.id, "the reserve who suits the vacated cell comes on")
        assertEquals(5, swap.on.slot.value, "he takes the injured player's cell")

        assertEquals(11, after.setup.home.lineup.size, "an injury is replaced, so the side keeps eleven")
        assertEquals(1, after.home.substitutionsUsed, "the replacement spends a substitution")
    }

    /**
     * Section 3.8's real keeper restriction, confirmed against the original to
     * run the opposite way from what the old spec text said: a reserve keeper
     * may not come on for an injured outfielder. The old text read as a filter
     * on the keeper's own cell instead, which SubstitutionTest's cascade test
     * now disproves directly; this is the refusal that actually exists, and it
     * lives in injure rather than in chooseReplacement. See section 3.8, the
     * paragraph beginning "A restricao de goleiro e o inverso do que se
     * poderia esperar", and OPEN-QUESTIONS item 41.
     *
     * The home bench holds nothing but a reserve keeper. Nine draws, the same
     * shape as the injury above:
     *
     * 1. 56 for the victim side, so the home side
     * 2. 0 against the yellow threshold of 70, a miss
     * 3. 0 against the red threshold of 900, a miss
     * 4. 1 against the injury threshold of 1000, so an injury fires
     * 5. 250 for the risk group, the injury table's g2 band of 250 to 319,
     *    cells 3 to 8
     * 6. 0 for the player, the first of the home side's two cells in that
     *    range, which is cell 3
     * 7. 3 for the short term x
     * 8. 0 for the long term draw, which a twenty five year old does not use
     * 9. 50 for the severity, inside the band that adds nothing
     *
     * The injury itself is logged exactly as it would be with any other
     * bench. What does not happen is the swap: the reserve keeper is not a
     * keeper leaving and is a keeper entering, which is exactly the shape
     * section 3.8 forbids, so the side plays on with ten and the reserve
     * keeper stays exactly where he was.
     */
    @Test
    fun `an injured outfielder is not replaced by a bench that holds only a reserve keeper`() {
        val ints = ScriptedInts(56, 0, 0, 1, 250, 0, 3, 0, 50)
        val before = state(homeBench = listOf(reserveKeeper()))
        val after = before.disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(9, ints.draws, "an injury costs six chain draws and three duration draws")
        assertEquals(DisciplineCounts(injuries = 1), after.counts, "counts")

        val injury = after.log.single() as MatchEvent.Injury
        assertEquals(3, injury.player.slot.value, "the cell the injured player stands in")

        assertTrue(
            after.log.none { it is MatchEvent.Substitution },
            "a reserve keeper must not replace an injured outfielder: ${after.log}",
        )
        assertEquals(10, after.setup.home.lineup.size, "the side plays on a man short")
        assertEquals(0, after.home.substitutionsUsed, "the refusal spends no substitution")
        assertEquals(1, after.home.bench.size, "the reserve keeper is still on the bench, unused")
    }

    /**
     * The case the rule allows, on the very same bench: a reserve keeper
     * replacing an injured keeper is a keeper leaving, which satisfies section
     * 3.8's refusal on its first branch regardless of who comes on.
     *
     * The same nine draws as above except the risk group, which is 0 here and
     * therefore the injury table's own goleiro band, and the player draw,
     * which is 0 for the only candidate the keeper's group ever offers, cell
     * 1.
     */
    @Test
    fun `the same bench does replace an injured keeper`() {
        val ints = ScriptedInts(56, 0, 0, 1, 0, 0, 3, 0, 50)
        val before = state(homeBench = listOf(reserveKeeper()))
        val after = before.disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(9, ints.draws, "an injury costs six chain draws and three duration draws")
        assertEquals(DisciplineCounts(injuries = 1), after.counts, "counts")

        val injury = after.log[0] as MatchEvent.Injury
        assertEquals(1, injury.player.slot.value, "the keeper's own cell")

        val swap = after.log[1] as MatchEvent.Substitution
        assertEquals(SubstitutionReason.INJURY, swap.reason, "the reason logged")
        assertEquals(RESERVE_KEEPER, swap.on.id, "the reserve keeper comes on")
        assertEquals(1, swap.on.slot.value, "he takes the injured keeper's own cell")

        assertEquals(11, after.setup.home.lineup.size, "an injury to the keeper is still replaced")
        assertEquals(1, after.home.substitutionsUsed, "the replacement spends a substitution")
        assertEquals(0, after.home.bench.size, "the reserve keeper leaves the bench")
    }

    /**
     * A minute in which nothing fired opens a window for both sides, not only
     * for the side this minute's victim draw landed on.
     *
     * Four draws on the chain, all of them misses:
     *
     * 1. 0 for the victim side, which is at or below the home threshold of 55,
     *    so the away side is this minute's victim
     * 2. 0 against the yellow threshold of 75, a miss
     * 3. 0 against the red threshold of 800, a miss
     * 4. 0 against the injury threshold of 800, a miss
     *
     * Both sides have the tenth minute of the second half among their routine
     * minutes and both have a tired man in it. A routine minute at or before
     * the fortieth of the half scans from the front and draws nothing at all,
     * so each side's own substitution stream is scripted empty and would fail
     * if the window drew from it.
     *
     * The home side's tired man is its cell 11 and the away side's is its cell
     * 24, and neither is the first outfielder in its lineup, so a window that
     * had taken the front of the list rather than the tiredness scan would be
     * caught here as well.
     */
    @Test
    fun `a quiet minute opens both sides' windows`() {
        val chain = ScriptedInts(0, 0, 0, 0)
        val homeWindow = ScriptedInts()
        val awayWindow = ScriptedInts()
        val before = state(
            awayBench = awayBench(),
            homePlan = SubstitutionPlan(chasing = emptyList(), routine = listOf(10), halfTimeSwap = false),
            awayPlan = SubstitutionPlan(chasing = emptyList(), routine = listOf(10), halfTimeSwap = false),
            homeEnergyByCell = mapOf(11 to 50),
            awayEnergyByCell = mapOf(24 to 50),
        )

        val after = before.disciplineMinute(
            SECOND_HALF_MINUTE,
            CLOCK,
            withWindows(chain, homeWindow, awayWindow),
        )

        assertEquals(4, chain.draws, "a quiet minute makes the three rolls and the victim draw")
        assertEquals(0, homeWindow.draws, "an early routine minute scans from the front")
        assertEquals(0, awayWindow.draws, "an early routine minute scans from the front")
        assertEquals(DisciplineCounts(), after.counts, "no counter moves in a quiet minute")

        val swaps = after.log.filterIsInstance<MatchEvent.Substitution>()
        assertEquals(2, swaps.size, "both sides substituted: ${after.log}")

        val home = swaps.single { it.side == TeamSide.HOME }
        assertEquals(SubstitutionReason.TIREDNESS, home.reason, "the home reason")
        assertEquals(11, home.off.slot.value, "the tired home player comes off")
        assertEquals(MIDFIELD_RESERVE, home.on.id, "the home reserve who suits cell 11")

        val away = swaps.single { it.side == TeamSide.AWAY }
        assertEquals(SubstitutionReason.TIREDNESS, away.reason, "the away reason")
        assertEquals(24, away.off.slot.value, "the tired away player comes off")
        assertEquals(AWAY_ATTACK_RESERVE, away.on.id, "the away reserve who suits cell 24")

        assertEquals(1, after.home.substitutionsUsed, "home substitutions used")
        assertEquals(1, after.away.substitutionsUsed, "away substitutions used")
    }

    /**
     * A minute that produced a card opens no window at all, for either side.
     *
     * The same state as the quiet minute above, with both sides due a routine
     * change in this very minute, and the same victim draw of 0; only the
     * yellow roll differs, drawing 1 against the threshold of 75 instead of a
     * miss. The generator handed in carries no substitution stream whatever, so
     * a chain that reached the window would fail on the missing fork rather
     * than pass with an empty log.
     *
     * The two draws after the yellow roll are the risk group, 0 for g0, and
     * the player, 0 for the first of the away side's two cells in 10 to 13,
     * which is cell 11.
     */
    @Test
    fun `a minute that produces a card opens no window`() {
        val chain = ScriptedInts(0, 1, 0, 0)
        val before = state(
            awayBench = awayBench(),
            homePlan = SubstitutionPlan(chasing = emptyList(), routine = listOf(10), halfTimeSwap = false),
            awayPlan = SubstitutionPlan(chasing = emptyList(), routine = listOf(10), halfTimeSwap = false),
            homeEnergyByCell = mapOf(11 to 50),
            awayEnergyByCell = mapOf(24 to 50),
        )

        val after = before.disciplineMinute(SECOND_HALF_MINUTE, CLOCK, disciplineOnly(chain))

        assertEquals(4, chain.draws, "the chain stopped at the booking")
        assertEquals(DisciplineCounts(yellows = 1), after.counts, "counts")

        val booking = after.log.single() as MatchEvent.Booking
        assertEquals(TeamSide.AWAY, booking.side, "the away side was drawn as the victim")
        assertEquals(11, booking.player.slot.value, "the cell the drawn player stands in")

        assertEquals(0, after.home.substitutionsUsed, "the home window must not have opened")
        assertEquals(0, after.away.substitutionsUsed, "the away window must not have opened")
        assertSame(before.setup.home, after.setup.home, "the home lineup is untouched")
    }

    /**
     * A roll that matches ends the minute even when it turns out to hit
     * nobody, rather than falling through to the roll below it, and the
     * yellow counter still moves for that empty attempt: section 3.8 says the
     * three counters are incremented even when the risk group drawn holds
     * nobody, so a match's counters can run ahead of what its log shows. See
     * the paragraph beginning "Os tres contadores que essas sobrescritas
     * leem" and OPEN-QUESTIONS item 39.
     *
     * Section 3.8 resolves at the first thing that casa, and what matches is
     * the roll, not the event. The home side here stands in cells 1, 22, 24,
     * 14, 15, 16, 17, 2, 9, 3 and 5, which leaves risk group g0, cells 10 to
     * 13, with nobody in it at all. Three draws:
     *
     * 1. 56 for the victim side, so the home side
     * 2. 1 against the yellow threshold of 70, so the yellow roll matches
     * 3. 0 for the risk group, which is g0, and nobody stands there
     *
     * The player draw is skipped rather than made and thrown away, so an
     * unusual shape does not shift the rest of the stream. Nothing at all is
     * logged, because no card happened, but the yellow attempt still counts.
     *
     * Exactly three draws are scripted, so a chain that fell through to the
     * red roll would run the stream out and fail rather than pass quietly.
     * This case is only reachable on a side already short of players, which is
     * why it is worth a test of its own: nothing else in the suite would
     * notice the difference.
     */
    @Test
    fun `a roll that finds an empty risk group still ends the minute`() {
        val ints = ScriptedInts(56, 1, 0)
        val before = state(homeCells = listOf(1, 22, 24, 14, 15, 16, 17, 2, 9, 3, 5))
        val after = before.disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(3, ints.draws, "the chain must stop at the roll that matched")
        assertTrue(after.log.isEmpty(), "no card happened, so nothing is logged: ${after.log}")
        assertEquals(DisciplineCounts(yellows = 1), after.counts, "the matched attempt still counts")
        assertSame(before.setup, after.setup, "nobody left the pitch")
        assertEquals(
            before.copy(counts = DisciplineCounts(yellows = 1)),
            after,
            "nothing but the counter changed",
        )
    }

    /**
     * What the two changes above are for together: an injury attempt that
     * finds an empty risk group still feeds the more than or equal to one
     * lesao overwrite on the very next threshold read, even though no injury
     * was ever logged.
     * anyInjuryAtLeast is 1 under the classic rules, so a single such attempt
     * already satisfies it; a match whose log carries no injury at all can
     * still have its card rate collapse the way section 3.8 says happens
     * "depois da primeira lesao da partida". A version that counted events
     * rather than attempts would never move injuries here and this assertion
     * would catch it.
     *
     * The home side stands in cells 1, 2, 3, 4, 5, 6, 7, 8, 9, 11 and 12,
     * the same lineup RiskGroupTest uses to leave risk group g5, cells 19 to
     * 24, with nobody in it. Five draws:
     *
     * 1. 56 for the victim side, so the home side
     * 2. 0 against the yellow threshold of 70, a miss
     * 3. 0 against the red threshold of 900, a miss
     * 4. 1 against the injury threshold of 1000, so the injury roll matches
     * 5. 450 for the risk group, which is inside the injury table's g5 band
     *    of 420 to 499, and nobody stands there
     *
     * The player draw is skipped, nothing is logged, and the minute ends
     * there with the injuries counter moved to one. Feeding that counter into
     * minuteThresholds for the tenth minute of the second half, whose own
     * injury base is 800, returns a yellow threshold of injuryOverwriteFactor
     * times 800 rather than the plain table cell an unmoved counter would
     * have produced.
     */
    @Test
    fun `an empty injury attempt still fires the overwrite on the next minute`() {
        val ints = ScriptedInts(56, 0, 0, 1, 450)
        val before = state(homeCells = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12))
        val after = before.disciplineMinute(FIRST_HALF_MINUTE, CLOCK, disciplineOnly(ints))

        assertEquals(5, ints.draws, "the chain must stop at the roll that matched")
        assertTrue(after.log.isEmpty(), "no injury happened, so nothing is logged: ${after.log}")
        assertEquals(DisciplineCounts(injuries = 1), after.counts, "the matched attempt still counts")

        val nextThresholds = minuteThresholds(
            SECOND_HALF_MINUTE,
            CLOCK,
            after.setup.home,
            after.counts,
            after.setup.rules,
        )
        assertEquals(
            after.setup.rules.injuryOverwriteFactor * 800,
            nextThresholds.yellow,
            "the overwrite must fire even though the log carries no injury at all",
        )
    }

    /**
     * The interval window reaches the pitch.
     *
     * This is the window a wrong reading of section 3.8 is likeliest to lose
     * altogether, and losing it is invisible: the routine minutes would still
     * fire and a test that only counted substitutions over a sample would
     * still pass. Putting a gate of five minutes into the half on the chain,
     * which is what the chain's fourth branch is gated by, would kill every
     * half time change in the game and nothing else in the suite would say so.
     *
     * The chain draws four times and misses three times: 56 for the victim
     * side, then 0 against the yellow threshold of 75, 0 against the red
     * threshold of 800 and 0 against the injury threshold of 800.
     *
     * The home side is a goal down, which is the deficit section 3.8 asks a
     * home side for at the interval, and its coin came up for a change when the
     * plan was drawn. Its window makes one draw, 2, over the ten outfielders in
     * lineup order, which is its cell 11. The away side wants two goals before
     * it considers a change and is a goal up, so its window makes no draw at
     * all and its stream is scripted empty.
     */
    @Test
    fun `the interval window opens`() {
        val chain = ScriptedInts(56, 0, 0, 0)
        val homeWindow = ScriptedInts(2)
        val awayWindow = ScriptedInts()
        val before = state(
            awayBench = awayBench(),
            homePlan = SubstitutionPlan(chasing = emptyList(), routine = emptyList(), halfTimeSwap = true),
            awayPlan = SubstitutionPlan(chasing = emptyList(), routine = emptyList(), halfTimeSwap = true),
            homeGoals = 0,
            awayGoals = 1,
        )

        val after = before.disciplineMinute(INTERVAL, CLOCK, withWindows(chain, homeWindow, awayWindow))

        assertEquals(4, chain.draws, "the chain made its victim draw and its three rolls")
        assertEquals(1, homeWindow.draws, "the interval takes a random outfielder, which is one draw")
        assertEquals(0, awayWindow.draws, "a side a goal up makes no draw at the interval")

        val swap = after.log.single() as MatchEvent.Substitution
        assertEquals(TeamSide.HOME, swap.side, "the side that was a goal down")
        assertEquals(SubstitutionReason.HALF_TIME, swap.reason, "the reason logged")
        assertEquals(11, swap.off.slot.value, "the third outfielder in lineup order comes off")
        assertEquals(MIDFIELD_RESERVE, swap.on.id, "the reserve who suits cell 11 comes on")
        assertEquals(1, after.home.substitutionsUsed, "home substitutions used")
        assertEquals(0, after.away.substitutionsUsed, "away substitutions used")
    }

    /**
     * A card at the interval does not close the interval window.
     *
     * The chain's fourth branch is gated by section 3.8 on the second half and
     * the fifth minute of it, and the interval is the nought'th minute of the
     * second half, so the interval cannot be that branch: it could never fire
     * there. It is section 3.8's own separate paragraph, and a mechanism of its
     * own is not gated on whether the chain found somebody to book.
     *
     * The same state as the interval test above, and the same victim draw, with
     * the yellow roll drawing 1 against the threshold of 75 instead of missing.
     * The two draws after it are the risk group, 0 for g0, and the player, 1
     * for the second of the home side's two cells in 10 to 13, which is cell
     * 13. The booked player is still on the pitch, so the window's draw of 2
     * still lands on cell 11.
     */
    @Test
    fun `a card at the interval does not close the interval window`() {
        val chain = ScriptedInts(56, 1, 0, 1)
        val homeWindow = ScriptedInts(2)
        val awayWindow = ScriptedInts()
        val before = state(
            awayBench = awayBench(),
            homePlan = SubstitutionPlan(chasing = emptyList(), routine = emptyList(), halfTimeSwap = true),
            homeGoals = 0,
            awayGoals = 1,
        )

        val after = before.disciplineMinute(INTERVAL, CLOCK, withWindows(chain, homeWindow, awayWindow))

        assertEquals(4, chain.draws, "the chain stopped at the booking")
        assertEquals(DisciplineCounts(yellows = 1), after.counts, "counts")

        val booking = after.log[0] as MatchEvent.Booking
        assertEquals(13, booking.player.slot.value, "the cell the booked player stands in")

        val swap = after.log[1] as MatchEvent.Substitution
        assertEquals(SubstitutionReason.HALF_TIME, swap.reason, "the interval window still opened")
        assertEquals(11, swap.off.slot.value, "the drawn outfielder comes off")
        assertEquals(MIDFIELD_RESERVE, swap.on.id, "the reserve who suits cell 11 comes on")
        assertEquals(2, after.log.size, "a booking and a substitution and nothing else")
        assertEquals(1, after.home.substitutionsUsed, "home substitutions used")
    }

    /**
     * The counters at the final whistle are never behind what the log says,
     * and agree with it exactly at every one of these sixty seeds, though
     * DisciplineCounts's own docstring explains why that need not always hold:
     * an attempt that lands on an empty risk group moves a counter without
     * logging anything, so a counter can in general run ahead of the fold
     * below it. None of these sixty seeds happens to draw that way; the case
     * where it does is pinned on its own by a scripted draw rather than by
     * chance here.
     *
     * Sixty whole matches at fixed seeds, both sides carrying a bench, folded
     * event by event: a booking is a yellow, a direct red dismissal is a
     * sending off, an injury is an injury. A dismissal for a second yellow is
     * excluded from the sendingsOff fold on purpose: it logs a SendingOff
     * event, since the log is unchanged by this fix, but it does not move the
     * sendingsOff counter, since only a direct red feeds the overwrite that
     * counter drives. This is the case a fold and a running counter are most
     * likely to disagree on if the exclusion above is ever lost.
     *
     * The three totals over the sample are asserted to be positive as well, so
     * that the invariant cannot be satisfied by a chain that never fires: a
     * mis-wired roll would make every match nought equals nought and pass.
     * Substitutions are counted for the same reason, since a bench that is
     * never used would make the whole substitution wiring invisible here.
     */
    @Test
    fun `the counters agree with the log at the final whistle`() {
        var yellows = 0
        var sendingsOff = 0
        var injuries = 0
        var substitutions = 0

        for (seed in 1L..60L) {
            val played = playMatch(setup(), SplitMix64Rng(seed), bench(), awayBench())
            val log = played.state.log
            val fold = DisciplineCounts(
                yellows = log.count { it is MatchEvent.Booking },
                sendingsOff = log.count { it is MatchEvent.SendingOff && !it.secondYellow },
                injuries = log.count { it is MatchEvent.Injury },
            )
            assertEquals(
                fold,
                played.state.counts,
                "seed $seed drew an attempt that landed on an empty risk group, so the " +
                    "counter ran ahead of the log fold",
            )

            yellows += fold.yellows
            sendingsOff += fold.sendingsOff
            injuries += fold.injuries
            substitutions += log.count { it is MatchEvent.Substitution }
        }

        assertTrue(yellows > 0, "the sample produced no booking at all, so the fold proves nothing")
        assertTrue(sendingsOff > 0, "the sample produced no dismissal at all")
        assertTrue(injuries > 0, "the sample produced no injury at all")
        assertTrue(substitutions > 0, "the sample produced no substitution at all")
    }

    private companion object {

        /**
         * Forty seven and forty six, so the second half starts at minute 47
         * and the tenth minute of it is minute 57.
         */
        val CLOCK = MatchClock(firstHalfMinutes = 47, secondHalfMinutes = 46)

        /** The middle phase of the first half, whose bounds are 15 and 30. */
        const val FIRST_HALF_MINUTE = 20

        /** The tenth minute of the second half, and its first phase. */
        const val SECOND_HALF_MINUTE = 57

        /** The nought'th minute of the second half, which stands for the break. */
        const val INTERVAL = 47

        val MIDFIELD_RESERVE = PlayerId(30)
        val DEFENCE_RESERVE = PlayerId(31)
        val ATTACK_RESERVE = PlayerId(32)
        val AWAY_ATTACK_RESERVE = PlayerId(42)
        val RESERVE_KEEPER = PlayerId(90)

        /**
         * A bench of three, strongest first, each a natural fit for a
         * different part of the pitch. The strength order is deliberately the
         * reverse of the order the cells want them in for the cases below, so
         * a search that ranked by strength alone rather than walking the
         * position cascade would pick the wrong man every time.
         */
        fun bench(): List<MatchPlayer> = listOf(
            reserve(MIDFIELD_RESERVE, 70, Position.MIDFIELDER, PlayerStyle.DEFENSIVE),
            reserve(DEFENCE_RESERVE, 65, Position.CENTREBACK, PlayerStyle.DEFENSIVE),
            reserve(ATTACK_RESERVE, 60, Position.FORWARD, PlayerStyle.OFFENSIVE),
        )

        /** A bench of exactly one, the shape the keeper refusal tests need. */
        fun reserveKeeper(): MatchPlayer = reserve(RESERVE_KEEPER, 40, Position.GOALKEEPER, PlayerStyle.DEFENSIVE)

        /** The same three for the away side, on identities of their own. */
        fun awayBench(): List<MatchPlayer> = listOf(
            reserve(PlayerId(40), 70, Position.MIDFIELDER, PlayerStyle.DEFENSIVE),
            reserve(PlayerId(41), 65, Position.CENTREBACK, PlayerStyle.DEFENSIVE),
            reserve(AWAY_ATTACK_RESERVE, 60, Position.FORWARD, PlayerStyle.OFFENSIVE),
        )

        fun reserve(id: PlayerId, strength: Int, position: Position, style: PlayerStyle) =
            Lineups.player(
                slot = Slot.UNUSED_SUBSTITUTE.value,
                strength = strength,
                id = id.value,
                position = position,
                style = style,
            )

        fun setup(homeCells: List<Int> = Lineups.FORMATION_4_4_2) = MatchSetup(
            home = Lineups.sideOfSlots(
                homeCells,
                strength = 50,
                context = Lineups.context(isHome = true),
            ),
            away = Lineups.sideOfSlots(
                Lineups.FORMATION_4_4_2,
                strength = 50,
                context = Lineups.context(isHome = false),
            ),
            season = 1,
            rules = RuleSets.CLASSIC,
        )

        /**
         * A state in the middle of a match. Every starter's identity is his
         * cell number, so an energy map and a bookings map are both keyed by
         * cell; anybody the maps do not name is unbooked and on the hundred
         * initialState gives him.
         */
        fun state(
            homeBench: List<MatchPlayer> = bench(),
            awayBench: List<MatchPlayer> = emptyList(),
            homePlan: SubstitutionPlan = SubstitutionPlan.NONE,
            awayPlan: SubstitutionPlan = SubstitutionPlan.NONE,
            homeBookings: Map<PlayerId, Int> = emptyMap(),
            counts: DisciplineCounts = DisciplineCounts(),
            homeEnergyByCell: Map<Int, Int> = emptyMap(),
            awayEnergyByCell: Map<Int, Int> = emptyMap(),
            homeCells: List<Int> = Lineups.FORMATION_4_4_2,
            homeGoals: Int = 0,
            awayGoals: Int = 0,
        ): MatchState {
            val base = initialState(
                setup = setup(homeCells),
                startingPossessor = TeamSide.HOME,
                homeBench = homeBench,
                awayBench = awayBench,
                homePlan = homePlan,
                awayPlan = awayPlan,
            )
            return base.copy(
                home = base.home.copy(
                    bookings = homeBookings,
                    energy = drained(base.home.energy, homeEnergyByCell),
                ),
                away = base.away.copy(energy = drained(base.away.energy, awayEnergyByCell)),
                counts = counts,
                homeGoals = homeGoals,
                awayGoals = awayGoals,
            )
        }

        fun drained(full: Map<PlayerId, Int>, byCell: Map<Int, Int>): Map<PlayerId, Int> {
            val energy = LinkedHashMap(full)
            for ((cell, value) in byCell) {
                energy[PlayerId(cell)] = value
            }
            return energy
        }

        /**
         * A minute generator that offers the chain its stream and nothing
         * else, so a chain that went on to open a substitution window fails on
         * the missing fork instead of passing with an unchanged state.
         */
        fun disciplineOnly(chain: Rng): Rng = Streams(DISCIPLINE_STREAM to chain)

        /** A minute generator that offers the chain and both sides' windows. */
        fun withWindows(chain: Rng, home: Rng, away: Rng): Rng = Streams(
            DISCIPLINE_STREAM to chain,
            SUBSTITUTION_STREAM to Streams(
                TeamSide.HOME.ordinal.toLong() to home,
                TeamSide.AWAY.ordinal.toLong() to away,
            ),
        )
    }
}

/**
 * A generator that only forks, into streams the test named.
 *
 * Every draw a minute makes goes through a fork first, so a test can hand this
 * in and script each stream separately. A fork the test did not name fails
 * loudly, which is what lets a test assert that a branch was never reached by
 * simply not scripting the stream that branch would have used.
 */
private class Streams(private vararg val children: Pair<Long, Rng>) : Rng {

    override fun fork(tag: Long): Rng =
        children.firstOrNull { it.first == tag }?.second
            ?: throw IllegalStateException("no stream was scripted for the tag $tag")

    override fun nextInt(bound: Int): Int =
        throw UnsupportedOperationException("a stream has to be forked before it is drawn from")

    override fun nextDouble(): Double =
        throw UnsupportedOperationException("a stream has to be forked before it is drawn from")

    override fun nextBits(): Long =
        throw UnsupportedOperationException("a stream has to be forked before it is drawn from")
}
