package org.openfoot.engine.world

import org.openfoot.engine.match.SideState
import org.openfoot.model.PlayerId
import org.openfoot.model.Position
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait

/**
 * The two designations of section 5.6 that section 3.7 can credit a goal to:
 * the free kick and penalty taker, and the corner taker.
 *
 * Section 5.6 names four designations. Captain and false nine are left out of
 * this type on purpose, because the table itself marks their real effect as
 * none at all, display or manual only, and section 3.7 never reads either one.
 * Only the two that a goal can actually be credited to belong here.
 *
 * Both fields hold a player's index into the squad this was derived from, the
 * same identity space fillEleven hands out through PlayerId, so a designation
 * and a lineup entry can be compared directly without a lookup in between.
 *
 * This is guarded state, not a per match computation. The original stores it
 * on the club and recomputes it at world creation and again at every squad
 * change, never per match, and clears it only when the designated player
 * leaves the club. v0.1 has no season state and therefore no transfer that
 * could change a squad after world creation, so today this is derived exactly
 * once, when GeneratedClub is built in WorldGeneration.kt, and stored there.
 * The day v0.3 adds transfers, whatever moves a player in or out of a squad is
 * what must call deriveDesignated again for that squad.
 */
@SpecRef("5.6")
data class Designated(
    @property:SpecRef("5.6") val taker: PlayerId?,
    @property:SpecRef("5.6") val cornerTaker: PlayerId?,
)

/**
 * What deriveDesignated needs to know about a player's energy, since section
 * 5.6 orders the whole professional squad by strength descending and then
 * this, independent of section 5.4's matchday pool and its own notion of who
 * can play at all.
 *
 * Season state, where a player's energy actually persists between matches,
 * does not exist yet in v0.1. Every caller today supplies FULL_SQUAD, because
 * the two moments the derivation runs, world creation and a squad change, are
 * both moments a real implementation would find every man fully rested: a
 * freshly generated player and a freshly transferred one both start there.
 * The real source arrives with v0.3's season state and replaces this at each
 * call site, not with a default parameter here, so that day is a compile
 * error rather than a silent one.
 */
@SpecRef("5.6")
fun interface DesignationEnergy {

    fun of(index: Int, player: Player): Int

    companion object {
        @SpecRef("5.6")
        val FULL_SQUAD = DesignationEnergy { _, _ -> SideState.FULL_ENERGY }
    }
}

/**
 * Derives the free kick and penalty taker, and the corner taker, of section
 * 5.6 from a whole professional squad.
 *
 * The pool is every player in the squad, not the matchday eleven, and
 * "titular" reads the data flag Player.starter rather than whether the man
 * ended up in a lineup at all. A titular by that flag who never makes the
 * eleven is still the designated taker; section 3.7 is what later refuses him
 * credit for not being on the pitch, and that filter has no business here.
 *
 * The whole squad is sorted once, by strength descending and then energy
 * descending, and three branches are tried against that order in the order
 * the spec gives them, each one stopping at the first match. That ordering is
 * the same comparator section 5.4's automatic lineup builds in
 * AutoLineup.sortedPool, duplicated rather than shared because this pool must
 * not carry sortedPool's canPlay filter; if section 5.4's ordering is ever
 * corrected, both sites need the same fix.
 *
 * 1. the first titular player whose first characteristic is Finalizacao. The
 *    second characteristic never qualifies a player here, only the first;
 * 2. failing that, the first titular player who is not a natural goalkeeper;
 * 3. failing that, the first player at all who is not a natural goalkeeper,
 *    now ignoring the titular flag entirely.
 *
 * A squad made up entirely of goalkeepers, or an empty one, leaves the taker
 * unset rather than falling back further, because no branch of the spec ever
 * hands the ball to a keeper.
 *
 * The corner taker is never filled here. Section 5.6 is explicit that the AI
 * never sets one; only a human manager does, through a path this engine does
 * not yet expose. Every squad this function is handed belongs to an AI club
 * in v0.1, so the corner taker always comes back null, and section 3.7's
 * olympic goal credit therefore always falls through to the scorer for a
 * squad built here. See OPEN-QUESTIONS item 56.
 *
 * The original also carries a path that would recompute the taker against a
 * given list, the matchday lineup for instance, but only when the stored
 * taker is missing from that list. Nothing in the original ever calls it, and
 * the spec records it as dead code. It is deliberately not ported here.
 *
 * This derivation takes no Rng and consumes no randomness at all: section 5.6
 * breaks every tie with strength and then energy, and nothing here is left to
 * chance. The absence of the parameter is the proof, enforced by the compiler
 * rather than by a test.
 */
@SpecRef("5.6")
fun deriveDesignated(squad: List<Player>, energy: DesignationEnergy): Designated {
    val order = squad.indices.sortedWith(
        compareByDescending<Int> { squad[it].strength }
            .thenByDescending { energy.of(it, squad[it]) },
    )

    val taker = order.firstOrNull { squad[it].starter && squad[it].firstTrait == Trait.FINISHING }
        ?: order.firstOrNull { squad[it].starter && squad[it].position != Position.GOALKEEPER }
        ?: order.firstOrNull { squad[it].position != Position.GOALKEEPER }

    return Designated(
        taker = taker?.let(::PlayerId),
        cornerTaker = null,
    )
}
