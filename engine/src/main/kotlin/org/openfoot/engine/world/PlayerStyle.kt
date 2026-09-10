package org.openfoot.engine.world

import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait

/**
 * Derives a player's style from position and characteristics, per section 4.3.
 *
 * Each chain is a sequence of ordered tests, and the spec is explicit about
 * which of the two characteristics each test reads. Three of those reads are
 * narrower than a symmetric rule would be, and all three are defects of the
 * original that the spec marks CONFIRMADO and this reproduces:
 *
 * A fullback's second characteristic is tested for pace only, never for
 * crossing, because the original's test of the second characteristic for
 * crossing reads the first one again. The fullback's final creative clause
 * reads only the first characteristic. And a forward's whole chain reads only
 * the first characteristic, so a forward with dribbling as his second
 * characteristic alone is a centre forward.
 *
 * Section 4.4.2 states the consequence for generated players: three of the
 * seven fullback rows are offensive, nine of the nineteen midfielder rows are
 * holders, four of the twelve forward rows are wingers and none is defensive.
 * PlayerStyleTest pins those counts against the table.
 *
 * Goalkeepers and centrebacks have no styles to choose between.
 */
@SpecRef("4.3")
fun playerStyle(position: Position, firstTrait: Trait, secondTrait: Trait): PlayerStyle =
    when (position) {
        Position.GOALKEEPER, Position.CENTREBACK -> PlayerStyle.DEFENSIVE

        Position.FULLBACK -> when {
            firstTrait in WIDE_TRAITS -> PlayerStyle.OFFENSIVE
            firstTrait in DEFENSIVE_TRAITS -> PlayerStyle.DEFENSIVE
            secondTrait == Trait.PACE -> PlayerStyle.OFFENSIVE
            secondTrait in DEFENSIVE_TRAITS -> PlayerStyle.DEFENSIVE
            firstTrait in CREATIVE_TRAITS -> PlayerStyle.OFFENSIVE
            else -> PlayerStyle.DEFENSIVE
        }

        Position.MIDFIELDER -> when {
            firstTrait in CREATIVE_TRAITS -> PlayerStyle.OFFENSIVE
            firstTrait in DEFENSIVE_TRAITS -> PlayerStyle.DEFENSIVE
            secondTrait in CREATIVE_TRAITS -> PlayerStyle.OFFENSIVE
            secondTrait in DEFENSIVE_TRAITS -> PlayerStyle.DEFENSIVE
            else -> PlayerStyle.OFFENSIVE
        }

        Position.FORWARD -> when {
            firstTrait in DEFENSIVE_TRAITS -> PlayerStyle.DEFENSIVE
            firstTrait in WINGER_TRAITS -> PlayerStyle.WINGER
            else -> PlayerStyle.OFFENSIVE
        }
    }

@SpecRef("4.3")
private val WIDE_TRAITS = setOf(Trait.PACE, Trait.CROSSING)

@SpecRef("4.3")
private val DEFENSIVE_TRAITS = setOf(Trait.TACKLING, Trait.MARKING)

@SpecRef("4.3")
private val CREATIVE_TRAITS = setOf(Trait.DRIBBLING, Trait.FINISHING, Trait.PASSING, Trait.PLAYMAKING)

@SpecRef("4.3")
private val WINGER_TRAITS = setOf(Trait.DRIBBLING, Trait.PACE, Trait.CROSSING)
