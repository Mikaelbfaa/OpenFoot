package org.openfoot.importer

import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.model.SpecRef

/**
 * Reads a national league configuration file.
 *
 * These files say how a country's pyramid is shaped: how many teams per
 * division, how many relegate, how many turns the schedule runs and whether
 * knockout ties go to penalties. There is no team list anywhere in them,
 * and none is derived here either - the pyramid generator of section 1.9
 * (engine module) is the one place that turns a shape plus a country's clubs
 * into division membership, first match by (country, division) winning.
 */
object LeagueConfigReader {

    fun read(bytes: ByteArray): List<LeagueConfigEntry> {
        val root = SerializedStreamReader(bytes).readRoot()
        val tiers = root.records(TIERS)
        require(tiers.isNotEmpty()) {
            "this does not look like a league configuration, it carries ${root.fields.keys}"
        }
        return tiers.mapNotNull { tier ->
            val country = tier.intOrNull(COUNTRY) ?: return@mapNotNull null
            val division = tier.intOrNull(DIVISION) ?: return@mapNotNull null
            if (division !in 1..LeagueConfigEntry.MAX_DIVISION) return@mapNotNull null
            val teamCount = tier.intOrNull(TEAM_COUNT) ?: return@mapNotNull null
            if (teamCount <= 0) return@mapNotNull null

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
        }
    }

    @SpecRef("FORMAT-SPEC, configuracoes")
    private const val TIERS = "a"

    @SpecRef("FORMAT-SPEC, configuracoes")
    private const val COUNTRY = "pais"

    @SpecRef("FORMAT-SPEC, configuracoes")
    private const val DIVISION = "divisao"

    @SpecRef("FORMAT-SPEC, configuracoes")
    private const val TEAM_COUNT = "nTimes"

    @SpecRef("FORMAT-SPEC, configuracoes")
    private const val RELEGATED = "nRebaixados"

    @SpecRef("FORMAT-SPEC, configuracoes")
    private const val FORMULA = "formula"

    @SpecRef("FORMAT-SPEC, configuracoes")
    private const val TIEBREAK = "desempate"
}

/**
 * Turns for a league of a given size, per section 1.3: the formula field of
 * a national league configuration is really the turn count, and formula 2
 * or 3 overrides the default for leagues of ten, twelve or fourteen teams.
 */
@SpecRef("1.3")
internal fun resolveTurns(teamCount: Int, formula: Int): Int {
    val default = when (teamCount) {
        8, 10 -> 4
        12, 14 -> 3
        26, 28, 30, 36 -> 1
        else -> 2
    }
    return if (teamCount in listOf(10, 12, 14) && formula in 2..3) formula else default
}

@SpecRef("1.9")
private const val SMALL_LEAGUE_TEAMS = 10

@SpecRef("1.9")
private const val SMALL_LEAGUE_RELEGATED_CAP = 2
