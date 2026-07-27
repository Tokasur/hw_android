package org.hedgewars.android.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // D-pad is SDL Android buttons 11-14 (never a hat)
        assertTrue(lines.contains("+left=j0b13"))
        assertTrue(lines.contains("+attack=j0b2"))
        assertTrue(lines.contains("pause=j0b6"))
        assertTrue(lines.contains("timer_u=j0b10"))
    }

    @Test
    fun `grenade fuse cycles instead of being pinned to one value`() {
        val entries = writeTo(true).filter { it.contains("=") }
        // "timer N" pins the fuse to N and can never cycle; timer_u steps it.
        assertTrue(entries.none { it.substringBefore('=').startsWith("timer ") })
        assertTrue(entries.any { it.startsWith("timer_u=") })
    }

    @Test
    fun `no trigger axis is bound on its released half`() {
        // Analog triggers rest at -32767, so jNaXd reads as permanently held:
        // anything bound there would fire once and never let go.
        val keys = writeTo(true).map { it.substringAfter('=') }
        assertTrue(keys.none { it matches Regex("""j\da[45]d""") })
    }

    @Test
    fun `movement is bound to both d-pad buttons and left stick axes`() {
        val lines = writeTo(true)
        assertTrue(lines.contains("+left=j0b13"))
        assertTrue(lines.contains("+left=j0a0d"))
        assertTrue(lines.contains("+right=j0b14"))
        assertTrue(lines.contains("+right=j0a0u"))
    }

    @Test
    fun `right stick drives the camera cursor`() {
        val lines = writeTo(true)
        assertTrue(lines.contains("+cur_r=j0a2u"))
        assertTrue(lines.contains("+cur_l=j0a2d"))
        // Android Y axis is positive DOWNWARD
        assertTrue(lines.contains("+cur_d=j0a3u"))
        assertTrue(lines.contains("+cur_u=j0a3d"))
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
    fun `section header comes before entries`() {
        val lines = writeTo(true)
        val header = lines.indexOf("[Binds]")
        val firstBind = lines.indexOfFirst { it.contains("=") }
        assertTrue(header in 0 until firstBind)
    }

    @Test
    fun `keys are unique - commands may repeat across physical inputs`() {
        val entries = writeTo(true).filter { it.contains("=") }
        val keys = entries.map { it.substringAfter('=') }
        // A key can carry only one command (engine indices are per-key)…
        assertEquals(keys.size, keys.toSet().size)
        // …but one command on several keys is the point (d-pad + stick).
        val commands = entries.map { it.substringBefore('=') }
        assertTrue(commands.size > commands.toSet().size)
    }
}
