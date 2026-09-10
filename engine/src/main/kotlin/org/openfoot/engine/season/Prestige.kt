package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.SpecRef

/**
 * A club's reputation and the prestige balance that feeds it, per section
 * 5.5. The original has no morale or confidence; this balance is the one
 * thing that moves a club's standing over the years, and the reputation it
 * produces is what the match engine reads in section 3.3.
 *
 * Two movements, both value returning. Decay takes a fixed amount off the
 * balance for the two top reputations and a smaller one for the two below,
 * and drops the reputation one rung when the balance has fallen past the
 * rung's floor; nothing decays at reputation one or zero. Promotion reads
 * the balance against the five thresholds and raises the reputation to the
 * highest one cleared, and only raises: a club below a threshold it once
 * cleared keeps its rung until the decay's own floor takes it.
 */
@SpecRef("5.5")
data class Prestige(
    val reputation: Int,
    val balance: Long,
) {
    init {
        require(reputation in 0..MAX_REPUTATION) { "reputation $reputation is outside 0..$MAX_REPUTATION" }
    }

    /** The balance after a title or a placing worth the given points. */
    @SpecRef("5.5")
    fun awarded(points: Long): Prestige = copy(balance = balance + points)

    /** The periodic decay of section 5.5, with the rung lost when the balance falls past the floor. */
    @SpecRef("5.5")
    fun decayed(): Prestige = when (reputation) {
        5 -> decayed(6_000, dropBelow = -90_000)
        4 -> decayed(600, dropBelow = -9_000)
        3 -> decayed(50, dropBelow = -1_000)
        2 -> copy(balance = balance - 5)
        else -> this
    }

    private fun decayed(amount: Long, dropBelow: Long): Prestige {
        val remaining = balance - amount
        return Prestige(if (remaining < dropBelow) reputation - 1 else reputation, remaining)
    }

    /** The reputation the balance now earns, never lower than the one held. */
    @SpecRef("5.5")
    fun promoted(): Prestige = copy(reputation = maxOf(reputation, reputationFor(balance)))

    companion object {
        @SpecRef("5.5")
        const val MAX_REPUTATION = 5

        @SpecRef("5.5")
        internal fun reputationFor(balance: Long): Int = when {
            balance > 100_000 -> 5
            balance > 10_000 -> 4
            balance > 1_000 -> 3
            balance > 100 -> 2
            balance > 10 -> 1
            else -> 0
        }
    }
}

/**
 * The prestige a title or a runner-up place is worth, per the table of
 * section 5.5, for a club of the given continent.
 *
 * A prize above a thousand is scaled down for a club outside Europe, and a
 * league title won in any division but the first is worth a flat fifty
 * whatever the table says. The competitions the table does not price are
 * worth nothing, which is the table's own silence and not a default.
 */
@SpecRef("5.5")
fun titlePrestige(kind: CompetitionKind, champion: Boolean, european: Boolean, division: Int? = null): Long {
    if (kind == CompetitionKind.NATIONAL_LEAGUE && champion && division != null && division > 1) {
        return LOWER_DIVISION_TITLE
    }
    val prize = TITLE_PRIZES[kind]?.let { if (champion) it.first else it.second } ?: 0L
    return if (!european && prize > FOREIGN_SCALE_ABOVE) prize * FOREIGN_NUMERATOR / FOREIGN_DENOMINATOR else prize
}

/** Champion and runner-up prizes, in the order section 5.5 lists them. */
@SpecRef("5.5")
private val TITLE_PRIZES: Map<CompetitionKind, Pair<Long, Long>> = mapOf(
    CompetitionKind.NATIONAL_LEAGUE to (500L to 90L),
    CompetitionKind.NATIONAL_CUP to (300L to 50L),
    CompetitionKind.STATE to (10L to 5L),
    CompetitionKind.CONTINENTAL_PRIMARY to (5_000L to 1_000L),
    CompetitionKind.CLUB_WORLD_CUP to (40_000L to 1_000L),
    CompetitionKind.CONTINENTAL_SECONDARY to (2_000L to 500L),
    CompetitionKind.RECOPA to (500L to 0L),
    CompetitionKind.REGIONAL to (50L to 0L),
    CompetitionKind.FINALISSIMA to (1_000L to 500L),
)

@SpecRef("5.5")
private const val LOWER_DIVISION_TITLE = 50L

@SpecRef("5.5")
private const val FOREIGN_SCALE_ABOVE = 1_000L

/** The 0.6 of section 5.5, kept as a ratio so the balance stays an integer. */
@SpecRef("5.5")
private const val FOREIGN_NUMERATOR = 3L

@SpecRef("5.5")
private const val FOREIGN_DENOMINATOR = 5L
