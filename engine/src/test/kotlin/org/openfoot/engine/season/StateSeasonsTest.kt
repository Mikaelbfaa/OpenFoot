package org.openfoot.engine.season

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateSeasonsTest {

    private fun squad(club: String) = buildList {
        add(WorldFixtures.player(name = "$club g1", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        add(WorldFixtures.player(name = "$club g2", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        repeat(3) { add(WorldFixtures.player(name = "$club z$it", position = Position.CENTREBACK, first = Trait.MARKING, second = Trait.TACKLING)) }
        repeat(3) { add(WorldFixtures.player(name = "$club l$it", position = Position.FULLBACK, first = Trait.PACE, second = Trait.CROSSING)) }
        repeat(6) { add(WorldFixtures.player(name = "$club m$it", position = Position.MIDFIELDER, first = Trait.PASSING, second = Trait.PLAYMAKING)) }
        repeat(4) { add(WorldFixtures.player(name = "$club a$it", position = Position.FORWARD, first = Trait.FINISHING, second = Trait.HEADING)) }
    }

    private fun club(ref: String, level: Int, state: Int?): ClubEntry =
        WorldFixtures.club(ref = ref, level = level, squad = squad(ref)).copy(state = state)

    private fun leagues(fourth: LeagueConfigEntry) = (1..3).map {
        LeagueConfigEntry(country = Country.BRAZIL, division = it, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true)
    } + fourth

    private val flatFourth = LeagueConfigEntry(country = Country.BRAZIL, division = 4, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true)

    /**
     * Fifty Brazilian clubs over four divisions of ten and a reserve of ten.
     * The thirty strongest, levels twenty down to eleven, alternate between
     * Sao Paulo (25) and Rio (18), fifteen each: two state divisions of six
     * and a state reserve of three, every one of them in national divisions
     * one to three. The twenty weakest, levels ten down to six, alternate
     * between Minas (10) and Rio Grande do Sul (22), ten each: one state
     * division of six and a state reserve of four.
     */
    private fun fourStates(): WorldDataset = WorldFixtures.dataset(
        clubs = (1..50).map { index ->
            val ref = "k${index.toString().padStart(2, '0')}"
            if (index <= 30) {
                club(ref, level = 20 - (index - 1) / 3, state = if (index % 2 == 1) 25 else 18)
            } else {
                club(ref, level = 10 - (index - 31) / 4, state = if (index % 2 == 1) 10 else 22)
            }
        },
    ).copy(leagues = leagues(flatFourth))

    private fun opening(dataset: WorldDataset, seed: Long) =
        openingSeason(generateWorld(dataset, seed, setOf(Country.BRAZIL)), dataset, setOf(Country.BRAZIL), 2026, seed)

    private fun play(state: SeasonState) = playSeason(state, RuleSets.CLASSIC, WeeklyTick.NONE)

    private fun turn(state: SeasonState) = nextSeason(state, setOf(Country.BRAZIL), RuleSets.CLASSIC)

    private fun SeasonState.order(key: String) = closed.single { it.key == key }.finalOrder

    private fun SeasonState.firstPhaseTable(key: String): List<String> {
        val competition = competitions.getValue(key)
        return (competition.phases.first() as Phase.League).phase.overallTable(competition.results.first()).map { it.key }
    }

    private fun SeasonState.division(state: Int, division: Int) = states.divisions.single { it.state == state && it.division == division }.clubs

    private fun SeasonState.standingOf(key: String) = club(key).standing

    private fun SeasonState.brazilianWithoutDivision() =
        clubs.values.filter { it.country == Country.BRAZIL && it.standing == Standing.WithoutDivision }.map { it.key }.toSet()

    private fun assertQueueHoldsTheClubsWithoutDivision(state: SeasonState) {
        val queue = state.reserves.getValue(Country.BRAZIL)
        assertEquals(queue.size, queue.toSet().size, "the queue lists a club twice")
        assertEquals(state.brazilianWithoutDivision(), queue.toSet())
    }

    @Test
    fun `season two's state memberships are season one's after the swap, kept clubs first and arrivals after`() {
        val end = play(opening(fourStates(), 11))
        val next = turn(end)
        for (state in listOf(25, 18)) {
            val first = end.division(state, 1)
            val second = end.division(state, 2)
            val reserve = end.states.reserve.getValue(state)
            val down = end.firstPhaseTable("state:$state:1").takeLast(2)
            val up = end.order("state:$state:2").take(2)
            val out = end.firstPhaseTable("state:$state:2").filter { it !in up }.takeLast(2)
            val inFromReserve = reserve.take(2)

            assertEquals(first.filter { it !in down } + up, next.division(state, 1))
            assertEquals(second.filter { it !in up && it !in out } + down + inFromReserve, next.division(state, 2))
            assertEquals(reserve.drop(2) + out, next.states.reserve.getValue(state))
            assertEquals(next.division(state, 2).toSet(), next.competitions.getValue("state:$state:2").participants.toSet())
        }
        for (state in listOf(10, 22)) {
            val only = end.division(state, 1)
            val reserve = end.states.reserve.getValue(state)
            val out = end.firstPhaseTable("state:$state:1").takeLast(2)
            assertEquals(only.filter { it !in out } + reserve.take(2), next.division(state, 1))
            assertEquals(reserve.drop(2) + out, next.states.reserve.getValue(state))
        }
    }

    @Test
    fun `every season, every club of a championship state sits in exactly one state division or in its reserve`() {
        val data = fourStates()
        var state = opening(data, 12)
        repeat(3) {
            for (code in listOf(25, 18, 10, 22)) {
                val members = data.clubs.filter { it.state == code }.map { it.ref }.sorted()
                val seated = state.states.divisions.filter { it.state == code }.flatMap { it.clubs } + state.states.reserve[code].orEmpty()
                assertEquals(members, seated.sorted(), "state $code in season ${state.number}")
            }
            state = turn(play(state))
        }
    }

    @Test
    fun `season one's fourth division is built from season one's state results and holds no club of divisions one to three`() {
        val start = opening(fourStates(), 13)
        assertTrue(start.clubs.values.none { it.standing == Standing.InDivision(4) }, "the level seated fourth is taken out")
        assertTrue(BrazilianFourth.KEY !in start.competitions)
        assertEquals(10, start.fourth?.size)
        assertEquals(9, start.schedule.slots.count { it.competition == BrazilianFourth.KEY }, "the configured shape's dates are reserved")
        assertQueueHoldsTheClubsWithoutDivision(start)

        val end = play(start)
        val minas = end.order("state:10:1")
        val rioGrande = end.order("state:22:1")
        val expected = (0 until 5).flatMap { listOf(minas[it], rioGrande[it]) }
        val fourth = end.competitions.getValue(BrazilianFourth.KEY)
        assertEquals(expected.toSet(), fourth.participants.toSet())
        expected.forEach { assertEquals(Standing.InDivision(4), end.standingOf(it)) }
        val divisionsOneToThree = start.clubs.values.filter { (it.standing as? Standing.InDivision)?.division in 1..3 }.map { it.key }.toSet()
        assertTrue(fourth.participants.none { it in divisionsOneToThree })
        val lastStateClose = end.closed.filter { it.kind == CompetitionKind.STATE }.maxOf { it.date }
        assertTrue(end.played.filter { it.competition == BrazilianFourth.KEY }.all { it.date > lastStateClose })
        assertQueueHoldsTheClubsWithoutDivision(end)
    }

    @Test
    fun `the third's relegated wait at the door and head the next fourth, whose best go up`() {
        val end = play(opening(fourStates(), 14))
        val next = turn(end)
        val door = end.order("league:29:3").takeLast(2)
        val promoted = end.order(BrazilianFourth.KEY).take(2)
        assertEquals(door, next.fourth?.door)
        door.forEach { assertEquals(Standing.WithoutDivision, next.standingOf(it)) }
        promoted.forEach { assertEquals(Standing.InDivision(3), next.standingOf(it)) }
        assertTrue(next.clubs.values.none { it.standing == Standing.InDivision(4) })
        assertQueueHoldsTheClubsWithoutDivision(next)

        val second = play(next)
        door.forEach { assertEquals(Standing.InDivision(4), second.standingOf(it)) }
        assertTrue(second.competitions.getValue(BrazilianFourth.KEY).participants.containsAll(door))
        assertQueueHoldsTheClubsWithoutDivision(second)
    }

    /**
     * Forty six Brazilian clubs: thirty strong clubs of no state in divisions
     * one to three, then sixteen weaker clubs listed weakest first, six of
     * them Minas (10) at every third place, the rest of no state. The state
     * queue can give six names only, and the fourth takes four more from the
     * stateless clubs in the dataset's order, which is not their level order.
     */
    private fun oneSmallState(): WorldDataset = WorldFixtures.dataset(
        clubs = (1..30).map { club("t${it.toString().padStart(2, '0')}", level = 20 - (it - 1) / 3, state = null) } +
            (1..16).map { index ->
                val ref = "u${index.toString().padStart(2, '0')}"
                club(ref, level = 6 + index / 4, state = if (index % 3 == 0 || index == 16) 10 else null)
            },
    ).copy(leagues = leagues(flatFourth))

    @Test
    fun `a short state queue is padded with stateless Brazilian clubs in world order`() {
        val data = oneSmallState()
        val start = opening(data, 15)
        val waiting = start.reserves.getValue(Country.BRAZIL)
        val end = play(start)
        val minas = end.order("state:10:1")
        assertEquals(6, minas.size)
        val stateless = data.clubs.map { it.ref }.filter { it.startsWith("u") && it !in minas }
        val fourth = end.competitions.getValue(BrazilianFourth.KEY).participants
        assertEquals((minas + stateless.take(4)).toSet(), fourth.toSet())

        val padded = stateless.take(4)
        assertTrue(padded.all { it in waiting }, "the padding clubs were waiting in Brazil's reserve queue")
        assertTrue(padded.none { it in end.reserves.getValue(Country.BRAZIL) }, "a club seated in the fourth leaves the queue")
        assertQueueHoldsTheClubsWithoutDivision(end)
        assertQueueHoldsTheClubsWithoutDivision(turn(end))
    }

    /**
     * Forty two Brazilian clubs: thirty of no state in divisions one to three
     * and twelve Minas clubs below them, which fill two state divisions of
     * six. Only the first state division feeds the fourth's queue, so the
     * fourth, configured for eight, gathers six.
     */
    private fun shortFourth(fourth: LeagueConfigEntry): WorldDataset = WorldFixtures.dataset(
        clubs = (1..30).map { club("t${it.toString().padStart(2, '0')}", level = 20 - (it - 1) / 3, state = null) } +
            (1..12).map { club("m${it.toString().padStart(2, '0')}", level = 10 - it / 3, state = 10) },
    ).copy(leagues = leagues(fourth))

    @Test
    fun `a short flat fourth plays on the first of its reserved dates and hands the rest back`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 4, teamCount = 8, relegated = 2, turns = 1, penaltiesTiebreak = true)
        val start = opening(shortFourth(config), 16)
        assertEquals(7, start.schedule.slots.count { it.competition == BrazilianFourth.KEY })
        val end = play(start)
        val fourth = end.competitions.getValue(BrazilianFourth.KEY)
        assertEquals(end.order("state:10:1").toSet(), fourth.participants.toSet())
        assertEquals(5, end.schedule.slots.count { it.competition == BrazilianFourth.KEY })
        assertTrue(fourth.finished)
    }

    @Test
    fun `a short fourth that cannot deal into its configured groups is refused by name`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 4, teamCount = 8, relegated = 2, turns = 1, penaltiesTiebreak = true, groups = 2)
        val refused = assertFailsWith<IllegalArgumentException> { play(opening(shortFourth(config), 17)) }
        assertTrue(refused.message!!.contains(BrazilianFourth.KEY), refused.message)
    }

    @Test
    fun `with the state championships off, the fourth is seated by level and swaps with the reserve as any last division`() {
        val data = fourStates().let { it.copy(options = it.options.copy(playStateChampionships = false)) }
        val start = opening(data, 18)
        assertNull(start.fourth)
        assertNotNull(start.competitions[BrazilianFourth.KEY])
        assertEquals(10, start.clubs.values.count { it.standing == Standing.InDivision(4) })
        val end = play(start)
        val next = turn(end)
        val swap = divisionSwaps(end, Country.BRAZIL).last()
        assertEquals(4, swap.upper)
        swap.promoted.forEach { assertEquals(Standing.InDivision(4), next.standingOf(it)) }
        swap.relegated.forEach { assertEquals(Standing.WithoutDivision, next.standingOf(it)) }
    }
}
