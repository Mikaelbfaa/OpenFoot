package org.openfoot.engine.world

import org.openfoot.engine.match.MatchPlayer
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.Side
import org.openfoot.model.Slot
import org.openfoot.model.SlotCandidate
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait

/**
 * A player once the world has been generated.
 *
 * The first eleven properties are carried through from the dataset unchanged.
 * Everything after them was computed here and exists nowhere on disk, which is
 * the whole point of world generation: the original stores an identity and a
 * club, and the numbers that make a simulation possible are derived.
 *
 * Abilities are a list rather than an array so that two players compare equal
 * when they are equal. That is what lets a test assert a whole generated world
 * is reproducible, which is the property the project is built on. The match
 * engine wants an array, and the adapter below is where that conversion
 * happens, once per lineup rather than once per formula.
 *
 * This lives beside the code that produces it rather than in the model, because
 * nothing outside the engine needs it yet. It moves the day something does.
 */
@SpecRef("4")
data class Player(
    val name: String,
    val age: Int,
    val country: Int,
    override val position: Position,
    override val side: Side,
    val firstTrait: Trait,
    val secondTrait: Trait,
    val starter: Boolean,
    val star: Boolean,
    val topWorld: Boolean,
    val talent: Int,
    @property:SpecRef("4.3") override val style: PlayerStyle,
    @property:SpecRef("4.4") val strength: Int,
    @property:SpecRef("4.2") val abilities: List<Int>,
    @property:SpecRef("4.7") val contractDays: Int,
    @property:SpecRef("4.8") val salary: Long,
    @property:SpecRef("4.9") val marketValue: Long,
) : SlotCandidate {
    /** True when either of the player's two characteristics is the given one. */
    fun hasTrait(trait: Trait): Boolean = firstTrait == trait || secondTrait == trait
}

/**
 * Puts a generated player into a pitch cell so the match engine can rate him.
 *
 * No rating formula of sections 3.4 or 3.6 reads side or style, and the cell
 * he is standing in decides what he is asked to do regardless of either: a
 * player out of position is penalised by section 3.3 whatever his own side or
 * sub role say. They are carried across anyway, because MatchPlayer is a
 * SlotCandidate now, and section 3.8's substitution needs both to run a
 * vacated cell through the same section 3.2 search that seated him here.
 *
 * The identity is handed in rather than derived, because it is an index into
 * the squad this player was picked from and a player does not know which squad
 * that is.
 */
@SpecRef("3.4")
fun Player.inSlot(
    slot: Slot,
    id: PlayerId,
    representsSideCountry: Boolean = false,
): MatchPlayer = MatchPlayer(
    id = id,
    slot = slot,
    naturalPosition = position,
    age = age,
    strength = strength,
    abilities = abilities.toIntArray(),
    firstTrait = firstTrait,
    secondTrait = secondTrait,
    star = star,
    topWorld = topWorld,
    side = side,
    style = style,
    representsSideCountry = representsSideCountry,
)
