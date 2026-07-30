package org.hedgewars.android.data

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

/**
 * Read-only view over the user content the engine mounts but the frontend's
 * plain-filesystem scans cannot see: loose trees under filesDir/user/Data
 * plus the entries of every installed *.hwp (a zip whose root is a slice of
 * the Data tree). Packs are never extracted — only the zip central
 * directory is listed, and single entries are read with a hard size cap.
 */
class PackContentIndex(private val userDataDir: File) {
    constructor(context: Context) : this(GamePaths(context).userDataDir)

    private data class Stamp(val length: Long, val mtime: Long)

    private val cache = HashMap<String, Pair<Stamp, List<String>>>()

    private fun packs(): List<File> =
        userDataDir.listFiles { f -> f.isFile && f.extension.equals("hwp", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    private fun entriesOf(pack: File): List<String> = synchronized(cache) {
        val stamp = Stamp(pack.length(), pack.lastModified())
        cache[pack.name]?.takeIf { it.first == stamp }?.second ?: run {
            val list = runCatching {
                ZipFile(pack).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory }
                        .map { it.name.replace('\\', '/') }
                        .filter { isSafeEntry(it) }
                        .toList()
                }
            }.getOrDefault(emptyList())
            cache[pack.name] = stamp to list
            list
        }
    }

    private fun isSafeEntry(name: String): Boolean =
        !name.startsWith("/") && name.split('/').none { it == ".." || it.isEmpty() }

    private fun allEntries(): Sequence<String> =
        packs().asSequence().flatMap { entriesOf(it) }

    /** Names X such that "<prefix>X/<marker>" exists (e.g. themes with theme.cfg). */
    fun dirsWithFile(prefix: String, marker: String): List<String> {
        val fromPacks = allEntries()
            .filter { it.startsWith(prefix) && it.endsWith("/$marker") }
            .mapNotNull { entry ->
                val middle = entry.removePrefix(prefix).removeSuffix("/$marker")
                middle.takeIf { it.isNotEmpty() && !it.contains('/') }
            }
        val loose = File(userDataDir, prefix.trimEnd('/'))
            .listFiles { f -> f.isDirectory && File(f, marker).isFile }
            ?.map { it.name }.orEmpty()
        return (fromPacks.toList() + loose).distinct().sorted()
    }

    /** Immediate child directories under prefix (e.g. "Maps/"). */
    fun subdirs(prefix: String): List<String> {
        // Files sitting directly under the prefix map to "" and drop out.
        val fromPacks = allEntries()
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.removePrefix(prefix).substringBefore('/', "").ifEmpty { null } }
        val loose = File(userDataDir, prefix.trimEnd('/'))
            .listFiles { f -> f.isDirectory }
            ?.map { it.name }.orEmpty()
        return (fromPacks.toList() + loose).distinct().sorted()
    }

    fun hasFile(path: String): Boolean =
        File(userDataDir, path).isFile || allEntries().any { it == path }

    /** Base names of "<prefix><base><suffix>" files (e.g. hats: Graphics/Hats/, .png). */
    fun fileNames(prefix: String, suffix: String): List<String> {
        val fromPacks = allEntries()
            .filter { it.startsWith(prefix) && it.endsWith(suffix, ignoreCase = true) }
            .mapNotNull { entry ->
                val base = entry.removePrefix(prefix).dropLast(suffix.length)
                base.takeIf { it.isNotEmpty() && !it.contains('/') }
            }
        val loose = File(userDataDir, prefix.trimEnd('/'))
            .listFiles { f -> f.isFile && f.name.endsWith(suffix, ignoreCase = true) }
            ?.map { it.name.dropLast(suffix.length) }.orEmpty()
        return (fromPacks.toList() + loose).distinct().sorted()
    }

    /**
     * Bytes of one entry (packs first in sorted order, then loose file),
     * or null when absent, unreadable or larger than [MAX_ENTRY_BYTES]
     * (zip-bomb guard — this feeds thumbnails, nothing bigger).
     */
    fun readEntry(path: String): ByteArray? {
        if (!isSafeEntry(path)) return null
        for (pack in packs()) {
            val bytes = runCatching {
                ZipFile(pack).use { zip ->
                    val entry = zip.getEntry(path) ?: return@use null
                    if (entry.isDirectory || entry.size > MAX_ENTRY_BYTES) return@use null
                    zip.getInputStream(entry).use { it.readBoundedOrNull(MAX_ENTRY_BYTES) }
                }
            }.getOrNull()
            if (bytes != null) return bytes
        }
        val loose = File(userDataDir, path)
        if (loose.isFile && loose.length() <= MAX_ENTRY_BYTES) {
            return runCatching { loose.readBytes() }.getOrNull()
        }
        return null
    }

    private fun java.io.InputStream.readBoundedOrNull(max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) return null // lied about its size: reject
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_ENTRY_BYTES = 4L * 1024 * 1024
    }
}
