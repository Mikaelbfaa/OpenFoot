package org.openfoot.engine.match

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Slot
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The three windows of section 3.8, and who each one takes off.
 *
 * Both sides field a four four two, whose cells in list order are 1, 22, 24,
 * 11, 13, 14, 16, 2, 9, 3 and 5. Every player on the pitch is a fifty, so
 * nothing below turns on a starter's strength; the home bench carries a
 * defensive centre back of seventy and an offensive forward of sixty, which
 * are told apart by the cell being filled and not by which is stronger.
 *
 * The clock is forty seven and forty eight minutes, so minute forty seven is
 * the first minute of the second half and a minute counted inside that half is
 * forty seven higher when counted from kick off.
 */
class SubstitutionWindowTest {

    /**
     * At the interval the home side changes when it is a goal down and the
     * away side only when it is two down, which is the one place in section
     * 3.8 where the two sides are held to different standards. Both figures
     * are pinned from both sides: level is not enough for the home side and a
     * single goal is not enough for the visitor.
     */
    @Test
    fun `the interval asks more of the away side`() {
        assertFalse(wantsHalfTimeSwap(scored(home = 1, away = 1), TeamSide.HOME, RULES))
        assertTrue(wantsHalfTimeSwap(scored(home = 0, away = 1), TeamSide.HOME, RULES))
        assertFalse(wantsHalfTimeSwap(scored(home = 1, away = 0), TeamSide.AWAY, RULES))
        assertTrue(wantsHalfTimeSwap(scored(home = 2, away = 0), TeamSide.AWAY, RULES))
    }

    /**
     * On a chasing minute the home side changes when it is level as well as
     * when it is behind; the away side settles for a draw. A home side a goal
     * up leaves it alone, which is the other side of its own boundary.
     */
    @Test
    fun `the home side chases a draw and the away side does not`() {
        assertFalse(wantsChasingSwap(scored(home = 2, away = 1), TeamSide.HOME, RULES))
        assertTrue(wantsChasingSwap(scored(home = 1, away = 1), TeamSide.HOME, RULES))
        assertTrue(wantsChasingSwap(scored(home = 0, away = 1), TeamSide.HOME, RULES))
        assertFalse(wantsChasingSwap(scored(home = 1, away = 1), TeamSide.AWAY, RULES))
        assertTrue(wantsChasingSwap(scored(home = 1, away = 0), TeamSide.AWAY, RULES))
    }

    /**
     * Before the lift the scan starts at the front and takes the first non
     * keeper under sixty energy. Cell two is the eighth man in the four four
     * two's list and cell nine the ninth, so a scan from the front reaches two
     * first even though nine is the more tired of them. It makes no draw at
     * all to decide where to start, which ScriptedInts proves by throwing on
     * any draw and by counting nought.
     */
    @Test
    fun `the early tiredness scan starts at the front and draws nothing`() {
        val state = withEnergy(mapOf(2 to 55, 9 to 20))
        val rng = ScriptedInts()
        val tired = tirednessTarget(state, TeamSide.HOME, intoHalf = 20, rng = rng)

        assertEquals(2, tired!!.slot.value)
        assertEquals(0, rng.draws)
    }

    /**
     * Sixty is the bar and it is exclusive: fifty nine is tired and sixty is
     * not, so a side whose most worn man is on exactly sixty changes nobody.
     */
    @Test
    fun `the early threshold is sixty and takes nobody who is on it`() {
        assertEquals(
            2,
            tirednessTarget(
                withEnergy(mapOf(2 to 59)),
                TeamSide.HOME,
                intoHalf = 20,
                rng = ScriptedInts(),
            )!!.slot.value,
        )
        assertNull(
            tirednessTarget(
                withEnergy(mapOf(2 to 60)),
                TeamSide.HOME,
                intoHalf = 20,
                rng = ScriptedInts(),
            ),
        )
    }

    /**
     * A keeper under the threshold is never the one taken off, whatever his
     * energy, because section 3.8 scans non keepers only. He is on nought here
     * and the scan still returns nobody.
     */
    @Test
    fun `the tiredness scan never takes the keeper`() {
        val state = withEnergy(mapOf(1 to 0))
        assertNull(tirednessTarget(state, TeamSide.HOME, intoHalf = 20, rng = ScriptedInts()))
    }

    /**
     * The bar rises to ninety after minute forty of the half, and "after" is
     * read strictly: at minute forty a man on eighty five is still fresh
     * enough and the scan makes no draw, and one minute later he is tired
     * enough and the scan draws its starting index first.
     */
    @Test
    fun `the threshold lifts after minute forty rather than at it`() {
        val state = withEnergy(mapOf(2 to 85))
        val atForty = ScriptedInts()

        assertNull(tirednessTarget(state, TeamSide.HOME, intoHalf = 40, rng = atForty))
        assertEquals(0, atForty.draws)

        val afterForty = ScriptedInts(0)
        assertEquals(
            2,
            tirednessTarget(state, TeamSide.HOME, intoHalf = 41, rng = afterForty)!!.slot.value,
        )
        assertEquals(1, afterForty.draws)
    }

    /**
     * The lifted bar is ninety and is exclusive too: eighty nine is tired and
     * ninety is not.
     */
    @Test
    fun `the late threshold is ninety and takes nobody who is on it`() {
        assertEquals(
            2,
            tirednessTarget(
                withEnergy(mapOf(2 to 89)),
                TeamSide.HOME,
                intoHalf = 41,
                rng = ScriptedInts(0),
            )!!.slot.value,
        )
        assertNull(
            tirednessTarget(
                withEnergy(mapOf(2 to 90)),
                TeamSide.HOME,
                intoHalf = 41,
                rng = ScriptedInts(0),
            ),
        )
    }

    /**
     * The late scan starts where the draw says and walks forward from there,
     * wrapping past the end of the lineup. Cell twenty two is the second man
     * in the list and the only tired one, so a start of nought reaches him at
     * once and a start of three has to walk the other eight, pass the keeper
     * and come back round to him.
     *
     * A start of two, with cells twenty two and two both tired, proves the
     * walk goes forward rather than back: it skips the twenty two behind it
     * and takes the two ahead of it.
     */
    @Test
    fun `the late scan starts at the drawn index and wraps`() {
        val oneTired = withEnergy(mapOf(22 to 85))
        assertEquals(
            22,
            tirednessTarget(oneTired, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(0))!!
                .slot.value,
        )
        assertEquals(
            22,
            tirednessTarget(oneTired, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(3))!!
                .slot.value,
        )

        val twoTired = withEnergy(mapOf(22 to 85, 2 to 85))
        assertEquals(
            2,
            tirednessTarget(twoTired, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(2))!!
                .slot.value,
        )
    }

    /**
     * A routine minute takes the tiredness scan's man off and puts the reserve
     * the vacated cell suits in his place. Cell two asks for a right sided
     * fullback and asks nothing of the sub role, so the cascade of section 5.4
     * reaches the centre back before the forward and the seventy comes on.
     *
     * The whole window makes no draw here: the scan is an early one and the
     * replacement search never draws at all.
     */
    @Test
    fun `a routine minute changes the tired man for the reserve his cell suits`() {
        val before = withEnergy(mapOf(2 to 55))
        val rng = ScriptedInts()

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(routine = listOf(20)),
            minute = SECOND_HALF_START + 20,
            clock = CLOCK,
            rng = rng,
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(SubstitutionReason.TIREDNESS, logged.reason)
        assertEquals(PlayerId(2), logged.off.id)
        assertEquals(CENTRE_BACK_ID, logged.on.id)
        assertEquals(2, logged.on.slot.value)
        assertEquals(CENTRE_BACK_ID, after.setup.home.lineup.last().id)
        assertEquals(11, after.setup.home.lineup.size)
        assertEquals(1, after.home.substitutionsUsed)
        assertEquals(listOf(FORWARD_ID), after.home.bench.map { it.id })
        assertEquals(0, rng.draws)
    }

    /**
     * The draw that picks who comes off runs over the whole eleven, keeper
     * included, taken in lineup order: cell one is the first index rather
     * than being excluded. Every index is pinned rather than a sample, so the
     * mapping from a draw to a player is the assertion and not an accident of
     * some index also being the first of the list.
     */
    @Test
    fun `the lineup draw runs over all eleven including the keeper`() {
        val side = state().setup.home
        val drawn = (0..10).map { randomLineupPlayer(side, ScriptedInts(it))!!.slot.value }

        assertEquals(listOf(1, 22, 24, 11, 13, 14, 16, 2, 9, 3, 5), drawn)
    }

    /**
     * A chasing minute's draw can land on the keeper, and section 3.8 wastes
     * the window rather than redraw: nothing changes, nothing is logged, and
     * the drawn minute is simply spent. Index nought is cell one, the
     * keeper's cell and the first of the whole eleven. ScriptedInts is
     * handed only that one value, so a caller that tried a second draw to
     * find somebody else would fail here rather than pass quietly.
     */
    @Test
    fun `a chasing minute drawn on the keeper wastes the window`() {
        val before = scored(home = 0, away = 1)
        val rng = ScriptedInts(0)

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        assertEquals(before, after)
        assertEquals(1, rng.draws)
    }

    /**
     * A chasing minute takes a drawn non keeper instead. Index two is cell
     * twenty four, the third of the whole eleven and neither the first index
     * nor the first of the lineup, so a passing result here proves the draw's
     * index was actually read rather than the lineup's own first() winning by
     * accident. Cell twenty four asks for an offensive forward, so the sixty
     * comes on ahead of the stronger centre back.
     */
    @Test
    fun `a chasing minute changes the non keeper the draw names`() {
        val before = scored(home = 0, away = 1)
        val rng = ScriptedInts(2)

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(SubstitutionReason.CHASING, logged.reason)
        assertEquals(PlayerId(24), logged.off.id)
        assertEquals(FORWARD_ID, logged.on.id)
        assertEquals(24, logged.on.slot.value)
        assertEquals(1, rng.draws)
    }

    /**
     * The away side runs the same window with the same code. It is a goal down
     * and its own chasing minute has come, so it changes cell twenty four for
     * the reserve that cell suits, while every home field is the object it
     * already was rather than merely an equal one.
     */
    @Test
    fun `an away chasing minute leaves the home side alone`() {
        val before = state(homeGoals = 1, awayGoals = 0, awayBench = bench())

        val after = before.runSubstitutionWindow(
            team = TeamSide.AWAY,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = ScriptedInts(2),
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(TeamSide.AWAY, logged.side)
        assertEquals(SubstitutionReason.CHASING, logged.reason)
        assertEquals(PlayerId(24), logged.off.id)
        assertEquals(FORWARD_ID, after.setup.away.lineup.last().id)
        assertEquals(1, after.away.substitutionsUsed)

        assertSame(before.setup.home, after.setup.home)
        assertEquals(before.home, after.home)
    }

    /**
     * A minute may sit in both pools at once: the chasing window is nineteen
     * to thirty eight and the middle routine pool is sixteen to thirty five,
     * so twenty five is in both. Section 3.8 lists the chasing window first
     * and that is the order taken. The side here is level, so the chasing
     * window wants a change, and cell two is tired enough that a routine
     * minute would have taken it instead; the reason logged says which branch
     * ran, and the man taken off says it a second time.
     */
    @Test
    fun `a minute in both pools is a chasing minute`() {
        val before = state(homeGoals = 1, awayGoals = 1, energyByCell = mapOf(2 to 55))

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25), routine = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = ScriptedInts(2),
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(SubstitutionReason.CHASING, logged.reason)
        assertEquals(PlayerId(24), logged.off.id)
    }

    /**
     * The same minute with the home side a goal up changes nothing and makes
     * no draw, so a side that is happy with the score does not shift the
     * stream for the side that is not.
     */
    @Test
    fun `a chasing minute the score does not call for changes nothing`() {
        val before = scored(home = 2, away = 1)
        val rng = ScriptedInts()

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        assertEquals(before, after)
        assertEquals(0, rng.draws)
    }

    /**
     * The interval is the first minute of the second half. It needs both the
     * plan's coin and the score, and it draws over the whole eleven like a
     * chasing minute does. Index seven is cell two, the eighth of the whole
     * eleven, and that cell asks for a right sided fullback and nothing of the
     * sub role, so the cascade of section 5.4 reaches the centre back.
     */
    @Test
    fun `the interval changes a drawn non keeper when the coin and the score agree`() {
        val before = scored(home = 0, away = 1)

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(halfTimeSwap = true),
            minute = SECOND_HALF_START,
            clock = CLOCK,
            rng = ScriptedInts(7),
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(SubstitutionReason.HALF_TIME, logged.reason)
        assertEquals(PlayerId(2), logged.off.id)
        assertEquals(CENTRE_BACK_ID, logged.on.id)
        assertEquals(2, logged.on.slot.value)
    }

    /**
     * The same interval with the coin against it changes nothing and makes no
     * draw, which is what lets the coin be drawn once with the plan rather
     * than here.
     */
    @Test
    fun `the interval with the coin against it changes nothing`() {
        val before = scored(home = 0, away = 1)
        val rng = ScriptedInts()

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(halfTimeSwap = false),
            minute = SECOND_HALF_START,
            clock = CLOCK,
            rng = rng,
        )

        assertEquals(before, after)
        assertEquals(0, rng.draws)
    }

    /**
     * A window with nobody left on the bench, or with all five substitutions
     * spent, changes nothing and logs nothing.
     */
    @Test
    fun `a window with nothing left to spend changes nothing`() {
        val plan = planOf(routine = listOf(20))
        val minute = SECOND_HALF_START + 20

        val spent = withEnergy(mapOf(2 to 10)).let {
            it.with(TeamSide.HOME, it.home.copy(substitutionsUsed = 5))
        }
        assertEquals(
            spent,
            spent.runSubstitutionWindow(TeamSide.HOME, plan, minute, CLOCK, ScriptedInts()),
        )

        val benchless = withEnergy(mapOf(2 to 10), bench = emptyList())
        assertEquals(
            benchless,
            benchless.runSubstitutionWindow(TeamSide.HOME, plan, minute, CLOCK, ScriptedInts()),
        )
    }

    /**
     * Five is the allowance and the fifth is still spendable: a side that has
     * used four changes somebody and comes out on five. Without this the only
     * thing pinned would be the refusal, and an allowance of three or of one
     * would pass every other test in the class while quietly costing every AI
     * side two changes a match.
     */
    @Test
    fun `a side that has used four still has a fifth to spend`() {
        val before = withEnergy(mapOf(2 to 55)).let {
            it.with(TeamSide.HOME, it.home.copy(substitutionsUsed = 4))
        }

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(routine = listOf(20)),
            minute = SECOND_HALF_START + 20,
            clock = CLOCK,
            rng = ScriptedInts(),
        )

        assertEquals(5, after.home.substitutionsUsed)
        assertEquals(PlayerId(2), after.log.filterIsInstance<MatchEvent.Substitution>().single().off.id)
    }

    /**
     * The gate is the fifth minute of the half and it is inclusive: a routine
     * minute at four is refused and one at five is taken. Five is also where
     * the earliest routine pool of section 3.8 begins, so a gate any higher
     * would silently delete part of that pool.
     */
    @Test
    fun `the window opens at the fifth minute of the half`() {
        val before = withEnergy(mapOf(2 to 55))
        val plan = planOf(routine = listOf(4, 5))

        val shut = ScriptedInts()
        assertEquals(
            before,
            before.runSubstitutionWindow(TeamSide.HOME, plan, SECOND_HALF_START + 4, CLOCK, shut),
        )
        assertEquals(0, shut.draws)

        val open = before.runSubstitutionWindow(
            TeamSide.HOME,
            plan,
            SECOND_HALF_START + 5,
            CLOCK,
            ScriptedInts(),
        )
        assertEquals(
            PlayerId(2),
            open.log.filterIsInstance<MatchEvent.Substitution>().single().off.id,
        )
    }

    /**
     * Section 3.8 says a human managed side is never substituted
     * automatically, whatever its plan says and however tired it is.
     */
    @Test
    fun `a human managed side is never substituted`() {
        val human = withEnergy(mapOf(2 to 10), humanHome = true)
        assertEquals(
            human,
            human.runSubstitutionWindow(
                TeamSide.HOME,
                planOf(routine = listOf(20)),
                SECOND_HALF_START + 20,
                CLOCK,
                ScriptedInts(),
            ),
        )
    }

    /**
     * Three minutes the window is shut in: one in neither pool, one in the
     * first half, and one before the fifth minute of the second half. The last
     * is section 3.8's own gate on the chain, and no pool of the plan can
     * reach below it on its own, so the plan here names a minute the draw
     * could never produce in order to prove the gate is what refuses it.
     */
    @Test
    fun `a shut window changes nothing and draws nothing`() {
        val state = withEnergy(mapOf(2 to 10))
        val plan = planOf(chasing = listOf(25), routine = listOf(3, 20))

        for (minute in listOf(SECOND_HALF_START + 13, 10, SECOND_HALF_START + 3)) {
            val rng = ScriptedInts()
            assertEquals(
                state,
                state.runSubstitutionWindow(TeamSide.HOME, plan, minute, CLOCK, rng),
                "minute $minute",
            )
            assertEquals(0, rng.draws, "minute $minute")
        }
    }

    private companion object {
        val RULES = RuleSets.CLASSIC
        val CLOCK = MatchClock(firstHalfMinutes = 47, secondHalfMinutes = 48)
        const val SECOND_HALF_START = 47

        val CENTRE_BACK_ID = PlayerId(30)
        val FORWARD_ID = PlayerId(31)

        /**
         * The home bench: one defensive centre back and one offensive forward.
         * The centre back is the stronger of the two, so a test that expects
         * the forward is a test that the cell decided and not the strength.
         */
        fun bench(): List<MatchPlayer> = listOf(
            Lineups.player(
                slot = Slot.UNUSED_SUBSTITUTE.value,
                strength = 70,
                id = CENTRE_BACK_ID.value,
                position = Position.CENTREBACK,
                style = PlayerStyle.DEFENSIVE,
            ),
            Lineups.player(
                slot = Slot.UNUSED_SUBSTITUTE.value,
                strength = 60,
                id = FORWARD_ID.value,
                position = Position.FORWARD,
                style = PlayerStyle.OFFENSIVE,
            ),
        )

        /**
         * Two four four twos of fifties, the home side carrying the bench
         * above and the away side carrying whatever it is given, with the
         * given score and the named home cells set to the named energies. A starter's identity is his cell, so an energy map is
         * keyed by cell number; everybody the map does not name is on the full
         * hundred initialState gives him.
         */
        fun state(
            homeGoals: Int = 0,
            awayGoals: Int = 0,
            energyByCell: Map<Int, Int> = emptyMap(),
            bench: List<MatchPlayer> = bench(),
            awayBench: List<MatchPlayer> = emptyList(),
            humanHome: Boolean = false,
        ): MatchState {
            val home = Lineups.side(
                Lineups.FORMATION_4_4_2.map { Lineups.player(it, strength = 50) },
                humanManaged = humanHome,
            )
            val away = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
            val base = initialState(
                setup = MatchSetup(home, away, season = 1, rules = RULES),
                startingPossessor = TeamSide.HOME,
                homeBench = bench,
                awayBench = awayBench,
            )
            val energy = LinkedHashMap(base.home.energy)
            for ((cell, value) in energyByCell) {
                energy[PlayerId(cell)] = value
            }
            return base.copy(
                home = base.home.copy(energy = energy),
                homeGoals = homeGoals,
                awayGoals = awayGoals,
            )
        }

        fun scored(home: Int, away: Int): MatchState = state(homeGoals = home, awayGoals = away)

        fun withEnergy(
            byCell: Map<Int, Int>,
            bench: List<MatchPlayer> = bench(),
            humanHome: Boolean = false,
        ): MatchState = state(energyByCell = byCell, bench = bench, humanHome = humanHome)

        fun planOf(
            chasing: List<Int> = emptyList(),
            routine: List<Int> = emptyList(),
            halfTimeSwap: Boolean = false,
        ): SubstitutionPlan = SubstitutionPlan(chasing, routine, halfTimeSwap)
    }
}
