package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

/**
 * The national cup of section 1.13 in its standard format: a single elimination
 * bracket of the largest power of two up to a hundred and twenty eight that fits
 * the country's clubs, at least eight, strong half against weak half each shuffled
 * with its own fork, and every round two legged with the weaker side hosting the
 * first leg. The novo formato of Brazil waits for the continental qualification
 * it seeds from, which names twelve seeds; its hardcoded first round of eighty
 * never goes to penalties, the second listed side of a level tie going through
 * (OPEN-QUESTIONS item eighty-two), while the standard format built here schedules
 * a shootout on every level tie.
 *
 * A knockout only competition reads its participants from the entrants of its
 * first knockout phase.
 */
@SpecRef("1.13")
fun nationalCup(country: Int, clubs: List<ClubState>, rng: Rng): Competition? {
    val eligible = clubs.filter { it.country == country }
        .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { it.key })
        .map { it.key }
    val size = bracketSize(eligible.size) ?: return null
    val field = eligible.take(size)
    val strong = shuffledOrder(field.take(size / 2), rng.fork(STRONG_HALF))
    val weak = shuffledOrder(field.drop(size / 2), rng.fork(WEAK_HALF))
    val entrants = (strong + weak).mapIndexed { index, key -> Entrant(key, index + 1) }
    return Competition(
        key = "cup:$country",
        kind = CompetitionKind.NATIONAL_CUP,
        country = country,
        division = null,
        phases = listOf(Phase.Knockout(KnockoutPhase(entrants, legsPerRound = List(MAX_CUP_ROUNDS) { true }, penalties = true))),
        qualifiers = { _, _ -> emptyList() },
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

/**
 * The largest power of two at most the club count and the cap, or null below
 * the minimum field.
 */
@SpecRef("1.13")
internal fun bracketSize(clubCount: Int): Int? {
    var size = MAX_CUP_FIELD
    while (size > clubCount) size /= 2
    return if (size >= MIN_CUP_FIELD) size else null
}

@SpecRef("1.13")
private const val MAX_CUP_FIELD = 128

@SpecRef("1.13")
private const val MIN_CUP_FIELD = 8

@SpecRef("1.13")
private const val MAX_CUP_ROUNDS = 7

@SpecRef("1.13")
private const val STRONG_HALF = 0L

@SpecRef("1.13")
private const val WEAK_HALF = 1L
