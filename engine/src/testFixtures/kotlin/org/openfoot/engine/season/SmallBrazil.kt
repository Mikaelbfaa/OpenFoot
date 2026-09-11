package org.openfoot.engine.season

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.PlayerEntry
import org.openfoot.dataset.StateChampionshipEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.Trait

/**
 * A small Brazil written by hand as open data, for the tests that play whole
 * seasons with state championships: the two season golden vector of the
 * command line module and the engine's two season integration test play
 * this one dataset, so the invariants the engine test asserts are the ones
 * the pinned printout shows. Nothing here is read from an importer or from
 * disk, and no name is a real club's.
 *
 * Forty Brazilian clubs, ranked one to forty. The ref is a state prefix and
 * the rank: sp for Sao Paulo (state 25), rn for Rio Grande do Norte (state
 * 19), br for a club of no state. Levels fall one step every four ranks,
 * twenty for ranks one to four down to eleven for ranks thirty seven to
 * forty, so every division boundary of section 1.9's pyramid falls between
 * two levels and no tiebreak draw can move a club across one.
 *
 * The league configuration seats four divisions of eight: ranks one to eight
 * in division one, nine to sixteen in two, seventeen to twenty four in
 * three, twenty five to thirty two in four, and thirty three to forty in the
 * reserve. Divisions one to three play two turns, fourteen rounds, and
 * relegate two; division three is configured with a relegation playoff,
 * directRelegated one, which section 1.12 overrides while the states feed
 * the fourth, so it earns no note. Division four holds two groups of four,
 * each playing its own two turn round robin, six rounds, and sends its top
 * two of each group to a two legged final phase of four, two rounds, four
 * dates. With the state championships on, the level seated fourth is taken
 * out at the opening (OPEN-QUESTIONS item 113) and rebuilt at its seated
 * size of eight when the season's last state competition closes.
 *
 * Sao Paulo holds eighteen clubs, ranks 1, 3, 5, 7, 9, 11, 13, 15, 17, 19,
 * 21, 23, 25, 27, 29, 33, 37 and 39, configured on preset 7 with the legs
 * of Sao Paulo's own file row in FORMAT-SPEC (quarter and semi finals single,
 * the final two legged): its sixteen best fill one division of four groups,
 * and the two of rank 37 and 39, both level eleven, form its reserve. Rio
 * Grande do Norte holds ten, ranks 2, 6, 10, 14, 18, 22, 26, 30, 34 and 38,
 * on preset 1, eight clubs in two turns with four through to a two legged
 * knockout: ranks 34 and 38 are its reserve. The other twelve are of no
 * state.
 *
 * Season one's fourth is therefore known before a ball is kicked: the
 * clubs of ranks twenty five to forty that sit in a state division are
 * sp25, sp27, sp29, sp33, rn26 and rn30, six names for eight places, and
 * section 1.12's step 3 completes the list with the first stateless
 * Brazilian clubs in dataset order outside divisions one to three, br28 and
 * br31.
 *
 * Every club fields a full, legal squad of eighteen: two keepers, three
 * centrebacks, three fullbacks, six midfielders and four forwards, all
 * twenty five years old.
 */
object SmallBrazil {

    const val YEAR = 2026

    val activeLeagues: Set<Int> = setOf(Country.BRAZIL)

    const val SAO_PAULO = 25

    const val RIO_GRANDE_DO_NORTE = 19

    private val statesByRank: List<Int?> = listOf(
        SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null, SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null,
        SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null, SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null,
        SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null, SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null,
        SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null, SAO_PAULO, RIO_GRANDE_DO_NORTE, null, null,
        SAO_PAULO, RIO_GRANDE_DO_NORTE, null, null, SAO_PAULO, RIO_GRANDE_DO_NORTE, SAO_PAULO, null,
    )

    private fun prefix(state: Int?): String = when (state) {
        SAO_PAULO -> "sp"
        RIO_GRANDE_DO_NORTE -> "rn"
        else -> "br"
    }

    private fun player(name: String, position: Position, first: Trait, second: Trait) =
        PlayerEntry(name = name, age = 25, country = Country.BRAZIL, position = position, firstTrait = first, secondTrait = second)

    private fun squad(ref: String): List<PlayerEntry> = buildList {
        repeat(2) { add(player("$ref g${it + 1}", Position.GOALKEEPER, Trait.REFLEXES, Trait.POSITIONING)) }
        repeat(3) { add(player("$ref z${it + 1}", Position.CENTREBACK, Trait.MARKING, Trait.TACKLING)) }
        repeat(3) { add(player("$ref l${it + 1}", Position.FULLBACK, Trait.PACE, Trait.CROSSING)) }
        repeat(6) { add(player("$ref m${it + 1}", Position.MIDFIELDER, Trait.PASSING, Trait.PLAYMAKING)) }
        repeat(4) { add(player("$ref a${it + 1}", Position.FORWARD, Trait.FINISHING, Trait.HEADING)) }
    }

    private val clubs: List<ClubEntry> = statesByRank.mapIndexed { index, state ->
        val rank = index + 1
        val ref = prefix(state) + rank.toString().padStart(2, '0')
        ClubEntry(
            ref = ref,
            name = ref,
            country = Country.BRAZIL,
            level = 20 - index / 4,
            reputation = 3,
            state = state,
            squad = squad(ref),
        )
    }

    private fun division(number: Int) =
        LeagueConfigEntry(country = Country.BRAZIL, division = number, teamCount = 8, relegated = 2, turns = 2, penaltiesTiebreak = true)

    val dataset = WorldDataset(
        countries = listOf(CountryEntry(index = Country.BRAZIL, name = "Brasil", level = 20, continent = Country.SOUTH_AMERICA_CONTINENT)),
        clubs = clubs,
        leagues = listOf(
            division(1),
            division(2),
            division(3).copy(directRelegated = 1),
            division(4).copy(groups = 2, knockoutQualifiers = 2),
        ),
        stateChampionships = listOf(
            StateChampionshipEntry(state = SAO_PAULO, division = 1, preset = 7, penaltiesTiebreak = true, twoLeggedRounds = listOf(false, false, true)),
            StateChampionshipEntry(state = RIO_GRANDE_DO_NORTE, division = 1, preset = 1, penaltiesTiebreak = true, twoLeggedRounds = listOf(true, true, true)),
        ),
    )
}
