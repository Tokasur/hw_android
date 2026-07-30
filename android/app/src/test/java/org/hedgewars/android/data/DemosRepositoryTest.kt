package org.hedgewars.android.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemosRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repo() = DemosRepository(File(folder.root, "Demos"))

    private fun date(text: String) =
        SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).parse(text)!!

    private fun tempWith(bytes: ByteArray): File =
        File(folder.root, "last_demo.hwd").apply { writeBytes(bytes) }

    private fun stream(prefix: String) =
        byteArrayOf(0x02) + prefix.toByteArray() + byteArrayOf(0x03, 0x4C, 0x2C, 0x01)

    @Test
    fun `the leading game type token becomes a demo token`() {
        assertArrayEquals(stream("TD"), DemosRepository.tlToTd(stream("TL")))
    }

    @Test
    fun `only the first occurrence is rewritten`() {
        // The same three bytes appearing later are binary sync payload, not a
        // game-type token — replacing them would corrupt the recording.
        val input = stream("TL") + byteArrayOf(0x02, 'T'.code.toByte(), 'L'.code.toByte())
        val expected = stream("TD") + byteArrayOf(0x02, 'T'.code.toByte(), 'L'.code.toByte())
        assertArrayEquals(expected, DemosRepository.tlToTd(input))
    }

    @Test
    fun `a stream without the token is left alone`() {
        val input = byteArrayOf(0x03, 0x4C, 0x2C, 0x01)
        assertArrayEquals(input, DemosRepository.tlToTd(input))
    }

    @Test
    fun `saving names the file after the timestamp and consumes the temp`() {
        val repo = repo()
        val temp = tempWith(stream("TL"))
        val saved = repo.saveLastDemo(temp, date("2026-07-30_14-05"))!!
        assertEquals("2026-07-30_14-05.60.hwd", saved.name)
        assertArrayEquals(stream("TD"), saved.readBytes())
        assertFalse(temp.exists())
    }

    @Test
    fun `two matches in the same minute both survive`() {
        val repo = repo()
        val at = date("2026-07-30_14-05")
        repo.saveLastDemo(tempWith(stream("TL")), at)
        val second = repo.saveLastDemo(tempWith(stream("TL")), at)!!
        assertEquals("2026-07-30_14-05_2.60.hwd", second.name)
        assertEquals(2, repo.list().size)
    }

    @Test
    fun `saving nothing yields null`() {
        val repo = repo()
        assertNull(repo.saveLastDemo(File(folder.root, "missing.hwd")))
        assertNull(repo.saveLastDemo(tempWith(ByteArray(0))))
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun `listing is newest first and skips foreign files`() {
        val repo = repo()
        val older = repo.saveLastDemo(tempWith(stream("TL")), date("2026-07-28_10-00"))!!
        val newer = repo.saveLastDemo(tempWith(stream("TL")), date("2026-07-30_14-05"))!!
        older.setLastModified(1_000_000)
        newer.setLastModified(2_000_000)
        File(older.parentFile, "notes.txt").writeText("hello")

        val listed = repo.list()
        assertEquals(listOf(newer.name, older.name), listed.map { it.file.name })
        assertEquals("2026-07-30 14-05", listed.first().displayName)
    }

    @Test
    fun `deleting removes the file`() {
        val repo = repo()
        repo.saveLastDemo(tempWith(stream("TL")), date("2026-07-30_14-05"))
        val demo = repo.list().single()
        assertTrue(repo.delete(demo))
        assertTrue(repo.list().isEmpty())
    }

    @Test
    fun `sweeping drops an unsaved recording`() {
        val repo = repo()
        val temp = tempWith(stream("TL"))
        repo.sweepTemp(temp)
        assertFalse(temp.exists())
    }
}
