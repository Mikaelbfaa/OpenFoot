package org.openfoot.engine.season

import org.openfoot.model.Rng
import org.openfoot.model.SpecRef
import org.openfoot.model.rand

/** One scheduled match of a round robin: who hosts and who visits. */
@SpecRef("1.3")
data class Fixture(val home: String, val away: String) {
    init {
        require(home != away) { "$home cannot host itself" }
    }
}

/** One round of a competition: the fixtures played on the same day. */
@SpecRef("1.3")
data class Round(val fixtures: List<Fixture>)

/**
 * The turn count section 1.3 gives a league of the given size, before the
 * configured override that LeagueConfigEntry already resolves at import.
 */
@SpecRef("1.3")
fun defaultTurns(teamCount: Int): Int = when (teamCount) {
    8, 10 -> 4
    12, 14 -> 3
    26, 28, 30, 36 -> 1
    else -> 2
}

/**
 * The random order the participants take before the circle is drawn: a
 * uniform permutation, one draw per position from the back, which is the
 * Fisher and Yates shuffle over the project's own generator.
 *
 * Section 1.3 says the original shuffles with its platform generator rather
 * than its own rand, so there is no draw sequence of the original to match
 * here, only the property that the order is uniform and independent of the
 * order the participants were listed in.
 */
@SpecRef("1.3")
fun shuffledOrder(participants: List<String>, rng: Rng): List<String> {
    val order = participants.toMutableList()
    for (index in order.indices.reversed()) {
        if (index == 0) break
        val other = rng.rand(index + 1)
        val held = order[index]
        order[index] = order[other]
        order[other] = held
    }
    return order
}

/**
 * The full schedule of a round robin over an already ordered list of
 * participants, per section 1.3.
 *
 * The first turn is the circle method: the last participant sits fixed and
 * the others rotate. Raw round r, counting from nought, pairs position
 * (r + i) against position (r - i) modulo n - 1 for every i from one up,
 * the higher expression hosting, and the fixed side meets position r. The
 * raw rounds are then reordered by interleaving the first half of the list
 * with the second, which for five rounds gives the spec's one, four, two,
 * five, three; and in the rounds landing at odd positions of that new order,
 * counting from one, the fixed side's match is flipped so its home and away
 * balance over the turn instead of sitting on one side. The spec states the
 * five round case and says it generalises; interleaving the halves is the
 * reading taken here, and the worked example of section 1.3 is pinned by
 * FixturesTest.
 *
 * The second turn is the first mirrored, every match with its sides swapped
 * and the rounds in the same order. A third turn repeats the first and a
 * fourth repeats the second: leagues of three or four turns never draw a
 * second circle.
 *
 * Only even counts are accepted. The original has a bye path for odd counts
 * and section 1.3 records it as unreachable by any configured competition,
 * so an odd list here is a caller's mistake rather than a case to serve.
 */
@SpecRef("1.3")
fun roundRobin(order: List<String>, turns: Int): List<Round> {
    require(order.size >= 2 && order.size % 2 == 0) {
        "a round robin of ${order.size} sides, and every configured league is even sized"
    }
    require(order.toSet().size == order.size) { "a participant is listed twice" }
    require(turns in 1..MAX_TURNS) { "$turns turns, and section 1.3 knows one to $MAX_TURNS" }

    val firstTurn = balancedTurn(order)
    val secondTurn = firstTurn.map { round -> Round(round.fixtures.map { Fixture(it.away, it.home) }) }
    return (1..turns).flatMap { turn -> if (turn % 2 == 1) firstTurn else secondTurn }
}

@SpecRef("1.3")
private fun balancedTurn(order: List<String>): List<Round> {
    val rotating = order.dropLast(1)
    val fixed = order.last()
    val modulus = rotating.size

    val raw = (0 until modulus).map { r ->
        val pairs = (1..modulus / 2).map { i ->
            Fixture(rotating[Math.floorMod(r + i, modulus)], rotating[Math.floorMod(r - i, modulus)])
        }
        Fixture(fixed, rotating[r]) to pairs
    }

    val firstHalf = raw.take((modulus + 1) / 2)
    val secondHalf = raw.drop((modulus + 1) / 2)
    val interleaved = firstHalf.indices.flatMap { index ->
        listOfNotNull(firstHalf[index], secondHalf.getOrNull(index))
    }

    return interleaved.mapIndexed { index, (fixedMatch, pairs) ->
        val oddPosition = index % 2 == 0
        val fixedFixture = if (oddPosition) Fixture(fixedMatch.away, fixedMatch.home) else fixedMatch
        Round(listOf(fixedFixture) + pairs)
    }
}

@SpecRef("1.3")
private const val MAX_TURNS = 4
