package org.openfoot.cli

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.PlayerEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Position
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One world, printed once, pinned exactly.
 *
 * The dataset below is written in code for this file alone: two countries,
 * neither is any country a real installation ships, built from PlayerEntry
 * and ClubEntry values chosen so that section 1.9's pyramid produces all
 * three standing tokens summarise can print in a single small world. Nothing
 * here is read from an importer or from disk.
 *
 * Country FIX plays its league. It holds eleven clubs at levels twenty down
 * to ten, one apart, named clube-01 at level twenty through clube-11 at
 * level ten. Eleven clears section 1.9's candidate minimum of ten, so a
 * league is seated: DIVISION_STEPS's first size at or under eleven remaining
 * clubs is ten, so the top ten by level, clube-01 through clube-10, fill
 * division one entirely, and the eleventh and weakest, clube-11, is left
 * over with no division left to hold it. Country REP never plays its
 * league, so its one club, clube-rep, stands on section 1.9's reputation
 * path regardless of how few or many clubs it has.
 *
 * Every club fields two players, the same PlayerEntry in every field but
 * name, name age 25, position midfielder, traits stamina and crossing,
 * never a starter or a star. That keeps the pinned literal to sixteen lines
 * total, four of header and one per club, and keeps every player's strength
 * a two term draw this docstring can bound by hand against section 4.4.
 *
 * Both countries are level twenty, past the country scale's weak ceiling of
 * thirteen (InitialStrength.kt's WEAK_COUNTRY_CEILING), and every club level
 * used here, ten through twenty, is at or above the ten that the scale's
 * upper ladder exempts. The scale therefore multiplies nothing anywhere in
 * this vector, which is what makes the ranges below the whole story rather
 * than an approximation of it.
 *
 * mappedLevel passes fifteen and under through unchanged, and above that
 * sixteen maps to seventeen, seventeen to eighteen, eighteen to nineteen,
 * nineteen to twenty one, and twenty (past all four special cases) to
 * twenty five by the general plus five rule. A division one player's
 * strength is mappedLevel(level) + 20 + rand(3), rand(3) drawing zero, one
 * or two. clube-11's leftover row instead uses ClubBands' divisionless
 * league band, strengthBase one, so its strength is mappedLevel(10) + 1 +
 * rand(3) = 10 + 1 + 0..2 = 11 to 13. clube-rep's reputation five band is
 * strengthBase twenty two, so its strength is mappedLevel(14) + 22 +
 * rand(3) = 14 + 22 + 0..2 = 36 to 38.
 *
 * The eleven division rows, worked out the same way:
 * clube-01 level 20, mapped 25: 25 + 20 + 0..2 = 45 to 47.
 * clube-02 level 19, mapped 21: 21 + 20 + 0..2 = 41 to 43.
 * clube-03 level 18, mapped 19: 19 + 20 + 0..2 = 39 to 41.
 * clube-04 level 17, mapped 18: 18 + 20 + 0..2 = 38 to 40.
 * clube-05 level 16, mapped 17: 17 + 20 + 0..2 = 37 to 39.
 * clube-06 level 15, mapped 15: 15 + 20 + 0..2 = 35 to 37.
 * clube-07 level 14, mapped 14: 14 + 20 + 0..2 = 34 to 36.
 * clube-08 level 13, mapped 13: 13 + 20 + 0..2 = 33 to 35.
 * clube-09 level 12, mapped 12: 12 + 20 + 0..2 = 32 to 34.
 * clube-10 level 11, mapped 11: 11 + 20 + 0..2 = 31 to 33.
 *
 * The pinned string below was produced by running this exact dataset
 * through generateWorld at seed 42 and summarise, then checked line by
 * line: every club's level and standing token against the pyramid this
 * docstring works out above, every club's player count against the two
 * PlayerEntry values it was given, and every best strength against the
 * range worked out for that club's row. The header's own strength line
 * follows from the same twelve ranges: the lowest figure summarise can ever
 * print is clube-11's floor of eleven and the highest is clube-01's ceiling
 * of forty seven, so a printed min of twelve and max of forty seven both
 * sit inside those bounds, and the median of thirty seven falls between
 * them where the bulk of the division rows cluster. Only then was the
 * printed string copied into the assertion below. The generator's own
 * reproducibility is what a changed figure here would report on; this file
 * does not reimplement SplitMix64 to predict which of a range's few values
 * a given seed draws.
 *
 * What this vector cannot see: it never runs the importer, so it says
 * nothing about a real installation's data surviving the trip through
 * InstallationImporter; it is one seed, so it is not evidence for any other
 * seed, any more than MatchGoldenVectorTest's four seeds are; and it holds
 * one active country and one inactive one, so it says nothing about a world
 * with two active leagues, or about the sixteen club threshold the five
 * countries HIGH_THRESHOLD_COUNTRIES names in Pyramid.kt need instead of
 * ten. Both are covered by WorldGenerationTest and PyramidTest instead,
 * which build whatever shape a given assertion needs rather than the one
 * shape this file is pinned to.
 */
class WorldGoldenVectorTest {

    private val fixCountry = CountryEntry(index = 701, name = "Fixture Ativo", level = 20, continent = 1)
    private val repCountry = CountryEntry(index = 702, name = "Fixture Reputacao", level = 20, continent = 2)

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

    private val dataset = WorldDataset(
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

    @Test
    fun `the fixture world summarises exactly as pinned`() {
        val world = generateWorld(dataset, seed = 42L, activeLeagues = setOf(fixCountry.index))
        assertEquals(
            """
            seed      42
            clubs     12
            players   24
            strength  min 12  median 37  max 47
              clube-01  level 20  div 1  players 2  best 47 clube-01 jogador 2
              clube-02  level 19  div 1  players 2  best 42 clube-02 jogador 1
              clube-03  level 18  div 1  players 2  best 41 clube-03 jogador 1
              clube-04  level 17  div 1  players 2  best 40 clube-04 jogador 2
              clube-05  level 16  div 1  players 2  best 39 clube-05 jogador 2
              clube-06  level 15  div 1  players 2  best 37 clube-06 jogador 2
              clube-07  level 14  div 1  players 2  best 35 clube-07 jogador 1
              clube-08  level 13  div 1  players 2  best 33 clube-08 jogador 1
              clube-09  level 12  div 1  players 2  best 33 clube-09 jogador 1
              clube-10  level 11  div 1  players 2  best 31 clube-10 jogador 1
              clube-11  level 10  div -  players 2  best 13 clube-11 jogador 1
              clube-rep  level 14  rep  players 2  best 37 clube-rep jogador 2
            """.trimIndent() + "\n",
            summarise(world),
        )
    }
}
