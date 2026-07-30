package org.hedgewars.android.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackContentIndexTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val icon = byteArrayOf(1, 2, 3, 4)

    private fun writePack(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun standardPack(dir: File) = writePack(
        File(dir, "TestPack.hwp"),
        mapOf(
            "Themes/Foo/theme.cfg" to "x".toByteArray(),
            "Themes/Foo/icon.png" to icon,
            "Maps/Bar/preview.png" to icon,
            "Maps/Baz/map.lua" to "-- lua".toByteArray(),
            "Graphics/Hats/zorro.png" to icon,
            "../evil.png" to icon,
        ),
    )

    @Test
    fun `enumerates themes maps and hats from a pack`() {
        standardPack(tmp.root)
        val index = PackContentIndex(tmp.root)
        assertEquals(listOf("Foo"), index.dirsWithFile("Themes/", "theme.cfg"))
        assertEquals(listOf("Bar", "Baz"), index.subdirs("Maps/"))
        assertEquals(listOf("zorro"), index.fileNames("Graphics/Hats/", ".png"))
        assertTrue(index.hasFile("Maps/Baz/map.lua"))
        assertFalse(index.hasFile("Maps/Bar/map.lua"))
    }

    @Test
    fun `reads entries with bounds and safety checks`() {
        standardPack(tmp.root)
        val index = PackContentIndex(tmp.root)
        assertArrayEquals(icon, index.readEntry("Themes/Foo/icon.png"))
        assertNull(index.readEntry("Themes/Foo/absent.png"))
        assertNull(index.readEntry("../evil.png"))
    }

    @Test
    fun `oversized entries are refused`() {
        writePack(
            File(tmp.root, "Big.hwp"),
            mapOf("Themes/Big/icon.png" to ByteArray(5 * 1024 * 1024)),
        )
        assertNull(PackContentIndex(tmp.root).readEntry("Themes/Big/icon.png"))
    }

    @Test
    fun `merges loose directories with pack entries`() {
        standardPack(tmp.root)
        File(tmp.root, "Themes/Loose").mkdirs()
        File(tmp.root, "Themes/Loose/theme.cfg").writeText("x")
        File(tmp.root, "Themes/Loose/icon.png").writeBytes(icon)
        val index = PackContentIndex(tmp.root)
        assertEquals(listOf("Foo", "Loose"), index.dirsWithFile("Themes/", "theme.cfg"))
        assertArrayEquals(icon, index.readEntry("Themes/Loose/icon.png"))
    }

    @Test
    fun `corrupt packs contribute nothing`() {
        File(tmp.root, "Broken.hwp").writeText("{ not a zip")
        assertTrue(PackContentIndex(tmp.root).dirsWithFile("Themes/", "theme.cfg").isEmpty())
    }

    @Test
    fun `cache refreshes when the pack file changes`() {
        val pack = File(tmp.root, "TestPack.hwp")
        writePack(pack, mapOf("Themes/Old/theme.cfg" to "x".toByteArray()))
        val index = PackContentIndex(tmp.root)
        assertEquals(listOf("Old"), index.dirsWithFile("Themes/", "theme.cfg"))
        writePack(
            pack,
            mapOf(
                "Themes/Old/theme.cfg" to "x".toByteArray(),
                "Themes/New/theme.cfg" to "longer content".toByteArray(),
            ),
        )
        assertEquals(listOf("New", "Old"), index.dirsWithFile("Themes/", "theme.cfg"))
    }
}
