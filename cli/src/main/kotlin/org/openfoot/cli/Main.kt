package org.openfoot.cli

import kotlinx.serialization.json.Json
import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.lineup.Availability
import org.openfoot.engine.lineup.assembleMatch
import org.openfoot.engine.match.simulateMatch
import org.openfoot.engine.world.World
import org.openfoot.engine.world.generateWorld
import org.openfoot.importer.InstallationImporter
import org.openfoot.model.CompetitionKind
import org.openfoot.model.RuleSets
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng
import java.io.File
import kotlin.system.exitProcess

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

private val USAGE = """
    usage: openfoot-cli import   --install <path> --out <path>
           openfoot-cli worldgen --dataset <path> --seed <number>
           openfoot-cli match    --dataset <path> --seed <number> --home <ref> --away <ref>

      import   reads your own installation of the original game and writes a
               dataset. Nothing is copied but numbers, and the files stay put.
      worldgen builds a world from a dataset and prints what came out. The same
               dataset and the same seed always print the same thing.
      match    generates a world from a dataset and a seed, then plays one
               match between the two named clubs and prints a report. The
               same dataset, seed and clubs always print the same match.
""".trimIndent()

/**
 * Why the dataset cannot be written where it was asked for, or null when it
 * can.
 *
 * Asked before the import runs rather than after. Reading an installation takes
 * seven hundred files, and finding out about the destination once that is done
 * throws all of it away over a typo.
 *
 * A missing directory is refused instead of created. Somebody who mistypes a
 * path wants to be told, not to find a tree of empty directories later.
 */
internal fun outputPathProblem(out: String): String? {
    val file = File(out)
    if (file.isDirectory) {
        return "$out is a directory, and the dataset needs a file name"
    }
    val parent = file.absoluteFile.parentFile
    if (parent != null && !parent.isDirectory) {
        return "no directory at $parent to write $out into"
    }
    return null
}

/**
 * Reads an installation and writes a dataset.
 *
 * Everything the installation could not supply is printed rather than left for
 * the reader to discover, because a dataset with placeholder country levels
 * generates a world that looks right and is not.
 */
private fun importInstallation(args: List<String>) {
    val options = parseOptions(args)
    val install = options["--install"] ?: fail("import needs --install <path>")
    val out = options["--out"] ?: fail("import needs --out <path>")

    val root = File(install)
    if (!root.isDirectory) {
        fail("no installation directory at $install")
    }
    outputPathProblem(out)?.let { fail(it) }

    val result = try {
        InstallationImporter.importFrom(root)
    } catch (failure: Exception) {
        fail("could not import $install: ${failure.message}")
    }

    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    File(out).writeText(json.encodeToString(result.dataset))

    println("clubs     ${result.dataset.clubs.size}")
    println("players   ${result.dataset.clubs.sumOf { it.squad.size }}")
    println("countries ${result.dataset.countries.size}")
    println("written   $out")
    if (result.notes.isNotEmpty()) {
        println("notes     ${result.notes.size}")
        result.notes.forEach { println("  $it") }
    }
}

/**
 * Reads a dataset, generates a world and describes it.
 *
 * Everything that can go wrong here is the user handing over a path or a
 * number, so each failure says which one and stops, rather than generating a
 * world from a default nobody asked for.
 */
private fun worldgen(args: List<String>) {
    val options = parseOptions(args)
    val path = options["--dataset"] ?: fail("worldgen needs --dataset <path>")
    val seedText = options["--seed"] ?: fail("worldgen needs --seed <number>")
    val seed = seedText.toLongOrNull() ?: fail("seed '$seedText' is not a number")

    val file = File(path)
    if (!file.isFile) {
        fail("no dataset file at $path")
    }

    val dataset = try {
        Json.decodeFromString<WorldDataset>(file.readText())
    } catch (failure: Exception) {
        fail("dataset at $path is not usable: ${failure.message}")
    }

    print(summarise(generateWorld(dataset, seed)))
}

/**
 * Reads a dataset, generates a world, assembles a match between two of its
 * clubs and plays it.
 *
 * Everything that can go wrong here is the user handing over a path, a
 * number or a club reference, so each failure says which one and stops.
 * A club reference that does not resolve in the generated world is the
 * likeliest mistake, a typo in a ref, so its own message names the ref it
 * could not find rather than only saying "home" or "away".
 *
 * The match is played as a friendly of the world's first season. This is a
 * command line demonstration of two clubs playing each other, not a fixture
 * drawn from a season, so neither a real competition kind nor a real season
 * number applies; a friendly is the one kind that carries no assumption
 * about which competition or round produced the match. Concretely, that
 * choice of kind means two things a reader of the result should know: home
 * advantage still applies, because CompetitionKind.isNeutralGround is only
 * true for CLUB_WORLD_CUP and NATIONAL_TEAM, and no reputation handicap is
 * applied, because competitionMultiplier in EffectiveStrength.kt has no
 * branch for FRIENDLY and falls through to its else of 1.0.
 *
 * Every player of both clubs is available. This command generates a world and
 * plays one match in it, so there is no season behind the match to have
 * injured or suspended anybody, and Availability.FULL_SQUAD is the truth about
 * this fixture rather than a placeholder. It is written out at the call site
 * because assembleMatch refuses to default it, so that the day a career mode
 * has real availability to pass, this line is a visible thing to change rather
 * than an invisible one to forget.
 *
 * assembleMatch and simulateMatch each take their own Rng built straight from
 * the seed. Every fork either of them takes from that Rng depends only on the
 * seed and the tag it forks with, never on how many draws the other one has
 * made, so the two do not interfere with each other and the same seed always
 * assembles and plays the same match.
 */
private fun match(args: List<String>) {
    val options = parseOptions(args)
    val path = options["--dataset"] ?: fail("match needs --dataset <path>")
    val seedText = options["--seed"] ?: fail("match needs --seed <number>")
    val homeRef = options["--home"] ?: fail("match needs --home <ref>")
    val awayRef = options["--away"] ?: fail("match needs --away <ref>")
    val seed = seedText.toLongOrNull() ?: fail("seed '$seedText' is not a number")

    val file = File(path)
    if (!file.isFile) {
        fail("no dataset file at $path")
    }

    val dataset = try {
        Json.decodeFromString<WorldDataset>(file.readText())
    } catch (failure: Exception) {
        fail("dataset at $path is not usable: ${failure.message}")
    }

    val world = generateWorld(dataset, seed)
    val home = world.club(homeRef) ?: fail("no club '$homeRef' in this world")
    val away = world.club(awayRef) ?: fail("no club '$awayRef' in this world")

    val assembled = assembleMatch(
        home = home,
        away = away,
        dataset = dataset,
        kind = CompetitionKind.FRIENDLY,
        season = MATCH_SEASON,
        rules = RuleSets.CLASSIC,
        availability = Availability.FULL_SQUAD,
        rng = SplitMix64Rng(seed),
    )
    val report = simulateMatch(
        setup = assembled.setup,
        rng = SplitMix64Rng(seed),
        homeBench = assembled.homeBench,
        awayBench = assembled.awayBench,
    )

    print(describe(report, homeRef, awayRef))
}

/**
 * The season a match played straight from the command line is credited to.
 *
 * Not an arbitrary placeholder: RuleSets.CLASSIC.compressionFirstSeason is 5,
 * and differenceDivisor in StrengthDifference.kt only widens once season
 * reaches that number, so a season below it, like this one, uses the base,
 * uncompressed divisors of section 3.5. Nothing here plays out a career, so
 * there is no real season to credit the match to, but season one is still a
 * choice with a visible effect, not a neutral default: it is the setting
 * that lets the two clubs' strength difference matter at its full, early
 * career weight, rather than the flattened one a later season would apply.
 */
@SpecRef("3.5")
private const val MATCH_SEASON = 1

private fun parseOptions(args: List<String>): Map<String, String> {
    val options = LinkedHashMap<String, String>()
    var index = 0
    while (index < args.size) {
        val flag = args[index]
        if (!flag.startsWith("--")) {
            fail("unexpected argument '$flag'")
        }
        options[flag] = args.getOrNull(index + 1) ?: fail("$flag needs a value")
        index += 2
    }
    return options
}

/**
 * A command line mistake: a missing flag, a bad number, a path that is not
 * there. Thrown instead of exiting so every failure path can be exercised by
 * a test; main is the only place that turns it into a process exit.
 */
internal class CliError(message: String) : RuntimeException(message)

private fun fail(message: String): Nothing {
    throw CliError(message)
}

/**
 * Describes a generated world in a form that is the same on every run.
 *
 * Nothing here reads a clock, a locale or a hash order, because the whole point
 * of printing it is that two runs can be compared with a diff. Clubs are listed
 * by reference rather than in dataset order for the same reason.
 */
internal fun summarise(world: World): String {
    val strengths = world.clubs.flatMap { club -> club.squad.map { it.strength } }.sorted()
    val builder = StringBuilder()

    builder.appendLine("seed      ${world.seed}")
    builder.appendLine("clubs     ${world.clubs.size}")
    builder.appendLine("players   ${world.playerCount}")

    if (strengths.isNotEmpty()) {
        builder.appendLine(
            "strength  min ${strengths.first()}  median ${strengths[strengths.size / 2]}  " +
                "max ${strengths.last()}",
        )
    }

    for (club in world.clubs.sortedBy { it.entry.ref }) {
        val best = club.squad.maxByOrNull { it.strength }
        builder.appendLine(
            "  ${club.entry.ref}  level ${club.entry.level}  players ${club.squad.size}  " +
                "best ${best?.strength ?: 0} ${best?.name.orEmpty()}",
        )
    }

    return builder.toString()
}
