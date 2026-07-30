package org.hedgewars.android.engine

import java.io.ByteArrayOutputStream

/**
 * Records one match as a replayable demo, byte for byte.
 *
 * Same recipe as the desktop frontend (QTfrontend/net/tcpBase.cpp
 * RawSendIPC + QTfrontend/game.cpp ParseMessage): everything the FRONTEND
 * writes to the engine goes in verbatim, and every engine frame that is not a
 * frontend-directed control message goes in too — re-prefixed with its length
 * byte, because that is how the engine will read it back.
 *
 * The result is a raw command stream with no header at all; replaying it means
 * handing those bytes to a fresh engine instead of a config (see
 * data/DemosRepository for the "TL" -> "TD" flip that turns a recording into a
 * demo).
 */
class DemoRecorder(private val limitBytes: Int = DEFAULT_LIMIT) {

    private val buffer = ByteArrayOutputStream(64 * 1024)
    private var valid = true

    /** Bytes we sent to the engine — already length-prefixed by the caller. */
    fun onFrontendBytes(bytes: ByteArray) = synchronized(this) {
        if (!valid) return
        append(bytes)
    }

    /**
     * One frame read from the engine, WITHOUT its length byte (that is what
     * [EngineProtocol.read] returns). Frames aimed at the frontend are
     * dropped, everything else — the synced game commands, i.e. the actual
     * match — is stored back in wire format.
     */
    fun onEngineFrame(payload: ByteArray) = synchronized(this) {
        if (!valid || payload.isEmpty()) return
        // Commands like 0x80 + slot are NEGATIVE as Kotlin bytes: compare
        // unsigned, or half the weapon-slot commands look like control frames.
        val kind = (payload[0].toInt() and 0xFF).toChar()
        if (kind in CONTROL_KINDS) return
        append(byteArrayOf(payload.size.toByte()))
        append(payload)
    }

    private fun append(bytes: ByteArray) {
        if (buffer.size() + bytes.size > limitBytes) {
            // A demo this long is a runaway, not a match: stop rather than
            // grow the buffer until the process dies.
            valid = false
            buffer.reset()
            return
        }
        buffer.write(bytes)
    }

    /**
     * Gives up on this recording — the engine told us the demo is worthless
     * ('m', sent when a Lua console command was used) or it grew past the cap.
     */
    fun invalidate() = synchronized(this) {
        valid = false
        buffer.reset()
    }

    /** The recorded stream, or null when there is nothing worth keeping. */
    fun snapshot(): ByteArray? = synchronized(this) {
        if (!valid || buffer.size() == 0) null else buffer.toByteArray()
    }

    companion object {
        /**
         * Engine frames the frontend answers or consumes itself; everything
         * else is match data. Same set as the desktop's ParseMessage cases.
         */
        private const val CONTROL_KINDS = "?CEiQqmHsbVvW~"

        private const val DEFAULT_LIMIT = 16 * 1024 * 1024
    }
}
