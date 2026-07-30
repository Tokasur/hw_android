package org.hedgewars.android.data

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Network side of the DLC screen: fetches the community catalog page and
 * opens pack downloads. Plain HttpURLConnection, HTTPS only, no extra
 * dependencies. All methods do blocking I/O — call on Dispatchers.IO.
 */
class CatalogRepository(
    private val catalogUrl: String = "https://hedgewars.org/content.html",
) {

    class Download(
        val stream: InputStream,
        /** Content-Length, or -1 when the server does not say. */
        val totalBytes: Long,
        private val connection: HttpURLConnection,
    ) : Closeable {
        override fun close() {
            runCatching { stream.close() }
            connection.disconnect()
        }
    }

    /** Fetches and parses the catalog. Throws IOException on any failure. */
    fun fetchCatalog(): List<DlcCategory> {
        val conn = open(catalogUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("catalog HTTP ${conn.responseCode}")
            }
            val bytes = conn.inputStream.use { it.readBounded(MAX_CATALOG_BYTES) }
            return DlcCatalogParser.parse(String(bytes, Charsets.UTF_8))
        } finally {
            conn.disconnect()
        }
    }

    /** Opens a pack download; the caller must [Download.close] it. */
    fun openDownload(entry: DlcEntry): Download {
        val url = DlcCatalogParser.resolveDownloadUrl(entry.href)
            ?: throw IOException("invalid pack URL: ${entry.href}")
        val conn = open(url)
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("download HTTP ${conn.responseCode}")
        }
        return Download(conn.inputStream, conn.contentLengthLong, conn)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Hedgewars-Android")
        }

    companion object {
        /** The catalog page is ~25 KB today; anything past this is not it. */
        private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
    }
}

/** Reads at most [max] bytes (truncating past that), never the whole heap. */
private fun InputStream.readBounded(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
