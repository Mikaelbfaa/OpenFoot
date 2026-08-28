package org.openfoot.engine.match

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Finding 6. simulateMatch derives one stream per minute by tag, plus five
 * fixed streams: SETUP_STREAM and SUBSTITUTION_PLAN_STREAM under the match,
 * and PLAY_STREAM, DISCIPLINE_STREAM and SUBSTITUTION_STREAM under a minute.
 * Nothing in the type system stops a future edit from picking a fixed stream
 * tag that collides with a minute index or with one of the other fixed
 * streams, which would make two draws that are supposed to be independent
 * come from the same fork. These tests lock the reservation down so such a
 * collision fails loudly here instead of silently making two minutes, or a
 * minute and the discipline draws, play identically.
 *
 * All five are checked against all five and against the minute range, rather
 * than only the pairs that share a fork level today. Which tags share a level
 * is a fact about the current wiring and has already changed once, so the
 * cheaper assertion would have to be revisited every time it moves.
 *
 * SUBSTITUTION_PLAN_STREAM used to be forked once more, by the side's ordinal,
 * because the two sides drew independent plans. Section 3.15 item 8's shared
 * shuffle made that one draw for the match, so the stream is now read directly
 * and its children nought and one have no caller. They are held reserved
 * rather than freed, which the constant's own docstring says and which the
 * count in the scripted test of SubstitutionPlanTest enforces: one script
 * feeding one generator produces both plans, so nothing can be forking per
 * side behind it. There is nothing to add to the list below, since the freed
 * tags were never fixed streams of their own.
 */
class SeedStreamsTest {

    @Test
    fun `the five fixed streams are pairwise distinct`() {
        val streams = FIXED_STREAMS.map { it.second }
        assertEquals(streams.size, streams.toSet().size, "the fixed streams must not collide: $streams")

        for (first in FIXED_STREAMS) {
            for (second in FIXED_STREAMS) {
                if (first.first != second.first) {
                    assertNotEquals(
                        first.second,
                        second.second,
                        "${first.first} and ${second.first} must not share a tag",
                    )
                }
            }
        }
    }

    /**
     * A minute index runs from zero up to, but not including, the longest
     * legal match's total minutes. A fixed tag sharing a fork level with those
     * tags is only safe while no minute index can ever reach it, so this
     * derives the ceiling from the same constants matchClock draws from rather
     * than repeating the 91 to 97 range as a second literal.
     */
    @Test
    fun `every fixed stream sits outside the range a minute index can reach`() {
        val longestMatchMinutes = REGULATION_HALF_MINUTES + FIRST_HALF_STOPPAGE_MAX +
            REGULATION_HALF_MINUTES + SECOND_HALF_STOPPAGE_MAX
        val highestPossibleMinuteIndex = longestMatchMinutes - 1

        for ((name, tag) in FIXED_STREAMS) {
            assertTrue(
                tag > highestPossibleMinuteIndex.toLong(),
                "$name ($tag) must be outside the reachable minute range " +
                    "(0..$highestPossibleMinuteIndex), or a long match could fork the same stream twice",
            )
        }
    }

    private companion object {

        /**
         * Every fixed tag the match forks by, with its name, so a failure says
         * which one collided rather than only which number did.
         */
        val FIXED_STREAMS = listOf(
            "SETUP_STREAM" to SETUP_STREAM,
            "PLAY_STREAM" to PLAY_STREAM,
            "DISCIPLINE_STREAM" to DISCIPLINE_STREAM,
            "SUBSTITUTION_PLAN_STREAM" to SUBSTITUTION_PLAN_STREAM,
            "SUBSTITUTION_STREAM" to SUBSTITUTION_STREAM,
        )
    }
}
