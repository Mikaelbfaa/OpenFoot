package org.openfoot.cli

import org.openfoot.dataset.WorldDataset
import org.openfoot.engine.world.NationalTeam

/**
 * Describes a called up national team in a form that is the same on every
 * run, so two call-ups can be compared with a diff.
 *
 * The men are listed in the order the team stores them, strength descending
 * and then age descending, which is the order section 4.12 leaves the squad
 * in and the order the automatic lineup breaks its ties by; reordering them
 * here would hide the one fact about the list a reader cannot recover. Each
 * line names the club the man was called from, or says he is a free agent
 * the call-up generated.
 */
internal fun describeCallUp(team: NationalTeam, dataset: WorldDataset): String {
    val country = dataset.country(team.country)
    val builder = StringBuilder()

    builder.appendLine("country   ${country?.name ?: team.country}  level ${team.level}  reputation ${team.reputation}")
    builder.appendLine("called    ${team.squad.size}  free agents ${team.freeAgentCount}")
    builder.appendLine("taker     ${team.designated.taker?.let { team.squad[it.value].name } ?: "-"}")

    team.squad.forEachIndexed { index, player ->
        builder.appendLine(
            "  ${player.position.name.padEnd(POSITION_WIDTH)}  ${player.side.name.padEnd(SIDE_WIDTH)}  " +
                "${player.style.name.padEnd(STYLE_WIDTH)}  strength ${player.strength.toString().padStart(3)}  " +
                "age ${player.age}  ${player.name}  ${team.origins[index] ?: FREE_AGENT_LABEL}",
        )
    }

    return builder.toString()
}

private const val POSITION_WIDTH = 10
private const val SIDE_WIDTH = 5
private const val STYLE_WIDTH = 9
private const val FREE_AGENT_LABEL = "free agent"
