package org.openfoot.cli

import org.openfoot.engine.season.WeeklyTick
import org.openfoot.engine.season.openingSeason
import org.openfoot.engine.season.playSeason
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.RuleSets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One season of the GoldenWorld fixture, played once, pinned exactly.
 *
 * GoldenWorld's own two-midfielder squads (WorldGoldenVectorTest's own
 * docstring explains why: a small squad keeps every strength a two-term draw
 * this project can bound by hand) turned out to sustain a whole season with
 * no failure of any kind: fillEleven fields fewer than eleven rather than
 * refusing outright whenever a squad runs short, per its own docstring in
 * AutoLineup.kt, so a club fielding at most two men every match all season
 * is exactly as legal as a club fielding eleven. Nothing about this fixture
 * needed building a fuller squad of its own for this task.
 *
 * Country FIX plays its league with eleven clubs, level twenty down to ten
 * (GoldenWorld's own docstring); WorldGoldenVectorTest already pins that
 * DIVISION_STEPS seats the top ten of them, clube-01 through clube-10, in
 * division one, leaving clube-11 without one. defaultTurns(10) is four, so
 * the division plays a double turn and its mirror twice over: nine rounds
 * per turn times four turns is thirty-six rounds, five matches a round for
 * ten clubs, a hundred and eighty league matches in all, thirty-six played
 * by every one of the ten clubs in the division. The national cup takes the
 * largest power of two at most eleven and at least eight, which is eight;
 * clube-11, the only club left out of the division, is also the weakest of
 * the eleven and therefore the one bracketSize's top-eight-by-level cut
 * leaves out of the cup too. Eight sides play three rounds, every round two
 * legged, for eight plus four plus two, fourteen cup matches; a finished
 * eight side bracket lists eight names in KnockoutPhase.meritOrder, which is
 * exactly the length of the cup's own final order line below. A hundred and
 * eighty plus fourteen is a hundred and ninety-four, the printed match
 * count; the printed round count, forty-two, is thirty-six Sundays for the
 * league plus six fortnightly Wednesdays for the cup, and the two calendars
 * never land on the same day by SeasonSchedule's own construction, so every
 * one of the forty-two is a distinct date.
 *
 * The cup, playing on Wednesdays from week twelve two at a time, finishes
 * long before the league, which needs thirty-six consecutive Sundays from
 * the same week twelve; describeSeason prints competitions in the order
 * state.closed records them; closing first is exactly why the cup is
 * printed before the league below, not a sorted or a hand chosen order.
 *
 * Every league row was checked by hand against its own wins, draws and
 * losses: three times wins plus draws is that row's points on every line
 * (for instance the champion, clube-08, sixty-one points from eighteen
 * wins and seven draws, three times eighteen plus seven), wins plus draws
 * plus losses is thirty-six on every line, goals for and goals against both
 * total six hundred and twelve, and the champion, clube-08, sits first,
 * which is the table's own order since this league has no final phase past
 * its one round robin for finalOrder to read instead. The one tie on points,
 * clube-02 and clube-04 at fifty-four, is broken by wins before goal
 * difference, as section 1.2's fixed comparator orders it: clube-02 has
 * sixteen wins to fourteen and sits above, although clube-04's goal
 * difference is the better of the two.
 *
 * The five top scorers are ordered by goals descending, and no two of them
 * share a count this season, so the name and club tie break below goals is
 * not reached here. The high goal counts, up to eighty-eight from a single
 * man, are a direct consequence of the two-midfielder fixture rather than a
 * defect of this printer or of the season engine: with only two men ever
 * eligible for a club's midfield and attack across a hundred and ninety-four
 * matches of a whole season, the same pair of names is credited with
 * essentially every goal their own club ever scores.
 *
 * Suspensions cost matches. Section 3.1 serves a club's suspensions before
 * it applies the match's cards and section 3.8 keeps the card record per
 * competition, so a ban earned in a league match is served by the club's
 * next league match and never by a cup match. In squads this small a man
 * sitting out a ban is felt at once, which is why the league table here is
 * not the one a season with bans served the day they were earned printed;
 * the cup, played by the same sides over six Wednesdays, came out the same.
 *
 * The pinned string below was produced by running this exact season and
 * copying the printed text verbatim, then checked line by line against
 * every fact worked out above. A changed figure reports on the season
 * engine's own reproducibility, or on a deliberate change to how a season
 * is built or played, and either way must be argued for in a commit
 * message rather than re-recorded silently.
 */
class SeasonGoldenVectorTest {

    @Test
    fun `one season of the golden fixture prints exactly as pinned`() {
        val activeLeagues = setOf(GoldenWorld.fixCountry.index)
        val world = generateWorld(GoldenWorld.dataset, seed = 42L, activeLeagues = activeLeagues)
        var state = openingSeason(world, GoldenWorld.dataset, activeLeagues, year = 2026, seed = 42L)
        state = playSeason(state, RuleSets.CLASSIC, WeeklyTick.NONE)

        assertEquals(
            """
            season    1  year 2026  rounds 42  matches 194
              cup:701  NATIONAL_CUP  champion clube-01  runner-up clube-03
                final order: clube-01, clube-03, clube-02, clube-04, clube-08, clube-07, clube-06, clube-05
              league:701:1  NATIONAL_LEAGUE  champion clube-08  runner-up clube-01
                pos  club      pts  pld   w   d   l  gf  ga
                  1  clube-08   61   36  18   7  11  63  60
                  2  clube-01   58   36  17   7  12  78  61
                  3  clube-05   56   36  15  11  10  60  51
                  4  clube-07   55   36  15  10  11  67  56
                  5  clube-02   54   36  16   6  14  67  63
                  6  clube-04   54   36  14  12  10  71  61
                  7  clube-10   46   36  13   7  16  57  65
                  8  clube-06   43   36  11  10  15  56  61
                  9  clube-03   36   36  10   6  20  48  71
                 10  clube-09   35   36   9   8  19  45  63
              top scorers
                88  clube-01 jogador 1  clube-01
                77  clube-04 jogador 1  clube-04
                76  clube-02 jogador 2  clube-02
                68  clube-07 jogador 2  clube-07
                65  clube-08 jogador 2  clube-08
            """.trimIndent() + "\n",
            describeSeason(state),
        )
    }
}
