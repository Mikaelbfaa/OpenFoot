package org.openfoot.engine.season

import org.openfoot.engine.match.MatchEvent
import org.openfoot.model.PlayerId
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.rand

/**
 * What section 3.8 accumulates about a player's cards between matches: the
 * yellow card count and the ban, which the spec calls the hook.
 *
 * A player is unavailable while he holds three yellows or any ban, and
 * serving a round clears the yellows if they are what suspends him, or else
 * takes one match off the ban. A player who arrives at a round with both is
 * therefore out for two rounds, yellows first; the spec team was asked to
 * confirm that reading and this is written to the text as it stands.
 */
@SpecRef("3.8")
data class DisciplineRecord(
    val yellows: Int = 0,
    val ban: Int = 0,
) {
    init {
        require(yellows >= 0 && ban >= 0) { "a record of $yellows yellows and a ban of $ban" }
    }

    val suspended: Boolean get() = yellows >= YELLOWS_THAT_SUSPEND || ban >= 1

    fun booked(): DisciplineRecord = copy(yellows = yellows + 1)

    fun banned(matches: Int): DisciplineRecord = copy(ban = ban + matches)

    /** The record after a round sat out. */
    @SpecRef("3.8")
    fun served(): DisciplineRecord = when {
        yellows >= YELLOWS_THAT_SUSPEND -> copy(yellows = 0)
        ban >= 1 -> copy(ban = ban - 1)
        else -> this
    }

    companion object {
        val CLEAN = DisciplineRecord()
    }
}

/**
 * Applies one side's cards from a match log to its records, in log order.
 *
 * A booking adds a yellow. A sending off for a second yellow adds one match
 * of ban and nothing else, because the engine logs the second yellow itself
 * as a booking in the same minute, so the yellow section 3.8 says the sending
 * off adds is already counted; reading both would charge three yellows for
 * two. A direct red draws its ban from the ladder below, one draw per red in
 * the order the reds fell, which is also the order they are logged in.
 *
 * Only the side asked for is read, so a caller with one stream per side gets
 * the same draws whichever side it applies first.
 */
@SpecRef("3.8")
fun Map<PlayerId, DisciplineRecord>.afterMatch(
    log: List<MatchEvent>,
    side: TeamSide,
    rng: Rng,
): Map<PlayerId, DisciplineRecord> {
    val records = toMutableMap()
    fun update(id: PlayerId, change: (DisciplineRecord) -> DisciplineRecord) {
        records[id] = change(records[id] ?: DisciplineRecord.CLEAN)
    }

    for (event in log) {
        if (event.side != side) continue
        when (event) {
            is MatchEvent.Booking -> update(event.player.id) { it.booked() }
            is MatchEvent.SendingOff -> {
                val matches = if (event.secondYellow) SECOND_YELLOW_BAN else directRedBan(rng)
                update(event.player.id) { it.banned(matches) }
            }

            else -> Unit
        }
    }
    return records
}

/**
 * The ban a direct red card earns, from one draw of a thousand: one match
 * seventy per cent of the time, then two, three, five and ten. The last two
 * bands are written exactly as the spec writes them, up to and including 990
 * for five matches and the nine values above it for ten, so the top band is
 * nine in a thousand rather than the one per cent the spec rounds it to.
 */
@SpecRef("3.8")
fun directRedBan(rng: Rng): Int {
    val draw = rng.rand(RED_BAN_DRAW_BOUND)
    return when {
        draw < ONE_MATCH_BELOW -> 1
        draw < TWO_MATCHES_BELOW -> 2
        draw < THREE_MATCHES_BELOW -> 3
        draw <= FIVE_MATCHES_UP_TO -> 5
        else -> 10
    }
}

@SpecRef("3.8")
internal const val YELLOWS_THAT_SUSPEND = 3

@SpecRef("3.8")
private const val SECOND_YELLOW_BAN = 1

@SpecRef("3.8")
private const val RED_BAN_DRAW_BOUND = 1000

@SpecRef("3.8")
private const val ONE_MATCH_BELOW = 700

@SpecRef("3.8")
private const val TWO_MATCHES_BELOW = 900

@SpecRef("3.8")
private const val THREE_MATCHES_BELOW = 970

@SpecRef("3.8")
private const val FIVE_MATCHES_UP_TO = 990
