package org.openfoot.engine.world

import org.openfoot.dataset.DatasetOptions
import org.openfoot.model.Attr
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The assembler, and with it the property the whole project rests on: a squad
 * is a function of its seed and nothing else.
 */
class SquadGenerationTest {

    private val options = DatasetOptions()

    @Test
    fun `a squad is a pure function of the club stream`() {
        val club = WorldFixtures.club()
        val data = WorldFixtures.dataset(listOf(club))

        val first = generateSquad(club, Standing.InDivision(1), data, options, SplitMix64Rng(42))
        val second = generateSquad(club, Standing.InDivision(1), data, options, SplitMix64Rng(42))

        assertEquals(first, second)
    }

    @Test
    fun `a different seed gives a different squad`() {
        val club = WorldFixtures.club()
        val data = WorldFixtures.dataset(listOf(club))

        val first = generateSquad(club, Standing.InDivision(1), data, options, SplitMix64Rng(42))
        val second = generateSquad(club, Standing.InDivision(1), data, options, SplitMix64Rng(43))

        assertNotEquals(first, second)
    }

    @Test
    fun `a player depends only on his own position in the squad`() {
        val entries = List(6) { WorldFixtures.player(name = "Jogador $it") }
        val club = WorldFixtures.club(squad = entries)
        val data = WorldFixtures.dataset(listOf(club))

        val whole = generateSquad(club, Standing.InDivision(1), data, options, SplitMix64Rng(7))

        val shortened = WorldFixtures.club(squad = entries.take(3))
        val prefix = generateSquad(
            shortened,
            Standing.InDivision(1),
            WorldFixtures.dataset(listOf(shortened)),
            options,
            SplitMix64Rng(7),
        )

        assertEquals(whole.take(3), prefix)
    }

    @Test
    fun `every generated player carries seven abilities`() {
        val club = WorldFixtures.club(squad = List(5) { WorldFixtures.player() })
        val squad = generateSquad(
            club,
            Standing.InDivision(1),
            WorldFixtures.dataset(listOf(club)),
            options,
            SplitMix64Rng(1),
        )

        assertEquals(5, squad.size)
        for (player in squad) {
            assertEquals(Attr.COUNT, player.abilities.size, player.name)
            assertTrue(player.strength > 0, player.name)
            assertTrue(player.contractDays in 210..239, player.name)
            assertTrue(player.salary >= 500, player.name)
            assertTrue(player.marketValue > 0, player.name)
        }
    }

    @Test
    fun `a starter comes out stronger than the same man on the bench`() {
        val club = WorldFixtures.club(
            squad = listOf(
                WorldFixtures.player(name = "Titular", starter = true),
                WorldFixtures.player(name = "Reserva", starter = false),
            ),
        )
        val squad = generateSquad(
            club,
            Standing.InDivision(1),
            WorldFixtures.dataset(listOf(club)),
            options,
            SplitMix64Rng(11),
        )
        assertTrue(
            squad[0].strength > squad[1].strength,
            "a starter takes eight to nine more than a substitute before anything else",
        )
    }

    @Test
    fun `the style follows the characteristics through to the finished player`() {
        val club = WorldFixtures.club(
            squad = listOf(
                WorldFixtures.player(position = Position.MIDFIELDER, first = org.openfoot.model.Trait.MARKING),
                WorldFixtures.player(position = Position.MIDFIELDER, first = org.openfoot.model.Trait.PASSING),
            ),
        )
        val squad = generateSquad(
            club,
            Standing.InDivision(1),
            WorldFixtures.dataset(listOf(club)),
            options,
            SplitMix64Rng(3),
        )
        assertEquals(PlayerStyle.DEFENSIVE, squad[0].style)
        assertEquals(PlayerStyle.OFFENSIVE, squad[1].style)
    }

    @Test
    fun `a club whose country is absent fails loudly`() {
        val club = WorldFixtures.club(country = 200)
        val data = WorldFixtures.dataset(listOf(club), countries = WorldFixtures.countries)

        val failure = assertFailsWith<IllegalArgumentException> {
            generateSquad(club, Standing.InDivision(1), data, options, SplitMix64Rng(1))
        }
        assertTrue(failure.message.orEmpty().contains("country level"))
    }

    @Test
    fun `a generated player adapts into a lineup entry the match engine accepts`() {
        val club = WorldFixtures.club(squad = listOf(WorldFixtures.player(position = Position.MIDFIELDER)))
        val player = generateSquad(
            club,
            Standing.InDivision(1),
            WorldFixtures.dataset(listOf(club)),
            options,
            SplitMix64Rng(5),
        ).single()

        val entry = player.inSlot(Slot(11), PlayerId(0))

        assertEquals(Slot(11), entry.slot)
        assertEquals(player.strength, entry.strength)
        assertEquals(player.position, entry.naturalPosition)
        assertEquals(player.abilities, entry.abilities.toList())
    }
}
