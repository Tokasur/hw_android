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
class TeamsRepository(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "teams"))

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        dir.mkdirs()
    }

    fun list(): List<Team> {
        seedIfEmpty()
        unpinStarterFort()
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
                hogNames = HEDGEHOG_NAMES,
            )
        )
        save(
            Team(
                name = "Robots",
                hogNames = ROBOT_NAMES,
                grave = "Rip",
                // No fort on purpose: both starter teams keep the "random"
                // default, so forts vary from one match to the next (the
                // desktop rolls a random fort for its starter teams too).
                difficulty = 3,
            )
        )
    }

    /**
     * Frees the starter CPU team from the fort the old seed pinned on it.
     *
     * Up to 0.2.9 the seed wrote `fort = "Castle"` on "Robots" — a value the
     * player never chose — which is why forts maps always showed the same two
     * forts. The team is only rewritten while it still matches that seed byte
     * for byte, so a fort (or anything else) the player did pick is never
     * touched, and once rewritten it no longer matches.
     */
    private fun unpinStarterFort() {
        val file = File(dir, fileName(SEEDED_ROBOTS.name))
        val stored = runCatching { json.decodeFromString<Team>(file.readText()) }.getOrNull()
        if (stored == SEEDED_ROBOTS) save(stored.copy(fort = Team.FORT_RANDOM))
    }

    private fun fileName(teamName: String): String =
        teamName.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".json"

    private companion object {
        val HEDGEHOG_NAMES = listOf(
            "Prickles", "Spike", "Needles", "Quilliam",
            "Thistle", "Bramble", "Conker", "Chestnut",
        )
        val ROBOT_NAMES = listOf(
            "Unit 1", "Unit 2", "Unit 3", "Unit 4",
            "Unit 5", "Unit 6", "Unit 7", "Unit 8",
        )

        /** The starter CPU team exactly as 0.2.9 wrote it. */
        val SEEDED_ROBOTS = Team(
            name = "Robots",
            hogNames = ROBOT_NAMES,
            grave = "Rip",
            fort = "Castle",
            difficulty = 3,
        )
    }
}
