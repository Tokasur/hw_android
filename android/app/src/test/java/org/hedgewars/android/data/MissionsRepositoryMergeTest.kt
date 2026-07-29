package org.hedgewars.android.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MissionsRepositoryMergeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dataDir: File
    private lateinit var userDataDir: File

    private fun repo(): MissionsRepository {
        if (!::dataDir.isInitialized) {
            dataDir = tmp.newFolder("Data")
            userDataDir = tmp.newFolder("user")
        }
        return MissionsRepository(dataDir, userDataDir)
    }

    private fun writePack(name: String, entries: List<String>) {
        ZipOutputStream(File(userDataDir, name).outputStream()).use { zip ->
            for (e in entries) {
                zip.putNextEntry(ZipEntry(e))
                zip.write("x".toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun systemDir(rel: String, marker: String? = null) {
        val d = File(dataDir, rel)
        d.mkdirs()
        marker?.let { File(d, it).writeText("x") }
    }

    @Test
    fun `themes merge system pack and loose content deduped and sorted`() {
        val r = repo()
        systemDir("Themes/Nature", "theme.cfg")
        writePack("P.hwp", listOf("Themes/Foo/theme.cfg", "Themes/Nature/theme.cfg"))
        File(userDataDir, "Themes/Bar").mkdirs()
        File(userDataDir, "Themes/Bar/theme.cfg").writeText("x")
        assertEquals(listOf("Bar", "Foo", "Nature"), r.themes())
    }

    @Test
    fun `pack maps with a mission script stay out of multiplayer maps`() {
        val r = repo()
        systemDir("Maps/Alpha")
        writePack("P.hwp", listOf("Maps/Packy/preview.png", "Maps/Scripted/map.lua"))
        assertEquals(listOf("Alpha", "Packy"), r.multiplayerMaps())
    }

    @Test
    fun `game styles merge packs and sort by pretty title`() {
        val r = repo()
        systemDir("Scripts/Multiplayer")
        File(dataDir, "Scripts/Multiplayer/WxW.lua").writeText("-- lua")
        writePack("P.hwp", listOf("Scripts/Multiplayer/Ready_Timer.lua"))
        val styles = r.gameStyles()
        assertEquals(listOf("Ready Timer", "WxW"), styles.map { it.title })
        assertEquals("Scripts/Multiplayer/Ready_Timer.lua", styles[0].script)
    }
}
