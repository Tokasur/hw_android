package org.hedgewars.android.config

import java.security.MessageDigest

/**
 * Serializes a game setup into the engine's IPC command stream.
 *
 * Reference implementations: QTfrontend/game.cpp (commonConfig,
 * SendTrainingConfig), QTfrontend/ui/widget/gamecfgwidget.cpp (getFullConfig)
 * and QTfrontend/team.cpp (teamGameConfig). Order matters: game type token
 * first, then map/scheme, then per-team ammo + team definition.
 */
object ConfigSerializer {

    const val PLAYER_NAME = "Player"

    /** Local game (TL): quick game vs CPU or hotseat multiplayer. */
    fun localGame(cfg: GameConfig): List<String> {
        val cmds = mutableListOf("TL")

        // --- map / rules (getFullConfig) ---
        cfg.script?.let { cmds += "escript $it" }
        when (val map = cfg.map) {
            is MapChoice.Named -> cmds += "emap ${map.mapName}"
            is MapChoice.Generated -> Unit
        }
        cmds += "etheme ${cfg.theme}"
        cmds += "eseed ${cfg.seed}"
        val s = cfg.scheme
        cmds += "e\$gmflags ${s.gameFlags()}"
        cmds += "e\$damagepct ${s.damagePercent}"
        cmds += "e\$turntime ${s.turnTimeSec * 1000}"
        cmds += "e\$inithealth ${s.initHealth}"
        cmds += "e\$sd_turns ${s.suddenDeathTurns}"
        cmds += "e\$casefreq ${s.caseFreq}"
        cmds += "e\$minestime ${s.minesTimeSec * 1000}"
        cmds += "e\$minesnum ${s.minesNum}"
        cmds += "e\$minedudpct ${s.mineDudPercent}"
        cmds += "e\$explosives ${s.explosives}"
        cmds += "e\$airmines ${s.airMines}"
        cmds += "e\$sentries ${s.sentries}"
        cmds += "e\$healthprob ${s.healthCaseProb}"
        cmds += "e\$hcaseamount ${s.healthCaseAmount}"
        cmds += "e\$waterrise ${s.waterRise}"
        cmds += "e\$healthdec ${s.healthDecrease}"
        cmds += "e\$ropepct ${s.ropePercent}"
        cmds += "e\$getawaytime ${s.getAwayTime}"
        cmds += "e\$worldedge ${s.worldEdge}"
        when (val map = cfg.map) {
            is MapChoice.Generated -> {
                cmds += "e\$template_filter ${map.templateFilter}"
                cmds += "e\$feature_size ${map.featureSize}"
                cmds += "e\$mapgen ${map.mapGen.id}"
                if (map.mapGen == MapGen.MAZE || map.mapGen == MapGen.PERLIN)
                    cmds += "e\$maze_size ${map.mazeSize}"
            }
            is MapChoice.Named -> {
                cmds += "e\$template_filter 0"
                cmds += "e\$feature_size 50"
                cmds += "e\$mapgen 0"
            }
        }
        cmds += "e\$scriptparam ${s.scriptParam}"

        // --- per team: ammo then team definition (commonConfig) ---
        val w = cfg.weapons
        for (slot in cfg.teams) {
            cmds += "eammloadt ${w.loadout}"
            cmds += "eammprob ${w.probability}"
            cmds += "eammdelay ${w.delay}"
            cmds += "eammreinf ${w.crate}"
            // ammo stores: one per team unless shared-ammo (then per clan);
            // Qt sends eammstore when sharedAmmo || !perHogAmmo
            if (s.sharedAmmo || !s.perHogAmmo) cmds += "eammstore"
            cmds += teamCommands(slot, s.initHealth)
        }
        return cmds
    }

    /** Mission / training / campaign game (SendTrainingConfig). */
    fun missionGame(cfg: MissionConfig): List<String> {
        val cmds = mutableListOf("TL")
        cmds += missionTeamCommands(cfg.team)
        // Defensive default: the engine requires a theme before it can start
        // (cifTheme init flag) and normally the mission's Lua script sets one
        // in onGameInit — but if the script fails to, the engine dies with
        // "Some parameters not set". A script-provided theme still wins.
        cmds += "etheme Nature"
        cmds += "eseed ${GameConfig.newSeed()}"
        cmds += "escript ${cfg.script}"
        return cmds
    }

    /** Commands defining one team (team.cpp teamGameConfig). */
    fun teamCommands(slot: TeamSlot, initHealth: Int): List<String> {
        val t = slot.team
        val color = Team.COLORS[slot.colorIndex % Team.COLORS.size]
        val cmds = mutableListOf<String>()
        cmds += "eaddteam ${md5Hex(PLAYER_NAME)} $color ${t.name}"
        cmds += "egrave ${t.grave}"
        cmds += "efort ${engineFort(t.fort)}"
        cmds += "evoicepack ${t.voicepack}"
        cmds += "eflag ${t.flag}"
        for (i in 0 until slot.hogCount) {
            cmds += "eaddhh ${t.difficulty} $initHealth ${t.hogNames[i]}"
            cmds += "ehat ${t.hat}"
        }
        return cmds
    }

    private fun missionTeamCommands(t: Team): List<String> {
        val color = Team.COLORS[0]
        val cmds = mutableListOf<String>()
        cmds += "esetmissteam ${md5Hex(PLAYER_NAME)} $color ${t.name}"
        cmds += "egrave ${t.grave}"
        cmds += "efort ${engineFort(t.fort)}"
        cmds += "evoicepack ${t.voicepack}"
        cmds += "eflag ${t.flag}"
        for (i in 0 until Team.MAX_HOGS) {
            cmds += "eaddmisshh 0 100 ${t.hogNames[i]}"
            cmds += "ehat ${t.hat}"
        }
        return cmds
    }

    /**
     * Safety net: the "random fort" marker is resolved before launch
     * ([FortPicker], called from GameLauncher). Should one ever reach here,
     * send a real fort — the engine treats a missing fort image as a fatal
     * error while generating a forts map.
     */
    private fun engineFort(fort: String): String =
        if (fort == Team.FORT_RANDOM) Team.FORT_FALLBACK else fort

    fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
