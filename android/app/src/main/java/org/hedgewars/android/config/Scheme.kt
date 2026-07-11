package org.hedgewars.android.config

import kotlinx.serialization.Serializable

/**
 * Game scheme: rule flags plus numeric settings.
 *
 * Flag bit values are the engine's gf* constants as sent in e$gmflags
 * (QTfrontend/ui/widget/gamecfgwidget.cpp getGameFlags).
 */
@Serializable
data class Scheme(
    val name: String,
    // boolean rules
    val divideTeams: Boolean = false,
    val solidLand: Boolean = false,
    val border: Boolean = false,
    val lowGravity: Boolean = false,
    val laserSight: Boolean = false,
    val invulnerable: Boolean = false,
    val resetHealth: Boolean = false,
    val vampirism: Boolean = false,
    val karma: Boolean = false,
    val artillery: Boolean = false,
    val randomOrder: Boolean = true,
    val king: Boolean = false,
    val placeHogs: Boolean = false,
    val sharedAmmo: Boolean = false,
    val disableGirders: Boolean = false,
    val disableLandObjects: Boolean = false,
    val aiSurvival: Boolean = false,
    val infiniteAttacks: Boolean = false,
    val resetWeapons: Boolean = false,
    val perHogAmmo: Boolean = false,
    val noWind: Boolean = false,
    val moreWind: Boolean = false,
    val tagTeam: Boolean = false,
    val bottomBorder: Boolean = false,
    val switchHog: Boolean = false,
    // numeric settings
    val damagePercent: Int = 100,
    val turnTimeSec: Int = 45,
    val initHealth: Int = 100,
    val suddenDeathTurns: Int = 15,
    val caseFreq: Int = 5,
    val minesTimeSec: Int = 3,
    val minesNum: Int = 4,
    val mineDudPercent: Int = 0,
    val explosives: Int = 2,
    val airMines: Int = 0,
    val sentries: Int = 0,
    val healthCaseProb: Int = 35,
    val healthCaseAmount: Int = 25,
    val waterRise: Int = 47,
    val healthDecrease: Int = 5,
    val ropePercent: Int = 100,
    val getAwayTime: Int = 100,
    val worldEdge: Int = 0,
    val scriptParam: String = "",
) {
    /** e$gmflags value (engine gf* constants, hedgewars/uConsts.pas). */
    fun gameFlags(): Long {
        var f = 0L
        if (switchHog) f = f or 0x00001000L
        if (divideTeams) f = f or 0x00000010L
        if (solidLand) f = f or 0x00000004L
        if (border) f = f or 0x00000008L
        if (lowGravity) f = f or 0x00000020L
        if (laserSight) f = f or 0x00000040L
        if (invulnerable) f = f or 0x00000080L
        if (resetHealth) f = f or 0x00000100L
        if (vampirism) f = f or 0x00000200L
        if (karma) f = f or 0x00000400L
        if (artillery) f = f or 0x00000800L
        if (randomOrder) f = f or 0x00002000L
        if (king) f = f or 0x00004000L
        if (placeHogs) f = f or 0x00008000L
        if (sharedAmmo) f = f or 0x00010000L
        if (disableGirders) f = f or 0x00020000L
        if (disableLandObjects) f = f or 0x00040000L
        if (aiSurvival) f = f or 0x00080000L
        if (infiniteAttacks) f = f or 0x00100000L
        if (resetWeapons) f = f or 0x00200000L
        if (perHogAmmo) f = f or 0x00400000L
        if (noWind) f = f or 0x00800000L
        if (moreWind) f = f or 0x01000000L
        if (tagTeam) f = f or 0x02000000L
        if (bottomBorder) f = f or 0x04000000L
        return f
    }

    companion object {
        val DEFAULT = Scheme(name = "Default")

        val PRESETS = listOf(
            DEFAULT,
            Scheme(
                name = "Pro Mode",
                sharedAmmo = true, turnTimeSec = 15, caseFreq = 0,
            ),
            Scheme(
                name = "Sudden Death",
                initHealth = 50, suddenDeathTurns = 8, waterRise = 100,
            ),
            Scheme(
                name = "Barrel Mayhem",
                sharedAmmo = true, explosives = 80, caseFreq = 0,
            ),
            Scheme(
                name = "King Mode",
                king = true,
            ),
        )
    }
}
