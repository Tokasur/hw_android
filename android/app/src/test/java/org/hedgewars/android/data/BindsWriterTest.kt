package org.hedgewars.android.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BindsWriterTest {

    private fun writeTo(enabled: Boolean): List<String> {
        val f = File.createTempFile("settings", ".ini")
        f.deleteOnExit()
        BindsWriter.write(f, gamepadEnabled = enabled)
        return f.readLines()
    }

    @Test
    fun `writes Binds section with command=key entries`() {
        val lines = writeTo(true)
        assertTrue(lines.contains("[Binds]"))
        // loadBinds parses "<command>=<keyname>" into: dbind <keyname> <command>
        assertTrue(lines.contains("+attack=j0b2"))
        assertTrue(lines.contains("ammomenu=j0b3"))
        assertTrue(lines.contains("pause=j0b6"))
    }

    @Test
    fun `dpad and left stick both drive movement`() {
        val lines = writeTo(true)
        // d-pad = semantic buttons 11-14 (never a hat: SDL Android converts)
        assertTrue(lines.contains("+up=j0b11"))
        assertTrue(lines.contains("+down=j0b12"))
        assertTrue(lines.contains("+left=j0b13"))
        assertTrue(lines.contains("+right=j0b14"))
        // left stick = semantic axes 0/1 (Android Y points down; engine
        // names the positive direction "u")
        assertTrue(lines.contains("+left=j0a0d"))
        assertTrue(lines.contains("+right=j0a0u"))
        assertTrue(lines.contains("+up=j0a1d"))
        assertTrue(lines.contains("+down=j0a1u"))
    }

    @Test
    fun `right stick drives the camera on semantic axes 2 and 3`() {
        val lines = writeTo(true)
        assertTrue(lines.contains("+cur_l=j0a2d"))
        assertTrue(lines.contains("+cur_r=j0a2u"))
        assertTrue(lines.contains("+cur_u=j0a3d"))
        assertTrue(lines.contains("+cur_d=j0a3u"))
    }

    @Test
    fun `R side cycles the fuse and L side cycles bounciness`() {
        val lines = writeTo(true)
        // The explicit field-tester request: R = timer, L = bounce.
        assertTrue(lines.contains("timer_u=j0b10")) // R1
        assertTrue(lines.contains("timer_u=j0a5u")) // R2
        assertTrue(lines.contains("+bounce=j0b9"))  // L1
        assertTrue(lines.contains("+precise=j0a4u")) // L2 held
        // "timer N" pins the fuse to one value and can never cycle.
        assertTrue(lines.none { it.substringBefore('=').startsWith("timer ") })
    }

    @Test
    fun `no trigger axis is bound on its released half`() {
        // Game-controller trigger axes rest at 0 and only go positive, so the
        // "d" half can never fire — binding it would be dead weight at best,
        // and with raw joystick numbering it used to be permanently held.
        val keys = writeTo(true).map { it.substringAfter('=') }
        assertTrue(keys.none { it == "j0a4d" || it == "j0a5d" })
    }

    @Test
    fun `disabled gamepad keeps only the back-button bind`() {
        val lines = writeTo(false)
        assertTrue(lines.contains("[Binds]"))
        assertEquals(listOf("quit=ac_back"), lines.filter { it.contains("=") })
    }

    @Test
    fun `back button is bound to engine quit for everyone`() {
        assertTrue(writeTo(true).contains("quit=ac_back"))
    }

    @Test
    fun `right thumb click confirms weapon targets`() {
        // Aim with the right stick, click it to place the target (teleport,
        // homing bee…) — without this, targets could only be tapped on screen.
        assertTrue(writeTo(true).contains("put=j0b8"))
    }

    @Test
    fun `section header comes before entries`() {
        val lines = writeTo(true)
        val header = lines.indexOf("[Binds]")
        val firstBind = lines.indexOfFirst { it.contains("=") }
        assertTrue(header in 0 until firstBind)
    }

    @Test
    fun `each key carries exactly one command`() {
        // Commands may repeat across keys (engine allows it on MOBILE);
        // a KEY appearing twice would be a real conflict.
        val keys = writeTo(true).filter { it.contains("=") }.map { it.substringAfter('=') }
        assertEquals(keys.size, keys.toSet().size)
    }
}
