package org.hedgewars.android.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * UI language, on top of Android's own resource selection.
 *
 * The app ships English (default) and French and follows the system
 * language out of the box; this only adds a manual override. On API 33+ the
 * platform owns per-app languages ([LocaleManager]) so the choice also shows
 * up in Android's app settings; below that, activities re-apply it
 * themselves through [wrap] (call from attachBaseContext).
 */
object AppLocale {
    /** Language tags the app has resources for, "" meaning "follow the system". */
    val SUPPORTED = listOf("", "en", "fr")

    /** The stored choice ("" = system). */
    fun stored(context: Context): String = UserPrefs(context).language

    /** Language actually in effect, for anything keyed on it (engine locale). */
    fun effectiveLanguage(context: Context): String {
        val chosen = stored(context)
        if (chosen.isNotEmpty()) return chosen
        @Suppress("DEPRECATION")
        return context.resources.configuration.locales[0].language
    }

    /**
     * Stores [tag] and applies it. On API 33+ the system recreates the
     * activities itself; below, the caller recreates them.
     */
    fun set(context: Context, tag: String) {
        UserPrefs(context).language = tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    /**
     * Context with the stored language applied, for `attachBaseContext` on
     * API < 33 (on API 33+ the platform already did it, so this is a no-op).
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = runCatching { stored(base) }.getOrDefault("")
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
