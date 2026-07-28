package org.hedgewars.android.engine

import java.net.Socket
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
}
