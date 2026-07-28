package org.hedgewars.android.data

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hedgewars.android.config.Scheme
import org.hedgewars.android.config.clamped

/** Why a candidate name for a custom scheme/weapon set is rejected. */
enum class NameError { EMPTY, TAKEN }

/**
 * Live input filter for custom names. Restricting to this charset keeps
 * `name + ".json"` an injective, file-safe mapping (no rename collisions)
 * and stays valid in navigation routes once Uri-encoded.
 */
fun sanitizeCustomName(raw: String): String =
    raw.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in " _-" }

/**
 * User-created game schemes as one JSON file each under the app's private
 * storage, alongside the compiled-in [Scheme.PRESETS] (read-only, like the
 * desktop frontend). Names are restricted to [A-Za-z0-9 _-] by the editor,
 * so `name + ".json"` maps names to files without collisions.
 */
class SchemesRepository(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "schemes"))

    init {
        dir.mkdirs()
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun customs(): List<Scheme> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<Scheme>(it.readText()) }.getOrNull() }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** Presets first (desktop order), then customs sorted by name. */
    fun all(): List<Scheme> = Scheme.PRESETS + customs()

    fun isPreset(name: String): Boolean =
        Scheme.PRESETS.any { it.name.equals(name, ignoreCase = true) }

    fun get(name: String): Scheme? =
        file(name).takeIf { it.exists() }
            ?.let { runCatching { json.decodeFromString<Scheme>(it.readText()) }.getOrNull() }

    /**
     * Selection lookup that never throws: preset, else custom (both
     * case-insensitive), else [Scheme.DEFAULT] — a selected name may
     * legitimately have been deleted since it was picked.
     */
    fun resolve(name: String): Scheme =
        Scheme.PRESETS.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: customs().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: Scheme.DEFAULT

    fun save(scheme: Scheme) {
        val s = scheme.clamped().let { it.copy(name = it.name.trim()) }
        file(s.name).writeText(json.encodeToString(s))
    }

    fun delete(name: String) {
        file(name).delete()
    }

    /**
     * null when [candidate] is usable. [original] is the name the entry had
     * when the editor opened (null when creating) so an unchanged or
     * case-only-changed name stays valid while editing.
     */
    fun nameError(candidate: String, original: String? = null): NameError? {
        val name = candidate.trim()
        if (name.isEmpty()) return NameError.EMPTY
        if (original != null && name.equals(original.trim(), ignoreCase = true)) return null
        if (isPreset(name)) return NameError.TAKEN
        if (customs().any { it.name.equals(name, ignoreCase = true) }) return NameError.TAKEN
        return null
    }

    /**
     * Saves and returns a copy of [source] named from [template] ("%s" = the
     * source name; the caller passes a localized template), suffixed with
     * " 2", " 3"... until free.
     */
    fun duplicate(source: Scheme, template: String = "Copy of %s"): Scheme {
        val base = template.format(source.name).trim()
        var name = base
        var n = 2
        while (nameError(name) != null) name = "$base ${n++}"
        val copy = source.copy(name = name)
        save(copy)
        return copy
    }

    private fun file(name: String): File = File(dir, name.trim() + ".json")
}
