package org.hedgewars.android.data

import java.io.File

/**
 * Enumerates missions from the installed Data tree.
 * Script paths are relative to Data/, ready for `escript`.
 */
class MissionsRepository(private val paths: GamePaths) {

    data class Mission(val title: String, val script: String)
    data class Campaign(val name: String, val title: String, val missions: List<Mission>)

    fun training(): List<Mission> = luaMissions("Missions/Training")
    fun challenges(): List<Mission> = luaMissions("Missions/Challenge")
    fun scenarios(): List<Mission> = luaMissions("Missions/Scenario")

    fun campaigns(): List<Campaign> {
        val dir = File(paths.dataDir, "Missions/Campaign")
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
        val dir = File(paths.dataDir, "Maps")
        return dir.listFiles { f -> f.isDirectory && File(f, "map.lua").exists() }
            ?.map { it.name }?.sorted()
            ?: emptyList()
    }

    /** Regular multiplayer maps (preview.png without mission script). */
    fun multiplayerMaps(): List<String> {
        val dir = File(paths.dataDir, "Maps")
        return dir.listFiles { f -> f.isDirectory && !File(f, "map.lua").exists() }
            ?.map { it.name }?.sorted()
            ?: emptyList()
    }

    fun themes(): List<String> {
        val dir = File(paths.dataDir, "Themes")
        return dir.listFiles { f -> f.isDirectory && File(f, "theme.cfg").exists() }
            ?.map { it.name }?.sorted()
            ?: emptyList()
    }

    /** Multiplayer style scripts for local games (Scripts/Multiplayer). */
    fun gameStyles(): List<Mission> {
        val dir = File(paths.dataDir, "Scripts/Multiplayer")
        return dir.listFiles { f -> f.extension == "lua" }
            ?.sortedBy { it.name }
            ?.map { Mission(pretty(it.nameWithoutExtension), "Scripts/Multiplayer/${it.name}") }
            ?: emptyList()
    }

    private fun luaMissions(rel: String): List<Mission> {
        val dir = File(paths.dataDir, rel)
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
