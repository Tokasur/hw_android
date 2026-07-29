package org.hedgewars.android.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PacksRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() = PacksRepository(tmp.root)

    @Test
    fun `install streams to the final file with no part left behind`() = runBlocking {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val progress = mutableListOf<Long>()
        repo().install("Pack.hwp", ByteArrayInputStream(bytes)) { progress += it }
        val written = File(tmp.root, "Pack.hwp")
        assertArrayEquals(bytes, written.readBytes())
        assertTrue(progress.isNotEmpty())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun `reinstall silently overwrites like the desktop`() = runBlocking {
        repo().install("Pack.hwp", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        repo().install("Pack.hwp", ByteArrayInputStream(byteArrayOf(9)))
        assertArrayEquals(byteArrayOf(9), File(tmp.root, "Pack.hwp").readBytes())
    }

    @Test
    fun `failed stream cleans the partial file and rethrows`() {
        val angry = object : InputStream() {
            var served = 0
            override fun read(): Int =
                if (served++ < 10) 42 else throw IOException("network gone")
        }
        var thrown = false
        try {
            runBlocking { repo().install("Pack.hwp", angry) }
        } catch (e: IOException) {
            thrown = true
        }
        assertTrue(thrown)
        assertTrue(tmp.root.listFiles()!!.isEmpty())
    }

    @Test
    fun `installed lists only hwp files sorted case-insensitively`() = runBlocking {
        repo().install("zeta.hwp", ByteArrayInputStream(byteArrayOf(1)))
        repo().install("Alpha.hwp", ByteArrayInputStream(byteArrayOf(1, 2)))
        File(tmp.root, "notes.txt").writeText("x")
        val packs = repo().installed()
        assertEquals(listOf("Alpha.hwp", "zeta.hwp"), packs.map { it.fileName })
        assertEquals(2L, packs[0].sizeBytes)
        assertEquals(setOf("alpha.hwp", "zeta.hwp"), repo().installedFileNames())
    }

    @Test
    fun `display name drops the extension and underscores`() {
        assertEquals(
            "Theme Patagonia v1",
            PacksRepository.InstalledPack("Theme_Patagonia_v1.hwp", 1).displayName,
        )
    }

    @Test
    fun `delete removes the pack and refuses traversal`() = runBlocking {
        repo().install("Pack.hwp", ByteArrayInputStream(byteArrayOf(1)))
        assertTrue(repo().delete("Pack.hwp"))
        assertFalse(File(tmp.root, "Pack.hwp").exists())
        assertFalse(repo().delete("../evil.hwp"))
        assertFalse(repo().delete(""))
    }

    @Test
    fun `sweep removes only stale part files`() {
        File(tmp.root, "old.hwp.part").writeText("junk")
        File(tmp.root, "Keep.hwp").writeText("pack")
        repo().sweepStaleParts()
        assertEquals(listOf("Keep.hwp"), tmp.root.listFiles()!!.map { it.name })
    }

    @Test
    fun `human sizes use sensible units`() {
        assertEquals("500 B", PacksRepository.humanSize(500))
        assertTrue(PacksRepository.humanSize(2048).endsWith(" KB"))
        assertTrue(PacksRepository.humanSize(3 * 1024 * 1024).endsWith(" MB"))
    }
}
