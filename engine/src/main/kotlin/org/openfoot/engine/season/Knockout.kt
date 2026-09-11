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
 * The first round's pairs of a knockout of the given field, as seed numbers,
 * better seed first, in tie order: FORMAT-SPEC's fixed bracket for the two,
 * four and eight side fields the state presets send, and, for any other
 * field, section 1.13's strength seeding, the best against the worst, the
 * second best against the second worst, and so on. KnockoutPhase opens
 * every knockout with these pairs, and groupedSeeds lays its seeds out
 * against the same pairs, so the two can never disagree.
 */
@SpecRef("1.13")
internal fun openingPairs(field: Int): List<Pair<Int, Int>> = BRACKETS[field] ?: (1..field / 2).map { it to field + 1 - it }

/**
 * The seeds of a knockout fed by the top places of several groups, per
 * FORMAT-SPEC's grouped presets and section 1.11, which has a grouped
 * national league reuse that engine: for each group, in group order, the
 * seed its first place takes, then its second, down to its last qualifying
 * place.
 *
 * The seeds are chosen against the knockout's own pairing, openingPairs for
 * the first round and nextRoundTies after it, so that every tie stays inside
 * one group until a single club of each group remains, and only then do the
 * groups cross in bracket order, group A's survivor against group B's, C's
 * against D's, and so on. The field's opening pairs are read in tie order
 * and dealt to the groups in consecutive blocks of perGroup halved, group A
 * first. Within a group's block, the pair holding the best seed goes to the
 * group's first and last places, the next to its second and second to last,
 * and so on, so place p meets place perGroup plus one minus p, and a better
 * place always holds a better seed: the better placed club is the higher
 * entrant, hosting the return leg, in every round played inside the group,
 * not only the first. Because nextRoundTies pairs the winners of consecutive
 * ties, a group's block stays together round by round until its own final
 * is played. A single qualifier a group has no round inside the group: each
 * opening pair holds two consecutive groups, A with B, the earlier group
 * taking the better seed.
 *
 * Four groups of two, presets 7 and 10, read FORMAT-SPEC's eight side
 * bracket, (2,7), (4,5), (1,8), (3,6), one pair a group, and give group A
 * the seeds 2 and 7, B 4 and 5, C 1 and 8, D 3 and 6: every quarter final
 * is first against second of one group, groups A to D in order, and the
 * semi finals cross A with B and C with D, exactly FORMAT-SPEC's text. One
 * group seeds its places one to perGroup, the single table bracket. For more
 * than two qualifiers a group, the pairing inside the group and the order
 * of a group's own ties are OPEN-QUESTIONS item 120's bet, INFERIDO.
 */
@SpecRef("FORMAT-SPEC, ces")
fun groupedSeeds(groups: Int, perGroup: Int): List<List<Int>> {
    val field = groups * perGroup
    require(groups >= 1 && perGroup >= 1 && field >= 2 && field and (field - 1) == 0) {
        "$groups groups of $perGroup qualifiers make a field of $field, and a knockout field is a power of two of at least two"
    }
    val pairs = openingPairs(field)
    if (perGroup == 1) return (0 until groups).map { group -> listOf(pairs[group / 2].let { if (group % 2 == 0) it.first else it.second }) }
    val tiesPerGroup = perGroup / 2
    return (0 until groups).map { group ->
        val block = pairs.subList(group * tiesPerGroup, (group + 1) * tiesPerGroup).sortedBy { it.first }
        val seeds = IntArray(perGroup)
        block.forEachIndexed { index, (better, worse) ->
            seeds[index] = better
            seeds[perGroup - 1 - index] = worse
        }
        check((1 until perGroup).all { seeds[it - 1] < seeds[it] }) { "group $group of $groups groups of $perGroup would hold seeds ${seeds.toList()}, out of place order" }
        seeds.toList()
    }
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
