package org.hedgewars.android.data

import java.io.File
import org.hedgewars.android.config.Scheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SchemesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() = SchemesRepository(tmp.root)

    private val custom = Scheme(
        name = "My Rules",
        artillery = true, king = true, randomOrder = false,
        turnTimeSec = 20, minesTimeSec = -1, worldEdge = 2,
        scriptParam = "a=1",
    )

    @Test
    fun `save then get roundtrips every field`() {
        val r = repo()
        r.save(custom)
        assertEquals(custom, r.get("My Rules"))
    }

    @Test
    fun `customs are sorted case-insensitively and corrupt files skipped`() {
        val r = repo()
        r.save(custom.copy(name = "beta"))
        r.save(custom.copy(name = "Alpha"))
        File(tmp.root, "broken.json").writeText("{ not json")
        assertEquals(listOf("Alpha", "beta"), r.customs().map { it.name })
    }

    @Test
    fun `all lists presets then customs`() {
        val r = repo()
        r.save(custom)
        val names = r.all().map { it.name }
        assertEquals(Scheme.PRESETS.map { it.name } + "My Rules", names)
    }

    @Test
    fun `resolve finds presets and customs case-insensitively`() {
        val r = repo()
        r.save(custom)
        assertEquals("Shoppa", r.resolve("shoppa").name)
        assertEquals(custom, r.resolve("my rules"))
    }

    @Test
    fun `resolve falls back to Default for unknown names`() {
        assertEquals(Scheme.DEFAULT, repo().resolve("vanished"))
    }

    @Test
    fun `save clamps out-of-range values`() {
        val r = repo()
        r.save(custom.copy(damagePercent = 9999))
        assertEquals(300, r.get("My Rules")!!.damagePercent)
    }

    @Test
    fun `nameError flags empty and taken names`() {
        val r = repo()
        r.save(custom)
        assertEquals(NameError.EMPTY, r.nameError("   "))
        assertEquals(NameError.TAKEN, r.nameError("default"))
        assertEquals(NameError.TAKEN, r.nameError("MY RULES"))
        assertNull(r.nameError("Fresh"))
        // Unchanged (or case-only changed) while editing stays valid.
        assertNull(r.nameError("my rules", original = "My Rules"))
        assertEquals(NameError.TAKEN, r.nameError("Shoppa", original = "My Rules"))
    }

    @Test
    fun `duplicate picks a free copy name and saves it`() {
        val r = repo()
        val c1 = r.duplicate(Scheme.PRESETS.first())
        assertEquals("Copy of Default", c1.name)
        val c2 = r.duplicate(Scheme.PRESETS.first())
        assertEquals("Copy of Default 2", c2.name)
        assertEquals(c1, r.get("Copy of Default"))
        assertEquals(c2, r.get("Copy of Default 2"))
    }

    @Test
    fun `duplicate uses the caller's localized template`() {
        val r = repo()
        assertEquals("Copie de Default", r.duplicate(Scheme.DEFAULT, "Copie de %s").name)
    }

    @Test
    fun `rename as save-new-delete-old leaves a single file`() {
        val r = repo()
        r.save(custom)
        r.save(custom.copy(name = "Renamed"))
        r.delete("My Rules")
        assertNull(r.get("My Rules"))
        assertEquals(custom.copy(name = "Renamed"), r.get("Renamed"))
        assertEquals(1, tmp.root.listFiles { f: File -> f.extension == "json" }!!.size)
    }

    @Test
    fun `isPreset matches case-insensitively`() {
        assertTrue(repo().isPreset("racer"))
        assertTrue(!repo().isPreset("My Rules"))
    }
}
