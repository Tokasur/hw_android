package org.hedgewars.android.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaponSetTest {

    @Test(expected = IllegalArgumentException::class)
    fun `init rejects short strings`() {
        WeaponSet("x", "0".repeat(59), "0".repeat(59), "0".repeat(59), "0".repeat(59))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `init rejects long strings`() {
        WeaponSet("x", "0".repeat(61), "0".repeat(61), "0".repeat(61), "0".repeat(61))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `init rejects non digits`() {
        WeaponSet("x", "a" + "0".repeat(59), "0".repeat(60), "0".repeat(60), "0".repeat(60))
    }

    @Test
    fun `all presets are four 60 digit strings`() {
        assertEquals(13, WeaponSet.PRESETS.size)
        for (p in WeaponSet.PRESETS) {
            for (f in AmmoField.entries) {
                assertEquals(p.name, WeaponSet.SIZE, p.string(f).length)
            }
        }
    }

    @Test
    fun `get reads the digit at a position`() {
        assertEquals(9, WeaponSet.DEFAULT.get(AmmoField.LOADOUT, 0))
        assertEquals(3, WeaponSet.DEFAULT.get(AmmoField.LOADOUT, 1))
        assertEquals(3, WeaponSet.DEFAULT.get(AmmoField.CRATE, 1))
    }

    @Test
    fun `set replaces one digit and clamps to the field max`() {
        val w = WeaponSet.EMPTY.set(AmmoField.LOADOUT, 0, 5)
        assertEquals(5, w.get(AmmoField.LOADOUT, 0))
        assertEquals(0, w.get(AmmoField.LOADOUT, 1))
        assertEquals(WeaponSet.EMPTY.probability, w.probability)

        assertEquals(9, WeaponSet.EMPTY.set(AmmoField.LOADOUT, 0, 42).get(AmmoField.LOADOUT, 0))
        assertEquals(8, WeaponSet.EMPTY.set(AmmoField.PROBABILITY, 0, 9).get(AmmoField.PROBABILITY, 0))
        assertEquals(0, WeaponSet.EMPTY.set(AmmoField.DELAY, 0, -3).get(AmmoField.DELAY, 0))
    }

    @Test
    fun `empty template keeps skip usable`() {
        assertEquals(9, WeaponSet.EMPTY.get(AmmoField.LOADOUT, AmmoCatalog.SKIP))
        assertEquals(0, WeaponSet.EMPTY.get(AmmoField.CRATE, AmmoCatalog.SKIP))
    }

    @Test
    fun `normalized forces skip and creeper like the desktop editor`() {
        var w = WeaponSet.EMPTY
        w = w.set(AmmoField.LOADOUT, AmmoCatalog.SKIP, 0)
        w = w.set(AmmoField.CRATE, AmmoCatalog.SKIP, 4)
        w = w.set(AmmoField.LOADOUT, AmmoCatalog.CREEPER, 7)
        w = w.set(AmmoField.PROBABILITY, AmmoCatalog.CREEPER, 3)
        val n = w.normalized()
        for (i in AmmoCatalog.HIDDEN) {
            assertEquals(9, n.get(AmmoField.LOADOUT, i))
            assertEquals(0, n.get(AmmoField.PROBABILITY, i))
            assertEquals(0, n.get(AmmoField.DELAY, i))
            assertEquals(0, n.get(AmmoField.CRATE, i))
        }
    }

    @Test
    fun `visible catalog hides skip and creeper`() {
        assertEquals(58, AmmoCatalog.VISIBLE.size)
        assertTrue(AmmoCatalog.SKIP !in AmmoCatalog.VISIBLE)
        assertTrue(AmmoCatalog.CREEPER !in AmmoCatalog.VISIBLE)
    }

    @Test
    fun `icon sheet is walked column major`() {
        assertEquals(0, AmmoCatalog.iconCol(0))
        assertEquals(0, AmmoCatalog.iconRow(0))
        assertEquals(0, AmmoCatalog.iconCol(15))
        assertEquals(15, AmmoCatalog.iconRow(15))
        assertEquals(1, AmmoCatalog.iconCol(16))
        assertEquals(0, AmmoCatalog.iconRow(16))
        assertEquals(3, AmmoCatalog.iconCol(59))
        assertEquals(11, AmmoCatalog.iconRow(59))
    }
}
