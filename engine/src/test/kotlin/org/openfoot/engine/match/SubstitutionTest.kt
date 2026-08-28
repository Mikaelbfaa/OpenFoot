package org.openfoot.engine.match

import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Side
import org.openfoot.model.Slot
import org.openfoot.model.TeamSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Who comes on when somebody goes off, and what the state looks like after.
 *
 * Cell five is the cell every replacement test below vacates. Section 3.2 asks
 * it for a centre back with no side of its own and the defensive reading of
 * the sub role, so a bench player only fits it outright when he is both a
 * centre back and defensive. Every reserve here therefore states its side and
 * its style rather than taking the fixture's defaults, which are right sided
 * and offensive: an offensive centre back fails both passes the classic rules
 * can reach and reaches the search's catch all, which returns the strongest
 * man regardless of fit and would make an assertion about strength pass for
 * the wrong reason.
 */
class SubstitutionTest {

    /**
     * Both reserves are defensive centre backs, so both fit cell five outright
     * and only the order the bench is offered in separates them. Section 5.4
     * step 2 orders it strongest first, so the eighty comes on and not the
     * sixty, whichever order they were named in.
     */
    @Test
    fun `the strongest fitting reserve takes the vacated cell`() {
        val state = stateWithBench(
            reserve(strength = 60, id = 30, position = Position.CENTREBACK),
            reserve(strength = 80, id = 31, position = Position.CENTREBACK),
        )
        assertEquals(80, chooseReplacement(state, TeamSide.HOME, Slot(5))!!.strength)
    }

    /**
     * The search is a search and not a maximum. The eighty is a centre back
     * with the offensive reading of the role, so he fails cell five's sub role
     * on both passes the classic rules reach; the sixty is defensive and fits
     * at the first pass. The eighty is offered first, because the bench is
     * ordered by strength, and is passed over anyway.
     */
    @Test
    fun `a weaker reserve who fits the cell beats a stronger one who does not`() {
        val state = stateWithBench(
            reserve(
                strength = 80,
                id = 30,
                position = Position.CENTREBACK,
                style = PlayerStyle.OFFENSIVE,
            ),
            reserve(strength = 60, id = 31, position = Position.CENTREBACK),
        )
        assertEquals(60, chooseReplacement(state, TeamSide.HOME, Slot(5))!!.strength)
    }

    /**
     * A cell nobody on the bench was born to still gets filled. The only
     * reserve is an offensive forward, so he fails cell five's sub role at
     * every position of the cascade of section 5.4 and is taken by its catch
     * all instead, because section 3.4 has no notion of an empty pitch cell.
     */
    @Test
    fun `a cell with no natural fit is still filled`() {
        val state = stateWithBench(
            reserve(
                strength = 60,
                id = 30,
                position = Position.FORWARD,
                style = PlayerStyle.OFFENSIVE,
            ),
        )
        assertEquals(60, chooseReplacement(state, TeamSide.HOME, Slot(5))!!.strength)
    }

    /**
     * The keeper's cell takes the section 5.4 cascade with no exception.
     * Section 3.8 is explicit: with no keeper on the bench the cascade
     * descends to a centre back, then a fullback, a midfielder and a forward,
     * and the cell is never left empty for want of a spare goalkeeper. This
     * reverses the earlier reading of section 3.8, which read the rule as a
     * filter that only a keeper could pass. See OPEN-QUESTIONS item 41.
     *
     * The bench below is a forward, a midfielder and a fullback, every one of
     * them stronger than the centre back, so a search that fell back to
     * strength rather than walking the cascade position by position would
     * pick one of the three ahead of him. Every reserve is defensive with no
     * side stated as a mismatch, which is what the keeper's cell asks for at
     * the first, strictest pass of section 3.2's relaxed search, so the
     * cascade's own position order is what decides and nothing about fit
     * does.
     */
    @Test
    fun `the cascade fills the keeper's cell with no exception`() {
        val state = stateWithBench(
            reserve(strength = 95, id = 30, position = Position.FORWARD),
            reserve(strength = 90, id = 31, position = Position.MIDFIELDER),
            reserve(strength = 85, id = 32, position = Position.FULLBACK),
            reserve(strength = 60, id = 33, position = Position.CENTREBACK),
        )
        val replacement = chooseReplacement(state, TeamSide.HOME, Slot(1))
        assertEquals(PlayerId(33), replacement!!.id, "the centre back, not the strongest reserve")
        assertEquals(Position.CENTREBACK, replacement.naturalPosition)
    }

    /**
     * The consequence of the cascade filling the keeper's cell: section 3.4's
     * keeper aggregate rates the outfielder who ends up there rather than
     * reaching for the missing keeper figure, rules.missingKeeperRating, which
     * only applies when the cell has nobody in it at all.
     *
     * The reserve is a strength seventy centre back. Individual abilities are
     * off, so his rating starts at his strength; section 3.3 halves it for
     * being out of position in the keeper's cell, bfRound(70 x 0.5) = 35, or
     * 3.5 on the zero to ten scale a rating is expressed on. Section 3.4 then
     * scales that already halved rating by keeperOutOfPositionFactor and
     * rounds again: bfRound(3.5 x 0.2) = bfRound(0.7) = 1. The missing keeper
     * rating the old, filtered reading would have produced instead is 0.1, so
     * 1.0 against 0.1 is what tells the two readings apart.
     */
    @Test
    fun `an outfielder in goal is rated, not the missing keeper figure`() {
        val reserve = reserve(strength = 70, id = 30, position = Position.CENTREBACK)
        val before = stateWithBench(reserve)
        val keeper = before.setup.home.lineup.first { it.slot.value == 1 }

        val replacement = chooseReplacement(before, TeamSide.HOME, Slot(1))
        assertEquals(reserve.id, replacement!!.id)

        val after = before.substitute(
            team = TeamSide.HOME,
            off = keeper,
            on = replacement,
            cell = keeper.slot,
            minute = 60,
            reason = SubstitutionReason.INJURY,
        )

        assertEquals(1.0, keeperAggregate(after.setup.home, RULES), TOLERANCE)
    }

    /**
     * Section 5.4 step 2 orders on two keys and the second one is real. Both
     * reserves are defensive centre backs of seventy, so strength cannot
     * separate them and only energy can. The fresher of the two is named
     * second, so a comparator that had lost its energy key would return the
     * first through the stability of the sort and never say so; naming him
     * first in the mirror case proves the answer follows the energy and not
     * the position in the list.
     */
    @Test
    fun `two reserves of one strength are separated by energy`() {
        val worn = reserve(strength = 70, id = 30, position = Position.CENTREBACK)
        val fresh = reserve(strength = 70, id = 31, position = Position.CENTREBACK)

        val freshLast = stateOf(
            homeBench = listOf(worn, fresh),
            homeBenchEnergy = mapOf(30 to 50, 31 to 90),
        )
        assertEquals(
            fresh.id,
            chooseReplacement(freshLast, TeamSide.HOME, Slot(5))!!.id,
        )

        val freshFirst = stateOf(
            homeBench = listOf(fresh, worn),
            homeBenchEnergy = mapOf(30 to 50, 31 to 90),
        )
        assertEquals(
            fresh.id,
            chooseReplacement(freshFirst, TeamSide.HOME, Slot(5))!!.id,
        )
    }

    @Test
    fun `an empty bench yields nobody`() {
        assertNull(chooseReplacement(stateWithBench(), TeamSide.HOME, Slot(5)))
    }

    /**
     * A player sent off leaves the pitch and does not join the bench: he is
     * out of the match, not available again. The ten who remain keep the order
     * they had, which is what section 3.4 walks.
     */
    @Test
    fun `leaving the pitch shortens the lineup and does not lengthen the bench`() {
        val before = stateWithBench()
        val victim = before.setup.home.lineup.first { it.slot.value == 5 }
        val after = before.leavePitch(TeamSide.HOME, victim)

        assertEquals(10, after.setup.home.lineup.size)
        assertEquals(0, after.home.bench.size)
        assertEquals(
            before.setup.home.lineup.filter { it.id != victim.id }.map { it.slot.value },
            after.setup.home.lineup.map { it.slot.value },
        )
    }

    /**
     * A substitution puts the reserve into the vacated cell, takes him off the
     * bench, spends one of the five, and logs it. The eleven he joins keep
     * their order and he goes last, matching the squad array of the original,
     * where a reserve sits after the starters and a substitution swaps two
     * slot numbers without moving anybody in the array.
     *
     * His energy is whatever he had, because section 3.9 drains only the
     * pitch, and here that is the full hundred every named player starts on.
     */
    @Test
    fun `a substitution moves the reserve into the vacated cell`() {
        val reserve = reserve(strength = 80, id = 30, position = Position.CENTREBACK)
        val before = stateWithBench(reserve)
        val victim = before.setup.home.lineup.first { it.slot.value == 5 }

        val after = before.substitute(
            team = TeamSide.HOME,
            off = victim,
            on = reserve,
            cell = victim.slot,
            minute = 60,
            reason = SubstitutionReason.INJURY,
        )

        assertEquals(
            before.setup.home.lineup.filter { it.id != victim.id }.map { it.id } + reserve.id,
            after.setup.home.lineup.map { it.id },
        )
        assertEquals(5, after.setup.home.lineup.last().slot.value)
        assertEquals(0, after.home.bench.size)
        assertEquals(1, after.home.substitutionsUsed)
        assertEquals(SideState.FULL_ENERGY, after.home.energy.getValue(reserve.id))

        val logged = after.log.filterIsInstance<MatchEvent.Substitution>().single()
        assertEquals(SubstitutionReason.INJURY, logged.reason)
        assertEquals(victim.id, logged.off.id)
        assertEquals(reserve.id, logged.on.id)
        assertEquals(5, logged.on.slot.value)
    }

    /**
     * Appending the arrival rather than seating him where the departed man sat
     * is observable, and this is the test that makes it so. It needs a line
     * with more players in its cells than that line's take, which no formation
     * of section 5.1 produces and which the manual lineup screen of section
     * 5.4 accepts without complaint, so the lineup here is built by hand.
     *
     * The eleven, in list order: cells 1, 22, 24, 10, 11, 12, 13, 14, 15, 2
     * and 9. Section 3.4's midfield is cells 10 to 17 and takes the first five
     * of them in list order, so cells 10, 11, 12, 13, 14 and 15 are six
     * midfielders competing for five places and cell 15 is the one left out.
     * Everybody is fifty except cell 15, who is twenty, and the reserve, who
     * is eighty. Individual abilities are off and nobody is out of position,
     * so a rating is the strength divided by ten; the marking is light, whose
     * midfield bonus is nought, so the bonus is the same under both orderings
     * and cancels.
     *
     * Cell 11 is the one substituted.
     *
     * Before: cells 10, 11, 12, 13 and 14 count, 5.0 five times over 25.0, and
     * the fixed divisor of five gives 5.0.
     * After, appending: the survivors are 10, 12, 13, 14 and 15 and the
     * arrival is last, so the five counted are 10, 12, 13, 14 and 15, that is
     * 5.0 + 5.0 + 5.0 + 5.0 + 2.0 over 22.0, and the divisor gives 4.4. The
     * eighty who just came on contributes nothing at all.
     * After, seating him at the vacated index instead: the five counted would
     * be 10, the arrival, 12, 13 and 14, that is 5.0 + 8.0 + 5.0 + 5.0 + 5.0
     * over 28.0, and the divisor gives 5.6.
     *
     * Both are asserted, the second by rebuilding the same eleven in the other
     * order, because a fixture the two orderings agree on would prove nothing.
     * The gap of 1.2 is most of the 2.0 of midfield that section 3.16 says
     * moves the possession duel from 55 per cent to about 69. See
     * OPEN-QUESTIONS item 45.
     */
    @Test
    fun `the arrival goes last, which an oversubscribed line can see`() {
        val cells = listOf(1 to 50, 22 to 50, 24 to 50, 10 to 50, 11 to 50, 12 to 50, 13 to 50, 14 to 50, 15 to 20, 2 to 50, 9 to 50)
        val home = Lineups.side(cells.map { (cell, strength) -> Lineups.player(cell, strength) })
        val reserve = Lineups.player(
            slot = Slot.UNUSED_SUBSTITUTE.value,
            strength = 80,
            id = 30,
            position = Position.MIDFIELDER,
            style = PlayerStyle.DEFENSIVE,
        )
        val before = initialState(
            setup = MatchSetup(
                home,
                Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50),
                season = 1,
                rules = RULES,
            ),
            startingPossessor = TeamSide.HOME,
            homeBench = listOf(reserve),
        )
        assertEquals(5.0, midfieldAggregate(before.setup.home, RULES), TOLERANCE)

        val victim = before.setup.home.lineup.first { it.slot.value == 11 }
        val vacatedIndex = before.setup.home.lineup.indexOf(victim)
        val after = before.substitute(
            team = TeamSide.HOME,
            off = victim,
            on = reserve,
            cell = victim.slot,
            minute = 60,
            reason = SubstitutionReason.INJURY,
        )

        val appended = after.setup.home.lineup
        assertEquals(4.4, midfieldAggregate(after.setup.home, RULES), TOLERANCE)
        assertEquals(reserve.id, appended.last().id)

        val seated = appended.dropLast(1).toMutableList()
        seated.add(vacatedIndex, appended.last())
        assertEquals(5.6, midfieldAggregate(home.withLineup(seated), RULES), TOLERANCE)
    }

    /**
     * The away side is a side too. Both rebuilders take the team as an
     * argument, and a copy paste that wrote the home field twice would leave
     * the away eleven whole and shorten the home one instead, so the home side
     * is asserted to be the very same object and not merely to be equal.
     */
    @Test
    fun `an away player leaving the pitch leaves the home side alone`() {
        val before = stateOf(awayBench = listOf(reserve(70, 30, Position.CENTREBACK)))
        val victim = before.setup.away.lineup.first { it.slot.value == 5 }
        val after = before.leavePitch(TeamSide.AWAY, victim)

        assertEquals(10, after.setup.away.lineup.size)
        assertTrue(after.setup.away.lineup.none { it.id == victim.id })
        assertSame(before.setup.home, after.setup.home)
        assertEquals(before.home, after.home)
    }

    /**
     * The same for a whole substitution: the away eleven and the away bench
     * both change, the away side spends one of its five, and every home field
     * is the object it already was.
     */
    @Test
    fun `an away substitution leaves the home side alone`() {
        val arriving = reserve(strength = 80, id = 30, position = Position.CENTREBACK)
        val before = stateOf(
            homeBench = listOf(reserve(strength = 80, id = 30, position = Position.CENTREBACK)),
            awayBench = listOf(arriving),
        )
        val victim = before.setup.away.lineup.first { it.slot.value == 5 }

        val after = before.substitute(
            team = TeamSide.AWAY,
            off = victim,
            on = arriving,
            cell = victim.slot,
            minute = 60,
            reason = SubstitutionReason.INJURY,
        )

        assertEquals(arriving.id, after.setup.away.lineup.last().id)
        assertEquals(5, after.setup.away.lineup.last().slot.value)
        assertEquals(11, after.setup.away.lineup.size)
        assertEquals(1, after.away.substitutionsUsed)
        assertEquals(0, after.away.bench.size)

        assertSame(before.setup.home, after.setup.home)
        assertEquals(before.home, after.home)
        assertEquals(TeamSide.AWAY, after.log.filterIsInstance<MatchEvent.Substitution>().single().side)
    }

    /**
     * When a defender is sent off the AI keeps its shape by sacrificing a
     * forward. The forward's cells are tried first, eighteen to twenty five,
     * and only when nobody stands there does it take an attacking midfielder
     * from fourteen to seventeen. Neither case needs the third range, so both
     * are run as an outfielder's dismissal.
     *
     * The four four two seats its forwards at twenty two and twenty four, and
     * twenty two comes first in the formation's own list, so it is the man
     * chosen and not merely a man in the range.
     */
    @Test
    fun `the sacrifice takes a forward before an attacking midfielder`() {
        val withForwards = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
        assertEquals(22, sacrificeTarget(withForwards, RULES, dismissedWasKeeper = false)!!.slot.value)

        val noForwards = Lineups.sideOfSlots(
            listOf(1, 2, 9, 3, 5, 11, 13, 16, 14, 10, 17),
            strength = 50,
        )
        assertEquals(16, sacrificeTarget(noForwards, RULES, dismissedWasKeeper = false)!!.slot.value)
    }

    /**
     * The two ordinary ranges are the whole rule for an outfielder's
     * dismissal. A side whose eleven stand only in the keeper's cell, the
     * defence and the holding midfield has nobody in either of them, so the
     * AI sacrifices nobody rather than reaching further back.
     */
    @Test
    fun `a side with neither has nobody to sacrifice`() {
        val side = Lineups.sideOfSlots(listOf(1, 2, 9, 3, 4, 5, 6, 7, 8, 11, 13), strength = 50)
        assertNull(sacrificeTarget(side, RULES, dismissedWasKeeper = false))
    }

    /**
     * The third range is section 3.8's own exception to the rule above, and it
     * opens only when the man sent off was the keeper. The same shape as the
     * test above, nobody in eighteen to twenty five or in fourteen to
     * seventeen: an outfielder's dismissal still sacrifices nobody, but a
     * keeper's dismissal falls back to the first man standing anywhere in
     * cells two to twenty five, which in lineup order is cell two.
     */
    @Test
    fun `a dismissed keeper opens a third range an outfielder does not`() {
        val side = Lineups.sideOfSlots(listOf(1, 2, 9, 3, 4, 5, 6, 7, 8, 11, 13), strength = 50)
        assertNull(
            sacrificeTarget(side, RULES, dismissedWasKeeper = false),
            "an outfielder's dismissal must not reach the third range",
        )
        assertEquals(2, sacrificeTarget(side, RULES, dismissedWasKeeper = true)!!.slot.value)
    }

    private companion object {
        val RULES = RuleSets.CLASSIC

        /** Line aggregates are doubles; every figure here is exact to well inside this. */
        const val TOLERANCE = 1e-9

        /**
         * A reserve, sitting on the minus one slot the original leaves on an
         * unused substitute. Side and style are stated rather than defaulted
         * for the reason the class docstring gives; defensive is the reading
         * cell five asks for, so a test that wants a misfit says so.
         */
        fun reserve(
            strength: Int,
            id: Int,
            position: Position,
            side: Side = Side.RIGHT,
            style: PlayerStyle = PlayerStyle.DEFENSIVE,
        ): MatchPlayer = Lineups.player(
            slot = Slot.UNUSED_SUBSTITUTE.value,
            strength = strength,
            id = id,
            position = position,
            side = side,
            style = style,
        )

        /**
         * Both sides in a four four two at fifty, the home side carrying the
         * given bench. Ids of the eleven come from their slots, so a reserve
         * is given thirty upwards and cannot collide with one; initialState
         * requires distinct identities and says so loudly if that stops being
         * true.
         */
        fun stateWithBench(vararg bench: MatchPlayer): MatchState =
            stateOf(homeBench = bench.toList())

        /**
         * The same two four four twos, with either bench filled and with any
         * named reserve's energy overridden. Identities are per side, so the
         * two benches may reuse the same numbers without colliding.
         */
        fun stateOf(
            homeBench: List<MatchPlayer> = emptyList(),
            awayBench: List<MatchPlayer> = emptyList(),
            homeBenchEnergy: Map<Int, Int> = emptyMap(),
        ): MatchState {
            val home = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
            val away = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
            val base = initialState(
                setup = MatchSetup(home, away, season = 1, rules = RULES),
                startingPossessor = TeamSide.HOME,
                homeBench = homeBench,
                awayBench = awayBench,
            )
            if (homeBenchEnergy.isEmpty()) {
                return base
            }
            val energy = LinkedHashMap(base.home.energy)
            for ((id, value) in homeBenchEnergy) {
                energy[PlayerId(id)] = value
            }
            return base.copy(home = base.home.copy(energy = energy))
        }
    }
}
