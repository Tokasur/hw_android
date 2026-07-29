package org.hedgewars.android.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Installed DLC packs: the *.hwp files sitting in the engine's user data
 * directory. The engine auto-mounts every one of them at startup
 * (uPhysFSLayer.pas + hwpacksmounter.c), and each match runs a fresh engine
 * process — so installing is just writing the file, deleting just deletes
 * it, and both take effect at the next match. This directory survives app
 * updates (DataInstaller only wipes filesDir/Data).
 */
class PacksRepository(private val dir: File) {
    constructor(context: Context) : this(GamePaths(context).userDataDir)

    init {
        dir.mkdirs()
    }

    data class InstalledPack(val fileName: String, val sizeBytes: Long) {
        val displayName: String get() = fileName.removeSuffix(".hwp").replace('_', ' ')
    }

    fun installed(): List<InstalledPack> =
        dir.listFiles { f -> f.isFile && f.extension.equals("hwp", ignoreCase = true) }
            ?.map { InstalledPack(it.name, it.length()) }
            ?.sortedBy { it.fileName.lowercase() }
            ?: emptyList()

    /** Lowercased names, for the catalog's "Installed" chip state. */
    fun installedFileNames(): Set<String> = installed().map { it.fileName.lowercase() }.toSet()

    /**
     * Streams a pack to disk: written to "<name>.part" (64 KiB buffer,
     * cancellation-aware), then atomically renamed over any existing file
     * (silent overwrite — the desktop behaves the same). On any failure or
     * cancellation the partial file is removed and the cause rethrown.
     */
    suspend fun install(finalName: String, input: InputStream, onBytes: (Long) -> Unit = {}): File {
        require(isSafeName(finalName) && finalName.endsWith(".hwp", ignoreCase = true)) {
            "unsafe pack name: $finalName"
        }
        val part = File(dir, "$finalName$PART_SUFFIX")
        val final = File(dir, finalName)
        try {
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onBytes(total)
                }
            }
            final.delete()
            if (!part.renameTo(final)) throw IOException("could not finalize $finalName")
            return final
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    fun delete(fileName: String): Boolean =
        isSafeName(fileName) && File(dir, fileName).delete()

    /** Removes leftovers of downloads killed with the process. */
    fun sweepStaleParts() {
        dir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
    }

    private fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.contains("..")

    companion object {
        private const val PART_SUFFIX = ".part"

        fun humanSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
