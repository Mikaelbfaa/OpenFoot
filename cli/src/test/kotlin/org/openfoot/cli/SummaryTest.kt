package org.openfoot.cli

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.PlayerEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The summary exists to be diffed between runs, so what matters is that it says
 * the same thing every time and orders itself by something stable.
 */
class SummaryTest {

    private fun dataset(refs: List<String>) = WorldDataset(
        countries = listOf(CountryEntry(index = Country.BRAZIL, name = "Brasil", level = 20)),
        clubs = refs.map { ref ->
            ClubEntry(
                ref = ref,
                name = ref,
                country = Country.BRAZIL,
                level = 18,
                reputation = 4,
                division = 1,
                squad = List(3) { index ->
                    PlayerEntry(
                        name = "$ref jogador $index",
                        age = 25,
                        country = Country.BRAZIL,
                        position = Position.MIDFIELDER,
                        firstTrait = Trait.PASSING,
                        secondTrait = Trait.STAMINA,
                        starter = index == 0,
                    )
                },
            )
        },
    )

    @Test
    fun `the same world summarises identically every time`() {
        val world = generateWorld(dataset(listOf("um", "dois")), 42)
        assertEquals(summarise(world), summarise(world))
        assertEquals(summarise(generateWorld(dataset(listOf("um", "dois")), 42)), summarise(world))
    }

    @Test
    fun `clubs are listed by reference and not in dataset order`() {
        val summary = summarise(generateWorld(dataset(listOf("zeta", "alfa")), 42))
        val alfa = summary.indexOf("alfa")
        val zeta = summary.indexOf("zeta")
        assertTrue(alfa in 1 until zeta, "expected alfa before zeta in:\n$summary")
    }

    @Test
    fun `the header reports the seed and the counts`() {
        val summary = summarise(generateWorld(dataset(listOf("um", "dois")), 7))
        assertTrue(summary.contains("seed      7"), summary)
        assertTrue(summary.contains("clubs     2"), summary)
        assertTrue(summary.contains("players   6"), summary)
    }

    @Test
    fun `the strength line brackets the squad`() {
        val world = generateWorld(dataset(listOf("um")), 3)
        val strengths = world.clubs.single().squad.map { it.strength }.sorted()
        val summary = summarise(world)
        assertTrue(
            summary.contains("min ${strengths.first()}") && summary.contains("max ${strengths.last()}"),
            summary,
        )
    }

    @Test
    fun `a dataset ordering change does not change the summary`() {
        assertEquals(
            summarise(generateWorld(dataset(listOf("um", "dois")), 42)),
            summarise(generateWorld(dataset(listOf("dois", "um")), 42)),
        )
    }

    @Test
    fun `a club too few to fill a division prints the reputation path`() {
        val world = generateWorld(dataset(listOf("um", "dois")), 42)
        val best = world.club("um")!!.squad.maxByOrNull { it.strength }
        assertEquals(
            "  um  level 18  rep  players 3  best ${best?.strength ?: 0} ${best?.name.orEmpty()}",
            summarise(world).lineSequence().first { it.trim().startsWith("um ") },
        )
    }

    @Test
    fun `a club large enough to fill a division prints its division`() {
        val refs = (1..10).map { "clube$it" }
        val world = generateWorld(dataset(refs), 42)
        val target = world.club("clube1")!!
        val best = target.squad.maxByOrNull { it.strength }
        assertEquals(
            "  clube1  level 18  div 1  players 3  best ${best?.strength ?: 0} ${best?.name.orEmpty()}",
            summarise(world).lineSequence().first { it.trim().startsWith("clube1 ") },
        )
    }
}
