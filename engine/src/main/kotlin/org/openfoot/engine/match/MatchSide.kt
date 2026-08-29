package org.openfoot.engine.match

import org.openfoot.model.Designated
import org.openfoot.model.HomeAdvantage
import org.openfoot.model.Marking
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.RuleSet
import org.openfoot.model.Side
import org.openfoot.model.Slot
import org.openfoot.model.SlotCandidate
import org.openfoot.model.SpecRef
import org.openfoot.model.TeamSide
import org.openfoot.model.Trait

/**
 * One lineup entry as the match engine sees it.
 *
 * Deliberately not the world model player. The rating and aggregate formulas
 * of sections 3.3, 3.4 and 3.6 read the cell, the natural position, the
 * strength, the abilities, the two characteristics and whether the player
 * represents the side's country, and nothing else of a Player; Player.inSlot
 * is the adapter that builds one of these from a Player, once per lineup
 * rather than once per formula, and nothing here changes because of it. Age
 * is section 3.9's, read by the energy drain and by injury duration rather
 * than by any of those formulas, and identity is what energy and bookings are
 * kept by.
 *
 * Star and topWorld are section 4.10's two badges, and they are here because
 * two later sections read them off a player who is on the pitch: section
 * 3.10's interactive penalty, which moves the conversion threshold for both
 * the taker and the goalkeeper facing him, and section 3.14 step 8, which
 * adds 0,4 for a star and 0,6 for a red star to every rating. topWorld is the
 * estrela vermelha and section 4.10 makes it imply star, so Player.star is
 * already true for a red star player; topWorld must therefore be carried
 * across from Player.topWorld and can never be derived from Player.star.
 * Neither has a default, so a new way of building a MatchPlayer cannot lose
 * them silently.
 *
 * Side and style are read by no formula of sections 3.3, 3.4 or 3.6 at all.
 * They are here for section 3.8, whose substitution asks the table of section
 * 3.2 which reserve suits a vacated cell, the same question section 5.4's
 * automatic lineup asks of a squad, which is what SlotCandidate asks of
 * anybody it is offered.
 *
 * Not a data class, because the abilities array would break value equality.
 */
@SpecRef("3.4")
class MatchPlayer(
    val id: PlayerId,
    val slot: Slot,
    val naturalPosition: Position,
    @property:SpecRef("3.9") val age: Int,
    val strength: Int,
    val abilities: IntArray,
    val firstTrait: Trait,
    val secondTrait: Trait,
    @property:SpecRef("4.10") val star: Boolean,
    @property:SpecRef("4.10") val topWorld: Boolean,
    @property:SpecRef("3.8") override val side: Side,
    @property:SpecRef("3.8") override val style: PlayerStyle,
    val representsSideCountry: Boolean = false,
) : SlotCandidate {

    /**
     * The position he was born to, which is what section 3.2's table asks
     * about, and not the cell he happens to be standing in.
     */
    @property:SpecRef("3.2")
    override val position: Position get() = naturalPosition

    /** True when either of the player's two characteristics is the given one. */
    fun hasTrait(trait: Trait): Boolean = firstTrait == trait || secondTrait == trait
}

/**
 * One team for one match.
 *
 * The order of the lineup list is load bearing. Section 3.4 walks it in order
 * and takes the first N players that qualify for a line, not the best N, so
 * this list must never be sorted.
 *
 * Bench entries may sit in the same list. Slots twenty six to thirty six fall
 * outside every line range, so the aggregates ignore them without special
 * handling, and shooter selection filters them explicitly.
 *
 * The designations of section 5.6 travel with the side because section 3.7
 * reads them at the moment of a goal. They are a fact about the club and not
 * about the eleven, derived once when the squad was built, so a side can name
 * a taker who never made the lineup; section 3.7 is what refuses him the
 * credit for not being on the pitch, and this type does no such filtering of
 * its own.
 */
@SpecRef("3.4")
class MatchSide(
    val lineup: List<MatchPlayer>,
    val marking: Marking,
    val context: StrengthContext,
    @property:SpecRef("5.6") val designated: Designated,
    val isHumanManaged: Boolean = false,
) {
    val reputation: Int get() = context.sideReputation

    val isHome: Boolean get() = context.isHomeSide

    private val countrymanContext = context.copy(playerRepresentsSideCountry = true)

    /**
     * The player's rating for this match on the zero to ten scale.
     *
     * Recomputed on every call, matching the original, which keeps no cache.
     */
    @SpecRef("3.3")
    fun ratingOf(player: MatchPlayer): Double = effectiveStrength(
        strength = player.strength,
        attrs = player.abilities,
        naturalPosition = player.naturalPosition,
        slot = player.slot,
        context = if (player.representsSideCountry) countrymanContext else context,
    )
}

/**
 * The same side with a different eleven on the pitch.
 *
 * MatchSide is not a data class, because the abilities array on a player would
 * break value equality, so there is no copy to lean on and every other field
 * has to be carried across by hand. That is why this sits immediately below
 * the constructor it mirrors: a field added to one and forgotten in the other
 * would silently reset itself the first time somebody was sent off.
 *
 * The caller owns the order of the list it hands in. Section 3.4 walks the
 * lineup in order and takes the first N that qualify for a line, so nothing
 * here sorts, filters or otherwise touches it.
 */
@SpecRef("3.4")
fun MatchSide.withLineup(lineup: List<MatchPlayer>): MatchSide = MatchSide(
    lineup = lineup,
    marking = marking,
    context = context,
    designated = designated,
    isHumanManaged = isHumanManaged,
)

/**
 * The two sides as they stand this minute, plus the season and the rules.
 *
 * This used to be the part of a match that could not change. It is now the
 * part that can: a sending off or a substitution rebuilds it with a different
 * lineup, which is what lets every aggregate and every duel below read the
 * current eleven without knowing that anything moved.
 */
@SpecRef("3.5")
class MatchSetup(
    val home: MatchSide,
    val away: MatchSide,
    val season: Int,
    val rules: RuleSet,
) {
    val isNeutralGround: Boolean get() = home.context.kind.isNeutralGround

    /**
     * The anti exploit rules of sections 3.6b and 3.6c only fire when a human
     * managed club is involved, on either side.
     */
    @SpecRef("3.6b")
    val hasHumanSide: Boolean get() = home.isHumanManaged || away.isHumanManaged

    fun side(team: TeamSide): MatchSide = if (team == TeamSide.HOME) home else away

    fun advantageFor(possessor: TeamSide): HomeAdvantage =
        HomeAdvantage.of(possessor, isNeutralGround)
}

/**
 * The same setup with one of the two sides replaced.
 *
 * MatchSetup is not a data class either, for the same reason MatchSide is not:
 * it carries one, and value equality would reach the abilities array through
 * it. Kept directly below the constructor it mirrors so that the two are read
 * together.
 */
@SpecRef("3.5")
fun MatchSetup.with(team: TeamSide, side: MatchSide): MatchSetup = MatchSetup(
    home = if (team == TeamSide.HOME) side else home,
    away = if (team == TeamSide.AWAY) side else away,
    season = season,
    rules = rules,
)
