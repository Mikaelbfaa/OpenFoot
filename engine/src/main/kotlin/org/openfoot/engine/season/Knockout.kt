package org.openfoot.engine.season

import org.openfoot.engine.match.AiShootoutResult
import org.openfoot.engine.match.aiPenaltyShootout
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide

/**
 * A club in a knockout, with the seed that decides its home rights: one is
 * the best placed of the first phase, and a lower number is always the better
 * placed side of any tie it meets.
 */
@SpecRef("FORMAT-SPEC, ces")
data class Entrant(val key: String, val seed: Int) {
    init {
        require(seed >= 1) { "$key seeded $seed, and seeds start at one" }
    }
}

/**
 * One knockout tie between a better placed and a worse placed side.
 *
 * FORMAT-SPEC's state championship section gives home rights by placing:
 * in a single leg the better placed side is at home, and over two legs the
 * worse placed side hosts the first leg and the better placed the return.
 * The same section names the side listed second in the pairing as the one
 * that goes through when a level tie is not settled on penalties, and
 * "second" there is the away side of the first or only match: the better
 * placed side over two legs, the worse placed in a single leg. Both readings
 * are functions of the two placings, so a tie needs nothing but them.
 */
@SpecRef("FORMAT-SPEC, ces")
data class Tie(val higher: Entrant, val lower: Entrant) {
    init {
        require(higher.seed < lower.seed) { "${higher.key} is not placed above ${lower.key}" }
    }

    /** Who hosts the given leg, one based. */
    @SpecRef("FORMAT-SPEC, ces")
    fun host(leg: Int, twoLegged: Boolean): Entrant = when {
        !twoLegged -> higher
        leg == 1 -> lower
        else -> higher
    }

    fun visitor(leg: Int, twoLegged: Boolean): Entrant = if (host(leg, twoLegged) == higher) lower else higher

    /** The side listed second in the pairing, which a level tie without penalties hands the place to. */
    @SpecRef("FORMAT-SPEC, ces")
    fun listedSecond(twoLegged: Boolean): Entrant = visitor(1, twoLegged)

    fun holds(key: String): Boolean = higher.key == key || lower.key == key
}

/**
 * The first knockout round of a state championship from the first phase
 * order, in the bracket order FORMAT-SPEC gives: a final of first against
 * second; semifinals of first against fourth and second against third; and
 * quarter finals of second against seventh, fourth against fifth, first
 * against eighth and third against sixth, in that order of ties.
 *
 * The list handed in is the qualified sides in table order, best first, and
 * only the three sizes the presets send to a knockout are accepted.
 */
@SpecRef("FORMAT-SPEC, ces")
fun firstRoundTies(qualified: List<Entrant>): List<Tie> {
    val bracket = BRACKETS[qualified.size]
        ?: throw IllegalArgumentException("a knockout of ${qualified.size} sides, and the presets send 2, 4 or 8")
    require(qualified.map { it.seed } == qualified.indices.map { it + 1 }) {
        "the qualified sides must be listed best first with seeds one to ${qualified.size}"
    }
    return bracket.map { (better, worse) -> Tie(qualified[better - 1], qualified[worse - 1]) }
}

/**
 * The next round: the winners of consecutive ties meet, in the order the
 * ties were played, the better placed of each pair holding the home rights.
 */
@SpecRef("FORMAT-SPEC, ces")
fun nextRoundTies(winners: List<Entrant>): List<Tie> {
    require(winners.size >= 2 && winners.size % 2 == 0) { "${winners.size} winners cannot be paired" }
    return winners.chunked(2).map { (first, second) ->
        if (first.seed < second.seed) Tie(first, second) else Tie(second, first)
    }
}

/**
 * Who went through a tie, and the shootout that decided it when one did.
 * The shootout result is read from the deciding match's host: winner HOME is
 * that host.
 */
@SpecRef("FORMAT-SPEC, ces")
data class TieOutcome(val winner: Entrant, val shootout: AiShootoutResult?)

/**
 * Settles a tie from its played legs, per FORMAT-SPEC's state championship
 * section and section 3.10.
 *
 * A tie is level when neither side won more legs than the other and the
 * aggregate is equal; no away goals rule applies, and extra time is never
 * played. Otherwise more legs won decides first, then the aggregate. A level
 * tie goes to the abstract shootout of 3.10 at the deciding match, which is
 * the single leg or the return, with the shootout's home side being that
 * match's host; with penalties off, the side listed second in the pairing
 * goes through, which is the better placed side over two legs and the worse
 * placed in a single leg. That last behaviour is a defect of the original,
 * reproduced on purpose and recorded in docs/known-quirks.md: the comparison
 * that was meant to favour the better placed side reads one value against
 * itself and always falls to the second listed.
 */
@SpecRef("FORMAT-SPEC, ces")
fun resolveTie(
    tie: Tie,
    legs: List<Result>,
    twoLegged: Boolean,
    penalties: Boolean,
    rules: RuleSet,
    rng: Rng,
): TieOutcome {
    require(legs.size == if (twoLegged) 2 else 1) { "${legs.size} legs for a ${if (twoLegged) "two" else "one"} legged tie" }
    legs.forEachIndexed { index, leg ->
        require(leg.home == tie.host(index + 1, twoLegged).key && leg.away == tie.visitor(index + 1, twoLegged).key) {
            "leg ${index + 1} was ${leg.home} against ${leg.away}, and the tie hosts it the other way"
        }
    }

    var higherWins = 0
    var lowerWins = 0
    var higherGoals = 0
    var lowerGoals = 0
    for (leg in legs) {
        val higherScored = if (leg.home == tie.higher.key) leg.homeGoals else leg.awayGoals
        val lowerScored = if (leg.home == tie.higher.key) leg.awayGoals else leg.homeGoals
        higherGoals += higherScored
        lowerGoals += lowerScored
        if (higherScored > lowerScored) higherWins++ else if (lowerScored > higherScored) lowerWins++
    }

    return when {
        higherWins != lowerWins -> TieOutcome(if (higherWins > lowerWins) tie.higher else tie.lower, null)
        higherGoals != lowerGoals -> TieOutcome(if (higherGoals > lowerGoals) tie.higher else tie.lower, null)
        penalties -> {
            val decidingLeg = legs.size
            val shootout = aiPenaltyShootout(rules, rng)
            val host = tie.host(decidingLeg, twoLegged)
            val winner = if (shootout.winner == TeamSide.HOME) host else tie.visitor(decidingLeg, twoLegged)
            TieOutcome(winner, shootout)
        }

        else -> TieOutcome(tie.listedSecond(twoLegged), null)
    }
}

/** Pairings by first phase position, better placed first, in tie order. */
@SpecRef("FORMAT-SPEC, ces")
private val BRACKETS: Map<Int, List<Pair<Int, Int>>> = mapOf(
    2 to listOf(1 to 2),
    4 to listOf(1 to 4, 2 to 3),
    8 to listOf(2 to 7, 4 to 5, 1 to 8, 3 to 6),
)
