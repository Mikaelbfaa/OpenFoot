package org.openfoot.dataset

import kotlinx.serialization.json.Json
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.Side
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A dataset file is untrusted input, so these pin the boundary rather than the
 * happy path. Every case here is something a hand edited or third party file
 * can plausibly contain, and every one of them must fail on decode rather than
 * reach world generation.
 */
class WorldDatasetTest {

    private fun player(
        name: String = "Jogador",
        age: Int = 25,
        country: Int = Country.BRAZIL,
        position: Position = Position.MIDFIELDER,
        firstTrait: Trait = Trait.PASSING,
        secondTrait: Trait = Trait.STAMINA,
        star: Boolean = false,
        topWorld: Boolean = false,
        talent: Int = 6,
    ) = PlayerEntry(
        name = name,
        age = age,
        country = country,
        position = position,
        firstTrait = firstTrait,
        secondTrait = secondTrait,
        star = star,
        topWorld = topWorld,
        talent = talent,
    )

    private fun club(
        ref: String = "clube_bra",
        level: Int = 18,
        reputation: Int = 4,
        country: Int = Country.BRAZIL,
        state: Int? = 25,
        squad: List<PlayerEntry> = listOf(player()),
    ) = ClubEntry(
        ref = ref,
        name = "Clube",
        country = country,
        level = level,
        reputation = reputation,
        state = state,
        squad = squad,
    )

    private fun dataset(clubs: List<ClubEntry> = listOf(club())) = WorldDataset(
        countries = listOf(CountryEntry(index = Country.BRAZIL, name = "Brasil", level = 20, continent = 1)),
        clubs = clubs,
    )

    @Test
    fun `a dataset survives a round trip through json unchanged`() {
        val original = dataset()
        val decoded = Json.decodeFromString<WorldDataset>(Json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `the wire format decodes from a hand written document`() {
        val document = """
            {
              "countries": [
                { "index": 29, "name": "Brasil", "level": 20, "continent": 2 }
              ],
              "clubs": [
                {
                  "ref": "clube_bra",
                  "name": "Clube",
                  "country": 29,
                  "level": 18,
                  "reputation": 4,
                  "state": 25,
                  "stadium": "Estadio",
                  "capacity": 45000,
                  "coach": "Tecnico",
                  "squad": [
                    {
                      "name": "Goleiro",
                      "age": 30,
                      "country": 29,
                      "position": "GOALKEEPER",
                      "firstTrait": "REFLEXES",
                      "secondTrait": "POSITIONING",
                      "starter": true,
                      "talent": 7
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val decoded = Json.decodeFromString<WorldDataset>(document)
        val onlyClub = decoded.clubs.single()
        val keeper = onlyClub.squad.single()

        assertEquals(WorldDataset.CURRENT_VERSION, decoded.version)
        assertEquals(20, decoded.country(Country.BRAZIL)?.level)
        assertEquals("clube_bra", onlyClub.ref)
        assertEquals(Country.BRAZIL, onlyClub.coachCountry)
        assertEquals(Position.GOALKEEPER, keeper.position)
        assertEquals(Side.RIGHT, keeper.side)
        assertEquals(7, keeper.talent)
    }

    @Test
    fun `two clubs sharing a ref are rejected because the ref seeds the generator`() {
        assertFailsWith<IllegalArgumentException> {
            dataset(listOf(club(ref = "mesmo"), club(ref = "mesmo")))
        }
    }

    @Test
    fun `a club level outside the range the original stores is rejected`() {
        assertFailsWith<IllegalArgumentException> { club(level = 21) }
        assertFailsWith<IllegalArgumentException> { club(level = 5) }
        club(level = 6)
        club(level = 20)
    }

    @Test
    fun `a reputation outside zero to five is rejected`() {
        assertFailsWith<IllegalArgumentException> { club(reputation = 6) }
        assertFailsWith<IllegalArgumentException> { club(reputation = -1) }
    }

    @Test
    fun `a state on a club outside Brazil is rejected as stale bytes`() {
        assertFailsWith<IllegalArgumentException> {
            club(country = Country.BRAZIL + 1, state = 25)
        }
        club(country = Country.BRAZIL + 1, state = null)
    }

    @Test
    fun `a state outside the twenty seven championships is rejected`() {
        assertFailsWith<IllegalArgumentException> { club(state = 27) }
        club(state = 26)
        club(state = 0)
    }

    @Test
    fun `a top world player who is not a star is rejected`() {
        assertFailsWith<IllegalArgumentException> { player(star = false, topWorld = true) }
        player(star = true, topWorld = true)
    }

    @Test
    fun `a goalkeeping trait on an outfielder is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            player(position = Position.FORWARD, firstTrait = Trait.REFLEXES)
        }
        assertFailsWith<IllegalArgumentException> {
            player(position = Position.GOALKEEPER, firstTrait = Trait.REFLEXES, secondTrait = Trait.PACE)
        }
        player(position = Position.GOALKEEPER, firstTrait = Trait.REFLEXES, secondTrait = Trait.POSITIONING)
    }

    @Test
    fun `talent accepts the zero that occurs in the distributed files`() {
        player(talent = 0)
        player(talent = 10)
        assertFailsWith<IllegalArgumentException> { player(talent = 11) }
        assertFailsWith<IllegalArgumentException> { player(talent = -1) }
    }

    @Test
    fun `a dataset from a future version is refused rather than half read`() {
        assertFailsWith<IllegalArgumentException> {
            WorldDataset(
                version = WorldDataset.CURRENT_VERSION + 1,
                countries = emptyList(),
                clubs = listOf(club()),
            )
        }
    }

    @Test
    fun `a version one file is refused with the version in the message`() {
        val document = """
            {
              "version": 1,
              "countries": [
                { "index": 29, "name": "Brasil", "level": 20, "continent": 1 }
              ],
              "clubs": [
                {
                  "ref": "clube_bra",
                  "name": "Clube",
                  "country": 29,
                  "level": 18,
                  "reputation": 4
                }
              ]
            }
        """.trimIndent()

        val failure = assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<WorldDataset>(document)
        }
        assertTrue(failure.message.orEmpty().contains("version 1"))
    }

    @Test
    fun `a dataset with no clubs is refused`() {
        assertFailsWith<IllegalArgumentException> {
            WorldDataset(countries = emptyList(), clubs = emptyList())
        }
    }

    @Test
    fun `country lookup finds a present index and reports a missing one`() {
        val data = dataset()
        assertEquals("Brasil", data.country(Country.BRAZIL)?.name)
        assertNull(data.country(Country.BRAZIL + 1))
    }

    private fun sampleLeague(
        country: Int = Country.BRAZIL,
        division: Int = 1,
        teamCount: Int = 20,
        relegated: Int = 4,
        turns: Int = 2,
        penaltiesTiebreak: Boolean = false,
    ) = LeagueConfigEntry(
        country = country,
        division = division,
        teamCount = teamCount,
        relegated = relegated,
        turns = turns,
        penaltiesTiebreak = penaltiesTiebreak,
    )

    @Test
    fun `a league configuration entry validates its ranges`() {
        val entry = sampleLeague()
        assertEquals(20, entry.teamCount)
        assertFailsWith<IllegalArgumentException> { entry.copy(country = -1) }
        assertFailsWith<IllegalArgumentException> { entry.copy(division = 0) }
        assertFailsWith<IllegalArgumentException> { entry.copy(division = 5) }
        assertFailsWith<IllegalArgumentException> { entry.copy(teamCount = 0) }
        assertFailsWith<IllegalArgumentException> { entry.copy(relegated = 21) }
        assertFailsWith<IllegalArgumentException> { entry.copy(turns = 0) }
        assertFailsWith<IllegalArgumentException> { entry.copy(turns = 5) }
    }

    @Test
    fun `league configurations survive a round trip and default to none`() {
        val original = dataset().copy(leagues = listOf(sampleLeague()))
        val encoded = Json.encodeToString(original)
        val decoded = Json.decodeFromString<WorldDataset>(encoded)
        assertEquals(listOf(sampleLeague()), decoded.leagues)
        assertEquals(emptyList(), dataset().leagues)
    }
}
