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
        assertTrue(lines.contains("+left=j0h0l"))
        assertTrue(lines.contains("+attack=j0b2"))
        assertTrue(lines.contains("pause=j0b7"))
        assertTrue(lines.contains("timer 3=j0b11"))
    }

    @Test
    fun `disabled gamepad leaves empty section`() {
        val lines = writeTo(false)
        assertTrue(lines.contains("[Binds]"))
        assertFalse(lines.any { it.contains("=") })
    }

    @Test
    fun `section header comes before entries`() {
        val lines = writeTo(true)
        val header = lines.indexOf("[Binds]")
        val firstBind = lines.indexOfFirst { it.contains("=") }
        assertTrue(header in 0 until firstBind)
    }

    @Test
    fun `one key per command - no duplicate keys or commands`() {
        val entries = writeTo(true).filter { it.contains("=") }
        val commands = entries.map { it.substringBefore('=') }
        val keys = entries.map { it.substringAfter('=') }
        assertEquals(commands.size, commands.toSet().size)
        assertEquals(keys.size, keys.toSet().size)
    }
}
