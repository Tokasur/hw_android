package org.hedgewars.android.data

import java.io.File
import org.hedgewars.android.config.Team

/**
 * Enumerates game content from the installed Data tree, merged with what
 * downloaded packs add (see [PackContentIndex]) for the lists players pick
 * from: themes, multiplayer maps and game styles. Script paths are relative
 * to Data/, ready for `escript`.
 */
class MissionsRepository(private val dataDir: File, userDataDir: File) {
    constructor(paths: GamePaths) : this(paths.dataDir, paths.userDataDir)

    private val packs by lazy { PackContentIndex(userDataDir) }

    data class Mission(val title: String, val script: String)
    data class Campaign(val name: String, val title: String, val missions: List<Mission>)

    fun training(): List<Mission> = luaMissions("Missions/Training")
    fun challenges(): List<Mission> = luaMissions("Missions/Challenge")
    fun scenarios(): List<Mission> = luaMissions("Missions/Scenario")

    fun campaigns(): List<Campaign> {
        val dir = File(dataDir, "Missions/Campaign")
        return dir.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?.map { c ->
                Campaign(
                    name = c.name,
                    title = pretty(c.name),
                    missions = c.listFiles { f -> f.extension == "lua" }
                        ?.sortedBy { it.name }
                        ?.map { Mission(pretty(it.nameWithoutExtension), "Missions/Campaign/${c.name}/${it.name}") }
                        ?: emptyList(),
                )
            }
            ?: emptyList()
    }

    /** Maps with their own mission script (Data/Maps with map.lua). */
    fun missionMaps(): List<String> {
        val dir = File(dataDir, "Maps")
        return dir.listFiles { f -> f.isDirectory && File(f, "map.lua").exists() }
            ?.map { it.name }?.sorted()
            ?: emptyList()
    }

    /**
     * Maps offered in game setup. Shipped mission maps (map.lua) stay hidden
     * as always — but a downloaded pack map is listed even when scripted
     * (StarRace &co): the player installed it to play it, and the engine
     * runs its script in a local match just like the desktop does.
     */
    fun multiplayerMaps(): List<String> {
        val dir = File(dataDir, "Maps")
        val system = dir.listFiles { f -> f.isDirectory && !File(f, "map.lua").exists() }
            ?.map { it.name }
            ?: emptyList()
        return (system + packs.subdirs("Maps/")).distinct().sorted()
    }

    fun themes(): List<String> {
        val dir = File(dataDir, "Themes")
        val system = dir.listFiles { f -> f.isDirectory && File(f, "theme.cfg").exists() }
            ?.map { it.name }
            ?: emptyList()
        return (system + packs.dirsWithFile("Themes/", "theme.cfg")).distinct().sorted()
    }

    /**
     * Fort names teams can use: Data/Forts holds one `<name>L.png` per fort
     * (plus an optional mirrored `R`), and packs can add more — the engine
     * mounts them at the same virtual path, so a pack fort is a valid choice.
     */
    fun forts(): List<String> {
        val system = File(dataDir, "Forts").listFiles { f -> f.isFile && f.name.endsWith("L.png") }
            ?.map { it.name.removeSuffix("L.png") }
            ?: emptyList()
        // Pack entries are matched case-insensitively, but the engine only ever
        // opens "<name>L.png" and PhysFS does not fold case: a pack shipping
        // "…L.PNG" would offer a fort that is fatal to load, so drop those.
        val fromPacks = packs.fileNames("Forts/", "L.png")
            .filter { packs.hasFile("Forts/${it}L.png") }
        return (system + fromPacks)
            .filter { it.isNotEmpty() && it != Team.FORT_RANDOM }
            .distinct()
            .sorted()
    }

    /**
     * A multiplayer style script plus the recommendations of its sibling
     * .cfg (line 1 scheme, line 2 weapons — GameStyleModel.cpp rules):
     * null = free choice ("*"), [GameStyle.LOCKED] = force Default, any
     * other value = force that scheme/weapon set. Missing file or line
     * means locked, like the desktop.
     */
    data class GameStyle(
        val title: String,
        val script: String,
        val scheme: String?,
        val weapons: String?,
    ) {
        companion object {
            const val LOCKED = "locked"
        }
    }

    /** Multiplayer style scripts for local games (Scripts/Multiplayer). */
    fun gameStyles(): List<GameStyle> {
        val system = File(dataDir, "Scripts/Multiplayer")
            .listFiles { f -> f.extension == "lua" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
        val fromPacks = packs.fileNames("Scripts/Multiplayer/", ".lua")
        return (system + fromPacks).distinct()
            .map { base ->
                val (scheme, weapons) = styleCfg(base)
                GameStyle(pretty(base), "Scripts/Multiplayer/$base.lua", scheme, weapons)
            }
            .sortedBy { it.title.lowercase() }
    }

    private fun styleCfg(base: String): Pair<String?, String?> {
        val rel = "Scripts/Multiplayer/$base.cfg"
        val text = File(dataDir, rel).takeIf { it.isFile }?.readText()
            ?: packs.readEntry(rel)?.toString(Charsets.UTF_8)
        val lines = text?.lines()?.map { it.trim().replace('_', ' ') }.orEmpty()
        fun norm(i: Int): String? {
            val v = lines.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: return GameStyle.LOCKED
            return if (v == "*") null else v
        }
        return norm(0) to norm(1)
    }

    private fun luaMissions(rel: String): List<Mission> {
        val dir = File(dataDir, rel)
        val order = File(dir, "order.cfg").takeIf { it.exists() }
            ?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }
        val files = dir.listFiles { f -> f.extension == "lua" }?.toList() ?: emptyList()
        val sorted = if (order != null) {
            files.sortedBy { f ->
                val i = order.indexOf(f.nameWithoutExtension)
                if (i >= 0) i else order.size + files.indexOf(f)
            }
        } else files.sortedBy { it.name }
        return sorted.map { Mission(pretty(it.nameWithoutExtension), "$rel/${it.name}") }
    }

    private fun pretty(fileName: String): String =
        fileName.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
}
