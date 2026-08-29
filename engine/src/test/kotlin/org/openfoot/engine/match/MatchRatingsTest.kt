package org.openfoot.engine.match

import org.openfoot.model.GoalType
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Slot
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Who section 3.14 rates, out of what, and from which stream.
 *
 * PlayerRatingTest pins the eleven steps against a hand built tally. This
 * file pins the wiring around them: that the report carries the elevens the
 * match kicked off with rather than the ones it finished with, that both
 * starting elevens and every arrival are rated while a man who never left the
 * bench is not rated at all, and that a genuine rating of nought is a
 * different fact from not being rated.
 */
class MatchRatingsTest {

    private val rules = RuleSets.CLASSIC

    private val clock = MatchClock(firstHalfMinutes = 45, secondHalfMinutes = 45)

    private fun report(
        homeLineup: List<MatchPlayer>,
        awayLineup: List<MatchPlayer>,
        log: List<MatchEvent>,
        homeGoals: Int = 0,
        awayGoals: Int = 0,
    ) = MatchReport(
        clock = clock,
        log = log,
        homeGoals = homeGoals,
        awayGoals = awayGoals,
        startingPossessor = TeamSide.HOME,
        homeLineup = homeLineup,
        awayLineup = awayLineup,
    )

    /**
     * A man who came on is rated, in the cell he inherited, and a man who sat
     * on the bench the whole match is not rated at all.
     *
     * The reserve who never came on appears in no lineup and in no event, so
     * he can only be absent, and asserting that he is absent rather than
     * present at nought is the whole of section 3.14's "quem recebe nota".
     */
    @Test
    fun `an arrival is rated in his inherited cell and a benched reserve is not rated`() {
        val keeper = Lineups.player(slot = 1, strength = 50, id = 1)
        val centreBack = Lineups.player(slot = 5, strength = 50, id = 5)
        val striker = Lineups.player(slot = 20, strength = 50, id = 20)
        val arrival = Lineups.player(slot = 20, strength = 50, id = 31)
        val awayEleven = listOf(
            Lineups.player(slot = 1, strength = 50, id = 1),
            Lineups.player(slot = 20, strength = 50, id = 20),
        )
        val log = listOf(
            MatchEvent.Substitution(
                minute = 50,
                side = TeamSide.HOME,
                off = striker,
                on = arrival,
                reason = SubstitutionReason.TIREDNESS,
            ),
        )

        val ratings = report(listOf(keeper, centreBack, striker), awayEleven, log)
            .playerRatings(rules, SplitMix64Rng(11))

        assertEquals(
            listOf(PlayerId(1), PlayerId(5), PlayerId(20), PlayerId(31)),
            ratings.home.keys.toList(),
            "the starting eleven in lineup order, then the arrival",
        )
        assertFalse(PlayerId(30) in ratings.home, "a reserve who never came on is not a key at all")
        assertEquals(Slot(20), ratings.home.getValue(PlayerId(31)).slot, "the arrival is rated in the cell he filled")
    }

    /**
     * A genuine rating of nought and a man who was never rated are two
     * different facts, and this is the fixture that produces the first of
     * them.
     *
     * The home striker scores an own goal in the fifth minute and is sent off
     * in the tenth. His minutes are the minute of his last event, which is
     * ten, so he is charged step 10's larger penalty; five for a defeat at
     * his strength, less one point five and nought point eight, is two point
     * seven, less two point five is nought point two, which step 11 lifts to
     * the floor and then zeroes because he played under twenty minutes.
     *
     * The reserve on the bench is absent from the same map. A reader that
     * folded the two into one nought could not tell the sent off striker from
     * a man who never played.
     */
    @Test
    fun `a rating of nought is a fact and a bench place is the absence of one`() {
        val homeKeeper = Lineups.player(slot = 1, strength = 50, id = 1)
        val disgraced = Lineups.player(slot = 20, strength = 30, id = 20)
        val awayKeeper = Lineups.player(slot = 1, strength = 50, id = 1)
        val awayStriker = Lineups.player(slot = 20, strength = 50, id = 20)
        val log = listOf(
            MatchEvent.Goal(
                minute = 5,
                side = TeamSide.AWAY,
                type = GoalType.OWN_GOAL,
                author = disgraced,
                scorer = awayStriker,
                matchGoalCredits = 1,
                assister = null,
            ),
            MatchEvent.SendingOff(minute = 10, side = TeamSide.HOME, player = disgraced, secondYellow = false),
        )

        val ratings = report(
            homeLineup = listOf(homeKeeper, disgraced),
            awayLineup = listOf(awayKeeper, awayStriker),
            log = log,
            homeGoals = 0,
            awayGoals = 1,
        ).playerRatings(rules, SplitMix64Rng(3))

        assertEquals(
            0.0,
            ratings.home.getValue(PlayerId(20)).value,
            TOLERANCE,
            "under twenty minutes and stopped on the floor, so no rating at all",
        )
        assertTrue(PlayerId(20) in ratings.home, "and he is still a key, unlike a man who never played")
        assertFalse(PlayerId(99) in ratings.home, "a reserve who never came on has no entry")
    }

    /**
     * One seed produces one set of ratings, twice.
     *
     * Section 3.14 spends draws, so this is not free: a rating that read a
     * shared generator, or one keyed on iteration order, would drift here.
     */
    @Test
    fun `rating one match twice from one seed gives one answer`() {
        val setup = setup()
        val played = simulateMatch(setup, SplitMix64Rng(9), homeBench(), awayBench())

        val first = played.playerRatings(rules, SplitMix64Rng(9))
        val second = played.playerRatings(rules, SplitMix64Rng(9))

        assertEquals(first, second, "the same seed must replay the same ratings")
    }

    /**
     * The report's two lineups are the elevens the match kicked off with.
     *
     * A side that substitutes ends the match with a different eleven on the
     * pitch, since leavePitch drops the departed man outright and the arrival
     * stands in his cell. Every seed below is checked, and the sample is
     * asserted to contain at least one substitution and at least one
     * departure, so the property cannot be satisfied by forty quiet matches.
     */
    @Test
    fun `the report carries the elevens the match kicked off with`() {
        var substitutions = 0
        var departures = 0

        for (seed in 1L..40L) {
            val setup = setup()
            val startingHome = setup.home.lineup.map { it.id }
            val startingAway = setup.away.lineup.map { it.id }
            val played = simulateMatch(setup, SplitMix64Rng(seed), homeBench(), awayBench())

            assertEquals(startingHome, played.homeLineup.map { it.id }, "seed $seed home lineup")
            assertEquals(startingAway, played.awayLineup.map { it.id }, "seed $seed away lineup")

            for (event in played.log) {
                if (event is MatchEvent.Substitution) {
                    substitutions++
                    val lineup = if (event.side == TeamSide.HOME) played.homeLineup else played.awayLineup
                    assertFalse(
                        lineup.any { it.id == event.on.id },
                        "seed $seed put an arrival into what should be the starting eleven",
                    )
                }
                if (event is MatchEvent.SendingOff) {
                    departures++
                    val lineup = if (event.side == TeamSide.HOME) played.homeLineup else played.awayLineup
                    assertTrue(
                        lineup.any { it.id == event.player.id },
                        "seed $seed dropped a dismissed man from what should be the starting eleven",
                    )
                }
            }
        }

        assertTrue(substitutions > 0, "the sample made no substitution, so it proves nothing about arrivals")
        assertTrue(departures > 0, "the sample had no dismissal, so it proves nothing about departures")
    }

    /**
     * Everybody who played is rated and nobody else.
     *
     * The expected set is built from the report rather than from the ratings,
     * so a rating that quietly skipped a man or invented one fails here. A
     * reserve who was never brought on must be missing from both sides.
     */
    @Test
    fun `every starter and every arrival is rated and nobody else`() {
        var arrivals = 0

        for (seed in 1L..20L) {
            val played = simulateMatch(setup(), SplitMix64Rng(seed), homeBench(), awayBench())
            val ratings = played.playerRatings(rules, SplitMix64Rng(seed))

            for (side in TeamSide.entries) {
                val lineup = if (side == TeamSide.HOME) played.homeLineup else played.awayLineup
                val expected = LinkedHashSet(lineup.map { it.id })
                for (event in played.log) {
                    if (event is MatchEvent.Substitution && event.side == side) {
                        expected += event.on.id
                        arrivals++
                    }
                }
                assertEquals(expected, ratings.of(side).keys, "seed $seed, $side")
            }
        }

        assertTrue(arrivals > 0, "no substitute came on anywhere in the sample")
    }

    /**
     * Every rating a real match produces sits on the scale section 3.14
     * publishes: nought for a man with no rating, and otherwise from the
     * floor of two up to the cap of ten.
     *
     * Nothing between nought and two may ever appear, since step 11 lifts
     * every such value to the floor and then either leaves it there or zeroes
     * it outright.
     */
    @Test
    fun `no rating falls between nought and the floor or above the cap`() {
        for (seed in 1L..20L) {
            val played = simulateMatch(setup(), SplitMix64Rng(seed), homeBench(), awayBench())
            val ratings = played.playerRatings(rules, SplitMix64Rng(seed))

            for (side in TeamSide.entries) {
                for ((id, rating) in ratings.of(side)) {
                    val legal = rating.value == 0.0 ||
                        (rating.value >= rules.ratings.limits.floor && rating.value <= rules.ratings.limits.cap)
                    assertTrue(legal, "seed $seed, $side, $id was rated ${rating.value}")
                }
            }
        }
    }

    private companion object {

        const val TOLERANCE = 1e-9

        fun setup() = MatchSetup(
            home = Lineups.sideOfSlots(
                Lineups.FORMATION_4_4_2,
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

        fun homeBench(): List<MatchPlayer> = listOf(
            reserve(30, 70, Position.MIDFIELDER, PlayerStyle.DEFENSIVE),
            reserve(31, 65, Position.CENTREBACK, PlayerStyle.DEFENSIVE),
            reserve(32, 60, Position.FORWARD, PlayerStyle.OFFENSIVE),
        )

        fun awayBench(): List<MatchPlayer> = listOf(
            reserve(40, 70, Position.MIDFIELDER, PlayerStyle.DEFENSIVE),
            reserve(41, 65, Position.CENTREBACK, PlayerStyle.DEFENSIVE),
            reserve(42, 60, Position.FORWARD, PlayerStyle.OFFENSIVE),
        )

        fun reserve(id: Int, strength: Int, position: Position, style: PlayerStyle) = Lineups.player(
            slot = Slot.UNUSED_SUBSTITUTE.value,
            strength = strength,
            id = id,
            position = position,
            style = style,
        )
    }
}
