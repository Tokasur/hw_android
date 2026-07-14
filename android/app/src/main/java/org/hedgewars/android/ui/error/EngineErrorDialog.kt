package org.hedgewars.android.ui.error

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hedgewars.android.engine.EngineOutcome

/**
 * Shown when the last engine run ended abnormally (IPC 'E' error or a native
 * crash). Lets the player read and share the engine log so a "the game just
 * closed" report becomes actionable.
 */
@Composable
fun EngineErrorDialog(
    message: String,
    /** System exit record / native tombstone, prepended to the shared log. */
    extraReport: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showLog by remember { mutableStateOf(false) }
    val log = remember {
        val engineLog = EngineOutcome.readEngineLog(context)
        listOfNotNull(extraReport, engineLog)
            .joinToString("\n\n")
            .ifBlank { null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Engine stopped") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
                if (showLog && log != null) {
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .horizontalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        dismissButton = {
            if (log != null) {
                if (!showLog) {
                    TextButton(onClick = { showLog = true }) { Text("View log") }
                } else {
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Hedgewars engine log")
                            putExtra(Intent.EXTRA_TEXT, log)
                        }
                        context.startActivity(Intent.createChooser(share, "Share engine log"))
                    }) { Text("Share log") }
                }
            }
        },
    )
}
