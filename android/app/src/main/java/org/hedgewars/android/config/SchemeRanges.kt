package org.hedgewars.android.config

/**
 * Editable bounds for the numeric scheme settings — the desktop frontend's
 * spinbox ranges (QTfrontend/ui/page/pagescheme.cpp). The engine does no
 * clamping of its own, so these are the only guard.
 */
object SchemeRanges {
    val damagePercent = 10..300
    val turnTimeSec = 1..9999
    val initHealth = 1..1000
    val suddenDeathTurns = 0..51
    val caseFreq = 0..9

    /** -1 = random timer (serialized as -1000 ms). */
    val minesTimeSec = -1..5
    val minesNum = 0..200
    val mineDudPercent = 0..100
    val explosives = 0..200
    val airMines = 0..200
    val sentries = 0..200
    val healthCaseProb = 0..100
    val healthCaseAmount = 0..1000
    val waterRise = 0..100
    val healthDecrease = 0..1000
    val ropePercent = 25..999
    val getAwayTime = 0..999

    /** 0 none, 1 wrap, 2 bounce, 3 sea. */
    val worldEdge = 0..3

    /** Keeps "e${'$'}scriptparam <s>" within the engine's 255-byte frame. */
    const val SCRIPT_PARAM_MAX = 240
}

/** Every numeric field coerced into its desktop range; applied on save. */
fun Scheme.clamped(): Scheme = copy(
    damagePercent = damagePercent.coerceIn(SchemeRanges.damagePercent),
    turnTimeSec = turnTimeSec.coerceIn(SchemeRanges.turnTimeSec),
    initHealth = initHealth.coerceIn(SchemeRanges.initHealth),
    suddenDeathTurns = suddenDeathTurns.coerceIn(SchemeRanges.suddenDeathTurns),
    caseFreq = caseFreq.coerceIn(SchemeRanges.caseFreq),
    minesTimeSec = minesTimeSec.coerceIn(SchemeRanges.minesTimeSec),
    minesNum = minesNum.coerceIn(SchemeRanges.minesNum),
    mineDudPercent = mineDudPercent.coerceIn(SchemeRanges.mineDudPercent),
    explosives = explosives.coerceIn(SchemeRanges.explosives),
    airMines = airMines.coerceIn(SchemeRanges.airMines),
    sentries = sentries.coerceIn(SchemeRanges.sentries),
    healthCaseProb = healthCaseProb.coerceIn(SchemeRanges.healthCaseProb),
    healthCaseAmount = healthCaseAmount.coerceIn(SchemeRanges.healthCaseAmount),
    waterRise = waterRise.coerceIn(SchemeRanges.waterRise),
    healthDecrease = healthDecrease.coerceIn(SchemeRanges.healthDecrease),
    ropePercent = ropePercent.coerceIn(SchemeRanges.ropePercent),
    getAwayTime = getAwayTime.coerceIn(SchemeRanges.getAwayTime),
    worldEdge = worldEdge.coerceIn(SchemeRanges.worldEdge),
    scriptParam = scriptParam.take(SchemeRanges.SCRIPT_PARAM_MAX),
)
