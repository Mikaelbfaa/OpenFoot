package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NationalLeaguesTest {

    /** Twenty four Brazilian clubs, levels twenty down to nine, so the pyramid seats twenty in division one and the rest stand without one. */
    private val data = WorldFixtures.dataset(
        clubs = (1..24).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it)) },
    )

    private fun clubs() = generateWorld(data, 4, activeLeagues = setOf(Country.BRAZIL)).clubs.map { ClubState.fresh(it) }

    @Test
    fun `the divisions come from the standings, with the embedded relegation default`() {
        val divisions = leagueDivisions(Country.BRAZIL, clubs(), data)
        assertEquals(1, divisions.size)
        assertEquals(20, divisions.single().clubs.size)
        assertEquals(4, divisions.single().relegated)
        assertEquals(null, divisions.single().config)
    }

    @Test
    fun `a flat division is one shuffled league of the default turns`() {
        val division = leagueDivisions(Country.BRAZIL, clubs(), data).single()
        val competition = leagueCompetition(division, SplitMix64Rng(5))
        val league = (competition.phases.single() as Phase.League).phase
        assertEquals(38, league.rounds.size)
        assertEquals(division.clubs.toSet(), league.participants.toSet())
        assertTrue(league.participants != division.clubs, "the order is shuffled before the circle")
        assertEquals(league.participants, (leagueCompetition(division, SplitMix64Rng(5)).phases.single() as Phase.League).phase.participants)
    }

    /**
     * Two groups rather than the Brazilian fourth division's real eight: the shared clubs()
     * fixture always seats twenty in division one from the embedded default of 1.9, no matter
     * what this test's own config says, since leagueDivisions reads a club's division off
     * ClubState.standing rather than off this config. Twenty splits into four groups of five, an
     * odd size RoundRobinPhase.grouped's own round robin per group refuses; two groups of ten
     * stays even while still exercising a real multi group deal and a power of two knockout field.
     */
    @Test
    fun `a grouped division with a final phase deals groups and seeds the knockout by group`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 20, relegated = 4, turns = 1, penaltiesTiebreak = true, groups = 2, knockoutQualifiers = 2)
        val division = leagueDivisions(Country.BRAZIL, clubs(), data.copy(leagues = listOf(config))).single()
        val competition = leagueCompetition(division, SplitMix64Rng(5))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(2, league.groups.size)
        assertTrue(league.gamesInsideGroup)
        assertEquals(2, competition.phases.size)
    }

    /**
     * Four groups over the shared fixture's twenty club division is five clubs a group, an odd
     * size RoundRobinPhase.grouped's own round robin per group refuses; leagueCompetition now
     * catches this itself before handing the split to RoundRobinPhase, with a message that names
     * the division rather than one from deep inside the phase code.
     */
    @Test
    fun `a division that does not split into equal even groups is refused by name`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 20, relegated = 4, turns = 1, penaltiesTiebreak = true, groups = 4, knockoutQualifiers = 2)
        val division = leagueDivisions(Country.BRAZIL, clubs(), data.copy(leagues = listOf(config))).single()
        val failure = assertFailsWith<IllegalArgumentException> { leagueCompetition(division, SplitMix64Rng(5)) }
        assertTrue(failure.message!!.contains("league:${Country.BRAZIL}:1"), failure.message!!)
    }

    /**
     * Two groups of ten with three qualifiers each seeds a final phase field of six, which is
     * not a power of two; leagueCompetition refuses this itself rather than letting
     * KnockoutPhase's own init throw from deep inside the phase code.
     */
    @Test
    fun `a final phase field that is not a power of two is refused by name`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 20, relegated = 4, turns = 1, penaltiesTiebreak = true, groups = 2, knockoutQualifiers = 3)
        val division = leagueDivisions(Country.BRAZIL, clubs(), data.copy(leagues = listOf(config))).single()
        val failure = assertFailsWith<IllegalArgumentException> { leagueCompetition(division, SplitMix64Rng(5)) }
        assertTrue(failure.message!!.contains("league:${Country.BRAZIL}:1"), failure.message!!)
    }

    @Test
    fun `movement reads the last and the first of the final order`() {
        val division = leagueDivisions(Country.BRAZIL, clubs(), data).single()
        val order = division.clubs
        val moved = movement(division, order, promotedCount = 2)
        assertEquals(order.takeLast(4), moved.relegated)
        assertEquals(order.take(2), moved.promoted)
    }
}
