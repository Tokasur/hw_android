package org.hedgewars.android.engine

import android.util.Log
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Frontend side of the engine IPC session for one game.
 *
 * Owns a loopback [ServerSocket]; the engine is launched with
 * `--internal --port <port>` and connects back. Message semantics mirror
 * QTfrontend/game.cpp ParseMessage.
 *
 * @param recorder when set, every byte of the session is recorded as a demo.
 * @param rawConfig when set, the engine gets these bytes instead of a
 *   serialized config — that is how a recorded demo is played back: the stream
 *   already contains the config it was recorded with, plus the whole match.
 */
class GameConnection(
    private val configCommands: () -> List<String>,
    private val listener: Listener,
    private val varStore: MissionVarStore? = null,
    private val recorder: DemoRecorder? = null,
    private val rawConfig: (() -> ByteArray)? = null,
) : Closeable {

    interface Listener {
        /** Engine reported a fatal error. */
        fun onEngineError(message: String) {}

        /** Game finished normally (q) or was interrupted (Q). */
        fun onGameFinished(interrupted: Boolean) {}

        /** Game stats line (i). */
        fun onStats(kind: Char, text: String) {}

        /**
         * The engine closed the connection without reporting anything — no
         * 'q', no 'Q', no error. For a normal match that is a crash; for a
         * replay it is simply the end of the recording (uGame.pas: "End of
         * input, halting now" exits without the usual end-of-match messages).
         */
        fun onEngineGone() {}
    }

    /** Persistence for campaign (V) and mission (v) variables. */
    interface MissionVarStore {
        fun getVar(campaign: Boolean, name: String): String
        fun setVar(campaign: Boolean, name: String, value: String)
    }

    // Bind the IPv4 loopback explicitly: the engine (uIO.pas) connects to
    // "127.0.0.1", so a server bound to the IPv6 loopback (::1, what
    // getLoopbackAddress() may return) would refuse the connection.
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1)
    }
    val port: Int get() = server.localPort

    @Volatile
    private var socket: Socket? = null

    /** Connected stream for [send]; the IPC thread also writes its replies. */
    @Volatile
    private var sendStream: java.io.OutputStream? = null
    private val writeLock = Any()

    private val thread = Thread({ run() }, "hw-game-ipc")

    fun start() {
        server.soTimeout = ACCEPT_TIMEOUT_MS
        thread.start()
    }

    /**
     * Sends one command to the running engine — e.g. "eforcequit", the same
     * clean abort the desktop frontend sends (QTfrontend/game.cpp
     * HWGame::abort). Returns false when the engine is not connected (yet,
     * or anymore). Does socket I/O: never call from the main thread.
     *
     * Deliberately NOT recorded into the demo: these are this player's session
     * controls (pause, quit), not match events, and replaying a "eforcequit"
     * would end the replay where the recording session happened to be paused.
     */
    fun send(command: String): Boolean {
        val out = sendStream ?: return false
        return runCatching {
            synchronized(writeLock) { EngineProtocol.write(out, command) }
        }.isSuccess
    }

    private fun run() {
        var interrupted = false
        var finished = false
        var errored = false
        try {
            val client = server.accept()
            socket = client
            client.tcpNoDelay = true
            val input = client.getInputStream()
            val output = client.getOutputStream()
            sendStream = output

            while (true) {
                val msg = EngineProtocol.read(input) ?: break
                if (msg.isEmpty()) continue
                // The demo is the whole session: record before interpreting
                // (the recorder keeps the match commands and drops the frames
                // handled below).
                recorder?.onEngineFrame(msg)
                when (msg[0].toInt().toChar()) {
                    '?' -> writeRaw(output, EngineProtocol.encode("!"))
                    'C' -> {
                        val raw = rawConfig
                        val bytes = if (raw != null) {
                            // Replay: the demo stream stands in for the config.
                            // The engine sends 'C' before its ping, so these
                            // bytes are read as commands before the pong.
                            raw().also { Log.i(TAG, "replaying ${it.size} recorded bytes") }
                        } else {
                            val cfg = configCommands()
                            Log.i(TAG, "engine asked for config; sending ${cfg.size} commands")
                            EngineProtocol.encodeAll(cfg)
                        }
                        writeRaw(output, bytes)
                    }
                    // An error after the game already reported its end (q/Q)
                    // is teardown noise, not a match failure — the outcome
                    // must stay "ok" so the menu doesn't show a crash dialog.
                    'E' -> if (!finished && !interrupted) {
                        errored = true
                        listener.onEngineError(trimmedText(msg))
                    } else {
                        Log.w(TAG, "post-quit engine error ignored: ${trimmedText(msg)}")
                    }
                    'i' -> if (msg.size >= 2) {
                        listener.onStats(msg[1].toInt().toChar(), String(msg, 2, msg.size - 2, Charsets.UTF_8))
                    }
                    'q' -> { finished = true; listener.onGameFinished(false) }
                    'Q' -> { interrupted = true; listener.onGameFinished(true) }
                    'H' -> Log.i(TAG, "engine halted")
                    's', 'b' -> Log.d(TAG, "chat: ${trimmedText(msg)}")
                    'W' -> Log.d(TAG, "resolution change ignored on Android")
                    '~' -> Log.d(TAG, "console: ${trimmedText(msg)}")
                    // The engine declares the recording worthless (a /lua
                    // console command was used), exactly as the desktop's
                    // SetDemoPresence(false).
                    'm' -> {
                        Log.d(TAG, "demo presence off")
                        recorder?.invalidate()
                    }
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
            sendStream = null
            if (!finished && !interrupted && !errored) listener.onEngineGone()
            close()
        }
    }

    /**
     * Writes ready-made bytes to the engine and, when recording, stores them:
     * everything the frontend says is part of the demo (desktop equivalent:
     * TCPBase::RawSendIPC appending to its own demo buffer).
     */
    private fun writeRaw(output: java.io.OutputStream, bytes: ByteArray) {
        synchronized(writeLock) {
            output.write(bytes)
            output.flush()
        }
        recorder?.onFrontendBytes(bytes)
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
                writeRaw(output, EngineProtocol.encode(prefix + value))
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
