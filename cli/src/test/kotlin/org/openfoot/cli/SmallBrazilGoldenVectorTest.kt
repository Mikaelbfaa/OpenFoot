package org.openfoot.cli

import org.openfoot.engine.season.SmallBrazil
import org.openfoot.engine.season.WeeklyTick
import org.openfoot.engine.season.nextSeason
import org.openfoot.engine.season.openingSeason
import org.openfoot.engine.season.playSeason
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.RuleSets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two seasons of SmallBrazil at seed 42, printed once, pinned exactly: the
 * one golden vector that plays state championships, a Brazilian fourth
 * division built from them, a grouped league final phase and a turnover.
 * SmallBrazil's own docstring gives the fixture; everything below was
 * checked by hand against the pinned text.
 *
 * Match counts. Sao Paulo's preset 7 plays twelve rounds of eight cross
 * group matches, then quarter finals and semi finals of one leg and a two
 * legged final: 96 plus 4, 2 and 2. Rio Grande do Norte's preset 1 plays
 * fourteen rounds of four, then two legged semi finals and final: 56 plus 4
 * and 2. Divisions one to three play fourteen rounds of four, 56 each. The
 * fourth plays six rounds of two matches in each of its two groups, then two
 * legged semi finals and final: 24 plus 4 and 2. The cup seats the thirty
 * two best of forty and plays five two legged rounds: 62. That is 426 a
 * season. The dates are Rio Grande do Norte's eighteen, which Sao Paulo's
 * sixteen share, the fourteen league Sundays, on the first ten of which the
 * fourth plays, and ten cup Wednesdays: 42.
 *
 * One standing each. Every season prints four divisions of eight, and the
 * eight clubs in none of them are Brazil's reserve, forty clubs in all. Each
 * division table has fourteen or six games a club, and every table's
 * points are three a win and one a draw. Every knockout's final order reads
 * its merit order first, champion, runner-up and the losers by round, then
 * the rest of the table in table order.
 *
 * Notes. Season one's Sao Paulo division carries the real groups note,
 * because load rule six runs at world creation; season two's does not.
 * Division three is configured with a relegation playoff, and section 1.12
 * forces its relegation direct while the states feed the fourth, so it
 * carries no note in either season.
 *
 * Season one's fourth is the eight clubs SmallBrazil's docstring names:
 * sp25, sp27, sp29, sp33, rn26 and rn30 from the state queue, and br28 and
 * br31 from the stateless clubs. Its semi finals are internal to each group
 * (the engine test checks this pairing), so its two finalists come from
 * different groups. It is also Brazil's own deepest division, but prints no
 * in line: section 1.12 gives it no reserve swap of its own while the states
 * feed it, so it has nothing an in line could name.
 *
 * Moves. Every up and down line of season one is found again in season
 * two. br12 and rn10 went up and sit in season two's division one. sp05 and
 * br08 came down to division two, and sp19 and br24 went up to it. sp09 and
 * sp13 came down to division three, and the fourth's rn26 and br28 went up
 * to it. Division three's sp21 and sp23 went to the door and head season
 * two's fourth.
 *
 * Sao Paulo's and Rio Grande do Norte's own divisions are each their state's
 * only, and so deepest, division, and each carries an in line beside its
 * down line, the reserve arrivals no other line would print. Season one
 * sends sp29 and sp33 down to Sao Paulo's state reserve and brings sp39 then
 * sp37 in, the order the reserve queue already held them in since world
 * creation; the same season sends rn26 and rn22 down to Rio Grande do
 * Norte's reserve and brings rn34 then rn38 in. Season two's in lines read
 * back what season one's down lines wrote to the tail of each queue: sp29
 * then sp33 for Sao Paulo, rn26 then rn22 for Rio Grande do Norte.
 *
 * Season two's fourth is the door plus six state clubs from outside
 * divisions one to three. sp37 is left out: it is last in Sao Paulo's final
 * order, and the queue reaches rn30, last in the shorter Rio Grande do
 * Norte order, first.
 *
 * A changed figure reports on the season engine's reproducibility, or on a
 * deliberate change to how a season is built, played or turned over, and
 * must be argued for in a commit message rather than re-recorded.
 */
class SmallBrazilGoldenVectorTest {

    @Test
    fun `two seasons of the small Brazil print exactly as pinned`() {
        val world = generateWorld(SmallBrazil.dataset, seed = 42L, activeLeagues = SmallBrazil.activeLeagues)
        val first = playSeason(
            openingSeason(world, SmallBrazil.dataset, SmallBrazil.activeLeagues, year = SmallBrazil.YEAR, seed = 42L),
            RuleSets.CLASSIC,
            WeeklyTick.NONE,
        )
        val second = playSeason(nextSeason(first, SmallBrazil.activeLeagues, RuleSets.CLASSIC), RuleSets.CLASSIC, WeeklyTick.NONE)

        assertEquals(
            """
            season    1  year 2026  rounds 42  matches 426
              state:25:1  STATE  champion sp05  runner-up sp01
                note: the Sao Paulo real groups option is ignored; the groups are dealt from the queue
                pos  club  pts  pld  w  d   l  gf  ga
                  1  sp03   29   12  9  2   1  29  10
                  2  sp01   28   12  9  1   2  32  11
                  3  sp07   25   12  8  1   3  24  16
                  4  sp11   23   12  7  2   3  24  12
                  5  sp05   23   12  7  2   3  24  12
                  6  sp13   22   12  6  4   2  29  13
                  7  sp09   20   12  6  2   4  16  11
                  8  sp15   18   12  4  6   2  14  10
                  9  sp17   15   12  5  0   7  21  22
                 10  sp23   14   12  4  2   6  17  17
                 11  sp25   12   12  3  3   6  12  26
                 12  sp21   11   12  3  2   7  13  24
                 13  sp19   11   12  2  5   5  12  21
                 14  sp27    8   12  2  2   8  12  29
                 15  sp29    8   12  2  2   8  13  33
                 16  sp33    2   12  0  2  10  12  37
                final order: sp05, sp01, sp11, sp15, sp09, sp03, sp07, sp13, sp17, sp23, sp25, sp21, sp19, sp27, sp29, sp33
                up:
                down: sp29, sp33
                in: sp39, sp37
              state:19:1  STATE  champion rn06  runner-up rn02
                pos  club  pts  pld   w  d  l  gf  ga
                  1  rn06   37   14  12  1  1  35  14
                  2  rn14   24   14   7  3  4  33  24
                  3  rn02   24   14   7  3  4  27  18
                  4  rn10   16   14   4  4  6  27  27
                  5  rn18   15   14   4  3  7  17  25
                  6  rn30   15   14   4  3  7  14  27
                  7  rn26   13   14   4  1  9  24  33
                  8  rn22   13   14   3  4  7  14  23
                final order: rn06, rn02, rn10, rn14, rn18, rn30, rn26, rn22
                up:
                down: rn26, rn22
                in: rn34, rn38
              league:29:4  NATIONAL_LEAGUE  champion rn26  runner-up br28
                pos  club  pts  pld  w  d  l  gf  ga
                  1  rn26   13    6  4  1  1  12   7
                  2  sp25   13    6  4  1  1   9   5
                  3  br28    8    6  2  2  2   8   8
                  4  sp27    8    6  2  2  2   4   5
                  5  sp29    7    6  2  1  3   8   8
                  6  br31    6    6  1  3  2   7   8
                  7  rn30    5    6  1  2  3   8  11
                  8  sp33    5    6  1  2  3   6  10
                final order: rn26, br28, sp27, sp25, sp29, br31, rn30, sp33
                up: rn26, br28
                down: sp27, sp25, sp29, br31, rn30, sp33
              league:29:1  NATIONAL_LEAGUE  champion sp03  runner-up rn06
                pos  club  pts  pld  w  d  l  gf  ga
                  1  sp03   24   14  7  3  4  33  22
                  2  rn06   24   14  7  3  4  20  15
                  3  rn02   22   14  6  4  4  22  19
                  4  sp01   19   14  5  4  5  21  24
                  5  br04   17   14  4  5  5  20  22
                  6  sp07   17   14  4  5  5  21  25
                  7  sp05   14   14  3  5  6  20  23
                  8  br08   14   14  3  5  6  20  27
                up:
                down: sp05, br08
              league:29:2  NATIONAL_LEAGUE  champion br12  runner-up rn10
                pos  club  pts  pld  w  d  l  gf  ga
                  1  br12   24   14  6  6  2  20  18
                  2  rn10   22   14  7  1  6  25  16
                  3  br16   22   14  6  4  4  22  21
                  4  sp15   21   14  6  3  5  23  21
                  5  sp11   20   14  6  2  6  28  25
                  6  rn14   18   14  5  3  6  17  23
                  7  sp09   15   14  4  3  7  19  21
                  8  sp13   14   14  4  2  8  15  24
                up: br12, rn10
                down: sp09, sp13
              league:29:3  NATIONAL_LEAGUE  champion sp19  runner-up br24
                pos  club  pts  pld  w  d  l  gf  ga
                  1  sp19   26   14  7  5  2  19  15
                  2  br24   22   14  6  4  4  22  16
                  3  sp17   22   14  6  4  4  17  13
                  4  br20   21   14  5  6  3  25  21
                  5  rn18   19   14  5  4  5  20  19
                  6  rn22   16   14  4  4  6  20  21
                  7  sp21   16   14  4  4  6  17  19
                  8  sp23    8   14  1  5  8  12  28
                up: sp19, br24
                down: sp21, sp23
              cup:29  NATIONAL_CUP  champion rn06  runner-up br04
                final order: rn06, br04, br20, br08, br12, rn14, sp05, sp01, rn02, sp11, sp15, br16, sp09, sp07, sp03, sp13, sp23, br32, sp27, br31, br28, rn10, br24, sp29, rn30, rn18, sp19, rn22, sp17, sp21, sp25, rn26
              top scorers
                29  sp01 a3  sp01
                28  rn06 a1  rn06
                28  rn06 a2  rn06
                25  sp05 a2  sp05
                23  sp03 a2  sp03
            season    2  year 2027  rounds 42  matches 426
              state:25:1  STATE  champion sp07  runner-up sp01
                pos  club  pts  pld   w  d  l  gf  ga
                  1  sp05   32   12  10  2  0  25   9
                  2  sp01   26   12   8  2  2  22   7
                  3  sp07   25   12   8  1  3  27  11
                  4  sp11   25   12   7  4  1  28  14
                  5  sp03   22   12   6  4  2  24  10
                  6  sp13   18   12   5  3  4  18  17
                  7  sp09   17   12   5  2  5  18  18
                  8  sp15   17   12   5  2  5  13  18
                  9  sp23   15   12   5  0  7  17  29
                 10  sp21   15   12   4  3  5  15  18
                 11  sp19   14   12   4  2  6  13  18
                 12  sp27   13   12   4  1  7  15  27
                 13  sp25   10   12   3  1  8  19  24
                 14  sp17   10   12   3  1  8  17  24
                 15  sp39    8   12   2  2  8  13  25
                 16  sp37    5   12   1  2  9  10  25
                final order: sp07, sp01, sp11, sp05, sp09, sp03, sp15, sp13, sp23, sp21, sp19, sp27, sp25, sp17, sp39, sp37
                up:
                down: sp39, sp37
                in: sp29, sp33
              state:19:1  STATE  champion rn06  runner-up rn02
                pos  club  pts  pld  w  d   l  gf  ga
                  1  rn06   30   14  9  3   2  30  10
                  2  rn18   27   14  8  3   3  19  13
                  3  rn02   26   14  8  2   4  26  13
                  4  rn10   25   14  8  1   5  29  17
                  5  rn14   24   14  7  3   4  26  16
                  6  rn34   11   14  3  2   9  14  27
                  7  rn38   10   14  2  4   8  11  34
                  8  rn30    5   14  1  2  11  10  35
                final order: rn06, rn02, rn10, rn18, rn14, rn34, rn38, rn30
                up:
                down: rn38, rn30
                in: rn26, rn22
              league:29:4  NATIONAL_LEAGUE  champion sp21  runner-up rn34
                pos  club  pts  pld  w  d  l  gf  ga
                  1  sp21   18    6  6  0  0  16   7
                  2  rn34   10    6  3  1  2  11   9
                  3  sp25   10    6  2  4  0  10   8
                  4  rn38    9    6  3  0  3  13  12
                  5  sp27    8    6  2  2  2   6   8
                  6  sp39    6    6  2  0  4  10  12
                  7  sp23    4    6  1  1  4   8  10
                  8  rn30    3    6  1  0  5  10  18
                final order: sp21, rn34, sp25, rn38, sp27, sp39, sp23, rn30
                up: sp21, rn34
                down: sp25, rn38, sp27, sp39, sp23, rn30
              league:29:1  NATIONAL_LEAGUE  champion sp01  runner-up sp03
                pos  club  pts  pld  w  d   l  gf  ga
                  1  sp01   25   14  6  7   1  20  15
                  2  sp03   24   14  7  3   4  23  16
                  3  rn06   20   14  5  5   4  18  18
                  4  br12   19   14  5  4   5  18  14
                  5  br04   19   14  4  7   3  26  24
                  6  sp07   18   14  4  6   4  20  17
                  7  rn02   17   14  5  2   7  14  21
                  8  rn10    8   14  2  2  10  19  33
                up:
                down: rn02, rn10
              league:29:2  NATIONAL_LEAGUE  champion sp05  runner-up sp11
                pos  club  pts  pld  w  d  l  gf  ga
                  1  sp05   29   14  9  2  3  23  10
                  2  sp11   28   14  9  1  4  26  20
                  3  br08   25   14  8  1  5  23  20
                  4  br16   20   14  6  2  6  21  20
                  5  sp15   18   14  5  3  6  19  16
                  6  sp19   15   14  4  3  7  21  21
                  7  br24   12   14  3  3  8  11  25
                  8  rn14   11   14  2  5  7  15  27
                up: sp05, sp11
                down: br24, rn14
              league:29:3  NATIONAL_LEAGUE  champion sp09  runner-up sp13
                pos  club  pts  pld   w  d  l  gf  ga
                  1  sp09   31   14  10  1  3  32  15
                  2  sp13   26   14   8  2  4  30  20
                  3  sp17   21   14   6  3  5  21  19
                  4  br20   19   14   6  1  7  21  24
                  5  rn22   17   14   4  5  5  17  23
                  6  rn26   16   14   4  4  6  16  20
                  7  rn18   15   14   4  3  7  18  21
                  8  br28   13   14   4  1  9  13  26
                up: sp09, sp13
                down: rn18, br28
              cup:29  NATIONAL_CUP  champion sp01  runner-up rn06
                final order: sp01, rn06, rn02, sp03, rn10, sp05, br16, sp09, sp13, rn22, rn14, sp07, br08, sp15, br04, br12, rn30, br24, sp25, sp11, sp17, br32, sp19, br28, sp29, sp21, sp23, br20, rn18, br31, sp27, rn26
              top scorers
                27  sp03 a3  sp03
                25  sp11 a1  sp11
                24  rn06 a2  rn06
                24  sp05 a2  sp05
                23  rn06 a1  rn06
            """.trimIndent() + "\n",
            describeSeason(first) + describeSeason(second),
        )
    }
}
