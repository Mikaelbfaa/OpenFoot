package org.openfoot.engine.lineup

import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.match.MatchPlayer
import org.openfoot.engine.match.MatchSetup
import org.openfoot.engine.match.MatchSide
import org.openfoot.engine.match.StrengthContext
import org.openfoot.engine.world.Competitor
import org.openfoot.engine.world.clubKey
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Marking
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.rand

/**
 * Draws the AI's tackling hardness for one side, following section 3.12's
 * rand(1..100) table: 1 to 5 very heavy, 6 to 70 light, 71 to 100 heavy.
 *
 * Light is the wide middle band, sixty five draws out of a hundred, not the
 * first band as the table's own row order might suggest. The table is written
 * one based, drawing a number from one to a hundred inclusive; the engine's
 * rand(bound) is nought based instead. This converts once, at the very top, by
 * drawing rand(100) and adding one, the same way drawFormation in
 * Formation.kt converts section 3.2's table rather than adjusting every band
 * boundary below by hand.
 */
@SpecRef("3.12")
fun drawMarking(rng: Rng): Marking {
    val draw = rng.rand(100) + 1
    return when (draw) {
        in 1..5 -> Marking.VERY_HEAVY
        in 6..70 -> Marking.LIGHT
        else -> Marking.HEAVY
    }
}

/**
 * A whole match assembled from two generated clubs: the setup ready to
 * simulate, and each side's bench, which MatchSetup itself has no room for
 * because a bench is not part of a minute's play, only of a substitution.
 */
data class AssembledMatch(
    val setup: MatchSetup,
    val homeBench: List<MatchPlayer>,
    val awayBench: List<MatchPlayer>,
)

/**
 * Builds a playable match between two competitors, generated clubs or called
 * up national teams: the bridge from world generation and the lineup layer to
 * something simulateMatch can take.
 *
 * Each side draws its own formation and its own marking from a stream forked
 * off its own competitor key, rng.fork(clubKey(key)), the same derivation
 * generateWorld uses to fork a club's squad stream off the world's. Because
 * the fork depends only on the key and never on which parameter, home or
 * away, the competitor was passed as, a side's formation and lineup come out
 * the same whichever end of the fixture it plays at. That is the property
 * MatchAssemblyTest pins.
 *
 * A national team side hands its country to the lineup, so every man it
 * fields carries section 3.3's national team scale; a club side hands none.
 *
 * The same is not asserted of marking. Whether the original really draws a
 * side's marking from that side's own stream, as implemented here, or from one
 * shared by the match instead, is not something the spec settles, since
 * section 5.4's automatic lineup consumes no randomness at all and marking is
 * the only draw assembleMatch has to place somewhere. See OPEN-QUESTIONS item
 * 37.
 *
 * StrengthContext needs each club's country and continent, both of which come
 * from the dataset's CountryEntry rather than from the club entry itself,
 * following how generateSquad resolves a club's country in SquadGeneration.kt.
 * A club whose country the given table does not describe is a fatal error for
 * the same reason it is there: the continent feeds a handicap in section 3.3,
 * so silently defaulting it would misrate every player of that club rather
 * than fail loudly at the one place the mistake can still be caught.
 *
 * Takes the whole WorldDataset rather than only its country table, because
 * StrengthContext.useIndividualAbilities has to come from somewhere too:
 * effectiveStrength reads that flag to decide whether a player is rated by his
 * seven attributes or by his single strength, and dataset.options.
 * individualAbilities is the one place that value lives. A narrower
 * countries-only signature has no way to read it at all, which would leave
 * every match simulated as though the option were off regardless of what an
 * installation's options.bcf actually says, silently, since nothing about a
 * mis-rated match says so on its own. There is deliberately no default for
 * this parameter: a default is exactly how a caller forgets to pass the real
 * dataset and reintroduces the same silent bug.
 *
 * Availability is required for the same reason, and it is the parameter where
 * the bug is still ahead of us rather than behind. Section 5.4 step 1 filters
 * the pool by who can play, so an assembled match is only as correct as what
 * it was told about injuries and suspensions. A season holds one PlayerRecord
 * per club, and the two clubs of a fixture answer section 5.4 step 1
 * independently of each other, so availability is asked once per competitor
 * rather than once for the whole match: availabilityOf(home) is not required
 * to equal availabilityOf(away), and in fact never does once ClubState exists,
 * since each club's own records are what the question is about. Letting it
 * default would put the choice out of sight in this file, and every call site
 * that still has no season state to ask passes Availability.FULL_SQUAD through
 * the single availability overload below and says so.
 */
@SpecRef("5.4")
fun assembleMatch(
    home: Competitor,
    away: Competitor,
    dataset: WorldDataset,
    kind: CompetitionKind,
    season: Int,
    rules: RuleSet,
    availabilityOf: (Competitor) -> Availability,
    rng: Rng,
): AssembledMatch {
    val homeSide = assembleSide(home, away, dataset, kind, isHomeSide = true, rules, availabilityOf(home), rng)
    val awaySide = assembleSide(away, home, dataset, kind, isHomeSide = false, rules, availabilityOf(away), rng)

    val setup = MatchSetup(
        home = homeSide.side,
        away = awaySide.side,
        season = season,
        rules = rules,
    )
    return AssembledMatch(setup = setup, homeBench = homeSide.bench, awayBench = awaySide.bench)
}

/**
 * The pre season one shot: every caller before ClubState existed, and every
 * caller since that still has nothing but Availability.FULL_SQUAD to hand in,
 * asks both sides the same question through this thin wrapper rather than
 * writing out a two argument lambda of their own at the call site.
 */
@SpecRef("5.4")
fun assembleMatch(
    home: Competitor,
    away: Competitor,
    dataset: WorldDataset,
    kind: CompetitionKind,
    season: Int,
    rules: RuleSet,
    availability: Availability,
    rng: Rng,
): AssembledMatch = assembleMatch(home, away, dataset, kind, season, rules, { availability }, rng)

/** One side's fielded eleven together with its bench, before either joins the other in a setup. */
private class AssembledSide(val side: MatchSide, val bench: List<MatchPlayer>)

/**
 * Builds one side of assembleMatch's result.
 *
 * Takes the opponent alongside the club being assembled because
 * StrengthContext.homeReputation and .awayReputation both name the pairing,
 * not just the side being built, and section 3.3's national cup and state
 * handicap reads both.
 *
 * availability answers section 5.4 step 1 for this one club alone.
 * assembleMatch asks availabilityOf once per side and hands each call's
 * result to the assembleSide built for that side, so a club with an injured
 * man never borrows the other club's own records by accident.
 *
 * The side carries the club's own designations across unchanged, unfiltered by
 * who actually made the eleven. Section 5.6 derives them from the whole squad
 * and section 3.7 is what refuses credit to a designated player who is not on
 * the pitch, so filtering them here would apply the same rule twice and hide
 * where it lives.
 */
private fun assembleSide(
    club: Competitor,
    opponent: Competitor,
    dataset: WorldDataset,
    kind: CompetitionKind,
    isHomeSide: Boolean,
    rules: RuleSet,
    availability: Availability,
    rng: Rng,
): AssembledSide {
    val country = clubCountry(club, dataset)

    val sideRng = rng.fork(clubKey(club.key))
    val formation = drawFormation(sideRng)
    val marking = drawMarking(sideRng)

    val context = StrengthContext(
        kind = kind,
        useIndividualAbilities = dataset.options.individualAbilities,
        sideReputation = club.reputation,
        sideCountry = country.index,
        sideContinent = country.continent,
        isHomeSide = isHomeSide,
        homeReputation = if (isHomeSide) club.reputation else opponent.reputation,
        awayReputation = if (isHomeSide) opponent.reputation else club.reputation,
    )

    val matchdaySquad = autoLineup(club.squad, formation, rules, availability, club.representedCountry)
    val side = MatchSide(
        lineup = matchdaySquad.onPitch,
        marking = marking,
        context = context,
        designated = club.designated,
    )
    return AssembledSide(side, matchdaySquad.bench)
}

/**
 * Resolves a club's country entry, the way generateSquad does in
 * SquadGeneration.kt: a club whose country is missing from the table is a
 * fatal error, because the continent scales the strength of every player it
 * fields in section 3.3, and a match rated from a default continent would be
 * a match rated wrong rather than one that failed to build.
 */
private fun clubCountry(club: Competitor, dataset: WorldDataset): CountryEntry =
    requireNotNull(dataset.country(club.country)) {
        "competitor ${club.key} sits in country ${club.country}, which the dataset does not " +
            "describe, and StrengthContext needs that country's continent"
    }
