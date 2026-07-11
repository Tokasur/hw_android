package org.hedgewars.android.engine

import android.util.Log
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Frontend side of the engine IPC session for one game.
 *
 * Owns a loopback [ServerSocket]; the engine is launched with
 * `--internal --port <port>` and connects back. Message semantics mirror
 * QTfrontend/game.cpp ParseMessage.
 */
class GameConnection(
    private val configCommands: () -> List<String>,
    private val listener: Listener,
    private val varStore: MissionVarStore? = null,
) : Closeable {

    interface Listener {
        /** Engine reported a fatal error. */
        fun onEngineError(message: String) {}

        /** Game finished normally (q) or was interrupted (Q). */
        fun onGameFinished(interrupted: Boolean) {}

        /** Game stats line (i). */
        fun onStats(kind: Char, text: String) {}
    }

    /** Persistence for campaign (V) and mission (v) variables. */
    interface MissionVarStore {
        fun getVar(campaign: Boolean, name: String): String
        fun setVar(campaign: Boolean, name: String, value: String)
    }

    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort

    @Volatile
    private var socket: Socket? = null

    private val thread = Thread({ run() }, "hw-game-ipc")

    fun start() {
        server.soTimeout = ACCEPT_TIMEOUT_MS
        thread.start()
    }

    private fun run() {
        try {
            val client = server.accept()
            socket = client
            client.tcpNoDelay = true
            val input = client.getInputStream()
            val output = client.getOutputStream()
            var interrupted = false
            var finished = false

            while (true) {
                val msg = EngineProtocol.read(input) ?: break
                if (msg.isEmpty()) continue
                when (msg[0].toInt().toChar()) {
                    '?' -> EngineProtocol.write(output, "!")
                    'C' -> {
                        val cfg = configCommands()
                        Log.i(TAG, "engine asked for config; sending ${cfg.size} commands")
                        output.write(EngineProtocol.encodeAll(cfg))
                        output.flush()
                    }
                    'E' -> listener.onEngineError(trimmedText(msg))
                    'i' -> if (msg.size >= 2) {
                        listener.onStats(msg[1].toInt().toChar(), String(msg, 2, msg.size - 2, Charsets.UTF_8))
                    }
                    'q' -> { finished = true; listener.onGameFinished(false) }
                    'Q' -> { interrupted = true; listener.onGameFinished(true) }
                    'H' -> Log.i(TAG, "engine halted")
                    's', 'b' -> Log.d(TAG, "chat: ${trimmedText(msg)}")
                    'W' -> Log.d(TAG, "resolution change ignored on Android")
                    '~' -> Log.d(TAG, "console: ${trimmedText(msg)}")
                    'm' -> Log.d(TAG, "demo presence off")
                    'V' -> handleVar(campaign = true, msg, output)
                    'v' -> handleVar(campaign = false, msg, output)
                    else -> {
                        // synced game commands: only relevant for demos/netplay
                    }
                }
                if (finished || interrupted) {
                    // engine closes shortly after; keep draining until EOF
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "IPC loop ended: $e")
        } finally {
            close()
        }
    }

    /** Payload text for messages that carry two trailing filler bytes. */
    private fun trimmedText(msg: ByteArray): String {
        val len = (msg.size - 3).coerceAtLeast(0)
        return String(msg, 1, len, Charsets.UTF_8)
    }

    private fun handleVar(campaign: Boolean, msg: ByteArray, output: java.io.OutputStream) {
        if (msg.size < 2 || varStore == null) return
        val op = msg[1].toInt().toChar()
        val body = String(msg, 2, msg.size - 2, Charsets.UTF_8)
        when (op) {
            '?' -> {
                val value = varStore.getVar(campaign, body)
                val prefix = if (campaign) "V." else "v."
                EngineProtocol.write(output, prefix + value)
            }
            '!' -> {
                val i = body.indexOf(' ')
                if (i > 0) varStore.setVar(campaign, body.substring(0, i), body.substring(i + 1))
            }
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        runCatching { server.close() }
    }

    companion object {
        private const val TAG = "HWGameConnection"
        private const val ACCEPT_TIMEOUT_MS = 60_000
    }
}
