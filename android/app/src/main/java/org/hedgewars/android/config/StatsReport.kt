package org.hedgewars.android.config

import kotlinx.serialization.Serializable

/**
 * End-of-match statistics, exactly what the engine sends in its 'i' frames
 * (uStats.pas SendStats) and what the desktop shows on its game-stats page
 * (QTfrontend/ui/page/pagegamestats.cpp).
 *
 * Serialized to JSON because it crosses the process boundary: the engine talks
 * to the ":game" process, the screen that displays this lives in the menu
 * process (see engine/MatchRecords).
 */
@Serializable
data class StatsReport(
    /** The engine's own winner sentence — already localized engine-side. */
    val winnerText: String? = null,
    val rankings: List<RankEntry> = emptyList(),
    val details: List<DetailEntry> = emptyList(),
    /** Clan colour -> clan health after each finished turn. */
    val healthSeries: Map<Int, List<Int>> = emptyMap(),
    /** Script-provided graph title ('g'); null means "Health graph". */
    val graphTitle: String? = null,
    /** True when these stats come from watching a replay, not from playing. */
    val fromReplay: Boolean = false,
) {
    val isEmpty: Boolean
        get() = winnerText == null && rankings.isEmpty() && details.isEmpty() && healthSeries.isEmpty()
}

/**
 * One team's line in the ranking, from an 'R' + 'P' pair.
 *
 * [color] is the clan colour as the engine sent it: a decimal *signed* int of
 * an ARGB value, so it is usually negative. [points] are kills unless
 * [pointsKind] says otherwise (a preceding 'p' frame, e.g. "!TIME", "!CRATES",
 * or a free word for a script's own unit).
 */
@Serializable
data class RankEntry(
    val color: Int,
    val points: Int,
    val team: String,
    val rank: Int,
    val pointsKind: String? = null,
)

/**
 * One line of the "Details" panel. [kind] is the engine's stat letter:
 * 'D' best shot, 'k' best killer, 'K' hedgehogs killed, 's' self damage,
 * 'S' self kills, 'B' turn skips, 'c' script achievement (text in [who]),
 * 'h' everyone-in-peace.
 */
@Serializable
data class DetailEntry(val kind: Char, val number: Int = 0, val who: String = "")
