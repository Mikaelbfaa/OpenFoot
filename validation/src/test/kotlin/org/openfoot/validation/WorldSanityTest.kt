package org.openfoot.validation

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.PlayerEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Position
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Section 1.9's pyramid decides which path a club's strength stands on, and
 * section 4.4's bands say a club parked on the reputation path is not a weak
 * club: a country whose league nobody plays still holds real clubs, and a
 * strong one among them should generate a strong squad, not a squad dragged
 * down to the divisionless row it never actually occupies.
 *
 * The fixture below is the smallest pair that lets both paths be compared by
 * hand. Country A holds twenty clubs and plays its league, so the pyramid of
 * section 1.9 seats every one of them and the strongest, ClubA, lands in
 * division one. Country B holds twenty clubs too but its league is never
 * activated, so section 1.9 takes only its strongest fifteen by level onto
 * the reputation path, and ClubB, the strongest of the twenty, is always
 * among them regardless of the seed.
 *
 * Both clubs are level twenty, both countries are level twenty so section
 * 4.4's country scale never fires (WEAK_COUNTRY_CEILING is thirteen, both
 * countries sit above it, and a level twenty club is never below the ten
 * that scale's upper ladder exempts), and every player of every club here is
 * the same PlayerEntry in every field but name: no starter, no star. That
 * turns the two clubs' strength formulas into arithmetic anyone can check
 * against section 4.4 without running anything:
 *
 * mappedLevel(20) is 25, since twenty is past the flat ceiling of fifteen and
 * the mapping's else branch adds five.
 *
 * ClubA stands in division one, so its bands are strengthBase twenty, and a
 * player's strength is 25 + 20 + rand(3), which draws zero, one or two: a
 * range of 45 to 47.
 *
 * ClubB stands on the reputation path at reputation five, so its bands are
 * strengthBase twenty two, and a player's strength is 25 + 22 + rand(3): a
 * range of 47 to 49.
 *
 * The two ranges already overlap at 47. The widest the gap between a best of
 * each can ever be, with no starter or star bonus reachable in this fixture
 * to widen it further, is |45 - 49| = 4, which is what makes six a band with
 * slack rather than a band chosen to just barely pass.
 */
@SpecRef("1.9")
class WorldSanityTest {

    @Test
    fun `a top club of a country without a played league is not weak`() {
        for (seed in 1..SEED_SAMPLE_COUNT) {
            val world = generateWorld(DATASET, seed.toLong(), ACTIVE_LEAGUES)
            val bestA = world.club(CLUB_A_REF)?.squad?.maxOf { it.strength }
            val bestB = world.club(CLUB_B_REF)?.squad?.maxOf { it.strength }
            requireNotNull(bestA) { "seed $seed: club $CLUB_A_REF was not instantiated" }
            requireNotNull(bestB) { "seed $seed: club $CLUB_B_REF was not instantiated" }
            assertTrue(
                abs(bestA - bestB) <= STRENGTH_BAND,
                "seed $seed: best of $CLUB_A_REF is $bestA, best of $CLUB_B_REF is $bestB, " +
                    "a gap of ${abs(bestA - bestB)} over the band of $STRENGTH_BAND",
            )
        }
    }

    /**
     * Country B's twenty clubs never play a league, so section 1.9 takes
     * only the strongest fifteen by level onto the reputation path and
     * leaves the other five out of the world entirely. ClubB is the
     * strongest of the twenty by construction, level twenty against every
     * filler's six, so it is always one of the fifteen no matter which
     * five the seed's tie break happens to drop.
     */
    @SpecRef("1.9")
    @Test
    fun `a country without a played league instantiates fifteen clubs`() {
        val world = generateWorld(DATASET, FIXED_SEED, ACTIVE_LEAGUES)
        val fromCountryB = world.clubs.filter { it.entry.country == COUNTRY_B.index }
        assertEquals(15, fromCountryB.size, "clubs of a country whose league is not played")
        assertTrue(
            fromCountryB.any { it.entry.ref == CLUB_B_REF },
            "the strongest club of country B, $CLUB_B_REF, should survive the cut: $fromCountryB",
        )
    }

    @Test
    fun `the whole world replays from its seed`() {
        val first = generateWorld(DATASET, FIXED_SEED, ACTIVE_LEAGUES)
        val second = generateWorld(DATASET, FIXED_SEED, ACTIVE_LEAGUES)
        assertEquals(first.clubs, second.clubs)
    }

    private companion object {

        /** Sample seeds one to fifty, as the brief asks for. */
        const val SEED_SAMPLE_COUNT = 50

        /** The seed the single-shot tests below run at; any one seed will do for them. */
        const val FIXED_SEED = 4_004L

        /**
         * Hand-computed above: the widest the gap between the two clubs' best
         * players can ever be here is four, so six is a band with slack.
         */
        @SpecRef("4.4")
        const val STRENGTH_BAND = 6

        /** The stored club level both ClubA and ClubB carry, mapped to 25 by 4.4. */
        @SpecRef("4.4")
        const val TOP_LEVEL = 20

        /** The level every filler club carries, the bottom of ClubEntry.LEVEL_RANGE. */
        const val FILLER_LEVEL = 6

        /** How many clubs beyond the top one fill out each country to twenty. */
        const val FILLER_COUNT = 19

        /** A full senior squad, section 4's own count. */
        const val SQUAD_SIZE = 25

        const val CLUB_A_REF = "clube-a"
        const val CLUB_B_REF = "clube-b"

        /**
         * An active league. Twenty clubs meets section 1.9's candidate
         * minimum of ten with room to spare, and twenty is also the first
         * division size DIVISION_STEPS offers, so every one of the twenty
         * lands in division one and ClubA, the strongest, is among them.
         */
        @SpecRef("1.9")
        val COUNTRY_A = CountryEntry(index = 501, name = "Pais A", level = TOP_LEVEL, continent = 3)

        /**
         * A country whose league is never activated for this fixture. Its
         * level is twenty as well, purely so that section 4.4's country
         * scale reads the same value on both sides of the comparison and
         * therefore fires on neither.
         */
        @SpecRef("4.4")
        val COUNTRY_B = CountryEntry(index = 502, name = "Pais B", level = TOP_LEVEL, continent = 3)

        /** One player template, repeated identically within a squad. */
        private fun squad(country: Int): List<PlayerEntry> = List(SQUAD_SIZE) { index ->
            PlayerEntry(
                name = "jogador $index",
                age = 25,
                country = country,
                position = Position.MIDFIELDER,
                firstTrait = Trait.STAMINA,
                secondTrait = Trait.CROSSING,
            )
        }

        /** Country A's strongest club: level twenty, the one division one measures. */
        @SpecRef("4.4")
        val CLUB_A = ClubEntry(
            ref = CLUB_A_REF,
            name = "Clube A",
            country = COUNTRY_A.index,
            level = TOP_LEVEL,
            reputation = 0,
            squad = squad(COUNTRY_A.index),
        )

        /** Country B's strongest club: level twenty, reputation five, the reputation path's own case. */
        @SpecRef("4.4")
        val CLUB_B = ClubEntry(
            ref = CLUB_B_REF,
            name = "Clube B",
            country = COUNTRY_B.index,
            level = TOP_LEVEL,
            reputation = 5,
            squad = squad(COUNTRY_B.index),
        )

        private fun fillerClubs(country: CountryEntry, prefix: String): List<ClubEntry> =
            (1..FILLER_COUNT).map { i ->
                ClubEntry(
                    ref = "$prefix-$i",
                    name = "Filler $prefix $i",
                    country = country.index,
                    level = FILLER_LEVEL,
                    reputation = 0,
                    squad = squad(country.index),
                )
            }

        val DATASET = WorldDataset(
            countries = listOf(COUNTRY_A, COUNTRY_B),
            clubs = listOf(CLUB_A, CLUB_B) +
                fillerClubs(COUNTRY_A, "filler-a") +
                fillerClubs(COUNTRY_B, "filler-b"),
        )

        val ACTIVE_LEAGUES = setOf(COUNTRY_A.index)
    }
}
