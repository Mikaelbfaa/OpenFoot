package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.PlayerEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.Trait

/**
 * Fixtures for the generation tests.
 *
 * Defaults are chosen so a squad is easy to reason about: a first division club
 * of level eighteen in a country strong enough that no scaling applies, which
 * makes a player's strength the mapped level plus the base plus his bonuses and
 * nothing else.
 */
object WorldFixtures {

    const val SPAIN = 65

    val countries = listOf(
        CountryEntry(index = Country.BRAZIL, name = "Brasil", level = 20, continent = 2),
        CountryEntry(index = SPAIN, name = "Espanha", level = 20, continent = Country.EUROPE_CONTINENT, majorLeague = true),
    )

    fun player(
        name: String = "Jogador",
        age: Int = 25,
        country: Int = Country.BRAZIL,
        position: Position = Position.MIDFIELDER,
        first: Trait = Trait.PASSING,
        second: Trait = Trait.STAMINA,
        starter: Boolean = false,
        star: Boolean = false,
        talent: Int = 6,
    ) = PlayerEntry(
        name = name,
        age = age,
        country = country,
        position = position,
        firstTrait = first,
        secondTrait = second,
        starter = starter,
        star = star,
        talent = talent,
    )

    fun club(
        ref: String = "clube_bra",
        name: String = "Clube",
        country: Int = Country.BRAZIL,
        level: Int = 18,
        reputation: Int = 4,
        squad: List<PlayerEntry> = listOf(player()),
    ) = ClubEntry(
        ref = ref,
        name = name,
        country = country,
        level = level,
        reputation = reputation,
        squad = squad,
    )

    fun dataset(
        clubs: List<ClubEntry> = listOf(club()),
        countries: List<CountryEntry> = this.countries,
    ) = WorldDataset(countries = countries, clubs = clubs)
}
