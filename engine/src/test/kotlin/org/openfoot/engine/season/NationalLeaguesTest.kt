package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `a grouped division with a final phase deals groups and takes each group's top by the group seeds`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 20, relegated = 4, turns = 1, penaltiesTiebreak = true, groups = 2, knockoutQualifiers = 2)
        val division = leagueDivisions(Country.BRAZIL, clubs(), data.copy(leagues = listOf(config))).single()
        val competition = leagueCompetition(division, SplitMix64Rng(5))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(2, league.groups.size)
        assertTrue(league.gamesInsideGroup)
        assertEquals(2, competition.phases.size)
        assertEquals(Qualifiers.PerGroup(2), competition.qualifiers)
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

    private val rules = RuleSets.CLASSIC

    /** The one stream every knockout of these tests is played and read back with, so a shootout reads the same draw both times. */
    private val knockoutRng = SplitMix64Rng(11)

    /**
     * A hand built division two of Brazil, bypassing the pyramid so the group
     * shape is the test's own: clubs named g01 upward, one turn, the given
     * groups and qualifiers per group.
     */
    private fun groupedLeague(clubs: Int, groups: Int, perGroup: Int, insideGroup: Boolean = true, overall: Boolean = false): Competition {
        val config = LeagueConfigEntry(
            country = Country.BRAZIL, division = 2, teamCount = clubs, relegated = 2, turns = 1, penaltiesTiebreak = true,
            groups = groups, gamesInsideGroup = insideGroup, knockoutQualifiers = perGroup, qualifyByOverallTable = overall,
        )
        val members = (1..clubs).map { "g${it.toString().padStart(2, '0')}" }
        return leagueCompetition(LeagueDivision(Country.BRAZIL, 2, config, members), SplitMix64Rng(7))
    }

    /** Every home side wins by one, so the tables are decided by the fixture list alone. */
    private fun homeWins(matches: List<ScheduledMatch>) = matches.map { it to Result(it.fixture.home, it.fixture.away, 1, 0) }

    private fun played(competition: Competition): Competition {
        var current = competition
        while (!current.finished) current = current.recorded(homeWins(current.nextMatches(rules, knockoutRng)))
        return current
    }

    private fun Competition.league() = (phases[0] as Phase.League).phase

    private fun Competition.knockout() = (phases[1] as Phase.Knockout).phase

    /** Every club's group index and one based place, read off its own group's table of the played league phase. */
    private fun Competition.places(): Map<String, Pair<Int, Int>> {
        val league = league()
        return league.groups.indices
            .flatMap { g -> league.groupTable(g, results[0]).mapIndexed { i, row -> row.key to (g to i + 1) } }
            .toMap()
    }

    private fun Competition.tiesOf(round: Int) = knockout().ties(round, results[1], rules, knockoutRng)

    /**
     * FORMAT-SPEC pairs a grouped preset's first knockout round inside the
     * group, and section 1.11 has a grouped league reuse that engine. Four
     * groups of eight sending four each make a field of sixteen: the first
     * round is first against fourth and second against third of one group,
     * groups in order, the second round is each group's own final, and only
     * the third crosses group A with B and C with D.
     */
    @Test
    fun `four groups of four qualifiers pair inside each group until one club a group remains`() {
        val competition = played(groupedLeague(clubs = 32, groups = 4, perGroup = 4))
        val places = competition.places()
        val first = competition.tiesOf(0)
        assertEquals(8, first.size)
        first.forEach { tie ->
            val (higherGroup, higherPlace) = places.getValue(tie.higher.key)
            val (lowerGroup, lowerPlace) = places.getValue(tie.lower.key)
            assertEquals(higherGroup, lowerGroup, "${tie.higher.key} and ${tie.lower.key} are of one group")
            assertEquals(5, higherPlace + lowerPlace, "place p meets place five minus p")
            assertTrue(higherPlace < lowerPlace, "the better placed holds the higher seat")
        }
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3), first.map { places.getValue(it.higher.key).first })
        competition.tiesOf(1).forEach { tie ->
            assertEquals(places.getValue(tie.higher.key).first, places.getValue(tie.lower.key).first, "the second round is a group's own final")
        }
        assertEquals(
            listOf(setOf(0, 1), setOf(2, 3)),
            competition.tiesOf(2).map { tie -> setOf(places.getValue(tie.higher.key).first, places.getValue(tie.lower.key).first) },
        )
    }

    /**
     * Two qualifiers a group: first against second of the same group, and
     * FORMAT-SPEC's "o melhor colocado recebe a volta", the group's first
     * hosting the return leg and its second the first leg.
     */
    @Test
    fun `two qualifiers a group meet first against second of their group, the first hosting the return leg`() {
        val competition = played(groupedLeague(clubs = 20, groups = 2, perGroup = 2))
        val places = competition.places()
        val ties = competition.tiesOf(0)
        assertEquals(2, ties.size)
        assertTrue(competition.knockout().twoLegged(0))
        ties.forEachIndexed { g, tie ->
            assertEquals(g to 1, places.getValue(tie.higher.key))
            assertEquals(g to 2, places.getValue(tie.lower.key))
            val legs = competition.results[1].filter { tie.holds(it.home) && tie.holds(it.away) }
            assertEquals(listOf(tie.lower.key, tie.higher.key), legs.map { it.home })
        }
    }

    /** With the overall table on, section 1.11 ignores the groups: the top of the overall table is seeded in table order, strong against weak. */
    @Test
    fun `with the overall table on, the qualifiers are seeded by the overall table, strong against weak`() {
        val competition = played(groupedLeague(clubs = 20, groups = 2, perGroup = 2, overall = true))
        val table = competition.league().overallTable(competition.results[0]).map { it.key }
        assertEquals(table.take(4).mapIndexed { i, key -> Entrant(key, i + 1) }, competition.knockout().entrants)
        assertEquals(listOf(table[0] to table[3], table[1] to table[2]), competition.tiesOf(0).map { it.higher.key to it.lower.key })
    }

    /**
     * Four groups of four playing only across each other, twelve rounds,
     * then a final phase of eight over two legs a round, six dates: the
     * schedule lays exactly the competition's eighteen dated rounds, and the
     * competition closes on the last of them.
     */
    @Test
    fun `a cross group league with a final phase takes exactly its dated rounds and closes on the last`() {
        val built = groupedLeague(clubs = 16, groups = 4, perGroup = 2, insideGroup = false)
        assertFalse(built.league().gamesInsideGroup)
        assertEquals(18, built.datedRounds)
        assertEquals(built.datedRounds, SeasonSchedule.build(2026, listOf(built)).slots.count { it.competition == built.key })
        var current = built
        var recorded = 0
        while (!current.finished) {
            current = current.recorded(homeWins(current.nextMatches(rules, knockoutRng)))
            recorded++
        }
        assertEquals(built.datedRounds, recorded)
        assertEquals(16, current.finalOrder(rules, knockoutRng).toSet().size)
    }

    /**
     * The dataset allows an odd team count, and section 1.3 records the odd
     * path of the round robin as unreachable for any configured league, so a
     * division of an odd count is refused up front with its key and count.
     */
    @Test
    fun `a division of an odd club count is refused by name`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 2, teamCount = 9, relegated = 2, turns = 1, penaltiesTiebreak = true)
        val division = LeagueDivision(Country.BRAZIL, 2, config, (1..9).map { "g$it" })
        val failure = assertFailsWith<IllegalArgumentException> { leagueCompetition(division, SplitMix64Rng(7)) }
        val message = failure.message.orEmpty()
        assertTrue("league:${Country.BRAZIL}:2" in message && "9 clubs" in message, message)
    }

    @Test
    fun `movement reads the last and the first of the final order`() {
        val division = leagueDivisions(Country.BRAZIL, clubs(), data).single()
        val order = division.clubs
        val moved = movement(division, order, promotedCount = 2)
        assertEquals(order.takeLast(4), moved.relegated)
        assertEquals(order.take(2), moved.promoted)
    }

    /** A configured division of Brazil over clubs named n01 upward, built straight from its configuration. */
    private fun configured(config: LeagueConfigEntry): Competition =
        leagueCompetition(LeagueDivision(Country.BRAZIL, config.division, config, (1..config.teamCount).map { "n${it.toString().padStart(2, '0')}" }), SplitMix64Rng(3))

    private fun tier(division: Int, teams: Int, relegated: Int) =
        LeagueConfigEntry(country = Country.BRAZIL, division = division, teamCount = teams, relegated = relegated, turns = 1, penaltiesTiebreak = true)

    @Test
    fun `a division built as configured carries no note`() {
        assertEquals(emptyList(), configured(tier(2, 20, 4)).approximations)
        assertEquals(emptyList(), configured(tier(2, 20, 4).copy(groups = 2, knockoutQualifiers = 2)).approximations)
    }

    /** Section 1.11's Serie C sentinel is played as one flat league with no final phase, and the competition says so. */
    @Test
    fun `the Serie C sentinel plays a flat league and notes it`() {
        val competition = configured(tier(3, 20, 4).copy(knockoutQualifiers = LeagueConfigEntry.SERIE_C_FORMAT))
        assertEquals(1, competition.phases.size)
        assertEquals(listOf(Approximation.SERIE_C_AS_FLAT_LEAGUE.text), competition.approximations)
    }

    /** Either playoff field of section 1.12, a relegation playoff or promotion playoff places, is replaced by direct movement, and noted. */
    @Test
    fun `a playoff configuration notes that movement is direct`() {
        val note = listOf(Approximation.PLAYOFFS_AS_DIRECT_MOVEMENT.text)
        assertEquals(note, configured(tier(2, 22, 3).copy(directRelegated = 2)).approximations)
        assertEquals(note, configured(tier(2, 22, 3).copy(promotionPlayoffPlaces = 1)).approximations)
    }

    @Test
    fun `a grouped league with best thirds notes that they are ignored`() {
        val competition = configured(tier(2, 20, 4).copy(groups = 2, knockoutQualifiers = 2, bestThirds = true))
        assertEquals(listOf(Approximation.BEST_THIRDS_IGNORED.text), competition.approximations)
    }

    /**
     * Relegation by group is only a different reading when there are groups:
     * Brazil's distributed divisions one and three set the flag without any,
     * and carry no note for it, while a grouped division that sets it does.
     */
    @Test
    fun `relegation by group is noted only on a grouped league`() {
        assertEquals(emptyList(), configured(tier(1, 20, 4).copy(relegatedByGroup = true)).approximations)
        assertEquals(
            listOf(Approximation.SERIE_C_AS_FLAT_LEAGUE.text),
            configured(tier(3, 20, 4).copy(relegatedByGroup = true, knockoutQualifiers = LeagueConfigEntry.SERIE_C_FORMAT)).approximations,
        )
        assertEquals(
            listOf(Approximation.RELEGATION_BY_GROUP_IGNORED.text),
            configured(tier(2, 20, 4).copy(groups = 2, knockoutQualifiers = 2, relegatedByGroup = true)).approximations,
        )
    }

    /** Several approximations on one division are listed in the declaration order of Approximation, whatever the configuration. */
    @Test
    fun `several notes are listed in one fixed order`() {
        val competition = configured(
            tier(2, 20, 4).copy(groups = 2, knockoutQualifiers = 2, relegatedByGroup = true, bestThirds = true, directRelegated = 3, promotionPlayoffPlaces = 2),
        )
        assertEquals(
            listOf(Approximation.PLAYOFFS_AS_DIRECT_MOVEMENT, Approximation.BEST_THIRDS_IGNORED, Approximation.RELEGATION_BY_GROUP_IGNORED).map { it.text },
            competition.approximations,
        )
    }
}
