package org.openfoot.engine.world

import org.openfoot.model.Position
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef
import org.openfoot.model.bfRound
import org.openfoot.model.bfRoundLong
import org.openfoot.model.rand

/**
 * How long a contract runs for a player who exists because the world was
 * created, rather than because he was signed, promoted or loaned.
 *
 * The spread matters more than the length. Every other event in section 4.7
 * hands out a round number of days, so if world creation did too, every squad
 * in the game would reach its renewal cliff on the same day of the first
 * season. The thirty day spread staggers them.
 */
@SpecRef("4.7")
fun initialContractDays(rng: Rng): Int = CONTRACT_BASE_DAYS + rng.rand(CONTRACT_SPREAD_DAYS)

@SpecRef("4.7")
private const val CONTRACT_BASE_DAYS = 210

@SpecRef("4.7")
private const val CONTRACT_SPREAD_DAYS = 30

/**
 * What a player is paid, in the unit the wage option selects.
 *
 * The formula produces a weekly figure and quadruples it when wages are shown
 * monthly, which is the default the original ships with. Nothing about the
 * player's own quality enters except his strength: age only ever subtracts, and
 * the position adjustment says a goalkeeper is cheaper than a midfielder of
 * identical strength at the same club.
 *
 * The youth multiplier of section 4.8 is absent for the same reason it is
 * absent from the strength formula: youth squads are not generated yet.
 */
@SpecRef("4.8")
fun salary(
    strength: Int,
    age: Int,
    position: Position,
    star: Boolean,
    topWorld: Boolean,
    clubLevel: Int,
    division: Int?,
    majorLeagueCountry: Boolean,
    monthlyWages: Boolean,
): Long {
    var base = divisionBase(division, majorLeagueCountry)
    if (clubLevel > RICH_CLUB_LEVEL) {
        base += RICH_CLUB_BONUS
    }
    base += positionAdjustment(position)
    base = bfRound(SALARY_BASE_HALVING * base)

    val core = strength.toLong() * SALARY_STRENGTH_FACTOR * base
    val starBonus = if (star || topWorld) strength.toLong() * STAR_SALARY_FACTOR else 0L

    var wage = if (age < DECLINE_AGE) {
        core + starBonus
    } else {
        core - (age - DECLINE_AGE).toLong() * AGE_PENALTY_PER_YEAR + starBonus
    }

    wage = maxOf(wage, SALARY_FLOOR)
    if (topWorld) {
        wage = bfRoundLong(wage * TOP_WORLD_SALARY_MULTIPLIER)
    }
    if (monthlyWages) {
        wage *= MONTHLY_MULTIPLIER
    }
    return wage
}

/**
 * The pay scale of the division, which is the only place the five countries
 * the spec singles out are treated differently from everyone else.
 */
@SpecRef("4.8")
private fun divisionBase(division: Int?, majorLeagueCountry: Boolean): Int =
    if (majorLeagueCountry) {
        when (division) {
            1 -> 750
            2 -> 550
            3 -> 500
            4, 5 -> 450
            else -> 350
        }
    } else {
        when (division) {
            1 -> 600
            2 -> 500
            3 -> 450
            4, 5 -> 400
            else -> 350
        }
    }

@SpecRef("4.8")
internal fun positionAdjustment(position: Position): Int = when (position) {
    Position.GOALKEEPER -> -70
    Position.FULLBACK -> -30
    Position.CENTREBACK -> -40
    Position.MIDFIELDER -> 0
    Position.FORWARD -> -50
}

/** A club above this level pays a premium. No data file expresses one. */
@SpecRef("4.8")
internal const val RICH_CLUB_LEVEL = 20

@SpecRef("4.8")
private const val RICH_CLUB_BONUS = 50

@SpecRef("4.8")
private const val SALARY_BASE_HALVING = 0.5

@SpecRef("4.8")
private const val SALARY_STRENGTH_FACTOR = 2

@SpecRef("4.8")
private const val STAR_SALARY_FACTOR = 250

@SpecRef("4.8")
private const val AGE_PENALTY_PER_YEAR = 300

@SpecRef("4.8")
private const val SALARY_FLOOR = 500L

@SpecRef("4.8")
private const val TOP_WORLD_SALARY_MULTIPLIER = 1.4

@SpecRef("4.8")
private const val MONTHLY_MULTIPLIER = 4

/** The age past which the salary formula starts subtracting. */
@SpecRef("4.8")
internal const val DECLINE_AGE = 32

/**
 * What a player would cost to buy.
 *
 * Value grows with the square of strength, which is by far the dominant term:
 * a player twice as strong is worth four times as much before anything else is
 * considered. Everything else is a modifier on that, and the age term is the
 * only one that can subtract.
 *
 * The arrival discount of section 4.9 scales a player by how recently he
 * joined. At world creation nobody has joined anything, so a file player
 * counts as long standing and takes no discount, which is the default; see
 * OPEN-QUESTIONS item 16. The one rung reachable today is "arrived this
 * season", which section 4.12 gives every synthetic free agent because his
 * arrival season is the current one. The two middle rungs need a season to
 * have passed and arrive with the season. The cap at a recorded asking price
 * is absent because no asking price exists yet.
 */
@SpecRef("4.9")
fun marketValue(
    strength: Int,
    age: Int,
    position: Position,
    starter: Boolean,
    star: Boolean,
    topWorld: Boolean,
    clubLevel: Int,
    europeanNationality: Boolean,
    arrivedThisSeason: Boolean = false,
): Long {
    val doubled = (strength * VALUE_STRENGTH_FACTOR).toLong()
    val quadratic = doubled * doubled

    val levelBase = clubLevelBase(clubLevel) + ageTerm(age)
    val effectiveBase = if (levelBase <= 0) COLLAPSED_VALUE_BASE else levelBase

    var value = quadratic.toDouble() * effectiveBase
    if (star) {
        value *= starMultiplier(clubLevel, europeanNationality)
    }
    if (topWorld) {
        value *= TOP_WORLD_VALUE_MULTIPLIER
    }
    if (position == Position.FORWARD) {
        value *= FORWARD_VALUE_MULTIPLIER
    }
    if (starter) {
        value *= STARTER_VALUE_MULTIPLIER
    }
    if (arrivedThisSeason) {
        value *= ARRIVED_THIS_SEASON_MULTIPLIER
    }
    return bfRoundLong(value)
}

/**
 * What the club a player sits at is worth to his price, before age.
 */
@SpecRef("4.9")
private fun clubLevelBase(clubLevel: Int): Int = when {
    clubLevel >= 21 -> 750
    clubLevel >= 20 -> 600
    clubLevel >= 18 -> 500
    clubLevel >= 12 -> 400
    else -> 366
}

/**
 * How far a player is from the age the market considers his peak.
 *
 * Positive up to thirty two, then decaying, then frankly negative. Past that
 * point a player can be worth so little that the base collapses to a floor.
 * Ages below sixteen are treated as sixteen, so a value can be asked for a
 * player younger than the professional game admits.
 */
@SpecRef("4.9")
internal fun ageTerm(age: Int): Int {
    val effective = maxOf(age, MINIMUM_VALUED_AGE)
    return when {
        effective < 20 -> (32 - effective) * 27
        effective <= 25 -> (32 - effective) * 22
        effective < 32 -> (32 - effective) * 15
        effective < 34 -> (34 - effective) * 10
        else -> -(effective - 34) * 50
    }
}

/**
 * A star is worth more, and worth more again at a club good enough to be
 * shopping in Europe. Both of the higher rungs need a club level above twenty,
 * which no data file expresses, so a star at world creation always takes the
 * lowest rung.
 */
@SpecRef("4.9")
internal fun starMultiplier(clubLevel: Int, europeanNationality: Boolean): Double = when {
    clubLevel >= 22 && europeanNationality -> 3.0
    clubLevel >= 21 && europeanNationality -> 2.0
    else -> 1.7
}

@SpecRef("4.9")
private const val VALUE_STRENGTH_FACTOR = 2

@SpecRef("4.9")
internal const val MINIMUM_VALUED_AGE = 16

/** What the base falls back to when age has driven it to nothing. */
@SpecRef("4.9")
internal const val COLLAPSED_VALUE_BASE = 60

@SpecRef("4.9")
private const val TOP_WORLD_VALUE_MULTIPLIER = 1.6

@SpecRef("4.9")
private const val FORWARD_VALUE_MULTIPLIER = 1.3

@SpecRef("4.9")
private const val STARTER_VALUE_MULTIPLIER = 1.2

@SpecRef("4.9")
private const val ARRIVED_THIS_SEASON_MULTIPLIER = 0.18
