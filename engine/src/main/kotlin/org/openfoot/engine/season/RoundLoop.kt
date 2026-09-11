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
 * availability of section 5.4, plays it and rates it with the very same
 * stream the match was played from, so a match and its ratings replay
 * together from one seed.
 *
 * 5. Every match writes itself onto both clubs' records before the next match
 * of the round is even assembled, so a club playing twice in one round, were
 * that ever legal, would carry the first match's marks into the second. See
 * ClubState.afterMatch below for what each field reads and from where.
 *
 * 6. The competition records the round's results and the match joins the
 * season's played history.
 *
 * 7. Once a competition's round is fully recorded, section 3.1's post round
 * runs for every one of its participants, home and away sides together with
 * every club the competition still lists that did not play today at all,
 * such as a cup entrant a previous round already eliminated; see postRound
 * below for the suspension and recovery rule and its own INFERIDO point. No
 * club's post round runs twice in one day; a club appearing in two
 * competitions scheduled the same day cannot happen under Task 7's own
 * invariant, and this is required rather than assumed.
 *
 * 8. A competition that has just finished, past every one of its phases,
 * closes now: its final order is drawn, the close is recorded, and section
 * 5.5's prestige is credited to its champion and its runner-up the moment it
 * closes rather than waiting for any later turnover.
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
        val recorded = ArrayList<Pair<ScheduledMatch, Result>>()
        val appeared = mutableMapOf<String, Set<Int>>()

        for (match in matches) {
            val home = match.fixture.home
            val away = match.fixture.away
            val matchRng = root.fork(SeedDomain.MATCH).fork(date.ordinal.toLong()).fork(clubKey(home)).fork(clubKey(away))
            val assembled = assembleMatch(
                home = current.club(home),
                away = current.club(away),
                dataset = current.dataset,
                kind = competition.kind,
                season = current.number,
                rules = rules,
                availabilityOf = { competitor -> (competitor as ClubState).availability(date) },
                rng = matchRng,
            )
            val report = simulateMatch(assembled.setup, matchRng, assembled.homeBench, assembled.awayBench)
            val ratings = report.playerRatings(rules, matchRng)
            val result = Result(home, away, report.homeGoals, report.awayGoals)

            current = current
                .withClub(current.club(home).afterMatch(report, ratings, TeamSide.HOME, date, matchRng))
                .withClub(current.club(away).afterMatch(report, ratings, TeamSide.AWAY, date, matchRng))
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
            current = current.close(advanced, date, rules, fixturesRng)
        }
    }
    return current.copy(dateIndex = current.dateIndex + 1)
}

/**
 * Plays every remaining round of a season, in order, until it is finished.
 */
@SpecRef("3.1")
fun playSeason(state: SeasonState, rules: RuleSet, tick: WeeklyTick): SeasonState {
    var current = state
    while (!current.finished) current = playRound(current, rules, tick)
    return current
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
 * Discipline (3.8): the side's own log entries, bookings and sendings off,
 * are applied to a map built fresh from the records' own discipline fields
 * and read back onto them; SEASON_DISCIPLINE_STREAM is this function's own
 * fork of the match's rng, separate from every stream the match itself drew,
 * so which round writes the discipline back never moves any draw the match
 * made while it was being played.
 *
 * Injuries (3.8): every MatchEvent.Injury of this side with a positive day
 * count sets the injured player's expiry to the match date plus that many
 * days; a positive permanent strength loss is the one place outside world
 * generation that ever rewrites a squad's Player, replacing his strength with
 * strength minus the loss, since the match itself has no way to touch the
 * squad it was given.
 */
@SpecRef("3.8")
private fun ClubState.afterMatch(report: MatchReport, ratings: MatchRatings, side: TeamSide, date: CalendarDate, rng: Rng): ClubState {
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
    val discipline = updated.records.mapIndexed { i, record -> PlayerId(i) to record.discipline }.toMap()
        .afterMatch(report.log, side, rng.fork(SEASON_DISCIPLINE_STREAM))
    for ((id, record) in discipline) {
        updated = updated.withRecord(id.value) { it.copy(discipline = record) }
    }
    return updated
}

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
 * Suspension serving (1.10, 3.8) reads a suspended discipline record's
 * served() only when appeared is not null, which is the controller ruling
 * this task implements: section 1.10 counts a served round as one the club
 * actually played in that competition, so a club sitting out today, eliminated
 * or merely without a fixture, does not serve one, and a man suspended before
 * elimination stays suspended in the season's own bookkeeping for as long as
 * the competition keeps asking about his club, which is a fact about the
 * competition's own history and not a bug in this function.
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
            val discipline = if (appeared != null && record.discipline.suspended) record.discipline.served() else record.discipline
            val gain = weeklyRecovery(squad[index].age, played = played, humanManaged = false)
            record.copy(discipline = discipline, energy = recover(record.energy, gain))
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
