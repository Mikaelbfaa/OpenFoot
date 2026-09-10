package org.openfoot.engine.world

import org.openfoot.model.Designated
import org.openfoot.model.SpecRef

/**
 * Anything that can field a side in a match: a club of the generated world,
 * or a national team called up from it.
 *
 * The match assembly of section 5.4 asks a competitor for exactly these
 * things and nothing else. The key names the stream the side's formation and
 * marking are drawn from, so the same competitor draws the same either end of
 * a fixture; the reputation and the country feed section 3.3's context; the
 * squad is what the automatic lineup picks from; and the designations travel
 * to the side for section 3.7.
 *
 * representedCountry is null for a club and the team's own country for a
 * national team. Section 3.3's national team scale applies to a player whose
 * nationality is the side's country, and only a national team side has such a
 * country; a club side represents nobody, so the flag on its players stays
 * off whatever their nationality.
 */
@SpecRef("5.4")
interface Competitor {
    val key: String
    val country: Int
    val reputation: Int
    val squad: List<Player>
    val designated: Designated
    @property:SpecRef("3.3") val representedCountry: Int?
}
