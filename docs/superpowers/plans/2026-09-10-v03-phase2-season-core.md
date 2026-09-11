# v0.3 Phase 2 (Season Core) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play one whole AI-only season of a country over a generated world: calendar, national leagues, state championships, the national cup, the round loop with recovery and suspensions, prize crediting and promotion/relegation, printed from a new `season` CLI subcommand.

**Architecture:** Additive, in the existing pure `org.openfoot.engine.season` package. A competition is a list of phases (round robin over groups, then knockout) built by factories from the dataset entries; a `SeasonState` is an immutable value advanced by `playRound`, which schedules through a `SeasonSchedule` that maps every competition round to a calendar date and guarantees one match per club per date; the tables, merit lists and statistics are read out of the results that the state records, never accumulated beside them. Player evolution, youth, turnover and every non-domestic competition are later plans; this plan leaves the hooks they need (a weekly tick interface, a club state that carries a squad it can replace, an end-of-season function that later plans extend).

**Tech Stack:** Kotlin/JVM 21, Gradle wrapper, kotlinx-serialization, kotlin.test. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-09-v03-temporada-design.md` arguing from `spec/SIMULATION-SPEC.md` sections 0, 1.2, 1.3, 1.9, 1.10, 1.11, 1.12, 1.13, 3.1, 3.8, 3.9, 5.5 and `spec/FORMAT-SPEC.md` ("Campos de ConfigLigaType", "Campeonatos estaduais - .ces"). Items 70 to 89 of `spec/OPEN-QUESTIONS.md` are the declared bets.

## Global Constraints

- Work on branch `feat/v0-3-season` (already exists, from `main`).
- Commits: Conventional Commits, atomic, `./gradlew check` green before each; the commit message is ONLY the subject and body given per task, NO Co-Authored-By trailer or any other trailer. Commits are authored solely by the repo's configured user.
- Comments: docstrings only, plain ASCII, no markdown inside comments, no line narration; the house style is long-form prose that says which spec section a number comes from (`checkCommentStyle` enforces the mechanical part).
- Every constant from the spec carries `@SpecRef("<section>")`.
- `:model`, `:dataset`, `:engine` stay pure: no I/O, clock, platform randomness, `HashMap`/`HashSet` (the token scan also hits `LinkedHashMap(` and `LinkedHashSet(` with a bare parenthesis, so write `LinkedHashMap<K, V>()`, `mutableMapOf()`, `mutableSetOf()`, or `toMutableMap()`), transcendental math; never branch on rule-set identity. Do not name a banned token inside a docstring in `:engine`.
- Clean-room: code is written only from `spec/`, the design doc and this plan. NEVER read decompiled output or `C:\Brasfoot22-23\decompiled`. Running the importer over the local game install is permitted only in the acceptance task.
- Randomness: every draw from `org.openfoot.model.Rng`; new randomness gets its own tagged `fork`, never an extra draw from an existing stream (that moves golden vectors).
- Existing season code this plan builds on, all in `engine/src/main/kotlin/org/openfoot/engine/season/`: `Standings.kt` (`Result`, `TableRow`, `TABLE_ORDER`, `standings(competitors, results)`), `Suspensions.kt` (`DisciplineRecord`, `Map<PlayerId, DisciplineRecord>.afterMatch(log, side, rng)`), `Recovery.kt` (`weeklyRecovery(age, played, humanManaged)`, `recover(energy, gain)`), `Prestige.kt` (`Prestige`, `titlePrestige(kind, champion, inLeague, continent, division)`), `Knockout.kt` (`Entrant`, `Tie`, `firstRoundTies`, `nextRoundTies`, `TieOutcome`, `resolveTie(tie, legs, twoLegged, penalties, rules, rng)`), `Fixtures.kt` (`Fixture`, `Round`, `roundRobin(order, turns)`, `shuffledOrder(participants, rng)`, `defaultTurns`), `CalendarDate.kt` (`CalendarDate`, `seasonStart(year)`, `plusDays`, `daysUntil`, `isSunday`), `Retirement.kt`.
- Single test class: `./gradlew :engine:test --tests "org.openfoot.engine.season.PhaseTest"` (adjust class).

---

### Task 1: the dataset carries the full league configuration

The pyramid, the group formats and the promotion playoffs of 1.11 and 1.12 read fields the schema dropped at v0.2. Additive: optional fields with the defaults the embedded format uses, so version 2 files still decode.

**Files:**
- Modify: `dataset/src/main/kotlin/org/openfoot/dataset/WorldDataset.kt` (class `LeagueConfigEntry`)
- Modify: `importer/src/main/kotlin/org/openfoot/importer/LeagueConfig.kt`
- Modify: `importer/src/test/kotlin/org/openfoot/importer/ImportFixtures.kt` (class `Tier`)
- Test: `dataset/src/test/kotlin/org/openfoot/dataset/WorldDatasetTest.kt`, `importer/src/test/kotlin/org/openfoot/importer/LeagueConfigTest.kt`

**Interfaces:**
- Produces: `LeagueConfigEntry` gains `groups: Int = 0`, `gamesInsideGroup: Boolean = true`, `relegatedByGroup: Boolean = false`, `knockoutQualifiers: Int = 0`, `qualifyByOverallTable: Boolean = false`, `bestThirds: Boolean = false`, `directRelegated: Int = relegated`, `promotionPlayoffPlaces: Int = 0`, `relegationPlayoffLegs: List<Boolean> = listOf(false, false, false)`, `promotionPlayoffLegs: List<Boolean> = listOf(false, false, false)`. `knockoutQualifiers` keeps sentinel values as read (1020 is the Serie C format of 1.11); `LeagueConfigEntry.SERIE_C_FORMAT = 1020`.

- [ ] **Step 1: Write the failing tests**

Add to `WorldDatasetTest.kt`:

```kotlin
    @Test
    fun `a league entry defaults to the embedded flat format and validates its group fields`() {
        val entry = sampleLeague()
        assertEquals(0, entry.groups)
        assertTrue(entry.gamesInsideGroup)
        assertEquals(entry.relegated, entry.directRelegated)
        assertEquals(0, entry.promotionPlayoffPlaces)
        assertEquals(listOf(false, false, false), entry.relegationPlayoffLegs)
        assertFailsWith<IllegalArgumentException> { entry.copy(groups = -1) }
        assertFailsWith<IllegalArgumentException> { entry.copy(directRelegated = entry.relegated + 1) }
        assertFailsWith<IllegalArgumentException> { entry.copy(promotionPlayoffPlaces = 3) }
        assertFailsWith<IllegalArgumentException> { entry.copy(relegationPlayoffLegs = listOf(true)) }
        assertFailsWith<IllegalArgumentException> { entry.copy(knockoutQualifiers = -1) }
    }
```

Add to `LeagueConfigTest.kt`:

```kotlin
    @Test
    fun `the group and playoff fields are read, with the flat defaults when absent`() {
        val entries = LeagueConfigReader.read(
            bytes(
                ImportFixtures.Pyramid(
                    arrayListOf(
                        ImportFixtures.Tier(
                            pais = 29, divisao = 4, nTimes = 64, nRebaixados = 4,
                            nGrupos = 8, numeroTimesMataMata = 4, rebaixadosDireto = 4,
                        ),
                        ImportFixtures.Tier(
                            pais = 65, divisao = 2, nTimes = 22, nRebaixados = 3,
                            rebaixadosDireto = 2, vagasSobemPeloMataMata = 1,
                            duasVoltasplayoffReb = booleanArrayOf(true, false, false),
                            duasVoltasMataMataSobe = booleanArrayOf(true, true, false),
                        ),
                        ImportFixtures.Tier(pais = 29, divisao = 3, nTimes = 20, nRebaixados = 4, numeroTimesMataMata = 1020),
                    ),
                ),
            ),
        )
        val brazilFour = entries[0]
        assertEquals(8, brazilFour.groups)
        assertEquals(4, brazilFour.knockoutQualifiers)
        assertTrue(brazilFour.gamesInsideGroup)
        assertEquals(4, brazilFour.directRelegated)

        val spainTwo = entries[1]
        assertEquals(2, spainTwo.directRelegated)
        assertEquals(1, spainTwo.promotionPlayoffPlaces)
        assertEquals(listOf(true, false, false), spainTwo.relegationPlayoffLegs)
        assertEquals(listOf(true, true, false), spainTwo.promotionPlayoffLegs)

        assertEquals(LeagueConfigEntry.SERIE_C_FORMAT, entries[2].knockoutQualifiers)
    }

    @Test
    fun `a promotion playoff count outside nought to two is read as nought`() {
        val entries = LeagueConfigReader.read(
            bytes(ImportFixtures.Pyramid(arrayListOf(ImportFixtures.Tier(pais = 65, divisao = 2, nTimes = 22, vagasSobemPeloMataMata = 5)))),
        )
        assertEquals(0, entries.single().promotionPlayoffPlaces)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :dataset:test --tests "*WorldDatasetTest*" :importer:test --tests "*LeagueConfigTest*"`
Expected: compilation failure, `groups` and `nGrupos` unknown.

- [ ] **Step 3: Extend the schema**

In `WorldDataset.kt`, replace the `LeagueConfigEntry` class body with:

```kotlin
@Serializable
data class LeagueConfigEntry(
    @property:SpecRef("1.9") val country: Int,
    @property:SpecRef("1.9") val division: Int,
    @property:SpecRef("1.9") val teamCount: Int,
    @property:SpecRef("1.9") val relegated: Int,
    @property:SpecRef("1.3") val turns: Int,
    @property:SpecRef("FORMAT-SPEC, desempate") val penaltiesTiebreak: Boolean,
    @property:SpecRef("1.11") val groups: Int = 0,
    @property:SpecRef("1.11") val gamesInsideGroup: Boolean = true,
    @property:SpecRef("1.11") val relegatedByGroup: Boolean = false,
    @property:SpecRef("1.11") val knockoutQualifiers: Int = 0,
    @property:SpecRef("1.11") val qualifyByOverallTable: Boolean = false,
    @property:SpecRef("1.11") val bestThirds: Boolean = false,
    @property:SpecRef("1.12") val directRelegated: Int = relegated,
    @property:SpecRef("1.12") val promotionPlayoffPlaces: Int = 0,
    @property:SpecRef("1.12") val relegationPlayoffLegs: List<Boolean> = listOf(false, false, false),
    @property:SpecRef("1.12") val promotionPlayoffLegs: List<Boolean> = listOf(false, false, false),
) {
    init {
        require(country >= 0) { "league configuration for negative country $country" }
        require(division in 1..MAX_DIVISION) {
            "league division $division, and section 1.9 builds at most $MAX_DIVISION"
        }
        require(teamCount > 0) { "a league tier of $teamCount teams" }
        require(relegated in 0..teamCount) { "$relegated relegated from a tier of $teamCount teams" }
        require(turns in 1..MAX_TURNS) { "a league of $turns turns, section 1.3 knows 1 to $MAX_TURNS" }
        require(groups >= 0) { "a tier of $groups groups" }
        require(knockoutQualifiers >= 0) { "a tier with $knockoutQualifiers knockout qualifiers" }
        require(directRelegated in 0..relegated) {
            "$directRelegated relegated directly out of $relegated"
        }
        require(promotionPlayoffPlaces in 0..MAX_PLAYOFF_PLACES) {
            "$promotionPlayoffPlaces promotion playoff places, the format allows 0 to $MAX_PLAYOFF_PLACES"
        }
        require(relegationPlayoffLegs.size == PLAYOFF_ROUNDS && promotionPlayoffLegs.size == PLAYOFF_ROUNDS) {
            "playoff legs are $PLAYOFF_ROUNDS flags each"
        }
    }

    /** True when the division decides part of its relegation or promotion by a playoff (1.12). */
    val hasRelegationPlayoff: Boolean get() = directRelegated < relegated

    companion object {
        @SpecRef("1.9")
        const val MAX_DIVISION = 4

        @SpecRef("1.3")
        const val MAX_TURNS = 4

        @SpecRef("FORMAT-SPEC, ConfigLigaType")
        const val MAX_PLAYOFF_PLACES = 2

        @SpecRef("FORMAT-SPEC, ConfigLigaType")
        const val PLAYOFF_ROUNDS = 3

        /** The sentinel value of the knockout qualifiers field that selects the Serie C format of 1.11. */
        @SpecRef("1.11")
        const val SERIE_C_FORMAT = 1020
    }
}
```

Keep the existing docstring above the class and add one sentence: "The group, final phase and playoff fields of FORMAT-SPEC's ConfigLigaType section default to the flat format the embedded generator uses, so a version 2 file without them still decodes."

- [ ] **Step 4: Extend the reader and the fixture**

In `ImportFixtures.kt`, replace class `Tier` with:

```kotlin
    class Tier(
        val pais: Int,
        val divisao: Int,
        val nTimes: Int,
        val nRebaixados: Int = 0,
        val formula: Int = 0,
        val desempate: Int = 0,
        val nGrupos: Int = 0,
        val jogosDentroGrupo: Boolean = true,
        val rebaixadoPeloGrupo: Boolean = false,
        val numeroTimesMataMata: Int = 0,
        val classificaPeloGeral: Boolean = false,
        val melhoresTerceiros: Boolean = false,
        val rebaixadosDireto: Int = nRebaixados,
        val vagasSobemPeloMataMata: Int = 0,
        val duasVoltasplayoffReb: BooleanArray = booleanArrayOf(false, false, false),
        val duasVoltasMataMataSobe: BooleanArray = booleanArrayOf(false, false, false),
        val versaoArquivo: Int = 22,
    ) : Serializable
```

In `LeagueConfig.kt`, build the entry with the new fields (the serialized reader returns a boolean array as `List<Any?>` of `Boolean`, and a missing field as null):

```kotlin
            val directRelegated = (tier.intOrNull(DIRECT_RELEGATED) ?: relegated).coerceIn(0, relegated)
            val playoffPlaces = (tier.intOrNull(PROMOTION_PLAYOFF_PLACES) ?: 0)
                .let { if (it in 0..LeagueConfigEntry.MAX_PLAYOFF_PLACES) it else 0 }
            LeagueConfigEntry(
                country = country,
                division = division,
                teamCount = teamCount,
                relegated = relegated,
                turns = resolveTurns(teamCount, tier.intOrNull(FORMULA) ?: 0),
                penaltiesTiebreak = (tier.intOrNull(TIEBREAK) ?: 0) == 0,
                groups = (tier.intOrNull(GROUPS) ?: 0).coerceAtLeast(0),
                gamesInsideGroup = tier.fields[GAMES_INSIDE_GROUP] as? Boolean ?: true,
                relegatedByGroup = tier.fields[RELEGATED_BY_GROUP] as? Boolean ?: false,
                knockoutQualifiers = (tier.intOrNull(KNOCKOUT_QUALIFIERS) ?: 0).coerceAtLeast(0),
                qualifyByOverallTable = tier.fields[QUALIFY_BY_OVERALL] as? Boolean ?: false,
                bestThirds = tier.fields[BEST_THIRDS] as? Boolean ?: false,
                directRelegated = directRelegated,
                promotionPlayoffPlaces = playoffPlaces,
                relegationPlayoffLegs = legs(tier.fields[RELEGATION_PLAYOFF_LEGS]),
                promotionPlayoffLegs = legs(tier.fields[PROMOTION_PLAYOFF_LEGS]),
            )
```

with, inside the object:

```kotlin
    /** Three leg flags, false where the file holds nothing readable. */
    @SpecRef("FORMAT-SPEC, ConfigLigaType")
    private fun legs(value: Any?): List<Boolean> {
        val flags = (value as? List<*>)?.map { it == true } ?: emptyList()
        return List(LeagueConfigEntry.PLAYOFF_ROUNDS) { flags.getOrElse(it) { false } }
    }

    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val GROUPS = "nGrupos"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val GAMES_INSIDE_GROUP = "jogosDentroGrupo"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val RELEGATED_BY_GROUP = "rebaixadoPeloGrupo"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val KNOCKOUT_QUALIFIERS = "numeroTimesMataMata"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val QUALIFY_BY_OVERALL = "classificaPeloGeral"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val BEST_THIRDS = "melhoresTerceiros"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val DIRECT_RELEGATED = "rebaixadosDireto"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val PROMOTION_PLAYOFF_PLACES = "vagasSobemPeloMataMata"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val RELEGATION_PLAYOFF_LEGS = "duasVoltasplayoffReb"
    @SpecRef("FORMAT-SPEC, ConfigLigaType") private const val PROMOTION_PLAYOFF_LEGS = "duasVoltasMataMataSobe"
```

Write one constant per line with its own annotation (the style check does not care, but the file's existing constants are one per block; either is fine).

- [ ] **Step 5: Run the tests and the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```
feat(dataset): the league configuration carries groups, final phase and playoffs

The fields of FORMAT-SPEC's ConfigLigaType beyond size, relegated, turns and
tiebreak, with the flat defaults of the embedded generator so version 2
files still decode. The Serie C sentinel is kept as the value the file
holds and named. The importer reads them from the .cfg.
```

---

### Task 2: player records, club state and availability

The state a season keeps per player between matches, and the two interfaces the lineup already asks of it.

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/ClubState.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/ClubStateTest.kt`

**Interfaces:**
- Consumes: `DisciplineRecord` (Suspensions.kt), `CalendarDate`, `Player`, `GeneratedClub`, `Standing`, `Prestige`, `Availability`, `PlayerAvailability`, `DesignationEnergy`, `SideState.FULL_ENERGY`.
- Produces:

```kotlin
@SpecRef("3.8")
data class PlayerRecord(
    val energy: Int = SideState.FULL_ENERGY,
    val discipline: DisciplineRecord = DisciplineRecord.CLEAN,
    val injuredUntil: CalendarDate? = null,
    val namedSinceTick: Boolean = false,
    val appearances: Int = 0,
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    val goals: Int = 0,
) {
    fun injured(on: CalendarDate): Boolean = injuredUntil != null && on < injuredUntil
    fun canPlay(on: CalendarDate): Boolean = !discipline.suspended && !injured(on)
}

@SpecRef("1.9")
data class ClubState(
    val club: GeneratedClub,
    val standing: Standing,
    val squad: List<Player>,
    val records: List<PlayerRecord>,
    val prestige: Prestige,
) : Competitor {
    val key: String get() = club.entry.ref   // and the other Competitor members from club and squad
    val inLeague: Boolean get() = standing is Standing.InDivision
    fun availability(on: CalendarDate): Availability
    val designationEnergy: DesignationEnergy
    fun withRecord(index: Int, change: (PlayerRecord) -> PlayerRecord): ClubState
    companion object { fun fresh(club: GeneratedClub): ClubState }
}
```

`ClubState` implements `Competitor` with `squad` (its own, not the club's, so a later plan can replace players), `designated = club.designated`, `country = club.entry.country`, `reputation = prestige.reputation`, `representedCountry = null`. `fresh` gives every player a default record, the club's pyramid standing (carried here so the turnover can move a club between divisions) and `Prestige(club.entry.reputation, 0)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.openfoot.engine.season

import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.engine.match.SideState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClubStateTest {

    private val world = generateWorld(WorldFixtures.dataset(), 1, activeLeagues = emptySet())
    private val state = ClubState.fresh(world.clubs.single())
    private val today = CalendarDate(2026, 1, 4)

    @Test
    fun `a fresh club has every man fit, fresh and at the club's reputation`() {
        assertEquals(state.squad.size, state.records.size)
        assertTrue(state.records.all { it.canPlay(today) && it.energy == SideState.FULL_ENERGY })
        assertEquals(state.club.entry.reputation, state.reputation)
        assertEquals(state.club.entry.ref, state.key)
        assertEquals(null, state.representedCountry)
        assertEquals(state.club.standing, state.standing)
    }

    @Test
    fun `availability reads suspension, injury and energy off the record`() {
        val changed = state
            .withRecord(0) { it.copy(discipline = it.discipline.banned(1)) }
            .withRecord(0) { it.copy(energy = 40) }
        val availability = changed.availability(today).of(0, changed.squad[0])
        assertFalse(availability.canPlay)
        assertEquals(40, availability.energy)
        assertEquals(40, changed.designationEnergy.of(0, changed.squad[0]))
    }

    @Test
    fun `an injury keeps a man out until its date and not on it`() {
        val record = PlayerRecord(injuredUntil = CalendarDate(2026, 1, 20))
        assertTrue(record.injured(CalendarDate(2026, 1, 19)))
        assertFalse(record.injured(CalendarDate(2026, 1, 20)))
        assertFalse(record.canPlay(CalendarDate(2026, 1, 4)))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.ClubStateTest"`
Expected: compilation failure, `ClubState` unknown.

- [ ] **Step 3: Implement**

`ClubState.kt`:

```kotlin
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
 */
@SpecRef("3.8")
data class PlayerRecord(
    @property:SpecRef("3.9") val energy: Int = SideState.FULL_ENERGY,
    @property:SpecRef("3.8") val discipline: DisciplineRecord = DisciplineRecord.CLEAN,
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

    fun canPlay(on: CalendarDate): Boolean = !discipline.suspended && !injured(on)
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
    :SpecRef("1.9") val standing: Standing,
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
    ("5.5")
    val inLeague: Boolean get() = standing is Standing.InDivision

    /** Section 5.4's two questions answered from the records, for the given day. */
    @SpecRef("5.4")
    fun availability(on: CalendarDate): Availability = Availability { index, _ ->
        val record = records[index]
        PlayerAvailability(canPlay = record.canPlay(on), energy = record.energy)
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
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.ClubStateTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```
feat(engine): the club state of a season, with per player records

Energy, cards, the injury expiry date of section 0, the named flag of 4.5
and the season tallies, one record per squad member, answering the lineup's
availability and designation questions for a given day.
```

---

### Task 3: competition phases

The one engine every domestic competition is built from: a round robin phase over one or more groups, and a knockout phase of ties over legs. A competition is a list of phases plus the rule that carries qualifiers from one to the next. Results are recorded per phase; tables and merit lists are readings.

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/Phase.kt`
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/Competition.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/PhaseTest.kt`, `engine/src/test/kotlin/org/openfoot/engine/season/CompetitionTest.kt`

**Interfaces:**
- Consumes: `Round`, `Fixture`, `roundRobin`, `Result`, `standings`, `TableRow`, `Entrant`, `Tie`, `firstRoundTies`, `nextRoundTies`, `resolveTie`, `TieOutcome`, `RuleSet`, `Rng`, `CompetitionKind`.
- Produces: `ScheduledMatch(phase: Int, round: Int, fixture: Fixture, leg: Int = 1)`; `RoundRobinPhase(groups: List<List<String>>, turns: Int, gamesInsideGroup: Boolean, rounds: List<Round>)` with `participants`, `groupTable(group, results)`, `overallTable(results)`, `RoundRobinPhase.single(order, turns)`, `RoundRobinPhase.grouped(groups, turns, gamesInsideGroup)`; `KnockoutPhase(entrants: List<Entrant>, legsPerRound: List<Boolean>, penalties: Boolean, field: Int = entrants.size)` with `rounds` (from `field`, so an unseeded phase already knows its size), `twoLegged(round)`, `ties(round, resultsSoFar, rules, rng)`, `outcomes(round, results, rules, rng)`, `meritOrder(results, rules, rng)`; `sealed interface Phase { League(phase: RoundRobinPhase); Knockout(phase: KnockoutPhase) }`; `Competition(key, kind, country, division: Int?, phases, qualifiers: (RoundRobinPhase, List<Result>) -> List<Entrant>, results: List<List<Result>>, phaseIndex, roundIndex)` with `finished`, `participants`, `nextMatches(rules, rng)`, `recorded(matches: List<Pair<ScheduledMatch, Result>>)`, `finalOrder(rules, rng)`.

Design the implementer must follow:

- `RoundRobinPhase.single` calls `roundRobin(order, turns)`. `grouped` with `gamesInsideGroup = true` generates `roundRobin` per group and zips the groups' rounds together round by round, so all groups advance in step. With `gamesInsideGroup = false` (the state presets 7 and 10) every side meets every side of the other groups once a turn and never its own group: the circle over the concatenated list with same group fixtures dropped and empty rounds removed. The original reads a fixed table for that case that the spec does not publish (OPEN-QUESTIONS item 75), so this arrangement is INFERIDO and the docstring says so.
- `groupTable(group, results)` is `standings(groups[group], results filtered to both sides in the group)`; `overallTable` is `standings(participants, results)`.
- `KnockoutPhase.ties(round, ...)`: round 0 is `firstRoundTies(entrants)` when the field is 2, 4 or 8 (the state brackets), otherwise the seeded bracket of 1.13, entrant i against entrant n-1-i in that order; later rounds pair the winners of the previous round with `nextRoundTies`. `outcomes` groups that round's recorded results by tie (the legs whose two sides the tie holds) and calls `resolveTie` once per tie with `rng.fork(round.toLong()).fork(index.toLong())`, so tie order never moves another tie's shootout. In a single elimination knockout two sides meet in one round only, so the filter is exact; say so in the docstring.
- `meritOrder` is FORMAT-SPEC's list: champion, runner-up, then the losers of each earlier round in tie order, last round first.
- `Competition.nextMatches` returns the current round's matches: for a league phase the round's fixtures; for a knockout, one match per tie for the current leg. The phase's round index counts legs, so a two legged round takes two indices; the host of leg k is `Tie.host(k, twoLegged)`.
- `recorded` appends the results to the current phase's list and advances the round index; past the phase's last round it moves to the next phase, replacing a knockout's entrants with `qualifiers(lastLeaguePhase, itsResults)`; past the last phase, `finished` is true.
- `finalOrder`: the last knockout's `meritOrder` completed by the last league phase's overall table for everyone else, or the table alone when there is no knockout. Requires `finished`.

- [ ] **Step 1: Write the failing tests**

`PhaseTest.kt`:

```kotlin
package org.openfoot.engine.season

import org.openfoot.engine.world.ScriptedInts
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhaseTest {

    private val eight = (1..8).map { "c$it" }

    @Test
    fun `a single group phase is the round robin of section 1 3`() {
        val phase = RoundRobinPhase.single(eight, turns = 2)
        assertEquals(roundRobin(eight, 2), phase.rounds)
        assertEquals(eight, phase.overallTable(emptyList()).map { it.key })
    }

    @Test
    fun `groups playing inside advance in step and read their own tables`() {
        val phase = RoundRobinPhase.grouped(listOf(eight.take(4), eight.drop(4)), turns = 1, gamesInsideGroup = true)
        assertEquals(3, phase.rounds.size)
        assertEquals(4, phase.rounds[0].fixtures.size)
        val results = listOf(Result("c1", "c2", 3, 0))
        assertEquals("c1", phase.groupTable(0, results).first().key)
        assertEquals(eight.drop(4), phase.groupTable(1, results).map { it.key })
        assertEquals("c1", phase.overallTable(results).first().key)
    }

    @Test
    fun `cross group play never pairs two sides of one group and covers every pair once a turn`() {
        val groups = listOf(eight.take(4), eight.drop(4))
        val phase = RoundRobinPhase.grouped(groups, turns = 1, gamesInsideGroup = false)
        val fixtures = phase.rounds.flatMap { it.fixtures }
        assertEquals(16, fixtures.size)
        assertTrue(fixtures.none { f -> groups.any { g -> f.home in g && f.away in g } })
        assertEquals(16, fixtures.map { setOf(it.home, it.away) }.toSet().size)
    }

    @Test
    fun `a knockout of eight plays the state bracket over two legs and reads its merit order`() {
        val entrants = eight.mapIndexed { i, key -> Entrant(key, i + 1) }
        val phase = KnockoutPhase(entrants, legsPerRound = listOf(true, true, true), penalties = true)
        assertEquals(3, phase.rounds)
        val ties = phase.ties(0, emptyList(), RuleSets.CLASSIC, SplitMix64Rng(1))
        assertEquals(listOf("c2" to "c7", "c4" to "c5", "c1" to "c8", "c3" to "c6"), ties.map { it.higher.key to it.lower.key })

        // The higher seed wins every tie on the return after a drawn first leg, so nothing is drawn.
        val firstRound = ties.flatMap { listOf(Result(it.lower.key, it.higher.key, 0, 0), Result(it.higher.key, it.lower.key, 1, 0)) }
        val outcomes = phase.outcomes(0, firstRound, RuleSets.CLASSIC, ScriptedInts())
        assertEquals(listOf("c2", "c4", "c1", "c3"), outcomes.map { it.winner.key })
    }

    @Test
    fun `a field that is not a state size seeds strong against weak`() {
        val entrants = (1..16).map { Entrant("c$it", it) }
        val ties = KnockoutPhase(entrants, listOf(false), penalties = true).ties(0, emptyList(), RuleSets.CLASSIC, SplitMix64Rng(1))
        assertEquals("c1" to "c16", ties.first().higher.key to ties.first().lower.key)
        assertEquals("c8" to "c9", ties.last().higher.key to ties.last().lower.key)
    }
}
```

`CompetitionTest.kt`:

```kotlin
package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompetitionTest {

    private val six = (1..6).map { "c$it" }

    private fun leagueOnly() = Competition(
        key = "liga",
        kind = CompetitionKind.NATIONAL_LEAGUE,
        country = 29,
        division = 1,
        phases = listOf(Phase.League(RoundRobinPhase.single(six, turns = 1))),
        qualifiers = { _, _ -> emptyList() },
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )

    /** Every home side wins by one, so the table is decided by the fixture list alone. */
    private fun homeWins(matches: List<ScheduledMatch>) =
        matches.map { it to Result(it.fixture.home, it.fixture.away, 1, 0) }

    @Test
    fun `a league plays its rounds in order and finishes after the last`() {
        var competition = leagueOnly()
        val rules = RuleSets.CLASSIC
        val rng = SplitMix64Rng(1)
        repeat(5) {
            assertFalse(competition.finished)
            val matches = competition.nextMatches(rules, rng)
            assertEquals(3, matches.size)
            assertEquals(it, matches.first().round)
            competition = competition.recorded(homeWins(matches))
        }
        assertTrue(competition.finished)
        assertEquals(6, competition.finalOrder(rules, rng).size)
        assertEquals(15, competition.results.single().size)
        assertTrue(competition.nextMatches(rules, rng).isEmpty())
    }

    @Test
    fun `a league with a final phase carries its qualifiers into a two legged knockout`() {
        val phases = listOf(
            Phase.League(RoundRobinPhase.single(six, turns = 1)),
            Phase.Knockout(KnockoutPhase(emptyList(), legsPerRound = listOf(true, false), penalties = true, field = 4)),
        )
        var competition = leagueOnly().copy(
            phases = phases,
            qualifiers = { phase, results ->
                phase.overallTable(results).take(4).mapIndexed { i, row -> Entrant(row.key, i + 1) }
            },
        )
        val rules = RuleSets.CLASSIC
        val rng = SplitMix64Rng(2)
        repeat(5) { competition = competition.recorded(homeWins(competition.nextMatches(rules, rng))) }
        assertEquals(1, competition.phaseIndex)

        val firstLegs = competition.nextMatches(rules, rng)
        assertEquals(2, firstLegs.size)
        assertEquals(1, firstLegs.first().leg)
        competition = competition.recorded(homeWins(firstLegs))
        val returnLegs = competition.nextMatches(rules, rng)
        assertEquals(2, returnLegs.first().leg)
        assertEquals(firstLegs.map { it.fixture.away }, returnLegs.map { it.fixture.home })
        competition = competition.recorded(returnLegs.map { it to Result(it.fixture.home, it.fixture.away, 3, 0) })

        val final = competition.nextMatches(rules, rng)
        assertEquals(1, final.size)
        competition = competition.recorded(homeWins(final))
        assertTrue(competition.finished)
        val order = competition.finalOrder(rules, rng)
        assertEquals(final.single().fixture.home, order.first())
        assertEquals(6, order.size)
        assertEquals(6, order.toSet().size)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.PhaseTest" --tests "org.openfoot.engine.season.CompetitionTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `Phase.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef

@SpecRef("1.10")
data class ScheduledMatch(val phase: Int, val round: Int, val fixture: Fixture, val leg: Int = 1)

@SpecRef("1.11")
data class RoundRobinPhase(
    val groups: List<List<String>>,
    val turns: Int,
    val gamesInsideGroup: Boolean,
    val rounds: List<Round>,
) {
    val participants: List<String> get() = groups.flatten()

    fun groupTable(group: Int, results: List<Result>): List<TableRow> {
        val members = groups[group].toSet()
        return standings(groups[group], results.filter { it.home in members && it.away in members })
    }

    fun overallTable(results: List<Result>): List<TableRow> = standings(participants, results)

    companion object {
        @SpecRef("1.3")
        fun single(order: List<String>, turns: Int): RoundRobinPhase =
            RoundRobinPhase(listOf(order), turns, gamesInsideGroup = true, rounds = roundRobin(order, turns))

        @SpecRef("1.11")
        fun grouped(groups: List<List<String>>, turns: Int, gamesInsideGroup: Boolean): RoundRobinPhase {
            val rounds = if (gamesInsideGroup) inStep(groups.map { roundRobin(it, turns) }) else crossGroups(groups, turns)
            return RoundRobinPhase(groups, turns, gamesInsideGroup, rounds)
        }

        private fun inStep(perGroup: List<List<Round>>): List<Round> {
            val length = perGroup.maxOf { it.size }
            return (0 until length).map { r -> Round(perGroup.flatMap { it.getOrNull(r)?.fixtures ?: emptyList() }) }
        }

        private fun crossGroups(groups: List<List<String>>, turns: Int): List<Round> {
            val groupOf = groups.flatMapIndexed { g, members -> members.map { it to g } }.toMap()
            return roundRobin(groups.flatten(), turns)
                .map { round -> Round(round.fixtures.filter { groupOf.getValue(it.home) != groupOf.getValue(it.away) }) }
                .filter { it.fixtures.isNotEmpty() }
        }
    }
}

@SpecRef("FORMAT-SPEC, ces")
data class KnockoutPhase(
    val entrants: List<Entrant>,
    val legsPerRound: List<Boolean>,
    val penalties: Boolean,
    val field: Int = entrants.size,
) {
    init {
        require(field >= 2 && field and (field - 1) == 0) { "a knockout of $field sides" }
        require(entrants.isEmpty() || entrants.size == field) { "${entrants.size} entrants for a field of $field" }
    }

    val rounds: Int get() = Integer.numberOfTrailingZeros(field)

    fun twoLegged(round: Int): Boolean = legsPerRound.getOrElse(round) { legsPerRound.lastOrNull() ?: false }

    fun ties(round: Int, resultsSoFar: List<Result>, rules: RuleSet, rng: Rng): List<Tie> {
        if (round == 0) return openingTies()
        return nextRoundTies(outcomes(round - 1, resultsSoFar, rules, rng).map { it.winner })
    }

    @SpecRef("1.13")
    private fun openingTies(): List<Tie> = when (entrants.size) {
        2, 4, 8 -> firstRoundTies(entrants)
        else -> (0 until entrants.size / 2).map { Tie(entrants[it], entrants[entrants.size - 1 - it]) }
    }

    fun outcomes(round: Int, results: List<Result>, rules: RuleSet, rng: Rng): List<TieOutcome> {
        val twoLegged = twoLegged(round)
        return ties(round, results, rules, rng).mapIndexed { index, tie ->
            val legs = results.filter { tie.holds(it.home) && tie.holds(it.away) }
            resolveTie(tie, legs, twoLegged, penalties, rules, rng.fork(round.toLong()).fork(index.toLong()))
        }
    }

    @SpecRef("FORMAT-SPEC, ces")
    fun meritOrder(results: List<Result>, rules: RuleSet, rng: Rng): List<String> {
        val order = ArrayList<String>()
        for (round in rounds - 1 downTo 0) {
            val ties = ties(round, results, rules, rng)
            val outcomes = outcomes(round, results, rules, rng)
            if (round == rounds - 1) order += outcomes.single().winner.key
            ties.zip(outcomes).forEach { (tie, outcome) ->
                order += if (outcome.winner == tie.higher) tie.lower.key else tie.higher.key
            }
        }
        return order
    }
}

sealed interface Phase {
    data class League(val phase: RoundRobinPhase) : Phase
    data class Knockout(val phase: KnockoutPhase) : Phase
}
```

Write the docstrings the design notes above describe (the INFERIDO cross group arrangement, the single elimination filter, the merit order) in the house style; every class and public function gets one.

- [ ] **Step 4: Implement `Competition.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SpecRef

@SpecRef("1.10")
data class Competition(
    val key: String,
    val kind: CompetitionKind,
    val country: Int,
    val division: Int?,
    val phases: List<Phase>,
    val qualifiers: (RoundRobinPhase, List<Result>) -> List<Entrant>,
    val results: List<List<Result>>,
    val phaseIndex: Int,
    val roundIndex: Int,
) {
    val finished: Boolean get() = phaseIndex >= phases.size

    val participants: List<String> get() = when (val first = phases.first()) {
        is Phase.League -> first.phase.participants
        is Phase.Knockout -> first.phase.entrants.map { it.key }
    }

    fun nextMatches(rules: RuleSet, rng: Rng): List<ScheduledMatch> {
        if (finished) return emptyList()
        return when (val phase = phases[phaseIndex]) {
            is Phase.League -> phase.phase.rounds[roundIndex].fixtures.map { ScheduledMatch(phaseIndex, roundIndex, it) }
            is Phase.Knockout -> {
                val (round, leg) = knockoutRoundAndLeg(phase.phase)
                val twoLegged = phase.phase.twoLegged(round)
                phase.phase.ties(round, results[phaseIndex], rules, rng).map { tie ->
                    ScheduledMatch(phaseIndex, roundIndex, Fixture(tie.host(leg, twoLegged).key, tie.visitor(leg, twoLegged).key), leg)
                }
            }
        }
    }

    private fun knockoutRoundAndLeg(phase: KnockoutPhase): Pair<Int, Int> {
        var remaining = roundIndex
        for (round in 0 until phase.rounds) {
            val legs = if (phase.twoLegged(round)) 2 else 1
            if (remaining < legs) return round to remaining + 1
            remaining -= legs
        }
        throw IllegalStateException("round index $roundIndex past the end of the knockout")
    }

    private fun lastRoundIndex(phase: Phase): Int = when (phase) {
        is Phase.League -> phase.phase.rounds.size - 1
        is Phase.Knockout -> (0 until phase.phase.rounds).sumOf { if (phase.phase.twoLegged(it)) 2 else 1 } - 1
    }

    fun recorded(matches: List<Pair<ScheduledMatch, Result>>): Competition {
        require(!finished) { "$key has finished" }
        require(matches.all { it.first.phase == phaseIndex && it.first.round == roundIndex }) { "results for another round of $key" }
        val extended = results.mapIndexed { i, list -> if (i == phaseIndex) list + matches.map { it.second } else list }
        if (roundIndex < lastRoundIndex(phases[phaseIndex])) return copy(results = extended, roundIndex = roundIndex + 1)
        return advancePhase(extended)
    }

    private fun advancePhase(extended: List<List<Result>>): Competition {
        val next = phaseIndex + 1
        if (next >= phases.size) return copy(results = extended, phaseIndex = next, roundIndex = 0)
        val seeded = when (val coming = phases[next]) {
            is Phase.Knockout -> {
                val league = phases[phaseIndex] as? Phase.League
                    ?: throw IllegalStateException("$key: a knockout phase must follow a league phase")
                Phase.Knockout(coming.phase.copy(entrants = qualifiers(league.phase, extended[phaseIndex])))
            }
            is Phase.League -> coming
        }
        return copy(
            phases = phases.mapIndexed { i, phase -> if (i == next) seeded else phase },
            results = extended + listOf(emptyList()),
            phaseIndex = next,
            roundIndex = 0,
        )
    }

    @SpecRef("FORMAT-SPEC, ces")
    fun finalOrder(rules: RuleSet, rng: Rng): List<String> {
        require(finished) { "$key has not finished" }
        val order = ArrayList<String>()
        val last = phases.last()
        if (last is Phase.Knockout) order += last.phase.meritOrder(results.last(), rules, rng)
        val leagueIndex = phases.indexOfLast { it is Phase.League }
        if (leagueIndex >= 0) {
            val league = (phases[leagueIndex] as Phase.League).phase
            league.overallTable(results[leagueIndex]).forEach { if (it.key !in order) order += it.key }
        }
        return order
    }
}
```

Docstring the class with the section 1.10 reading: every competition carries its own round counter and finishes on its own; the qualifiers function is the one place a format's qualification rule lives; a value, `recorded` returns the next state.

- [ ] **Step 5: Run the tests and the comment style check**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.PhaseTest" --tests "org.openfoot.engine.season.CompetitionTest" :engine:checkCommentStyle`
Expected: PASS.

- [ ] **Step 6: Commit**

```
feat(engine): competition phases, a round robin over groups and a seeded knockout

The engine every domestic competition is built from: a league phase that
plays inside or across groups, a knockout phase over seeded entrants with
legs per round, and a competition that walks its phases, records results
per phase and reads its final order as the merit list of FORMAT-SPEC.
```

---

### Task 4: state championships from the state files

FORMAT-SPEC's "Campeonatos estaduais" section end to end: the eleven presets, which states get a championship, the club queue, the division loop with the default format, the reserve, and the competition each division becomes.

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/StateChampionships.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/StateChampionshipsTest.kt`

**Interfaces:**
- Consumes: `StateChampionshipEntry`, `ClubEntry`, `WorldDataset`, `DatasetOptions`, `ClubState`, `Competition`, `Phase`, `RoundRobinPhase`, `KnockoutPhase`, `Entrant`, `shuffledOrder`, `Rng`, `Country.BRAZIL`.
- Produces:

```kotlin
@SpecRef("FORMAT-SPEC, formula")
data class StatePreset(val teams: Int, val groups: Int, val qualifiers: Int, val twoTurns: Boolean, val relegated: Int)

@SpecRef("FORMAT-SPEC, formula")
val STATE_PRESETS: List<StatePreset>   // index 0 to 10, exactly the table

@SpecRef("FORMAT-SPEC, ces")
data class StateDivision(val state: Int, val division: Int, val preset: StatePreset, val penalties: Boolean, val twoLeggedRounds: List<Boolean>, val clubs: List<String>)

@SpecRef("FORMAT-SPEC, ces")
data class StateSetup(val divisions: List<StateDivision>, val reserve: Map<Int, List<String>>)

/** Which states play, their divisions and each state's reserve queue, for one season. */
@SpecRef("FORMAT-SPEC, ces")
fun stateSetup(clubs: List<ClubState>, dataset: WorldDataset, tiebreak: (String) -> Int): StateSetup

/** The competition one state division plays. */
@SpecRef("FORMAT-SPEC, ces")
fun stateCompetition(division: StateDivision, rng: Rng): Competition
```

Design the implementer must follow (all from FORMAT-SPEC "Carga na criação do mundo" and "O que a temporada faz com cada campo"):

- Presets, index: 0 (6, 0, 2, two turns, 2), 1 (8, 0, 4, two turns, 2), 2 (10, 0, 4, one, 2), 3 (11, 0, 4, one, 2), 4 (12, 0, 4, one, 2), 5 (12, 0, 8, one, 2), 6 (14, 0, 8, one, 2), 7 (16, 4, 2 per group, one, 2), 8 (16, 0, 4, one, 2), 9 (16, 0, 8, one, 2), 10 (20, 4, 2 per group, one, 4). For the two group presets `qualifiers` is the per group count, 2.
- `stateSetup` only when `dataset.options.playStateChampionships`; otherwise empty. Brazilian clubs (`club.country == Country.BRAZIL`, `entry.state` in 0..26) are counted by state; a state with at least 6 is eligible. The queue is the state's clubs ordered by level descending, ties by `tiebreak(ref)` ascending (the caller passes the pyramid's per club draw; use the same function `assemblePyramids` uses, exposed as `internal fun pyramidTiebreak(worldRng, ref)` in `Pyramid.kt` if it is private today, and pass `{ ref -> pyramidTiebreak(worldRng, ref) }`), then by ref.
- Divisions 1 to 4 while at least 6 clubs remain: the first `dataset.stateChampionships` entry with that state and division; the default format (preset 0, penalties on, legs all two legged) when there is no entry or the entry's preset asks for more than the state's total club count T (not the remaining count; that is item 69 and this plan reproduces the literal check). The division takes the first `preset.teams` of the queue.
- Leftovers are the state's reserve, in queue order.
- The Sao Paulo real groups option of load rule 6 is out of this plan: record that in the docstring and in OPEN-QUESTIONS as an implementation item (see Task 10).
- `stateCompetition`: a group phase `RoundRobinPhase.grouped(groups, turns = if twoTurns 2 else 1, gamesInsideGroup = groups == 0)`: with `groups == 0` a single list; with 4 groups, `shuffledOrder(clubs, rng)` dealt round robin into groups (the k-th club to group k mod 4) then cross group play only. The qualifiers function: without groups, the top `qualifiers` of the overall table seeded 1..n; with groups, the top 2 of each group in group order A, B, C, D, seeded so that FORMAT-SPEC's "1o do grupo x 2o do grupo" pairing comes out of `firstRoundTies` for 8: seeds (A1 = 1, A2 = 8, B1 = 2, B2 = 7, C1 = 3, C2 = 6, D1 = 4, D2 = 5) do not reproduce "within the same group"; instead build the knockout entrants in an order the eight bracket pairs as (2,7), (4,5), (1,8), (3,6) so that each pair is one group's first and second: assign A1 seed 2, A2 seed 7, B1 seed 4, B2 seed 5, C1 seed 1, C2 seed 8, D1 seed 3, D2 seed 6. Document this seeding as the mapping onto the state bracket in the docstring. The knockout phase uses `division.twoLeggedRounds` and `division.penalties`. The competition key is `"state:${state}:${division}"`, kind `CompetitionKind.STATE`, country Brazil, division the state division number.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.openfoot.engine.season

import org.openfoot.dataset.StateChampionshipEntry
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateChampionshipsTest {

    private fun dataset(clubsPerState: Map<Int, Int>, entries: List<StateChampionshipEntry> = emptyList()) = WorldFixtures.dataset(
        clubs = clubsPerState.flatMap { (state, count) ->
            (1..count).map { i ->
                WorldFixtures.club(ref = "s${state}c$i", level = 20 - i % 10).copy(state = state)
            }
        },
    ).copy(stateChampionships = entries)

    private fun states(data: org.openfoot.dataset.WorldDataset): StateSetup {
        val world = generateWorld(data, 3, activeLeagues = emptySet())
        return stateSetup(world.clubs.map { ClubState.fresh(it) }, data) { it.hashCode() }
    }

    @Test
    fun `the presets are the eleven rows of the format spec`() {
        assertEquals(11, STATE_PRESETS.size)
        assertEquals(StatePreset(6, 0, 2, true, 2), STATE_PRESETS[0])
        assertEquals(StatePreset(16, 4, 2, false, 2), STATE_PRESETS[7])
        assertEquals(StatePreset(20, 4, 2, false, 4), STATE_PRESETS[10])
    }

    @Test
    fun `a state needs six clubs, takes the default format without a file, and keeps a reserve`() {
        val setup = states(dataset(mapOf(25 to 7, 18 to 5)))
        val division = setup.divisions.single()
        assertEquals(25, division.state)
        assertEquals(STATE_PRESETS[0], division.preset)
        assertEquals(6, division.clubs.size)
        assertEquals(1, setup.reserve.getValue(25).size)
        assertTrue(18 !in setup.reserve)
    }

    @Test
    fun `a file entry shapes the division, and one asking for more than the state holds is ignored`() {
        val entries = listOf(
            StateChampionshipEntry(state = 25, division = 1, preset = 1, penaltiesTiebreak = false, twoLeggedRounds = listOf(false, false, true)),
            StateChampionshipEntry(state = 18, division = 1, preset = 4, penaltiesTiebreak = true, twoLeggedRounds = listOf(true, true, true)),
        )
        val setup = states(dataset(mapOf(25 to 9, 18 to 8), entries))
        val saoPaulo = setup.divisions.single { it.state == 25 }
        assertEquals(STATE_PRESETS[1], saoPaulo.preset)
        assertEquals(false, saoPaulo.penalties)
        assertEquals(listOf(false, false, true), saoPaulo.twoLeggedRounds)
        val rio = setup.divisions.single { it.state == 18 }
        assertEquals(STATE_PRESETS[0], rio.preset, "a twelve team preset over eight clubs falls to the default")
        assertEquals(true, rio.penalties)
    }

    @Test
    fun `the option turns every state off`() {
        val data = dataset(mapOf(25 to 8)).let { it.copy(options = it.options.copy(playStateChampionships = false)) }
        assertTrue(states(data).divisions.isEmpty())
    }

    @Test
    fun `a six team division is a two turn league feeding a final of two`() {
        val setup = states(dataset(mapOf(25 to 6)))
        val competition = stateCompetition(setup.divisions.single(), SplitMix64Rng(9))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(10, league.rounds.size)
        assertEquals(2, competition.phases.size)
        var played = competition
        while (played.phaseIndex == 0) {
            val matches = played.nextMatches(RuleSets.CLASSIC, SplitMix64Rng(1))
            played = played.recorded(matches.map { it to Result(it.fixture.home, it.fixture.away, 1, 0) })
        }
        val final = (played.phases[1] as Phase.Knockout).phase
        assertEquals(2, final.entrants.size)
        assertEquals(league.overallTable(played.results[0]).first().key, final.entrants.first().key)
    }

    @Test
    fun `a sixteen team preset deals four groups and seeds the quarters within each group`() {
        val setup = states(dataset(mapOf(25 to 16), listOf(StateChampionshipEntry(25, 1, 7, true, listOf(false, false, true)))))
        val competition = stateCompetition(setup.divisions.single(), SplitMix64Rng(9))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(4, league.groups.size)
        assertTrue(league.groups.all { it.size == 4 })
        assertEquals(false, league.gamesInsideGroup)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.StateChampionshipsTest"`
Expected: compilation failure.

- [ ] **Step 3: Expose the pyramid tiebreak**

In `engine/src/main/kotlin/org/openfoot/engine/world/Pyramid.kt` the tie break is `private fun tiebreak(worldRng: Rng, ref: String): Int`. Rename it to `internal fun pyramidTiebreak(worldRng: Rng, ref: String): Int`, update its one call site, and extend its docstring with one sentence: the state championships of FORMAT-SPEC order a state's clubs with the same draw.

- [ ] **Step 4: Implement `StateChampionships.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.dataset.WorldDataset
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

@SpecRef("FORMAT-SPEC, formula")
data class StatePreset(val teams: Int, val groups: Int, val qualifiers: Int, val twoTurns: Boolean, val relegated: Int)

@SpecRef("FORMAT-SPEC, formula")
val STATE_PRESETS: List<StatePreset> = listOf(
    StatePreset(6, 0, 2, true, 2),
    StatePreset(8, 0, 4, true, 2),
    StatePreset(10, 0, 4, false, 2),
    StatePreset(11, 0, 4, false, 2),
    StatePreset(12, 0, 4, false, 2),
    StatePreset(12, 0, 8, false, 2),
    StatePreset(14, 0, 8, false, 2),
    StatePreset(16, 4, 2, false, 2),
    StatePreset(16, 0, 4, false, 2),
    StatePreset(16, 0, 8, false, 2),
    StatePreset(20, 4, 2, false, 4),
)

@SpecRef("FORMAT-SPEC, ces")
data class StateDivision(
    val state: Int,
    val division: Int,
    val preset: StatePreset,
    val penalties: Boolean,
    val twoLeggedRounds: List<Boolean>,
    val clubs: List<String>,
)

@SpecRef("FORMAT-SPEC, ces")
data class StateSetup(val divisions: List<StateDivision>, val reserve: Map<Int, List<String>>)

@SpecRef("FORMAT-SPEC, ces")
fun stateSetup(clubs: List<ClubState>, dataset: WorldDataset, tiebreak: (String) -> Int): StateSetup {
    if (!dataset.options.playStateChampionships) return StateSetup(emptyList(), emptyMap())
    val byState = clubs
        .filter { it.country == Country.BRAZIL && it.club.entry.state != null }
        .groupBy { it.club.entry.state!! }
        .toSortedMap()

    val divisions = ArrayList<StateDivision>()
    val reserve = LinkedHashMap<Int, List<String>>()
    for ((state, members) in byState) {
        if (members.size < MINIMUM_STATE_CLUBS) continue
        val total = members.size
        var queue = members
            .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { tiebreak(it.key) }.thenBy { it.key })
            .map { it.key }
        for (division in 1..MAX_STATE_DIVISIONS) {
            if (queue.size < MINIMUM_STATE_CLUBS) break
            val entry = dataset.stateChampionships.firstOrNull { it.state == state && it.division == division }
            val configured = entry?.let { STATE_PRESETS[it.preset] }
            val shaped = if (entry != null && configured != null && configured.teams <= total) {
                StateDivision(state, division, configured, entry.penaltiesTiebreak, entry.twoLeggedRounds, queue.take(configured.teams))
            } else {
                StateDivision(state, division, STATE_PRESETS[0], penalties = true, twoLeggedRounds = listOf(true, true, true), clubs = queue.take(STATE_PRESETS[0].teams))
            }
            divisions += shaped
            queue = queue.drop(shaped.clubs.size)
        }
        if (queue.isNotEmpty()) reserve[state] = queue
    }
    return StateSetup(divisions, reserve)
}

@SpecRef("FORMAT-SPEC, ces")
fun stateCompetition(division: StateDivision, rng: Rng): Competition {
    val preset = division.preset
    val turns = if (preset.twoTurns) 2 else 1
    val league = if (preset.groups == 0) {
        RoundRobinPhase.single(division.clubs, turns)
    } else {
        val dealt = shuffledOrder(division.clubs, rng)
        val groups = (0 until preset.groups).map { g -> dealt.filterIndexed { index, _ -> index % preset.groups == g } }
        RoundRobinPhase.grouped(groups, turns, gamesInsideGroup = false)
    }
    val qualifiers: (RoundRobinPhase, List<Result>) -> List<Entrant> = { phase, results ->
        if (phase.groups.size == 1) {
            phase.overallTable(results).take(preset.qualifiers).mapIndexed { i, row -> Entrant(row.key, i + 1) }
        } else {
            val seeds = GROUP_SEEDS
            phase.groups.indices.flatMap { g ->
                phase.groupTable(g, results).take(preset.qualifiers).mapIndexed { place, row -> Entrant(row.key, seeds[g][place]) }
            }.sortedBy { it.seed }
        }
    }
    return Competition(
        key = "state:${division.state}:${division.division}",
        kind = CompetitionKind.STATE,
        country = Country.BRAZIL,
        division = division.division,
        phases = listOf(
            Phase.League(league),
            Phase.Knockout(KnockoutPhase(emptyList(), division.twoLeggedRounds, division.penalties, field = preset.qualifiers * maxOf(1, preset.groups))),
        ),
        qualifiers = qualifiers,
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

/** Seeds by group and place that put a group's first and second in one quarter final of the eight bracket. */
@SpecRef("FORMAT-SPEC, ces")
private val GROUP_SEEDS: List<List<Int>> = listOf(listOf(2, 7), listOf(4, 5), listOf(1, 8), listOf(3, 6))

@SpecRef("FORMAT-SPEC, ces")
private const val MINIMUM_STATE_CLUBS = 6

@SpecRef("FORMAT-SPEC, ces")
private const val MAX_STATE_DIVISIONS = 4
```

Docstrings in the house style for every public declaration; the `stateSetup` one records that the check against the total rather than the remaining count is FORMAT-SPEC's literal rule and item 69, and that the Sao Paulo real groups option is left for a later plan.

- [ ] **Step 5: Run the tests and the check**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.StateChampionshipsTest" :engine:test --tests "org.openfoot.engine.world.PyramidTest" :engine:checkCommentStyle`
Expected: PASS.

- [ ] **Step 6: Commit**

```
feat(engine): the state championships of FORMAT-SPEC, from files to competitions

The eleven presets, the six club eligibility, the queue by level with the
pyramid's tie break, the default format when a file is missing or asks for
more than the state holds, the reserve, and each division as a league phase
feeding the state bracket, groups dealt round robin and seeded so each
quarter final stays inside its group.
```

---

### Task 5: national leagues from the pyramid

Every active country's divisions become competitions: flat leagues, the grouped fourth division of Brazil with its final phase, and the relegation and promotion lists of 1.12 read from the final order. The Serie C sentinel format, the sixty eight club preliminary and the promotion playoffs are recorded as deferred (Task 10 lists them in OPEN-QUESTIONS).

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/NationalLeagues.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/NationalLeaguesTest.kt`

**Interfaces:**
- Consumes: `ClubState`, `Standing`, `LeagueConfigEntry`, `WorldDataset`, `defaultTurns`, `shuffledOrder`, `RoundRobinPhase`, `KnockoutPhase`, `Competition`, `Rng`.
- Produces:

```kotlin
@SpecRef("1.9")
data class LeagueDivision(val country: Int, val division: Int, val config: LeagueConfigEntry?, val clubs: List<String>)

/** The divisions of one country this season, from the clubs' standings. */
@SpecRef("1.9")
fun leagueDivisions(country: Int, clubs: List<ClubState>, dataset: WorldDataset): List<LeagueDivision>

@SpecRef("1.11")
fun leagueCompetition(division: LeagueDivision, rng: Rng): Competition

/** Relegated (worst first is NOT required: last first) and promoted (best first) of a finished division, per 1.12. */
@SpecRef("1.12")
data class Movement(val relegated: List<String>, val promoted: List<String>)

@SpecRef("1.12")
fun movement(division: LeagueDivision, finalOrder: List<String>, promotedCount: Int): Movement
```

Design:

- `leagueDivisions`: clubs whose `club.standing` is `Standing.InDivision(d)` for the country, grouped by d ascending; each division's `config` is the first `dataset.leagues` entry matching (country, d) or null; clubs listed in dataset order. The relegated count is `config?.relegated` or the embedded default of 1.9: 4 when the division has 20 clubs, 2 otherwise; expose it as `LeagueDivision.relegated`. Turns: `config?.turns ?: defaultTurns(clubs.size)`.
- `leagueCompetition`: `shuffledOrder(clubs, rng)` first (section 1.3). With `config == null || config.groups == 0`: one league phase. With groups: deal the shuffled order round robin into `config.groups` groups, `gamesInsideGroup = config.gamesInsideGroup`; if `config.knockoutQualifiers in 1..64` add a knockout phase whose qualifiers are the top `knockoutQualifiers` of each group (or of the overall table with `qualifyByOverallTable`), seeded in group order then place, legs all two legged (1.11 says the final phase follows the estadual convention; the .cfg has no legs array for it, so two legs is INFERIDO and the docstring says so), penalties on. A `knockoutQualifiers` equal to `LeagueConfigEntry.SERIE_C_FORMAT` is treated as a flat league in this plan and recorded as deferred.
- Key `"league:${country}:${division}"`, kind `NATIONAL_LEAGUE`.
- `movement`: relegated are the last `relegated` of `finalOrder` (when `config.relegatedByGroup` is false, which is every distributed case but the Brazilian first division, whose relegation by group is moot with no groups); promoted are the first `promotedCount` of `finalOrder`. `directRelegated < relegated` (the Spanish playoff) is treated as all direct in this plan and recorded as deferred.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NationalLeaguesTest {

    /** Twenty four Brazilian clubs, levels twenty down to nine, so the pyramid seats twenty in division one and the rest stand without one. */
    private val data = WorldFixtures.dataset(
        clubs = (1..24).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it)) },
    )

    private fun clubs() = generateWorld(data, 4, activeLeagues = setOf(Country.BRAZIL)).clubs.map { ClubState.fresh(it) }

    @Test
    fun `the divisions come from the standings, with the embedded relegation default`() {
        val divisions = leagueDivisions(Country.BRAZIL, clubs(), data)
        assertEquals(1, divisions.size)
        assertEquals(20, divisions.single().clubs.size)
        assertEquals(4, divisions.single().relegated)
        assertEquals(null, divisions.single().config)
    }

    @Test
    fun `a flat division is one shuffled league of the default turns`() {
        val division = leagueDivisions(Country.BRAZIL, clubs(), data).single()
        val competition = leagueCompetition(division, SplitMix64Rng(5))
        val league = (competition.phases.single() as Phase.League).phase
        assertEquals(38, league.rounds.size)
        assertEquals(division.clubs.toSet(), league.participants.toSet())
        assertTrue(league.participants != division.clubs, "the order is shuffled before the circle")
        assertEquals(league.participants, (leagueCompetition(division, SplitMix64Rng(5)).phases.single() as Phase.League).phase.participants)
    }

    @Test
    fun `a grouped division with a final phase deals groups and seeds the knockout by group`() {
        val config = LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 20, relegated = 4, turns = 1, penaltiesTiebreak = true, groups = 4, knockoutQualifiers = 2)
        val division = leagueDivisions(Country.BRAZIL, clubs(), data.copy(leagues = listOf(config))).single()
        val competition = leagueCompetition(division, SplitMix64Rng(5))
        val league = (competition.phases[0] as Phase.League).phase
        assertEquals(4, league.groups.size)
        assertTrue(league.gamesInsideGroup)
        assertEquals(2, competition.phases.size)
    }

    @Test
    fun `movement reads the last and the first of the final order`() {
        val division = leagueDivisions(Country.BRAZIL, clubs(), data).single()
        val order = division.clubs
        val moved = movement(division, order, promotedCount = 2)
        assertEquals(order.takeLast(4), moved.relegated)
        assertEquals(order.take(2), moved.promoted)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.NationalLeaguesTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `NationalLeagues.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.Standing
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

@SpecRef("1.9")
data class LeagueDivision(
    val country: Int,
    val division: Int,
    val config: LeagueConfigEntry?,
    val clubs: List<String>,
) {
    /** The configured count, or the embedded default: four on the twenty club step, two otherwise. */
    @SpecRef("1.9")
    val relegated: Int get() = config?.relegated ?: if (clubs.size == TWENTY_CLUB_STEP) DEFAULT_RELEGATED_TWENTY else DEFAULT_RELEGATED

    val turns: Int get() = config?.turns ?: defaultTurns(clubs.size)
}

@SpecRef("1.9")
fun leagueDivisions(country: Int, clubs: List<ClubState>, dataset: WorldDataset): List<LeagueDivision> =
    clubs
        .filter { it.country == country }
        .mapNotNull { state -> (state.standing as? Standing.InDivision)?.let { it.division to state.key } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (division, refs) ->
            LeagueDivision(country, division, dataset.leagues.firstOrNull { it.country == country && it.division == division }, refs)
        }

@SpecRef("1.11")
fun leagueCompetition(division: LeagueDivision, rng: Rng): Competition {
    val order = shuffledOrder(division.clubs, rng)
    val config = division.config
    val groups = config?.groups ?: 0
    val phases = ArrayList<Phase>()
    val qualifiers: (RoundRobinPhase, List<Result>) -> List<Entrant>
    if (groups == 0 || config == null) {
        phases += Phase.League(RoundRobinPhase.single(order, division.turns))
        qualifiers = { _, _ -> emptyList() }
    } else {
        val dealt = (0 until groups).map { g -> order.filterIndexed { index, _ -> index % groups == g } }
        phases += Phase.League(RoundRobinPhase.grouped(dealt, division.turns, config.gamesInsideGroup))
        val perGroup = config.knockoutQualifiers
        if (perGroup in 1..MAX_KNOCKOUT_FIELD) {
            phases += Phase.Knockout(KnockoutPhase(emptyList(), legsPerRound = List(LeagueConfigEntry.PLAYOFF_ROUNDS) { true }, penalties = true, field = perGroup * groups))
        }
        qualifiers = { phase, results ->
            val picked = if (config.qualifyByOverallTable) {
                phase.overallTable(results).take(perGroup * groups).map { it.key }
            } else {
                phase.groups.indices.flatMap { g -> phase.groupTable(g, results).take(perGroup).map { it.key } }
            }
            picked.mapIndexed { i, key -> Entrant(key, i + 1) }
        }
    }
    return Competition(
        key = "league:${division.country}:${division.division}",
        kind = CompetitionKind.NATIONAL_LEAGUE,
        country = division.country,
        division = division.division,
        phases = phases,
        qualifiers = qualifiers,
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

@SpecRef("1.12")
data class Movement(val relegated: List<String>, val promoted: List<String>)

@SpecRef("1.12")
fun movement(division: LeagueDivision, finalOrder: List<String>, promotedCount: Int): Movement =
    Movement(relegated = finalOrder.takeLast(division.relegated), promoted = finalOrder.take(promotedCount))

@SpecRef("1.9")
private const val TWENTY_CLUB_STEP = 20

@SpecRef("1.9")
private const val DEFAULT_RELEGATED_TWENTY = 4

@SpecRef("1.9")
private const val DEFAULT_RELEGATED = 2

/** The largest final phase a configured league can ask for before the value is a sentinel of 1.11. */
@SpecRef("1.11")
private const val MAX_KNOCKOUT_FIELD = 64
```

A knockout phase whose qualifier count is not a power of two would fail `KnockoutPhase`'s check when seeded; the Brazilian fourth division seeds 4 per group times 8 groups, 32, which is fine. Docstring `leagueCompetition` with the two deferrals (Serie C sentinel, promotion playoffs) and the INFERIDO two legged final phase.

- [ ] **Step 4: Run the tests and the check**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.NationalLeaguesTest" :engine:checkCommentStyle`
Expected: PASS.

- [ ] **Step 5: Commit**

```
feat(engine): national league divisions as competitions, with the movement of 1.12

Each active country's divisions come from the clubs' standings and the
league configuration: a shuffled flat league by default, groups with a
final phase when the file asks, the embedded relegation default, and the
relegated and promoted lists read off a finished division's final order.
```

---

### Task 6: the national cup

Section 1.13's standard format: a single elimination bracket of the largest power of two up to 128 that fits the country's clubs, strong against weak with a shuffle inside each half, every round two legged, weaker side hosting the first leg. The "novo formato" (Brazil with 91 or more clubs) needs the continental qualification of a later plan for its twelve seeds and has an open pot pattern (item 82), so this plan plays the standard format everywhere and records the deferral.

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/NationalCup.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/NationalCupTest.kt`

**Interfaces:**
- Consumes: `ClubState`, `shuffledOrder`, `KnockoutPhase`, `Entrant`, `Competition`, `Rng`.
- Produces: `fun nationalCup(country: Int, clubs: List<ClubState>, rng: Rng): Competition?` (null when the country has fewer than eight clubs), key `"cup:$country"`, kind `NATIONAL_CUP`, division null.

Design:

- Field size: the largest power of two at most `min(128, clubs.size)`, and at least 8, else null.
- Entrants: the country's clubs ordered by level descending (ties by ref), the first `size` taken; the top half and the bottom half each shuffled with `rng.fork(0)` and `rng.fork(1)`; then seeded so that `KnockoutPhase`'s strong against weak pairing (entrant i against n-1-i) meets a strong side with a weak one: seeds 1..n/2 to the strong half in shuffled order, seeds n/2+1..n to the weak half in shuffled order. Because `Tie` puts the lower seed as `higher`, the weak side hosts the first leg through `Tie.host(1, twoLegged = true)`, which is exactly 1.13's convention.
- Every round two legged (`legsPerRound = List(7) { true }`), penalties true.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.openfoot.engine.season

import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.Country
import org.openfoot.model.RuleSets
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NationalCupTest {

    private fun clubs(count: Int) = generateWorld(
        WorldFixtures.dataset(clubs = (1..count).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it)) }),
        2,
        activeLeagues = setOf(Country.BRAZIL),
    ).clubs.map { ClubState.fresh(it) }

    @Test
    fun `the bracket is the largest power of two that fits, never below eight`() {
        assertNull(nationalCup(Country.BRAZIL, clubs(7), SplitMix64Rng(1)))
        val ten = nationalCup(Country.BRAZIL, clubs(10), SplitMix64Rng(1))!!
        assertEquals(8, ten.participants.size)
        val eighteen = nationalCup(Country.BRAZIL, clubs(18), SplitMix64Rng(1))!!
        assertEquals(16, eighteen.participants.size)
    }

    @Test
    fun `the strong half meets the weak half, and the weak side hosts the first leg`() {
        val clubs = clubs(16)
        val cup = nationalCup(Country.BRAZIL, clubs, SplitMix64Rng(3))!!
        val strong = clubs.sortedByDescending { it.club.entry.level }.take(8).map { it.key }.toSet()
        val firstLegs = cup.nextMatches(RuleSets.CLASSIC, SplitMix64Rng(3))
        assertEquals(8, firstLegs.size)
        assertTrue(firstLegs.all { it.fixture.away in strong && it.fixture.home !in strong })
        assertEquals(1, firstLegs.first().leg)
    }

    @Test
    fun `the same seed draws the same cup`() {
        val clubs = clubs(12)
        assertEquals(nationalCup(Country.BRAZIL, clubs, SplitMix64Rng(8))!!.participants, nationalCup(Country.BRAZIL, clubs, SplitMix64Rng(8))!!.participants)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.NationalCupTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `NationalCup.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

@SpecRef("1.13")
fun nationalCup(country: Int, clubs: List<ClubState>, rng: Rng): Competition? {
    val eligible = clubs.filter { it.country == country }
        .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { it.key })
        .map { it.key }
    val size = bracketSize(eligible.size) ?: return null
    val field = eligible.take(size)
    val strong = shuffledOrder(field.take(size / 2), rng.fork(STRONG_HALF))
    val weak = shuffledOrder(field.drop(size / 2), rng.fork(WEAK_HALF))
    val entrants = (strong + weak).mapIndexed { index, key -> Entrant(key, index + 1) }
    return Competition(
        key = "cup:$country",
        kind = CompetitionKind.NATIONAL_CUP,
        country = country,
        division = null,
        phases = listOf(Phase.Knockout(KnockoutPhase(entrants, legsPerRound = List(MAX_CUP_ROUNDS) { true }, penalties = true))),
        qualifiers = { _, _ -> emptyList() },
        results = listOf(emptyList()),
        phaseIndex = 0,
        roundIndex = 0,
    )
}

/** The largest power of two at most the club count and the cap, or null below the minimum field. */
@SpecRef("1.13")
internal fun bracketSize(clubCount: Int): Int? {
    var size = MAX_CUP_FIELD
    while (size > clubCount) size /= 2
    return if (size >= MIN_CUP_FIELD) size else null
}

@SpecRef("1.13")
private const val MAX_CUP_FIELD = 128

@SpecRef("1.13")
private const val MIN_CUP_FIELD = 8

@SpecRef("1.13")
private const val MAX_CUP_ROUNDS = 7

@SpecRef("1.13")
private const val STRONG_HALF = 0L

@SpecRef("1.13")
private const val WEAK_HALF = 1L
```

Docstring `nationalCup` with the standard format reading and the deferral of the novo formato (twelve seeds from the continental qualification, the open pot pattern of item 82, and the hardcoded no shootout first round), and note that in a knockout only competition `Competition.participants` reads the entrants.

- [ ] **Step 4: Run the tests and the check**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.NationalCupTest" :engine:checkCommentStyle`
Expected: PASS.

- [ ] **Step 5: Commit**

```
feat(engine): the national cup in its standard format

The largest bracket up to a hundred and twenty eight that fits the
country's clubs, at least eight, strong half against weak half with a
shuffle inside each, every round two legged with the weaker side hosting
the first leg. The novo formato of Brazil waits for the continental
qualification it seeds from.
```

---

### Task 7: the season schedule

Dates for every round of every competition, built once at season start, with one match per club per date guaranteed by construction. The original's calendar is emergent from day types (1.10) and its exact interleaving is item 74; this task fixes the INFERIDO policy the design chose and pins it with an invariant test.

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/Schedule.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/ScheduleTest.kt`

**Interfaces:**
- Consumes: `CalendarDate`, `Competition`, `CompetitionKind`, `Phase`, `RoundRobinPhase`, `KnockoutPhase`.
- Produces:

```kotlin
/** One competition's round on one date. */
@SpecRef("1.10")
data class Slot(val date: CalendarDate, val competition: String)

@SpecRef("1.10")
data class SeasonSchedule(val year: Int, val start: CalendarDate, val slots: List<Slot>) {
    val dates: List<CalendarDate>          // distinct, ascending
    fun on(date: CalendarDate): List<String>   // competition keys with a round that day
    companion object {
        fun build(year: Int, competitions: List<Competition>): SeasonSchedule
        fun roundCount(competition: Competition): Int
    }
}
```

Policy (INFERIDO, item 74; every constant `@SpecRef("1.10")`):

- Week w counts from the season start, the first Sunday of January (section 0). Sunday of week w is `start.plusDays(7 * w)`; Wednesday of week w is `start.plusDays(7 * w + 3)`.
- State championships: Sundays from week 0, and Wednesdays from week 0, alternating Sunday, Wednesday, Sunday, Wednesday, so their rounds run twice a week and finish early in the year.
- National leagues: Sundays starting at week 12 (late March), one round a week, all divisions of every country on the same Sundays; a twenty club league of thirty eight rounds ends in week 49.
- National cup: Wednesdays starting at week 12, one round every two weeks (weeks 12, 14, 16, ...), two legged rounds on consecutive fortnights.
- `roundCount` sums the league phases' round counts and each knockout phase's leg count (`(0 until rounds).sumOf { if twoLegged 2 else 1 }`).
- A competition that runs out of dates before the year ends is an error (`require`), so the policy is checked at build time.

The invariant this policy gives: a club plays in at most one state division, at most one league division and the cup; state dates never coincide with league or cup dates because state rounds end before week 12 for every configured shape (a 20 club two group preset plays 15 cross group rounds plus 6 knockout legs at two a week, 21 dates, done by week 10), and `build` requires it: a state competition whose last date is not before the league start is refused; league Sundays and cup Wednesdays never coincide. The test asserts, for a Brazil shaped season, that no club key appears twice on one date.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleTest {

    private fun league(key: String, clubs: List<String>, turns: Int = 2) = Competition(
        key, CompetitionKind.NATIONAL_LEAGUE, 29, 1,
        listOf(Phase.League(RoundRobinPhase.single(clubs, turns))), { _, _ -> emptyList() }, listOf(emptyList()), 0, 0,
    )

    private fun cup(key: String, clubs: List<String>) = Competition(
        key, CompetitionKind.NATIONAL_CUP, 29, null,
        listOf(Phase.Knockout(KnockoutPhase(clubs.mapIndexed { i, c -> Entrant(c, i + 1) }, List(7) { true }, true))),
        { _, _ -> emptyList() }, listOf(emptyList()), 0, 0,
    )

    private fun state(key: String, clubs: List<String>) = Competition(
        key, CompetitionKind.STATE, 29, 1,
        listOf(Phase.League(RoundRobinPhase.single(clubs, 2)), Phase.Knockout(KnockoutPhase(emptyList(), listOf(true, true, true), true, field = 2))),
        { phase, results -> phase.overallTable(results).take(2).mapIndexed { i, r -> Entrant(r.key, i + 1) } }, listOf(emptyList()), 0, 0,
    )

    private val twenty = (1..20).map { "c$it" }

    @Test
    fun `round counts add league rounds and knockout legs`() {
        assertEquals(38, SeasonSchedule.roundCount(league("l", twenty)))
        assertEquals(8, SeasonSchedule.roundCount(cup("k", twenty.take(16))))
        assertEquals(12, SeasonSchedule.roundCount(state("s", twenty.take(6))))
    }

    @Test
    fun `leagues play Sundays from late March, cups Wednesdays every fortnight, states twice a week from January`() {
        val schedule = SeasonSchedule.build(2026, listOf(league("l", twenty), cup("k", twenty.take(16)), state("s", twenty.take(6))))
        val start = CalendarDate.seasonStart(2026)
        assertEquals(start, schedule.start)
        val leagueDates = schedule.slots.filter { it.competition == "l" }.map { it.date }
        assertEquals(38, leagueDates.size)
        assertEquals(start.plusDays(7 * 12), leagueDates.first())
        assertTrue(leagueDates.all { it.isSunday })
        val cupDates = schedule.slots.filter { it.competition == "k" }.map { it.date }
        assertEquals(start.plusDays(7 * 12 + 3), cupDates.first())
        assertEquals(14, cupDates[0].daysUntil(cupDates[1]))
        val stateDates = schedule.slots.filter { it.competition == "s" }.map { it.date }
        assertEquals(start, stateDates[0])
        assertEquals(start.plusDays(3), stateDates[1])
        assertEquals(start.plusDays(7), stateDates[2])
    }

    @Test
    fun `no competition of one country shares a date with another of a different weekday policy`() {
        val schedule = SeasonSchedule.build(2026, listOf(league("l", twenty), cup("k", twenty.take(16)), state("s", twenty.take(6))))
        for (date in schedule.dates) {
            val keys = schedule.on(date)
            assertTrue(keys.size == 1 || keys.toSet() == setOf("l"), "$date holds $keys")
        }
        assertTrue(schedule.dates.zipWithNext().all { (a, b) -> a < b })
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.ScheduleTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `Schedule.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.model.CompetitionKind
import org.openfoot.model.SpecRef

@SpecRef("1.10")
data class Slot(val date: CalendarDate, val competition: String)

@SpecRef("1.10")
data class SeasonSchedule(val year: Int, val start: CalendarDate, val slots: List<Slot>) {
    val dates: List<CalendarDate> get() = slots.map { it.date }.distinct().sorted()

    fun on(date: CalendarDate): List<String> = slots.filter { it.date == date }.map { it.competition }

    companion object {
        @SpecRef("1.10")
        fun build(year: Int, competitions: List<Competition>): SeasonSchedule {
            val start = CalendarDate.seasonStart(year)
            val end = CalendarDate(year, 12, 31)
            val slots = ArrayList<Slot>()
            for (competition in competitions) {
                val dates = datesFor(competition.kind, start).take(roundCount(competition))
                require(dates.size == roundCount(competition) && dates.all { it <= end }) {
                    "${competition.key} does not fit the year $year"
                }
                if (competition.kind == CompetitionKind.STATE) {
                    require(dates.lastOrNull()?.let { it < start.plusDays(DAYS_IN_WEEK * LEAGUE_FIRST_WEEK) } ?: true) {
                        "${competition.key} runs past the league start, and states must end before it"
                    }
                }
                dates.forEach { slots += Slot(it, competition.key) }
            }
            return SeasonSchedule(year, start, slots.sortedWith(compareBy<Slot> { it.date }.thenBy { it.competition }))
        }

        fun roundCount(competition: Competition): Int = competition.phases.sumOf { phase ->
            when (phase) {
                is Phase.League -> phase.phase.rounds.size
                is Phase.Knockout -> (0 until phase.phase.rounds).sumOf { if (phase.phase.twoLegged(it)) 2 else 1 }
            }
        }

        @SpecRef("1.10")
        private fun datesFor(kind: CompetitionKind, start: CalendarDate): Sequence<CalendarDate> = when (kind) {
            CompetitionKind.STATE -> generateSequence(0) { it + 1 }.map { i ->
                val week = i / 2
                start.plusDays(DAYS_IN_WEEK * week + if (i % 2 == 0) 0 else WEDNESDAY_OFFSET)
            }
            CompetitionKind.NATIONAL_LEAGUE -> generateSequence(LEAGUE_FIRST_WEEK) { it + 1 }.map { start.plusDays(DAYS_IN_WEEK * it) }
            CompetitionKind.NATIONAL_CUP -> generateSequence(LEAGUE_FIRST_WEEK) { it + CUP_FORTNIGHT }.map { start.plusDays(DAYS_IN_WEEK * it + WEDNESDAY_OFFSET) }
            else -> throw IllegalArgumentException("no schedule policy for $kind in this version")
        }
    }
}

@SpecRef("1.10")
private const val DAYS_IN_WEEK = 7

@SpecRef("1.10")
private const val WEDNESDAY_OFFSET = 3

@SpecRef("1.10")
private const val LEAGUE_FIRST_WEEK = 12

@SpecRef("1.10")
private const val CUP_FORTNIGHT = 2
```

`KnockoutPhase.rounds` reads the phase's `field`, which Tasks 3 to 5 set when they build an unseeded phase, so the schedule counts a knockout's legs before it is seeded.

Docstring `SeasonSchedule` with the policy and its INFERIDO status (item 74), and the one match per club per date invariant it exists to keep (item 72).

- [ ] **Step 4: Run every season test and the check**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.*" :engine:checkCommentStyle`
Expected: PASS.

- [ ] **Step 5: Commit**

```
feat(engine): the season schedule, one date per competition round

State championships twice a week from the first Sunday of January, leagues
on Sundays from May, the cup on alternate Wednesdays: the interleaving the
spec could not fix (item 74), declared as the policy that keeps one match
per club per date (item 72). A knockout phase now carries its field size
before it is seeded, so a schedule can count its legs.
```

---

### Task 8: the season state and the round loop

The state a season carries and the one function that advances it: the next scheduled date, its matches assembled and simulated from the club states, the records updated from the reports, the post-round of section 3.1 for every competition that played, and the pending Sundays of section 0 handed to a weekly tick that a later plan fills.

**Files:**
- Modify: `engine/src/main/kotlin/org/openfoot/engine/lineup/MatchAssembly.kt` (availability per side)
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/SeasonState.kt`
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/RoundLoop.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/RoundLoopTest.kt`

**Interfaces:**
- Consumes: `ClubState`, `PlayerRecord`, `Competition`, `ScheduledMatch`, `SeasonSchedule`, `Result`, `CalendarDate`, `assembleMatch`, `simulateMatch`, `MatchReport`, `playerRatings`, `MatchEvent`, `GoalType`, `afterMatch`, `weeklyRecovery`, `recover`, `titlePrestige`, `clubKey` (engine world package, internal), `SeedDomain`, `SplitMix64Rng`, `RuleSet`, `WorldDataset`.
- Produces:

```kotlin
@SpecRef("1.10")
data class PlayedMatch(val date: CalendarDate, val competition: String, val match: ScheduledMatch, val result: Result)

@SpecRef("5.5")
data class CompetitionClose(val date: CalendarDate, val key: String, val kind: CompetitionKind, val finalOrder: List<String>)

/** What a later plan runs on every calendar Sunday; this plan passes NONE. */
@SpecRef("4.5")
fun interface WeeklyTick {
    fun apply(state: SeasonState, sunday: CalendarDate): SeasonState
    companion object { val NONE = WeeklyTick { state, _ -> state } }
}

@SpecRef("1.10")
data class SeasonState(
    val number: Int,
    val year: Int,
    val seed: Long,
    val dataset: WorldDataset,
    val clubs: Map<String, ClubState>,
    val competitions: Map<String, Competition>,
    val schedule: SeasonSchedule,
    val played: List<PlayedMatch>,
    val closed: List<CompetitionClose>,
    val dateIndex: Int,
    val lastTick: CalendarDate?,
) {
    val finished: Boolean
    val today: CalendarDate?      // the next date to play, null when finished
    fun club(key: String): ClubState
    fun withClub(state: ClubState): SeasonState
}

@SpecRef("3.1")
fun playRound(state: SeasonState, rules: RuleSet, tick: WeeklyTick): SeasonState

@SpecRef("3.1")
fun playSeason(state: SeasonState, rules: RuleSet, tick: WeeklyTick): SeasonState   // until finished
```

Also in this task, `assembleMatch` gains an overload whose availability is chosen per side: change the existing function's `availability: Availability` parameter into `availabilityOf: (Competitor) -> Availability`, keep the old signature as a thin wrapper `assembleMatch(home, away, dataset, kind, season, rules, availability: Availability, rng) = assembleMatch(home, away, dataset, kind, season, rules, { availability }, rng)`, and pass `availabilityOf(club)` in `assembleSide`. The docstring's paragraph on availability changes to say that a season holds one record per club and answers per side. Existing tests keep compiling.

Design of `playRound`, in order (every step is a `@SpecRef` where it reads the spec):

1. `require(!state.finished)`. `date = state.schedule.dates[state.dateIndex]`.
2. Pending Sundays (section 0): for every Sunday `s` with `(state.lastTick ?: state.schedule.start.plusDays(-1)) < s <= date`, in order, `current = tick.apply(current, s)`; then `lastTick = date` only if `date` is a Sunday, otherwise the last Sunday passed. Whether the pending Sundays fire before or after the day's matches the spec does not say; before is the reading taken (Task 10 records it).
3. Season rng root: `SplitMix64Rng(state.seed).fork(SeedDomain.SEASON).fork(state.number.toLong())`.
4. For every competition key in `schedule.on(date)`, in that order: `matches = competition.nextMatches(rules, root.fork(SeedDomain.FIXTURES).fork(clubKey(competition.key)))`. For each match: `matchRng = root.fork(SeedDomain.MATCH).fork(date.ordinal.toLong()).fork(clubKey(fixture.home)).fork(clubKey(fixture.away))`; `assembled = assembleMatch(home = club(home), away = club(away), dataset, kind = competition.kind, season = state.number, rules, availabilityOf = { competitor -> (competitor as ClubState).availability(date) }, rng = matchRng)`; `report = simulateMatch(assembled.setup, matchRng, assembled.homeBench, assembled.awayBench)`; `ratings = report.playerRatings(rules, matchRng)`; `result = Result(home, away, report.homeGoals, report.awayGoals)`.
5. Records after a match, for each side (`TeamSide.HOME` with the home club, `AWAY` with the away club), where `id.value` indexes the club's squad:
   - named: every `PlayerId` in `report.energy(side)` (lineup and bench) gets `namedSinceTick = true` and `energy = report.energy(side)[id]`;
   - appearances: every id in `ratings.of(side)` (starters and men who came on) `appearances + 1`, `ratingSum + rating.value`, `ratingCount + 1`;
   - goals: for every `MatchEvent.Goal` of that side with `type != GoalType.OWN_GOAL` and a non-null `scorer`, `goals + 1` on the scorer;
   - discipline: `records.mapIndexed { i, r -> PlayerId(i) to r.discipline }.toMap().afterMatch(report.log, side, matchRng.fork(SEASON_DISCIPLINE_STREAM))`, written back; `SEASON_DISCIPLINE_STREAM = 0x5EA5L` with `@SpecRef("3.8")`;
   - injuries: every `MatchEvent.Injury` of that side with `days > 0` sets `injuredUntil = date.plusDays(event.days)`; a `permanentStrengthLoss > 0` replaces the squad's `Player` with `copy(strength = strength - loss)` (section 3.8, the one place a match writes strength).
6. `competition.recorded(matches zip results)`; `played += PlayedMatch(...)` per match.
7. Post-round (3.1) for every participant of every competition that played today, once per club even if it appears in the round's byes: for every record, `served()` on a suspended discipline (a suspended man did not play by construction); `energy = recover(energy, weeklyRecovery(player.age, played = id in ratings for that match, humanManaged = false))` where a man with no match today counts as not played. A club that appears in two competitions today cannot happen (Task 7's invariant); `require` it anyway.
8. A competition that `finished` after recording closes now: `finalOrder = competition.finalOrder(rules, root.fork(SeedDomain.FIXTURES).fork(clubKey(key)))`, `closed += CompetitionClose(date, key, kind, finalOrder)`, and the champion and the runner-up get `prestige.awarded(titlePrestige(kind, champion, club.inLeague, dataset.country(club.country)!!.continent, competition.division))` (5.5, credited when the competition closes).
9. `dateIndex + 1`.

`playSeason` loops `playRound` until `finished`.

- [ ] **Step 1: Write the failing test**

Build a small world: Brazil with 12 clubs of 18 players each (use the `squad(...)` builder pattern of `MatchAssemblyTest`, copied into this test, with two keepers, three centrebacks, three fullbacks, six midfielders, four forwards, so every lineup fills), which the pyramid seats as one division of 10 plus two without a division; a cup of 8; no states. Assemble the state with a helper that Task 9 will also use, defined here in `SeasonState.kt`:

```kotlin
/** The opening state of season one over a generated world: clubs fresh, competitions built, schedule laid. */
@SpecRef("1.10")
fun openingSeason(world: World, dataset: WorldDataset, activeLeagues: Set<Int>, year: Int, seed: Long): SeasonState
```

which builds `ClubState.fresh` for every club, `leagueDivisions` per active country turned into `leagueCompetition` (rng `SplitMix64Rng(seed).fork(SeedDomain.SEASON).fork(1).fork(SeedDomain.FIXTURES).fork(clubKey(key))`), `nationalCup` per active country with the same forking, `stateSetup` with the pyramid tie break of `SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)` and `stateCompetition` per division, and `SeasonSchedule.build(year, competitions)`.

```kotlin
package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoundLoopTest {

    private fun squad(club: String) = buildList {
        add(WorldFixtures.player(name = "$club g1", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        add(WorldFixtures.player(name = "$club g2", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        repeat(3) { add(WorldFixtures.player(name = "$club z$it", position = Position.CENTREBACK, first = Trait.MARKING, second = Trait.TACKLING)) }
        repeat(3) { add(WorldFixtures.player(name = "$club l$it", position = Position.FULLBACK, first = Trait.PACE, second = Trait.CROSSING)) }
        repeat(6) { add(WorldFixtures.player(name = "$club m$it", position = Position.MIDFIELDER, first = Trait.PASSING, second = Trait.PLAYMAKING)) }
        repeat(4) { add(WorldFixtures.player(name = "$club a$it", position = Position.FORWARD, first = Trait.FINISHING, second = Trait.HEADING)) }
    }

    private val data = WorldFixtures.dataset(
        clubs = (1..12).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it), squad = squad("c$it")) },
    ).copy(leagues = listOf(LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true)))

    private fun opening(seed: Long) = openingSeason(generateWorld(data, seed, setOf(Country.BRAZIL)), data, setOf(Country.BRAZIL), 2026, seed)

    @Test
    fun `the opening season holds one division of ten, a cup of eight and a schedule for both`() {
        val state = opening(1)
        assertEquals(setOf("league:29:1", "cup:29"), state.competitions.keys)
        assertEquals(10, state.competitions.getValue("league:29:1").participants.size)
        assertEquals(8, state.competitions.getValue("cup:29").participants.size)
        assertEquals(12, state.clubs.size)
        assertEquals(CalendarDate.seasonStart(2026).plusDays(7 * 12), state.today)
    }

    @Test
    fun `a round plays every scheduled match, records results and moves the date`() {
        val before = opening(1)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        assertEquals(5, after.played.size)
        assertTrue(after.played.all { it.date == before.today && it.competition == "league:29:1" })
        assertEquals(1, after.dateIndex)
        val league = after.competitions.getValue("league:29:1")
        assertEquals(5, league.results[0].size)
        assertEquals(after.played.map { it.result }, league.results[0])
    }

    @Test
    fun `a match leaves its marks on the records of both clubs`() {
        val before = opening(2)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        val match = after.played.first()
        val home = after.club(match.result.home)
        assertTrue(home.records.count { it.appearances == 1 } >= 11, "the starters appeared")
        assertTrue(home.records.count { it.namedSinceTick } >= home.records.count { it.appearances == 1 }, "the bench was named")
        assertTrue(home.records.filter { it.appearances == 1 }.all { it.ratingCount == 1 })
        val away = after.club(match.result.away)
        assertTrue(home.records.sumOf { it.goals } <= match.result.homeGoals, "goals credit the scorers, own goals aside")
    }

    @Test
    fun `the post round recovers everyone of a competition that played, played or not`() {
        val before = opening(3)
        val after = playRound(before, RuleSets.CLASSIC, WeeklyTick.NONE)
        val rested = after.clubs.values.filter { it.standing is org.openfoot.engine.world.Standing.WithoutDivision }
        assertTrue(rested.all { club -> club.records.all { it.energy == 100 } }, "clubs outside the round are untouched")
        val played = after.club(after.played.first().result.home)
        assertTrue(played.records.all { it.energy in 1..100 })
    }

    @Test
    fun `pending Sundays reach the tick in order before the day's matches`() {
        val before = opening(4)
        val seen = ArrayList<CalendarDate>()
        val tick = WeeklyTick { state, sunday -> seen += sunday; state }
        val after = playRound(before, RuleSets.CLASSIC, tick)
        assertEquals(13, seen.size, "the first league Sunday is week twelve, and every Sunday from the start fires once")
        assertTrue(seen.zipWithNext().all { (a, b) -> a.daysUntil(b) == 7 })
        assertEquals(before.today, seen.last())
        assertEquals(seen.last(), after.lastTick)
        val next = playRound(after, RuleSets.CLASSIC, WeeklyTick { state, sunday -> seen += sunday; state })
        assertEquals(14, seen.size)
        assertEquals(next.today?.plusDays(-7), seen.last())
    }

    @Test
    fun `a whole season finishes, closes every competition and crediting prestige to the champions`() {
        val end = playSeason(opening(5), RuleSets.CLASSIC, WeeklyTick.NONE)
        assertTrue(end.finished)
        assertEquals(setOf("league:29:1", "cup:29"), end.closed.map { it.key }.toSet())
        assertTrue(end.competitions.values.all { it.finished })
        val leagueClose = end.closed.single { it.kind == CompetitionKind.NATIONAL_LEAGUE }
        assertEquals(10, leagueClose.finalOrder.size)
        val cupClose = end.closed.single { it.kind == CompetitionKind.NATIONAL_CUP }
        fun cupPrize(key: String): Long = when (key) {
            cupClose.finalOrder[0] -> 300L
            cupClose.finalOrder[1] -> 50L
            else -> 0L
        }
        val champion = leagueClose.finalOrder[0]
        val runnerUp = leagueClose.finalOrder[1]
        assertEquals(500 + cupPrize(champion), end.club(champion).prestige.balance)
        assertEquals(90 + cupPrize(runnerUp), end.club(runnerUp).prestige.balance)
        assertEquals(45 + 14, end.played.size, "forty five league matches and a cup of eight over two legs")
    }

    @Test
    fun `the same seed plays the same season`() {
        val once = playSeason(opening(6), RuleSets.CLASSIC, WeeklyTick.NONE)
        val twice = playSeason(opening(6), RuleSets.CLASSIC, WeeklyTick.NONE)
        assertEquals(once.played, twice.played)
        assertEquals(once.clubs.mapValues { it.value.records }, twice.clubs.mapValues { it.value.records })
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.RoundLoopTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `SeasonState.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.World
import org.openfoot.engine.world.clubKey
import org.openfoot.engine.world.pyramidTiebreak
import org.openfoot.model.CompetitionKind
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng

@SpecRef("1.10")
data class PlayedMatch(val date: CalendarDate, val competition: String, val match: ScheduledMatch, val result: Result)

@SpecRef("5.5")
data class CompetitionClose(val date: CalendarDate, val key: String, val kind: CompetitionKind, val finalOrder: List<String>)

@SpecRef("4.5")
fun interface WeeklyTick {
    fun apply(state: SeasonState, sunday: CalendarDate): SeasonState

    companion object {
        val NONE = WeeklyTick { state, _ -> state }
    }
}

@SpecRef("1.10")
data class SeasonState(
    val number: Int,
    val year: Int,
    val seed: Long,
    val dataset: WorldDataset,
    val clubs: Map<String, ClubState>,
    val competitions: Map<String, Competition>,
    val schedule: SeasonSchedule,
    val played: List<PlayedMatch>,
    val closed: List<CompetitionClose>,
    val dateIndex: Int,
    val lastTick: CalendarDate?,
) {
    val finished: Boolean get() = dateIndex >= schedule.dates.size

    val today: CalendarDate? get() = schedule.dates.getOrNull(dateIndex)

    fun club(key: String): ClubState = clubs[key] ?: throw IllegalArgumentException("no club $key in this season")

    fun withClub(state: ClubState): SeasonState = copy(clubs = clubs + (state.key to state))

    fun withCompetition(competition: Competition): SeasonState = copy(competitions = competitions + (competition.key to competition))
}

@SpecRef("1.10")
fun openingSeason(world: World, dataset: WorldDataset, activeLeagues: Set<Int>, year: Int, seed: Long): SeasonState {
    val clubs = world.clubs.map { ClubState.fresh(it) }
    val number = 1
    val root = SplitMix64Rng(seed).fork(SeedDomain.SEASON).fork(number.toLong()).fork(SeedDomain.FIXTURES)
    val worldRng = SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)

    val competitions = ArrayList<Competition>()
    for (country in activeLeagues.sorted()) {
        leagueDivisions(country, clubs, dataset).forEach { division ->
            competitions += leagueCompetition(division, root.fork(clubKey("league:$country:${division.division}")))
        }
        nationalCup(country, clubs, root.fork(clubKey("cup:$country")))?.let { competitions += it }
    }
    val states = stateSetup(clubs, dataset) { ref -> pyramidTiebreak(worldRng, ref) }
    states.divisions.forEach { division ->
        competitions += stateCompetition(division, root.fork(clubKey("state:${division.state}:${division.division}")))
    }

    return SeasonState(
        number = number,
        year = year,
        seed = seed,
        dataset = dataset,
        clubs = clubs.associateBy { it.key },
        competitions = competitions.associateBy { it.key },
        schedule = SeasonSchedule.build(year, competitions),
        played = emptyList(),
        closed = emptyList(),
        dateIndex = 0,
        lastTick = null,
    )
}
```

`associateBy` returns a `LinkedHashMap`, insertion ordered; both maps are iterated only through `schedule.on(date)` and sorted keys, so no map order is load bearing.

- [ ] **Step 4: Implement `RoundLoop.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.engine.lineup.Availability
import org.openfoot.engine.lineup.assembleMatch
import org.openfoot.engine.match.MatchEvent
import org.openfoot.engine.match.MatchReport
import org.openfoot.engine.match.MatchRatings
import org.openfoot.engine.match.playerRatings
import org.openfoot.engine.match.simulateMatch
import org.openfoot.engine.world.clubKey
import org.openfoot.model.GoalType
import org.openfoot.model.PlayerId
import org.openfoot.model.Rng
import org.openfoot.model.RuleSet
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng
import org.openfoot.model.TeamSide

@SpecRef("3.1")
fun playRound(state: SeasonState, rules: RuleSet, tick: WeeklyTick): SeasonState {
    require(!state.finished) { "season ${state.number} has finished" }
    val date = state.today!!
    var current = firePendingSundays(state, date, tick)
    val root = SplitMix64Rng(state.seed).fork(SeedDomain.SEASON).fork(state.number.toLong())

    val playedToday = mutableSetOf<String>()
    val appeared = mutableMapOf<String, Set<Int>>()
    for (key in current.schedule.on(date)) {
        val competition = current.competitions.getValue(key)
        val fixturesRng = root.fork(SeedDomain.FIXTURES).fork(clubKey(key))
        val matches = competition.nextMatches(rules, fixturesRng)
        val recorded = ArrayList<Pair<ScheduledMatch, Result>>()
        for (match in matches) {
            val home = match.fixture.home
            val away = match.fixture.away
            require(playedToday.add(home) && playedToday.add(away)) { "$home or $away plays twice on $date" }
            val matchRng = root.fork(SeedDomain.MATCH).fork(date.ordinal.toLong()).fork(clubKey(home)).fork(clubKey(away))
            val assembled = assembleMatch(
                home = current.club(home),
                away = current.club(away),
                dataset = current.dataset,
                kind = competition.kind,
                season = current.number,
                rules = rules,
                availabilityOf = { competitor -> (competitor as ClubState).availability(date) },
                rng = matchRng,
            )
            val report = simulateMatch(assembled.setup, matchRng, assembled.homeBench, assembled.awayBench)
            val ratings = report.playerRatings(rules, matchRng)
            val result = Result(home, away, report.homeGoals, report.awayGoals)
            current = current
                .withClub(current.club(home).afterMatch(report, ratings, TeamSide.HOME, date, matchRng))
                .withClub(current.club(away).afterMatch(report, ratings, TeamSide.AWAY, date, matchRng))
            appeared[home] = ratings.home.keys.map { it.value }.toSet()
            appeared[away] = ratings.away.keys.map { it.value }.toSet()
            recorded += match to result
            current = current.copy(played = current.played + PlayedMatch(date, key, match, result))
        }
        var advanced = competition.recorded(recorded)
        for (participant in competition.participants) {
            current = current.withClub(current.club(participant).postRound(appeared[participant] ?: emptySet()))
        }
        if (advanced.finished) {
            current = current.close(advanced, date, rules, fixturesRng)
        }
        current = current.withCompetition(advanced)
    }
    return current.copy(dateIndex = current.dateIndex + 1)
}

@SpecRef("3.1")
fun playSeason(state: SeasonState, rules: RuleSet, tick: WeeklyTick): SeasonState {
    var current = state
    while (!current.finished) current = playRound(current, rules, tick)
    return current
}

@SpecRef("0")
private fun firePendingSundays(state: SeasonState, date: CalendarDate, tick: WeeklyTick): SeasonState {
    var current = state
    var sunday = state.lastTick?.plusDays(DAYS_IN_WEEK) ?: state.schedule.start
    while (sunday <= date) {
        current = tick.apply(current, sunday).copy(lastTick = sunday)
        sunday = sunday.plusDays(DAYS_IN_WEEK)
    }
    return current
}

@SpecRef("3.8")
private fun ClubState.afterMatch(report: MatchReport, ratings: MatchRatings, side: TeamSide, date: CalendarDate, rng: Rng): ClubState {
    var updated = this
    val energies = report.energy(side)
    val rated = ratings.of(side)
    for ((id, energy) in energies) {
        updated = updated.withRecord(id.value) { it.copy(namedSinceTick = true, energy = energy) }
    }
    for ((id, rating) in rated) {
        updated = updated.withRecord(id.value) {
            it.copy(appearances = it.appearances + 1, ratingSum = it.ratingSum + rating.value, ratingCount = it.ratingCount + 1)
        }
    }
    for (event in report.log) {
        if (event.side != side) continue
        when (event) {
            is MatchEvent.Goal -> if (event.type != GoalType.OWN_GOAL && event.scorer != null) {
                updated = updated.withRecord(event.scorer.id.value) { it.copy(goals = it.goals + 1) }
            }
            is MatchEvent.Injury -> {
                if (event.days > 0) {
                    updated = updated.withRecord(event.player.id.value) { it.copy(injuredUntil = date.plusDays(event.days)) }
                }
                if (event.permanentStrengthLoss > 0) {
                    val index = event.player.id.value
                    updated = updated.copy(squad = updated.squad.mapIndexed { i, p -> if (i == index) p.copy(strength = p.strength - event.permanentStrengthLoss) else p })
                }
            }
            else -> Unit
        }
    }
    val discipline = updated.records.mapIndexed { i, r -> PlayerId(i) to r.discipline }.toMap()
        .afterMatch(report.log, side, rng.fork(SEASON_DISCIPLINE_STREAM))
    for ((id, record) in discipline) {
        updated = updated.withRecord(id.value) { it.copy(discipline = record) }
    }
    return updated
}

@SpecRef("3.9")
private fun ClubState.postRound(appeared: Set<Int>): ClubState =
    copy(
        records = records.mapIndexed { index, record ->
            val served = if (record.discipline.suspended && index !in appeared) record.discipline.served() else record.discipline
            val gain = weeklyRecovery(squad[index].age, played = index in appeared, humanManaged = false)
            record.copy(discipline = served, energy = recover(record.energy, gain))
        },
    )

@SpecRef("5.5")
private fun SeasonState.close(competition: Competition, date: CalendarDate, rules: RuleSet, rng: Rng): SeasonState {
    val order = competition.finalOrder(rules, rng)
    var current = copy(closed = closed + CompetitionClose(date, competition.key, competition.kind, order))
    order.take(2).forEachIndexed { place, key ->
        val club = current.club(key)
        val continent = current.dataset.country(club.country)?.continent
            ?: throw IllegalStateException("club $key has no country in the dataset")
        val prize = titlePrestige(competition.kind, champion = place == 0, inLeague = club.inLeague, continent = continent, division = competition.division)
        current = current.withClub(club.copy(prestige = club.prestige.awarded(prize)))
    }
    return current
}

@SpecRef("3.8")
private const val SEASON_DISCIPLINE_STREAM = 0x5EA5L

@SpecRef("0")
private const val DAYS_IN_WEEK = 7
```

Docstrings: `playRound` carries the nine step reading above with its section references and the two INFERIDO points (Sundays before the matches; a club of a competition without a match today recovers as not played); `firePendingSundays` cites section 0's pending day sweep; `afterMatch` cites 3.8, 3.9, 4.5 and 4.10 per field; `postRound` cites 3.1 and 1.10; `close` cites 5.5's crediting at competition close.

- [ ] **Step 5: Run the season tests and the full check**

Run: `./gradlew check`
Expected: PASS (the CLI, validation and existing engine tests still compile against the availability wrapper).

- [ ] **Step 6: Commit**

```
feat(engine): the season state and the round loop of section 3.1

A season is a value: clubs with records, competitions with results, a
schedule and a date cursor. playRound fires the pending Sundays of section
0 into a weekly tick, assembles and simulates every match of the day from
the club states with one availability per side, writes energy, cards,
injuries, appearances, ratings and goals back to the records, runs the
post-round recovery and suspension serving for every club of a competition
that played, and credits the 5.5 prizes the moment a competition closes.
```

---

### Task 9: the turnover between seasons

What section 1.12 and 5.5 do when every competition has closed and no market runs: relegation and promotion across adjacent divisions, the last division's swap with the reserve, the Brazilian fourth division rebuilt from the state champions' queue, prestige decay and promotion, and the next season's state built from the moved clubs. Aging, retirement, youth and coaches are the next plan's; this task leaves `nextSeason` as the function that plan extends.

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/season/Turnover.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/season/TurnoverTest.kt`

**Interfaces:**
- Consumes: `SeasonState`, `ClubState`, `Standing`, `CompetitionClose`, `leagueDivisions`, `movement`, `Prestige`, `openingSeason`'s builders (factor the competition building of `openingSeason` into `internal fun buildCompetitions(number: Int, clubs: List<ClubState>, dataset: WorldDataset, activeLeagues: Set<Int>, seed: Long): List<Competition>` in `SeasonState.kt`, used by both).
- Produces:

```kotlin
@SpecRef("1.12")
data class DivisionSwap(val country: Int, val upper: Int, val relegated: List<String>, val promoted: List<String>)

/** The swaps of one country from the closed season, top boundary first, the reserve last. */
@SpecRef("1.12")
fun divisionSwaps(state: SeasonState, country: Int): List<DivisionSwap>

/** The Brazilian fourth division queue of 1.12 from the state championships' final orders. */
@SpecRef("1.12")
fun stateChampionsQueue(closes: List<CompetitionClose>, stateOf: (String) -> Int?, size: Int): List<String>

/** The next season: standings moved, prestige decayed and promoted, records reset, competitions rebuilt. */
@SpecRef("1.4")
fun nextSeason(state: SeasonState, activeLeagues: Set<Int>, rules: RuleSet): SeasonState
```

Design:

- `divisionSwaps`: divisions of the country from `leagueDivisions(country, clubs, dataset)` (the season's standings), each closed league competition's `finalOrder` from `state.closed` (key `league:$country:$d`). Boundary d/d+1 from the top: `movement(divisionD, orderD, promotedCount = 0)` gives the relegated; `movement(divisionD1, orderD1, promotedCount = divisionD.relegated)` gives the promoted; a `DivisionSwap(country, d, relegated, promoted)`. The last division swaps with the reserve: the country's clubs with `Standing.WithoutDivision`, ordered by level descending then `pyramidTiebreak` then ref (section 1.9's queue); `min(relegated, reserve.size)` go down and the first that many of the reserve come up, `DivisionSwap(country, last, relegated.take(n), reserve.take(n))`.
- Brazil with state championships on: the fourth division, when it exists, does not swap with the third by the boundary rule; the third's relegated go down directly and the fourth is rebuilt from `stateChampionsQueue` with the relegated of the third at its head, sized to the fourth division's club count. `stateChampionsQueue`: tiers `listOf(listOf(25, 18, 10, 22), listOf(17, 23, 4, 15, 5), listOf(8, 1, 13, 11, 9, 19, 14), listOf(24, 0, 16, 12, 2, 6), listOf(7, 26, 20, 3, 21))` by state index (SP, RJ, MG, RS; PR, SC, BA, PE, CE; GO, AL, PA, MS, MA, RN, PB; SE, AC, PI, MT, AM, DF; ES, TO, RO, AP, RR); the closes of kind `STATE` with division 1 give each state's final order; the queue takes place 1 of every state tier by tier, then place 2 of tier 1, place 3 of tier 1, place 2 of tier 2, place 2 of tier 3, place 2 of tier 4, then deepening in the same order (tier 1 one place deeper each pass before the others) until `size` names are gathered or the orders run dry; names already in the queue are skipped. Season one's fourth division stays as the pyramid seated it (a bet, recorded by Task 10).
- `nextSeason`: apply every swap to the standings (`ClubState.standing` set to `InDivision(upper + 1)` for relegated, `InDivision(upper)` for promoted; the reserve swap sets `WithoutDivision` and `InDivision(last)`); prestige `decayed(inLeague).promoted()` for every club (5.5, at the turnover, reading the standing of the season that closed); records reset to `PlayerRecord()` except `injuredUntil` carried over (an injury runs by date), `namedSinceTick` cleared; `number + 1`, `year + 1`, competitions rebuilt with `buildCompetitions(number + 1, ...)` and a new schedule; `played`, `closed` emptied; `dateIndex = 0`; `lastTick = null`. Requires `state.finished`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.openfoot.engine.season

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.WorldFixtures
import org.openfoot.engine.world.generateWorld
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.Position
import org.openfoot.model.RuleSets
import org.openfoot.model.Trait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TurnoverTest {

    private fun squad(club: String) = buildList {
        add(WorldFixtures.player(name = "$club g1", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        add(WorldFixtures.player(name = "$club g2", position = Position.GOALKEEPER, first = Trait.REFLEXES, second = Trait.POSITIONING))
        repeat(3) { add(WorldFixtures.player(name = "$club z$it", position = Position.CENTREBACK, first = Trait.MARKING, second = Trait.TACKLING)) }
        repeat(3) { add(WorldFixtures.player(name = "$club l$it", position = Position.FULLBACK, first = Trait.PACE, second = Trait.CROSSING)) }
        repeat(6) { add(WorldFixtures.player(name = "$club m$it", position = Position.MIDFIELDER, first = Trait.PASSING, second = Trait.PLAYMAKING)) }
        repeat(4) { add(WorldFixtures.player(name = "$club a$it", position = Position.FORWARD, first = Trait.FINISHING, second = Trait.HEADING)) }
    }

    /** Twenty two clubs: ten in the first division, ten in the second, two in the reserve. */
    private val data = WorldFixtures.dataset(
        clubs = (1..22).map { WorldFixtures.club(ref = "c${it.toString().padStart(2, '0')}", level = maxOf(6, 21 - it / 2), squad = squad("c$it")) },
    ).copy(
        leagues = listOf(
            LeagueConfigEntry(country = Country.BRAZIL, division = 1, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true),
            LeagueConfigEntry(country = Country.BRAZIL, division = 2, teamCount = 10, relegated = 2, turns = 1, penaltiesTiebreak = true),
        ),
    )

    private fun closedSeason(seed: Long): SeasonState {
        val opening = openingSeason(generateWorld(data, seed, setOf(Country.BRAZIL)), data, setOf(Country.BRAZIL), 2026, seed)
        return playSeason(opening, RuleSets.CLASSIC, WeeklyTick.NONE)
    }

    @Test
    fun `swaps read the closed tables, two boundaries and the reserve`() {
        val end = closedSeason(1)
        val swaps = divisionSwaps(end, Country.BRAZIL)
        assertEquals(listOf(1, 2), swaps.map { it.upper })
        val first = end.closed.single { it.key == "league:29:1" }.finalOrder
        val second = end.closed.single { it.key == "league:29:2" }.finalOrder
        assertEquals(first.takeLast(2), swaps[0].relegated)
        assertEquals(second.take(2), swaps[0].promoted)
        assertEquals(second.takeLast(2), swaps[1].relegated)
        assertEquals(2, swaps[1].promoted.size)
        assertTrue(swaps[1].promoted.all { end.club(it).standing is Standing.WithoutDivision })
    }

    @Test
    fun `the next season moves the clubs, decays prestige and starts clean`() {
        val end = closedSeason(2)
        val next = nextSeason(end, setOf(Country.BRAZIL), RuleSets.CLASSIC)
        val swaps = divisionSwaps(end, Country.BRAZIL)
        assertEquals(2, next.number)
        assertEquals(2027, next.year)
        swaps[0].relegated.forEach { assertEquals(Standing.InDivision(2), next.club(it).standing) }
        swaps[0].promoted.forEach { assertEquals(Standing.InDivision(1), next.club(it).standing) }
        swaps[1].promoted.forEach { assertEquals(Standing.InDivision(2), next.club(it).standing) }
        swaps[1].relegated.forEach { assertEquals(Standing.WithoutDivision, next.club(it).standing) }
        val champion = end.closed.single { it.key == "league:29:1" }.finalOrder[0]
        assertEquals(end.club(champion).prestige.decayed(inLeague = true).promoted(), next.club(champion).prestige)
        assertTrue(next.clubs.values.all { club -> club.records.all { it.appearances == 0 && it.goals == 0 && it.energy == 100 } })
        assertTrue(next.played.isEmpty() && next.closed.isEmpty() && next.dateIndex == 0)
        assertEquals(10, next.competitions.getValue("league:29:1").participants.size)
        assertTrue(next.competitions.getValue("league:29:1").participants.containsAll(swaps[0].promoted))
    }

    @Test
    fun `the state champions queue walks the tiers place by place`() {
        val closes = listOf(25, 18, 17, 8, 24, 7).map { state ->
            CompetitionClose(CalendarDate(2026, 4, 5), "state:$state:1", CompetitionKind.STATE, listOf("$state-1", "$state-2", "$state-3"))
        }
        val stateOf: (String) -> Int? = { it.substringBefore('-').toInt() }
        val queue = stateChampionsQueue(closes, stateOf, size = 10)
        assertEquals(listOf("25-1", "18-1", "17-1", "8-1", "24-1", "7-1", "25-2", "18-2", "25-3", "18-3"), queue)
        assertEquals(18, stateChampionsQueue(closes, stateOf, size = 40).size)
    }
}
```

The queue test needs `stateOf` only because `CompetitionClose` names clubs, not states; `nextSeason` derives it from `club.entry.state`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.season.TurnoverTest"`
Expected: compilation failure.

- [ ] **Step 3: Factor the competition builder**

In `SeasonState.kt`, move the body of `openingSeason` that builds the competitions into:

```kotlin
@SpecRef("1.10")
internal fun buildCompetitions(number: Int, clubs: List<ClubState>, dataset: WorldDataset, activeLeagues: Set<Int>, seed: Long): List<Competition> {
    val root = SplitMix64Rng(seed).fork(SeedDomain.SEASON).fork(number.toLong()).fork(SeedDomain.FIXTURES)
    val worldRng = SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)
    val competitions = ArrayList<Competition>()
    for (country in activeLeagues.sorted()) {
        leagueDivisions(country, clubs, dataset).forEach { division ->
            competitions += leagueCompetition(division, root.fork(clubKey("league:$country:${division.division}")))
        }
        nationalCup(country, clubs, root.fork(clubKey("cup:$country")))?.let { competitions += it }
    }
    stateSetup(clubs, dataset) { ref -> pyramidTiebreak(worldRng, ref) }.divisions.forEach { division ->
        competitions += stateCompetition(division, root.fork(clubKey("state:${division.state}:${division.division}")))
    }
    return competitions
}
```

and have `openingSeason` call it with `number = 1`.

- [ ] **Step 4: Implement `Turnover.kt`**

```kotlin
package org.openfoot.engine.season

import org.openfoot.engine.world.Standing
import org.openfoot.engine.world.pyramidTiebreak
import org.openfoot.model.CompetitionKind
import org.openfoot.model.Country
import org.openfoot.model.RuleSet
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng

@SpecRef("1.12")
data class DivisionSwap(val country: Int, val upper: Int, val relegated: List<String>, val promoted: List<String>)

@SpecRef("1.12")
fun divisionSwaps(state: SeasonState, country: Int): List<DivisionSwap> {
    val clubs = state.clubs.values.toList()
    val divisions = leagueDivisions(country, clubs, state.dataset)
    fun order(division: Int): List<String> =
        state.closed.firstOrNull { it.key == "league:$country:$division" }?.finalOrder
            ?: throw IllegalStateException("division $division of $country has not closed")

    val swaps = ArrayList<DivisionSwap>()
    for ((upper, lower) in divisions.zipWithNext()) {
        val down = movement(upper, order(upper.division), promotedCount = 0).relegated
        val up = movement(lower, order(lower.division), promotedCount = upper.relegated).promoted
        swaps += DivisionSwap(country, upper.division, down, up)
    }
    val last = divisions.last()
    val worldRng = SplitMix64Rng(state.seed).fork(SeedDomain.WORLDGEN)
    val reserve = clubs
        .filter { it.country == country && it.standing == Standing.WithoutDivision }
        .sortedWith(compareByDescending<ClubState> { it.club.entry.level }.thenBy { pyramidTiebreak(worldRng, it.key) }.thenBy { it.key })
        .map { it.key }
    val down = movement(last, order(last.division), promotedCount = 0).relegated
    val count = minOf(down.size, reserve.size)
    swaps += DivisionSwap(country, last.division, down.take(count), reserve.take(count))
    return swaps
}

@SpecRef("1.12")
fun stateChampionsQueue(closes: List<CompetitionClose>, stateOf: (String) -> Int?, size: Int): List<String> {
    val orders = closes
        .filter { it.kind == CompetitionKind.STATE && it.key.endsWith(":1") }
        .associate { close -> (stateOf(close.finalOrder.first()) ?: -1) to close.finalOrder }
    val queue = ArrayList<String>()
    fun take(tier: List<Int>, place: Int) {
        for (state in tier) {
            val name = orders[state]?.getOrNull(place) ?: continue
            if (name !in queue && queue.size < size) queue += name
        }
    }
    STATE_TIERS.forEach { take(it, 0) }
    var depth = 1
    while (queue.size < size && depth < MAX_QUEUE_DEPTH) {
        take(STATE_TIERS[0], depth)
        take(STATE_TIERS[0], depth + 1)
        STATE_TIERS.drop(1).forEach { take(it, depth) }
        depth++
    }
    return queue
}

@SpecRef("1.4")
fun nextSeason(state: SeasonState, activeLeagues: Set<Int>, rules: RuleSet): SeasonState {
    require(state.finished) { "season ${state.number} has not finished" }
    var clubs = state.clubs.toMutableMap()
    for (country in activeLeagues.sorted()) {
        val divisions = leagueDivisions(country, clubs.values.toList(), state.dataset)
        if (divisions.isEmpty()) continue
        for (swap in divisionSwaps(state, country)) {
            val isReserve = swap.upper == divisions.last().division
            swap.relegated.forEach { key ->
                clubs[key] = clubs.getValue(key).copy(standing = if (isReserve) Standing.WithoutDivision else Standing.InDivision(swap.upper + 1))
            }
            swap.promoted.forEach { key -> clubs[key] = clubs.getValue(key).copy(standing = Standing.InDivision(swap.upper)) }
        }
    }
    val moved = clubs.values.map { club ->
        club.copy(
            prestige = state.club(club.key).let { it.prestige.decayed(it.inLeague).promoted() },
            records = club.records.map { PlayerRecord(injuredUntil = it.injuredUntil) },
        )
    }
    val number = state.number + 1
    val competitions = buildCompetitions(number, moved, state.dataset, activeLeagues, state.seed)
    return SeasonState(
        number = number,
        year = state.year + 1,
        seed = state.seed,
        dataset = state.dataset,
        clubs = moved.associateBy { it.key },
        competitions = competitions.associateBy { it.key },
        schedule = SeasonSchedule.build(state.year + 1, competitions),
        played = emptyList(),
        closed = emptyList(),
        dateIndex = 0,
        lastTick = null,
    )
}

/** The five priority tiers of Brazilian states of 1.12, by state index of FORMAT-SPEC. */
@SpecRef("1.12")
private val STATE_TIERS: List<List<Int>> = listOf(
    listOf(25, 18, 10, 22),
    listOf(17, 23, 4, 15, 5),
    listOf(8, 1, 13, 11, 9, 19, 14),
    listOf(24, 0, 16, 12, 2, 6),
    listOf(7, 26, 20, 3, 21),
)

@SpecRef("1.12")
private const val MAX_QUEUE_DEPTH = 20
```

The Brazilian fourth division rebuild from the queue is wired in `nextSeason` only when Brazil has a fourth division and `dataset.options.playStateChampionships`: replace the boundary swap 3/4 with the third division's relegated going directly to the fourth and the fourth's membership set to `relegatedOfThird + stateChampionsQueue(state.closed, { key -> state.club(key).club.entry.state }, size = fourth.clubs.size - relegatedOfThird.size)`, every other current member of the fourth becoming `WithoutDivision`. Write that as a private `rebuildBrazilianFourth` called before the generic loop for Brazil, guarded so the generic loop skips the 3/4 boundary when it ran. Tests for it are the golden vector of Task 10 (the fixture has no fourth division), so add one unit test to `TurnoverTest` with a hand built `SeasonState` only if you can do it in under thirty lines; otherwise record the gap in the commit message. The `rules` parameter is unused in this plan and exists for the next plan's aging and retirement; suppress the warning with `@Suppress("UNUSED_PARAMETER")` and say why in the docstring.

- [ ] **Step 5: Run the tests and the full check**

Run: `./gradlew check`
Expected: PASS.

- [ ] **Step 6: Commit**

```
feat(engine): the turnover between seasons, swaps, reserve and prestige

Relegation and promotion across adjacent divisions from the closed tables,
the last division's swap with the country's reserve queue, the Brazilian
fourth division rebuilt from the state champions' queue of 1.12, prestige
decayed and promoted at the turnover per 5.5, and the next season built
from the moved clubs with fresh records and a new schedule.
```

---

### Task 10: the season command, its golden vector and the declared bets

**Files:**
- Create: `cli/src/main/kotlin/org/openfoot/cli/SeasonPrinter.kt`
- Modify: `cli/src/main/kotlin/org/openfoot/cli/Main.kt` (subcommand `season`, USAGE)
- Create: `cli/src/test/kotlin/org/openfoot/cli/SeasonGoldenVectorTest.kt`
- Modify: `cli/src/test/kotlin/org/openfoot/cli/CliDispatchTest.kt`
- Modify: `spec/OPEN-QUESTIONS.md` (new heading and items 110 to 118)
- Modify: `docs/known-quirks.md` ("Ainda não implementados": the Copa Nacional novo formato no-shootout first round)

**Interfaces:**
- Consumes: `openingSeason`, `playSeason`, `nextSeason`, `SeasonState`, `CompetitionClose`, `Phase.League`, `standings`, `resolveCountry`, `parseLeagues`, `loadDataset`, `GoldenWorld`.
- Produces: `internal fun describeSeason(state: SeasonState): String`; `openfoot-cli season --dataset <path> --seed <number> [--seasons <n>] [--leagues BRA,ESP|all]`.

`describeSeason` prints, deterministically:

```
season    1  year 2026  rounds 24  matches 59
  league:29:1  NATIONAL_LEAGUE  champion clube-01  runner-up clube-02
    pos  club       pts  pld  w  d  l  gf  ga
      1  clube-01    ...
  cup:29  NATIONAL_CUP  champion ...  runner-up ...
    final order: a, b, c, ...
  top scorers
    12  clube-01 jogador 2  clube-01
```

with tables for every league phase (final overall table via `standings`), the final order for knockout competitions, and the five top scorers across all clubs (goals descending, then name), each line `goals name club`.

The CLI runs `--seasons` seasons (default 1), printing each, calling `nextSeason` between them with the same active leagues. The golden vector plays one season of `GoldenWorld.dataset` at seed 42 with `setOf(GoldenWorld.fixCountry.index)` (a division of ten, a cup of eight, no states, players of two midfielders per club, so lineups are short but legal) and pins the printed text exactly, with a docstring that checks what can be checked by hand: 180 league matches plus 14 cup matches (a division of ten plays four turns by section 1.3), every club of the division with 36 league matches played, points arithmetic consistent with wins and draws, the champion at the top of the table.

`OPEN-QUESTIONS.md` gains a heading `## Implementação da v0.3 - apostas declaradas na fase 2` with items 110 to 118, each with a `**Resolução (INFERIDO):**` paragraph in Portuguese: 110 the schedule policy (states twice a week from January and done before week 12, leagues Sundays from week 12, cup alternate Wednesdays from week 12; restates 74); 111 cross group fixtures by the circle with same group pairs dropped (restates 75); 112 a knockout field that is not 2, 4 or 8 seeded strong against weak, i against n-1-i; 113 the Brazilian fourth division of season one seated by level, rebuilt from the state queue from season two; 114 pending Sundays fire before the day's matches; 115 the two legged final phase of a grouped national league; 116 records reset at the turnover, injuries carried by date; 117 a club of a competition without a match on a round day recovers as not played; 118 deferred to a later plan and listed: the Serie C sentinel format, the sixty eight club preliminary, the promotion and relegation playoffs of the .cfg, the Copa Nacional novo formato, the Sao Paulo real groups option.

- [ ] **Step 1: Write the failing tests**

`SeasonGoldenVectorTest.kt` starts with an empty expected string and the docstring above; run once, copy the printed text in after checking it by hand, exactly as `CallUpGoldenVectorTest` did. Add to `CliDispatchTest`: `assertEquals(1, dispatch(arrayOf("season")))` and `assertEquals(1, dispatch(arrayOf("season", "--dataset", "x.json")))`.

- [ ] **Step 2: Implement `SeasonPrinter.kt` and the subcommand**

`describeSeason` as specified; in `Main.kt` add `"season" -> { season(args.drop(1)); 0 }`, the USAGE line, and:

```kotlin
private fun season(args: List<String>) {
    val options = parseOptions(args)
    val path = options["--dataset"] ?: fail("season needs --dataset <path>")
    val seedText = options["--seed"] ?: fail("season needs --seed <number>")
    val seed = seedText.toLongOrNull() ?: fail("seed '$seedText' is not a number")
    val seasons = options["--seasons"]?.let { it.toIntOrNull() ?: fail("seasons '$it' is not a number") } ?: 1
    val dataset = loadDataset(path)
    val activeLeagues = parseLeagues(options["--leagues"], dataset)
    val world = generateWorld(dataset, seed, activeLeagues)
    var state = openingSeason(world, dataset, activeLeagues, SEASON_ONE_YEAR, seed)
    repeat(seasons) { index ->
        if (index > 0) state = nextSeason(state, activeLeagues, RuleSets.CLASSIC)
        state = playSeason(state, RuleSets.CLASSIC, WeeklyTick.NONE)
        print(describeSeason(state))
    }
}

/** The calendar year of season one, a fixed choice since no career date exists yet to derive it from. */
@SpecRef("0")
private const val SEASON_ONE_YEAR = 2026
```

- [ ] **Step 3: Pin the golden vector, write the open questions and the quirk entry, run the check**

Run: `./gradlew check` then `./gradlew checkDocumentStyle`.
Expected: PASS.

- [ ] **Step 4: Acceptance against the real install (local only, nothing committed)**

With JDK 21 on the PATH: `./gradlew :cli:installDist`, then `openfoot-cli import --install C:\Brasfoot22-23 --out %TEMP%\base.json` and `openfoot-cli season --dataset %TEMP%\base.json --seed 42 --seasons 2`. Expected: two seasons print without error; the Brazilian first division has twenty clubs and thirty eight rounds; every state championship of an eligible state closes; the second season's first division contains the promoted clubs of the first. Report anything odd in the commit message of the next step rather than fixing silently.

- [ ] **Step 5: Commit**

```
feat(cli): the season subcommand, with a pinned season of the golden fixture

season plays one or more seasons of a dataset and prints each competition's
final table or order, the champions and the top scorers, in an order that
diffs identically between runs. One season of the golden fixture is pinned
exactly. Items 110 to 118 record the bets the season core takes where the
original leaves the calendar and the formats open.
```
