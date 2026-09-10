package org.openfoot.engine.lineup

import org.openfoot.engine.match.MatchPlayer
import org.openfoot.engine.world.Player
import org.openfoot.engine.world.inSlot
import org.openfoot.model.PlayerId
import org.openfoot.model.RuleSet
import org.openfoot.model.Slot
import org.openfoot.model.SlotCandidate
import org.openfoot.model.SpecRef
import org.openfoot.model.chooseCandidate

/**
 * Picks the eleven a squad fields in a formation, the way section 5.4 does it.
 *
 * The pool is filtered, sorted by strength descending and then energy
 * descending, and nothing else is ever consulted again. Each cell of the
 * formation is then filled, in the order the formation lists its cells, by the
 * first player left in that pool who fits. Because the formation lists name
 * the forwards before the defenders, the attack picks from the top of the pool
 * and the defence takes what survives, which is the AI behaviour section 5.4
 * insists a reimplementation reproduce.
 *
 * A cell that finds nobody at all takes the strongest player left regardless.
 * Section 5.4 does not say what happens when a chain runs out, and this is the
 * reading recorded as item 35 of OPEN-QUESTIONS: the bench of section 5.4 step
 * 4 is a fixed eleven and the aggregates of section 3.4 have no notion of an
 * empty pitch cell, so a side that fielded ten would be visible everywhere and
 * is described nowhere. Fewer than eleven come back only when fewer than
 * eleven can play.
 *
 * The identity handed to each player is his index in the squad passed in,
 * which is what keeps it stable for as long as that squad is.
 */
@SpecRef("5.4")
fun fillEleven(
    squad: List<Player>,
    formation: Formation,
    rules: RuleSet,
    availability: Availability,
    representedCountry: Int? = null,
): List<MatchPlayer> {
    val pool = sortedPool(squad, availability)
    val taken = BooleanArray(squad.size)
    val eleven = mutableListOf<MatchPlayer>()
    for (slot in formation.slots) {
        val chosen = chooseFor(slot, squad, pool, taken, rules) ?: continue
        taken[chosen] = true
        eleven += squad[chosen].inSlot(slot, PlayerId(chosen), represents(squad[chosen], representedCountry))
    }
    return eleven
}

/**
 * Whether a fielded player carries section 3.3's national team scale: only
 * when the side represents a country at all and the player's nationality is
 * that country. A club side represents none, so its players never do.
 */
@SpecRef("3.3")
private fun represents(player: Player, representedCountry: Int?): Boolean =
    representedCountry != null && player.country == representedCountry

/**
 * The pool of section 5.4 step 1 and 2: not injured, not suspended, sorted by
 * strength descending and then energy descending, and nothing else. Shared by
 * the eleven and the bench, because both are drawn from the same ordering and
 * a second copy of it would be a second place for the two to drift apart.
 */
@SpecRef("5.4")
private fun sortedPool(squad: List<Player>, availability: Availability): List<Int> {
    val availabilities = squad.mapIndexed { index, player -> availability.of(index, player) }
    return squad.indices
        .filter { availabilities[it].canPlay }
        .sortedWith(
            compareByDescending<Int> { squad[it].strength }
                .thenByDescending { availabilities[it].energy },
        )
}

/**
 * The eleven a squad fields together with who sits behind them, which is what
 * the rest of the engine asks for: a match never needs one without the other.
 */
@SpecRef("5.4")
data class MatchdaySquad(
    val onPitch: List<MatchPlayer>,
    val bench: List<MatchPlayer>,
)

/**
 * Names the eleven of section 5.4 step 3 and then the bench of step 4, from
 * whoever the eleven left behind.
 *
 * The bench template, 1, 1, 2, 4, 4, 12, 15, 15, 20, 20, 23, is read as model
 * cells, not as cells a substitute stands in: cell 1 asks for the goalkeeper
 * reading, cell 2 for a fullback on the right, cell 4 for a centre back, cell
 * 12 for a holding midfielder, cell 15 for an attacking one and cell 20 and 23
 * for a centre forward, twice and once. Each entry runs through the same
 * position cascade and side/style relaxation that fills the eleven, so a bench
 * place that finds nobody of its own kind cascades exactly as a pitch cell
 * would, right down to the catch all that seats the strongest man left over
 * regardless of fit.
 *
 * A squad too small to fill every bench place benches fewer rather than
 * failing: once every remaining player has been used, the catch all itself has
 * nobody left to hand back, and the template stops rather than repeating a man
 * already on the pitch or already on the bench.
 *
 * Every bench entry carries Slot.UNUSED_SUBSTITUTE, which is section 3.2's
 * minus one for a substitute who has not come on, never the template cell that
 * chose him. The template cell is only ever a question asked of the pool, not
 * an answer recorded on the player.
 *
 * Availability has no default here, and neither does it on fillEleven. Today
 * every caller hands in Availability.FULL_SQUAD, because season state, where
 * injuries and suspensions live, does not exist yet. A default would make that
 * fact invisible: the day the real dataset does exist, every call site that
 * inherited the default would go on fielding injured and suspended men and
 * nothing about the lineup it produced would say so. Requiring the argument
 * makes each caller state which squad it means, and turns that day into a
 * compile error instead of a silent one.
 *
 * representedCountry does default, to null, because null is the truth for
 * every club side rather than a placeholder for a value nobody has yet: only
 * a national team represents a country, and only its assembly passes one.
 * The eleven and the bench both carry the flag, since a substitute who comes
 * on is rated by the same section 3.3 scale as the man he replaces.
 */
@SpecRef("5.4")
fun autoLineup(
    squad: List<Player>,
    formation: Formation,
    rules: RuleSet,
    availability: Availability,
    representedCountry: Int? = null,
): MatchdaySquad {
    val onPitch = fillEleven(squad, formation, rules, availability, representedCountry)

    val pool = sortedPool(squad, availability)
    val taken = BooleanArray(squad.size)
    for (player in onPitch) {
        taken[player.id.value] = true
    }

    val bench = mutableListOf<MatchPlayer>()
    for (cell in rules.benchTemplate) {
        val chosen = chooseFor(Slot(cell), squad, pool, taken, rules) ?: break
        taken[chosen] = true
        bench += squad[chosen].inSlot(
            Slot.UNUSED_SUBSTITUTE,
            PlayerId(chosen),
            represents(squad[chosen], representedCountry),
        )
    }

    return MatchdaySquad(onPitch, bench)
}

/**
 * The relaxed search of section 5.4 step 3, narrowed to the players this fill
 * has not used yet.
 *
 * The search itself lives in the model beside the slot table it reads, because
 * section 3.8's substitution asks the same question of a bench. What stays
 * here is the bookkeeping the lineup needs and the substitution does not: an
 * index into the squad, which is both the taken marker and the identity the
 * player carries for the rest of the match.
 */
private fun chooseFor(
    slot: Slot,
    squad: List<Player>,
    pool: List<Int>,
    taken: BooleanArray,
    rules: RuleSet,
): Int? {
    val available = pool.filter { !taken[it] }.map { SquadCandidate(it, squad[it]) }
    return chooseCandidate(slot, available, rules)?.index
}

/** One squad member together with where he sits in the squad he came from. */
private class SquadCandidate(val index: Int, player: Player) : SlotCandidate by player
