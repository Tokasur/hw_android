package org.hedgewars.android.data

import java.io.File
import org.hedgewars.android.config.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TeamsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() = TeamsRepository(File(tmp.root, "teams"))

    /** "Robots" exactly as 0.2.9 seeded it, fort pinned to Castle. */
    private val legacyRobots = """
        {
            "name": "Robots",
            "hogNames": ["Unit 1", "Unit 2", "Unit 3", "Unit 4", "Unit 5", "Unit 6", "Unit 7", "Unit 8"],
            "grave": "Rip",
            "fort": "Castle",
            "difficulty": 3
        }
    """.trimIndent()

    private fun writeTeam(name: String, json: String) {
        val dir = File(tmp.root, "teams").apply { mkdirs() }
        File(dir, "$name.json").writeText(json)
    }

    @Test
    fun `starter teams have no fort of their own so forts vary`() {
        val teams = repo().list()
        assertEquals(listOf("Hedgehogs", "Robots"), teams.map { it.name })
        assertTrue(teams.all { it.fort == Team.FORT_RANDOM })
        assertEquals(3, teams.first { it.name == "Robots" }.difficulty)
    }

    @Test
    fun `the fort pinned by the old starter seed is released`() {
        writeTeam("Robots", legacyRobots)
        val robots = repo().list().single()
        assertEquals(Team.FORT_RANDOM, robots.fort)
        // …and only the fort changed.
        assertEquals("Rip", robots.grave)
        assertEquals(3, robots.difficulty)
        assertEquals("Unit 1", robots.hogNames.first())
    }

    @Test
    fun `a starter team the player edited is left alone`() {
        // Same team, but the player picked a fort (or anything else): not ours
        // to rewrite any more.
        writeTeam("Robots", legacyRobots.replace("\"Castle\"", "\"Lego\""))
        assertEquals("Lego", repo().list().single().fort)

        writeTeam("Robots", legacyRobots.replace("\"Rip\"", "\"Statue\""))
        assertEquals("Castle", repo().list().single().fort)
    }

    @Test
    fun `another team called Robots elsewhere keeps its Castle`() {
        // A team the player created himself that happens to be named Robots but
        // differs from the seed (different hogs) keeps everything.
        writeTeam("Robots", legacyRobots.replace("Unit 1", "Bolt"))
        assertEquals("Castle", repo().list().single().fort)
    }

    @Test
    fun `releasing the pinned fort happens once and survives a reload`() {
        writeTeam("Robots", legacyRobots)
        val repo = repo()
        assertEquals(Team.FORT_RANDOM, repo.list().single().fort)
        // Second pass: the file no longer matches the seed, nothing to do.
        assertEquals(Team.FORT_RANDOM, repo.list().single().fort)
        assertEquals(Team.FORT_RANDOM, repo.get("Robots")!!.fort)
    }

    @Test
    fun `save and get round-trip a team`() {
        val repo = repo()
        val team = Team.default("Les Hérissons", "Hog", difficulty = 2).copy(fort = "Cake")
        repo.save(team)
        assertEquals(team, repo.get("Les Hérissons"))
        repo.delete("Les Hérissons")
        assertEquals(null, repo.get("Les Hérissons"))
    }
}
