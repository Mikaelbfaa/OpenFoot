package org.openfoot.importer

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.CountryEntry
import org.openfoot.dataset.DatasetOptions
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
 * silently. Country strength lives in the game's code, out of reach of the clean
 * room rule, so it is derived from the data instead. Country names live in a
 * table that ships inside the game rather than beside it, so the file naming
 * convention supplies a code and the full name is left for a human.
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

        val withDivisions = assignDivisions(clubs, readPyramids(root, notes))
        reportDivisionCoverage(withDivisions, notes)

        return ImportResult(
            dataset = WorldDataset(
                options = readOptions(root, notes),
                countries = countries(withDivisions, notes),
                clubs = withDivisions,
            ),
            notes = notes.notes,
        )
    }

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
    private fun reportDivisionCoverage(clubs: List<ClubEntry>, notes: ImportNotes) {
        val without = clubs.count { it.division == null && !it.nationalTeam }
        if (without > 0) {
            notes.note(
                "$without of ${clubs.size} clubs have no division, because the installation " +
                    "configures a league for only some countries. Those clubs generate on the " +
                    "weakest band of section 4.4, a strength base of one against twenty; when " +
                    "the weekly evolution of section 4.5 lands they will also grow only to the " +
                    "divisionless ceiling of 30 instead of 80 to 100, and decline to the floor " +
                    "of 1 instead of 35",
            )
        }
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

    private fun readPyramids(root: File, notes: ImportNotes): List<DivisionShape> {
        val directory = File(root, LEAGUES_DIRECTORY)
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(LEAGUE_SUFFIX) }
            ?.sortedBy { it.name }
        if (files.isNullOrEmpty()) {
            notes.note(
                "no league configuration under $LEAGUES_DIRECTORY, so no club has a division and " +
                    "every squad will be generated on the weakest band",
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
     * The level is derived rather than read, because the original keeps it in
     * code that the clean room rule puts out of reach. A country is rated by the
     * strongest club it holds, which is the only signal the data offers and a
     * sound one: a federation whose best side is weak is a weak federation. See
     * OPEN-QUESTIONS item 14.
     *
     * The derivation validates itself on the distributed data. The five
     * countries it rates highest are exactly the five that section 4.8 pays
     * more in, which is a table the derivation never sees.
     *
     * A country nobody's club plays in gets the fallback, since there is no club
     * to rate it by. The fallback sits above the threshold at which section 4.4
     * begins penalising, so an unrateable country leaves strength unscaled
     * rather than quietly weakening a squad.
     */
    @SpecRef("4.4")
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

        val unrateable = referenced.count { it !in strongestClub }
        if (unrateable > 0) {
            notes.note(
                "$unrateable of ${referenced.size} countries hold no club, so they cannot be " +
                    "rated and fall back to level $UNRATEABLE_COUNTRY_LEVEL",
            )
        }
        notes.note(
            "country names are the file suffixes the data uses, not real names, because the " +
                "name table ships inside the game rather than beside it",
        )

        return referenced.map { index ->
            CountryEntry(
                index = index,
                name = codes[index]?.uppercase() ?: "$UNKNOWN_PREFIX$index",
                level = strongestClub[index] ?: UNRATEABLE_COUNTRY_LEVEL,
                continent = UNKNOWN_CONTINENT,
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
     * OPEN-QUESTIONS item 21.
     */
    @SpecRef("4.8")
    val MAJOR_LEAGUE_COUNTRIES = setOf(3, 65, 72, 97, 104)

    /** Anything but Europe, so an unfilled table grants no European exemptions. */
    @SpecRef("3.3")
    const val UNKNOWN_CONTINENT = 1
}
