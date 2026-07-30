package org.hedgewars.android.engine

import java.net.Socket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameConnectionSendTest {

    @Test
    fun `send fails before the engine connects and frames commands after`() {
        val conn = GameConnection(
            configCommands = { emptyList() },
            listener = object : GameConnection.Listener {},
        )
        assertFalse(conn.send("eforcequit"))

        conn.start()
        Socket("127.0.0.1", conn.port).use { engine ->
            // The accept thread publishes the stream shortly after connect.
            val deadline = System.currentTimeMillis() + 5_000
            while (!conn.send("eforcequit")) {
                assertTrue("engine never became sendable", System.currentTimeMillis() < deadline)
                Thread.sleep(10)
            }
            val frame = EngineProtocol.read(engine.getInputStream())
            assertEquals("eforcequit", frame?.toString(Charsets.UTF_8))
        }
        conn.close()
        assertFalse(conn.send("eforcequit"))
    }

    @Test
    fun `a replay gets the recorded bytes instead of a config`() {
        val demo = EngineProtocol.encodeAll(listOf("TD", "etheme Nature")) +
            byteArrayOf(0x03, 'L'.code.toByte(), 0x2C, 0x01)
        val conn = GameConnection(
            configCommands = { listOf("TL", "should not be sent") },
            listener = object : GameConnection.Listener {},
            rawConfig = { demo },
        )
        conn.start()
        Socket("127.0.0.1", conn.port).use { engine ->
            val out = engine.getOutputStream()
            val input = engine.getInputStream()
            // The engine's real startup order: ask for the config, then ping.
            EngineProtocol.write(out, "C")
            EngineProtocol.write(out, "?")

            val received = ByteArray(demo.size)
            readFully(input, received)
            assertArrayEquals(demo, received)
            assertEquals("!", EngineProtocol.read(input)?.toString(Charsets.UTF_8))
        }
        conn.close()
    }

    @Test
    fun `recording keeps the config and the pong but not the stat frames`() {
        val recorder = DemoRecorder()
        val stats = mutableListOf<Pair<Char, String>>()
        val conn = GameConnection(
            configCommands = { listOf("TL", "etheme Nature") },
            listener = object : GameConnection.Listener {
                override fun onStats(kind: Char, text: String) {
                    stats += kind to text
                }
            },
            recorder = recorder,
        )
        conn.start()
        Socket("127.0.0.1", conn.port).use { engine ->
            val out = engine.getOutputStream()
            val input = engine.getInputStream()
            EngineProtocol.write(out, "C")
            readFully(input, ByteArray(EngineProtocol.encodeAll(listOf("TL", "etheme Nature")).size))
            EngineProtocol.write(out, "?")
            assertEquals("!", EngineProtocol.read(input)?.toString(Charsets.UTF_8))
            // A synced game command, then a stat frame that must not be kept.
            out.write(byteArrayOf(0x03, 'L'.code.toByte(), 0x2C, 0x01))
            EngineProtocol.write(out, "irAlpha wins!")
            out.flush()

            val deadline = System.currentTimeMillis() + 5_000
            while (stats.isEmpty()) {
                assertTrue("stat frame never arrived", System.currentTimeMillis() < deadline)
                Thread.sleep(10)
            }
        }
        conn.close()

        val expected = EngineProtocol.encodeAll(listOf("TL", "etheme Nature")) +
            EngineProtocol.encode("!") +
            byteArrayOf(0x03, 'L'.code.toByte(), 0x2C, 0x01)
        assertArrayEquals(expected, recorder.snapshot())
        assertEquals(listOf('r' to "Alpha wins!"), stats)
    }

    private fun readFully(input: java.io.InputStream, into: ByteArray) {
        var off = 0
        while (off < into.size) {
            val n = input.read(into, off, into.size - off)
            assertTrue("stream ended early", n > 0)
            off += n
        }
    }
}
