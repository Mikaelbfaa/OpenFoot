# v0.2 Phase 1 (Hardening Track) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the CLI's parsing and dispatch testable and tested, replace the per-player linear country scan with an index, and upgrade the importer's no-division note to state its full cost.

**Architecture:** Three independent, sweep-proof changes from the v0.2 design's hardening track. The CLI work extracts a `dispatch` function that returns an exit code and converts `fail` from process exit to a thrown `CliError`, preserving observable behavior while making both testable. The dataset change is a behavior-preserving lookup index. The importer change is a note-text upgrade pinned by a new test.

**Tech Stack:** Kotlin/JVM 21, Gradle wrapper, kotlin.test on JUnit Platform. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-29-v02-importador-geracao-de-mundo-design.md` (section "Trilha de endurecimento, independente da varredura")

## Global Constraints

- Work on branch `feat/v0-2-hardening`, created from `main` (NOT from `docs/v0-2-design`).
- Every commit: Conventional Commits in Portuguese subject style used by this repo (`refactor(cli):`, `test(cli):`, `perf(dataset):`, `fix(importer):`), atomic, `./gradlew check` green before each commit.
- Comments: docstrings only, plain ASCII, no markdown syntax inside comments (enforced by `checkCommentStyle`).
- Any new constant that comes from the spec carries `@SpecRef("<section>")`. This plan adds no new spec constants; do not invent any.
- Never branch on rule set identity; never add I/O, clock, platform randomness, HashMap iteration, or transcendental math to `:model`, `:dataset`, `:engine`.
- Clean-room: code is written only from `spec/` and this plan. Never read anything under `C:\Brasfoot22-23`.
- Run a single test class with: `./gradlew :cli:test --tests "org.openfoot.cli.CliDispatchTest"` (adjust module and class).

---

### Task 1: CLI errors throw and dispatch returns an exit code

The CLI's `fail` calls `exitProcess`, so no failure path can be exercised by a test without killing the test JVM, and `main`'s `when` cannot be called at all. Convert failures to a thrown `CliError` and extract the `when` into an `internal fun dispatch(args): Int`; `main` becomes the only place a process exits. Observable behavior is unchanged: same stderr lines, same exit codes, same usage printing.

**Files:**
- Modify: `cli/src/main/kotlin/org/openfoot/cli/Main.kt` (the `main` function, the `fail` function; add `CliError` and `dispatch`)
- Test: `cli/src/test/kotlin/org/openfoot/cli/CliDispatchTest.kt` (create)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `internal class CliError(message: String) : RuntimeException(message)` and `internal fun dispatch(args: Array<String>): Int` in package `org.openfoot.cli`. Task 2 uses `CliError`.

- [ ] **Step 1: Write the failing test**

Create `cli/src/test/kotlin/org/openfoot/cli/CliDispatchTest.kt`:

```kotlin
package org.openfoot.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class CliDispatchTest {
    @Test
    fun `help exits zero`() {
        assertEquals(0, dispatch(arrayOf("help")))
    }

    @Test
    fun `no arguments print usage and exit one`() {
        assertEquals(1, dispatch(emptyArray()))
    }

    @Test
    fun `an unknown subcommand exits one`() {
        assertEquals(1, dispatch(arrayOf("bogus")))
    }

    @Test
    fun `a subcommand missing its options exits one rather than crashing`() {
        assertEquals(1, dispatch(arrayOf("worldgen")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cli:test --tests "org.openfoot.cli.CliDispatchTest"`
Expected: compilation FAILURE, `dispatch` is unresolved.

- [ ] **Step 3: Implement**

In `cli/src/main/kotlin/org/openfoot/cli/Main.kt`, replace the current `main` function with:

```kotlin
fun main(args: Array<String>) {
    exitProcess(dispatch(args))
}

/**
 * Runs one subcommand and reports how the process should exit, without
 * exiting. Keeping the exit in main and nowhere else is what lets a test
 * call this with a broken command line and read the answer.
 */
internal fun dispatch(args: Array<String>): Int {
    return try {
        when (args.firstOrNull()) {
            "worldgen" -> {
                worldgen(args.drop(1))
                0
            }

            "match" -> {
                match(args.drop(1))
                0
            }

            "import" -> {
                importInstallation(args.drop(1))
                0
            }

            "help", "--help" -> {
                println(USAGE)
                0
            }

            null -> {
                println(USAGE)
                1
            }

            else -> {
                System.err.println("openfoot-cli: unknown subcommand '${args[0]}'")
                System.err.println(USAGE)
                1
            }
        }
    } catch (mistake: CliError) {
        System.err.println("openfoot-cli: ${mistake.message}")
        1
    }
}
```

Add the error type next to `fail`, and change `fail` to throw instead of exiting (the stderr line moves to the catch in `dispatch`, so the printed output is identical):

```kotlin
/**
 * A command line mistake: a missing flag, a bad number, a path that is not
 * there. Thrown instead of exiting so every failure path can be exercised by
 * a test; main is the only place that turns it into a process exit.
 */
internal class CliError(message: String) : RuntimeException(message)

private fun fail(message: String): Nothing {
    throw CliError(message)
}
```

Remove nothing else. `exitProcess` stays imported for `main`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :cli:test --tests "org.openfoot.cli.CliDispatchTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (comment style, pure deps, all module tests).

- [ ] **Step 6: Commit**

```bash
git add cli/src/main/kotlin/org/openfoot/cli/Main.kt cli/src/test/kotlin/org/openfoot/cli/CliDispatchTest.kt
git commit -m "refactor(cli): erros viram CliError e o despacho devolve o codigo de saida"
```

---

### Task 2: parseOptions is pinned by tests

`parseOptions` is the only argument parser the CLI has and nothing tests it. Make it `internal` and pin its four behaviors, including the one nobody decided on purpose (a repeated flag keeps the last value) so a future change to it is loud.

**Files:**
- Modify: `cli/src/main/kotlin/org/openfoot/cli/Main.kt` (the `parseOptions` function: visibility only)
- Test: `cli/src/test/kotlin/org/openfoot/cli/ParseOptionsTest.kt` (create)

**Interfaces:**
- Consumes: `CliError` from Task 1.
- Produces: `internal fun parseOptions(args: List<String>): Map<String, String>` (same signature it has today, `private` becomes `internal`).

- [ ] **Step 1: Write the failing test**

Create `cli/src/test/kotlin/org/openfoot/cli/ParseOptionsTest.kt`:

```kotlin
package org.openfoot.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParseOptionsTest {
    @Test
    fun `flags and values parse into pairs`() {
        val options = parseOptions(listOf("--dataset", "base.json", "--seed", "42"))
        assertEquals(mapOf("--dataset" to "base.json", "--seed" to "42"), options)
    }

    @Test
    fun `a bare token is refused`() {
        assertFailsWith<CliError> { parseOptions(listOf("base.json")) }
    }

    @Test
    fun `a trailing flag without a value is refused`() {
        assertFailsWith<CliError> { parseOptions(listOf("--seed")) }
    }

    @Test
    fun `a repeated flag keeps the last value`() {
        val options = parseOptions(listOf("--seed", "1", "--seed", "2"))
        assertEquals(mapOf("--seed" to "2"), options)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :cli:test --tests "org.openfoot.cli.ParseOptionsTest"`
Expected: compilation FAILURE, `parseOptions` is not visible.

- [ ] **Step 3: Implement**

In `Main.kt`, change the declaration `private fun parseOptions(args: List<String>): Map<String, String>` to `internal fun parseOptions(args: List<String>): Map<String, String>`. Change nothing in its body.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :cli:test --tests "org.openfoot.cli.ParseOptionsTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add cli/src/main/kotlin/org/openfoot/cli/Main.kt cli/src/test/kotlin/org/openfoot/cli/ParseOptionsTest.kt
git commit -m "test(cli): fixa o comportamento do parseOptions, inclusive a bandeira repetida"
```

---

### Task 3: country lookup by index map

`WorldDataset.country(index)` scans the country list on every call, and squad generation calls it once per player: 703 clubs times roughly 25 players over 134 countries. Replace the scan with a map built lazily once. Behavior-preserving: the existing tests at `dataset/src/test/kotlin/org/openfoot/dataset/WorldDatasetTest.kt:115` and `:206-207` already pin that `country` returns the right entry and null for a missing index, so this task adds no new test and the existing suite is the harness. A delegated `by lazy` property has no backing field kotlinx-serialization would write, so the JSON output does not change, which the existing round-trip tests also pin.

**Files:**
- Modify: `dataset/src/main/kotlin/org/openfoot/dataset/WorldDataset.kt:50` (the `country` function; add the lazy map)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `fun country(index: Int): CountryEntry?` unchanged in signature and behavior.

- [ ] **Step 1: Run the existing tests to establish the green baseline**

Run: `./gradlew :dataset:test`
Expected: PASS.

- [ ] **Step 2: Implement**

In `WorldDataset.kt`, replace:

```kotlin
    /** The country entry for an index, or null when the dataset omits it. */
    fun country(index: Int): CountryEntry? = countries.firstOrNull { it.index == index }
```

with:

```kotlin
    /**
     * The country entry for an index, or null when the dataset omits it.
     * Backed by a map built once, because world generation asks once per
     * player. Lazy and delegated, so nothing here reaches the serialized
     * form of a dataset.
     */
    fun country(index: Int): CountryEntry? = countriesByIndex[index]

    private val countriesByIndex by lazy { countries.associateBy { it.index } }
```

- [ ] **Step 3: Run the tests to verify nothing moved**

Run: `./gradlew :dataset:test :engine:test`
Expected: PASS (the engine's world generation consumes `country`, so both suites must stay green).

- [ ] **Step 4: Full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add dataset/src/main/kotlin/org/openfoot/dataset/WorldDataset.kt
git commit -m "perf(dataset): indexa os paises por numero em vez de varrer a lista"
```

---

### Task 4: the no-division note states the whole cost

The importer's note for clubs without a division only mentions the section 4.4 generation base. The spec's item 27 records that this understates the problem: those clubs also grow only to the divisionless ceiling of 30 (against 80 to 100 for a first division side) and decline to a floor of 1 (against 35) once section 4.5's weekly evolution lands. Say all three in the note, and pin the wording with a test.

**Files:**
- Modify: `importer/src/main/kotlin/org/openfoot/importer/ImportInstallation.kt` (the `reportDivisionCoverage` function, around line 84)
- Test: `importer/src/test/kotlin/org/openfoot/importer/InstallationImporterTest.kt` (add one test)

**Interfaces:**
- Consumes: nothing from other tasks. Uses the existing test fixtures `root()`, `installation(...)` and `team(...)` already present in that test file and in `ImportFixtures.kt`.
- Produces: nothing other tasks rely on.

- [ ] **Step 1: Write the failing test**

Add to `InstallationImporterTest.kt`, next to the existing `no pyramid at all is reported rather than left to be noticed` test:

```kotlin
    @Test
    fun `the no-division note states generation, growth and decline`() {
        val result = InstallationImporter.importFrom(installation(root(), listOf(team())))
        val note = result.notes.single { it.contains("no division") }
        assertTrue(note.contains("ceiling of 30"), note)
        assertTrue(note.contains("floor of 1"), note)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :importer:test --tests "org.openfoot.importer.InstallationImporterTest"`
Expected: FAIL on the new test (the current note has neither phrase); every other test PASSES.

- [ ] **Step 3: Implement**

In `ImportInstallation.kt`, inside `reportDivisionCoverage`, replace the `notes.note(...)` call with:

```kotlin
            notes.note(
                "$without of ${clubs.size} clubs have no division, because the installation " +
                    "configures a league for only some countries. Those clubs generate on the " +
                    "weakest band of section 4.4, a strength base of one against twenty; when " +
                    "the weekly evolution of section 4.5 lands they will also grow only to the " +
                    "divisionless ceiling of 30 instead of 80 to 100, and decline to the floor " +
                    "of 1 instead of 35",
            )
```

Update the function's docstring last paragraph from "and it must not be discovered by wondering why a good club is bad" context to also name evolution, replacing the docstring with:

```kotlin
    /**
     * Says how many clubs came out with no division at all.
     *
     * This is the single most consequential thing about an imported world. A
     * club with no division is generated on the weakest band of section 4.4, a
     * strength base of one against twenty for a first division side, and when
     * the weekly evolution of section 4.5 exists it will also be capped at the
     * divisionless growth ceiling and dropped to the divisionless decline
     * floor. The distributed data configures very few countries, so this is
     * the normal case rather than the exception, and it must not be
     * discovered by wondering why a good club is bad.
     */
    @SpecRef("4.4")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :importer:test --tests "org.openfoot.importer.InstallationImporterTest"`
Expected: PASS, including the new test.

- [ ] **Step 5: Full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add importer/src/main/kotlin/org/openfoot/importer/ImportInstallation.kt importer/src/test/kotlin/org/openfoot/importer/InstallationImporterTest.kt
git commit -m "fix(importer): a nota de clube sem divisao declara o custo inteiro"
```

---

### Task 5: final verification

- [ ] **Step 1: Full check from a clean slate**

Run: `./gradlew clean check`
Expected: BUILD SUCCESSFUL, every module.

- [ ] **Step 2: Smoke the CLI wiring by hand**

Run: `./gradlew :cli:installDist` and then `./cli/build/install/openfoot-cli/bin/openfoot-cli help`
Expected: usage text, exit code 0. Then `./cli/build/install/openfoot-cli/bin/openfoot-cli worldgen` (no flags).
Expected: `openfoot-cli: worldgen needs --dataset <path>` on stderr, exit code 1.

- [ ] **Step 3: Report**

Report the branch name and the four commits. Do not merge; integration is decided by the finishing-a-development-branch flow with the user.
