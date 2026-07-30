package org.hedgewars.android.data

import java.net.URI
import java.net.URLDecoder

/** One downloadable pack in the community catalog. */
data class DlcEntry(
    val name: String,
    val href: String,
    val description: String?,
    val author: String?,
    /** Size text verbatim from the site, e.g. "899 KiB". */
    val size: String?,
    /** Local file name after install (.zip already mapped to .hwp) — the join key against installed packs. */
    val fileName: String,
)

data class DlcCategory(val title: String, val entries: List<DlcEntry>)

/**
 * Pure parser for https://hedgewars.org/content.html — the same page the
 * desktop DLC screen renders (QTfrontend/ui/page/pagedata.cpp). The page is
 * one big table: rows with a `dlc_type` cell announce a category, ordinary
 * rows are packs whose first cell links to a .hwp (or legacy .zip). Keep in
 * sync with the fixture app/src/test/resources/content_snapshot.html.
 */
object DlcCatalogParser {
    private const val BEGIN = "<!-- BEGIN -->"
    private const val END = "<!-- END -->"

    private val ROW = Regex("(?is)<tr[^>]*>(.*?)</tr>")
    private val CELL = Regex("(?is)<td[^>]*>(.*?)</td>")
    private val CATEGORY_CELL = Regex("(?is)<td[^>]*class=\"[^\"]*dlc_type[^\"]*\"[^>]*>(.*?)</td>")
    private val HREF = Regex("(?is)href=\"([^\"]+)\"")
    private val ANCHOR_TEXT = Regex("(?is)<a[^>]*>(.*?)</a>")
    private val ANY_PACK_ANCHOR = Regex("(?is)<a[^>]+href=\"([^\"]+\\.(?:hwp|zip))\"[^>]*>(.*?)</a>")
    private val TAGS = Regex("<[^>]*>")
    private val SPACES = Regex("\\s+")

    fun parse(html: String): List<DlcCategory> {
        val begin = html.indexOf(BEGIN)
        val end = html.indexOf(END)
        val frag = if (begin in 0 until end) html.substring(begin, end) else html

        val categories = mutableListOf<DlcCategory>()
        var title = ""
        var entries = mutableListOf<DlcEntry>()
        fun flush() {
            if (entries.isNotEmpty()) categories += DlcCategory(title, entries.toList())
            entries = mutableListOf()
        }

        for (row in ROW.findAll(frag)) {
            val body = row.groupValues[1]
            val category = CATEGORY_CELL.find(body)
            if (category != null) {
                flush()
                title = clean(category.groupValues[1])
                continue
            }
            runCatching { parseEntry(body) }.getOrNull()?.let { entries += it }
        }
        flush()

        if (categories.none { it.entries.isNotEmpty() }) {
            // Structure-drift fallback: any pack anchor becomes a flat entry,
            // so the screen never comes up empty while download links exist.
            val flat = ANY_PACK_ANCHOR.findAll(frag)
                .mapNotNull { m -> entry(m.groupValues[1], clean(m.groupValues[2]), null, null, null) }
                .distinctBy { it.href }
                .toList()
            return if (flat.isEmpty()) emptyList() else listOf(DlcCategory("", flat))
        }

        // Dedup by href across the whole catalog, preserving site order.
        val seen = mutableSetOf<String>()
        return categories.mapNotNull { c ->
            val kept = c.entries.filter { seen.add(it.href) }
            if (kept.isEmpty()) null else DlcCategory(c.title, kept)
        }
    }

    private fun parseEntry(rowBody: String): DlcEntry? {
        val cells = CELL.findAll(rowBody).map { it.groupValues[1] }.toList()
        if (cells.isEmpty()) return null
        val href = HREF.find(cells[0])?.groupValues?.get(1) ?: return null
        if (!isPackPath(href)) return null
        val name = clean(ANCHOR_TEXT.find(cells[0])?.groupValues?.get(1) ?: cells[0])
        return entry(
            href,
            name,
            description = cells.getOrNull(1)?.let(::clean)?.ifEmpty { null },
            author = cells.getOrNull(2)?.let(::clean)?.ifEmpty { null },
            size = cells.getOrNull(3)?.let(::clean)?.ifEmpty { null },
        )
    }

    private fun entry(
        href: String,
        name: String,
        description: String?,
        author: String?,
        size: String?,
    ): DlcEntry? {
        val fileName = installFileName(href) ?: return null
        return DlcEntry(name.ifEmpty { fileName }, href, description, author, size, fileName)
    }

    /** URL-path suffix rule from the desktop (pagedata.cpp): .hwp/.zip = a pack. */
    private fun isPackPath(href: String): Boolean {
        val path = href.substringBefore('#').substringBefore('?')
        return path.endsWith(".hwp", ignoreCase = true) || path.endsWith(".zip", ignoreCase = true)
    }

    /**
     * Local install name for a pack href: last path segment, URL-decoded,
     * .zip renamed to .hwp (the desktop rule). Null when it would not be a
     * plain, safe .hwp file name.
     */
    fun installFileName(href: String): String? {
        val path = href.substringBefore('#').substringBefore('?')
        val raw = path.substringAfterLast('/')
        if (raw.isEmpty()) return null
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
        if (decoded.contains('/') || decoded.contains('\\') || decoded.contains("..")) return null
        return when {
            decoded.endsWith(".hwp", ignoreCase = true) -> decoded
            decoded.endsWith(".zip", ignoreCase = true) -> decoded.dropLast(4) + ".hwp"
            else -> null
        }
    }

    /**
     * Absolute download URL for an href. Relative paths are rebased onto
     * https://www.hedgewars.org like the desktop does; absolute URLs are
     * accepted only for hedgewars.org hosts (http promoted to https) so a
     * foreign link injected into the page can never be downloaded.
     */
    fun resolveDownloadUrl(href: String): String? {
        val trimmed = href.trim()
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host != "hedgewars.org" && !host.endsWith(".hedgewars.org")) return null
            return "https://" + host + (uri.rawPath ?: "")
        }
        val path = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "https://www.hedgewars.org$path"
    }

    private fun clean(s: String): String = s
        .replace(TAGS, " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(SPACES, " ")
        .trim()
}
