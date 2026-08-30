package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.DatasetOptions
import org.openfoot.dataset.PlayerEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Country
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

/**
 * Turns one dataset entry into a generated player.
 *
 * Draws run in the order section 4.3 through 4.7 are applied, and that order is
 * the contract: strength before abilities because the primary ability is built
 * from strength, style before abilities because it selects which row of the
 * table applies, and trait bonuses after the abilities they modify. See
 * OPEN-QUESTIONS item 10.
 *
 * Abilities are always generated, even though the original only fills them when
 * its individual ability option is on. Generating them unconditionally costs
 * seven draws and cannot be observed when the option is off, because the match
 * engine reads the single strength in that mode and never looks at them. It
 * buys a player who is the same player whichever way the option is set.
 *
 * The standing comes from the pyramid of section 1.9, built once per world, and
 * replaces the dataset's former division field as the source of the bands: a
 * club stands wherever the pyramid ranked it, not wherever its own entry claims.
 */
@SpecRef("4")
internal fun generatePlayer(
    entry: PlayerEntry,
    standing: Standing,
    club: ClubEntry,
    countryLevel: Int,
    majorLeagueCountry: Boolean,
    europeanNationality: Boolean,
    options: DatasetOptions,
    rng: Rng,
): Player {
    val bands = when (standing) {
        is Standing.InDivision -> ClubBands.bands(standing.division, club.reputation)
        Standing.WithoutDivision -> ClubBands.bands(null, club.reputation)
        Standing.ByReputation -> ClubBands.bands(null, club.reputation, reputationPath = true)
    }
    val qualitySeed = ClubBands.qualitySeed(club.level)

    val style = playerStyle(entry.position, entry.firstTrait, entry.secondTrait)

    val strength = initialStrength(
        clubLevel = club.level,
        bands = bands,
        countryLevel = countryLevel,
        starter = entry.starter,
        star = entry.star || entry.topWorld,
        rng = rng,
    )

    val abilities = generateAbilities(
        position = entry.position,
        style = style,
        strength = strength,
        qualitySeed = qualitySeed,
        band = bands.abilityBand,
        rng = rng,
    )
    applyTraitBonuses(
        abilities = abilities,
        position = entry.position,
        firstTrait = entry.firstTrait,
        secondTrait = entry.secondTrait,
        qualitySeed = qualitySeed,
        band = bands.abilityBand,
        rng = rng,
    )

    val contractDays = initialContractDays(rng)

    return Player(
        name = entry.name,
        age = entry.age,
        country = entry.country,
        position = entry.position,
        side = entry.side,
        firstTrait = entry.firstTrait,
        secondTrait = entry.secondTrait,
        starter = entry.starter,
        star = entry.star,
        topWorld = entry.topWorld,
        talent = entry.talent,
        style = style,
        strength = strength,
        abilities = abilities.toList(),
        contractDays = contractDays,
        salary = salary(
            strength = strength,
            age = entry.age,
            position = entry.position,
            star = entry.star,
            topWorld = entry.topWorld,
            clubLevel = club.level,
            division = (standing as? Standing.InDivision)?.division,
            majorLeagueCountry = majorLeagueCountry,
            monthlyWages = options.monthlyWages,
        ),
        marketValue = marketValue(
            strength = strength,
            age = entry.age,
            position = entry.position,
            starter = entry.starter,
            star = entry.star,
            topWorld = entry.topWorld,
            clubLevel = club.level,
            europeanNationality = europeanNationality,
        ),
    )
}

/**
 * Generates a whole squad from a club's dataset entry.
 *
 * Each player gets his own stream, forked from the club's by his position in
 * the squad list. Nothing is drawn from a stream shared between players, so
 * generating the eleventh player does not depend on the first ten having been
 * generated, and a squad comes out the same whether it is built in order, in
 * reverse or across threads.
 *
 * A club whose country is missing from the dataset is a fatal error rather than
 * a default, because the country level scales the strength of every player in
 * it. A player whose own nationality is missing is not: it selects only between
 * star value rungs that need a club level no data file can express.
 *
 * The standing comes from the pyramid of section 1.9, built once per world, and
 * replaces the dataset's former division field as the source of the bands.
 */
@SpecRef("4")
fun generateSquad(
    club: ClubEntry,
    standing: Standing,
    dataset: WorldDataset,
    options: DatasetOptions,
    clubRng: Rng,
): List<Player> {
    val country = dataset.country(club.country)
    requireNotNull(country) {
        "club ${club.ref} sits in country ${club.country}, which the dataset does not describe, " +
            "and the country level scales the strength of every player in the squad"
    }

    return club.squad.mapIndexed { index, entry ->
        val nationality = dataset.country(entry.country)
        generatePlayer(
            entry = entry,
            standing = standing,
            club = club,
            countryLevel = country.level,
            majorLeagueCountry = country.majorLeague,
            europeanNationality = nationality?.continent == Country.EUROPE_CONTINENT,
            options = options,
            rng = clubRng.fork(index.toLong()),
        )
    }
}
