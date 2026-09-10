package org.openfoot.cli

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.PlayerEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Position
import org.openfoot.model.Trait

/**
 * The one dataset the golden vectors of this module are pinned against.
 *
 * Written in code for these tests alone: two countries, neither any country
 * a real installation ships, built from PlayerEntry and ClubEntry values
 * chosen so that section 1.9's pyramid produces all three standing tokens
 * summarise can print in a single small world. Nothing here is read from an
 * importer or from disk. WorldGoldenVectorTest works the ranges out in full;
 * CallUpGoldenVectorTest reuses the same shape so its ranges follow from the
 * same arithmetic.
 *
 * Country FIX plays its league and holds eleven clubs at levels twenty down
 * to ten. Country REP never plays its league and holds one club, whose two
 * players are nonetheless of FIX nationality, like every player here: REP has
 * no player of its own anywhere in the world.
 */
object GoldenWorld {

    val fixCountry = CountryEntry(index = 701, name = "Fixture Ativo", level = 20, continent = 1)
    val repCountry = CountryEntry(index = 702, name = "Fixture Reputacao", level = 20, continent = 2)

    private fun players(ref: String): List<PlayerEntry> = listOf(
        PlayerEntry(
            name = "$ref jogador 1",
            age = 25,
            country = fixCountry.index,
            position = Position.MIDFIELDER,
            firstTrait = Trait.STAMINA,
            secondTrait = Trait.CROSSING,
        ),
        PlayerEntry(
            name = "$ref jogador 2",
            age = 25,
            country = fixCountry.index,
            position = Position.MIDFIELDER,
            firstTrait = Trait.STAMINA,
            secondTrait = Trait.CROSSING,
        ),
    )

    private fun fixClub(ref: String, level: Int) = ClubEntry(
        ref = ref,
        name = ref,
        country = fixCountry.index,
        level = level,
        reputation = 3,
        squad = players(ref),
    )

    val dataset = WorldDataset(
        countries = listOf(fixCountry, repCountry),
        clubs = listOf(
            fixClub("clube-01", 20),
            fixClub("clube-02", 19),
            fixClub("clube-03", 18),
            fixClub("clube-04", 17),
            fixClub("clube-05", 16),
            fixClub("clube-06", 15),
            fixClub("clube-07", 14),
            fixClub("clube-08", 13),
            fixClub("clube-09", 12),
            fixClub("clube-10", 11),
            fixClub("clube-11", 10),
            ClubEntry(
                ref = "clube-rep",
                name = "clube-rep",
                country = repCountry.index,
                level = 14,
                reputation = 5,
                squad = players("clube-rep"),
            ),
        ),
    )
}
