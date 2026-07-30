package org.hedgewars.android.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Byte-exactness is the whole point of a demo: the engine replays the stream
 * as commands, so a single dropped or added byte desynchronizes the match.
 */
class DemoRecorderTest {

    private fun frame(payload: ByteArray) = byteArrayOf(payload.size.toByte()) + payload

    @Test
    fun `frontend bytes are stored verbatim`() {
        val rec = DemoRecorder()
        val config = EngineProtocol.encodeAll(listOf("TL", "etheme Nature"))
        rec.onFrontendBytes(config)
        rec.onFrontendBytes(EngineProtocol.encode("!"))
        assertArrayEquals(config + EngineProtocol.encode("!"), rec.snapshot())
    }

    @Test
    fun `engine frames are re-framed with their length byte`() {
        val rec = DemoRecorder()
        // A synced command with its two trailing tick bytes, as the engine
        // sends them (uIO.pas SendIPC).
        val walk = byteArrayOf('L'.code.toByte(), 0x2C, 0x01)
        rec.onEngineFrame(walk)
        assertArrayEquals(frame(walk), rec.snapshot())
    }

    @Test
    fun `weapon slot commands are not mistaken for control frames`() {
        val rec = DemoRecorder()
        // 0x80 + slot: negative as a Kotlin Byte. Comparing signed would make
        // this look like anything but a game command.
        val slot = byteArrayOf(0x85.toByte(), 0x10, 0x00)
        rec.onEngineFrame(slot)
        assertEquals(-123, slot[0].toInt())
        assertArrayEquals(frame(slot), rec.snapshot())
    }

    @Test
    fun `control frames are excluded`() {
        val rec = DemoRecorder()
        for (kind in "?CEiQqmHsbVvW~") {
            rec.onEngineFrame(byteArrayOf(kind.code.toByte(), 0x41))
        }
        assertNull(rec.snapshot())
    }

    @Test
    fun `a 255 byte frame keeps its length byte`() {
        val rec = DemoRecorder()
        val payload = ByteArray(255) { if (it == 0) '+'.code.toByte() else 0x7A }
        rec.onEngineFrame(payload)
        val out = rec.snapshot()!!
        assertEquals(256, out.size)
        assertEquals(255, out[0].toInt() and 0xFF)
    }

    @Test
    fun `invalidate throws the recording away for good`() {
        val rec = DemoRecorder()
        rec.onEngineFrame(byteArrayOf('L'.code.toByte()))
        rec.invalidate()
        rec.onEngineFrame(byteArrayOf('R'.code.toByte()))
        rec.onFrontendBytes(EngineProtocol.encode("!"))
        assertNull(rec.snapshot())
    }

    @Test
    fun `passing the size cap invalidates instead of growing`() {
        val rec = DemoRecorder(limitBytes = 8)
        rec.onEngineFrame(byteArrayOf('L'.code.toByte()))
        rec.onFrontendBytes(ByteArray(64))
        assertNull(rec.snapshot())
    }

    @Test
    fun `an empty recording yields nothing`() {
        assertNull(DemoRecorder().snapshot())
    }
}
