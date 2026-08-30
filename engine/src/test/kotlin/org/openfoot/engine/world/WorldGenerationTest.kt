package org.openfoot.engine.world

import org.openfoot.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The determinism contract the whole project rests on, stated as tests.
 *
 * A career replays from its seed, a bug report is a seed plus a command log,
 * and neither is true unless generation depends on the seed and on nothing
 * else at all.
 */
class WorldGenerationTest {

    // These fixtures put fewer than ten clubs in Brazil, below the candidate
    // minimum section 1.9 needs to seat a league, so an active Brazil and an
    // inactive one instantiate the same clubs on the same reputation path;
    // activeLeagues is passed explicitly as emptySet() to say so plainly.
    private fun dataset(refs: List<String>) = WorldFixtures.dataset(
        refs.map { ref ->
            WorldFixtures.club(
                ref = ref,
                squad = List(4) { index -> WorldFixtures.player(name = "$ref jogador $index") },
            )
        },
    )

    /** The same fixture, plus one more club in Spain, a country no ref above uses. */
    private fun datasetWithForeignClub(refs: List<String>, foreignRef: String) = WorldFixtures.dataset(
        dataset(refs).clubs + WorldFixtures.club(
            ref = foreignRef,
            country = WorldFixtures.SPAIN,
            squad = List(4) { index -> WorldFixtures.player(name = "$foreignRef jogador $index") },
        ),
    )

    @Test
    fun `the same seed builds the same world`() {
        val data = dataset(listOf("um", "dois", "tres"))
        assertEquals(
            generateWorld(data, 2026, activeLeagues = emptySet()),
            generateWorld(data, 2026, activeLeagues = emptySet()),
        )
    }

    @Test
    fun `one bit of seed changes the world`() {
        val data = dataset(listOf("um", "dois", "tres"))
        assertNotEquals(
            generateWorld(data, 2026, activeLeagues = emptySet()),
            generateWorld(data, 2027, activeLeagues = emptySet()),
        )
    }

    @Test
    fun `a club generates the same squad wherever it sits in the dataset`() {
        val forward = dataset(listOf("um", "dois", "tres"))
        val reversed = dataset(listOf("tres", "dois", "um"))

        val fromForward = generateWorld(forward, 99, activeLeagues = emptySet()).club("dois")
        val fromReversed = generateWorld(reversed, 99, activeLeagues = emptySet()).club("dois")

        assertEquals(fromForward, fromReversed)
    }

    @Test
    fun `adding a club leaves every other club untouched`() {
        // Adding a club to the same country may reshape that country's pyramid,
        // which is section 1.9's own behavior; the invariance now holds across countries.
        val before = generateWorld(dataset(listOf("um", "dois")), 5, activeLeagues = emptySet())
        val after = generateWorld(
            datasetWithForeignClub(listOf("um", "dois"), "novo"),
            5,
            activeLeagues = emptySet(),
        )

        assertEquals(before.club("um"), after.club("um"))
        assertEquals(before.club("dois"), after.club("dois"))
        assertEquals(3, after.clubs.size)
    }

    @Test
    fun `adding a club to another country leaves this country byte identical`() {
        // Build the base world, then a world whose dataset adds one club in a
        // country no base club uses, and assert every base club's squad is
        // equal in both.
        val base = dataset(listOf("um", "dois", "tres"))
        val expanded = datasetWithForeignClub(listOf("um", "dois", "tres"), "estrangeiro")

        val before = generateWorld(base, 7, activeLeagues = emptySet())
        val after = generateWorld(expanded, 7, activeLeagues = emptySet())

        for (club in base.clubs) {
            assertEquals(before.club(club.ref)?.squad, after.club(club.ref)?.squad)
        }
    }

    @Test
    fun `the world only holds clubs the pyramid instantiated`() {
        val clubs = (1..20).map { i ->
            WorldFixtures.club(ref = "c$i", squad = List(2) { WorldFixtures.player() })
        }
        val world = generateWorld(WorldFixtures.dataset(clubs), 5, activeLeagues = emptySet())
        assertEquals(15, world.clubs.size)
    }

    @Test
    fun `a generated club knows its standing`() {
        // Ten same-country clubs with Brazil active meet section 1.9's
        // candidate minimum and fit division one exactly, so every club here
        // stands in division one and its division mirrors that standing.
        val clubs = (1..10).map { i ->
            WorldFixtures.club(ref = "c$i", squad = List(2) { WorldFixtures.player() })
        }
        val world = generateWorld(WorldFixtures.dataset(clubs), 5, activeLeagues = setOf(Country.BRAZIL))
        val club = world.clubs.first()
        assertEquals(Standing.InDivision(1), club.standing)
        assertEquals(1, club.division)
    }

    @Test
    fun `two clubs with different references generate differently`() {
        val world = generateWorld(dataset(listOf("um", "dois")), 5, activeLeagues = emptySet())
        assertNotEquals(
            world.club("um")?.squad?.map { it.strength },
            world.club("dois")?.squad?.map { it.strength },
        )
    }

    @Test
    fun `the world carries the seed it was built from`() {
        assertEquals(2026L, generateWorld(dataset(listOf("um")), 2026, activeLeagues = emptySet()).seed)
    }

    @Test
    fun `the world counts its players and finds its clubs`() {
        val world = generateWorld(dataset(listOf("um", "dois")), 1, activeLeagues = emptySet())
        assertEquals(8, world.playerCount)
        assertEquals("um", world.club("um")?.entry?.ref)
        assertNull(world.club("ausente"))
    }

    @Test
    fun `every player in the world comes out playable`() {
        val world = generateWorld(dataset(listOf("um", "dois", "tres")), 4321, activeLeagues = emptySet())
        for (club in world.clubs) {
            for (player in club.squad) {
                assertTrue(player.strength in 1..100, "${player.name} strength ${player.strength}")
                assertTrue(
                    player.abilities.all { it in 0..100 },
                    "${player.name} abilities ${player.abilities}",
                )
            }
        }
    }

    @Test
    fun `the club key is stable for the references a dataset uses`() {
        assertEquals("clube_bra".hashCode().toLong(), clubKey("clube_bra"))
        assertNotEquals(clubKey("um"), clubKey("dois"))
    }
}
