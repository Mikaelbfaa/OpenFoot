package org.openfoot.importer

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.LeagueConfigEntry
import org.openfoot.dataset.StateChampionshipEntry
import org.openfoot.model.SpecRef

/**
 * Reads a state championship configuration file.
 *
 * A file holds one entry per configured division of one state, and each
 * entry is nothing but a preset index and two knockout settings: the size,
 * groups, turns and relegated count all come from the preset table of
 * FORMAT-SPEC's formula section, which the season expands. There is no team
 * list in the file, and none is derived here: which clubs play which state
 * division is decided at world creation from the clubs' own state field, by
 * the load rules FORMAT-SPEC gives for these files.
 *
 * The game validates nothing on the way in and indexes its preset table with
 * whatever the file says, so a preset outside the table is a crash at world
 * creation there. Here it is a refusal at read time instead, naming the
 * entry. An entry whose state or division the loader can never look up is
 * dropped rather than kept, which is the same as the inert entry FORMAT-SPEC
 * describes, only visible.
 */
object StateChampionshipReader {

    fun read(bytes: ByteArray): List<StateChampionshipEntry> {
        val root = SerializedStreamReader(bytes).readRoot()
        val entries = root.records(ENTRIES)
        require(entries.isNotEmpty() && entries.all { it.hasFields(STATE, DIVISION, PRESET) }) {
            "this does not look like a state championship configuration, it carries " +
                "${root.fields.keys} holding ${entries.map { it.fields.keys }}"
        }
        return entries.mapNotNull { entry ->
            val state = entry.int(STATE)
            val division = entry.int(DIVISION)
            if (state !in ClubEntry.STATE_RANGE || division !in 1..LeagueConfigEntry.MAX_DIVISION) {
                return@mapNotNull null
            }
            val preset = entry.int(PRESET)
            require(preset in StateChampionshipEntry.PRESET_RANGE) {
                "state $state division $division names preset $preset, and the table runs " +
                    "${StateChampionshipEntry.PRESET_RANGE}"
            }
            val legs = entry.fields[LEGS] as? List<*> ?: emptyList<Any?>()
            require(legs.size == StateChampionshipEntry.KNOCKOUT_ROUNDS) {
                "state $state division $division lists ${legs.size} knockout rounds, and the " +
                    "format always carries ${StateChampionshipEntry.KNOCKOUT_ROUNDS}"
            }
            StateChampionshipEntry(
                state = state,
                division = division,
                preset = preset,
                penaltiesTiebreak = (entry.intOrNull(TIEBREAK) ?: 0) == 0,
                twoLeggedRounds = legs.map { it == TWO_LEGS },
            )
        }
    }

    @SpecRef("FORMAT-SPEC, ces")
    private const val ENTRIES = "a"

    @SpecRef("FORMAT-SPEC, ces")
    private const val STATE = "id"

    @SpecRef("FORMAT-SPEC, ces")
    private const val DIVISION = "divisao"

    @SpecRef("FORMAT-SPEC, formula")
    private const val PRESET = "formula"

    @SpecRef("FORMAT-SPEC, desempate")
    private const val TIEBREAK = "desempate"

    @SpecRef("FORMAT-SPEC, finaisIdaVolta")
    private const val LEGS = "finaisIdaVolta"

    /**
     * The one value that means a two legged round. The editor writes one for
     * a single leg, and the distributed Rio file carries a zero in a position
     * the game never reads; both, and anything else, mean a single leg.
     */
    @SpecRef("FORMAT-SPEC, finaisIdaVolta")
    private const val TWO_LEGS = 2
}
