package org.hedgewars.android.data

import org.hedgewars.android.config.AmmoCatalog
import org.hedgewars.android.config.AmmoField
import org.hedgewars.android.config.WeaponSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WeaponSetsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() = WeaponSetsRepository(tmp.root)

    private val custom = WeaponSet.EMPTY
        .copy(name = "My Arsenal")
        .set(AmmoField.LOADOUT, 0, 3)
        .set(AmmoField.PROBABILITY, 2, 5)

    @Test
    fun `save then get roundtrips the normalized set`() {
        val r = repo()
        r.save(custom)
        assertEquals(custom.normalized(), r.get("My Arsenal"))
    }

    @Test
    fun `save normalizes the hidden skip and creeper digits`() {
        val r = repo()
        r.save(custom.set(AmmoField.LOADOUT, AmmoCatalog.SKIP, 0))
        val loaded = r.get("My Arsenal")!!
        for (i in AmmoCatalog.HIDDEN) {
            assertEquals(9, loaded.get(AmmoField.LOADOUT, i))
            assertEquals(0, loaded.get(AmmoField.CRATE, i))
        }
    }

    @Test
    fun `all lists presets then customs and resolve never throws`() {
        val r = repo()
        r.save(custom)
        assertEquals(WeaponSet.PRESETS.map { it.name } + "My Arsenal", r.all().map { it.name })
        assertEquals("Crazy", r.resolve("crazy").name)
        assertEquals("My Arsenal", r.resolve("my arsenal").name)
        assertEquals(WeaponSet.DEFAULT, r.resolve("vanished"))
    }

    @Test
    fun `nameError and duplicate mirror the schemes repository`() {
        val r = repo()
        r.save(custom)
        assertEquals(NameError.EMPTY, r.nameError(""))
        assertEquals(NameError.TAKEN, r.nameError("shoppa pro"))
        assertEquals(NameError.TAKEN, r.nameError("my arsenal"))
        assertNull(r.nameError("my arsenal", original = "My Arsenal"))
        assertEquals("Copy of Crazy", r.duplicate(r.resolve("Crazy")).name)
        assertEquals("Copy of Crazy 2", r.duplicate(r.resolve("Crazy")).name)
    }

    @Test
    fun `delete removes the file`() {
        val r = repo()
        r.save(custom)
        r.delete("My Arsenal")
        assertNull(r.get("My Arsenal"))
    }
}
