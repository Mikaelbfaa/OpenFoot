package org.openfoot.cli

import org.openfoot.engine.season.CompetitionClose
import org.openfoot.engine.season.Phase
import org.openfoot.engine.season.SeasonMovements
import org.openfoot.engine.season.SeasonState
import org.openfoot.engine.season.TableRow
import org.openfoot.engine.season.seasonMovements

/**
 * Describes a played season in a form that is the same on every run, so two
 * seasons can be compared with a diff.
 *
 * The header names the season, the calendar year it was played over, how
 * many distinct calendar dates the schedule actually used (schedule.dates.size,
 * a fact about the whole season rather than about any one competition) and
 * how many matches were played across every competition (played.size).
 *
 * Competitions are then printed in the order they closed, state.closed's own
 * order, rather than sorted by key or by kind: which competition finishes
 * first is a real fact about the season the schedule produced (the national
 * cup of this version's own calendar policy always closes well before the
 * league it shares a country with, since it plays a handful of fortnightly
 * Wednesdays against thirty-odd weekly Sundays), and reading it off state.closed
 * costs nothing further since every finished competition is already recorded
 * there exactly once, in the order playRound closed it.
 *
 * Each competition prints its key, its kind and its champion and runner-up,
 * the first two names of CompetitionClose.finalOrder, since every format
 * this version builds seats at least two sides. Right under that header
 * comes one note line for each of the competition's approximations, in the
 * competition's own order: a format the dataset asked for and this version
 * replaced by a fallback is shown, never played silently, per OPEN-QUESTIONS
 * item 118. A competition built as configured prints no note. A competition
 * with a league phase then prints that phase's own overall table (the first
 * Phase.League of its phase list, which is the only one for every shape this
 * version builds:
 * a plain division, a grouped division or a state championship's group stage
 * all keep their league phase first); a competition whose last phase is a
 * knockout then also prints the final order line, so a state championship
 * that both tables its group stage and settles a bracket prints both, while
 * a plain league without a final phase prints only the table and a pure
 * knockout, the national cup, prints only the order.
 *
 * Every league division and state division then ends its block with an up
 * line and a down line, the design's "quem subiu e desceu": the clubs the
 * turnover that follows this season moves out of the division, upwards and
 * downwards, in the order it moves them, or nothing after the colon when it
 * moves none. They are read from seasonMovements, the one function
 * nextSeason applies, so the printout names exactly the moves the next
 * season is built from. A club coming up out of a reserve belongs to no
 * competition and appears on no line; the Brazilian fourth fed by the states
 * lists as down every club that does not go up, since each of them leaves
 * the division for the reserve.
 *
 * The top scorers section closes the printout: the five leading scorers of
 * the whole season, across every club and every competition, goals
 * descending and then, per the interface's own total order, player name and
 * then club key. Only a player who actually scored is listed, so a season
 * with fewer than five scorers anywhere prints fewer than five lines rather
 * than padding the list with names that never troubled a scoreboard.
 */
internal fun describeSeason(state: SeasonState): String {
    val builder = StringBuilder()
    builder.appendLine(
        "season    ${state.number}  year ${state.year}  rounds ${state.schedule.dates.size}  matches ${state.played.size}",
    )

    val movements = seasonMovements(state)
    for (close in state.closed) {
        appendCompetition(builder, state, close, movements)
    }

    appendTopScorers(builder, state)

    return builder.toString()
}

private fun appendCompetition(builder: StringBuilder, state: SeasonState, close: CompetitionClose, movements: SeasonMovements) {
    val competition = state.competitions.getValue(close.key)
    val champion = close.finalOrder[0]
    val runnerUp = close.finalOrder[1]
    builder.appendLine("  ${close.key}  ${close.kind}  champion $champion  runner-up $runnerUp")
    competition.approximations.forEach { builder.appendLine("    note: $it") }

    val leagueIndex = competition.phases.indexOfFirst { it is Phase.League }
    if (leagueIndex >= 0) {
        val league = (competition.phases[leagueIndex] as Phase.League).phase
        appendTable(builder, league.overallTable(competition.results[leagueIndex]))
    }
    if (competition.phases.last() is Phase.Knockout) {
        builder.appendLine("    final order: ${close.finalOrder.joinToString(", ")}")
    }
    movements.departuresOf(close.key)?.let { departures ->
        builder.appendLine("    up:" + listed(departures.up))
        builder.appendLine("    down:" + listed(departures.down))
    }
}

private fun listed(keys: List<String>): String = if (keys.isEmpty()) "" else " " + keys.joinToString(", ")

private val TABLE_HEADERS = listOf("pos", "club", "pts", "pld", "w", "d", "l", "gf", "ga")

private fun appendTable(builder: StringBuilder, rows: List<TableRow>) {
    val cells = rows.mapIndexed { index, row ->
        listOf(
            (index + 1).toString(),
            row.key,
            row.points.toString(),
            row.played.toString(),
            row.wins.toString(),
            row.draws.toString(),
            row.losses.toString(),
            row.goalsFor.toString(),
            row.goalsAgainst.toString(),
        )
    }
    val widths = TABLE_HEADERS.indices.map { column ->
        maxOf(TABLE_HEADERS[column].length, cells.maxOfOrNull { it[column].length } ?: 0)
    }

    fun render(values: List<String>): String = values.indices.joinToString("  ") { column ->
        if (column == 1) values[column].padEnd(widths[column]) else values[column].padStart(widths[column])
    }

    builder.appendLine("    " + render(TABLE_HEADERS))
    cells.forEach { builder.appendLine("    " + render(it)) }
}

private data class Scorer(val goals: Int, val name: String, val club: String)

private val SCORER_ORDER: Comparator<Scorer> = compareByDescending<Scorer> { it.goals }
    .thenBy { it.name }
    .thenBy { it.club }

private fun appendTopScorers(builder: StringBuilder, state: SeasonState) {
    val scorers = state.clubs.values.flatMap { club ->
        club.records.mapIndexedNotNull { index, record ->
            if (record.goals > 0) Scorer(record.goals, club.squad[index].name, club.key) else null
        }
    }.sortedWith(SCORER_ORDER).take(TOP_SCORER_COUNT)

    builder.appendLine("  top scorers")
    scorers.forEach { builder.appendLine("    ${it.goals}  ${it.name}  ${it.club}") }
}

private const val TOP_SCORER_COUNT = 5
