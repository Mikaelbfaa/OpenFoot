package org.openfoot.engine.match

import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.Trait
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShooterSelectionTest {

    private val rules = RuleSets.CLASSIC

    @Test
    fun `cell weights follow the spec table`() {
        assertEquals(1, shooterWeight(Lineups.player(2, 50), rules))
        assertEquals(1, shooterWeight(Lineups.player(9, 50), rules))
        assertEquals(8, shooterWeight(Lineups.player(10, 50), rules))
        assertEquals(4, shooterWeight(Lineups.player(11, 50), rules))
        assertEquals(4, shooterWeight(Lineups.player(12, 50), rules))
        assertEquals(4, shooterWeight(Lineups.player(13, 50), rules))
        assertEquals(8, shooterWeight(Lineups.player(14, 50), rules))
        assertEquals(8, shooterWeight(Lineups.player(17, 50), rules))
        assertEquals(22, shooterWeight(Lineups.player(18, 50), rules))
        assertEquals(22, shooterWeight(Lineups.player(25, 50), rules))
    }

    @Test
    fun `finishing adds four`() {
        val striker = Lineups.player(20, 50, firstTrait = Trait.FINISHING, secondTrait = Trait.PACE)
        assertEquals(26, shooterWeight(striker, rules))
    }

    @Test
    fun `finishing wins outright over heading`() {
        val both = Lineups.player(20, 50, firstTrait = Trait.FINISHING, secondTrait = Trait.HEADING)
        assertEquals(26, shooterWeight(both, rules))
    }

    @Test
    fun `a heading centre back gets the extra defender bonus`() {
        val stopper = Lineups.player(5, 50, firstTrait = Trait.HEADING, secondTrait = Trait.TACKLING)
        assertEquals(5, shooterWeight(stopper, rules))
    }

    @Test
    fun `a heading midfielder gets only the plain bonus`() {
        val midfielder = Lineups.player(14, 50, firstTrait = Trait.HEADING, secondTrait = Trait.PASSING)
        assertEquals(10, shooterWeight(midfielder, rules))
    }

    @Test
    fun `a fullback heading is not a defender for this bonus`() {
        val fullback = Lineups.player(2, 50, firstTrait = Trait.HEADING, secondTrait = Trait.PACE)
        assertEquals(3, shooterWeight(fullback, rules))
    }

    @Test
    fun `the trait counts in either position`() {
        val first = Lineups.player(20, 50, firstTrait = Trait.FINISHING, secondTrait = Trait.PACE)
        val second = Lineups.player(20, 50, firstTrait = Trait.PACE, secondTrait = Trait.FINISHING)
        assertEquals(shooterWeight(first, rules), shooterWeight(second, rules))
    }

    @Test
    fun `the keeper and the bench carry no cell weight`() {
        assertEquals(0, shooterWeight(Lineups.player(1, 50), rules))
        assertEquals(0, shooterWeight(Lineups.player(30, 50), rules))
    }

    @Test
    fun `the four four two weights total seventy two`() {
        val side = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
        val total = side.lineup
            .filter { it.slot.value in rules.shooterEligibleSlots }
            .sumOf { shooterWeight(it, rules) }
        assertEquals(72, total)
    }

    @Test
    fun `the keeper is never drawn`() {
        val side = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
        val rng = SplitMix64Rng(3)
        repeat(20_000) {
            val shooter = selectShooter(side, rules, rng)
            assertTrue(shooter != null && shooter.slot.value != 1, "the keeper was drawn")
        }
    }

    @Test
    fun `a striker takes about twenty two of every seventy two shots`() {
        val side = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
        val rng = SplitMix64Rng(5)
        val draws = 100_000
        val striker = (1..draws).count { selectShooter(side, rules, rng)?.slot?.value == 22 }
        val share = striker.toDouble() / draws
        assertTrue(abs(share - 22.0 / 72.0) < 0.006, "expected about 0.3056, measured $share")
    }

    @Test
    fun `defenders share a small slice of the shots`() {
        val side = Lineups.sideOfSlots(Lineups.FORMATION_4_4_2, strength = 50)
        val rng = SplitMix64Rng(13)
        val draws = 100_000
        val defenders = (1..draws).count {
            selectShooter(side, rules, rng)?.slot?.value in listOf(2, 9, 3, 5)
        }
        val share = defenders.toDouble() / draws
        assertTrue(abs(share - 4.0 / 72.0) < 0.004, "expected about 0.0556, measured $share")
    }

    @Test
    fun `a bench striker with finishing is never drawn`() {
        val pitch = Lineups.FORMATION_4_4_2.map { Lineups.player(it, 50) }
        val benchStriker = Lineups.player(
            slot = 30,
            strength = 99,
            firstTrait = Trait.FINISHING,
            secondTrait = Trait.PACE,
        )
        val side = Lineups.side(pitch + benchStriker)
        val rng = SplitMix64Rng(17)
        repeat(20_000) {
            assertTrue(selectShooter(side, rules, rng)?.slot?.value != 30, "a bench player shot")
        }
    }

    @Test
    fun `slot eighteen can shoot even though it feeds no line`() {
        val side = Lineups.sideOfSlots(Lineups.FORMATION_3_4_3, strength = 50)
        val rng = SplitMix64Rng(19)
        val drawn = (1..5_000).any { selectShooter(side, rules, rng)?.slot?.value == 18 }
        assertTrue(drawn, "the slot eighteen forward never shot")
    }

    @Test
    fun `a side of only a keeper falls back to him`() {
        val side = Lineups.sideOfSlots(listOf(1), strength = 50)
        val shooter = assertNotNull(
            selectShooter(side, rules, ScriptedRng()),
            "the fallback should still have named the only man on the pitch",
        )
        assertEquals(1, shooter.slot.value)
    }

    @Test
    fun `a natural keeper fielded outfield is never drawn`() {
        val pitch = Lineups.FORMATION_4_4_2.map { slot ->
            if (slot == 22) {
                Lineups.player(
                    slot = slot,
                    strength = 99,
                    position = Position.GOALKEEPER,
                    firstTrait = Trait.FINISHING,
                    secondTrait = Trait.PACE,
                )
            } else {
                Lineups.player(slot, 50)
            }
        }
        val side = Lineups.side(pitch)
        val rng = SplitMix64Rng(23)
        repeat(20_000) {
            assertTrue(
                selectShooter(side, rules, rng)?.slot?.value != 22,
                "a keeper fielded outfield was drawn to shoot",
            )
        }
    }

    @Test
    fun `a non keeper occupant of slot one is never drawn`() {
        val pitch = listOf(
            Lineups.player(
                slot = 1,
                strength = 99,
                position = Position.MIDFIELDER,
                firstTrait = Trait.FINISHING,
                secondTrait = Trait.PACE,
            ),
        ) + Lineups.FORMATION_4_4_2.drop(1).map { Lineups.player(it, 50) }
        val side = Lineups.side(pitch)
        val rng = SplitMix64Rng(29)
        repeat(20_000) {
            assertTrue(
                selectShooter(side, rules, rng)?.slot?.value != 1,
                "the slot one occupant was drawn despite not being a keeper",
            )
        }
    }

    /**
     * ScriptedRng carries no scripted doubles at all, so any attempt to draw
     * fails the test outright rather than by chance: with three natural
     * keepers on the pitch the candidate list must be empty and the fallback
     * must return the last player without ever consuming the generator.
     *
     * The three are deliberately arranged so that list order, slot order and
     * strength order disagree. lastInList sits neither at the highest slot
     * (that is highestSlot, 20) nor at the highest strength (that is
     * strongest, 90), so a fallback wrongly written as maxByOrNull on either
     * of those would still pick the wrong man, and only a true walk of the
     * list in its own order lands on lastInList.
     */
    @Test
    fun `the draw falls back to the last player of the lineup when nobody is eligible`() {
        val highestSlot = Lineups.player(slot = 20, strength = 50, position = Position.GOALKEEPER)
        val strongest = Lineups.player(slot = 5, strength = 90, position = Position.GOALKEEPER)
        val lastInList = Lineups.player(slot = 10, strength = 60, position = Position.GOALKEEPER)
        val side = Lineups.side(listOf(highestSlot, strongest, lastInList))
        val shooter = assertNotNull(selectShooter(side, rules, ScriptedRng()))
        assertEquals(
            lastInList.id,
            shooter.id,
            "the fallback should have picked the last player in list order, not the highest slot or the strongest",
        )
    }

    /**
     * ScriptedRng again rules out a lucky draw. The pitch holds nothing but
     * natural keepers, so the candidate list is empty and the fallback must
     * walk to the last pitch player; the benched man sits after him in list
     * order and would be handed back instead if the fallback's isPitch guard
     * were ever dropped.
     */
    @Test
    fun `the fallback never returns a benched player even when he is last in the list`() {
        val keeperA = Lineups.player(slot = 3, strength = 80, position = Position.GOALKEEPER)
        val keeperB = Lineups.player(slot = 20, strength = 55, position = Position.GOALKEEPER)
        val lastOnPitch = Lineups.player(slot = 10, strength = 65, position = Position.GOALKEEPER)
        val benched = Lineups.player(slot = 30, strength = 99)
        val side = Lineups.side(listOf(keeperA, keeperB, lastOnPitch, benched))
        val shooter = assertNotNull(selectShooter(side, rules, ScriptedRng()))
        assertEquals(
            lastOnPitch.id,
            shooter.id,
            "the fallback should have named the last pitch player, not the benched one",
        )
    }
}
