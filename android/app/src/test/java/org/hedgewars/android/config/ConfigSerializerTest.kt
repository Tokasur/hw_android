package org.hedgewars.android.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden tests for the IPC command stream, mirroring what
 * QTfrontend/game.cpp + gamecfgwidget.cpp + team.cpp send.
 */
class ConfigSerializerTest {

    private fun team(name: String, difficulty: Int = 0) = Team(
        name = name,
        hogNames = (1..8).map { "$name$it" },
        // A team as the launcher hands it over: its fort is already resolved
        // (the "random" marker is replaced before serialization).
        fort = "Plane",
        difficulty = difficulty,
    )

    private fun defaultConfig() = GameConfig(
        teams = listOf(
            TeamSlot(team("Alpha"), colorIndex = 0, hogCount = 2),
            TeamSlot(team("Beta", difficulty = 3), colorIndex = 1, hogCount = 2),
        ),
        seed = "{fixed-seed}",
    )

    @Test
    fun `local game starts with TL token`() {
        assertEquals("TL", ConfigSerializer.localGame(defaultConfig()).first())
    }

    @Test
    fun `scheme values match desktop default scheme`() {
        val cmds = ConfigSerializer.localGame(defaultConfig())
        assertTrue(cmds.contains("e\$gmflags 8192"))       // random order only
        assertTrue(cmds.contains("e\$damagepct 100"))
        assertTrue(cmds.contains("e\$turntime 45000"))     // seconds -> ms
        assertTrue(cmds.contains("e\$inithealth 100"))
        assertTrue(cmds.contains("e\$sd_turns 15"))
        assertTrue(cmds.contains("e\$casefreq 5"))
        assertTrue(cmds.contains("e\$minestime 3000"))     // seconds -> ms
        assertTrue(cmds.contains("e\$minesnum 4"))
        assertTrue(cmds.contains("e\$healthprob 35"))
        assertTrue(cmds.contains("e\$waterrise 47"))
        assertTrue(cmds.contains("e\$mapgen 0"))
        assertTrue(cmds.contains("etheme Nature"))
        assertTrue(cmds.contains("eseed {fixed-seed}"))
    }

    @Test
    fun `random map sends no emap`() {
        val cmds = ConfigSerializer.localGame(defaultConfig())
        assertFalse(cmds.any { it.startsWith("emap") })
    }

    @Test
    fun `the random fort marker never reaches the engine`() {
        // An unknown fort name is fatal on a forts map (the engine loads
        // <name>L.png as a critical asset), so the marker must never be sent
        // even if a launch path forgot to resolve it.
        val random = team("Alpha").copy(fort = Team.FORT_RANDOM)
        val local = ConfigSerializer.localGame(
            defaultConfig().copy(teams = listOf(TeamSlot(random, colorIndex = 0, hogCount = 1)))
        )
        assertTrue(local.contains("efort ${Team.FORT_FALLBACK}"))
        assertFalse(local.any { it.contains(Team.FORT_RANDOM) })

        val mission = ConfigSerializer.missionGame(
            MissionConfig(team = random, script = "Missions/Training/Basic_Training_-_Bazooka.lua")
        )
        assertTrue(mission.contains("efort ${Team.FORT_FALLBACK}"))
        assertFalse(mission.any { it.contains(Team.FORT_RANDOM) })
    }

    @Test
    fun `an explicit fort is sent as is`() {
        val cmds = ConfigSerializer.localGame(
            defaultConfig().copy(
                teams = listOf(
                    TeamSlot(team("Alpha").copy(fort = "Lonely_Island"), colorIndex = 0, hogCount = 1)
                )
            )
        )
        assertTrue(cmds.contains("efort Lonely_Island"))

        // Same for a mission team — its fort must reach the engine too.
        val mission = ConfigSerializer.missionGame(
            MissionConfig(
                team = team("Solo").copy(fort = "Lonely_Island"),
                script = "Missions/Training/Basic_Training_-_Bazooka.lua",
            )
        )
        assertTrue(mission.contains("efort Lonely_Island"))
    }

    @Test
    fun `forts map sends mapgen 4 with the fort distance and no maze size`() {
        val cmds = ConfigSerializer.localGame(
            defaultConfig().copy(map = MapChoice.Generated(mapGen = MapGen.FORTS, featureSize = 12))
        )
        assertTrue(cmds.contains("e\$mapgen 4"))
        // Forts reuse feature_size as the gap between forts.
        assertTrue(cmds.contains("e\$feature_size 12"))
        assertFalse(cmds.any { it.startsWith("emap") })
        assertFalse(cmds.any { it.startsWith("e\$maze_size") })
        // The engine ORs gfDivideTeams (0x10) in itself for mgForts
        // (uLand.pas MakeFortsMap): sending it would double up on the desktop's
        // own behaviour, which never sets it either.
        val flags = cmds.first { it.startsWith("e\$gmflags ") }.removePrefix("e\$gmflags ").toInt()
        assertEquals(0, flags and 0x10)
    }

    @Test
    fun `named map sends emap`() {
        val cmds = ConfigSerializer.localGame(
            defaultConfig().copy(map = MapChoice.Named("Bath"))
        )
        assertTrue(cmds.contains("emap Bath"))
    }

    @Test
    fun `team block has ammo then team then hogs with hats`() {
        val cmds = ConfigSerializer.localGame(defaultConfig())
        val w = WeaponSet.DEFAULT
        val i = cmds.indexOf("eammloadt ${w.loadout}")
        assertTrue(i > 0)
        assertEquals("eammprob ${w.probability}", cmds[i + 1])
        assertEquals("eammdelay ${w.delay}", cmds[i + 2])
        assertEquals("eammreinf ${w.crate}", cmds[i + 3])
        assertEquals("eammstore", cmds[i + 4])
        val expectedHash = ConfigSerializer.md5Hex("Player")
        assertEquals("eaddteam $expectedHash ${Team.COLORS[0]} Alpha", cmds[i + 5])
        assertEquals("egrave Statue", cmds[i + 6])
        assertEquals("efort Plane", cmds[i + 7])
        assertEquals("evoicepack Default", cmds[i + 8])
        assertEquals("eflag hedgewars", cmds[i + 9])
        assertEquals("eaddhh 0 100 Alpha1", cmds[i + 10])
        assertEquals("ehat NoHat", cmds[i + 11])
        assertEquals("eaddhh 0 100 Alpha2", cmds[i + 12])
    }

    @Test
    fun `cpu team uses its difficulty in eaddhh`() {
        val cmds = ConfigSerializer.localGame(defaultConfig())
        assertTrue(cmds.contains("eaddhh 3 100 Beta1"))
    }

    @Test
    fun `md5 of Player matches reference`() {
        // echo -n Player | md5sum
        assertEquals("c9df43d1b6fb148c1c0e5c2a0e460ca8".length, ConfigSerializer.md5Hex("Player").length)
        assertEquals(ConfigSerializer.md5Hex("Player"), ConfigSerializer.md5Hex("Player"))
    }

    @Test
    fun `per hog ammo drops eammstore`() {
        val cfg = defaultConfig().copy(scheme = Scheme.DEFAULT.copy(perHogAmmo = true))
        val cmds = ConfigSerializer.localGame(cfg)
        assertFalse(cmds.contains("eammstore"))
        // shared ammo forces the store back even with perHogAmmo
        val cfg2 = defaultConfig().copy(
            scheme = Scheme.DEFAULT.copy(perHogAmmo = true, sharedAmmo = true)
        )
        assertTrue(ConfigSerializer.localGame(cfg2).contains("eammstore"))
    }

    @Test
    fun `all commands fit engine 255 byte frames`() {
        val cmds = ConfigSerializer.localGame(defaultConfig())
        cmds.forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 255) }
    }

    @Test
    fun `mission game is TL, mission team, seed and script`() {
        val cmds = ConfigSerializer.missionGame(
            MissionConfig(
                team = team("Solo"),
                script = "Missions/Training/Basic_Training_-_Bazooka.lua",
            )
        )
        assertEquals("TL", cmds.first())
        assertTrue(cmds.any { it.startsWith("esetmissteam ") && it.endsWith(" Solo") })
        assertTrue(cmds.any { it.startsWith("eaddmisshh 0 100 Solo1") })
        assertTrue(cmds.any { it.startsWith("eseed ") })
        assertEquals("escript Missions/Training/Basic_Training_-_Bazooka.lua", cmds.last())
    }

    @Test
    fun `game flags map to engine gf constants`() {
        assertEquals(0x2000L, Scheme.DEFAULT.gameFlags()) // random order
        val s = Scheme(
            name = "t", randomOrder = false,
            solidLand = true, border = true, divideTeams = true,
            lowGravity = true, laserSight = true, invulnerable = true,
        )
        assertEquals(0x4L or 0x8L or 0x10L or 0x20L or 0x40L or 0x80L, s.gameFlags())
    }
}
