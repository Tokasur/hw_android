package org.hedgewars.android.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R

private const val SOURCE_URL = "https://github.com/tokasur/hw_android"

private val COMPONENTS = listOf(
    "Hedgewars engine — GPL-2.0",
    "SDL2, SDL2_image, SDL2_mixer, SDL2_ttf, SDL2_net — zlib license",
    "Lua 5.1 — MIT license",
    "PhysicsFS — zlib license",
    "FreeType (via SDL2_ttf) — FTL",
    "Ogg/Opus/Opusfile (via SDL2_mixer) — BSD",
    "Game art, sounds and music — see CREDITS and Data licenses",
)

@Composable
fun AboutScreen(nav: NavController) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.about_gpl))

        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.about_source)) }

        Text("Components", style = MaterialTheme.typography.titleMedium)
        COMPONENTS.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}
