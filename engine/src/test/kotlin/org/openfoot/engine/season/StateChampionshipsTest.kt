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
        val competition = stateCompetition(setup.divisions.single(), SplitMix64Rng(9))
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
        val competition = stateCompetition(setup.divisions.single(), SplitMix64Rng(9))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(4, league.groups.size)
        assertTrue(league.groups.all { it.size == 4 })
        assertEquals(false, league.gamesInsideGroup)
    }

    @Test
    fun `the group seeding pairs each quarter final inside its group, and the semis cross group A with B and C with D`() {
        val setup = states(dataset(mapOf(25 to 16), listOf(StateChampionshipEntry(25, 1, 7, true, listOf(false, false, true)))))
        val competition = stateCompetition(setup.divisions.single(), SplitMix64Rng(9))
        val league = (competition.phases[0] as Phase.League).phase

        val entrants = competition.qualifiers(league, emptyList())
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
}
