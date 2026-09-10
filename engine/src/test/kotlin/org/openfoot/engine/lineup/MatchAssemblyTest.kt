package org.openfoot.engine.lineup

import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.Competitor
import org.openfoot.engine.world.ScriptedInts
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.callUpNationalTeam
import org.openfoot.engine.world.generateWorld
import org.openfoot.engine.world.nationalTeamReputation
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Marking
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * assembleMatch is the bridge that lets a generated world play a match: it
 * turns two GeneratedClub into the MatchSetup simulateMatch takes, drawing
 * each side's formation and marking and running the automatic lineup over
 * each squad.
 */
class MatchAssemblyTest {

    /**
     * Eighteen players covering every position, enough to fill an eleven
     * under any of the twelve formations without the catch all doing all the
     * work, and enough left over to seat a full eleven cell bench too.
     */
    private fun squad(club: String, country: Int) = buildList {
        add(WorldFixtures.player(name = "$club goleiro 1", country = country, position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        add(WorldFixtures.player(name = "$club goleiro 2", country = country, position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        repeat(3) { i -> add(WorldFixtures.player(name = "$club zagueiro $i", country = country, position = Position.CENTREBACK, first = Trait.MARKING, second = Trait.TACKLING)) }
        repeat(3) { i -> add(WorldFixtures.player(name = "$club lateral $i", country = country, position = Position.FULLBACK, first = Trait.PACE, second = Trait.CROSSING)) }
        repeat(6) { i -> add(WorldFixtures.player(name = "$club meia $i", country = country, position = Position.MIDFIELDER, first = Trait.PASSING, second = Trait.PLAYMAKING)) }
        repeat(4) { i -> add(WorldFixtures.player(name = "$club atacante $i", country = country, position = Position.FORWARD, first = Trait.FINISHING, second = Trait.HEADING)) }
    }

    private fun dataset() = WorldFixtures.dataset(
        clubs = listOf(
            WorldFixtures.club(ref = "casa_bra", name = "Casa", country = Country.BRAZIL, squad = squad("Casa", Country.BRAZIL)),
            WorldFixtures.club(ref = "fora_esp", name = "Fora", country = WorldFixtures.SPAIN, squad = squad("Fora", WorldFixtures.SPAIN)),
        ),
    )

    /**
     * Builds a two club world from WorldFixtures and assembles a match
     * between them, putting the two clubs in the given order. swapped
     * places "fora_esp" at home and "casa_bra" away, the mirror of the
     * default order.
     */
    private fun assemble(seed: Long, swapped: Boolean = false): AssembledMatch {
        val data = dataset()
        val world = generateWorld(data, seed)

        val casa = world.club("casa_bra")!!
        val fora = world.club("fora_esp")!!

        val (home, away) = if (swapped) fora to casa else casa to fora

        return assembleMatch(
            home = home,
            away = away,
            dataset = data,
            kind = CompetitionKind.NATIONAL_LEAGUE,
            season = 1,
            rules = RuleSets.CLASSIC,
            availability = Availability.FULL_SQUAD,
            rng = SplitMix64Rng(seed),
        )
    }

    @Test
    fun `the marking bands of section 3 12 are drawn as written`() {
        val drawn = (0 until 100).map { drawMarking(ScriptedInts(it)) }
        val counts = drawn.groupingBy { it }.eachCount()

        assertEquals(5, counts[Marking.VERY_HEAVY], "five of a hundred are very heavy")
        assertEquals(65, counts[Marking.LIGHT], "light is the wide middle band, not the first")
        assertEquals(30, counts[Marking.HEAVY], "thirty are heavy")
    }

    @Test
    fun `both sides field eleven`() {
        val match = assemble(seed = 7L)

        assertEquals(11, match.setup.home.lineup.size, "home")
        assertEquals(11, match.setup.away.lineup.size, "away")
    }

    @Test
    fun `only the home side's context says it is at home`() {
        val match = assemble(seed = 7L)

        assertTrue(match.setup.home.context.isHomeSide, "home")
        assertFalse(match.setup.away.context.isHomeSide, "away")
    }

    @Test
    fun `each club draws from its own stream and not from its position`() {
        val asHome = assemble(seed = 7L)
        val asAway = assemble(seed = 7L, swapped = true)

        assertEquals(
            asHome.setup.home.lineup.map { it.id },
            asAway.setup.away.lineup.map { it.id },
            "the same club fields the same eleven whichever end of the fixture it is at",
        )
    }

    @Test
    fun `the same world and the same seed assemble the same match twice`() {
        val once = assemble(seed = 7L)
        val twice = assemble(seed = 7L)

        assertEquals(once.setup.home.marking, twice.setup.home.marking, "marking")
        assertEquals(
            once.setup.home.lineup.map { it.slot.value to it.id.value },
            twice.setup.home.lineup.map { it.slot.value to it.id.value },
            "the eleven and their cells",
        )
    }

    /**
     * A dataset's options.individualAbilities has to reach StrengthContext, or
     * every match would be simulated as though the option were always off,
     * regardless of what an installation actually has it set to. Both
     * assembles pick the same club, the same formation and the same lineup,
     * because neither depends on the option, so the same pitch cell is
     * compared under the two settings.
     *
     * Section 3.3's per slot weights sum to one (SlotWeights.kt), so a player
     * whose seven attributes all equalled his strength would rate the same
     * either way and this test would prove nothing. The precondition check
     * below confirms the picked player is not that case before the real
     * assertion runs.
     */
    @Test
    fun `individual abilities reach StrengthContext only when the dataset turns them on`() {
        val abilitiesOn = dataset().let { it.copy(options = it.options.copy(individualAbilities = true)) }
        val abilitiesOff = abilitiesOn.copy(options = abilitiesOn.options.copy(individualAbilities = false))

        val world = generateWorld(abilitiesOn, seed = 7L)
        val casa = world.club("casa_bra")!!
        val fora = world.club("fora_esp")!!

        fun matchRating(dataset: WorldDataset): Double {
            val match = assembleMatch(
                home = casa,
                away = fora,
                dataset = dataset,
                kind = CompetitionKind.NATIONAL_LEAGUE,
                season = 1,
                rules = RuleSets.CLASSIC,
                availability = Availability.FULL_SQUAD,
                rng = SplitMix64Rng(7L),
            )
            val keeper = match.setup.home.lineup.first()
            check(keeper.abilities.any { it != keeper.strength }) {
                "this test proves nothing unless the picked player's seven attributes differ from " +
                    "his overall strength: abilities ${keeper.abilities.toList()} vs strength " +
                    "${keeper.strength}"
            }
            return match.setup.home.ratingOf(keeper)
        }

        assertNotEquals(
            matchRating(abilitiesOn),
            matchRating(abilitiesOff),
            "the dataset's individualAbilities option must reach StrengthContext, or a player is " +
                "rated by his overall strength no matter what an installation's options.bcf says",
        )
    }

    /**
     * Two national teams, called up from the two club world above: every
     * Brazilian plays for casa_bra and every Spaniard for fora_esp, eighteen
     * of each, so both call-ups run on club players alone.
     */
    private fun nationalMatch(seed: Long): AssembledMatch {
        val data = dataset()
        val world = generateWorld(data, seed)
        return assembleMatch(
            home = callUpNationalTeam(world, data, Country.BRAZIL),
            away = callUpNationalTeam(world, data, WorldFixtures.SPAIN),
            dataset = data,
            kind = CompetitionKind.NATIONAL_TEAM,
            season = 1,
            rules = RuleSets.CLASSIC,
            availability = Availability.FULL_SQUAD,
            rng = SplitMix64Rng(seed),
        )
    }

    @Test
    fun `a national team side fields eleven countrymen who all carry the section 3 3 scale`() {
        val match = nationalMatch(seed = 3L)

        assertEquals(11, match.setup.home.lineup.size)
        assertTrue(match.setup.home.lineup.all { it.representsSideCountry }, "every man represents Brazil")
        assertTrue(match.homeBench.all { it.representsSideCountry }, "and so does the bench")
        assertEquals(Country.BRAZIL, match.setup.home.context.sideCountry)
        assertEquals(nationalTeamReputation(20), match.setup.home.context.sideReputation)
        assertEquals(WorldFixtures.SPAIN, match.setup.away.context.sideCountry)
    }

    @Test
    fun `a club side represents nobody, whatever its players' nationality`() {
        val match = assemble(seed = 3L)
        assertTrue(match.setup.home.lineup.none { it.representsSideCountry })
        assertTrue(match.homeBench.none { it.representsSideCountry })
    }

    @Test
    fun `a national team draws from its own stream whichever end it plays at`() {
        val data = dataset()
        val world = generateWorld(data, 3L)
        val brazil = callUpNationalTeam(world, data, Country.BRAZIL)
        val spain = callUpNationalTeam(world, data, WorldFixtures.SPAIN)

        fun assembleAs(home: Competitor, away: Competitor) = assembleMatch(
            home = home,
            away = away,
            dataset = data,
            kind = CompetitionKind.NATIONAL_TEAM,
            season = 1,
            rules = RuleSets.CLASSIC,
            availability = Availability.FULL_SQUAD,
            rng = SplitMix64Rng(3L),
        )

        assertEquals(
            assembleAs(brazil, spain).setup.home.lineup.map { it.id },
            assembleAs(spain, brazil).setup.away.lineup.map { it.id },
        )
    }
}
