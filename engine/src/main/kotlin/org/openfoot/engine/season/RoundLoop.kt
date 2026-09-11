package org.openfoot.engine.season

import org.openfoot.engine.lineup.assembleMatch
import org.openfoot.engine.match.MatchEvent
import org.openfoot.engine.match.MatchRatings
import org.openfoot.engine.match.MatchReport
import org.openfoot.engine.match.playerRatings
import org.openfoot.engine.match.simulateMatch
import org.openfoot.engine.world.clubKey
import org.openfoot.model.GoalType
import org.openfoot.model.PlayerId
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide

/**
 * Plays the season's next scheduled date and returns the season advanced past
 * it, per section 3.1.
 *
 * The nine steps below are the whole of what one round does, in the order it
 * does them, and every one names the section it answers to.
 *
 * 1. Refuses a finished season outright, and reads the date the cursor names.
 *
 * 2. Section 0's pending Sundays fire first, oldest to newest, into the given
 * tick, one call per Sunday strictly after the last one that fired and up to
 * and including today; lastTick then remembers the last Sunday actually
 * fired, today itself when today is a Sunday and the one before it otherwise.
 * Whether the pending Sundays fire before or after the day's matches is not
 * something section 0 states outright; before is the reading this plan takes,
 * and it is INFERIDO, recorded again where firePendingSundays is defined.
 *
 * 3. One season rng root is forked once per round call, from the season's own
 * seed and number, never carried on the state itself; see SeasonState's own
 * docstring on why a live generator cannot live on a value.
 *
 * 4. Every competition scheduled today, in the schedule's own order, is asked
 * for its matches; each match forks its own stream from the root by the date
 * and the two clubs, assembles both sides with the season's own per club
 * availability of section 5.4 for that competition, each club read by its
 * key from the season as it stands, plays it and rates it with the very same
 * stream the match was played from, so a match and its ratings replay
 * together from one seed.
 *
 * 5. Every match writes itself onto both clubs' records before the next match
 * of the round is even assembled. That includes discipline in section 3.1's
 * post round order: the competition's suspensions are served first, then the
 * match's cards are applied, so a ban earned today is served by the club's
 * next match in the same competition and not by today's. A club may play at
 * most one match of one competition's round, and a round that names a club
 * twice is refused before any of it is played. See ClubState.afterMatch
 * below for what each field reads and from where.
 *
 * 6. The competition records the round's results and the match joins the
 * season's played history.
 *
 * 7. Once a competition's round is fully recorded, section 3.1's post round
 * runs for every one of its participants, home and away sides together with
 * every club the competition still lists that did not play today at all,
 * such as a cup entrant a previous round already eliminated; see postRound
 * below for the recovery rule and its own INFERIDO point. Suspensions are
 * not served here but by the match itself, in step 5, so a club with no
 * match in the competition today serves nothing in it. No club's post round
 * runs twice in one day; a club appearing in two competitions scheduled the
 * same day cannot happen under the schedule's own construction, and this is
 * required rather than assumed.
 *
 * 8. A competition that has just finished, past every one of its phases,
 * closes now: its final order is drawn, the close is recorded, and section
 * 5.5's prestige is credited to its champion and its runner-up the moment it
 * closes rather than waiting for any later turnover. When the close was the
 * season's last state competition, sections 1.9 and 1.12 build the Brazilian
 * fourth division fed by the states now, from this season's state results,
 * through withBrazilianFourthIfDue.
 *
 * 9. The cursor advances one date, whether or not anything was scheduled on
 * the one just played; a date with nothing scheduled on it simply advances
 * with no other effect.
 */
@SpecRef("3.1")
fun playRound(state: SeasonState, rules: RuleSet, tick: WeeklyTick): SeasonState {
    require(!state.finished) { "season ${state.number} has finished" }
    val date = requireNotNull(state.today) { "season ${state.number} has finished" }
    var current = firePendingSundays(state, date, tick)
    val root = SplitMix64Rng(current.seed).fork(SeedDomain.SEASON).fork(current.number.toLong())

    val postRoundDone = mutableSetOf<String>()
    for (key in current.schedule.on(date)) {
        val competition = current.competitions.getValue(key)
        val fixturesRng = root.fork(SeedDomain.FIXTURES).fork(clubKey(key))
        val matches = competition.nextMatches(rules, fixturesRng)
        val scheduled = mutableSetOf<String>()
        for (match in matches) {
            for (side in listOf(match.fixture.home, match.fixture.away)) {
                require(scheduled.add(side)) { "$side is scheduled twice in the round of $key on $date" }
            }
        }
        val recorded = ArrayList<Pair<ScheduledMatch, Result>>()
        val appeared = mutableMapOf<String, Set<Int>>()

        for (match in matches) {
            val home = match.fixture.home
            val away = match.fixture.away
            val matchRng = root.fork(SeedDomain.MATCH).fork(date.ordinal.toLong()).fork(clubKey(home)).fork(clubKey(away))
            val beforeMatch = current
            val assembled = assembleMatch(
                home = current.club(home),
                away = current.club(away),
                dataset = current.dataset,
                kind = competition.kind,
                season = current.number,
                rules = rules,
                availabilityOf = { competitor -> beforeMatch.club(competitor.key).availability(date, key) },
                rng = matchRng,
            )
            val report = simulateMatch(assembled.setup, matchRng, assembled.homeBench, assembled.awayBench)
            val ratings = report.playerRatings(rules, matchRng)
            val result = Result(home, away, report.homeGoals, report.awayGoals)

            current = current
                .withClub(current.club(home).afterMatch(report, ratings, TeamSide.HOME, date, key, matchRng))
                .withClub(current.club(away).afterMatch(report, ratings, TeamSide.AWAY, date, key, matchRng))
            appeared[home] = ratings.home.keys.map { it.value }.toSet()
            appeared[away] = ratings.away.keys.map { it.value }.toSet()

            recorded += match to result
            current = current.copy(played = current.played + PlayedMatch(date, key, match, result))
        }

        val advanced = competition.recorded(recorded)
        for (participant in competition.participants) {
            require(postRoundDone.add(participant)) {
                "$participant plays in more than one competition scheduled on $date"
            }
            current = current.withClub(current.club(participant).postRound(appeared[participant]))
        }
        current = current.withCompetition(advanced)
        if (advanced.finished) {
            current = current.close(advanced, date, rules, fixturesRng).withBrazilianFourthIfDue()
        }
    }
    return current.copy(dateIndex = current.dateIndex + 1)
}

/**
 * Plays every remaining round of a season, in order, until it is finished,
 * and then fires every Sunday still pending through the thirty first of
 * December of the season's year.
 *
 * Section 0 fires the weekly tick on every Sunday of the calendar, matches or
 * not, and says that no Sunday is skipped. playRound only fires the Sundays
 * up to the date it plays, so the Sundays after the season's last scheduled
 * date have no round to carry them; they fire here, oldest first, each once,
 * before the finished season is handed back, and the turnover that follows
 * starts the next year from a season that has seen its whole calendar.
 */
@SpecRef("0")
fun playSeason(state: SeasonState, rules: RuleSet, tick: WeeklyTick): SeasonState {
    var current = state
    while (!current.finished) current = playRound(current, rules, tick)
    return firePendingSundays(current, CalendarDate.seasonEnd(current.year), tick)
}

/**
 * Fires every calendar Sunday of section 0 that has not yet fired, from the
 * one right after lastTick up to and including today, oldest first.
 *
 * A season with no lastTick yet has never fired at all, and its baseline is
 * the schedule's own start date, which CalendarDate.seasonStart already
 * guarantees is itself a Sunday; the very first pending Sunday of a season is
 * therefore that start date, not the one after it. Every later call resumes
 * from lastTick plus seven days, since lastTick is always a Sunday that has
 * already fired.
 */
@SpecRef("0")
private fun firePendingSundays(state: SeasonState, date: CalendarDate, tick: WeeklyTick): SeasonState {
    var current = state
    var sunday = current.lastTick?.plusDays(DAYS_IN_WEEK) ?: current.schedule.start
    while (sunday <= date) {
        current = tick.apply(current, sunday).copy(lastTick = sunday)
        sunday = sunday.plusDays(DAYS_IN_WEEK)
    }
    return current
}

/**
 * Writes one match's marks onto one club's records, per section 3.1's post
 * match bookkeeping.
 *
 * Named (4.5): every player report.energy(side) knows about, lineup and bench
 * alike, is marked named since tick and left at the energy the match reports
 * for him; a match never touches a record outside that map, so an unused
 * reserve who was not even on the bench keeps whatever he already had.
 *
 * Appearances and ratings (4.10): every identity ratings.of(side) rated,
 * starters and every man who came on, gains one appearance and the match's
 * rating is folded into his running sum and count; a man who never left the
 * bench is not a key of that map and gains neither.
 *
 * Goals: every MatchEvent.Goal credited to this side whose type is not an own
 * goal and whose scorer is not null adds one to that scorer's own goals
 * count. An own goal is excluded because section 3.1's season chart, like the
 * report's own docstring on Goal, credits nobody at all for one; the scorer
 * of an own goal still owns the match goal counter section 3.14 reads, but he
 * is on the other side's log of events and is never read from this side's
 * pass over it.
 *
 * Discipline (3.1, 3.8): disciplineAfterMatch below runs last, for the
 * competition this match belongs to, serving that competition's suspensions
 * and then applying this match's cards to it. Its stream is disciplineRng,
 * this function's own fork of the match's rng, separate from every stream
 * the match itself drew, so writing the discipline back never moves any draw
 * the match made while it was being played, and separate per side, so the
 * home call and the away call of this same function never share one stream
 * between them.
 *
 * Injuries (3.8): every MatchEvent.Injury of this side with a positive day
 * count sets the injured player's expiry to the match date plus that many
 * days; a positive permanent strength loss is the one place outside world
 * generation that ever rewrites a squad's Player, replacing his strength with
 * strength minus the loss, since the match itself has no way to touch the
 * squad it was given.
 */
@SpecRef("3.8")
private fun ClubState.afterMatch(
    report: MatchReport,
    ratings: MatchRatings,
    side: TeamSide,
    date: CalendarDate,
    competition: String,
    rng: Rng,
): ClubState {
    var updated = this
    for ((id, energy) in report.energy(side)) {
        updated = updated.withRecord(id.value) { it.copy(namedSinceTick = true, energy = energy) }
    }
    for ((id, rating) in ratings.of(side)) {
        updated = updated.withRecord(id.value) {
            it.copy(appearances = it.appearances + 1, ratingSum = it.ratingSum + rating.value, ratingCount = it.ratingCount + 1)
        }
    }
    for (event in report.log) {
        if (event.side != side) continue
        when (event) {
            is MatchEvent.Goal -> {
                val scorer = event.scorer
                if (event.type != GoalType.OWN_GOAL && scorer != null) {
                    updated = updated.withRecord(scorer.id.value) { it.copy(goals = it.goals + 1) }
                }
            }

            is MatchEvent.Injury -> {
                if (event.days > 0) {
                    updated = updated.withRecord(event.player.id.value) { it.copy(injuredUntil = date.plusDays(event.days)) }
                }
                if (event.permanentStrengthLoss > 0) {
                    val index = event.player.id.value
                    updated = updated.copy(
                        squad = updated.squad.mapIndexed { i, player ->
                            if (i == index) player.copy(strength = player.strength - event.permanentStrengthLoss) else player
                        },
                    )
                }
            }

            else -> Unit
        }
    }
    return updated.disciplineAfterMatch(competition, report.log, side, disciplineRng(rng, side))
}

/**
 * The discipline of one club at the end of one of its matches in the given
 * competition, in the order section 3.1's post round gives: suspensions are
 * served first, then the match's cards are applied.
 *
 * Serving reads DisciplineRecord.served on every record the competition
 * holds, which leaves an unsuspended record as it was and applies section
 * 3.8's yellows-first rule to a suspended one, so a man holding three yellows
 * and a ban clears the yellows this match and starts on the ban the next.
 * The cards then land on the same competition's records through the side's
 * own discipline stream. A ban earned in this match is therefore still whole
 * when the club's next match in the competition is assembled, and that match
 * is the one that serves it, which is the whole point of the order.
 *
 * Every other competition's records are left alone: section 3.8 runs this
 * test at the end of each match of the player's club in that competition,
 * and section 1.10 adds that a competition in which the club has no match
 * serves nothing. Serving draws nothing, so the discipline stream sees the
 * same draws in the same order as the cards alone would give it.
 */
@SpecRef("3.1")
internal fun ClubState.disciplineAfterMatch(competition: String, log: List<MatchEvent>, side: TeamSide, rng: Rng): ClubState {
    val served = records.map { it.withDiscipline(competition, it.disciplineIn(competition).served()) }
    val cards = served.mapIndexed { i, record -> PlayerId(i) to record.disciplineIn(competition) }.toMap().afterMatch(log, side, rng)
    return copy(records = served.mapIndexed { i, record -> cards[PlayerId(i)]?.let { record.withDiscipline(competition, it) } ?: record })
}

/**
 * The stream section 3.8's post match discipline draw reads from, forked by
 * side as well as by the season discipline tag.
 *
 * afterMatch is called once for TeamSide.HOME and once for TeamSide.AWAY of
 * the very same match, both times handed the same matchRng, since that is the
 * one seed source the whole match and its ratings already replay from. Rng.fork
 * depends only on the parent's origin and the tag, never on how much the
 * parent or any other child has already produced, so forking only
 * SEASON_DISCIPLINE_STREAM off that shared matchRng twice would hand both
 * calls the identical child stream: a home side's direct red and an away
 * side's direct red in the same match would then draw the same ban length off
 * the ladder of directRedBan, which is not two independent draws and not what
 * section 3.8 describes. Forking the side's own ordinal in as a second tag
 * keeps the two sides' discipline apart the same way every other per side
 * stream in this codebase already is, while still replaying identically from
 * the match's own seed.
 */
@SpecRef("3.8")
internal fun disciplineRng(matchRng: Rng, side: TeamSide): Rng = matchRng.fork(SEASON_DISCIPLINE_STREAM).fork(side.ordinal.toLong())

/**
 * The post round of section 3.1 for one club of one competition that played
 * today, section 1.10's own bookkeeping between one round and the next.
 *
 * appeared is the set of squad indices ratings actually rated in today's
 * match, or null for a club that had no match today at all: a competition
 * that keeps every original entrant on its participant list forever, the way
 * a knockout's does, still lists a club a previous round eliminated, and that
 * club's post round still runs every round the competition plays after that,
 * with no match of its own to read.
 *
 * Suspensions are not served here. Section 3.1 serves them before it
 * applies the round's cards, and section 3.8 runs that test at the end of
 * each match the club plays in the competition, so both belong to the match
 * itself and run in disciplineAfterMatch. A club with no match today,
 * appeared null, therefore serves nothing in this competition, which is
 * section 1.10's own rule, while it still recovers below.
 *
 * Recovery (3.9) runs for every record regardless: weeklyRecovery reads only
 * the player's age and whether he personally played, never the club's
 * suspension state, so a suspended man recovers exactly as anyone else on his
 * club does. played is true only for an index appeared actually names; a
 * club with no match today, appeared null, therefore recovers every one of
 * its men as not played, which is OPEN-QUESTIONS item 117's own bet and is
 * INFERIDO rather than read from section 3.9's own text, since the spec never
 * says what a bye recovers as.
 */
@SpecRef("3.1")
internal fun ClubState.postRound(appeared: Set<Int>?): ClubState =
    copy(
        records = records.mapIndexed { index, record ->
            val played = appeared != null && index in appeared
            val gain = weeklyRecovery(squad[index].age, played = played, humanManaged = false)
            record.copy(energy = recover(record.energy, gain))
        },
    )

/**
 * Closes a competition that has just finished, per section 5.5: its final
 * order is drawn from the same fixtures stream its own rounds were played
 * from, the close is added to the season's history, and the champion and the
 * runner-up are credited titlePrestige's prize the moment the competition
 * closes rather than at any later season turnover, which is section 5.5's own
 * timing and not a simplification taken here.
 */
@SpecRef("5.5")
private fun SeasonState.close(competition: Competition, date: CalendarDate, rules: RuleSet, rng: Rng): SeasonState {
    val order = competition.finalOrder(rules, rng)
    var current = copy(closed = closed + CompetitionClose(date, competition.key, competition.kind, order))
    order.take(PLACES_CREDITED).forEachIndexed { place, key ->
        val club = current.club(key)
        val continent = requireNotNull(dataset.country(club.country)) {
            "club $key sits in country ${club.country}, which the dataset does not describe"
        }.continent
        val prize = titlePrestige(
            kind = competition.kind,
            champion = place == 0,
            inLeague = club.inLeague,
            continent = continent,
            division = competition.division,
        )
        current = current.withClub(club.copy(prestige = club.prestige.awarded(prize)))
    }
    return current
}

/**
 * A stream of the match's own generator set apart for section 3.8's post
 * match discipline draw, distinct from every stream the match itself already
 * used while it was being played, so applying discipline after the match adds
 * no draw the match's own replay would ever see.
 */
@SpecRef("3.8")
private const val SEASON_DISCIPLINE_STREAM = 0x5EA5L

/** The champion and the runner up, the two places section 5.5 ever credits. */
@SpecRef("5.5")
private const val PLACES_CREDITED = 2

@SpecRef("0")
private const val DAYS_IN_WEEK = 7
