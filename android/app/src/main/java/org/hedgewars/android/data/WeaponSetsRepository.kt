package org.hedgewars.android.data

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hedgewars.android.config.WeaponSet

/**
 * User-created weapon sets as one JSON file each, alongside the compiled-in
 * [WeaponSet.PRESETS] (read-only). Twin of [SchemesRepository].
 */
class WeaponSetsRepository(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "weaponsets"))

    init {
        dir.mkdirs()
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun customs(): List<WeaponSet> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<WeaponSet>(it.readText()) }.getOrNull() }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** Presets first (desktop order), then customs sorted by name. */
    fun all(): List<WeaponSet> = WeaponSet.PRESETS + customs()

    fun isPreset(name: String): Boolean =
        WeaponSet.PRESETS.any { it.name.equals(name, ignoreCase = true) }

    fun get(name: String): WeaponSet? =
        file(name).takeIf { it.exists() }
            ?.let { runCatching { json.decodeFromString<WeaponSet>(it.readText()) }.getOrNull() }

    /** Never throws; falls back to [WeaponSet.DEFAULT] for vanished names. */
    fun resolve(name: String): WeaponSet =
        WeaponSet.PRESETS.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: customs().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: WeaponSet.DEFAULT

    fun save(set: WeaponSet) {
        val s = set.normalized().copy(name = set.name.trim())
        file(s.name).writeText(json.encodeToString(s))
    }

    fun delete(name: String) {
        file(name).delete()
    }

    /** Same contract as [SchemesRepository.nameError]. */
    fun nameError(candidate: String, original: String? = null): NameError? {
        val name = candidate.trim()
        if (name.isEmpty()) return NameError.EMPTY
        if (original != null && name.equals(original.trim(), ignoreCase = true)) return null
        if (isPreset(name)) return NameError.TAKEN
        if (customs().any { it.name.equals(name, ignoreCase = true) }) return NameError.TAKEN
        return null
    }

    /** Same contract as [SchemesRepository.duplicate]. */
    fun duplicate(source: WeaponSet, template: String = "Copy of %s"): WeaponSet {
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
