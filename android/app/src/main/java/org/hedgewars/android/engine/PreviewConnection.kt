package org.hedgewars.android.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import org.hedgewars.android.config.MapChoice
import org.hedgewars.android.config.MapGen

/**
 * IPC peer for `--landpreview` runs (QTfrontend/net/hwmap.cpp).
 *
 * Unlike a game session, the frontend sends the generation parameters
 * immediately when the engine connects (the trailing "!" doubles as the pong
 * the engine waits for), then reads the raw, unframed preview: 4096 bytes of
 * 256x128 1-bit landscape plus one byte with the hedgehog limit.
 */
class PreviewConnection(
    private val seed: String,
    private val map: MapChoice.Generated,
) : Closeable {

    data class Preview(val bitmap: Bitmap, val hogLimit: Int)

    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort

    /** Blocks until the engine delivered the preview (call on a worker thread). */
    fun await(timeoutMs: Int = 30_000): Preview? {
        server.soTimeout = timeoutMs
        try {
            server.accept().use { client ->
                client.soTimeout = timeoutMs
                client.tcpNoDelay = true
                val output = client.getOutputStream()
                val commands = buildList {
                    add("eseed $seed")
                    add("e\$template_filter ${map.templateFilter}")
                    add("e\$mapgen ${map.mapGen.id}")
                    add("e\$feature_size ${map.featureSize}")
                    if (map.mapGen == MapGen.MAZE || map.mapGen == MapGen.PERLIN)
                        add("e\$maze_size ${map.mazeSize}")
                    add("!")
                }
                output.write(EngineProtocol.encodeAll(commands))
                output.flush()

                val raw = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                val input = client.getInputStream()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    raw.write(buf, 0, n)
                }
                return decode(raw.toByteArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "preview failed: $e")
            return null
        } finally {
            close()
        }
    }

    private fun decode(data: ByteArray): Preview? {
        if (data.size != WIDTH * HEIGHT / 8 + 1) {
            Log.w(TAG, "unexpected preview size: ${data.size}")
            return null
        }
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        var i = 0
        for (y in 0 until HEIGHT) {
            for (xb in 0 until WIDTH / 8) {
                val b = data[i++].toInt()
                for (bit in 0 until 8) {
                    val land = (b shr (7 - bit)) and 1 == 1
                    bmp.setPixel(xb * 8 + bit, y, if (land) LAND_COLOR else WATER_COLOR)
                }
            }
        }
        return Preview(bmp, data[data.size - 1].toInt() and 0xff)
    }

    override fun close() {
        runCatching { server.close() }
    }

    companion object {
        private const val TAG = "HWPreview"
        private const val WIDTH = 256
        private const val HEIGHT = 128
        private val LAND_COLOR = Color.rgb(0xF5, 0xD3, 0x5C)
        private val WATER_COLOR = Color.rgb(0x14, 0x2C, 0x4A)
    }
}
