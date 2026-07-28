package org.hedgewars.android.config

import org.hedgewars.android.data.SchemesRepository
import org.hedgewars.android.data.WeaponSetsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Golden tests for user-created schemes and weapon sets: what the editors
 * produce must reach the engine as the exact command stream the desktop
 * frontend would send. ConfigSerializerTest pins the presets; this pins
 * the custom path (including JSON persistence transparency).
 */
class CustomConfigGoldenTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun team(name: String) = Team(name = name, hogNames = (1..8).map { "$name$it" })

    private fun config(scheme: Scheme, weapons: WeaponSet) = GameConfig(
        teams = listOf(
            TeamSlot(team("Alpha"), colorIndex = 0, hogCount = 1),
            TeamSlot(team("Beta"), colorIndex = 1, hogCount = 1),
        ),
        scheme = scheme,
        weapons = weapons,
        seed = "{fixed-seed}",
    )

    private val scheme = Scheme(
        name = "My Rules",
        randomOrder = false, king = true, artillery = true, perHogAmmo = true,
        turnTimeSec = 30, minesTimeSec = -1, worldEdge = 2, scriptParam = "x=1",
    )

    private val weapons = WeaponSet.EMPTY.copy(name = "My Arsenal")
        .set(AmmoField.LOADOUT, 0, 4)
        .set(AmmoField.PROBABILITY, 2, 5)
        .set(AmmoField.DELAY, 3, 2)
        .set(AmmoField.CRATE, 4, 6)
        .normalized()

    @Test
    fun `custom scheme serializes to the exact engine commands`() {
        val cmds = ConfigSerializer.localGame(config(scheme, weapons))
        assertTrue(cmds.contains("e\$gmflags ${0x800L or 0x4000L or 0x400000L}"))
        assertTrue(cmds.contains("e\$turntime 30000"))
        assertTrue(cmds.contains("e\$minestime -1000")) // -1 = random timer
        assertTrue(cmds.contains("e\$worldedge 2"))
        assertTrue(cmds.contains("e\$scriptparam x=1"))
        // per-hog ammo without shared ammo: no shared store
        assertFalse(cmds.contains("eammstore"))
    }

    @Test
    fun `custom weapon set reaches every team verbatim`() {
        val cmds = ConfigSerializer.localGame(config(scheme, weapons))
        // The editor's digit edits landed where they should…
        assertEquals('4', weapons.loadout[0])
        assertEquals('5', weapons.probability[2])
        assertEquals('2', weapons.delay[3])
        assertEquals('6', weapons.crate[4])
        // …and each of the two teams gets the same four 60-digit lines.
        assertEquals(2, cmds.count { it == "eammloadt ${weapons.loadout}" })
        val i = cmds.indexOf("eammloadt ${weapons.loadout}")
        assertEquals("eammprob ${weapons.probability}", cmds[i + 1])
        assertEquals("eammdelay ${weapons.delay}", cmds[i + 2])
        assertEquals("eammreinf ${weapons.crate}", cmds[i + 3])
        assertTrue(cmds[i + 4].startsWith("eaddteam "))
    }

    @Test
    fun `every frame fits the engine 255 byte limit even with max script param`() {
        val big = scheme.copy(scriptParam = "p".repeat(SchemeRanges.SCRIPT_PARAM_MAX))
        val cmds = ConfigSerializer.localGame(config(big, weapons))
        cmds.forEach {
            assertTrue("${it.take(24)}… is ${it.toByteArray(Charsets.UTF_8).size} bytes",
                it.toByteArray(Charsets.UTF_8).size <= 255)
        }
    }

    @Test
    fun `json persistence is transparent to the engine`() {
        val schemes = SchemesRepository(tmp.newFolder())
        val weaponSets = WeaponSetsRepository(tmp.newFolder())
        schemes.save(scheme)
        weaponSets.save(weapons)
        val direct = ConfigSerializer.localGame(config(scheme, weapons))
        val viaDisk = ConfigSerializer.localGame(
            config(schemes.get("My Rules")!!, weaponSets.get("My Arsenal")!!)
        )
        assertEquals(direct, viaDisk)
    }
}
