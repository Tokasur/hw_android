package org.hedgewars.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinned against app/src/test/resources/content_snapshot.html — a verbatim
 * capture of https://hedgewars.org/content.html. If the site drifts, refresh
 * the fixture and adjust deliberately.
 */
class DlcCatalogParserTest {

    private val html: String by lazy {
        javaClass.getResourceAsStream("/content_snapshot.html")!!
            .readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses the eight categories with their entry counts`() {
        val cats = DlcCatalogParser.parse(html)
        assertEquals(
            listOf(
                "Themes" to 11, "Maps" to 6, "Game styles" to 11, "Hats" to 7,
                "Voices" to 6, "Flags" to 3, "Music" to 1, "Other" to 1,
            ),
            cats.map { it.title to it.entries.size },
        )
    }

    @Test
    fun `first theme entry carries all its fields`() {
        val patagonia = DlcCatalogParser.parse(html).first().entries.first()
        assertEquals("Patagonia v1", patagonia.name)
        assertEquals("/download/packs/Theme_Patagonia_v1.hwp", patagonia.href)
        assertEquals("Morning in Patagonia", patagonia.description)
        assertEquals("KIRA", patagonia.author)
        assertEquals("899 KiB", patagonia.size)
        assertEquals("Theme_Patagonia_v1.hwp", patagonia.fileName)
    }

    @Test
    fun `legacy zip packs get an hwp install name`() {
        val entries = DlcCatalogParser.parse(html).flatMap { it.entries }
        val zips = entries.filter { it.href.endsWith(".zip", ignoreCase = true) }
        assertEquals(9, zips.size)
        zips.forEach { assertTrue(it.fileName, it.fileName.endsWith(".hwp")) }
    }

    @Test
    fun `parses without the BEGIN-END markers too`() {
        val without = html.replace("<!-- BEGIN -->", "").replace("<!-- END -->", "")
        val total = DlcCatalogParser.parse(without).sumOf { it.entries.size }
        assertEquals(46, total)
    }

    @Test
    fun `broken tables fall back to a flat anchor list`() {
        val mangled = """
            <p>stuff <a href="/download/packs/Alpha.hwp">Alpha</a> and
            <a href="/download/maps/Beta.zip">Beta</a> and
            <a href="/download/maps/Beta.zip">Beta again</a> and
            <a href="/node/123">not a pack</a></p>
        """.trimIndent()
        val cats = DlcCatalogParser.parse(mangled)
        assertEquals(1, cats.size)
        assertEquals("", cats[0].title)
        assertEquals(listOf("Alpha.hwp", "Beta.hwp"), cats[0].entries.map { it.fileName })
    }

    @Test
    fun `no pack anchors at all yields an empty catalog`() {
        assertTrue(DlcCatalogParser.parse("<p>nothing here</p>").isEmpty())
    }

    @Test
    fun `duplicate hrefs are kept once`() {
        val twice = """
            <table>
            <tr><td class="dlc_type">Maps</td></tr>
            <tr><td><a href="/download/a.hwp">A</a></td><td>d</td></tr>
            <tr><td><a href="/download/a.hwp">A copy</a></td><td>d</td></tr>
            </table>
        """.trimIndent()
        assertEquals(1, DlcCatalogParser.parse(twice).sumOf { it.entries.size })
    }

    @Test
    fun `install file names are decoded, renamed and validated`() {
        assertEquals("Foo.hwp", DlcCatalogParser.installFileName("/download/x/Foo.zip"))
        assertEquals("Foo Bar.hwp", DlcCatalogParser.installFileName("/d/Foo%20Bar.hwp"))
        assertEquals("Pack.hwp", DlcCatalogParser.installFileName("/d/Pack.hwp?x=1#frag"))
        assertNull(DlcCatalogParser.installFileName("/d/evil%2F..%2Fname.hwp"))
        assertNull(DlcCatalogParser.installFileName("/d/notapack.txt"))
        assertNull(DlcCatalogParser.installFileName("/d/"))
    }

    @Test
    fun `download urls rebase relatives and allow only hedgewars hosts`() {
        assertEquals(
            "https://www.hedgewars.org/download/a.hwp",
            DlcCatalogParser.resolveDownloadUrl("/download/a.hwp"),
        )
        assertEquals(
            "https://hedgewars.org/x.hwp",
            DlcCatalogParser.resolveDownloadUrl("http://hedgewars.org/x.hwp"),
        )
        assertEquals(
            "https://www.hedgewars.org/y.hwp",
            DlcCatalogParser.resolveDownloadUrl("https://www.hedgewars.org/y.hwp"),
        )
        assertNull(DlcCatalogParser.resolveDownloadUrl("https://evil.example.com/x.hwp"))
        assertNull(DlcCatalogParser.resolveDownloadUrl("https://nothedgewars.org/x.hwp"))
    }
}
