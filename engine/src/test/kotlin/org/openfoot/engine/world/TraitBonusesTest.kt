package org.openfoot.engine.world

import org.openfoot.model.Attr
import org.openfoot.model.Position
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bonus list of section 4.2, one characteristic at a time, on a flat set of
 * abilities so the bonus itself is the only thing being read.
 *
 * The standing example keeps a club quality seed of fifteen and a band of
 * seven, so the two are easy to tell apart in an expected value.
 */
class TraitBonusesTest {

    private val seed = 15
    private val band = 7
    private val flat = 10

    private fun bonused(
        trait: Trait,
        second: Trait = trait,
        position: Position = Position.MIDFIELDER,
        vararg draws: Int,
    ): IntArray {
        val abilities = IntArray(Attr.COUNT) { flat }
        applyTraitBonuses(abilities, position, trait, second, seed, band, ScriptedInts(*draws))
        return abilities
    }

    @Test
    fun `playmaking lifts both playmaking and passing by the band`() {
        val row = bonused(Trait.PLAYMAKING, draws = intArrayOf(0, 0))
        assertEquals(flat + band, row[Attr.PLAYMAKING])
        assertEquals(flat + band, row[Attr.PASSING])
    }

    @Test
    fun `playmaking draws separately for each of its two abilities`() {
        val row = bonused(Trait.PLAYMAKING, draws = intArrayOf(1, 4))
        assertEquals(flat + band + 1, row[Attr.PLAYMAKING])
        assertEquals(flat + band + 4, row[Attr.PASSING])
    }

    @Test
    fun `heading and crossing are flat bonuses that ignore the club`() {
        assertEquals(flat + 2, bonused(Trait.HEADING, draws = intArrayOf(0))[Attr.FINISHING])
        assertEquals(flat + 4, bonused(Trait.HEADING, draws = intArrayOf(2))[Attr.FINISHING])
        assertEquals(flat + 2, bonused(Trait.CROSSING, draws = intArrayOf(0))[Attr.PASSING])
    }

    @Test
    fun `the three tackling characteristics differ in spread and in base`() {
        assertEquals(flat + band, bonused(Trait.TACKLING, draws = intArrayOf(0))[Attr.TACKLING])
        assertEquals(flat + band + 4, bonused(Trait.MARKING, draws = intArrayOf(4))[Attr.TACKLING])
        assertEquals(flat + 3, bonused(Trait.STAMINA, draws = intArrayOf(0))[Attr.TACKLING])
    }

    @Test
    fun `dribbling and finishing take the band`() {
        assertEquals(flat + band, bonused(Trait.DRIBBLING, draws = intArrayOf(0))[Attr.TECHNIQUE])
        assertEquals(flat + band, bonused(Trait.FINISHING, draws = intArrayOf(0))[Attr.FINISHING])
    }

    @Test
    fun `pace is the one bonus built from the quality seed for everyone`() {
        assertEquals(flat + seed, bonused(Trait.PACE, draws = intArrayOf(0))[Attr.PACE])
        assertEquals(
            flat + seed,
            bonused(Trait.PACE, position = Position.FORWARD, draws = intArrayOf(0))[Attr.PACE],
        )
    }

    @Test
    fun `a forward reads the quality seed where others read the band`() {
        val forward = bonused(Trait.PLAYMAKING, position = Position.FORWARD, draws = intArrayOf(0, 0))
        assertEquals(flat + seed, forward[Attr.PLAYMAKING])
        assertEquals(flat + seed, forward[Attr.PASSING])

        val passer = bonused(Trait.PASSING, position = Position.FORWARD, draws = intArrayOf(0))
        assertEquals(flat + seed, passer[Attr.PASSING])
    }

    @Test
    fun `the forward substitution touches only the two creative characteristics`() {
        val forward = bonused(Trait.TACKLING, position = Position.FORWARD, draws = intArrayOf(0))
        assertEquals(flat + band, forward[Attr.TACKLING])
    }

    @Test
    fun `a repeated characteristic is worth exactly one bonus`() {
        val once = bonused(Trait.MARKING, second = Trait.STAMINA, draws = intArrayOf(0, 0))
        val twice = bonused(Trait.MARKING, second = Trait.MARKING, draws = intArrayOf(0))
        assertEquals(flat + band, twice[Attr.TACKLING])
        assertEquals(flat + band + 3, once[Attr.TACKLING])
    }

    @Test
    fun `two characteristics both fire and draw in the order the spec lists them`() {
        val row = bonused(Trait.PACE, second = Trait.DRIBBLING, draws = intArrayOf(1, 2))
        assertEquals(flat + band + 1, row[Attr.TECHNIQUE])
        assertEquals(flat + seed + 2, row[Attr.PACE])
    }

    @Test
    fun `a keeper reads his first characteristic for the large draw and his second for the small one`() {
        // Positioning first, rushing out second: both are positional, so the
        // technique term fires twice, 2 + rand(5) for the first and rand(2)
        // for the second, in that order.
        val positional = bonused(
            Trait.POSITIONING,
            second = Trait.RUSHING_OUT,
            position = Position.GOALKEEPER,
            draws = intArrayOf(4, 1),
        )
        assertEquals(flat + 2 + 4 + 1, positional[Attr.TECHNIQUE])

        val reflexes = bonused(
            Trait.REFLEXES,
            second = Trait.PENALTY_SAVING,
            position = Position.GOALKEEPER,
            draws = intArrayOf(3, 1),
        )
        assertEquals(flat + 2 + 3, reflexes[Attr.PACE])
        assertEquals(flat + 1, reflexes[Attr.GOALKEEPING])

        val saving = bonused(
            Trait.PENALTY_SAVING,
            second = Trait.REFLEXES,
            position = Position.GOALKEEPER,
            draws = intArrayOf(1, 2),
        )
        assertEquals(flat + 1, saving[Attr.PACE])
        assertEquals(flat + 1 + 2, saving[Attr.GOALKEEPING])
    }

    @Test
    fun `a keeper carrying the same positional characteristic twice collects both terms`() {
        // This is the shape section 4.12's synthetic keeper has, both
        // characteristics at index zero when the generator runs.
        val row = bonused(Trait.POSITIONING, position = Position.GOALKEEPER, draws = intArrayOf(0, 1))
        assertEquals(flat + 2 + 0 + 1, row[Attr.TECHNIQUE])
        assertEquals(flat, row[Attr.PACE])
        assertEquals(flat, row[Attr.GOALKEEPING])
    }

    @Test
    fun `the keeper list is chosen by position, so an outfielder at index zero earns nothing`() {
        val row = bonused(Trait.POSITIONING, position = Position.MIDFIELDER)
        assertEquals(IntArray(Attr.COUNT) { flat }.toList(), row.toList())
    }

    @Test
    fun `the outfield list is chosen by position, so a keeper never reads it`() {
        val row = bonused(Trait.PACE, second = Trait.MARKING, position = Position.GOALKEEPER)
        assertEquals(IntArray(Attr.COUNT) { flat }.toList(), row.toList())
    }

    @Test
    fun `the ceiling clamps an ability that would pass one hundred`() {
        val abilities = IntArray(Attr.COUNT) { 99 }
        applyTraitBonuses(
            abilities,
            Position.MIDFIELDER,
            Trait.MARKING,
            Trait.STAMINA,
            seed,
            band,
            ScriptedInts(4, 2),
        )
        assertEquals(ABILITY_CEILING, abilities[Attr.TACKLING])
    }
}
