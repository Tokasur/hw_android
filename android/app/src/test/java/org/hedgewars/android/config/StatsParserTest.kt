package org.hedgewars.android.config

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine's stat frames (uStats.pas SendStats) as the desktop page reads
 * them (QTfrontend/ui/page/pagegamestats.cpp GameStats).
 */
class StatsParserTest {

    /** Colours as the engine sends them: decimal of a signed ARGB int. */
    private val red = 0xFFFF0204.toInt()   // negative in Kotlin
    private val blue = 0xFF4980C1.toInt()

    private fun feedAll(parser: StatsParser, frames: List<Pair<Char, String>>) {
        frames.forEach { (k, t) -> parser.feed(k, t) }
    }

    @Test
    fun `a realistic end-of-match sequence`() {
        val parser = StatsParser()
        feedAll(parser, listOf(
            'H' to "$red 100",
            'H' to "$blue 100",
            'H' to "$red 88",
            'H' to "$blue 43",
            'H' to "$red 88",
            'H' to "$blue 0",
            'r' to "Alpha wins!",
            'R' to "1",
            'P' to "$red 3 Alpha",
            'R' to "2",
            'P' to "$blue 1 Beta",
            'D' to "57 Hog 1 (Alpha)",
            'k' to "2 Hog 1 (Alpha)",
            'B' to "4 Beta",
            'K' to "5",
        ))

        val r = parser.build() ?: error("expected a report")
        assertEquals("Alpha wins!", r.winnerText)
        assertEquals(
            listOf(
                RankEntry(red, 3, "Alpha", 1),
                RankEntry(blue, 1, "Beta", 2),
            ),
            r.rankings,
        )
        assertEquals(
            listOf(
                DetailEntry('D', 57, "Hog 1 (Alpha)"),
                DetailEntry('k', 2, "Hog 1 (Alpha)"),
                DetailEntry('B', 4, "Beta"),
                DetailEntry('K', 5, ""),
            ),
            r.details,
        )
        assertEquals(listOf(100, 88, 88), r.healthSeries[red])
        assertEquals(listOf(100, 43, 0), r.healthSeries[blue])
        assertNull(r.graphTitle)
        assertEquals(false, r.fromReplay)
    }

    @Test
    fun `the match counts as over only once a ranking has arrived`() {
        // The frames that stream during play must not look like the end: the
        // game process banks its results on this signal, seconds before the
        // engine reports the end and takes the process down with it.
        val parser = StatsParser()
        assertEquals(false, parser.hasFinalRankings)
        feedAll(parser, listOf('H' to "$red 100", 'H' to "$blue 43", 'r' to "Alpha wins!"))
        assertEquals(false, parser.hasFinalRankings)
        feedAll(parser, listOf('R' to "1", 'P' to "$red 3 Alpha"))
        assertTrue(parser.hasFinalRankings)
    }

    @Test
    fun `teams of one clan share a rank when no explicit rank is sent`() {
        val parser = StatsParser()
        feedAll(parser, listOf(
            'P' to "$red 2 Alpha",
            'P' to "$red 1 Alpha Reserve",
            'P' to "$blue 0 Beta",
        ))
        assertEquals(listOf(1, 1, 2), parser.build()!!.rankings.map { it.rank })
    }

    @Test
    fun `an explicit rank applies to the next entry only`() {
        val parser = StatsParser()
        feedAll(parser, listOf(
            'R' to "4",
            'P' to "$red 0 Alpha",
            'P' to "$blue 0 Beta",
        ))
        assertEquals(listOf(4, 2), parser.build()!!.rankings.map { it.rank })
    }

    @Test
    fun `a points kind applies to the next entry only`() {
        val parser = StatsParser()
        feedAll(parser, listOf(
            'p' to "!TIME",
            'P' to "$red 12345 Alpha",
            'P' to "$blue 999 Beta",
        ))
        val ranks = parser.build()!!.rankings
        assertEquals("!TIME", ranks[0].pointsKind)
        assertNull(ranks[1].pointsKind)
    }

    @Test
    fun `negative colours round-trip identically in rankings and health`() {
        val parser = StatsParser()
        feedAll(parser, listOf('P' to "$red 0 Alpha", 'H' to "$red 100"))
        val r = parser.build()!!
        assertTrue(r.rankings[0].color < 0)
        assertEquals(r.rankings[0].color, r.healthSeries.keys.single())
    }

    @Test
    fun `malformed lines are ignored`() {
        val parser = StatsParser()
        feedAll(parser, listOf(
            'P' to "no-space-here",
            'H' to "notanumber 5",
            'R' to "x",
            'K' to "",
            'P' to "$red 1 Alpha",
        ))
        val r = parser.build()!!
        assertEquals(listOf(RankEntry(red, 1, "Alpha", 1)), r.rankings)
        assertTrue(r.details.isEmpty())
        assertTrue(r.healthSeries.isEmpty())
    }

    @Test
    fun `nothing sent means no report`() {
        assertNull(StatsParser().build())
        val onlyIgnored = StatsParser()
        onlyIgnored.feed('T', "some team stat string")
        assertNull(onlyIgnored.build())
    }

    @Test
    fun `graph title and script achievements are kept verbatim`() {
        val parser = StatsParser()
        feedAll(parser, listOf('g' to "Race times", 'c' to "Alpha collected every crate"))
        val r = parser.build()!!
        assertEquals("Race times", r.graphTitle)
        assertEquals(DetailEntry('c', 0, "Alpha collected every crate"), r.details.single())
    }

    @Test
    fun `report survives a JSON round trip`() {
        val parser = StatsParser()
        feedAll(parser, listOf(
            'r' to "Alpha wins!",
            'P' to "$red 3 Alpha",
            'H' to "$red 100",
            'h' to "",
        ))
        val report = parser.build(fromReplay = true)!!
        val json = Json.encodeToString(report)
        assertEquals(report, Json.decodeFromString<StatsReport>(json))
    }
}
