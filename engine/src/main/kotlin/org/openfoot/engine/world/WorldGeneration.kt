package org.openfoot.engine.world

import org.openfoot.dataset.ClubEntry
import org.openfoot.dataset.WorldDataset
import org.openfoot.model.Designated
import org.openfoot.model.Rng
import org.openfoot.model.SeedDomain
import org.openfoot.model.SpecRef
import org.openfoot.model.SplitMix64Rng

/**
 * A club, the squad generated for it, and the designations of section 5.6
 * that club stores alongside it.
 */
data class GeneratedClub(
    val entry: ClubEntry,
    val squad: List<Player>,
    @property:SpecRef("5.6") val designated: Designated,
)

/**
 * A generated world, and the seed it came from.
 *
 * Carrying the seed is not bookkeeping. A bug report in this project is a seed
 * and a command log, so a world that cannot say where it came from cannot be
 * reported.
 */
data class World(
    val seed: Long,
    val clubs: List<GeneratedClub>,
) {
    val playerCount: Int get() = clubs.sumOf { it.squad.size }

    fun club(ref: String): GeneratedClub? = clubs.firstOrNull { it.entry.ref == ref }
}

/**
 * Builds a whole world from a dataset and a seed.
 *
 * A club's stream is derived from its reference rather than from its position
 * in the list, so reordering the dataset, adding a club or removing one leaves
 * every other club generating exactly what it generated before. That is what
 * makes a dataset editable without invalidating every seed anyone has shared.
 *
 * The options come from the dataset rather than from a parameter. A world built
 * with different options is a different world, so which options were in force is
 * a property of the data and not of the call.
 */
@SpecRef("4")
fun generateWorld(dataset: WorldDataset, seed: Long): World {
    val worldRng: Rng = SplitMix64Rng(seed).fork(SeedDomain.WORLDGEN)

    val clubs = dataset.clubs.map { club ->
        val clubRng = worldRng.fork(clubKey(club.ref))
        val squad = generateSquad(club, dataset, dataset.options, clubRng)
        GeneratedClub(
            entry = club,
            squad = squad,
            designated = deriveDesignated(squad, DesignationEnergy.FULL_SQUAD),
        )
    }
    return World(seed = seed, clubs = clubs)
}

/**
 * Turns a club reference into the tag its stream is derived from.
 *
 * The string hash is used because the Java language specification states it
 * exactly, as a sum over the characters, so it is the same number on every
 * implementation and every version rather than merely the same within one run.
 * Anything less specified would make a shared seed reproduce only on the
 * machine that made it, which is the one thing this project cannot allow.
 */
internal fun clubKey(ref: String): Long = ref.hashCode().toLong()
