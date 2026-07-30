package org.hedgewars.android.ui.stats

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hedgewars.android.Features
import org.hedgewars.android.R
import org.hedgewars.android.config.DetailEntry
import org.hedgewars.android.config.GameConfig
import org.hedgewars.android.config.RankEntry
import org.hedgewars.android.config.StatsReport
import org.hedgewars.android.data.DemosRepository
import org.hedgewars.android.engine.MatchRecords
import org.hedgewars.android.game.GameLauncher
import org.hedgewars.android.ui.common.HwButton
import org.hedgewars.android.ui.common.HwPanel
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.SectionHeader
import org.hedgewars.android.ui.common.safeBack
import org.hedgewars.android.ui.theme.HwColors

/**
 * The report to show, handed over from the resume hook that read it out of the
 * ":game" process's file. A process-wide holder rather than a nav argument: it
 * is far too big for a route, and it must survive a rotation of the screen.
 */
object StatsHolder {
    @Volatile
    var report: StatsReport? = null
}

/**
 * End-of-match screen: who won, the ranking, the notable moments and the
 * clans' health over the match — the port's equivalent of the desktop's
 * PageGameStats (QTfrontend/ui/page/pagegamestats.cpp).
 */
@Composable
fun StatsScreen(nav: NavController) {
    val context = LocalContext.current
    val report = StatsHolder.report
    val scope = rememberCoroutineScope()
    val demos = remember { DemosRepository(context) }
    val temp = remember { MatchRecords.demoTemp(context) }

    // A replay of a replay is not a thing, and a replay has nothing new to save.
    val isReplay = report?.fromReplay == true
    // Saving consumes the temporary recording, so its mere presence is the
    // answer to "is there anything left to save?" — which also survives a
    // rotation. An unsaved recording is not deleted here: this composition is
    // destroyed by a rotation too, and the next match sweeps it anyway
    // (GameLauncher.launch / GameActivity.onCreate).
    var canSave by remember {
        mutableStateOf(Features.REPLAYS && !isReplay && temp.length() > 0L)
    }

    HwScreen(title = stringResource(R.string.stats_title), onBack = { nav.safeBack() }) {
        if (report == null) {
            // Nothing to show (a stale navigation): let the user back out.
            Text(
                stringResource(R.string.stats_empty),
                color = HwColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@HwScreen
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            report.winnerText?.let { winner ->
                Text(
                    winner,
                    color = HwColors.Gold,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }

            if (report.rankings.isNotEmpty()) {
                IconHeader(R.drawable.stats_ranking, stringResource(R.string.stats_ranking_header))
                HwPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.rankings.forEach { RankingRow(it) }
                    }
                }
            }

            if (report.details.isNotEmpty()) {
                IconHeader(R.drawable.stats_details, stringResource(R.string.stats_details_header))
                HwPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.details.forEach { DetailRow(it) }
                    }
                }
            }

            // Under two data points there is no curve to look at (same rule as
            // the desktop page).
            if ((report.healthSeries.values.maxOfOrNull { it.size } ?: 0) >= 2) {
                IconHeader(
                    R.drawable.stats_graph,
                    report.graphTitle ?: stringResource(R.string.stats_graph_header),
                )
                HealthGraph(report.healthSeries)
            }

            Spacer(Modifier.height(6.dp))

            val lastConfig = GameLauncher.lastLocalConfig
            if (!isReplay && lastConfig != null) {
                HwButton(stringResource(R.string.stats_play_again), onClick = {
                    // Same setup, fresh terrain — like hitting the desktop's
                    // "play again" arrow.
                    GameLauncher(context).launchLocalGame(
                        lastConfig.copy(seed = GameConfig.newSeed())
                    )
                    nav.safeBack()
                })
            }
            if (canSave) {
                HwButton(stringResource(R.string.stats_save_replay), primary = false, onClick = {
                    canSave = false
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { demos.saveLastDemo(temp) }
                        if (saved != null) {
                            Toast.makeText(
                                context,
                                R.string.stats_replay_saved,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                })
            }
            HwButton(
                stringResource(R.string.stats_continue),
                primary = isReplay || lastConfig == null,
                onClick = { nav.safeBack() },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IconHeader(icon: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = painterResource(icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp).padding(end = 6.dp),
        )
        SectionHeader(title)
    }
}

@Composable
private fun RankingRow(entry: RankEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = painterResource(medalFor(entry.rank)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(28.dp),
        )
        Text(
            "${entry.rank}.",
            color = HwColors.TextMuted,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            entry.team,
            color = clanColor(entry.color).readable(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            pointsText(entry),
            color = HwColors.TextLight,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun medalFor(rank: Int): Int = when (rank) {
    1 -> R.drawable.stats_medal1
    2 -> R.drawable.stats_medal2
    3 -> R.drawable.stats_medal3
    else -> R.drawable.stats_medal4
}

/** The team's score as a parenthesized suffix, following the 'p' points kind. */
@Composable
private fun pointsText(entry: RankEntry): String {
    val kind = entry.pointsKind
    val n = entry.points
    val body = when {
        kind == null -> pluralStringResource(R.plurals.stats_kills, n, n)
        kind == "!POINTS" -> pluralStringResource(R.plurals.stats_points, n, n)
        kind == "!CRATES" -> pluralStringResource(R.plurals.stats_crates, n, n)
        kind == "!EMPTY" -> return ""
        kind.startsWith("!TIME") -> {
            // "!TIME" is milliseconds; "!TIMEn" asks for n decimals.
            val decimals = kind.drop(5).firstOrNull()?.digitToIntOrNull() ?: 3
            val seconds = "%.${decimals}f".format(n / 1000.0)
            pluralStringResource(R.plurals.stats_seconds, n / 1000, seconds)
        }
        // A script's own unit, e.g. "4 flags".
        else -> stringResource(R.string.stats_custom_points, n, kind)
    }
    return "($body)"
}

@Composable
private fun DetailRow(entry: DetailEntry) {
    val icon = when (entry.kind) {
        'D' -> R.drawable.stats_best_shot
        'k' -> R.drawable.stats_best_killer
        'K' -> R.drawable.stats_hogs_killed
        's' -> R.drawable.stats_self_damage
        'S' -> R.drawable.stats_self_kills
        'B' -> R.drawable.stats_turn_skips
        'h' -> R.drawable.stats_ever_after
        else -> R.drawable.stats_custom
    }
    val text = when (entry.kind) {
        'D' -> pluralStringResource(R.plurals.stats_best_shot, entry.number, entry.who, entry.number)
        'k' -> pluralStringResource(R.plurals.stats_best_killer, entry.number, entry.who, entry.number)
        'K' -> pluralStringResource(R.plurals.stats_hogs_killed, entry.number, entry.number)
        's' -> pluralStringResource(R.plurals.stats_self_damage, entry.number, entry.who, entry.number)
        'S' -> pluralStringResource(R.plurals.stats_self_kills, entry.number, entry.who, entry.number)
        'B' -> pluralStringResource(R.plurals.stats_turn_skips, entry.number, entry.who, entry.number)
        'h' -> stringResource(R.string.stats_ever_after)
        else -> entry.who
    }
    Row(verticalAlignment = Alignment.Top) {
        androidx.compose.foundation.Image(
            painter = painterResource(icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(22.dp).padding(end = 8.dp, top = 2.dp),
        )
        Text(text, color = HwColors.TextLight, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One polyline per clan, in the clan's colour, over a black plate — the
 * desktop's health graph, minus the axes it doesn't draw either.
 */
@Composable
private fun HealthGraph(series: Map<Int, List<Int>>) {
    val turns = series.values.maxOf { it.size }
    val top = maxOf(1, series.values.flatten().maxOrNull() ?: 1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        drawRect(Color.Black)
        // A clan at full health draws exactly on the top edge and a dead one on
        // the bottom edge: without this inset half of each such line falls
        // outside the plate, and a team that never took damage is invisible.
        val inset = size.height * 0.05f
        val plotHeight = size.height - 2 * inset
        val stepX = if (turns > 1) size.width / (turns - 1) else size.width
        fun points(values: List<Int>) = values.mapIndexed { i, hp ->
            androidx.compose.ui.geometry.Offset(
                x = i * stepX,
                y = inset + plotHeight - (hp.coerceAtLeast(0).toFloat() / top) * plotHeight,
            )
        }
        for ((color, values) in series) {
            if (values.size < 2) continue
            val path = points(values)
            val clan = clanColor(color)
            // A nearly black clan colour is invisible on the plate: trace it
            // in white first, as the desktop does with a dotted marker pen.
            if (clan.isVeryDark()) {
                for (i in 0 until path.size - 1) {
                    drawLine(Color.White, path[i], path[i + 1], strokeWidth = 5f, cap = StrokeCap.Round)
                }
            }
            for (i in 0 until path.size - 1) {
                drawLine(clan, path[i], path[i + 1], strokeWidth = 2.5f, cap = StrokeCap.Round)
            }
        }
        drawRect(HwColors.OutlineSoft, style = Stroke(width = 2f))
    }
}

/** The engine sends clan colours as a signed ARGB int; only RGB matters. */
private fun clanColor(engineColor: Int): Color =
    Color((engineColor and 0xFFFFFF) or 0xFF000000.toInt())

private fun Color.isVeryDark(): Boolean =
    maxOf(red, green, blue) < 64f / 255f

/** Keeps a dark clan colour legible on the dark panels. */
private fun Color.readable(): Color {
    val peak = maxOf(red, green, blue)
    if (peak >= 0.5f) return this
    // Pure black cannot be brightened proportionally — fall back to the
    // regular text colour rather than painting a name nobody can read.
    if (peak <= 0.01f) return HwColors.TextLight
    val factor = 0.5f / peak
    return Color(
        red = (red * factor).coerceAtMost(1f),
        green = (green * factor).coerceAtMost(1f),
        blue = (blue * factor).coerceAtMost(1f),
    )
}
