package org.hedgewars.android.ui.common

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json

/**
 * rememberSaveable adapter for any @Serializable value — the editors keep
 * their whole draft (a Scheme or WeaponSet) in one state object, so a
 * rotation must round-trip all of it.
 */
inline fun <reified T : Any> jsonSaver(): Saver<T, String> = Saver(
    save = { Json.encodeToString(it) },
    restore = { runCatching { Json.decodeFromString<T>(it) }.getOrNull() },
)
