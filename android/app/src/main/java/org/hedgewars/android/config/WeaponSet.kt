package org.hedgewars.android.config

import kotlinx.serialization.Serializable

/** The four per-weapon digit strings of a weapon set, with their max digit. */
enum class AmmoField(val max: Int) {
    /** Starting ammo count; 9 = infinite. */
    LOADOUT(9),

    /** Crate drop weight, 0-8 (engine maps through a probability table). */
    PROBABILITY(8),

    /** Turns before the weapon unlocks, 0-8. */
    DELAY(8),

    /** Amount found in an ammo crate, 0-8. */
    CRATE(8),
}

/**
 * Weapon set: four strings of one digit per weapon
 * (QTfrontend/weapons.h). Sent as eammloadt/eammprob/eammdelay/eammreinf.
 * The engine rejects the whole game unless each string is exactly
 * [SIZE] digits (uAmmos.pas AddAmmoStore).
 */
@Serializable
data class WeaponSet(
    val name: String,
    val loadout: String,
    val probability: String,
    val delay: String,
    val crate: String,
) {
    init {
        for (s in listOf(loadout, probability, delay, crate)) {
            require(s.length == SIZE) { "ammo string must be $SIZE digits, got ${s.length}" }
            require(s.all { it in '0'..'9' }) { "ammo string must be all digits" }
        }
    }

    fun get(field: AmmoField, index: Int): Int = string(field)[index] - '0'

    /** Copy with one digit replaced, coerced into the field's 0..max range. */
    fun set(field: AmmoField, index: Int, value: Int): WeaponSet {
        val chars = string(field).toCharArray()
        chars[index] = '0' + value.coerceIn(0, field.max)
        val s = String(chars)
        return when (field) {
            AmmoField.LOADOUT -> copy(loadout = s)
            AmmoField.PROBABILITY -> copy(probability = s)
            AmmoField.DELAY -> copy(delay = s)
            AmmoField.CRATE -> copy(crate = s)
        }
    }

    fun string(field: AmmoField): String = when (field) {
        AmmoField.LOADOUT -> loadout
        AmmoField.PROBABILITY -> probability
        AmmoField.DELAY -> delay
        AmmoField.CRATE -> crate
    }

    /**
     * What the desktop editor writes for its hidden rows on save
     * (selectWeapon.cpp): Skip and Creeper get loadout 9 and zero
     * probability/delay/crate. The engine force-zeroes Creeper at load.
     */
    fun normalized(): WeaponSet {
        var w = this
        for (i in AmmoCatalog.HIDDEN) {
            w = w.set(AmmoField.LOADOUT, i, 9)
            w = w.set(AmmoField.PROBABILITY, i, 0)
            w = w.set(AmmoField.DELAY, i, 0)
            w = w.set(AmmoField.CRATE, i, 0)
        }
        return w
    }

    companion object {
        const val SIZE = 60

        val DEFAULT = WeaponSet(
            name = "Default",
            loadout = "939192942219912103223511100120000000021110010101111100010001",
            probability = "040504054160065554655446477657666666615551010111541111111073",
            delay = "000000000000020550000004000700400000000022000000060002000000",
            crate = "131111031211111112311411111111111111121111111111111111111111",
        )

        /** Blank template a new custom set starts from (AMMOLINE_EMPTY_*). */
        val EMPTY = WeaponSet(
            name = "",
            loadout = "000000900000000000000000000000000000000000000000000000000000",
            probability = "000000000000000000000000000000000000000000000000000000000000",
            delay = "000000000000000000000000000000000000000000000000000000000000",
            crate = "131111031211111112311411111111111111121111111111111111111111",
        )

        /** The 13 desktop sets, in cDefaultAmmos order (QTfrontend/hwconsts.cpp.in). */
        val PRESETS = listOf(
            DEFAULT,
            WeaponSet(
                name = "Crazy",
                loadout = "999999999999999999299999999999999929999999999999999299919099",
                probability = "111111011111111111111111111111111111111111111111111111111011",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "131111031211111112311411111111111111121111111111111111111111",
            ),
            WeaponSet(
                name = "Pro Mode",
                loadout = "909000900000000000000900000000000000000000000000000000000000",
                probability = "000000000000000000000000000000000000000000000000000000000000",
                delay = "000000000000020550000004000700400000000020000000000002000000",
                crate = "111111011111111111111111111111111111111111111111111111111111",
            ),
            WeaponSet(
                name = "Shoppa",
                loadout = "000000990000000000000000000000000000000000000000000000000000",
                probability = "444441004424440221011212122242200000000200040001001100101010",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "111111011111111111111111111111111111111111111111111111111111",
            ),
            WeaponSet(
                name = "Clean Slate",
                loadout = "101000900001000001100000000000000000000000000000100000000000",
                probability = "040504054160065554655446477657666666615551010111541112111040",
                delay = "000000000000000000000000000000000000000000000000000002000000",
                crate = "131111031211111112311411111111111111121111111111111111111111",
            ),
            WeaponSet(
                name = "Minefield",
                loadout = "000000990009000000030000000000000000000000000000000000000000",
                probability = "000000000000000000000000000000000000000000000000000000000000",
                delay = "000000000000020550000004000700400000000020000000060002000000",
                crate = "111111011111111111111111111111111111111111111111111111111111",
            ),
            WeaponSet(
                name = "Thinking with Portals",
                loadout = "900000900200000000210000000000000011000009000000000000000000",
                probability = "040504054160065554655446477657666666615551010111541112111020",
                delay = "000000000000020550000004000700400000000020000000060002000000",
                crate = "131111031211111112311411111111111111121111111111111111111111",
            ),
            WeaponSet(
                name = "One of Everything",
                loadout = "111111911111111111111111111111111111111111111111111111111011",
                probability = "111111011111111111111111111111111111111111111111111111111011",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "111111011111111111111111111111111111111111111111111111111111",
            ),
            WeaponSet(
                name = "Highlander",
                loadout = "111111911111111111110191111111111001011111011110010010111010",
                probability = "000000000000000000000000000000000000000000000000000000000000",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "000000000000000000000000000000000000000000000000000000000001",
            ),
            WeaponSet(
                name = "Balanced Random Weapon",
                loadout = "333233923323323223232331311221130000032322030220222000203010",
                probability = "000000000000000000000000000000001111100000000000000000000000",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "111111011111111111111111111111111111111111111111111111111111",
            ),
            WeaponSet(
                name = "Construction Mode",
                loadout = "110001900000001001009000000000000000000000000000000000000000",
                probability = "111111011111111001000111111011111111111111011111001011101010",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "111111011111111111111111111111111111111111111111111111111111",
            ),
            WeaponSet(
                name = "Shoppa Pro",
                loadout = "000000990000000000000000000000000000000000000000000000000000",
                probability = "444440004404440000000000000040000000000000000000000000000000",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "111111011111111111111111111111111111111111111111111112111111",
            ),
            WeaponSet(
                name = "HedgeEditor",
                loadout = "000000900000000000000000000000000000000000000000000000000000",
                probability = "000000000000000000000000000000000000000000000000000000000000",
                delay = "000000000000000000000000000000000000000000000000000000000000",
                crate = "111111011111111111111111111111111111111111111111111111111111",
            ),
        )
    }
}
