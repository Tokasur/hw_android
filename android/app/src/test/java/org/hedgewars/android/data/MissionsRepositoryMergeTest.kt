package org.hedgewars.android.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun writePack(name: String, entries: Map<String, String>) {
        ZipOutputStream(File(userDataDir, name).outputStream()).use { zip ->
            for ((e, content) in entries) {
                zip.putNextEntry(ZipEntry(e))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun systemFile(rel: String, content: String = "x") {
        val f = File(dataDir, rel)
        f.parentFile!!.mkdirs()
        f.writeText(content)
    }

    @Test
    fun `themes merge system pack and loose content deduped and sorted`() {
        val r = repo()
        systemFile("Themes/Nature/theme.cfg")
        writePack("P.hwp", mapOf("Themes/Foo/theme.cfg" to "x", "Themes/Nature/theme.cfg" to "x"))
        File(userDataDir, "Themes/Bar").mkdirs()
        File(userDataDir, "Themes/Bar/theme.cfg").writeText("x")
        assertEquals(listOf("Bar", "Foo", "Nature"), r.themes())
    }

    @Test
    fun `pack maps are listed even with a mission script, shipped ones stay hidden`() {
        val r = repo()
        File(dataDir, "Maps/Alpha").mkdirs()
        systemFile("Maps/Basketball/map.lua", "-- shipped mission map")
        writePack("P.hwp", mapOf("Maps/Packy/preview.png" to "x", "Maps/Scripted/map.lua" to "-- lua"))
        assertEquals(listOf("Alpha", "Packy", "Scripted"), r.multiplayerMaps())
    }

    @Test
    fun `forts come from the L images of Data and of packs`() {
        val r = repo()
        systemFile("Forts/PlaneL.png")
        systemFile("Forts/EarthL.png")
        systemFile("Forts/EarthR.png")           // mirrored variant, not a fort
        systemFile("Forts/Castle-icon.png")      // frontend art, not a fort
        systemFile("Forts/L.png")                // no name at all
        writePack(
            "P.hwp",
            mapOf(
                "Forts/RocketfortL.png" to "x",
                "Forts/PlaneL.png" to "x",
                // The engine opens "<name>L.png" verbatim and PhysFS does not
                // fold case, so this one would be fatal to load: don't offer it.
                "Forts/WindowsfortL.PNG" to "x",
            ),
        )
        assertEquals(listOf("Earth", "Plane", "Rocketfort"), r.forts())
    }

    @Test
    fun `no forts installed yields an empty list`() {
        assertEquals(emptyList<String>(), repo().forts())
    }

    @Test
    fun `game styles read their cfg recommendations like the desktop`() {
        val r = repo()
        systemFile("Scripts/Multiplayer/Racer.lua", "-- lua")
        systemFile("Scripts/Multiplayer/Racer.cfg", "Racer\nShoppa")
        systemFile("Scripts/Multiplayer/Gravity.lua", "-- lua")
        systemFile("Scripts/Multiplayer/Gravity.cfg", "*\n*")
        systemFile("Scripts/Multiplayer/Frenzy.lua", "-- lua")
        // Frenzy has no cfg: both recommendations fall back to locked.
        writePack(
            "P.hwp",
            mapOf(
                "Scripts/Multiplayer/Ready_Timer.lua" to "-- lua",
                "Scripts/Multiplayer/Ready_Timer.cfg" to "The_Specialists\n*",
            ),
        )
        val styles = r.gameStyles().associateBy { it.title }
        assertEquals(
            listOf("Frenzy", "Gravity", "Racer", "Ready Timer"),
            r.gameStyles().map { it.title },
        )
        assertEquals("Racer", styles["Racer"]!!.scheme)
        assertEquals("Shoppa", styles["Racer"]!!.weapons)
        assertNull(styles["Gravity"]!!.scheme)
        assertNull(styles["Gravity"]!!.weapons)
        assertEquals(MissionsRepository.GameStyle.LOCKED, styles["Frenzy"]!!.scheme)
        assertEquals(MissionsRepository.GameStyle.LOCKED, styles["Frenzy"]!!.weapons)
        // Pack cfg is read from the zip; "_" becomes a space.
        assertEquals("The Specialists", styles["Ready Timer"]!!.scheme)
        assertNull(styles["Ready Timer"]!!.weapons)
        assertEquals("Scripts/Multiplayer/Ready_Timer.lua", styles["Ready Timer"]!!.script)
    }
}
