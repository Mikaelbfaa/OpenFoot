package org.openfoot.engine.season

import org.openfoot.model.SpecRef

/**
 * One played result, as a table reads it: who was at home, who was away, and
 * the goals. Keys are competitor keys, the same strings a Competitor carries,
 * so a result names its sides the way every other part of the season does.
 */
@SpecRef("1.2")
data class Result(
    val home: String,
    val away: String,
    val homeGoals: Int,
    val awayGoals: Int,
) {
    init {
        require(home != away) { "$home cannot play itself" }
        require(homeGoals >= 0 && awayGoals >= 0) { "$home $homeGoals x $awayGoals $away is not a score" }
    }
}

/**
 * The eight values section 1.2 keeps per club and per competition. Draws are
 * derived there from games, wins and losses, and the goal difference from the
 * two goal counts; both are exposed as properties so a row reads like the
 * spec's record without storing anything twice.
 */
@SpecRef("1.2")
data class TableRow(
    val key: String,
    val points: Int,
    val played: Int,
    val wins: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
) {
    val draws: Int get() = played - wins - losses

    val goalDifference: Int get() = goalsFor - goalsAgainst
}

/**
 * The one classification comparator of the original, fixed for every
 * competition and every country: points, then wins, then goal difference,
 * then goals scored, all descending. It is a comparator rather than a sort so
 * that groups, overall tables and merit lists all rank with the same object.
 *
 * Whatever it leaves tied stays in the order it was given, because every sort
 * in this package is stable; standings feeds it the competition's own club
 * order, so two clubs level on all four values rank as the competition listed
 * them. Section 1.2 names no fifth criterion.
 */
@SpecRef("1.2")
val TABLE_ORDER: Comparator<TableRow> = compareByDescending<TableRow> { it.points }
    .thenByDescending { it.wins }
    .thenByDescending { it.goalDifference }
    .thenByDescending { it.goalsFor }

/**
 * The table of a competition, read out of its results.
 *
 * The results are the record and the table is a reading of it, the same way
 * a match's statistics are read out of its event log: nothing accumulates a
 * table alongside the results where the two could disagree. Every competitor
 * is present with a row, played or not, and a result naming a key the
 * competition does not hold is refused rather than folded in, because a
 * misspelt key would otherwise quietly create a phantom club.
 */
@SpecRef("1.2")
fun standings(competitors: List<String>, results: List<Result>): List<TableRow> {
    val rows = LinkedHashMap<String, TableRow>()
    for (key in competitors) {
        require(!rows.containsKey(key)) { "$key is listed twice among the competitors" }
        rows[key] = TableRow(key, points = 0, played = 0, wins = 0, losses = 0, goalsFor = 0, goalsAgainst = 0)
    }

    for (result in results) {
        val home = rows[result.home] ?: throw IllegalArgumentException("${result.home} is not in this competition")
        val away = rows[result.away] ?: throw IllegalArgumentException("${result.away} is not in this competition")
        rows[result.home] = home.credited(result.homeGoals, result.awayGoals)
        rows[result.away] = away.credited(result.awayGoals, result.homeGoals)
    }

    return rows.values.sortedWith(TABLE_ORDER)
}

@SpecRef("1.2")
private fun TableRow.credited(scored: Int, conceded: Int): TableRow = copy(
    points = points + when {
        scored > conceded -> WIN_POINTS
        scored == conceded -> DRAW_POINTS
        else -> 0
    },
    played = played + 1,
    wins = wins + if (scored > conceded) 1 else 0,
    losses = losses + if (scored < conceded) 1 else 0,
    goalsFor = goalsFor + scored,
    goalsAgainst = goalsAgainst + conceded,
)

/** Three for a win and one for a draw, the modern scale section 1.2 confirms. */
@SpecRef("1.2")
private const val WIN_POINTS = 3

@SpecRef("1.2")
private const val DRAW_POINTS = 1
