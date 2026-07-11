package org.hedgewars.android.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineProtocolTest {

    @Test
    fun `encode prefixes payload with length byte`() {
        val encoded = EngineProtocol.encode("TL")
        assertArrayEquals(byteArrayOf(2, 'T'.code.toByte(), 'L'.code.toByte()), encoded)
    }

    @Test
    fun `encode handles utf8 multibyte`() {
        val encoded = EngineProtocol.encode("eaddteam héros")
        assertEquals(encoded.size - 1, encoded[0].toInt() and 0xff)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `messages longer than 255 bytes are rejected`() {
        EngineProtocol.encode("x".repeat(256))
    }

    @Test
    fun `255 byte message is allowed`() {
        val encoded = EngineProtocol.encode("x".repeat(255))
        assertEquals(256, encoded.size)
        assertEquals(255, encoded[0].toInt() and 0xff)
    }

    @Test
    fun `encodeAll concatenates frames`() {
        val bytes = EngineProtocol.encodeAll(listOf("TL", "!"))
        assertArrayEquals(
            byteArrayOf(2, 'T'.code.toByte(), 'L'.code.toByte(), 1, '!'.code.toByte()),
            bytes,
        )
    }

    @Test
    fun `read returns messages then null on EOF`() {
        val stream = ByteArrayInputStream(EngineProtocol.encodeAll(listOf("C", "?")))
        assertArrayEquals(byteArrayOf('C'.code.toByte()), EngineProtocol.read(stream))
        assertArrayEquals(byteArrayOf('?'.code.toByte()), EngineProtocol.read(stream))
        assertNull(EngineProtocol.read(stream))
    }

    @Test
    fun `write then read round-trips`() {
        val out = ByteArrayOutputStream()
        EngineProtocol.write(out, "eseed {test}")
        val msg = EngineProtocol.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals("eseed {test}", msg?.toString(Charsets.UTF_8))
    }
}
