package org.openfoot.importer

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.DatasetOptions
import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.SpecRef
import java.io.File

/** A dataset built from an installation, and everything worth saying about it. */
data class ImportResult(
    val dataset: WorldDataset,
    val notes: List<String>,
)

/**
 * Builds a dataset from a player's own installation of the original game.
 *
 * The files stay where they are. Nothing is copied into the project, and the
 * denylist in the ignore file plus the check in continuous integration exist to
 * keep it that way: this reads a machine the player already owns and writes a
 * dataset of numbers, not of anyone's artwork.
 *
 * What the installation cannot supply is derived or reported, never guessed
 * silently. Country strength and continent come from the embedded table of
 * section 4.4.1 for every country that table knows; league shape comes from
 * whatever configuration files the installation carries, concatenated in file
 * order. Neither club division nor the national team flag is written here -
 * dividing clubs into leagues is the pyramid generator's job (section 1.9,
 * engine module), which reads these league entries at world generation time
 * rather than at import time. Country names live in a table that ships inside
 * the game rather than beside it, so the file naming convention supplies a
 * code and the full name is left for a human.
 */
object InstallationImporter {

    fun importFrom(root: File): ImportResult {
        val notes = ImportNotes()

        val teamFiles = File(root, TEAMS_DIRECTORY)
            .listFiles { file -> file.isFile && file.name.endsWith(TEAM_SUFFIX) }
            ?.sortedBy { it.name }
            ?: throw IllegalArgumentException(
                "no $TEAMS_DIRECTORY directory under $root, so this is not an installation",
            )
        require(teamFiles.isNotEmpty()) { "$TEAMS_DIRECTORY under $root holds no team files" }

        val clubs = ArrayList<ClubEntry>(teamFiles.size)
        val seen = HashSet<String>()
        for (file in teamFiles) {
            try {
                val club = TeamFileReader.read(file.readBytes(), file.nameWithoutExtension, notes)
                if (!seen.add(club.ref)) {
                    notes.note("${file.name} repeats the reference '${club.ref}' and was skipped")
                } else {
                    clubs.add(club)
                }
            } catch (failure: Exception) {
                notes.note("${file.name} could not be read: ${failure.message}")
            }
        }
        require(clubs.isNotEmpty()) { "not one team file under $root could be read" }

        return ImportResult(
            dataset = WorldDataset(
                options = readOptions(root, notes),
                countries = countries(clubs, notes),
                clubs = clubs,
                leagues = readLeagues(root, notes),
            ),
            notes = notes.notes,
        )
    }

    private fun readOptions(root: File, notes: ImportNotes): DatasetOptions {
        val file = File(root, OPTIONS_FILE)
        if (!file.isFile) {
            notes.note("no $OPTIONS_FILE, so the options the original ships with are assumed")
            return DatasetOptions()
        }
        return try {
            OptionsFileReader.read(file.readBytes())
        } catch (failure: Exception) {
            notes.note("$OPTIONS_FILE could not be read, using defaults: ${failure.message}")
            DatasetOptions()
        }
    }

    /**
     * Every league tier every configuration file under the installation
     * describes, concatenated in file order.
     *
     * File order is meaningful, not incidental: the pyramid generator of
     * section 1.9 resolves a (country, division) pair to the first entry
     * that matches, exactly as the original concatenates its own
     * configuration list. Reordering these on the way in would silently
     * change which entry wins.
     *
     * An installation with no configuration files at all is not an error -
     * the pyramid generator falls back to the built in defaults of section
     * 1.9 for every country - so this is recorded only as information.
     */
    private fun readLeagues(root: File, notes: ImportNotes): List<LeagueConfigEntry> {
        val directory = File(root, LEAGUES_DIRECTORY)
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(LEAGUE_SUFFIX) }
            ?.sortedBy { it.name }
        if (files.isNullOrEmpty()) {
            notes.note(
                "no league configuration under $LEAGUES_DIRECTORY; worlds generated from this " +
                    "dataset will use the embedded defaults of section 1.9",
            )
            return emptyList()
        }
        return files.flatMap { file ->
            try {
                LeagueConfigReader.read(file.readBytes())
            } catch (failure: Exception) {
                notes.note("${file.name} could not be read: ${failure.message}")
                emptyList()
            }
        }
    }

    /**
     * Every country the clubs and their players refer to.
     *
     * Level and continent come from the embedded table of section 4.4.1
     * whenever a country's index is one of the 224 the original ships. That
     * covers every real country a distributed file can reference, so the
     * fallback below is for corrupt or out of range data rather than the
     * normal case.
     *
     * A country whose index falls outside the table is rated by the
     * strongest club it holds instead, the only signal the data offers for
     * such an index. A country nobody's club plays in either gets the
     * fallback level, since there is no club to rate it by; the fallback
     * sits above the threshold at which section 4.4 begins penalising, so
     * an unrateable country leaves strength unscaled rather than quietly
     * weakening a squad. A table continent of -1, which only the three
     * reserved indices of 4.4.1 carry, is also treated as unknown, so it can
     * never grant a European exemption.
     */
    @SpecRef("4.4.1")
    private fun countries(clubs: List<ClubEntry>, notes: ImportNotes): List<CountryEntry> {
        val strongestClub = HashMap<Int, Int>()
        val referenced = sortedSetOf<Int>()
        val codes = HashMap<Int, String>()

        for (club in clubs) {
            referenced.add(club.country)
            referenced.add(club.coachCountry)
            club.squad.forEach { referenced.add(it.country) }

            val best = strongestClub[club.country]
            if (best == null || club.level > best) {
                strongestClub[club.country] = club.level
            }
            val code = club.ref.substringAfterLast(REF_SEPARATOR, "")
            if (code.isNotBlank()) {
                codes.putIfAbsent(club.country, code)
            }
        }

        for (index in referenced) {
            if (index !in CountryTable.rows) {
                notes.note(
                    "country $index is outside the embedded table, level derived from its " +
                        "strongest club",
                )
            }
        }
        notes.note(
            "country names are the file suffixes the data uses, not real names, because the " +
                "name table ships inside the game rather than beside it",
        )

        return referenced.map { index ->
            val row = CountryTable.rows[index]
            CountryEntry(
                index = index,
                name = codes[index]?.uppercase() ?: "$UNKNOWN_PREFIX$index",
                level = row?.level ?: strongestClub[index] ?: UNRATEABLE_COUNTRY_LEVEL,
                continent = row?.continent?.takeIf { it >= 0 } ?: UNKNOWN_CONTINENT,
                majorLeague = index in MAJOR_LEAGUE_COUNTRIES,
            )
        }
    }

    private const val TEAMS_DIRECTORY = "teams"
    private const val TEAM_SUFFIX = ".ban"
    private const val LEAGUES_DIRECTORY = "conf_ligas_nacionais"
    private const val LEAGUE_SUFFIX = ".cfg"

    private const val OPTIONS_FILE = "options.bcf"
    private const val REF_SEPARATOR = '_'
    private const val UNKNOWN_PREFIX = "pais "

    /** One above the level at which section 4.4 begins scaling a country down. */
    @SpecRef("4.4")
    const val UNRATEABLE_COUNTRY_LEVEL = 14

    /**
     * The five countries section 4.8 pays more in.
     *
     * The spec names them and publishes the numeric index of only four. The
     * fifth, England at ninety seven, comes from the data: the file naming
     * convention tags every club with its country, and the indices of the
     * tagged countries run in alphabetical order, which places ninety seven
     * exactly where England belongs between France and Italy. See
     * OPEN-QUESTIONS item 21. The embedded table of 4.4.1 confirms it
     * doubly: index 97 is ING, level 20.
     */
    @SpecRef("4.8")
    val MAJOR_LEAGUE_COUNTRIES = setOf(3, 65, 72, 97, 104)

    /** Anything but Europe, so an unfilled table grants no European exemptions. */
    @SpecRef("3.3")
    const val UNKNOWN_CONTINENT = 1
}
