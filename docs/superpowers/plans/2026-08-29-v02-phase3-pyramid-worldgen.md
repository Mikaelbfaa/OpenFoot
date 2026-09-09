# v0.2 Phase 3 (Pyramid, Country Table, Schema v2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the CONFIRMADO league-pyramid generator of spec section 1.9, the corrected reputation path of 4.4, the embedded country table of 4.4.1, and move division assignment from import time to world-generation time, landing dataset schema version 2.

**Architecture:** Additive first, breaking last. Tasks 1-6 add the new machinery while the deprecated schema fields still exist (every commit compiles and passes `./gradlew check`); Task 7 is the single breaking commit that removes `ClubEntry.division`/`nationalTeam`, makes `continent` required and bumps the schema to version 2; Tasks 8-9 add world-level validation and run the real-installation acceptance check. The pyramid generator lives in `:engine` (it builds worlds); the 224-country table lives in `:importer` (it fills datasets).

**Tech Stack:** Kotlin/JVM 21, Gradle wrapper, kotlinx-serialization, kotlin.test. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-29-v02-importador-geracao-de-mundo-design.md` (including the "Adendo (2026-08-29, pos-varredura)" section, which is the binding revision) arguing from `spec/SIMULATION-SPEC.md` sections 1.9, 4.4, 4.4.1 and `spec/FORMAT-SPEC.md` ("Semantica dos .cfg").

## Global Constraints

- Work on branch `feat/v0-2-pyramid`, created from `main`.
- Commits: Conventional Commits, atomic, `./gradlew check` green before each; the commit message is ONLY the subject line given per task - NO Co-Authored-By trailer or any other trailer; commits are authored solely by the repo's configured user.
- Comments: docstrings only, plain ASCII, no markdown inside comments (`checkCommentStyle` enforces).
- Every constant from the spec carries `@SpecRef("<section>")`.
- `:model`, `:dataset`, `:engine` stay pure: no I/O, clock, platform randomness, HashMap iteration, transcendental math; never branch on rule-set identity.
- Clean-room: code is written only from `spec/`, the design doc, and this plan. NEVER read decompiled output. Reading `spec/SIMULATION-SPEC.md` and running the importer over the local game install (Task 9 only) are both permitted.
- Single test class: `./gradlew :engine:test --tests "org.openfoot.engine.world.PyramidTest"` (adjust module/class).
- Known consequence, accepted by design: v2 datasets and their worlds are NOT seed-compatible with v0.1 output.

---

### Task 1: dataset carries league configurations

The pyramid generator consults configuration records before its built-in defaults. Those records come from the installation's `.cfg` files (or a hand-written dataset) and belong in the dataset schema. Additive: nothing consumes them yet.

**Files:**
- Modify: `dataset/src/main/kotlin/org/openfoot/dataset/WorldDataset.kt` (add `LeagueConfigEntry`; add `leagues` field to `WorldDataset`)
- Test: `dataset/src/test/kotlin/org/openfoot/dataset/WorldDatasetTest.kt` (add tests)

**Interfaces:**
- Consumes: nothing.
- Produces: `@Serializable data class LeagueConfigEntry(country: Int, division: Int, teamCount: Int, relegated: Int, turns: Int, penaltiesTiebreak: Boolean)` and `WorldDataset.leagues: List<LeagueConfigEntry>` (default `emptyList()`). Tasks 3 and 5 use both.

- [ ] **Step 1: Write the failing tests**

Add to `WorldDatasetTest.kt` (reuse the file's existing helper style for building a minimal dataset; the two tests only need the new type):

```kotlin
    @Test
    fun `a league configuration entry validates its ranges`() {
        val entry = LeagueConfigEntry(
            country = Country.BRAZIL,
            division = 1,
            teamCount = 20,
            relegated = 4,
            turns = 2,
            penaltiesTiebreak = false,
        )
        assertEquals(20, entry.teamCount)
        assertFailsWith<IllegalArgumentException> { entry.copy(division = 0) }
        assertFailsWith<IllegalArgumentException> { entry.copy(division = 5) }
        assertFailsWith<IllegalArgumentException> { entry.copy(teamCount = 0) }
        assertFailsWith<IllegalArgumentException> { entry.copy(relegated = 21) }
        assertFailsWith<IllegalArgumentException> { entry.copy(turns = 0) }
        assertFailsWith<IllegalArgumentException> { entry.copy(turns = 5) }
    }

    @Test
    fun `league configurations survive a round trip and default to none`() {
        val dataset = decode(encode(sample().copy(leagues = listOf(sampleLeague()))))
        assertEquals(listOf(sampleLeague()), dataset.leagues)
        assertEquals(emptyList(), sample().leagues)
    }
```

If `WorldDatasetTest.kt` has no `sample()`/`encode()`/`decode()` helpers under those names, adapt the two tests to the file's existing round-trip idiom (there are existing decode round-trip tests near lines 70 and 110 to copy from) and add a private `sampleLeague()` returning the entry literal above. Import `org.openfoot.model.Country` if not present.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :dataset:test --tests "org.openfoot.dataset.WorldDatasetTest"`
Expected: compilation FAILURE, `LeagueConfigEntry` unresolved.

- [ ] **Step 3: Implement**

In `WorldDataset.kt`, add `leagues` to the `WorldDataset` constructor after `clubs`:

```kotlin
    val leagues: List<LeagueConfigEntry> = emptyList(),
```

and add the entity at the end of the file:

```kotlin
/**
 * One configured tier of a national league.
 *
 * The pyramid generator of section 1.9 consults these before its built in
 * defaults, first match by country and division winning, which is why entry
 * order is preserved and duplicates are legal rather than rejected: a later
 * entry for the same pair is simply never reached, exactly as in the
 * original's concatenated configuration list.
 */
@Serializable
data class LeagueConfigEntry(
    @property:SpecRef("1.9") val country: Int,
    @property:SpecRef("1.9") val division: Int,
    @property:SpecRef("1.9") val teamCount: Int,
    @property:SpecRef("1.9") val relegated: Int,
    @property:SpecRef("1.3") val turns: Int,
    @property:SpecRef("FORMAT-SPEC, desempate") val penaltiesTiebreak: Boolean,
) {
    init {
        require(country >= 0) { "league configuration for negative country $country" }
        require(division in 1..MAX_DIVISION) {
            "league division $division, and section 1.9 builds at most $MAX_DIVISION"
        }
        require(teamCount > 0) { "a league tier of $teamCount teams" }
        require(relegated in 0..teamCount) {
            "$relegated relegated from a tier of $teamCount teams"
        }
        require(turns in 1..MAX_TURNS) { "a league of $turns turns, section 1.3 knows 1 to $MAX_TURNS" }
    }

    companion object {
        @SpecRef("1.9")
        const val MAX_DIVISION = 4

        @SpecRef("1.3")
        const val MAX_TURNS = 4
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :dataset:test --tests "org.openfoot.dataset.WorldDatasetTest"`
Expected: PASS.

- [ ] **Step 5: Full check, then commit**

Run: `./gradlew check` (expect green), then:

```bash
git add dataset/src/main/kotlin/org/openfoot/dataset/WorldDataset.kt dataset/src/test/kotlin/org/openfoot/dataset/WorldDatasetTest.kt
git commit -m "feat(dataset): configuracoes de liga entram no esquema"
```

---

### Task 2: ClubBands grows the corrected reputation path

Spec item 18's INFERIDO was wrong: the reputation path covers national teams AND every club of a country whose league is not played; reputation 0 gives base 1 and range 1 (not 5 and 1). Rename the parameter, correct the table, keep the call in `SquadGeneration.kt:36` compiling unchanged (same position, boolean, until Task 4 rewires it).

**Files:**
- Modify: `engine/src/main/kotlin/org/openfoot/engine/world/ClubBands.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/world/ClubBandsTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `ClubBands.bands(division: Int?, reputation: Int, reputationPath: Boolean = false): GenerationBands`. Task 4 calls it with `reputationPath = true` for clubs standing on the reputation path.

- [ ] **Step 1: Update the tests first**

In `ClubBandsTest.kt`:
- The test `the three lowest reputations are indistinguishable` (line 84) pinned the wrong bet. Replace it with:

```kotlin
    @Test
    fun `reputations one to three are indistinguishable and zero is weaker`() {
        val low = ClubBands.bands(division = null, reputation = 1, reputationPath = true)
        assertEquals(low, ClubBands.bands(division = null, reputation = 2, reputationPath = true))
        assertEquals(low, ClubBands.bands(division = null, reputation = 3, reputationPath = true))
        assertEquals(GenerationBands(strengthBase = 5, abilityBand = 1), low)
        assertEquals(
            GenerationBands(strengthBase = 1, abilityBand = 1),
            ClubBands.bands(division = null, reputation = 0, reputationPath = true),
        )
    }
```

- In the tests `reputation decides the bands only for a national team` (line 78) and `a club in a league ignores its reputation` (line 92), rename any `nationalTeam = ` named argument to `reputationPath = ` and update the first one's name to `` `reputation decides the bands on the reputation path` `` (its body otherwise stands: rep 5 gives 22/7, rep 4 gives 15/4).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.world.ClubBandsTest"`
Expected: compilation FAILURE (`reputationPath` unresolved) or assertion FAILURE on the zero row.

- [ ] **Step 3: Implement**

In `ClubBands.kt`, replace the `bands` function, its docstring, and `nationalTeamBands` with:

```kotlin
    /**
     * The bands for a squad: by reputation on the reputation path, by
     * division for a club of a country whose league is played.
     *
     * The reputation path covers national teams and every club of a country
     * whose league is not played in this world, per section 1.9. That is why
     * a Bayern of a world where Germany's league is off still generates a
     * strong squad: it stands on reputation five, not on the divisionless
     * row. Reputation zero lands on the weakest pair; OPEN-QUESTIONS item 18
     * records that the earlier reading of this row was wrong.
     */
    @SpecRef("4.4")
    fun bands(division: Int?, reputation: Int, reputationPath: Boolean = false): GenerationBands =
        if (reputationPath) reputationBands(reputation) else leagueBands(division)
```

```kotlin
    @SpecRef("4.4")
    private fun reputationBands(reputation: Int): GenerationBands = when (reputation) {
        5 -> GenerationBands(strengthBase = 22, abilityBand = 7)
        4 -> GenerationBands(strengthBase = 15, abilityBand = 4)
        3, 2, 1 -> GenerationBands(strengthBase = 5, abilityBand = 1)
        else -> GenerationBands(strengthBase = 1, abilityBand = 1)
    }
```

Leave `leagueBands` untouched. `SquadGeneration.kt:36` passes its third argument positionally, so it still compiles; its semantics are corrected in Task 4.

- [ ] **Step 4: Run to verify pass, full check, commit**

Run: `./gradlew :engine:test --tests "org.openfoot.engine.world.ClubBandsTest"` then `./gradlew check`.

```bash
git add engine/src/main/kotlin/org/openfoot/engine/world/ClubBands.kt engine/src/test/kotlin/org/openfoot/engine/world/ClubBandsTest.kt
git commit -m "fix(engine): o caminho de reputacao cobre pais sem liga e reputacao zero da 1/1"
```

---

### Task 3: the pyramid generator (spec 1.9) as pure functions

**Files:**
- Create: `engine/src/main/kotlin/org/openfoot/engine/world/Pyramid.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/world/PyramidTest.kt`

**Interfaces:**
- Consumes: `WorldDataset`, `LeagueConfigEntry` (Task 1), `Rng`/`clubKey` (existing).
- Produces:

```kotlin
sealed interface Standing {
    data class InDivision(val division: Int) : Standing
    data object WithoutDivision : Standing
    data object ByReputation : Standing
}
fun assemblePyramids(dataset: WorldDataset, activeLeagues: Set<Int>, worldRng: Rng): Map<String, Standing>
```

The map is keyed by club ref, iteration-ordered (LinkedHashMap); a ref ABSENT from the map is a club that does not exist in this world (beyond the top 15 of a non-active country). Task 4 consumes exactly this contract.

- [ ] **Step 1: Write the failing tests**

Create `PyramidTest.kt`. Build minimal datasets inline; a helper keeps them short:

```kotlin
package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PyramidTest {

    private fun club(ref: String, country: Int, level: Int) = ClubEntry(
        ref = ref,
        name = ref,
        country = country,
        level = level,
        reputation = 3,
        stadium = "st",
        capacity = 1000,
        coach = "c",
        coachCountry = country,
        squad = emptyList(),
    )

    private fun dataset(clubs: List<ClubEntry>, leagues: List<LeagueConfigEntry> = emptyList()) =
        WorldDataset(
            countries = clubs.map { it.country }.distinct().map {
                CountryEntry(index = it, name = "pais $it", level = 15, continent = 1)
            },
            clubs = clubs,
            leagues = leagues,
        )

    private fun rng() = SplitMix64Rng(42L).fork(org.openfoot.model.SeedDomain.WORLDGEN)

    @Test
    fun `an active country of twenty two clubs gets one division of twenty and two leftovers`() {
        val clubs = (1..22).map { club("c$it", country = 50, level = 20 - it % 10) }
        val standings = assemblePyramids(dataset(clubs), activeLeagues = setOf(50), rng())
        assertEquals(22, standings.size)
        assertEquals(20, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(2, standings.values.count { it == Standing.WithoutDivision })
    }

    @Test
    fun `a configuration that fits overrides the default step`() {
        val clubs = (1..22).map { club("c$it", country = 50, level = 20 - it % 10) }
        val config = LeagueConfigEntry(
            country = 50, division = 1, teamCount = 12,
            relegated = 2, turns = 3, penaltiesTiebreak = false,
        )
        val standings = assemblePyramids(dataset(clubs, listOf(config)), setOf(50), rng())
        assertEquals(12, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(10, standings.values.count { it == Standing.InDivision(2) })
    }

    @Test
    fun `a configuration too large for the remaining clubs falls back to the default`() {
        val clubs = (1..15).map { club("c$it", country = 50, level = 10) }
        val config = LeagueConfigEntry(
            country = 50, division = 1, teamCount = 20,
            relegated = 4, turns = 2, penaltiesTiebreak = false,
        )
        val standings = assemblePyramids(dataset(clubs, listOf(config)), setOf(50), rng())
        assertEquals(14, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(1, standings.values.count { it == Standing.WithoutDivision })
    }

    @Test
    fun `below ten clubs no further division is created`() {
        val clubs = (1..29).map { club("c$it", country = 50, level = 10) }
        val standings = assemblePyramids(dataset(clubs), setOf(50), rng())
        assertEquals(20, standings.values.count { it == Standing.InDivision(1) })
        assertEquals(9, standings.values.count { it == Standing.WithoutDivision })
    }

    @Test
    fun `a country that is not active keeps its top fifteen on the reputation path`() {
        val clubs = (1..20).map { club("c$it", country = 60, level = it) }
        val standings = assemblePyramids(dataset(clubs), activeLeagues = emptySet(), rng())
        assertEquals(15, standings.size)
        assertTrue(standings.values.all { it == Standing.ByReputation })
        assertTrue(standings.containsKey("c20"))
        assertFalse(standings.containsKey("c1"))
    }

    @Test
    fun `a country below the candidate threshold is never a league even when asked`() {
        val clubs = (1..9).map { club("c$it", country = 60, level = 10) }
        val standings = assemblePyramids(dataset(clubs), activeLeagues = setOf(60), rng())
        assertEquals(9, standings.size)
        assertTrue(standings.values.all { it == Standing.ByReputation })
    }

    @Test
    fun `the five big countries need sixteen clubs to be a league`() {
        val germany = 3
        val clubs = (1..15).map { club("c$it", country = germany, level = 10) }
        val standings = assemblePyramids(dataset(clubs), setOf(germany), rng())
        assertTrue(standings.values.all { it == Standing.ByReputation })

        val sixteen = (1..16).map { club("d$it", country = germany, level = 10) }
        val active = assemblePyramids(dataset(sixteen), setOf(germany), rng())
        assertEquals(16, active.values.count { it == Standing.InDivision(1) })
    }

    @Test
    fun `the tie break is drawn from the seed and does not depend on dataset order`() {
        val clubs = (1..25).map { club("c$it", country = 50, level = 10) }
        val forward = assemblePyramids(dataset(clubs), setOf(50), rng())
        val backward = assemblePyramids(dataset(clubs.reversed()), setOf(50), rng())
        assertEquals(forward, backward)
    }

    @Test
    fun `a different seed can order equal levels differently`() {
        val clubs = (1..25).map { club("c$it", country = 50, level = 10) }
        val a = assemblePyramids(dataset(clubs), setOf(50), rng())
        val b = assemblePyramids(
            dataset(clubs), setOf(50),
            SplitMix64Rng(43L).fork(org.openfoot.model.SeedDomain.WORLDGEN),
        )
        assertTrue(a != b, "25 equal-level clubs ordering identically across seeds is wrong")
    }
}
```

Note for the implementer: `ClubEntry` still carries `division`/`nationalTeam` defaults at this point in history, so the `club(...)` helper compiles without naming them. If `CountryEntry.continent` has a default it may be omitted; passing it explicitly as above is future-proof for Task 7.

- [ ] **Step 2: Run to verify compilation failure** (`Standing`, `assemblePyramids` unresolved).

- [ ] **Step 3: Implement `Pyramid.kt`**

```kotlin
package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Rng
import org.openfoot.model.SpecRef

/**
 * Where a club stands when its world is generated.
 *
 * A division of an active league, the leftover row of an active country
 * whose pyramid could not hold it, or the reputation path that section 1.9
 * gives to every club of a country whose league is not played. National
 * teams, when they exist, will stand on the reputation path too.
 */
@SpecRef("1.9")
sealed interface Standing {
    data class InDivision(val division: Int) : Standing
    data object WithoutDivision : Standing
    data object ByReputation : Standing
}

/**
 * Builds every active country's pyramid and decides which clubs exist.
 *
 * The original assembles this once per world: candidates are countries with
 * enough team files, the player picks which candidate leagues are played,
 * and clubs are ranked by level with a random tie break drawn per club. Here
 * the tie break is derived from the seed through the club's reference, in
 * the pattern OPEN-QUESTIONS item 10 established: the mechanism of the
 * original, made reproducible. Deriving it from the reference rather than
 * from list position keeps a dataset reorder from changing any pyramid.
 *
 * A ref absent from the returned map is a club that does not exist in this
 * world: section 1.9 instantiates only the top fifteen clubs of a country
 * whose league is not played.
 */
@SpecRef("1.9")
fun assemblePyramids(
    dataset: WorldDataset,
    activeLeagues: Set<Int>,
    worldRng: Rng,
): Map<String, Standing> {
    val standings = LinkedHashMap<String, Standing>()

    for ((country, clubs) in dataset.clubs.groupBy { it.country }) {
        val ranked = clubs.sortedWith(
            compareByDescending<ClubEntry> { it.level }
                .thenBy { tiebreak(worldRng, it.ref) }
                .thenBy { it.ref },
        )
        if (country in activeLeagues && ranked.size >= candidateThreshold(country)) {
            assignDivisions(country, ranked, dataset, standings)
        } else {
            ranked.take(TOP_CLUBS_WITHOUT_LEAGUE).forEach {
                standings[it.ref] = Standing.ByReputation
            }
        }
    }
    return standings
}

/**
 * The per club tie break of section 1.9, drawn from the club's own stream
 * family so it never disturbs the squad draws: forking consumes nothing.
 */
@SpecRef("1.9")
private fun tiebreak(worldRng: Rng, ref: String): Int =
    worldRng.fork(clubKey(ref)).fork(PYRAMID_TIEBREAK_STREAM).nextInt(TIEBREAK_BOUND)

@SpecRef("1.9")
private fun assignDivisions(
    country: Int,
    ranked: List<ClubEntry>,
    dataset: WorldDataset,
    standings: LinkedHashMap<String, Standing>,
) {
    var index = 0
    for (division in 1..MAX_DIVISIONS) {
        val remaining = ranked.size - index
        val configured = dataset.leagues.firstOrNull {
            it.country == country && it.division == division
        }
        val size = when {
            configured != null && configured.teamCount <= remaining -> configured.teamCount
            else -> DIVISION_STEPS.firstOrNull { it <= remaining } ?: break
        }
        repeat(size) {
            standings[ranked[index].ref] = Standing.InDivision(division)
            index += 1
        }
    }
    while (index < ranked.size) {
        standings[ranked[index].ref] = Standing.WithoutDivision
        index += 1
    }
}

@SpecRef("1.9")
private fun candidateThreshold(country: Int): Int =
    if (country in HIGH_THRESHOLD_COUNTRIES) HIGH_CANDIDATE_MINIMUM else CANDIDATE_MINIMUM

/** Countries that need sixteen clubs to host a league: ALE, ARG, FRA, ING, ITA. */
@SpecRef("1.9")
private val HIGH_THRESHOLD_COUNTRIES = setOf(3, 11, 72, 97, 104)

@SpecRef("1.9")
private const val CANDIDATE_MINIMUM = 10

@SpecRef("1.9")
private const val HIGH_CANDIDATE_MINIMUM = 16

@SpecRef("1.9")
private const val MAX_DIVISIONS = 4

/** Division sizes tried largest first, the default when no configuration fits. */
@SpecRef("1.9")
private val DIVISION_STEPS = listOf(20, 18, 16, 14, 12, 10)

@SpecRef("1.9")
private const val TOP_CLUBS_WITHOUT_LEAGUE = 15

/**
 * Stream tag for the tie break inside a club's stream family. Player streams
 * fork by squad index, small numbers, so any large distinct constant is safe.
 */
private const val PYRAMID_TIEBREAK_STREAM = 0x50F7L

@SpecRef("1.9")
private const val TIEBREAK_BOUND = 1000
```

- [ ] **Step 4: Run PyramidTest to green, then `./gradlew check`, then commit**

```bash
git add engine/src/main/kotlin/org/openfoot/engine/world/Pyramid.kt engine/src/test/kotlin/org/openfoot/engine/world/PyramidTest.kt
git commit -m "feat(engine): o gerador de piramide da secao 1.9, com desempate derivado da semente"
```

---

### Task 4: generateWorld assembles pyramids and squads stand on their standing

**Files:**
- Modify: `engine/src/main/kotlin/org/openfoot/engine/world/WorldGeneration.kt`
- Modify: `engine/src/main/kotlin/org/openfoot/engine/world/SquadGeneration.kt`
- Test: `engine/src/test/kotlin/org/openfoot/engine/world/WorldGenerationTest.kt`, `engine/src/test/kotlin/org/openfoot/engine/world/SquadGenerationTest.kt`

**Interfaces:**
- Consumes: `assemblePyramids`/`Standing` (Task 3), `ClubBands.bands(..., reputationPath=...)` (Task 2).
- Produces: `fun generateWorld(dataset, seed, activeLeagues: Set<Int> = setOf(Country.BRAZIL)): World`; `GeneratedClub.standing: Standing` with `val division: Int? get() = (standing as? Standing.InDivision)?.division`; `fun generateSquad(club, standing: Standing, dataset, options, clubRng)`. Tasks 6 and 8 rely on these exact shapes.

- [ ] **Step 1: Update tests first.** In `WorldGenerationTest.kt` the fixtures live near the top; give every generated world an explicit `activeLeagues` matching its fixture country so the tests keep generating league clubs (whichever country index the fixtures use - read it; if fixtures do not set 20 or more same-country clubs, pass `activeLeagues = emptySet()` and the club count assertions change to `min(clubs, 15)` per country). Required test changes:
  - `adding a club leaves every other club untouched` (line 51): the added club must sit in a DIFFERENT country from the others, and the test docstring gains one sentence: adding a club to the same country may reshape that country's pyramid, which is section 1.9's own behavior; the invariance now holds across countries. Add a companion test:

```kotlin
    @Test
    fun `adding a club to another country leaves this country byte identical`() {
        // build the base world, then a world whose dataset adds one club in a country
        // no base club uses, and assert every base club's squad is equal in both
    }
```

  (Write it concretely against the file's fixture helpers.)
  - Add: `` `the world only holds clubs the pyramid instantiated` `` asserting a 20-club non-active country yields 15 generated clubs.
  - Add: `` `a generated club knows its standing` `` asserting `world.clubs.first().standing` is the expected `Standing` and `division` mirrors it.
  - `SquadGenerationTest.kt`: its direct `generateSquad(club, dataset, options, rng)` calls gain the `standing` argument; use `Standing.InDivision(club.division!!)` where the fixture set a division, `Standing.WithoutDivision` where it was null, `Standing.ByReputation` where it set `nationalTeam = true`. Strength expectations do not move for these three mappings (bands are identical to the old semantics for them).

- [ ] **Step 2: Run both test classes; expect compilation failures.**

- [ ] **Step 3: Implement.**

`WorldGeneration.kt` - replace `generateWorld` and `GeneratedClub`:

```kotlin
data class GeneratedClub(
    val entry: ClubEntry,
    @property:SpecRef("1.9") val standing: Standing,
    val squad: List<Player>,
    @property:SpecRef("5.6") val designated: Designated,
) {
    val division: Int? get() = (standing as? Standing.InDivision)?.division
}
```

```kotlin
@SpecRef("4")
fun generateWorld(
    dataset: WorldDataset,
    seed: Long,
    activeLeagues: Set<Int> = setOf(Country.BRAZIL),
): World {
    val worldRng: Rng = SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)
    val standings = assemblePyramids(dataset, activeLeagues, worldRng)

    val clubs = dataset.clubs.mapNotNull { club ->
        val standing = standings[club.ref] ?: return@mapNotNull null
        val clubRng = worldRng.fork(clubKey(club.ref))
        val squad = generateSquad(club, standing, dataset, dataset.options, clubRng)
        GeneratedClub(
            entry = club,
            standing = standing,
            squad = squad,
            designated = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD),
        )
    }
    return World(seed = seed, clubs = clubs)
}
```

Extend the `generateWorld` docstring with two sentences: the active league set defaults to Brazil because that is the one league the original pre-ticks at new game; and clubs the pyramid does not instantiate are absent from the world, not weak in it. Import `org.openfoot.model.Country`.

`SquadGeneration.kt` - `generateSquad` gains `standing: Standing` as its second parameter and passes it to `generatePlayer` (also second parameter); inside `generatePlayer` replace line 36 and the salary call's division:

```kotlin
    val bands = when (standing) {
        is Standing.InDivision -> ClubBands.bands(standing.division, club.reputation)
        Standing.WithoutDivision -> ClubBands.bands(null, club.reputation)
        Standing.ByReputation -> ClubBands.bands(null, club.reputation, reputationPath = true)
    }
```

and in the `salary(...)` call: `division = (standing as? Standing.InDivision)?.division`. Update the two functions' docstrings: the standing comes from the pyramid of section 1.9 and replaces the dataset's former division field.

- [ ] **Step 4: Run `:engine:test`, expect green (fix what the compiler still names - the CLI does not compile until its call site is touched: `cli` calls `generateWorld(dataset, seed)` which still compiles thanks to the default). Then `./gradlew check`, then commit.**

```bash
git add engine/src/main/kotlin/org/openfoot/engine/world/WorldGeneration.kt engine/src/main/kotlin/org/openfoot/engine/world/SquadGeneration.kt engine/src/test/kotlin/org/openfoot/engine/world/WorldGenerationTest.kt engine/src/test/kotlin/org/openfoot/engine/world/SquadGenerationTest.kt
git commit -m "feat(engine): o mundo monta as piramides e cada elenco nasce da sua posicao nelas"
```

---

### Task 5: importer fills the real country table and emits league configurations

**Files:**
- Create: `importer/src/main/kotlin/org/openfoot/importer/CountryTable.kt`
- Modify: `importer/src/main/kotlin/org/openfoot/importer/LeagueConfig.kt` (reader emits `LeagueConfigEntry`; `DivisionShape` and `assignDivisions` are deleted)
- Modify: `importer/src/main/kotlin/org/openfoot/importer/ImportInstallation.kt` (country level/continent from the table; leagues into the dataset; the no-division note and `reportDivisionCoverage` are deleted - division is a world property now)
- Test: `importer/src/test/kotlin/org/openfoot/importer/CountryTableTest.kt` (create), `importer/src/test/kotlin/org/openfoot/importer/LeagueConfigTest.kt` (rewrite reader tests, drop assignment tests), `importer/src/test/kotlin/org/openfoot/importer/InstallationImporterTest.kt` (update)

**Interfaces:**
- Consumes: `LeagueConfigEntry` (Task 1).
- Produces: `internal object CountryTable { data class Row(val level: Int, val continent: Int); val rows: Map<Int, Row> }` with all 224 entries; `LeagueConfigReader.read(bytes): List<LeagueConfigEntry>`; `ImportResult.dataset.leagues` populated.

- [ ] **Step 1: Write the failing tests.**

`CountryTableTest.kt`:

```kotlin
package org.openfoot.importer

import kotlin.test.Test
import kotlin.test.assertEquals

class CountryTableTest {
    @Test
    fun `the table carries all two hundred and twenty four countries`() {
        assertEquals(224, CountryTable.rows.size)
    }

    @Test
    fun `spot checks against the published table hold`() {
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[3])
        assertEquals(CountryTable.Row(level = 20, continent = 1), CountryTable.rows[11])
        assertEquals(CountryTable.Row(level = 20, continent = 1), CountryTable.rows[29])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[65])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[72])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[97])
        assertEquals(CountryTable.Row(level = 20, continent = 0), CountryTable.rows[104])
        assertEquals(CountryTable.Row(level = 19, continent = 0), CountryTable.rows[154])
        assertEquals(-1, CountryTable.rows[135]?.continent)
        assertEquals(-1, CountryTable.rows[204]?.continent)
        assertEquals(-1, CountryTable.rows[207]?.continent)
    }

    @Test
    fun `levels stay inside the scale the spec publishes`() {
        assertEquals(emptyList(), CountryTable.rows.filterValues { it.level !in 11..20 }.keys.toList())
    }
}
```

`LeagueConfigTest.kt`: rewrite the reader tests so they assert a `LeagueConfigEntry` with the relegation clamp and the turns rule; delete every `assignDivisions` test (that behavior moved to `PyramidTest` in Task 3). Concretely, using the existing `ImportFixtures.Pyramid`/`Tier` fixtures (extend `Tier` if it lacks the extra serialized fields - the fixture classes are plain Java-serializable stand-ins; add `nRebaixados`, `formula`, `desempate` int fields defaulting to 0):

```kotlin
    @Test
    fun `a tier is read with relegation clamp and resolved turns`() {
        // 10 team tier claiming 4 relegated: clamped to 2 per FORMAT-SPEC;
        // formula 4 on a 10 team league resolves to 4 turns per section 1.3
    }

    @Test
    fun `desempate zero means penalties on and one means off`() { ... }
```

Write these tests fully against the fixture shapes found in `ImportFixtures.kt` (read it first); the load-bearing assertions are `relegated == 2` for the clamp case, `turns == 4` for ESP-third-division shape, and `penaltiesTiebreak` true for `desempate = 0`, false for `1`.

`InstallationImporterTest.kt`: replace the assertions of `a country is rated by the strongest club it holds` and `a country holding no club falls back rather than being rated zero` with table-driven expectations: a club with ref suffix `_bra` yields `CountryEntry(level = 20, continent = 1)` because Brazil is index 29 in the table (the fixture team builder sets the country index - read what it sets and assert the table row for that index); the fallback test now asserts the note mentions the table, see Step 3. Delete `the no-division note states generation, growth and decline` and `no pyramid at all is reported rather than left to be noticed` if the note it pins is gone (it is - see Step 3); add:

```kotlin
    @Test
    fun `league configurations reach the dataset`() {
        // installation with one Pyramid fixture: result.dataset.leagues is not empty
        // and carries the configured country and division
    }
```

- [ ] **Step 2: Run the three importer test classes; expect failures.**

- [ ] **Step 3: Implement.**

`CountryTable.kt`: transcribe ALL 224 rows from the table in `spec/SIMULATION-SPEC.md` section 4.4.1 (the four-column layout lists index, three-letter code, continent, level; the code column is not carried):

```kotlin
package org.openfoot.importer

import org.openfoot.model.SpecRef

/**
 * The table of countries embedded in the original: continent and country
 * level per index. Transcribed verbatim from SIMULATION-SPEC section 4.4.1,
 * where it is published in full. The three reserved indices carry continent
 * minus one and belong to no confederation.
 */
@SpecRef("4.4.1")
internal object CountryTable {
    data class Row(val level: Int, val continent: Int)

    val rows: Map<Int, Row> = mapOf(
        0 to Row(level = 14, continent = 3),
        // ... all 224 entries, one line each, in index order ...
    )
}
```

The transcription is mechanical and MUST be verified by the three tests above, not by eye. Transcribe from the spec file directly (open `spec/SIMULATION-SPEC.md`, section 4.4.1); a short throwaway script that parses the markdown table into Kotlin lines is a fine way to produce the literal, but the committed artifact is the plain `mapOf` literal, no generator.

`LeagueConfig.kt`: `read` returns `List<LeagueConfigEntry>`; per tier it also reads `nRebaixados` (constant `RELEGATED = "nRebaixados"`), `formula` (`FORMULA = "formula"`), `desempate` (`TIEBREAK = "desempate"`), all `@SpecRef("FORMAT-SPEC, configuracoes")`; then:

```kotlin
            val relegated = (tier.intOrNull(RELEGATED) ?: 0).let {
                if (teamCount <= SMALL_LEAGUE_TEAMS && it > SMALL_LEAGUE_RELEGATED_CAP) {
                    SMALL_LEAGUE_RELEGATED_CAP
                } else {
                    it.coerceIn(0, teamCount)
                }
            }
            LeagueConfigEntry(
                country = country,
                division = division,
                teamCount = teamCount,
                relegated = relegated,
                turns = resolveTurns(teamCount, tier.intOrNull(FORMULA) ?: 0),
                penaltiesTiebreak = (tier.intOrNull(TIEBREAK) ?: 0) == 0,
            )
```

with:

```kotlin
/**
 * Turns for a league of a given size, per section 1.3: the formula field of
 * a national league configuration is really the turn count, and it only
 * overrides the default for leagues of ten, twelve or fourteen teams.
 */
@SpecRef("1.3")
internal fun resolveTurns(teamCount: Int, formula: Int): Int {
    val default = when (teamCount) {
        8, 10 -> 4
        12, 14 -> 3
        26, 28, 30, 36 -> 1
        else -> 2
    }
    return if (teamCount in listOf(10, 12, 14) && formula in 2..4) formula else default
}

@SpecRef("1.9")
private const val SMALL_LEAGUE_TEAMS = 10

@SpecRef("1.9")
private const val SMALL_LEAGUE_RELEGATED_CAP = 2
```

Guard `division` into `1..LeagueConfigEntry.MAX_DIVISION` (skip the tier with a note-worthy value rather than throwing - return null from the mapNotNull and let the caller count). Delete `DivisionShape` and `assignDivisions` and their imports.

`ImportInstallation.kt`: in the country-building pass, `CountryTable.rows[index]` supplies `level` and `continent`; the strongest-club derivation and `UNRATEABLE_COUNTRY_LEVEL` survive only for an index missing from the table, with the note text `"country $index is outside the embedded table, level derived from its strongest club"`; a table continent of `-1` also falls back to `UNKNOWN_CONTINENT` (cannot happen for a country that has clubs, and the fallback keeps it from ever granting Europe). Delete `reportDivisionCoverage` and its call; the pyramids-read pass now feeds `LeagueConfigReader` results into `dataset.leagues` (preserving file order, which the first-match rule makes meaningful) and notes `"no league configuration under ..."` only as information (`"...; worlds generated from this dataset will use the embedded defaults of section 1.9"`). Keep `MAJOR_LEAGUE_COUNTRIES` as is (still `@SpecRef`, now doubly confirmed).

- [ ] **Step 4: Run all importer tests to green, `./gradlew check`, commit.**

```bash
git add importer/ 
git commit -m "feat(importer): tabela real de paises e configuracoes de liga no conjunto de dados"
```

---

### Task 6: the CLI chooses which leagues are played

**Files:**
- Modify: `cli/src/main/kotlin/org/openfoot/cli/Main.kt` (worldgen and match parse `--leagues`; `summarise` prints the standing)
- Test: `cli/src/test/kotlin/org/openfoot/cli/LeagueSelectionTest.kt` (create), `cli/src/test/kotlin/org/openfoot/cli/SummaryTest.kt` (update)

**Interfaces:**
- Consumes: `generateWorld(dataset, seed, activeLeagues)` (Task 4).
- Produces: `internal fun parseLeagues(value: String?, dataset: WorldDataset): Set<Int>`.

- [ ] **Step 1: Failing tests.** `LeagueSelectionTest.kt`:

```kotlin
package org.openfoot.cli

import org.openfoot.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LeagueSelectionTest {
    @Test
    fun `absent means brazil, matching the original's pre ticked box`() {
        assertEquals(setOf(Country.BRAZIL), parseLeagues(null, twoCountryDataset()))
    }

    @Test
    fun `named countries resolve by their dataset name`() {
        assertEquals(setOf(29, 65), parseLeagues("BRA,ESP", twoCountryDataset()))
    }

    @Test
    fun `all selects every country the dataset holds`() {
        assertEquals(setOf(29, 65), parseLeagues("all", twoCountryDataset()))
    }

    @Test
    fun `an unknown name is refused by name`() {
        assertFailsWith<CliError> { parseLeagues("BRA,XYZ", twoCountryDataset()) }
    }
}
```

with a private `twoCountryDataset()` building a minimal `WorldDataset` whose countries are named `BRA` (29) and `ESP` (65) - copy the construction idiom from `SummaryTest.kt`. In `SummaryTest.kt`, update the expected summary block: each club line gains a standing suffix and the header gains none (see Step 3 for the exact format; update the pinned strings accordingly).

- [ ] **Step 2: Run; expect compilation failure.**

- [ ] **Step 3: Implement.** In `Main.kt`:

```kotlin
/**
 * Which countries' leagues this world plays, from the --leagues flag.
 *
 * Absent means Brazil, because that is the box the original pre ticks at
 * new game. Names are the dataset's country names, which the importer
 * derives from the file suffixes, so BRA and ESP name the shipped leagues.
 * The word all plays every league the pyramid generator finds eligible.
 */
@SpecRef("1.9")
internal fun parseLeagues(value: String?, dataset: WorldDataset): Set<Int> {
    if (value == null) return setOf(Country.BRAZIL)
    if (value == "all") return dataset.countries.map { it.index }.toSet()
    return value.split(',').map { name ->
        val trimmed = name.trim().uppercase()
        dataset.countries.firstOrNull { it.name.uppercase() == trimmed }?.index
            ?: fail("no country named '$trimmed' in this dataset")
    }.toSet()
}
```

`worldgen` and `match` read `options["--leagues"]`, call `parseLeagues(...)`, and pass the set to `generateWorld`. Usage text gains ` [--leagues BRA,ESP|all]` on both lines and one explanatory sentence. `summarise` club line gains the standing: after `level ${...}`, insert `div ${club.division ?: "-"}  ` when `club.standing != Standing.ByReputation`, else `rep  `; keep everything else byte-stable (SummaryTest pins the exact strings - write them out there).

- [ ] **Step 4: Green, `./gradlew check`, commit.**

```bash
git add cli/
git commit -m "feat(cli): a escolha de ligas ativas chega ao worldgen e ao match"
```

---

### Task 7: schema version 2 - the one breaking commit

**Files:**
- Modify: `dataset/src/main/kotlin/org/openfoot/dataset/WorldDataset.kt` (remove `ClubEntry.division` and `ClubEntry.nationalTeam`; remove the `!nationalTeam || division == null` require; `CountryEntry.continent` loses its default; `CURRENT_VERSION = 2` with the docstring noting the v1 break and why: division and national teams became world properties)
- Modify: every fixture that names the removed fields. Find them all with: `grep -rln "division =\|nationalTeam =" --include=*.kt` and remove those named arguments (the transformation is purely mechanical: delete the argument; where a test asserted `division` on a `ClubEntry`, the assertion moved to `GeneratedClub.standing` in Task 4 already). Expected files: `dataset/.../WorldDatasetTest.kt`, `engine/.../world/*Test.kt` fixtures, `cli/.../SummaryTest.kt`, `importer/.../InstallationImporterTest.kt`, `importer/.../TeamFileReader.kt` (if it names either field when constructing `ClubEntry` - it does not set them today, verify), `engine/.../world/SquadGeneration.kt` (any leftover read must already be gone from Task 4 - the compiler is the checklist).
- Test: existing suites are the harness; add to `WorldDatasetTest.kt`:

```kotlin
    @Test
    fun `a version one file is refused with the version in the message`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            decode(encode(sample()).replace("\"version\": 2", "\"version\": 1"))
        }
        assertTrue(failure.message.orEmpty().contains("version 1"))
    }
```

(adapted to the file's encode/decode helpers and actual JSON formatting).

**Interfaces:** consumes everything from Tasks 1-6; produces the final v2 `WorldDataset` shape.

- [ ] **Step 1: Make the schema change, then let the compiler enumerate every fixture to fix; fix them all.**
- [ ] **Step 2: `./gradlew check` - the whole build is the test for this task.** Expected: green.
- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(dataset)!: esquema versao 2, divisao e selecao viram propriedades do mundo"
```

---

### Task 8: world-level validation and a worldgen golden vector

**Files:**
- Create: `validation/src/test/kotlin/org/openfoot/validation/WorldSanityTest.kt`
- Create: `cli/src/test/kotlin/org/openfoot/cli/WorldGoldenVectorTest.kt`

**Interfaces:** consumes `generateWorld` (Task 4) and `summarise` (Task 6). Produces nothing downstream.

- [ ] **Step 1: `WorldSanityTest.kt`** - synthetic dataset, two level-20 clubs with full 25-man squads of identical entries: club A in a country with an active league (give the country 20 clubs so A lands in division 1), club B alone in a non-active country with reputation 5. Assertions:

```kotlin
    @Test
    fun `a top club of a country without a played league is not weak`() {
        // bands div1 = 20/7 vs reputation5 = 22/7: B's best player must be
        // within a small band of A's, never collapsed to the 1/1 row.
        // assert abs(bestA - bestB) <= 6 over seeds 1..50
    }

    @Test
    fun `a country without a played league instantiates fifteen clubs`() { ... }

    @Test
    fun `the whole world replays from its seed`() {
        // generateWorld twice with the same arguments: equal worlds
    }
```

Write the fixtures concretely in the file (follow `EqualSides.kt` for style: named constants, docstrings citing 1.9/4.4); expected strength bands are hand-computed from 4.4: level 20 maps to 25; div1 base 20 gives 45 to 47 before starter and star bonuses and country scale; rep5 base 22 gives 47 to 49 - the two distributions overlap and the assertion band of 6 holds with slack.

- [ ] **Step 2: `WorldGoldenVectorTest.kt`** - a fixed synthetic dataset built in code (NOT read from the real install), seed 42, `activeLeagues` = the fixture country; pin `summarise(generateWorld(...))` to an exact expected string, in the manner of `MatchGoldenVectorTest`: first run the generator, read the actual output, verify every line by hand against sections 1.9 and 4.4, then commit the pinned literal with a docstring explaining what the vector can and cannot see (no importer, no real data, one country).

- [ ] **Step 3: `./gradlew check`, commit**

```bash
git add validation/ cli/src/test/kotlin/org/openfoot/cli/WorldGoldenVectorTest.kt
git commit -m "test(validation): sanidade de mundo da 1.9 e vetor dourado do worldgen"
```

---

### Task 9: acceptance against the real installation (local only, nothing committed)

- [ ] **Step 1:** `./gradlew clean check :cli:installDist` - green.
- [ ] **Step 2:** Import the real install to the scratchpad (allowed: data files, not code):
`./cli/build/install/openfoot-cli/bin/openfoot-cli import --install C:/Brasfoot22-23 --out <scratchpad>/base2.json`
Expected: 703 clubs; countries carry table levels (spot check in the JSON: country 29 level 20 continent 1); `leagues` holds the BRA and ESP entries; NO note about clubs without division.
- [ ] **Step 3:** `worldgen --dataset <scratchpad>/base2.json --seed 42 --leagues all` and `--leagues BRA`:
  - with `all`: Bayern's (`bayern_ale` or similar ref) best player and Real Madrid's best player within a handful of points of each other. CORRECTION (post-acceptance): the original expectation here said German clubs would carry `div` markers, but the shipped installation has only 8 German team files, below the 16-club candidate threshold of section 1.9, so German clubs correctly render `rep` even under `all`. Only BRA (196 files) and ESP (46) reach their thresholds in the distributed data. Do not "fix" the `rep` rendering against this stale expectation.
  - with `BRA` only: German clubs appear with `rep` marking and only the top 15 German clubs exist; Brazilian clubs carry `div 1..4` (or as many divisions as fit).
  - Same command twice: byte-identical output (`diff`).
- [ ] **Step 4:** Report the observed numbers in the completion report. Delete nothing from the repo; the scratchpad dataset stays out of git.

---

## Deferred to the next plan (recorded here so nobody hunts for them)

State championships (`.ces` into the dataset) and national-team generation (spec 4.12) - each lands with its consumer, per the design addendum. The spec-team's open note about position-varying trait-bonus increments (4.2) awaits a follow-up sweep before any code changes.
