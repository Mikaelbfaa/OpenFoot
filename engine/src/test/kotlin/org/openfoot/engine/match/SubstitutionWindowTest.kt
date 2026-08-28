package org.openfoot.engine.match

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.RuleSet
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
     * to the end of the lineup and no further. Cell twenty two is the second
     * man in the list and the only tired one, so a start of nought reaches him
     * at once; a start of three walks the remaining seven, never reaches back
     * to him, and finds nobody.
     *
     * A start of two, with cells twenty two and two both tired, proves the
     * walk goes forward rather than back: it skips the twenty two behind it
     * and still reaches the two ahead of it, which a version that stopped
     * scanning at the first miss rather than continuing to the end would also
     * fail.
     */
    @Test
    fun `the late scan walks forward to the end and stops`() {
        val oneTired = withEnergy(mapOf(22 to 85))
        assertEquals(
            22,
            tirednessTarget(oneTired, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(0))!!
                .slot.value,
            "a start at the front reaches him without needing to wrap",
        )
        assertNull(
            tirednessTarget(oneTired, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(3)),
            "a start past him walks only to the end of the lineup and never circles back",
        )

        val twoTired = withEnergy(mapOf(22 to 85, 2 to 85))
        assertEquals(
            2,
            tirednessTarget(twoTired, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(2))!!
                .slot.value,
        )
    }

    /**
     * The exact shape the missing wrap costs a match: a tired man stands at
     * list index nought, and a start drawn anywhere after him leaves him
     * unreached. The formation's own list puts the keeper at index nought,
     * whom the scan would skip regardless of his energy, so this uses
     * FRONT_LOADED_CELLS to put a fullback there instead, with the keeper one
     * place behind him rather than absent.
     */
    @Test
    fun `a tired man at the front of the list is missed once the start is past him`() {
        val state = withEnergy(mapOf(2 to 85), homeCells = FRONT_LOADED_CELLS)

        assertEquals(
            2,
            tirednessTarget(state, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(0))!!.slot.value,
            "a start at index nought reaches the tired man standing there",
        )
        assertNull(
            tirednessTarget(state, TeamSide.HOME, intoHalf = 41, rng = ScriptedInts(5)),
            "a start drawn after him walks only to the end and never circles back to index nought",
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
     * The score windows avoid taking off a man who came on earlier in the same
     * match, and section 3.8 gives them exactly one redraw to do it with.
     *
     * The home side is a goal down on its own chasing minute and its cell 2 is
     * a man who came on. Two draws in order: 7, which is cell 2 in lineup
     * order and is the man to be avoided, then 2, which is cell 24 and is the
     * third of the eleven. Neither index is nought and neither cell is the
     * first of the lineup, so nothing here passes by the draw being ignored.
     *
     * Cell 24 asks for an offensive forward, so the sixty comes on rather than
     * the stronger centre back that stands first on the bench: the man who
     * comes on is neither the first reserve nor the best one.
     */
    @Test
    fun `a score window redraws once off a man who came on`() {
        val before = arriving(scored(home = 0, away = 1), TeamSide.HOME, listOf(PlayerId(2)))
        val rng = ScriptedInts(7, 2)

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
        assertEquals(2, rng.draws)
    }

    /**
     * A draw that names nobody who came on is not redrawn, even with a list of
     * arrivals to consult. The same state as above and the same list, with the
     * single draw 2 naming cell 24 rather than the cell 2 the list holds, so a
     * window that redrew unconditionally would run ScriptedInts out and fail.
     */
    @Test
    fun `a draw that misses the arrivals is not redrawn`() {
        val before = arriving(scored(home = 0, away = 1), TeamSide.HOME, listOf(PlayerId(2)))
        val rng = ScriptedInts(2)

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        assertEquals(PlayerId(24), after.log.filterIsInstance<MatchEvent.Substitution>().single().off.id)
        assertEquals(1, rng.draws)
    }

    /**
     * One redraw and not a loop: when the redraw names a second man who came
     * on, he is the one taken off anyway.
     *
     * Both cell 2 and cell 24 came on here, and the two draws are 7 and 2, the
     * same pair as the redraw test above. The window takes off cell 24, which
     * is a man the rule was trying to protect, and makes no third draw.
     */
    @Test
    fun `the redraw stands even when it names another man who came on`() {
        val before = arriving(
            scored(home = 0, away = 1),
            TeamSide.HOME,
            listOf(PlayerId(2), PlayerId(24)),
        )
        val rng = ScriptedInts(7, 2)

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(PlayerId(24), logged.off.id)
        assertEquals(2, rng.draws)
    }

    /**
     * The redraw runs before the keeper is judged, which is the order these
     * two guards of section 3.8 are applied in and the one case that tells the
     * two orders apart.
     *
     * The keeper here came on this match, which a side whose own keeper was
     * hurt reaches routinely, so index nought names both the keeper and a man
     * who has just arrived. Under the order taken the arrival is redrawn off
     * and the second draw, 2, names cell 24, so the window makes a change and
     * spends two draws. Under the other order the keeper would have been
     * judged first, the window would have died on him and the draw count would
     * have been one. See OPEN-QUESTIONS item 48.
     */
    @Test
    fun `a keeper who came on is redrawn rather than wasting the window`() {
        val before = arriving(scored(home = 0, away = 1), TeamSide.HOME, listOf(PlayerId(1)))
        val rng = ScriptedInts(0, 2)

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(PlayerId(24), logged.off.id)
        assertEquals(2, rng.draws)
    }

    /**
     * The keeper is judged on whichever index the draw finally settled on, so
     * a redraw that lands on him wastes the window exactly as a first draw on
     * him would. Cell 2 came on, the first draw of 7 names him, and the redraw
     * of nought names the keeper: nothing changes, nothing is logged, and no
     * third draw is made.
     */
    @Test
    fun `a redraw that lands on the keeper wastes the window`() {
        val before = arriving(scored(home = 0, away = 1), TeamSide.HOME, listOf(PlayerId(2)))
        val rng = ScriptedInts(7, 0)

        val after = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        assertEquals(before, after)
        assertEquals(2, rng.draws)
    }

    /**
     * The man a window brings on is recorded as an arrival, and the next
     * window will not take him off. Two windows on one state, end to end,
     * which is the only test here that does not hand the arrivals in ready
     * made.
     *
     * The routine minute takes cell 2 off for the centre back, who is appended
     * to the end of the lineup and so stands at index ten of eleven. The
     * chasing minute that follows draws exactly that index, finds the man who
     * came on a minute ago and redraws 2, which is cell 24, taking the forward
     * off the bench instead. Index ten is the last of the list and index two
     * the third, so neither is reachable by taking the front of the lineup.
     */
    @Test
    fun `a substitute who has just come on is not taken off again`() {
        val before = state(homeGoals = 0, awayGoals = 1, energyByCell = mapOf(2 to 55))

        val afterRoutine = before.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(routine = listOf(20)),
            minute = SECOND_HALF_START + 20,
            clock = CLOCK,
            rng = ScriptedInts(),
        )

        assertEquals(listOf(Arrival(TeamSide.HOME, CENTRE_BACK_ID)), afterRoutine.home.arrivals)
        assertEquals(CENTRE_BACK_ID, afterRoutine.setup.home.lineup.last().id)

        val rng = ScriptedInts(10, 2)
        val after = afterRoutine.runSubstitutionWindow(
            team = TeamSide.HOME,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        val chasing = after.log.filterIsInstance<MatchEvent.Substitution>().last()
        assertEquals(SubstitutionReason.CHASING, chasing.reason)
        assertEquals(PlayerId(24), chasing.off.id)
        assertEquals(FORWARD_ID, chasing.on.id)
        assertEquals(2, rng.draws)
        assertEquals(
            listOf(Arrival(TeamSide.HOME, CENTRE_BACK_ID), Arrival(TeamSide.HOME, FORWARD_ID)),
            after.home.arrivals,
        )
    }

    /**
     * Section 3.15 item 12: the check reads the home side's list of arrivals
     * whichever side the window belongs to, so the away side has no protection
     * at all and can take off, a minute later, the substitute it has just
     * brought on.
     *
     * The away side is a goal down on its own chasing minute, and its cell 24
     * is a man it brought on; the home side has brought nobody on. Its single
     * draw of 2 names that very man and he goes straight off, with no redraw
     * and so with one draw and not two.
     */
    @Test
    fun `the away side takes off the man it just brought on`() {
        val before = arriving(
            state(homeGoals = 1, awayGoals = 0, awayBench = bench()),
            TeamSide.AWAY,
            listOf(PlayerId(24)),
        )
        val rng = ScriptedInts(2)

        val after = before.runSubstitutionWindow(
            team = TeamSide.AWAY,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(TeamSide.AWAY, logged.side)
        assertEquals(PlayerId(24), logged.off.id)
        assertEquals(1, rng.draws)
    }

    /**
     * The modern rules point each side at its own list, so the same state and
     * the same first draw redraw for the away side instead. The second draw,
     * 1, names cell 22, and the away side changes him rather than the man it
     * had just brought on.
     *
     * Cell 22 asks for an offensive forward, so the sixty comes on rather than
     * the stronger centre back that stands first on the bench: the reserve
     * expected here is neither the first of the bench nor the best of it, so
     * the assertion cannot be satisfied by a search that took either.
     */
    @Test
    fun `the modern rules protect the away side too`() {
        val before = arriving(
            state(homeGoals = 1, awayGoals = 0, awayBench = bench(), rules = RuleSets.MODERN),
            TeamSide.AWAY,
            listOf(PlayerId(24)),
        )
        val rng = ScriptedInts(2, 1)

        val after = before.runSubstitutionWindow(
            team = TeamSide.AWAY,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(PlayerId(22), logged.off.id)
        assertEquals(FORWARD_ID, logged.on.id)
        assertEquals(22, logged.on.slot.value)
        assertEquals(2, rng.draws)
    }

    /**
     * A home arrival whose squad index an away player also holds does not
     * protect that away player, because the check compares the side as well as
     * the identity.
     *
     * A PlayerId is an index into the squad its owner was picked from, so the
     * two squads hand out the same small numbers and a home arrival can carry
     * the number of an away starter. Here the home side has brought on a man
     * whose id is 24 and the away side's cell 24 carries that same id, and the
     * away window draws 2, which names it. Section 3.15 item 12 says the away
     * side has no protection at all, so what must happen is one draw and that
     * man going off; a check that compared bare numbers would redraw here, and
     * would both protect a man item 12 leaves unprotected and spend a second
     * draw the away side's stream never gives back.
     */
    @Test
    fun `a home arrival does not protect the away player who shares his id`() {
        val before = arriving(
            state(homeGoals = 1, awayGoals = 0, awayBench = bench()),
            TeamSide.HOME,
            listOf(PlayerId(24)),
        )
        val rng = ScriptedInts(2)

        val after = before.runSubstitutionWindow(
            team = TeamSide.AWAY,
            plan = planOf(chasing = listOf(25)),
            minute = SECOND_HALF_START + 25,
            clock = CLOCK,
            rng = rng,
        )

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(TeamSide.AWAY, logged.side)
        assertEquals(PlayerId(24), logged.off.id)
        assertEquals(1, rng.draws)
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
            rules: RuleSet = RULES,
            homeCells: List<Int> = Lineups.FORMATION_4_4_2,
        ): MatchState {
            val home = Lineups.side(
                homeCells.map { Lineups.player(it, strength = 50) },
                humanManaged = humanHome,
            )
            val away = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
            val base = initialState(
                setup = MatchSetup(home, away, season = 1, rules = rules),
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

        /**
         * The same state with the named side already carrying the given men
         * as arrivals. Handing the list in rather than playing the window that
         * would have produced it keeps a test of the redraw a test of the
         * redraw alone; the one test that does play both windows in order is
         * "a substitute who has just come on is not taken off again".
         *
         * The ids are stamped with the side whose list they are being put on,
         * which is what substitute itself does. A test that wants a home
         * arrival carrying an id an away player also holds asks for it that
         * way, by putting the id on the home side's list.
         */
        fun arriving(state: MatchState, team: TeamSide, ids: List<PlayerId>): MatchState =
            state.with(team, state.of(team).copy(arrivals = ids.map { Arrival(team, it) }))

        fun withEnergy(
            byCell: Map<Int, Int>,
            bench: List<MatchPlayer> = bench(),
            humanHome: Boolean = false,
            homeCells: List<Int> = Lineups.FORMATION_4_4_2,
        ): MatchState = state(energyByCell = byCell, bench = bench, humanHome = humanHome, homeCells = homeCells)

        /**
         * Formation four's own eleven cells, reordered so a fullback rather
         * than the keeper stands at list index nought. Section 3.8's tiredness
         * scan skips the keeper by his cell rather than by his position in the
         * list, so this shape is what lets a test put a tired non keeper at
         * the very front of the lineup.
         */
        val FRONT_LOADED_CELLS = listOf(2, 1, 22, 24, 11, 13, 14, 16, 9, 3, 5)

        fun planOf(
            chasing: List<Int> = emptyList(),
            routine: List<Int> = emptyList(),
            halfTimeSwap: Boolean = false,
        ): SubstitutionPlan = SubstitutionPlan(chasing, routine, halfTimeSwap)
    }
}
