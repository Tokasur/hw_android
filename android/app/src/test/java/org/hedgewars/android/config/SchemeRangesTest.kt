package org.hedgewars.android.config

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemeRangesTest {

    @Test
    fun `clamped pins every numeric to its desktop range`() {
        val wild = Scheme(
            name = "wild",
            damagePercent = 5,
            turnTimeSec = 0,
            initHealth = 0,
            suddenDeathTurns = 99,
            caseFreq = 42,
            minesTimeSec = -7,
            minesNum = 999,
            mineDudPercent = 101,
            explosives = -1,
            airMines = 500,
            sentries = 201,
            healthCaseProb = 150,
            healthCaseAmount = 5000,
            waterRise = 999,
            healthDecrease = -3,
            ropePercent = 10,
            getAwayTime = 1000,
            worldEdge = 9,
            scriptParam = "x".repeat(300),
        ).clamped()

        assertEquals(10, wild.damagePercent)
        assertEquals(1, wild.turnTimeSec)
        assertEquals(1, wild.initHealth)
        assertEquals(51, wild.suddenDeathTurns)
        assertEquals(9, wild.caseFreq)
        assertEquals(-1, wild.minesTimeSec)
        assertEquals(200, wild.minesNum)
        assertEquals(100, wild.mineDudPercent)
        assertEquals(0, wild.explosives)
        assertEquals(200, wild.airMines)
        assertEquals(200, wild.sentries)
        assertEquals(100, wild.healthCaseProb)
        assertEquals(1000, wild.healthCaseAmount)
        assertEquals(100, wild.waterRise)
        assertEquals(0, wild.healthDecrease)
        assertEquals(25, wild.ropePercent)
        assertEquals(999, wild.getAwayTime)
        assertEquals(3, wild.worldEdge)
        assertEquals(240, wild.scriptParam.length)
    }

    @Test
    fun `clamped leaves in-range values alone`() {
        val s = Scheme(name = "ok", turnTimeSec = 30, minesTimeSec = -1, explosives = 200)
        assertEquals(s, s.clamped())
    }

    @Test
    fun `every preset survives clamping unchanged`() {
        for (p in Scheme.PRESETS) {
            assertEquals(p.name, p, p.clamped())
        }
    }
}
