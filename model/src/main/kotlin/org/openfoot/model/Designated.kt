package org.openfoot.model

/**
 * The two designations of section 5.6 that section 3.7 can credit a goal to:
 * the free kick and penalty taker, and the corner taker.
 *
 * Section 5.6 names four designations. Captain and false nine are left out of
 * this type on purpose, because the table itself marks their real effect as
 * none at all, display or manual only, and section 3.7 never reads either one.
 * Only the two that a goal can actually be credited to belong here.
 *
 * Both fields hold a player's index into the squad the designation was derived
 * from, the same identity space a lineup entry carries, so a designation and a
 * fielded player can be compared directly without a lookup in between. Section
 * 3.7 credits a designated player only while he is on the pitch, which is a
 * question about the lineup and not about this value.
 *
 * This lives in the model rather than beside the derivation that fills it
 * because both ends need it: world generation derives and stores it, and a
 * match side carries it so that section 3.7 can read it at the moment of a
 * goal.
 */
@SpecRef("5.6")
data class Designated(
    @property:SpecRef("5.6") val taker: PlayerId?,
    @property:SpecRef("5.6") val cornerTaker: PlayerId?,
) {
    companion object {
        /**
         * Nobody designated at all.
         *
         * A side with this carries no redirection: section 3.7's penalty,
         * free kick and olympic patches all fall through to the drawn
         * finisher.
         */
        @SpecRef("5.6")
        val NONE = Designated(taker = null, cornerTaker = null)
    }
}
