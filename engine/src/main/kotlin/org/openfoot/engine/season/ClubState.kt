package org.openfoot.engine.season

import org.openfoot.engine.lineup.Availability
import org.openfoot.engine.lineup.PlayerAvailability
import org.openfoot.engine.match.SideState
import org.openfoot.engine.world.Competitor
import org.openfoot.engine.world.DesignationEnergy
import org.openfoot.engine.world.GeneratedClub
import org.openfoot.engine.world.Player
import org.openfoot.engine.world.Standing
import org.openfoot.model.Designated
import org.openfoot.model.SpecRef

/**
 * What a season keeps about one player between matches: the energy section
 * 3.9 recovers, the cards of section 3.8, the injury expiry date section 0
 * says the original stores rather than a round count, the flag section 4.5
 * sets when a man is named to a matchday squad, and the season tallies the
 * star of section 4.10 and the printed top scorers read.
 *
 * The cards are kept one record per competition, keyed by the competition's
 * own key, because section 3.8 runs its test at the end of each match of the
 * player's club in that competition and nowhere else. A league ban therefore
 * never keeps a man out of a cup match, and yellows booked in two different
 * competitions never add up to a suspension. A competition absent from the
 * map holds the clean record, and withDiscipline keeps it that way by
 * dropping a record the moment it is clean again, so two players with the
 * same cards compare equal however they came by them.
 */
@SpecRef("3.8")
data class PlayerRecord(
    @property:SpecRef("3.9") val energy: Int = SideState.FULL_ENERGY,
    @property:SpecRef("3.8") val discipline: Map<String, DisciplineRecord> = emptyMap(),
    @property:SpecRef("0") val injuredUntil: CalendarDate? = null,
    @property:SpecRef("4.5") val namedSinceTick: Boolean = false,
    val appearances: Int = 0,
    @property:SpecRef("4.10") val ratingSum: Double = 0.0,
    @property:SpecRef("4.10") val ratingCount: Int = 0,
    val goals: Int = 0,
) {
    /** True while the expiry date is still ahead of the given day; the expiry day itself is free. */
    @SpecRef("0")
    fun injured(on: CalendarDate): Boolean = injuredUntil != null && on < injuredUntil

    /** The card record of the given competition, clean when the competition has none. */
    @SpecRef("3.8")
    fun disciplineIn(competition: String): DisciplineRecord = discipline[competition] ?: DisciplineRecord.CLEAN

    /** This record with the given competition's cards replaced, a clean record kept as an absent key. */
    @SpecRef("3.8")
    fun withDiscipline(competition: String, record: DisciplineRecord): PlayerRecord =
        copy(discipline = if (record == DisciplineRecord.CLEAN) discipline - competition else discipline + (competition to record))

    /** Fit on the given day and not suspended in the given competition; a suspension elsewhere does not count. */
    @SpecRef("3.8")
    fun canPlay(on: CalendarDate, competition: String): Boolean = !disciplineIn(competition).suspended && !injured(on)
}

/**
 * A club as the season carries it: the generated club it came from, the
 * squad it fields today, one record per squad member and its prestige.
 *
 * The squad is carried here rather than read from the generated club so that
 * the weekly evolution and the turnover of later plans can replace players
 * without touching world generation. Records run parallel to the squad, and
 * withRecord is the one way to change one, so the two never drift apart.
 */
@SpecRef("1.9")
data class ClubState(
    val club: GeneratedClub,
    @param:SpecRef("1.9") val standing: Standing,
    override val squad: List<Player>,
    val records: List<PlayerRecord>,
    val prestige: Prestige,
) : Competitor {
    init {
        require(records.size == squad.size) { "${club.entry.ref} has ${squad.size} players and ${records.size} records" }
    }

    override val key: String get() = club.entry.ref
    override val country: Int get() = club.entry.country
    override val reputation: Int get() = prestige.reputation
    override val designated: Designated get() = club.designated
    override val representedCountry: Int? get() = null

    /** True while the club plays a national league division this season, which the prestige decay of 5.5 reads. */
    @SpecRef("5.5")
    val inLeague: Boolean get() = standing is Standing.InDivision

    /** Section 5.4's two questions answered from the records, for a match of the given competition on the given day. */
    @SpecRef("5.4")
    fun availability(on: CalendarDate, competition: String): Availability = Availability { index, _ ->
        val record = records[index]
        PlayerAvailability(canPlay = record.canPlay(on, competition), energy = record.energy)
    }

    @SpecRef("5.6")
    val designationEnergy: DesignationEnergy get() = DesignationEnergy { index, _ -> records[index].energy }

    fun withRecord(index: Int, change: (PlayerRecord) -> PlayerRecord): ClubState =
        copy(records = records.mapIndexed { i, record -> if (i == index) change(record) else record })

    companion object {
        fun fresh(club: GeneratedClub): ClubState = ClubState(
            club = club,
            standing = club.standing,
            squad = club.squad,
            records = List(club.squad.size) { PlayerRecord() },
            prestige = Prestige(club.entry.reputation, 0),
        )
    }
}
