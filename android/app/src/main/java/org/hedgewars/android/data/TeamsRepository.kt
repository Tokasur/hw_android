package org.hedgewars.android.data

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hedgewars.android.config.Team

/**
 * Stores user-created teams as JSON files under the app's private storage.
 * Ships two starter teams on first run so a quick game works out of the box.
 */
class TeamsRepository(context: Context) {
    private val dir = File(context.filesDir, "teams").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun list(): List<Team> {
        seedIfEmpty()
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<Team>(it.readText()) }.getOrNull() }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun save(team: Team) {
        File(dir, fileName(team.name)).writeText(json.encodeToString(team))
    }

    fun delete(name: String) {
        File(dir, fileName(name)).delete()
    }

    fun get(name: String): Team? =
        File(dir, fileName(name)).takeIf { it.exists() }
            ?.let { runCatching { json.decodeFromString<Team>(it.readText()) }.getOrNull() }

    private fun seedIfEmpty() {
        if (dir.listFiles()?.any { it.extension == "json" } == true) return
        save(
            Team(
                name = "Hedgehogs",
                hogNames = listOf(
                    "Prickles", "Spike", "Needles", "Quilliam",
                    "Thistle", "Bramble", "Conker", "Chestnut",
                ),
            )
        )
        save(
            Team(
                name = "Robots",
                hogNames = listOf(
                    "Unit 1", "Unit 2", "Unit 3", "Unit 4",
                    "Unit 5", "Unit 6", "Unit 7", "Unit 8",
                ),
                grave = "Rip",
                fort = "Castle",
                difficulty = 3,
            )
        )
    }

    private fun fileName(teamName: String): String =
        teamName.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".json"
}
