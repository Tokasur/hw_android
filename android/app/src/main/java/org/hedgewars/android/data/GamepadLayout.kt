package org.hedgewars.android.data

import android.view.InputDevice
import android.view.MotionEvent

/**
 * Where a connected gamepad's sticks and triggers actually land in SDL's
 * joystick axis numbering.
 *
 * The engine reads RAW SDL joystick axes (j0a0, j0a1, …), numbered by the
 * order SDLControllerManager.pollInputDevices builds: every motion range of
 * class JOYSTICK except the two hat axes, sorted by SDL's RangeComparator.
 * That order depends on WHICH axes a pad reports, so "the right stick is
 * axes 2 and 3" holds for some pads and not others — on a pad that reports
 * an extra pair, the camera binds landed on axes that were never moved, or
 * worse, on a trigger. Recompute the numbering from the device instead.
 */
object GamepadLayout {

    /** SDL axis indices, null when the pad does not report that control. */
    data class Axes(
        val leftX: Int? = null,
        val leftY: Int? = null,
        val rightX: Int? = null,
        val rightY: Int? = null,
        val leftTrigger: Int? = null,
        val rightTrigger: Int? = null,
    )

    /** Verbatim port of SDLControllerManager.RangeComparator's ordering. */
    internal fun sortKey(axis: Int): Int {
        // Some pads report GAS for the right trigger and BRAKE for the left:
        // swap so left sorts first.
        var a = when (axis) {
            MotionEvent.AXIS_GAS -> MotionEvent.AXIS_BRAKE
            MotionEvent.AXIS_BRAKE -> MotionEvent.AXIS_GAS
            else -> axis
        }
        // Keep AXIS_Z sorted between AXIS_RY and AXIS_RZ.
        if (a == MotionEvent.AXIS_Z) {
            a = MotionEvent.AXIS_RZ - 1
        } else if (a > MotionEvent.AXIS_Z && a < MotionEvent.AXIS_RZ) {
            a -= 1
        }
        return a
    }

    /** Same acceptance test as SDLControllerManager.isDeviceSDLJoystick. */
    private fun isJoystick(device: InputDevice): Boolean {
        val s = device.sources
        return (s and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ||
            (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
    }

    /**
     * Layout of the pad SDL will call joystick 0, or null when none is
     * connected. SDL enumerates real devices before adding the accelerometer,
     * so a pad present at launch is joystick 0.
     */
    fun detect(): Axes? {
        val device = InputDevice.getDeviceIds()
            .filter { it >= 0 }
            .mapNotNull { runCatching { InputDevice.getDevice(it) }.getOrNull() }
            .firstOrNull { isJoystick(it) } ?: return null

        val axes = device.motionRanges
            .filter { (it.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 }
            .filter { it.axis != MotionEvent.AXIS_HAT_X && it.axis != MotionEvent.AXIS_HAT_Y }
            .sortedBy { sortKey(it.axis) }
        return fromSortedAxes(axes.map { it.axis to (it.min < 0f) })
    }

    /**
     * The numbering rules, split out so they can be exercised without a
     * device: [sorted] is (axis constant, is it centred at rest?) in SDL's
     * order. A trigger rests at one end of its range (min == 0) while a stick
     * is centred (min < 0) — a far more reliable way to tell a right stick
     * from a trigger than the axis constant, which pads disagree about.
     */
    internal fun fromSortedAxes(sorted: List<Pair<Int, Boolean>>): Axes? {
        if (sorted.isEmpty()) return null
        fun index(axis: Int) = sorted.indexOfFirst { it.first == axis }.takeIf { it >= 0 }
        fun centred(axis: Int) = sorted.firstOrNull { it.first == axis }?.second == true

        // Right stick: whichever of the two usual pairs is a real stick.
        val right = listOf(
            MotionEvent.AXIS_Z to MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RX to MotionEvent.AXIS_RY,
        ).firstOrNull { (x, y) -> centred(x) && centred(y) }

        // Triggers: the first unipolar pair that the right stick did not take.
        val triggers = listOf(
            MotionEvent.AXIS_LTRIGGER to MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE to MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_Z to MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RX to MotionEvent.AXIS_RY,
        ).firstOrNull { pair ->
            val (l, r) = pair
            pair != right && index(l) != null && index(r) != null && !centred(l) && !centred(r)
        }

        return Axes(
            leftX = index(MotionEvent.AXIS_X),
            leftY = index(MotionEvent.AXIS_Y),
            rightX = right?.first?.let { index(it) },
            rightY = right?.second?.let { index(it) },
            leftTrigger = triggers?.first?.let { index(it) },
            rightTrigger = triggers?.second?.let { index(it) },
        )
    }
}
