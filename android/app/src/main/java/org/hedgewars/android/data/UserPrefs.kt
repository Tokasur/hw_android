package org.hedgewars.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Frontend settings (audio, quality, gamepad) backed by SharedPreferences. */
class UserPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hedgewars", Context.MODE_PRIVATE)

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit { putBoolean("sound", v) }

    var music: Boolean
        get() = prefs.getBoolean("music", true)
        set(v) = prefs.edit { putBoolean("music", v) }

    /** Main-theme music in the frontend menus (in-game music is [music]). */
    var menuMusic: Boolean
        get() = prefs.getBoolean("menuMusic", true)
        set(v) = prefs.edit { putBoolean("menuMusic", v) }

    var showFps: Boolean
        get() = prefs.getBoolean("showFps", false)
        set(v) = prefs.edit { putBoolean("showFps", v) }

    var lowQuality: Boolean
        get() = prefs.getBoolean("lowQuality", false)
        set(v) = prefs.edit { putBoolean("lowQuality", v) }

    var gamepadBinds: Boolean
        get() = prefs.getBoolean("gamepadBinds", true)
        set(v) = prefs.edit { putBoolean("gamepadBinds", v) }

    /**
     * In-game UI scale: "auto" (default) or a fixed factor ("1.0", "1.5",
     * "2.0", "2.5"). The game renders at screen-size / factor and Android
     * upscales, so a bigger factor means bigger touch buttons and HUD.
     */
    var uiScale: String
        get() = prefs.getString("uiScale", "auto") ?: "auto"
        set(v) = prefs.edit { putString("uiScale", v) }

    /**
     * UI language as a tag ("en", "fr"); empty means follow the system.
     * Applied through [AppLocale] — the engine's own locale follows it too.
     */
    var language: String
        get() = prefs.getString("language", "") ?: ""
        set(v) = prefs.edit { putString("language", v) }
}
