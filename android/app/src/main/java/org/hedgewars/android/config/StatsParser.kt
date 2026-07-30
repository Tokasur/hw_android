package org.hedgewars.android.config

/**
 * Accumulates the engine's 'i' stat frames into a [StatsReport].
 *
 * Mirrors PageGameStats::GameStats (QTfrontend/ui/page/pagegamestats.cpp):
 * the engine streams one frame per fact ~3 s before it reports the end of the
 * match, and the frontend is what turns them into a screen.
 *
 * Not thread-safe: fed from the IPC thread only.
 */
class StatsParser {

    private var winnerText: String? = null
    private val rankings = mutableListOf<RankEntry>()
    private val details = mutableListOf<DetailEntry>()
    private val healthSeries = LinkedHashMap<Int, MutableList<Int>>()
    private var graphTitle: String? = null

    /** Rank bookkeeping: same clan colour twice in a row = shared rank. */
    private var position = 0
    private var lastColor: Int? = null

    /** Set by an 'R' frame, consumed by the next 'P'. */
    private var scriptRank = 0

    /** Set by a 'p' frame, consumed by the next 'P'. */
    private var pointsKind: String? = null

    /** Feeds one frame. A malformed line is dropped, never fatal. */
    fun feed(kind: Char, text: String) {
        runCatching { parse(kind, text) }
    }

    private fun parse(kind: Char, text: String) {
        when (kind) {
            'r' -> winnerText = text
            'g' -> graphTitle = text
            'R' -> scriptRank = text.trim().toInt()
            'p' -> pointsKind = text.takeIf { it.isNotEmpty() }
            'P' -> parseRank(text)
            'H' -> {
                val (color, hp) = twoFields(text)
                healthSeries.getOrPut(color.toInt()) { mutableListOf() }.add(hp.toInt())
            }
            // Details with a "<number> <who>" payload.
            'D', 'k', 's', 'S', 'B' -> {
                val (num, who) = twoFields(text)
                details += DetailEntry(kind, num.toInt(), who)
            }
            'K' -> details += DetailEntry(kind, text.trim().toInt())
            'c' -> details += DetailEntry(kind, who = text)
            'h' -> details += DetailEntry(kind)
            // 'T' (per-team stat string) is unused by the desktop page too.
        }
    }

    /** "<color> <points> <team name>" — the team name may contain spaces. */
    private fun parseRank(text: String) {
        val (colorText, rest) = twoFields(text)
        val color = colorText.toInt()
        val (pointsText, team) = twoFields(rest)

        position++
        // The engine lists teams of the same clan one after another; they share
        // one rank (and one colour), so don't advance the position for them.
        if (lastColor == color) position--
        lastColor = color

        rankings += RankEntry(
            color = color,
            points = pointsText.toInt(),
            team = team,
            // The engine sends an explicit rank ('R') before every 'P'; the
            // counter above is the fallback for scripts that don't.
            rank = if (scriptRank == 0) position else scriptRank,
            pointsKind = pointsKind,
        )
        scriptRank = 0
        pointsKind = null
    }

    private fun twoFields(text: String): Pair<String, String> {
        val i = text.indexOf(' ')
        require(i > 0) { "expected two fields in '$text'" }
        return text.substring(0, i) to text.substring(i + 1)
    }

    /**
     * True once the end-of-match ranking has arrived ('R'+'P' frames, sent by
     * uStats.pas SendStats). The engine only ever emits those when the match
     * has actually concluded — and it does so ~3 seconds BEFORE the final 'q',
     * which makes this the earliest reliable "the match is over" signal.
     */
    val hasFinalRankings: Boolean get() = rankings.isNotEmpty()

    /** The collected report, or null when the engine sent nothing usable. */
    fun build(fromReplay: Boolean = false): StatsReport? {
        val report = StatsReport(
            winnerText = winnerText,
            rankings = rankings.toList(),
            details = details.toList(),
            healthSeries = healthSeries.mapValues { it.value.toList() },
            graphTitle = graphTitle,
            fromReplay = fromReplay,
        )
        return if (report.isEmpty) null else report
    }
}
