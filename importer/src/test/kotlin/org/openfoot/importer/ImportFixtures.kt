package org.openfoot.importer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * Serializable stand ins for the records the game data holds.
 *
 * These carry the same field names a real file does, because those names are
 * what the readers look for. They are this project's own classes and contain
 * nothing from the original; no distributed file is involved, and none can be,
 * since the data is not redistributable and no continuous integration machine
 * has any.
 *
 * They live in one place so the unit tests and the installation test cannot
 * drift apart on what a file looks like.
 */
object ImportFixtures {

    class Squadman(
        val a: String,
        val b: Boolean,
        val c: Int,
        val d: Int,
        val e: Int,
        val f: Int,
        val g: Int,
        val h: Int,
        val i: Int,
        val j: Boolean,
        val hash: Int,
    ) : Serializable

    class Team(
        val a: Int,
        val b: Int,
        val c: Int,
        val d: String,
        val e: String,
        val f: String,
        val g: Int,
        val h: String,
        val i: Int,
        val n: Int,
        val vid: Int,
        val l: ArrayList<Squadman>,
    ) : Serializable

    class Tier(
        val pais: Int,
        val divisao: Int,
        val nTimes: Int,
        val nRebaixados: Int = 0,
        val formula: Int = 0,
        val desempate: Int = 0,
    ) : Serializable

    class Pyramid(val a: ArrayList<Tier>) : Serializable

    class Options(
        val habilidadeIndividual: Boolean,
        val salarioMensal: Boolean,
        val velocidade: Int,
    ) : Serializable

    fun squadman(
        name: String = "Jogador",
        age: Int = 25,
        country: Int = 29,
        position: Int = 3,
        side: Int = 0,
        firstTrait: Int = 11,
        secondTrait: Int = 12,
        status: Int = 1,
        star: Boolean = false,
        topWorld: Boolean = false,
        talent: Int = 6,
    ) = Squadman(
        a = name, b = star, c = country, d = age, e = position, f = status,
        g = firstTrait, h = secondTrait, i = side, j = topWorld, hash = talent,
    )

    fun team(
        ref: String = "clube_bra",
        name: String = "Clube",
        country: Int = 29,
        state: Int = 18,
        level: Int = 18,
        reputation: Int = 4,
        version: Int = 185,
        squad: List<Squadman> = listOf(squadman()),
    ) = Team(
        a = country, b = state, c = level, d = ref, e = name,
        f = "Estadio", g = 45000, h = "Tecnico", i = country, n = reputation,
        vid = version, l = ArrayList(squad),
    )

    fun bytes(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        ObjectOutputStream(out).use { it.writeObject(value) }
        return out.toByteArray()
    }

    /** Lays out a directory shaped like an installation, holding the given files. */
    fun installation(
        root: File,
        teams: List<Team>,
        pyramids: List<Pyramid> = emptyList(),
        options: Options? = null,
    ): File {
        val teamDirectory = File(root, "teams")
        teamDirectory.mkdirs()
        teams.forEach { File(teamDirectory, "${it.d}.ban").writeBytes(bytes(it)) }

        if (pyramids.isNotEmpty()) {
            val leagueDirectory = File(root, "conf_ligas_nacionais")
            leagueDirectory.mkdirs()
            pyramids.forEachIndexed { index, pyramid ->
                File(leagueDirectory, "liga$index.cfg").writeBytes(bytes(pyramid))
            }
        }
        options?.let { File(root, "options.bcf").writeBytes(bytes(it)) }
        return root
    }
}
