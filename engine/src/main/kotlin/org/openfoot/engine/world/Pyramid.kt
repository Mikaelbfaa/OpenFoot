package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

/**
 * Where a club stands when its world is generated.
 *
 * A division of an active league, the leftover row of an active country
 * whose pyramid could not hold it, or the reputation path that section 1.9
 * gives to every club of a country whose league is not played. National
 * teams, when they exist, will stand on the reputation path too.
 */
@SpecRef("1.9")
sealed interface Standing {
    data class InDivision(val division: Int) : Standing
    data object WithoutDivision : Standing
    data object ByReputation : Standing
}

/**
 * Builds every active country's pyramid and decides which clubs exist.
 *
 * The original assembles this once per world: candidates are countries with
 * enough team files, the player picks which candidate leagues are played,
 * and clubs are ranked by level with a random tie break drawn per club. Here
 * the tie break is derived from the seed through the club's reference, in
 * the pattern OPEN-QUESTIONS item 10 established: the mechanism of the
 * original, made reproducible. Deriving it from the reference rather than
 * from list position keeps a dataset reorder from changing any pyramid.
 *
 * A ref absent from the returned map is a club that does not exist in this
 * world: section 1.9 instantiates only the top fifteen clubs of a country
 * whose league is not played.
 */
@SpecRef("1.9")
fun assemblePyramids(
    dataset: WorldDataset,
    activeLeagues: Set<Int>,
    worldRng: Rng,
): Map<String, Standing> {
    val standings = LinkedHashMap<String, Standing>()

    for ((country, clubs) in dataset.clubs.groupBy { it.country }) {
        val ranked = clubs.sortedWith(
            compareByDescending<ClubEntry> { it.level }
                .thenBy { tiebreak(worldRng, it.ref) }
                .thenBy { it.ref },
        )
        if (country in activeLeagues && ranked.size >= candidateThreshold(country)) {
            assignDivisions(country, ranked, dataset, standings)
        } else {
            ranked.take(TOP_CLUBS_WITHOUT_LEAGUE).forEach {
                standings[it.ref] = Standing.ByReputation
            }
        }
    }
    return standings
}

/**
 * The per club tie break of section 1.9, drawn from the club's own stream
 * family so it never disturbs the squad draws: forking consumes nothing.
 */
@SpecRef("1.9")
private fun tiebreak(worldRng: Rng, ref: String): Int =
    worldRng.fork(clubKey(ref)).fork(PYRAMID_TIEBREAK_STREAM).nextInt(TIEBREAK_BOUND)

@SpecRef("1.9")
private fun assignDivisions(
    country: Int,
    ranked: List<ClubEntry>,
    dataset: WorldDataset,
    standings: LinkedHashMap<String, Standing>,
) {
    var index = 0
    for (division in 1..MAX_DIVISIONS) {
        val remaining = ranked.size - index
        val configured = dataset.leagues.firstOrNull {
            it.country == country && it.division == division
        }
        val size = when {
            configured != null && configured.teamCount <= remaining -> configured.teamCount
            else -> DIVISION_STEPS.firstOrNull { it <= remaining } ?: break
        }
        repeat(size) {
            standings[ranked[index].ref] = Standing.InDivision(division)
            index += 1
        }
    }
    while (index < ranked.size) {
        standings[ranked[index].ref] = Standing.WithoutDivision
        index += 1
    }
}

@SpecRef("1.9")
private fun candidateThreshold(country: Int): Int =
    if (country in HIGH_THRESHOLD_COUNTRIES) HIGH_CANDIDATE_MINIMUM else CANDIDATE_MINIMUM

/** Countries that need sixteen clubs to host a league: ALE, ARG, FRA, ING, ITA. */
@SpecRef("1.9")
private val HIGH_THRESHOLD_COUNTRIES = setOf(3, 11, 72, 97, 104)

@SpecRef("1.9")
private const val CANDIDATE_MINIMUM = 10

@SpecRef("1.9")
private const val HIGH_CANDIDATE_MINIMUM = 16

@SpecRef("1.9")
private const val MAX_DIVISIONS = 4

/** Division sizes tried largest first, the default when no configuration fits. */
@SpecRef("1.9")
private val DIVISION_STEPS = listOf(20, 18, 16, 14, 12, 10)

@SpecRef("1.9")
private const val TOP_CLUBS_WITHOUT_LEAGUE = 15

/**
 * Stream tag for the tie break inside a club's stream family. Player streams
 * fork by squad index, small numbers, so any large distinct constant is safe.
 */
private const val PYRAMID_TIEBREAK_STREAM = 0x50F7L

@SpecRef("1.9")
private const val TIEBREAK_BOUND = 1000
