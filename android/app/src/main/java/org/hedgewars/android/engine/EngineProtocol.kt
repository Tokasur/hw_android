package org.hedgewars.android.engine

import java.io.InputStream
import java.io.OutputStream

/**
 * Wire format of the engine IPC socket (hedgewars/uIO.pas):
 * every message is one length byte (0..255) followed by that many bytes of
 * UTF-8 payload. There is no 2-byte extension in the Pascal engine — commands
 * longer than 255 bytes are impossible and must be rejected upstream.
 */
object EngineProtocol {
    const val MAX_MESSAGE = 255

    fun encode(message: String): ByteArray {
        val payload = message.toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_MESSAGE) {
            "engine IPC message longer than $MAX_MESSAGE bytes: $message"
        }
        val out = ByteArray(payload.size + 1)
        out[0] = payload.size.toByte()
        payload.copyInto(out, 1)
        return out
    }

    fun encodeAll(messages: List<String>): ByteArray {
        var size = 0
        for (m in messages) size += m.toByteArray(Charsets.UTF_8).size + 1
        val out = ByteArray(size)
        var pos = 0
        for (m in messages) {
            val enc = encode(m)
            enc.copyInto(out, pos)
            pos += enc.size
        }
        return out
    }

    fun write(stream: OutputStream, message: String) {
        stream.write(encode(message))
        stream.flush()
    }

    /**
     * Reads one length-prefixed message. Returns null on EOF.
     * The raw payload is returned; interpretation (and the trailing 2 junk
     * bytes some engine messages carry) is up to the caller.
     */
    fun read(stream: InputStream): ByteArray? {
        val len = stream.read()
        if (len < 0) return null
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = stream.read(buf, off, len - off)
            if (n < 0) return null
            off += n
        }
        return buf
    }
}
