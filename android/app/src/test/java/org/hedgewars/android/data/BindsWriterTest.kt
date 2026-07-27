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
        val axes = GamepadLayout.Axes(
            leftX = 0, leftY = 1, rightX = 2, rightY = 3,
            leftTrigger = 4, rightTrigger = 5,
        )
        val f = File.createTempFile("settings", ".ini").apply { deleteOnExit() }
        BindsWriter.write(f, gamepadEnabled = true, axes = axes)
        val keys = f.readLines().map { it.substringAfter('=') }
        assertTrue(keys.contains("j0a5u"))
        assertTrue(keys.none { it == "j0a4d" || it == "j0a5d" })
    }

    @Test
    fun `stick axis numbers follow the device, not a guess`() {
        // A pad that also reports RX/RY pushes its Z/RZ right stick to 4 and 5.
        val axes = GamepadLayout.Axes(leftX = 0, leftY = 1, rightX = 4, rightY = 5)
        val f = File.createTempFile("settings", ".ini").apply { deleteOnExit() }
        BindsWriter.write(f, gamepadEnabled = true, axes = axes)
        val lines = f.readLines()
        assertTrue(lines.contains("+cur_r=j0a4u"))
        assertTrue(lines.contains("+cur_u=j0a5d"))
        // and nothing is bound to the axes the pad does not use for a stick
        assertTrue(lines.none { it.endsWith("=j0a2u") || it.endsWith("=j0a3u") })
    }

    @Test
    fun `absent sticks produce no axis binds`() {
        val f = File.createTempFile("settings", ".ini").apply { deleteOnExit() }
        BindsWriter.write(f, gamepadEnabled = true, axes = GamepadLayout.Axes())
        val keys = f.readLines().map { it.substringAfter('=') }
        // no stick axes -> no stick binds; triggers fall back to digital
        assertTrue(keys.none { it.startsWith("j0a") })
        assertTrue(keys.contains("j0b13"))
    }

    @Test
    fun `one physical trigger gets exactly one binding`() {
        // Analog trigger present: axis form only — a pad reporting the trigger
        // as axis AND button would otherwise fire +bounce twice per pull, and
        // the two overlapped presses used to strand gmPrecise (frozen hog).
        val analog = GamepadLayout.Axes(leftTrigger = 4, rightTrigger = 5)
        val f1 = File.createTempFile("settings", ".ini").apply { deleteOnExit() }
        BindsWriter.write(f1, gamepadEnabled = true, axes = analog)
        val l1 = f1.readLines()
        assertTrue(l1.contains("+bounce=j0a5u"))
        assertTrue(l1.contains("+precise=j0a4u"))
        assertTrue(l1.none { it.endsWith("=j0b16") || it.endsWith("=j0b15") })

        // No analog trigger: digital fallback, once.
        val digital = GamepadLayout.Axes(leftX = 0, leftY = 1)
        val f2 = File.createTempFile("settings", ".ini").apply { deleteOnExit() }
        BindsWriter.write(f2, gamepadEnabled = true, axes = digital)
        val l2 = f2.readLines()
        assertEquals(1, l2.count { it.startsWith("+bounce=") })
        assertTrue(l2.contains("+bounce=j0b16"))
        assertTrue(l2.contains("+precise=j0b15"))
    }

    @Test
    fun `SDL axis sort keeps Z between RY and RZ`() {
        // Verbatim expectation from SDLControllerManager.RangeComparator.
        val sorted = listOf(
            MotionEventAxis.X, MotionEventAxis.Y, MotionEventAxis.Z,
            MotionEventAxis.RZ, MotionEventAxis.RX, MotionEventAxis.RY,
        ).sortedBy { GamepadLayout.sortKey(it) }
        assertEquals(
            listOf(
                MotionEventAxis.X, MotionEventAxis.Y, MotionEventAxis.RX,
                MotionEventAxis.RY, MotionEventAxis.Z, MotionEventAxis.RZ,
            ),
            sorted,
        )
    }

    /** android.view.MotionEvent axis constants (not on the JVM test path). */
    private object MotionEventAxis {
        const val X = 0
        const val Y = 1
        const val Z = 11
        const val RX = 12
        const val RY = 13
        const val RZ = 14
    }

    @Test
    fun `a pad reporting an extra stick pair moves its right stick to 4 and 5`() {
        // (axis, is it centred at rest) in SDL's sorted order for a pad
        // reporting X, Y, RX, RY, Z, RZ with the right stick on Z/RZ.
        val axes = GamepadLayout.fromSortedAxes(
            listOf(
                MotionEventAxis.X to true,
                MotionEventAxis.Y to true,
                MotionEventAxis.RX to false, // unipolar: these are the triggers
                MotionEventAxis.RY to false,
                MotionEventAxis.Z to true,
                MotionEventAxis.RZ to true,
            ),
        )
        assertEquals(4, axes?.rightX)
        assertEquals(5, axes?.rightY)
        assertEquals(2, axes?.leftTrigger)
        assertEquals(3, axes?.rightTrigger)
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
