package org.openfoot.engine.world

import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.DatasetOptions
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Country
import org.openfoot.model.Designated
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.Rng
import org.openfoot.model.SeedDomain
import org.openfoot.model.Side
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.Trait
import org.openfoot.model.rand

/**
 * A national team, called up from a generated world the way section 4.12
 * describes.
 *
 * The original keeps no national squad in any file: the team is created on
 * demand from the country's level, and its squad is a list of players who
 * still belong to their clubs, plus the free agents the call-up generated for
 * a country short of real players. This value is the result of one such
 * call-up.
 *
 * The squad is stored in the order the original leaves it in, strength
 * descending and then age descending, because that order is what the
 * automatic lineup of section 5.4 breaks its ties by. origins runs parallel
 * to it and names the club each man was called from, or null for a free
 * agent, so a reader of the list can tell the two apart; nothing in the
 * simulation reads it.
 *
 * The designations are the ones section 5.6 says a call-up recomputes: the set
 * piece taker over the called list, and no corner taker, since the AI never
 * sets one. The taker indexes this squad.
 *
 * As a competitor the team's key carries a colon, which no file name and so
 * no club reference can contain, so a national team's stream never collides
 * with a club's. Every man of the squad has the team's nationality by
 * construction, so representing the country marks all of them for section
 * 3.3's national team scale.
 */
@SpecRef("4.12")
data class NationalTeam(
    override val country: Int,
    val level: Int,
    @property:SpecRef("4.12") override val reputation: Int,
    override val squad: List<Player>,
    val origins: List<String?>,
    @property:SpecRef("5.6") override val designated: Designated,
) : Competitor {
    /** How many of the called men are free agents rather than club players. */
    val freeAgentCount: Int get() = origins.count { it == null }

    override val key: String get() = nationalTeamKey(country)

    override val representedCountry: Int? get() = country
}

/** The competitor key of a country's national team. */
@SpecRef("4.12")
fun nationalTeamKey(country: Int): String = "national:$country"

/**
 * The reputation a national team is born with, read off the country's level.
 *
 * This is what section 3.3's national team scale and the ability band of the
 * free agent generator read. It never falls below one, which is why the
 * reputation zero row of section 4.4 is unreachable for a national team.
 */
@SpecRef("4.12")
fun nationalTeamReputation(countryLevel: Int): Int = when {
    countryLevel >= 20 -> 5
    countryLevel == 19 -> 4
    countryLevel >= 17 -> 3
    countryLevel >= 15 -> 2
    else -> 1
}

/**
 * Calls up one country's national team from a generated world.
 *
 * The steps are section 4.12's, in its order. The pool is every player of the
 * country's nationality who has a club, enumerated club by club in reference
 * order and squad order within the club, followed by the free agents in
 * creation order. That enumeration is OPEN-QUESTIONS item 67's data defined
 * stand in for the original's world list order, and it matters because the
 * ranking that follows is stable: two men of equal strength and star mark are
 * called in enumeration order.
 *
 * Free agents are generated only when both sufficiency tests fail, and they
 * are generated here rather than stored anywhere, because v0.2 has no state
 * that outlives a call-up: the pool the original keeps between call-ups is,
 * in a world that is generated and played once, exactly the batch this
 * call-up generates. Their stream is forked off the world seed by country, so
 * a country's free agents are the same whichever call-up asks for them and
 * whatever else the world has drawn.
 *
 * The set piece taker is derived over the list in the order section 4.12
 * step 6 leaves it before the captain's sort, position first, because that
 * is the order the original's taker routine sorts from; the stored squad is
 * then the captain's order, and the taker's index is carried across to it.
 */
@SpecRef("4.12")
fun callUpNationalTeam(world: World, dataset: WorldDataset, country: Int): NationalTeam {
    val entry = requireNotNull(dataset.country(country)) {
        "country $country is not in the dataset, and a national team is born from its level"
    }
    val reputation = nationalTeamReputation(entry.level)

    val clubPlayers = ArrayList<Player>()
    val clubRefs = ArrayList<String>()
    for (club in world.clubs.sortedBy { it.entry.ref }) {
        for (player in club.squad) {
            if (player.country == country) {
                clubPlayers += player
                clubRefs += club.entry.ref
            }
        }
    }

    val rng = SplitMix64Rng(world.seed)
        .fork(SeedDomain.WORLDGEN)
        .fork(NATIONAL_TEAM_STREAM)
        .fork(country.toLong())
    val freeAgents = if (needsFreeAgents(clubPlayers, emptyList())) {
        generateFreeAgents(entry, dataset.options, rng)
    } else {
        emptyList()
    }

    val pool = clubPlayers + freeAgents
    val origins = clubRefs + List(freeAgents.size) { null }

    val listed = selectSquad(pool)
    val listedPlayers = listed.map { pool[it] }
    val designated = deriveDesignated(listedPlayers, DesignationEnergy.FULL_SQUAD)

    val stored = listedPlayers.indices.sortedWith(
        compareByDescending<Int> { listedPlayers[it].strength }
            .thenByDescending { listedPlayers[it].age },
    )
    val taker = designated.taker?.let { PlayerId(stored.indexOf(it.value)) }

    return NationalTeam(
        country = country,
        level = entry.level,
        reputation = reputation,
        squad = stored.map { listedPlayers[it] },
        origins = stored.map { origins[listed[it]] },
        designated = Designated(taker = taker, cornerTaker = null),
    )
}

/**
 * Whether a call-up has to generate free agents: both of section 4.12's
 * sufficiency tests must fail.
 *
 * The first counts club players only, the second counts free agents too. The
 * original stores the first as a flag computed at world creation and never
 * refreshes it for the AI, which is exactly the value computing it at a
 * call-up gives in a world nobody has retired from. See OPEN-QUESTIONS item 68.
 */
@SpecRef("4.12")
internal fun needsFreeAgents(clubPlayers: List<Player>, freeAgents: List<Player>): Boolean =
    !sufficient(clubPlayers, CLUB_OUTFIELD_MINIMUM) &&
        !sufficient(clubPlayers + freeAgents, POOL_OUTFIELD_MINIMUM)

@SpecRef("4.12")
private fun sufficient(players: List<Player>, outfieldMinimum: Int): Boolean {
    val keepers = players.count { it.position == Position.GOALKEEPER }
    return players.size - keepers >= outfieldMinimum && keepers >= GOALKEEPER_MINIMUM
}

/**
 * Section 4.12's call-up list from a pool, as indices into the pool in the
 * order step 6 leaves the list before the designations resort it.
 *
 * Step 3 ranks the pool by stored strength descending with the star mark
 * breaking ties and a stable sort keeping enumeration order after that. Step
 * 4 walks the ranking once, seating each man in his cell while the cell has
 * room and skipping him otherwise; a skipped man is not reconsidered. Step 5
 * runs only when the whole pool is short of twenty three, and in that case
 * adds every man of each under quota group's position and then everyone left,
 * so the net effect is that the whole pool is called; it never tops the list
 * up to twenty three, and with a pool of twenty three or more it never runs at
 * all, which is why a country with no left sided fullback calls fewer than
 * twenty three men and nothing completes the list. OPEN-QUESTIONS item 64 and
 * docs/known-quirks.md record that.
 *
 * The list comes back ordered by position, then style, then strength
 * descending, then star first, stable over the order the men were seated in.
 */
@SpecRef("4.12")
internal fun selectSquad(pool: List<Player>): List<Int> {
    val ranked = pool.indices.sortedWith(
        compareByDescending<Int> { pool[it].strength }
            .thenByDescending { pool[it].star },
    )

    val seated = IntArray(Cell.entries.size)
    val listed = mutableSetOf<Int>()
    for (index in ranked) {
        val cell = cellOf(pool[index])
        if (seated[cell.ordinal] < cell.quota) {
            seated[cell.ordinal] += 1
            listed += index
        }
    }

    if (pool.size < SQUAD_SIZE) {
        for (group in Group.entries) {
            val groupSeated = Cell.entries.filter { it.group == group }.sumOf { seated[it.ordinal] }
            if (groupSeated < group.quota) {
                ranked.filter { it !in listed && pool[it].position == group.position }.forEach { listed += it }
            }
        }
        ranked.filter { it !in listed }.forEach { listed += it }
    }

    return listed.sortedWith(
        compareBy<Int> { pool[it].position.ordinal }
            .thenBy { pool[it].style.ordinal }
            .thenByDescending { pool[it].strength }
            .thenByDescending { pool[it].star },
    )
}

/**
 * The six quota groups of section 4.12 step 4, in the order step 5 visits
 * them: goalkeepers, fullbacks, centrebacks, playmakers, forwards, holders.
 *
 * A midfielder's group is his section 4.3 style; a forward's style is not
 * read, so centre forwards and wingers share one group.
 */
@SpecRef("4.12")
private enum class Group(val position: Position, val quota: Int) {
    GOALKEEPERS(Position.GOALKEEPER, 3),
    FULLBACKS(Position.FULLBACK, 4),
    CENTREBACKS(Position.CENTREBACK, 4),
    PLAYMAKERS(Position.MIDFIELDER, 5),
    FORWARDS(Position.FORWARD, 4),
    HOLDERS(Position.MIDFIELDER, 3),
}

/**
 * The eleven cells of section 4.12 step 4: each outfield group split by
 * preferred side, and the goalkeepers as one cell with no side.
 */
@SpecRef("4.12")
private enum class Cell(val group: Group, val side: Side?, val quota: Int) {
    GOALKEEPER(Group.GOALKEEPERS, null, 3),
    FULLBACK_RIGHT(Group.FULLBACKS, Side.RIGHT, 2),
    FULLBACK_LEFT(Group.FULLBACKS, Side.LEFT, 2),
    CENTREBACK_RIGHT(Group.CENTREBACKS, Side.RIGHT, 2),
    CENTREBACK_LEFT(Group.CENTREBACKS, Side.LEFT, 2),
    PLAYMAKER_RIGHT(Group.PLAYMAKERS, Side.RIGHT, 2),
    PLAYMAKER_LEFT(Group.PLAYMAKERS, Side.LEFT, 3),
    HOLDER_RIGHT(Group.HOLDERS, Side.RIGHT, 2),
    HOLDER_LEFT(Group.HOLDERS, Side.LEFT, 1),
    FORWARD_RIGHT(Group.FORWARDS, Side.RIGHT, 2),
    FORWARD_LEFT(Group.FORWARDS, Side.LEFT, 2),
}

@SpecRef("4.12")
private fun cellOf(player: Player): Cell {
    val group = when (player.position) {
        Position.GOALKEEPER -> return Cell.GOALKEEPER
        Position.FULLBACK -> Group.FULLBACKS
        Position.CENTREBACK -> Group.CENTREBACKS
        Position.MIDFIELDER -> if (player.style == PlayerStyle.DEFENSIVE) Group.HOLDERS else Group.PLAYMAKERS
        Position.FORWARD -> Group.FORWARDS
    }
    return Cell.entries.first { it.group == group && it.side == player.side }
}

/**
 * The twenty free agents section 4.12 generates for a country short of real
 * players: three keepers, four fullbacks, four centrebacks, five midfielders
 * and four forwards, created in that order.
 *
 * Each man gets his own stream forked off the country's by his creation
 * index, in the pattern generateSquad uses for a club, so no man's draws
 * depend on the men created before him. His name is the placeholder of
 * OPEN-QUESTIONS item 63, the country's name and his creation counter, which
 * consumes no draw: the original's name generator reads name lists that are
 * its own data, and the project does not copy them.
 */
@SpecRef("4.12")
internal fun generateFreeAgents(country: CountryEntry, options: DatasetOptions, rng: Rng): List<Player> =
    FREE_AGENT_POSITIONS.mapIndexed { index, position ->
        generateFreeAgent(
            country = country,
            position = position,
            name = placeholderName(country, index + 1),
            options = options,
            rng = rng.fork(index.toLong()),
        )
    }

/**
 * The deterministic placeholder name of OPEN-QUESTIONS item 63: the country's
 * name as the dataset gives it, which the importer makes the three letter
 * code of the file suffixes, and a fixed width creation counter.
 */
@SpecRef("4.12")
internal fun placeholderName(country: CountryEntry, counter: Int): String =
    "${country.name}-${counter.toString().padStart(PLACEHOLDER_COUNTER_WIDTH, '0')}"

/**
 * One free agent, built in the sequence section 4.12 gives under "Jogadores
 * avulsos", with these draws in this order: strength, the seven abilities,
 * the trait bonuses, the trait pair, talent, age, side.
 *
 * Strength is the country's mapped level minus five plus rand(8), with no
 * country scale and no ceiling; the mapping is section 4.4's, which is the
 * identity to fifteen and never reaches the zero the original would give
 * above twenty five, because no country of section 4.4.1 sits there.
 *
 * The abilities are generated before the trait pair is drawn and before the
 * style is derived, reading the pair and the style the original's freshly
 * created player holds at that moment: both characteristics at index zero
 * and the defensive style. That is a defect of ordering in the original,
 * reproduced on purpose: a fullback or midfielder is always built on the
 * defensive row of section 4.2 whatever pair he then draws, a keeper always
 * collects the positioning bonus, and no outfielder collects any bonus. See
 * OPEN-QUESTIONS item 65 and docs/known-quirks.md.
 *
 * The quality seed is section 4.2's A read off the country level, and the
 * band is the reputation row's, both as section 4.12 step 5 states them.
 *
 * Salary and market value are computed with no club: the division is absent
 * and the club level is the ten section 4.12 step 9 names, which pays no
 * rich club premium and prices at the lowest club level base; the value
 * takes the arrived this season discount because the man's arrival season is
 * the current one.
 */
@SpecRef("4.12")
internal fun generateFreeAgent(
    country: CountryEntry,
    position: Position,
    name: String,
    options: DatasetOptions,
    rng: Rng,
): Player {
    val strength = ClubBands.mappedLevel(country.level) - FREE_AGENT_STRENGTH_OFFSET +
        rng.rand(FREE_AGENT_STRENGTH_SPREAD)

    val reputation = nationalTeamReputation(country.level)
    val qualitySeed = ClubBands.qualitySeed(country.level)
    val band = ClubBands.bands(null, reputation, reputationPath = true).abilityBand

    val abilities = generateAbilities(
        position = position,
        style = UNSET_STYLE,
        strength = strength,
        qualitySeed = qualitySeed,
        band = band,
        rng = rng,
    )
    applyTraitBonuses(
        abilities = abilities,
        position = position,
        firstTrait = UNSET_TRAIT,
        secondTrait = UNSET_TRAIT,
        qualitySeed = qualitySeed,
        band = band,
        rng = rng,
    )

    val (firstTrait, secondTrait) = drawTraitPair(position, rng)
    val talent = FREE_AGENT_TALENT_BASE + rng.rand(FREE_AGENT_TALENT_SPREAD)
    val age = FREE_AGENT_AGE_BASE + rng.rand(FREE_AGENT_AGE_SPREAD)
    val side = Side.ofOrdinal(rng.rand(Side.entries.size))
    val style = playerStyle(position, firstTrait, secondTrait)

    return Player(
        name = name,
        age = age,
        country = country.index,
        position = position,
        side = side,
        firstTrait = firstTrait,
        secondTrait = secondTrait,
        starter = false,
        star = false,
        topWorld = false,
        talent = talent,
        style = style,
        strength = strength,
        abilities = abilities.toList(),
        contractDays = FREE_AGENT_CONTRACT_DAYS,
        salary = salary(
            strength = strength,
            age = age,
            position = position,
            star = false,
            topWorld = false,
            clubLevel = NO_CLUB_LEVEL,
            division = null,
            majorLeagueCountry = false,
            monthlyWages = options.monthlyWages,
        ),
        marketValue = marketValue(
            strength = strength,
            age = age,
            position = position,
            starter = false,
            star = false,
            topWorld = false,
            clubLevel = NO_CLUB_LEVEL,
            europeanNationality = country.continent == Country.EUROPE_CONTINENT,
            arrivedThisSeason = true,
        ),
    )
}

/**
 * The trait pair of section 4.4.2: one draw over the position's table of
 * pairs, the row giving both characteristics in its own order. A row repeated
 * in a table is how the original weights a pair, so the tables are kept with
 * their repeats rather than collapsed.
 */
@SpecRef("4.4.2")
internal fun drawTraitPair(position: Position, rng: Rng): Pair<Trait, Trait> {
    val rows = traitPairRows(position)
    return rows[rng.rand(rows.size)]
}

@SpecRef("4.4.2")
internal fun traitPairRows(position: Position): List<Pair<Trait, Trait>> = when (position) {
    Position.GOALKEEPER -> GOALKEEPER_PAIRS
    Position.FULLBACK -> FULLBACK_PAIRS
    Position.CENTREBACK -> CENTREBACK_PAIRS
    Position.MIDFIELDER -> MIDFIELDER_PAIRS
    Position.FORWARD -> FORWARD_PAIRS
}

private fun pairs(vararg ordinals: Int): List<Pair<Trait, Trait>> =
    ordinals.toList().chunked(2) { (first, second) -> Trait.ofOrdinal(first) to Trait.ofOrdinal(second) }

@SpecRef("4.4.2")
private val GOALKEEPER_PAIRS = pairs(0, 3, 0, 1, 2, 0, 1, 2, 3, 1, 0, 2)

@SpecRef("4.4.2")
private val FULLBACK_PAIRS = pairs(6, 10, 6, 13, 10, 11, 10, 13, 10, 6, 10, 9, 6, 11)

@SpecRef("4.4.2")
private val CENTREBACK_PAIRS = pairs(7, 10, 7, 12, 7, 5, 10, 13, 7, 13, 7, 10, 7, 5, 7, 13, 7, 12, 7, 9, 7, 10, 5, 12)

@SpecRef("4.4.2")
private val MIDFIELDER_PAIRS = pairs(
    4, 11, 4, 9, 9, 11, 11, 9, 4, 8, 4, 13, 7, 10, 7, 11, 7, 5, 7, 13,
    10, 13, 10, 11, 9, 4, 10, 12, 4, 11, 8, 11, 7, 9, 11, 13, 7, 11,
)

@SpecRef("4.4.2")
private val FORWARD_PAIRS = pairs(9, 5, 13, 9, 9, 5, 8, 9, 9, 13, 9, 5, 9, 8, 5, 13, 8, 11, 9, 11, 9, 12, 13, 8)

/** The creation order of the synthetic batch: 3 GOL, 4 LAT, 4 ZAG, 5 MEI, 4 ATA. */
@SpecRef("4.12")
private val FREE_AGENT_POSITIONS: List<Position> =
    List(3) { Position.GOALKEEPER } +
        List(4) { Position.FULLBACK } +
        List(4) { Position.CENTREBACK } +
        List(5) { Position.MIDFIELDER } +
        List(4) { Position.FORWARD }

/**
 * The characteristic a freshly created player of the original holds in both
 * slots before his pair is drawn: index zero, which is a goalkeeping one.
 */
@SpecRef("4.12")
private val UNSET_TRAIT = Trait.ofOrdinal(0)

/** The style a freshly created player holds before section 4.3 runs: zero, defensive. */
@SpecRef("4.12")
private val UNSET_STYLE = PlayerStyle.DEFENSIVE

/** The list step 4 fills: three keepers and twenty outfielders. */
@SpecRef("4.12")
internal const val SQUAD_SIZE = 23

/** The first sufficiency test: this many outfielders with a club. */
@SpecRef("4.12")
private const val CLUB_OUTFIELD_MINIMUM = 15

/** The second sufficiency test: this many outfielders counting free agents. */
@SpecRef("4.12")
private const val POOL_OUTFIELD_MINIMUM = 16

/** Both sufficiency tests want two goalkeepers. */
@SpecRef("4.12")
private const val GOALKEEPER_MINIMUM = 2

@SpecRef("4.12")
private const val FREE_AGENT_STRENGTH_OFFSET = 5

@SpecRef("4.12")
private const val FREE_AGENT_STRENGTH_SPREAD = 8

@SpecRef("4.12")
private const val FREE_AGENT_TALENT_BASE = 7

@SpecRef("4.12")
private const val FREE_AGENT_TALENT_SPREAD = 4

@SpecRef("4.12")
private const val FREE_AGENT_AGE_BASE = 18

@SpecRef("4.12")
private const val FREE_AGENT_AGE_SPREAD = 12

/** The "elenco inicial" row of section 4.7's contract table. */
@SpecRef("4.7")
internal const val FREE_AGENT_CONTRACT_DAYS = 180

/**
 * What a man with no club counts as for the club level of sections 4.8 and
 * 4.9: ten, below the rich club premium and at the lowest value base.
 */
@SpecRef("4.12")
internal const val NO_CLUB_LEVEL = 10

/** Four digits, so the placeholder names sort as they were created. */
@SpecRef("4.12")
private const val PLACEHOLDER_COUNTER_WIDTH = 4

/**
 * The stream family national teams draw from, forked off the world's
 * generation stream and then by country, so a call-up's draws depend on the
 * seed and the country and on nothing else.
 */
@SpecRef("4.12")
internal const val NATIONAL_TEAM_STREAM = 0x5E1EL
