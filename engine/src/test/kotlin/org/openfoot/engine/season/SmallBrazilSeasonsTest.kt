package org.openfoot.engine.season

import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.clubKey
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.RuleSets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two whole seasons of SmallBrazil, the fixture the command line's two
 * season golden vector pins, played round by round so every match can be
 * checked as it is played, with the invariants a season with state
 * championships and a fourth division built from them must keep:
 *
 * every Brazilian club stands in exactly one place at every season's end, a
 * division of the pyramid or Brazil's reserve queue, and its standing says
 * which; the four divisions keep eight clubs each, the two state divisions
 * sixteen and eight, the two state reserves two each; every competition
 * closes exactly once; at least one suspension is served, a record going
 * from suspended to not suspended in its competition, and no man suspended
 * in a competition takes the field in a match of it.
 *
 * The determinism tripwire replays the two seasons from the same seed and
 * asserts whole SeasonStates equal, which is meaningful because every field
 * down to a competition's qualification rule is data. It also rebuilds each
 * season's fourth by hand, leagueCompetition over the same members with the
 * fork buildCompetitions takes for league:29:4, and asserts its first phase
 * equal to the one withBrazilianFourthIfDue built when the last state
 * competition closed, so building the division later draws exactly what
 * building it at the opening would have.
 */
class SmallBrazilSeasonsTest {

    private val seed = 42L

    private fun opening(): SeasonState =
        openingSeason(generateWorld(SmallBrazil.dataset, seed, SmallBrazil.activeLeagues), SmallBrazil.dataset, SmallBrazil.activeLeagues, SmallBrazil.YEAR, seed)

    private fun turn(state: SeasonState) = nextSeason(state, SmallBrazil.activeLeagues, RuleSets.CLASSIC)

    private class Tally {
        var served = 0
        var suspendedChecks = 0
    }

    /**
     * Plays a season one date at a time and, for every match of the date,
     * checks each squad member of both sides who was suspended in the
     * match's competition before the date: his appearances must not have
     * grown, and a record no longer suspended afterwards counts as a served
     * suspension. playSeason then fires the Sundays left after the last date.
     */
    private fun playChecked(start: SeasonState, tally: Tally): SeasonState {
        var state = start
        while (!state.finished) {
            val before = state
            state = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
            for (played in state.played.drop(before.played.size)) {
                for (key in listOf(played.match.fixture.home, played.match.fixture.away)) {
                    val now = state.club(key).records
                    before.club(key).records.forEachIndexed { index, record ->
                        if (!record.disciplineIn(played.competition).suspended) return@forEachIndexed
                        tally.suspendedChecks++
                        assertEquals(record.appearances, now[index].appearances, "$key's man $index played ${played.competition} on ${played.date} while suspended")
                        if (!now[index].disciplineIn(played.competition).suspended) tally.served++
                    }
                }
            }
        }
        return playSeason(state, RuleSets.CLASSIC, WeeklyTick.NONE)
    }

    private fun participants(state: SeasonState, division: Int) = state.competitions.getValue("league:${Country.BRAZIL}:$division").participants

    private fun assertInvariants(end: SeasonState) {
        val season = "season ${end.number}"
        val brazilian = end.clubs.values.filter { it.country == Country.BRAZIL }.map { it.key }.sorted()
        val reserve = end.reserves.getValue(Country.BRAZIL)
        assertEquals(brazilian, ((1..4).flatMap { participants(end, it) } + reserve).sorted(), "$season: every club stands in exactly one place")
        for (division in 1..4) {
            participants(end, division).forEach { assertEquals(Standing.InDivision(division), end.club(it).standing, "$season: $it") }
        }
        reserve.forEach { assertEquals(Standing.WithoutDivision, end.club(it).standing, "$season: $it") }

        assertEquals(listOf(8, 8, 8, 8), (1..4).map { participants(end, it).size }, season)
        assertEquals(listOf(SmallBrazil.SAO_PAULO to 16, SmallBrazil.RIO_GRANDE_DO_NORTE to 8).sortedBy { it.first }, end.states.divisions.map { it.state to it.clubs.size })
        assertEquals(mapOf(SmallBrazil.SAO_PAULO to 2, SmallBrazil.RIO_GRANDE_DO_NORTE to 2), end.states.reserve.mapValues { it.value.size }, season)

        val closed = end.closed.map { it.key }
        assertEquals(closed.distinct(), closed, "$season: a competition closed twice")
        assertEquals(end.competitions.keys.sorted(), closed.sorted(), "$season: every competition closes")
    }

    private fun twoSeasons(tally: Tally): List<SeasonState> {
        val start = opening()
        assertTrue(BrazilianFourth.KEY !in start.competitions, "season one's fourth waits for the states")
        val first = playChecked(start, tally)
        val next = turn(first)
        assertTrue(BrazilianFourth.KEY !in next.competitions, "season two's fourth waits for the states")
        return listOf(first, playChecked(next, tally))
    }

    @Test
    fun `two seasons keep every club in one place, every size, every close and every ban`() {
        val tally = Tally()
        val (first, second) = twoSeasons(tally)
        assertInvariants(first)
        assertInvariants(second)
        assertTrue(tally.served >= 1, "no suspension was served in ${tally.suspendedChecks} checks")

        val movements = seasonMovements(first)
        for (departures in movements.departures.filter { it.key.startsWith("league:") }) {
            val division = departures.key.substringAfterLast(':').toInt()
            departures.up.forEach { assertTrue(it in participants(second, division - 1), "$it went up from ${departures.key}") }
        }
        val door = assertNotNull(movements.fourth).door
        assertEquals(2, door.size)
        assertTrue(second.competitions.getValue(BrazilianFourth.KEY).participants.containsAll(door), "the door heads season two's fourth")
    }

    /**
     * The grouped final phases inside a whole season: every tie of the
     * first knockout round of Sao Paulo's preset 7 (quarter finals) and of
     * the fourth's grouped league (semi finals) pairs two clubs of one group,
     * Qualifiers.PerGroup seeded by groupedSeeds, in both seasons.
     */
    @Test
    fun `the first knockout round of every grouped competition pairs clubs of one group`() {
        for (end in twoSeasons(Tally())) {
            for (key in listOf("state:${SmallBrazil.SAO_PAULO}:1", BrazilianFourth.KEY)) {
                val competition = end.competitions.getValue(key)
                val groups = (competition.phases.first() as Phase.League).phase.groups
                val opening = end.played.filter { it.competition == key && it.match.phase == 1 && it.match.round == 0 }
                assertTrue(opening.isNotEmpty(), "$key played no knockout in season ${end.number}")
                for (played in opening) {
                    val home = groups.indexOfFirst { played.match.fixture.home in it }
                    val away = groups.indexOfFirst { played.match.fixture.away in it }
                    assertEquals(home, away, "$key season ${end.number}: ${played.match.fixture}")
                }
            }
        }
    }

    @Test
    fun `season one's fourth is the six state clubs outside the top three divisions and the first two stateless ones`() {
        val first = twoSeasons(Tally()).first()
        assertEquals(
            setOf("sp25", "sp27", "sp29", "sp33", "rn26", "rn30", "br28", "br31"),
            first.competitions.getValue(BrazilianFourth.KEY).participants.toSet(),
        )
    }

    @Test
    fun `the same seed plays the same two seasons, and the fourth built mid season draws what an opening build would`() {
        val once = twoSeasons(Tally())
        val twice = twoSeasons(Tally())
        assertEquals(once, twice)

        var door = emptyList<String>()
        for (end in once) {
            val fourth = assertNotNull(end.fourth)
            assertEquals(door, fourth.door)
            val inStateDivision = end.states.divisions.flatMap { it.clubs }.toSet()
            val members = rebuiltFourth(
                door = fourth.door,
                stateQueue = stateChampionsQueue(end.closed, { key -> end.club(key).club.entry.state }, size = Int.MAX_VALUE),
                stateless = SmallBrazil.dataset.clubs.filter { it.country == Country.BRAZIL && it.ref !in inStateDivision }.map { it.ref },
                excluded = end.clubs.values.filter { (it.standing as? Standing.InDivision)?.division in 1..3 }.map { it.key }.toSet(),
                size = fourth.size,
            )
            val expected = leagueCompetition(
                LeagueDivision(Country.BRAZIL, BrazilianFourth.DIVISION, BrazilianFourth.configuration(SmallBrazil.dataset), members),
                seasonFixturesRoot(seed, end.number).fork(clubKey(BrazilianFourth.KEY)),
            )
            assertEquals(expected.phases.first(), end.competitions.getValue(BrazilianFourth.KEY).phases.first(), "season ${end.number}")
            door = seasonMovements(end).fourth?.door.orEmpty()
        }
    }
}
