package org.openfoot.engine.season

import org.openfoot.dataset.StateChampionshipEntry
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateChampionshipsTest {

    private fun dataset(clubsPerState: Map<Int, Int>, entries: List<StateChampionshipEntry> = emptyList()) = WorldFixtures.dataset(
        clubs = clubsPerState.flatMap { (state, count) ->
            (1..count).map { i ->
                WorldFixtures.club(ref = "s${state}c$i", level = 20 - i % 10).copy(state = state)
            }
        },
    ).copy(stateChampionships = entries)

    private fun states(data: org.openfoot.dataset.WorldDataset): StateSetup {
        val world = generateWorld(data, 3, activeLeagues = setOf(Country.BRAZIL))
        return stateSetup(world.clubs.map { ClubState.fresh(it) }, data) { it.hashCode() }
    }

    @Test
    fun `the presets are the eleven rows of the format spec`() {
        assertEquals(11, STATE_PRESETS.size)
        assertEquals(StatePreset(6, 0, 2, true, 2), STATE_PRESETS[0])
        assertEquals(StatePreset(16, 4, 2, false, 2), STATE_PRESETS[7])
        assertEquals(StatePreset(20, 4, 2, false, 4), STATE_PRESETS[10])
    }

    @Test
    fun `a state needs six clubs, takes the default format without a file, and keeps a reserve`() {
        val setup = states(dataset(mapOf(25 to 7, 18 to 5)))
        val division = setup.divisions.single()
        assertEquals(25, division.state)
        assertEquals(STATE_PRESETS[0], division.preset)
        assertEquals(6, division.clubs.size)
        assertEquals(1, setup.reserve.getValue(25).size)
        assertTrue(18 !in setup.reserve)
    }

    @Test
    fun `a file entry shapes the division, and one asking for more than the state holds is ignored`() {
        val entries = listOf(
            StateChampionshipEntry(state = 25, division = 1, preset = 1, penaltiesTiebreak = false, twoLeggedRounds = listOf(false, false, true)),
            StateChampionshipEntry(state = 18, division = 1, preset = 4, penaltiesTiebreak = true, twoLeggedRounds = listOf(true, true, true)),
        )
        val setup = states(dataset(mapOf(25 to 9, 18 to 8), entries))
        val saoPaulo = setup.divisions.single { it.state == 25 }
        assertEquals(STATE_PRESETS[1], saoPaulo.preset)
        assertEquals(false, saoPaulo.penalties)
        assertEquals(listOf(false, false, true), saoPaulo.twoLeggedRounds)
        val rio = setup.divisions.single { it.state == 18 }
        assertEquals(STATE_PRESETS[0], rio.preset, "a twelve team preset over eight clubs falls to the default")
        assertEquals(true, rio.penalties)
    }

    @Test
    fun `the option turns every state off`() {
        val data = dataset(mapOf(25 to 8)).let { it.copy(options = it.options.copy(playStateChampionships = false)) }
        assertTrue(states(data).divisions.isEmpty())
    }

    @Test
    fun `a six team division is a two turn league feeding a final of two`() {
        val setup = states(dataset(mapOf(25 to 6)))
        val competition = stateCompetition(setup.divisions.single(), realStateGroups = true, rng = SplitMix64Rng(9))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(10, league.rounds.size)
        assertEquals(2, competition.phases.size)
        var played = competition
        while (played.phaseIndex == 0) {
            val matches = played.nextMatches(RuleSets.CLASSIC, SplitMix64Rng(1))
            played = played.recorded(matches.map { it to Result(it.fixture.home, it.fixture.away, 1, 0) })
        }
        val final = (played.phases[1] as Phase.Knockout).phase
        assertEquals(2, final.entrants.size)
        assertEquals(league.overallTable(played.results[0]).first().key, final.entrants.first().key)
    }

    @Test
    fun `a sixteen team preset deals four groups and seeds the quarters within each group`() {
        val setup = states(dataset(mapOf(25 to 16), listOf(StateChampionshipEntry(25, 1, 7, true, listOf(false, false, true)))))
        val competition = stateCompetition(setup.divisions.single(), realStateGroups = true, rng = SplitMix64Rng(9))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(4, league.groups.size)
        assertTrue(league.groups.all { it.size == 4 })
        assertEquals(false, league.gamesInsideGroup)
    }

    @Test
    fun `the group seeding pairs each quarter final inside its group, and the semis cross group A with B and C with D`() {
        val setup = states(dataset(mapOf(25 to 16), listOf(StateChampionshipEntry(25, 1, 7, true, listOf(false, false, true)))))
        val competition = stateCompetition(setup.divisions.single(), realStateGroups = true, rng = SplitMix64Rng(9))
        val league = (competition.phases[0] as Phase.League).phase

        val entrants = competition.qualifiers.pick(league, emptyList())
        val ties = firstRoundTies(entrants)
        assertEquals(4, ties.size)
        ties.forEach { tie ->
            val group = league.groups.single { tie.higher.key in it && tie.lower.key in it }
            assertEquals(group[0], tie.higher.key, "the group's first place holds the tie's higher seat")
            assertEquals(group[1], tie.lower.key)
        }

        val semis = nextRoundTies(ties.map { it.higher })
        assertEquals(2, semis.size)
        val groupWinner = league.groups.map { it[0] }
        assertTrue(semis[0].holds(groupWinner[0]) && semis[0].holds(groupWinner[1]), "group A meets group B")
        assertTrue(semis[1].holds(groupWinner[2]) && semis[1].holds(groupWinner[3]), "group C meets group D")
    }

    @Test
    fun `a grouped preset deals the queue by position, drawing no order at all`() {
        val setup = states(dataset(mapOf(25 to 16), listOf(StateChampionshipEntry(25, 1, 7, true, listOf(false, false, true)))))
        val division = setup.divisions.single()

        val groupsFirstRng = (stateCompetition(division, realStateGroups = true, rng = SplitMix64Rng(9)).phases[0] as Phase.League).phase.groups
        val groupsOtherRng = (stateCompetition(division, realStateGroups = true, rng = SplitMix64Rng(1)).phases[0] as Phase.League).phase.groups
        assertEquals(groupsFirstRng, groupsOtherRng, "the deal reads the queue, not the rng")

        for (g in 0 until 4) {
            assertEquals(
                listOf(division.clubs[g], division.clubs[g + 4], division.clubs[g + 8], division.clubs[g + 12]),
                groupsFirstRng[g],
                "group $g holds queue positions $g, ${g + 4}, ${g + 8} and ${g + 12}",
            )
        }
    }

    @Test
    fun `a single group preset shuffles the clubs before the circle, and the same rng repeats the same order`() {
        val setup = states(dataset(mapOf(25 to 6)))
        val division = setup.divisions.single()

        val first = (stateCompetition(division, realStateGroups = true, rng = SplitMix64Rng(9)).phases[0] as Phase.League).phase.participants
        val repeated = (stateCompetition(division, realStateGroups = true, rng = SplitMix64Rng(9)).phases[0] as Phase.League).phase.participants
        assertEquals(first, repeated, "the same rng draws the same shuffle")
        assertEquals(division.clubs.toSet(), first.toSet(), "the shuffle is a permutation of the division's own clubs")
        assertTrue(first != division.clubs, "the queue order is actually shuffled, not carried through unchanged")
    }

    @Test
    fun `a division's two last go down, the division below's two best go up, and the last division swaps with the reserve as a queue`() {
        val first = StateDivision(25, 1, STATE_PRESETS[0], true, listOf(true, true, true), listOf("a1", "a2", "a3", "a4", "a5", "a6"))
        val second = first.copy(division = 2, clubs = listOf("b1", "b2", "b3", "b4", "b5", "b6"))
        val setup = StateSetup(listOf(first, second), mapOf(25 to listOf("r1", "r2", "r3")))
        val tables = mapOf(
            "state:25:1" to listOf("a6", "a5", "a4", "a3", "a2", "a1"),
            "state:25:2" to listOf("b1", "b2", "b3", "b4", "b5", "b6"),
        )
        val merits = mapOf("state:25:1" to tables.getValue("state:25:1"), "state:25:2" to listOf("b3", "b1", "b2", "b4", "b5", "b6"))

        val turned = stateTurnover(setup, { tables.getValue(it) }, { merits.getValue(it) })
        val next = turned.next

        assertEquals(listOf(StateSwap(25, 1, listOf("a2", "a1"), listOf("b3", "b1")), StateSwap(25, 2, listOf("b5", "b6"), listOf("r1", "r2"))), turned.swaps)
        assertEquals(listOf("a3", "a4", "a5", "a6", "b3", "b1"), next.divisions[0].clubs)
        assertEquals(listOf("b2", "b4", "a2", "a1", "r1", "r2"), next.divisions[1].clubs)
        assertEquals(mapOf(25 to listOf("r3", "b5", "b6")), next.reserve)
        assertEquals(listOf(first.preset, second.preset), next.divisions.map { it.preset })
    }

    @Test
    fun `a reserve shorter than the relegation zone sends down only the last of it`() {
        val only = StateDivision(18, 1, STATE_PRESETS[0], true, listOf(true, true, true), listOf("c1", "c2", "c3", "c4", "c5", "c6"))
        val next = stateTurnover(StateSetup(listOf(only), mapOf(18 to listOf("r1"))), { listOf("c1", "c2", "c3", "c4", "c5", "c6") }, { emptyList() }).next
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5", "r1"), next.divisions.single().clubs)
        assertEquals(mapOf(18 to listOf("c6")), next.reserve)
    }

    @Test
    fun `a club that goes up out of a division is not also sent down from it`() {
        val first = StateDivision(25, 1, STATE_PRESETS[0], true, listOf(true, true, true), listOf("a1", "a2", "a3", "a4", "a5", "a6"))
        val second = first.copy(division = 2, clubs = listOf("b1", "b2", "b3", "b4", "b5", "b6"))
        val setup = StateSetup(listOf(first, second), mapOf(25 to listOf("r1", "r2")))
        val tables = mapOf("state:25:1" to first.clubs, "state:25:2" to second.clubs)
        val next = stateTurnover(setup, { tables.getValue(it) }, { if (it == "state:25:2") listOf("b6", "b1", "b2", "b3", "b4", "b5") else first.clubs }).next
        assertEquals(listOf("a1", "a2", "a3", "a4", "b6", "b1"), next.divisions[0].clubs)
        assertEquals(listOf("b2", "b3", "a5", "a6", "r1", "r2"), next.divisions[1].clubs)
        assertEquals(mapOf(25 to listOf("b4", "b5")), next.reserve)
    }

    /**
     * FORMAT-SPEC load rule six: Sao Paulo's first division on preset 7 with
     * the real groups option on would seat the recorded regional groups when
     * every listed club is present. This version always deals by the queue,
     * and the competition says so when the option asks for more; with the
     * option off, or another state on the same preset, nothing is noted.
     */
    @Test
    fun `Sao Paulo's first division on preset 7 notes the real groups option it ignores`() {
        val entries = listOf(
            StateChampionshipEntry(state = 25, division = 1, preset = 7, penaltiesTiebreak = true, twoLeggedRounds = listOf(false, false, true)),
            StateChampionshipEntry(state = 18, division = 1, preset = 7, penaltiesTiebreak = true, twoLeggedRounds = listOf(false, false, true)),
        )
        val setup = states(dataset(mapOf(25 to 16, 18 to 16), entries))
        val saoPaulo = setup.divisions.single { it.state == 25 }
        val rio = setup.divisions.single { it.state == 18 }
        assertEquals(STATE_PRESETS[7], saoPaulo.preset)
        assertEquals(listOf(Approximation.REAL_STATE_GROUPS_IGNORED.text), stateCompetition(saoPaulo, realStateGroups = true, rng = SplitMix64Rng(9)).approximations)
        assertEquals(emptyList(), stateCompetition(saoPaulo, realStateGroups = false, rng = SplitMix64Rng(9)).approximations)
        assertEquals(emptyList(), stateCompetition(rio, realStateGroups = true, rng = SplitMix64Rng(9)).approximations)
    }
}
