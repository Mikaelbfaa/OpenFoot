package org.openfoot.engine.world

import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.DatasetOptions
import org.openfoot.model.Attr
import org.openfoot.model.Country
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.Side
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.Trait
import org.openfoot.model.bfRound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Section 4.12, one step at a time: the team's reputation, the synthetic
 * free agents draw by draw, the trait table of 4.4.2, the ranking and the
 * quotas of the call-up, and the whole thing over a generated world.
 */
class NationalTeamTest {

    private val strongCountry = CountryEntry(index = Country.BRAZIL, name = "BRA", level = 20, continent = Country.SOUTH_AMERICA_CONTINENT)

    /** A generated player built by hand, so a pool can be shaped exactly. */
    private fun player(
        name: String,
        position: Position,
        strength: Int,
        side: Side = Side.RIGHT,
        style: PlayerStyle = if (position == Position.MIDFIELDER) PlayerStyle.OFFENSIVE else PlayerStyle.DEFENSIVE,
        star: Boolean = false,
        starter: Boolean = false,
        age: Int = 25,
        country: Int = Country.BRAZIL,
    ) = Player(
        name = name,
        age = age,
        country = country,
        position = position,
        side = side,
        firstTrait = if (position == Position.GOALKEEPER) Trait.REFLEXES else Trait.STAMINA,
        secondTrait = if (position == Position.GOALKEEPER) Trait.POSITIONING else Trait.HEADING,
        starter = starter,
        star = star,
        topWorld = false,
        talent = 6,
        style = style,
        strength = strength,
        abilities = List(Attr.COUNT) { 0 },
        contractDays = 200,
        salary = 0,
        marketValue = 0,
    )

    /** A pool that fills every cell of step 4 exactly, twenty three men, strongest first. */
    private fun fullPool(): List<Player> = buildList {
        repeat(3) { add(player("gk$it", Position.GOALKEEPER, 90 - it)) }
        repeat(2) { add(player("lat-d$it", Position.FULLBACK, 80 - it, Side.RIGHT)) }
        repeat(2) { add(player("lat-e$it", Position.FULLBACK, 78 - it, Side.LEFT)) }
        repeat(2) { add(player("zag-d$it", Position.CENTREBACK, 76 - it, Side.RIGHT)) }
        repeat(2) { add(player("zag-e$it", Position.CENTREBACK, 74 - it, Side.LEFT)) }
        repeat(2) { add(player("arm-d$it", Position.MIDFIELDER, 72 - it, Side.RIGHT, PlayerStyle.OFFENSIVE)) }
        repeat(3) { add(player("arm-e$it", Position.MIDFIELDER, 70 - it, Side.LEFT, PlayerStyle.OFFENSIVE)) }
        repeat(2) { add(player("vol-d$it", Position.MIDFIELDER, 67 - it, Side.RIGHT, PlayerStyle.DEFENSIVE)) }
        add(player("vol-e0", Position.MIDFIELDER, 65, Side.LEFT, PlayerStyle.DEFENSIVE))
        repeat(2) { add(player("ata-d$it", Position.FORWARD, 64 - it, Side.RIGHT)) }
        repeat(2) { add(player("ata-e$it", Position.FORWARD, 62 - it, Side.LEFT)) }
    }

    @Test
    fun `the reputation ladder reads the country level`() {
        assertEquals(5, nationalTeamReputation(20))
        assertEquals(5, nationalTeamReputation(25))
        assertEquals(4, nationalTeamReputation(19))
        assertEquals(3, nationalTeamReputation(18))
        assertEquals(3, nationalTeamReputation(17))
        assertEquals(2, nationalTeamReputation(16))
        assertEquals(2, nationalTeamReputation(15))
        assertEquals(1, nationalTeamReputation(14))
        assertEquals(1, nationalTeamReputation(1))
    }

    @Test
    fun `both sufficiency tests must fail before free agents are generated`() {
        fun squad(outfield: Int, keepers: Int) =
            List(outfield) { player("l$it", Position.MIDFIELDER, 50) } +
                List(keepers) { player("g$it", Position.GOALKEEPER, 50) }

        assertFalse(needsFreeAgents(squad(15, 2), emptyList()))
        assertTrue(needsFreeAgents(squad(14, 2), emptyList()))
        assertTrue(needsFreeAgents(squad(15, 1), emptyList()))
        // Fifteen with a club fails the first test, and sixteen counting a
        // free agent passes the second, so nothing is generated.
        assertFalse(needsFreeAgents(squad(14, 2), squad(2, 0)))
        assertTrue(needsFreeAgents(squad(14, 2), squad(1, 0)))
    }

    @Test
    fun `a synthetic keeper is built draw by draw in the order the spec gives`() {
        // Strength rand(8); the keeper row of 4.2, seven draws; the two
        // positioning terms of the keeper bonus, both firing because both
        // characteristics are at index zero; the pair rand(6); talent
        // rand(4); age rand(12); side rand(2). Fourteen draws in all.
        val draws = ScriptedInts(3, 1, 6, 2, 3, 0, 1, 2, 4, 1, 2, 3, 11, 1)
        val keeper = generateFreeAgent(strongCountry, Position.GOALKEEPER, "BRA-0001", DatasetOptions(), draws)

        val mapped = 25
        val seed = mapped - 4
        val band = 7
        assertEquals(mapped - 5 + 3, keeper.strength)
        assertEquals(keeper.strength + 1, keeper.abilities[Attr.GOALKEEPING])
        assertEquals(seed + 6, keeper.abilities[Attr.PACE])
        assertEquals(seed + 2 + 2 + 4 + 1, keeper.abilities[Attr.TECHNIQUE])
        assertEquals(seed + 3, keeper.abilities[Attr.PASSING])
        assertEquals(band + 0, keeper.abilities[Attr.TACKLING])
        assertEquals(band + 1, keeper.abilities[Attr.PLAYMAKING])
        assertEquals(band + 2, keeper.abilities[Attr.FINISHING])
        assertEquals(Trait.REFLEXES to Trait.POSITIONING, keeper.firstTrait to keeper.secondTrait)
        assertEquals(7 + 3, keeper.talent)
        assertEquals(18 + 11, keeper.age)
        assertEquals(Side.LEFT, keeper.side)
        assertEquals(PlayerStyle.DEFENSIVE, keeper.style)
        assertEquals(FREE_AGENT_CONTRACT_DAYS, keeper.contractDays)
        assertEquals(Country.BRAZIL, keeper.country)
        assertEquals("BRA-0001", keeper.name)
        assertFalse(keeper.starter)
        assertFalse(keeper.star)
        assertEquals(14, draws.draws)
    }

    @Test
    fun `a synthetic midfielder is built on the holding row whatever pair he then draws`() {
        // Strength; the defensive midfielder row, seven draws; no bonus draw
        // at all, since index zero is no outfield characteristic; then the
        // pair, row zero of the midfielder table, which is playmaking and
        // passing and makes him offensive by 4.3; talent; age; side.
        val draws = ScriptedInts(0, 2, 1, 2, 3, 4, 5, 3, 0, 0, 0, 0)
        val midfielder = generateFreeAgent(strongCountry, Position.MIDFIELDER, "BRA-0012", DatasetOptions(), draws)

        val strength = 25 - 5
        val seed = 21
        assertEquals(strength, midfielder.strength)
        assertEquals(bfRound(strength * 0.7) + 2, midfielder.abilities[Attr.TACKLING])
        assertEquals(seed + 4, midfielder.abilities[Attr.PLAYMAKING])
        assertEquals(Trait.PLAYMAKING to Trait.PASSING, midfielder.firstTrait to midfielder.secondTrait)
        assertEquals(PlayerStyle.OFFENSIVE, midfielder.style)
        assertEquals(12, draws.draws)
    }

    @Test
    fun `the batch is twenty men in position order, named by country and counter`() {
        val batch = generateFreeAgents(strongCountry, DatasetOptions(), SplitMix64Rng(7))

        assertEquals(20, batch.size)
        assertEquals(
            List(3) { Position.GOALKEEPER } + List(4) { Position.FULLBACK } + List(4) { Position.CENTREBACK } +
                List(5) { Position.MIDFIELDER } + List(4) { Position.FORWARD },
            batch.map { it.position },
        )
        assertEquals("BRA-0001", batch.first().name)
        assertEquals("BRA-0020", batch.last().name)
        assertTrue(batch.all { it.strength in 20..27 }, batch.map { it.strength }.toString())
        assertTrue(batch.all { it.age in 18..29 && it.talent in 7..10 })
        assertTrue(batch.all { it.style == playerStyle(it.position, it.firstTrait, it.secondTrait) })
        assertEquals(batch, generateFreeAgents(strongCountry, DatasetOptions(), SplitMix64Rng(7)))
    }

    @Test
    fun `a weak country's batch sits five below its own level`() {
        val weak = CountryEntry(index = 900, name = "FRA", level = 11, continent = 1)
        val batch = generateFreeAgents(weak, DatasetOptions(), SplitMix64Rng(3))
        assertTrue(batch.all { it.strength in 6..13 }, batch.map { it.strength }.toString())
        assertEquals("FRA-0007", batch[6].name)
    }

    @Test
    fun `the trait draw reads one row of the position's table`() {
        assertEquals(Trait.MARKING to Trait.CROSSING, drawTraitPair(Position.FULLBACK, ScriptedInts(4)))
        assertEquals(Trait.REFLEXES to Trait.POSITIONING, drawTraitPair(Position.GOALKEEPER, ScriptedInts(2)))
        assertEquals(Trait.PACE to Trait.DRIBBLING, drawTraitPair(Position.FORWARD, ScriptedInts(11)))
        assertEquals(6, traitPairRows(Position.GOALKEEPER).size)
        assertEquals(7, traitPairRows(Position.FULLBACK).size)
        assertEquals(12, traitPairRows(Position.CENTREBACK).size)
        assertEquals(19, traitPairRows(Position.MIDFIELDER).size)
        assertEquals(12, traitPairRows(Position.FORWARD).size)
    }

    @Test
    fun `no row of any table repeats a characteristic`() {
        for (position in Position.entries) {
            assertTrue(traitPairRows(position).none { it.first == it.second }, position.toString())
        }
    }

    @Test
    fun `the tables give the style proportions section 4 4 2 states`() {
        fun styles(position: Position) = traitPairRows(position).map { playerStyle(position, it.first, it.second) }

        assertEquals(3, styles(Position.FULLBACK).count { it == PlayerStyle.OFFENSIVE })
        assertEquals(9, styles(Position.MIDFIELDER).count { it == PlayerStyle.DEFENSIVE })
        assertEquals(4, styles(Position.FORWARD).count { it == PlayerStyle.WINGER })
        assertEquals(0, styles(Position.FORWARD).count { it == PlayerStyle.DEFENSIVE })
    }

    @Test
    fun `a full pool seats every cell and comes out in position and style order`() {
        val pool = fullPool().shuffled(kotlin.random.Random(1))
        val listed = selectSquad(pool).map { pool[it] }

        assertEquals(SQUAD_SIZE, listed.size)
        assertEquals(
            listed.sortedWith(
                compareBy<Player> { it.position.ordinal }.thenBy { it.style.ordinal }.thenByDescending { it.strength },
            ),
            listed,
        )
        assertEquals(3, listed.count { it.position == Position.GOALKEEPER })
        assertEquals(5, listed.count { it.position == Position.MIDFIELDER && it.style == PlayerStyle.OFFENSIVE })
        assertEquals(3, listed.count { it.position == Position.MIDFIELDER && it.style == PlayerStyle.DEFENSIVE })
    }

    @Test
    fun `a full cell skips the weaker man even when he outranks a man of another cell`() {
        val pool = fullPool() + player("lat-d-extra", Position.FULLBACK, 99, Side.RIGHT)
        val listed = selectSquad(pool).map { pool[it].name }

        assertTrue("lat-d-extra" in listed)
        assertFalse("lat-d1" in listed, listed.toString())
        assertEquals(SQUAD_SIZE, listed.size)
    }

    @Test
    fun `the ranking is by strength, then star, then pool order`() {
        val pool = fullPool() + listOf(
            player("gk-plain", Position.GOALKEEPER, 95),
            player("gk-star", Position.GOALKEEPER, 95, star = true),
        )
        val listed = selectSquad(pool).map { pool[it].name }
        // Three keepers: the two at 95, star first, then gk0 at 90.
        assertEquals(listOf("gk-star", "gk-plain", "gk0"), listed.filter { it.startsWith("gk") })
    }

    @Test
    fun `a pool short of twenty three is called in full`() {
        val pool = fullPool().take(10) + player("lat-d-extra", Position.FULLBACK, 1, Side.RIGHT) +
            player("lat-d-more", Position.FULLBACK, 2, Side.RIGHT)
        val listed = selectSquad(pool)
        assertEquals(pool.indices.toSet(), listed.toSet())
    }

    @Test
    fun `a pool of twenty three with an empty cell leaves the list short and nothing completes it`() {
        // Every left fullback is replaced by a right one: the two left cells
        // stay empty, the extra right fullbacks are skipped, and the list has
        // twenty one men. OPEN-QUESTIONS item 64.
        val pool = fullPool().map {
            if (it.position == Position.FULLBACK && it.side == Side.LEFT) it.copy(side = Side.RIGHT) else it
        }
        assertEquals(SQUAD_SIZE, pool.size)
        assertEquals(SQUAD_SIZE - 2, selectSquad(pool).size)
    }

    private fun clubWithPlayers(ref: String, players: List<Player>) = GeneratedClub(
        entry = WorldFixtures.club(ref = ref),
        standing = Standing.InDivision(1),
        squad = players,
        designated = deriveDesignated(players, DesignationEnergy.FULL_SQUAD),
    )

    @Test
    fun `a country with enough club players calls no free agent and enumerates clubs by ref`() {
        val pool = fullPool()
        val world = World(
            seed = 5,
            clubs = listOf(
                clubWithPlayers("zebra_bra", pool.drop(12)),
                clubWithPlayers("alfa_bra", pool.take(12)),
            ),
        )
        val team = callUpNationalTeam(world, WorldFixtures.dataset(), Country.BRAZIL)

        assertEquals(0, team.freeAgentCount)
        assertEquals(SQUAD_SIZE, team.squad.size)
        assertEquals(5, team.reputation)
        assertEquals(20, team.level)
        assertTrue(team.origins.all { it == "alfa_bra" || it == "zebra_bra" })
        assertEquals(team.squad.sortedWith(compareByDescending<Player> { it.strength }.thenByDescending { it.age }), team.squad)
        val taker = assertNotNull(team.designated.taker)
        assertTrue(team.squad[taker.value].position != Position.GOALKEEPER)
    }

    @Test
    fun `a country short of players gets the synthetic batch, marked as free agents`() {
        val data = WorldFixtures.dataset(
            clubs = listOf(
                WorldFixtures.club(
                    ref = "unico_bra",
                    squad = List(4) { WorldFixtures.player(name = "jogador $it") },
                ),
            ),
        )
        val world = generateWorld(data, 11, activeLeagues = emptySet())
        val team = callUpNationalTeam(world, data, Country.BRAZIL)

        // Four club midfielders, all offensive and right sided by the fixture's
        // passing and stamina, plus twenty free agents make a pool of twenty
        // four, so step 5 never runs: the two strongest club men fill the right
        // playmaker cell, the other two are skipped, and free agents fill what
        // their drawn sides and styles allow.
        assertTrue(team.freeAgentCount > 0)
        assertEquals(2, team.origins.count { it == "unico_bra" })
        assertTrue(team.squad.size <= SQUAD_SIZE)
        assertTrue(team.squad.all { it.country == Country.BRAZIL })
        assertEquals(team, callUpNationalTeam(world, data, Country.BRAZIL))
    }

    @Test
    fun `a country nobody plays for is a batch of free agents and nothing else`() {
        val data = WorldFixtures.dataset()
        val world = generateWorld(data, 11, activeLeagues = emptySet())
        val team = callUpNationalTeam(world, data, WorldFixtures.SPAIN)

        assertEquals(20, team.squad.size)
        assertEquals(20, team.freeAgentCount)
        assertTrue(team.squad.all { it.name.startsWith("Espanha-") })
    }
}
