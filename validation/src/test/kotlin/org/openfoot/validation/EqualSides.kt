package org.openfoot.validation

import org.openfoot.engine.match.MatchPlayer
import org.openfoot.engine.match.MatchSetup
import org.openfoot.engine.match.MatchSide
import org.openfoot.engine.match.StrengthContext
import org.openfoot.model.Attr
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Marking
import org.openfoot.model.PlayerId
import org.openfoot.model.PlayerStyle
import org.openfoot.model.Position
import org.openfoot.model.RuleSet
import org.openfoot.model.RuleSets
import org.openfoot.model.Side
import org.openfoot.model.Slot
import org.openfoot.model.SpecRef
import org.openfoot.model.Trait

/**
 * The pairing section 3.16 describes: two equivalent sides, neither human, on a
 * normal ground, in season one.
 *
 * Individual abilities are off, so a player's rating is his strength over ten
 * and every figure in the check can be worked out by hand.
 */
@SpecRef("3.16")
object EqualSides {

    /** Formation four, the four four two the AI picks most often. */
    @SpecRef("3.2")
    val FORMATION_4_4_2 = listOf(1, 22, 24, 11, 13, 14, 16, 2, 9, 3, 5)

    /**
     * Strength fifty, so both sides rate five out of ten on every line and the
     * duel probabilities of section 3.6 reduce to their equal sides values.
     */
    @SpecRef("3.16")
    const val EQUAL_STRENGTH = 50

    /**
     * Reputation three on both sides, so the reputation term of section 3.3
     * cancels and neither side is favoured by anything except the ground.
     */
    @SpecRef("3.3")
    const val EQUAL_REPUTATION = 3

    /** Season one, the season section 3.16 measures. */
    @SpecRef("3.16")
    const val FIRST_SEASON = 1

    /**
     * The age every player of the section 3.16 pairing carries.
     *
     * One age for all twenty two, so that section 3.9's drain costs the same
     * everywhere and no figure section 3.16 measures can move because one side
     * happened to be older. It also puts the whole pairing in a single bracket
     * of section 3.9's cost table and a single term of section 3.8's injury
     * duration, which is exactly what benchedFixture below exists to widen.
     */
    @SpecRef("3.9")
    const val EQUAL_AGE = 25

    /**
     * Side and style are fixed at Side.RIGHT and PlayerStyle.OFFENSIVE for
     * every one of the eleven. No figure section 3.16 measures reads either
     * property, so a constant that says nothing about the individual player is
     * an honest stand in here, unlike Lineups.player's defaults, which a
     * caller can override per player.
     */
    fun side(
        strength: Int,
        isHome: Boolean,
        formation: List<Int> = FORMATION_4_4_2,
        ages: List<Int> = List(formation.size) { EQUAL_AGE },
    ): MatchSide {
        require(ages.size == formation.size) {
            "${formation.size} cells were given ${ages.size} ages"
        }
        val context = StrengthContext(
            kind = CompetitionKind.NATIONAL_LEAGUE,
            useIndividualAbilities = false,
            sideReputation = EQUAL_REPUTATION,
            sideCountry = Country.BRAZIL,
            sideContinent = SOUTH_AMERICA_CONTINENT,
            isHomeSide = isHome,
            homeReputation = EQUAL_REPUTATION,
            awayReputation = EQUAL_REPUTATION,
        )
        val lineup = formation.mapIndexed { index, slot ->
            val cell = Slot(slot)
            MatchPlayer(
                id = PlayerId(index),
                slot = cell,
                naturalPosition = cell.requiredPosition ?: Position.MIDFIELDER,
                age = ages[index],
                strength = strength,
                abilities = IntArray(Attr.COUNT),
                firstTrait = Trait.STAMINA,
                secondTrait = Trait.CROSSING,
                side = Side.RIGHT,
                style = PlayerStyle.OFFENSIVE,
            )
        }
        return MatchSide(lineup = lineup, marking = Marking.LIGHT, context = context)
    }

    fun setup(
        homeStrength: Int = EQUAL_STRENGTH,
        awayStrength: Int = EQUAL_STRENGTH,
        season: Int = FIRST_SEASON,
        rules: RuleSet = RuleSets.CLASSIC,
    ) = MatchSetup(
        home = side(homeStrength, isHome = true),
        away = side(awayStrength, isHome = false),
        season = season,
        rules = rules,
    )

    /**
     * The only eleven man lineup that puts the defence and the attack exactly
     * on their fixed divisors: five defenders over a divisor of five and three
     * forwards over a divisor of three, which makes both line ratings equal to
     * a single player's rating.
     *
     * It costs a shorthanded midfield, which section 3.4 collapses to the
     * degenerate rating. That is harmless here because it happens to both sides
     * and the possession duel reads only the difference.
     */
    @SpecRef("3.4")
    val FORMATION_LINES_AT_DIVISOR = listOf(1, 20, 22, 24, 13, 14, 2, 9, 3, 5, 7)

    fun linesAtDivisorSetup() = MatchSetup(
        home = side(EQUAL_STRENGTH, isHome = true, formation = FORMATION_LINES_AT_DIVISOR),
        away = side(EQUAL_STRENGTH, isHome = false, formation = FORMATION_LINES_AT_DIVISOR),
        season = FIRST_SEASON,
        rules = RuleSets.CLASSIC,
    )

    /**
     * The two sides of benchedFixture together with the reserves that setup
     * itself has no room for, mirroring AssembledMatch: a bench is not part of
     * a minute of play, only of a substitution, so it travels beside the setup
     * rather than inside it.
     */
    @SpecRef("3.8")
    class BenchedFixture(
        @property:SpecRef("3.8") val setup: MatchSetup,
        @property:SpecRef("3.8") val homeBench: List<MatchPlayer>,
        @property:SpecRef("3.8") val awayBench: List<MatchPlayer>,
    )

    /**
     * The pairing the section 3.16 fixture above cannot be: one that can
     * actually substitute, and whose players are not all the same age.
     *
     * setup and linesAtDivisorSetup are the fixtures of section 3.16, and both
     * play with an empty bench on purpose, since section 3.16's own figures
     * were measured against sides that never replace anybody. That leaves them
     * blind in three separate ways, and every aggregate read off them is blind
     * in the same three ways with them.
     *
     * The first is substitutions. An empty bench fails canSubstitute, so both
     * plans are blanked at kick off and not one of section 3.8's four windows
     * can ever open. Nothing measured against those two fixtures moves at all
     * when the substitution rules change, however wrong they are.
     *
     * The second is the age brackets. Every player there is twenty five, which
     * is one bracket of section 3.9's cost table out of five and one term of
     * section 3.8's injury duration out of six. In particular the duration of
     * nought that section 3.8 says registers no injury at all is reachable
     * only at twenty or under, so those two fixtures cannot reach it and
     * cannot tell whether it is logged.
     *
     * The third is the risk groups, and it is the one this fixture does not
     * change: formation four occupies all seven groups of section 3.8 at kick
     * off, so an attempt that draws an empty group needs somebody to have left
     * the pitch first.
     *
     * That is rare rather than impossible, and the difference matters, because
     * the three counters of section 3.8 move on such an attempt even though
     * nothing reaches the log. Two things make it likelier than it sounds. A
     * dismissal is not the only way a cell empties: the section 3.16 pairing
     * has no bench, so nobody who leaves is replaced and an injury empties a
     * cell exactly as a dismissal does, and injuries are about two departures
     * in five there. And the seven groups are not evenly occupied: group four
     * is cells 8 to 9 and formation four stands only slot 9 in it, while the
     * keeper's group holds only slot 1, so a single departure empties either
     * of those two.
     *
     * It is still far too rare for any aggregate band to see, a few times in
     * twenty thousand matches, which is why nothing here measures it and why
     * DisciplineChainTest pins the path with a scripted draw instead. That
     * scripted coverage is the only coverage the path has and must not be
     * dropped on the strength of formation four filling all seven groups.
     *
     * Everything else is held at the section 3.16 pairing's own values. Both
     * sides field formation four in the same order, every one of the forty
     * four players rates the same fifty, and the benches are built from the
     * same template, so the two sides stay equivalent and the difference
     * between what they produce is section 3.8's own, not a strength gap.
     */
    @SpecRef("3.8")
    fun benchedFixture() = BenchedFixture(
        setup = MatchSetup(
            home = side(EQUAL_STRENGTH, isHome = true, ages = BENCHED_PITCH_AGES),
            away = side(EQUAL_STRENGTH, isHome = false, ages = BENCHED_PITCH_AGES),
            season = FIRST_SEASON,
            rules = RuleSets.CLASSIC,
        ),
        homeBench = bench(),
        awayBench = bench(),
    )

    /**
     * One age per cell of FORMATION_4_4_2, in that list's own order, so cell 1
     * holds the thirty three year old, cell 22 the twenty six year old and so
     * on down to cell 5 and the thirty eight year old.
     *
     * Chosen to reach every bracket of both age tables at once. Section 3.9's
     * drain costs one to five across bounds of twenty, twenty five, thirty
     * one, thirty six and above, and the eleven ages below land in all five.
     * Section 3.8's injury duration has terms at twenty, twenty five, thirty,
     * thirty five, forty five and above, and they land in the first five of
     * those six; nobody over forty five is fielded, because no plausible squad
     * holds one and InjuryTest covers that term directly instead.
     *
     * The two youngest, nineteen and twenty, stand in cells 11 and 13. That is
     * deliberate rather than incidental: section 3.8's injury risk table gives
     * the group of cells 10 to 13 the largest share of any group, 29.8 per
     * cent, so putting the only two players who can draw a duration of nought
     * there is what makes that case reachable often enough to measure. With
     * them anywhere else the whole sample produces it a handful of times.
     */
    @SpecRef("3.9")
    val BENCHED_PITCH_AGES = listOf(33, 26, 30, 19, 20, 23, 25, 27, 36, 31, 38)

    /**
     * One age per place of the classic rules' bench template, in that
     * template's own order, spread across the same brackets the eleven above
     * cover so that a reserve who comes on is not all one age either.
     */
    @SpecRef("3.9")
    val BENCHED_BENCH_AGES = listOf(18, 21, 22, 24, 28, 29, 32, 34, 35, 37, 39)

    /**
     * One side's reserves, built from the model cells of the classic rules'
     * bench template.
     *
     * Every reserve carries the position, the flank and the sub role his model
     * cell asks for, so the relaxed search of section 5.4 decides who comes on
     * by fit rather than falling through to its catch all. Two of the eleven
     * are keepers, which is what the template asks for and what makes section
     * 3.8's inverted keeper refusal reachable: a reserve keeper is the one man
     * who may not take an injured outfielder's cell.
     *
     * Every reserve sits on Slot.UNUSED_SUBSTITUTE, the value the original
     * leaves on a substitute who has not come on, and identities continue past
     * the eleven's, since energy and bookings are kept by identity and a
     * collision inside one side would give two men one record.
     *
     * Called once per side rather than shared between them. The two lists come
     * out equal in value and distinct in object, which is what MatchPlayer's
     * reference equality asks of anything that has to stay two separate
     * benches.
     */
    @SpecRef("5.4")
    fun bench(): List<MatchPlayer> =
        RuleSets.CLASSIC.benchTemplate.mapIndexed { index, cell ->
            val model = Slot(cell)
            MatchPlayer(
                id = PlayerId(FORMATION_4_4_2.size + index),
                slot = Slot.UNUSED_SUBSTITUTE,
                naturalPosition = model.requiredPosition ?: Position.MIDFIELDER,
                age = BENCHED_BENCH_AGES[index],
                strength = EQUAL_STRENGTH,
                abilities = IntArray(Attr.COUNT),
                firstTrait = Trait.STAMINA,
                secondTrait = Trait.CROSSING,
                side = model.requiredSide ?: Side.RIGHT,
                style = model.requiredStyle ?: PlayerStyle.OFFENSIVE,
            )
        }

    /**
     * The continent both sides belong to. Only the European exemption of
     * section 3.3 reads this, and neither side is European, so the value only
     * has to be the same on both sides.
     */
    @SpecRef("3.3")
    private const val SOUTH_AMERICA_CONTINENT = 1
}
