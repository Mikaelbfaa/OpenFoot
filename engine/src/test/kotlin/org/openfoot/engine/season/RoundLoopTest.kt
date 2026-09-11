package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.match.Lineups
import org.openfoot.engine.match.MatchEvent
import org.openfoot.engine.world.ScriptedInts
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.Rng
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide
import org.openfoot.model.Trait
import org.openfoot.model.rand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RoundLoopTest {

    private fun squad(club: String) = buildList {
        add(WorldFixtures.player(name = "$club g1", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        add(WorldFixtures.player(name = "$club g2", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        repeat(3) { add(WorldFixtures.player(name = "$club z$it", position = Position.CENTREBACK, first = Trait.MARKING, second = Trait.TACKLING)) }
        repeat(3) { add(WorldFixtures.player(name = "$club l$it", position = Position.FULLBACK, first = Trait.PACE, second = Trait.CROSSING)) }
        repeat(6) { add(WorldFixtures.player(name = "$club m$it", position = Position.MIDFIELDER, first = Trait.PASSING, second = Trait.PLAYMAKING)) }
        repeat(4) { add(WorldFixtures.player(name = "$club a$it", position = Position.FORWARD, first = Trait.FINISHING, second = Trait.HEADING)) }
    }

    private val data = WorldFixtures.dataset(
        clubs = (1..12).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it), squad = squad("c$it")) },
    ).copy(leagues = listOf(LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true)))

    private fun opening(seed: Long) = openingSeason(generateWorld(data, seed, setOf(Country.BRAZIL)), data, setOf(Country.BRAZIL), 2026, seed)

    @Test
    fun `the opening season holds one division of ten, a cup of eight and a schedule for both`() {
        val state = opening(1)
        assertEquals(setOf("league:29:1", "cup:29"), state.competitions.keys)
        assertEquals(10, state.competitions.getValue("league:29:1").participants.size)
        assertEquals(8, state.competitions.getValue("cup:29").participants.size)
        assertEquals(12, state.clubs.size)
        assertEquals(CalendarDate.seasonStart(2026).plusDays(7 * 12), state.today)
    }

    @Test
    fun `a round plays every scheduled match, records results and moves the date`() {
        val before = opening(1)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        assertEquals(5, after.played.size)
        assertTrue(after.played.all { it.date == before.today && it.competition == "league:29:1" })
        assertEquals(1, after.dateIndex)
        val league = after.competitions.getValue("league:29:1")
        assertEquals(5, league.results[0].size)
        assertEquals(after.played.map { it.result }, league.results[0])
    }

    @Test
    fun `a match leaves its marks on the records of both clubs`() {
        val before = opening(2)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        val match = after.played.first()
        val home = after.club(match.result.home)
        assertTrue(home.records.count { it.appearances == 1 } >= 11, "the starters appeared")
        assertTrue(home.records.count { it.namedSinceTick } >= home.records.count { it.appearances == 1 }, "the bench was named")
        assertTrue(home.records.filter { it.appearances == 1 }.all { it.ratingCount == 1 })
        val away = after.club(match.result.away)
        assertTrue(home.records.sumOf { it.goals } <= match.result.homeGoals, "goals credit the scorers, own goals aside")
    }

    @Test
    fun `the post round recovers everyone of a competition that played, played or not`() {
        val before = opening(3)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        val rested = after.clubs.values.filter { it.standing is org.openfoot.engine.world.Standing.WithoutDivision }
        assertTrue(rested.all { club -> club.records.all { it.energy == 100 } }, "clubs outside the round are untouched")
        val played = after.club(after.played.first().result.home)
        assertTrue(played.records.all { it.energy in 1..100 })
    }

    /**
     * The round right after the first plays the cup's midweek fixture, three
     * days after the league's opening Sunday, and crosses no new Sunday on
     * its own; the round after that one lands back on a Sunday, the second
     * league round, which is where the next pending Sunday actually fires.
     */
    @Test
    fun `pending Sundays reach the tick in order before the day's matches`() {
        val before = opening(4)
        val seen = ArrayList<CalendarDate>()
        val tick = WeeklyTick { state, sunday -> seen += sunday; state }
        val after = playRound(before, RuleSets.CLASSIC, tick)
        assertEquals(13, seen.size, "the first league Sunday is week twelve, and every Sunday from the start fires once")
        assertTrue(seen.zipWithNext().all { (a, b) -> a.daysUntil(b) == 7 })
        assertEquals(before.today, seen.last())
        assertEquals(seen.last(), after.lastTick)
        val afterCup = playRound(after, RuleSets.CLASSIC, WeeklyTick { state, sunday -> seen += sunday; state })
        assertEquals(13, seen.size, "the cup's midweek date is not itself a Sunday and crosses none")
        val next = playRound(afterCup, RuleSets.CLASSIC, WeeklyTick { state, sunday -> seen += sunday; state })
        assertEquals(14, seen.size)
        assertEquals(next.today?.plusDays(-7), seen.last())
    }

    @Test
    fun `a whole season finishes, closes every competition and crediting prestige to the champions`() {
        val end = playSeason(opening(5), RuleSets.CLASSIC, WeeklyTick.NONE)
        assertTrue(end.finished)
        assertEquals(setOf("league:29:1", "cup:29"), end.closed.map { it.key }.toSet())
        assertTrue(end.competitions.values.all { it.finished })
        val leagueClose = end.closed.single { it.kind == CompetitionKind.NATIONAL_LEAGUE }
        assertEquals(10, leagueClose.finalOrder.size)
        val cupClose = end.closed.single { it.kind == CompetitionKind.NATIONAL_CUP }
        fun cupPrize(key: String): Long = when (key) {
            cupClose.finalOrder[0] -> 300L
            cupClose.finalOrder[1] -> 50L
            else -> 0L
        }
        val champion = leagueClose.finalOrder[0]
        val runnerUp = leagueClose.finalOrder[1]
        assertEquals(500 + cupPrize(champion), end.club(champion).prestige.balance)
        assertEquals(90 + cupPrize(runnerUp), end.club(runnerUp).prestige.balance)
        assertEquals(45 + 14, end.played.size, "forty five league matches and a cup of eight over two legs")
    }

    /**
     * Section 0 fires the weekly tick on every Sunday of the calendar year,
     * matches or not, and says outright that no Sunday is skipped. This
     * fixture's league finishes in the spring, so every Sunday from then to
     * the end of December has no round left to carry it; playSeason still
     * fires each of them, once, in order, before it returns. The expected
     * list is counted with CalendarDate alone, from the season's first
     * Sunday to the thirty first of December of its year.
     */
    @Test
    fun `a whole season fires every Sunday of its year exactly once`() {
        val seen = ArrayList<CalendarDate>()
        val end = playSeason(opening(5), RuleSets.CLASSIC, WeeklyTick { state, sunday -> seen += sunday; state })
        val expected = generateSequence(CalendarDate.seasonStart(2026)) { it.plusDays(7) }
            .takeWhile { it <= CalendarDate(2026, 12, 31) }
            .toList()
        assertEquals(expected, seen)
        assertEquals(expected.last(), end.lastTick)
    }

    /**
     * A club can play only one match of a competition's round. A round that
     * names one club twice is refused before any of it is played, and the
     * message names both the club and the competition, so a broken format
     * is caught where it is scheduled rather than as a silently doubled set
     * of records.
     */
    @Test
    fun `a club scheduled twice in one competition's round is refused`() {
        val state = opening(1)
        val league = state.competitions.getValue(LEAGUE)
        val first = (league.phases[0] as Phase.League).phase
        val (a, b, c) = first.participants
        val doubled = Round(listOf(Fixture(a, b), Fixture(c, a)))
        val broken = league.copy(
            phases = league.phases.mapIndexed { i, phase ->
                if (i == 0) Phase.League(first.copy(rounds = listOf(doubled) + first.rounds.drop(1))) else phase
            },
        )
        val error = assertFailsWith<IllegalArgumentException> {
            playRound(state.withCompetition(broken), RuleSets.CLASSIC, WeeklyTick.NONE)
        }
        val message = error.message.orEmpty()
        assertTrue(a in message && LEAGUE in message, message)
    }

    @Test
    fun `the same seed plays the same season`() {
        val once = playSeason(opening(6), RuleSets.CLASSIC, WeeklyTick.NONE)
        val twice = playSeason(opening(6), RuleSets.CLASSIC, WeeklyTick.NONE)
        assertEquals(once.played, twice.played)
        assertEquals(once.clubs.mapValues { it.value.records }, twice.clubs.mapValues { it.value.records })
    }

    /**
     * The sequence the round loop runs for one club at the end of one of its
     * matches in a competition: the discipline step of that match, then the
     * post round. The man at squad index five is the one the tests book.
     */
    private fun ClubState.plays(competition: String, log: List<MatchEvent> = emptyList(), rng: Rng = ScriptedInts()): ClubState =
        disciplineAfterMatch(competition, log, TeamSide.HOME, rng).postRound(appeared = emptySet())

    private fun ClubState.fields(competition: String): Boolean =
        availability(CalendarDate(2026, 3, 1), competition).of(MAN, squad[MAN]).canPlay

    private val man = Lineups.player(slot = 5, strength = 50, id = MAN)

    /**
     * Section 3.1 serves suspensions before it applies the round's cards, and
     * section 3.8 keeps the record per competition. A third yellow earned in
     * league match k therefore costs league match k plus one and nothing
     * else: the man is back for k plus two, and a cup match between the two
     * neither stops him nor serves his league suspension.
     */
    @Test
    fun `a third league yellow costs the next league match and never a cup match`() {
        var club = opening(8).club("c01").withRecord(MAN) { it.withDiscipline(LEAGUE, DisciplineRecord(yellows = 2)) }
        club = club.plays(LEAGUE, listOf(MatchEvent.Booking(10, TeamSide.HOME, man)))
        assertFalse(club.fields(LEAGUE), "the third yellow keeps him out of league match k+1")
        assertTrue(club.fields(CUP), "a league suspension never keeps him out of the cup")
        club = club.plays(CUP)
        assertFalse(club.fields(LEAGUE), "the cup match in between serves nothing in the league")
        club = club.plays(LEAGUE)
        assertTrue(club.fields(LEAGUE), "league match k+1 served it, so he is back for k+2")
    }

    /**
     * A direct red drawn at 750 of a thousand is a two match ban, per the
     * ladder of section 3.8. Only league matches serve it, one each; the cup
     * matches played between them leave it where it was.
     */
    @Test
    fun `a two match league ban is served only by league matches`() {
        var club = opening(8).club("c01")
        club = club.plays(LEAGUE, listOf(MatchEvent.SendingOff(30, TeamSide.HOME, man, secondYellow = false)), ScriptedInts(750))
        assertEquals(DisciplineRecord(ban = 2), club.records[MAN].disciplineIn(LEAGUE))
        club = club.plays(CUP)
        assertEquals(DisciplineRecord(ban = 2), club.records[MAN].disciplineIn(LEAGUE), "a cup match does not reduce it")
        club = club.plays(LEAGUE)
        assertEquals(DisciplineRecord(ban = 1), club.records[MAN].disciplineIn(LEAGUE))
        assertFalse(club.fields(LEAGUE))
        club = club.plays(CUP)
        club = club.plays(LEAGUE)
        assertEquals(DisciplineRecord.CLEAN, club.records[MAN].disciplineIn(LEAGUE))
        assertTrue(club.fields(LEAGUE))
    }

    @Test
    fun `yellows in two competitions never add up to a suspension`() {
        var club = opening(8).club("c01")
        val booked = listOf(MatchEvent.Booking(10, TeamSide.HOME, man))
        club = club.plays(LEAGUE, booked).plays(LEAGUE, booked).plays(CUP, booked)
        assertEquals(DisciplineRecord(yellows = 2), club.records[MAN].disciplineIn(LEAGUE))
        assertEquals(DisciplineRecord(yellows = 1), club.records[MAN].disciplineIn(CUP))
        assertTrue(club.fields(LEAGUE) && club.fields(CUP))
    }

    /**
     * Sections 1.10 and 3.8 serve a suspension only by a match the club
     * actually plays in that competition. The season is walked to the first
     * cup date whose round leaves one entrant idle, an eliminated side the
     * cup still lists; that round is then replayed with one suspended man at
     * the idle club and one at a club that plays. The playing club serves,
     * the idle club serves nothing, and the idle club still recovers energy,
     * which is item 117.
     */
    @Test
    fun `a club with no match in a competition that plays today serves nothing in it`() {
        var state = opening(9)
        var idle: String? = null
        var playing: String? = null
        while (idle == null) {
            val date = requireNotNull(state.today)
            val trial = playRound(state, RuleSets.CLASSIC, WeeklyTick.NONE)
            val cup = trial.played.filter { it.date == date && it.competition == CUP }
            val sides = cup.flatMap { listOf(it.result.home, it.result.away) }.toSet()
            idle = state.competitions.getValue(CUP).participants.firstOrNull { cup.isNotEmpty() && it !in sides }
            playing = sides.firstOrNull()
            if (idle == null) state = trial
        }
        val suspended = DisciplineRecord(ban = 1)
        val seeded = state
            .withClub(state.club(idle).withRecord(MAN) { it.withDiscipline(CUP, suspended).copy(energy = 40) })
            .withClub(state.club(requireNotNull(playing)).withRecord(MAN) { it.withDiscipline(CUP, suspended) })
        val after = playRound(seeded, RuleSets.CLASSIC, WeeklyTick.NONE)
        assertEquals(suspended, after.club(idle).records[MAN].disciplineIn(CUP), "no cup match today, nothing served")
        assertTrue(after.club(idle).records[MAN].energy > 40, "the idle club still recovers")
        assertEquals(DisciplineRecord.CLEAN, after.club(playing).records[MAN].disciplineIn(CUP), "the club that played served")
    }

    /**
     * Fix for the review finding on this task's first round: afterMatch is
     * called once for TeamSide.HOME and once for TeamSide.AWAY of the same
     * match, both times handed the very same matchRng, so forking only the
     * season discipline tag off it would hand both calls one identical child
     * stream and give a home direct red and an away direct red of the same
     * match the same drawn ban length. disciplineRng forks the side's own
     * ordinal in as well, so the two draw from different streams; checked
     * over a handful of seeds so the assertion does not rest on one seed's
     * coincidence, and checked that each side's own stream still replays
     * identically from the same match seed, which is what makes the season
     * reproducible in the first place.
     */
    @Test
    fun `each side of a match draws its own discipline stream`() {
        for (seed in 1L..5L) {
            val matchRng = SplitMix64Rng(seed)
            val home = disciplineRng(matchRng, TeamSide.HOME).rand(1000)
            val away = disciplineRng(matchRng, TeamSide.AWAY).rand(1000)
            assertNotEquals(home, away, "seed $seed: the two sides drew the same first discipline value")

            val homeAgain = disciplineRng(SplitMix64Rng(seed), TeamSide.HOME).rand(1000)
            val awayAgain = disciplineRng(SplitMix64Rng(seed), TeamSide.AWAY).rand(1000)
            assertEquals(home, homeAgain, "seed $seed: the home stream did not replay from the same match seed")
            assertEquals(away, awayAgain, "seed $seed: the away stream did not replay from the same match seed")
        }
    }

    private companion object {
        const val LEAGUE = "league:29:1"
        const val CUP = "cup:29"
        const val MAN = 5
    }
}
