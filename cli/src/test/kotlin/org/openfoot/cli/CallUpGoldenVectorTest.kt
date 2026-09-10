package org.openfoot.cli

import org.openfoot.engine.world.callUpNationalTeam
import org.openfoot.engine.world.generateWorld
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two call-ups from the GoldenWorld fixture, printed once, pinned exactly.
 *
 * Country REP has no player of its own anywhere in the world, so its call-up
 * fails both sufficiency tests of section 4.12 and generates the synthetic
 * batch of twenty: three keepers, four fullbacks, four centrebacks, five
 * midfielders and four forwards, named by the country's dataset name and a
 * creation counter (OPEN-QUESTIONS item 63), so the counter says which
 * position each man was created as: 1 to 3 keepers, 4 to 7 fullbacks, 8 to
 * 11 centrebacks, 12 to 16 midfielders, 17 to 20 forwards. REP is level
 * twenty, mapped twenty five, so every one of them has strength 25 - 5 +
 * rand(8), that is 20 to 27, with no country scale and no cap; every age is
 * 18 + rand(12), 18 to 29. The pool is those twenty and nobody else, short
 * of twenty three, so step 5 calls the whole batch and the list is twenty
 * men, stored strength descending and then age descending. The reputation
 * is five, the top rung of the level ladder. The taker is the strongest
 * outfielder by the third branch of section 5.6, since no free agent is a
 * starter.
 *
 * Country FIX has twenty four club players and not one keeper, so both
 * sufficiency tests fail on the keeper minimum and FIX generates its own
 * batch too; the pool is forty four, not short of twenty three, so step 5
 * never runs and the quotas alone decide. The club men are all right sided
 * offensive midfielders, since the fixture's stamina and crossing pair
 * matches no test of the midfielder chain of section 4.3 and falls to its
 * offensive default, and the only cell they fit is the right playmaker's,
 * quota two: the two strongest are clube-01's pair, division one at level
 * twenty, 25 + 20 + rand(3), 45 to 47, which WorldGoldenVectorTest pins at
 * 47 and 45, and they head the list at 47 and 46 while the other twenty two
 * club men are skipped. Every other cell is filled from the batch as its
 * drawn sides and styles allow, and four cells go short for want of a
 * candidate of the right side: the right centreback cell seats one man, the
 * left playmaker cell one of its three, the left forward cell one of its
 * two; that is OPEN-QUESTIONS item 64 made visible in a printout, nineteen
 * called and nothing completes the list. The taker is again the strongest
 * outfielder, clube-01's stronger man.
 *
 * The pinned strings below were produced by running these exact call-ups
 * and then checked line by line against the ranges above: every strength
 * in 20 to 27 or, for the two club men, in 45 to 47; every age in 18 to 29
 * or 25 for a club man; every counter's position matching the batch order;
 * the list order strength then age descending. A changed figure reports on
 * the generator's reproducibility or on a deliberate change to section 4.12,
 * and either way must be argued for in a commit message.
 */
class CallUpGoldenVectorTest {

    private val world = generateWorld(GoldenWorld.dataset, seed = 42L, activeLeagues = setOf(GoldenWorld.fixCountry.index))

    @Test
    fun `a country with no players calls up its synthetic batch exactly as pinned`() {
        val team = callUpNationalTeam(world, GoldenWorld.dataset, GoldenWorld.repCountry.index)
        assertEquals(
            """
            country   Fixture Reputacao  level 20  reputation 5
            called    20  free agents 20
            taker     Fixture Reputacao-0011
              CENTREBACK  RIGHT  DEFENSIVE  strength  27  age 20  Fixture Reputacao-0011  free agent
              MIDFIELDER  RIGHT  DEFENSIVE  strength  26  age 28  Fixture Reputacao-0016  free agent
              FORWARD     RIGHT  WINGER     strength  26  age 27  Fixture Reputacao-0018  free agent
              FORWARD     RIGHT  OFFENSIVE  strength  26  age 20  Fixture Reputacao-0020  free agent
              FORWARD     LEFT   WINGER     strength  26  age 19  Fixture Reputacao-0019  free agent
              MIDFIELDER  RIGHT  DEFENSIVE  strength  25  age 27  Fixture Reputacao-0012  free agent
              FULLBACK    LEFT   DEFENSIVE  strength  25  age 25  Fixture Reputacao-0007  free agent
              CENTREBACK  RIGHT  DEFENSIVE  strength  25  age 21  Fixture Reputacao-0010  free agent
              MIDFIELDER  RIGHT  OFFENSIVE  strength  24  age 27  Fixture Reputacao-0015  free agent
              FULLBACK    LEFT   OFFENSIVE  strength  24  age 26  Fixture Reputacao-0004  free agent
              MIDFIELDER  LEFT   OFFENSIVE  strength  23  age 22  Fixture Reputacao-0014  free agent
              MIDFIELDER  LEFT   DEFENSIVE  strength  23  age 21  Fixture Reputacao-0013  free agent
              GOALKEEPER  LEFT   DEFENSIVE  strength  22  age 26  Fixture Reputacao-0002  free agent
              CENTREBACK  RIGHT  DEFENSIVE  strength  22  age 20  Fixture Reputacao-0009  free agent
              CENTREBACK  RIGHT  DEFENSIVE  strength  21  age 26  Fixture Reputacao-0008  free agent
              GOALKEEPER  RIGHT  DEFENSIVE  strength  21  age 20  Fixture Reputacao-0003  free agent
              GOALKEEPER  LEFT   DEFENSIVE  strength  20  age 29  Fixture Reputacao-0001  free agent
              FULLBACK    LEFT   OFFENSIVE  strength  20  age 22  Fixture Reputacao-0006  free agent
              FORWARD     RIGHT  OFFENSIVE  strength  20  age 22  Fixture Reputacao-0017  free agent
              FULLBACK    RIGHT  DEFENSIVE  strength  20  age 18  Fixture Reputacao-0005  free agent
            """.trimIndent() + "\n",
            describeCallUp(team, GoldenWorld.dataset),
        )
    }

    @Test
    fun `a country whose club men all fit one cell calls up nineteen exactly as pinned`() {
        val team = callUpNationalTeam(world, GoldenWorld.dataset, GoldenWorld.fixCountry.index)
        assertEquals(
            """
            country   Fixture Ativo  level 20  reputation 5
            called    19  free agents 17
            taker     clube-01 jogador 2
              MIDFIELDER  RIGHT  OFFENSIVE  strength  47  age 25  clube-01 jogador 2  clube-01
              MIDFIELDER  RIGHT  OFFENSIVE  strength  46  age 25  clube-01 jogador 1  clube-01
              CENTREBACK  RIGHT  DEFENSIVE  strength  27  age 25  Fixture Ativo-0011  free agent
              FORWARD     RIGHT  OFFENSIVE  strength  27  age 22  Fixture Ativo-0020  free agent
              GOALKEEPER  LEFT   DEFENSIVE  strength  27  age 21  Fixture Ativo-0003  free agent
              FORWARD     RIGHT  WINGER     strength  26  age 27  Fixture Ativo-0019  free agent
              CENTREBACK  LEFT   DEFENSIVE  strength  25  age 21  Fixture Ativo-0009  free agent
              FULLBACK    RIGHT  DEFENSIVE  strength  25  age 18  Fixture Ativo-0004  free agent
              MIDFIELDER  RIGHT  DEFENSIVE  strength  23  age 28  Fixture Ativo-0015  free agent
              FULLBACK    LEFT   DEFENSIVE  strength  23  age 26  Fixture Ativo-0007  free agent
              GOALKEEPER  RIGHT  DEFENSIVE  strength  23  age 25  Fixture Ativo-0002  free agent
              FULLBACK    LEFT   DEFENSIVE  strength  23  age 23  Fixture Ativo-0005  free agent
              CENTREBACK  LEFT   DEFENSIVE  strength  23  age 23  Fixture Ativo-0008  free agent
              MIDFIELDER  LEFT   DEFENSIVE  strength  23  age 20  Fixture Ativo-0013  free agent
              FORWARD     LEFT   OFFENSIVE  strength  22  age 20  Fixture Ativo-0017  free agent
              FULLBACK    RIGHT  DEFENSIVE  strength  21  age 27  Fixture Ativo-0006  free agent
              GOALKEEPER  RIGHT  DEFENSIVE  strength  20  age 22  Fixture Ativo-0001  free agent
              MIDFIELDER  RIGHT  DEFENSIVE  strength  20  age 21  Fixture Ativo-0012  free agent
              MIDFIELDER  LEFT   OFFENSIVE  strength  20  age 19  Fixture Ativo-0014  free agent
            """.trimIndent() + "\n",
            describeCallUp(team, GoldenWorld.dataset),
        )
    }
}
