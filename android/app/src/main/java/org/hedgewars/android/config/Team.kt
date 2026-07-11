package org.hedgewars.android.config

import kotlinx.serialization.Serializable

/**
 * A team as configured in the frontend.
 *
 * [difficulty] follows the engine convention (eaddhh):
 * 0 = human-controlled, 1..5 = CPU where 1 is the strongest AI.
 */
@Serializable
data class Team(
    val name: String,
    val hogNames: List<String>,
    val grave: String = "Statue",
    val fort: String = "Plane",
    val voicepack: String = "Default",
    val flag: String = "hedgewars",
    val hat: String = "NoHat",
    val difficulty: Int = 0,
) {
    init {
        require(hogNames.size == MAX_HOGS) { "a team stores exactly $MAX_HOGS hog names" }
    }

    val isCpu: Boolean get() = difficulty > 0

    companion object {
        const val MAX_HOGS = 8

        /** Official team color palette (QTfrontend/hwconsts.h HW_TEAMCOLOR_ARRAY). */
        val COLORS = intArrayOf(
            0xff0204, // red
            0x4980c1, // blue
            0x1de6ba, // teal
            0xb541ef, // purple
            0xe55bb0, // pink
            0x20bf00, // green
            0xfe8b0e, // orange
            0x8f5902, // brown
            0xffff01, // yellow
        )

        fun default(name: String, hogPrefix: String, difficulty: Int = 0): Team =
            Team(
                name = name,
                hogNames = (1..MAX_HOGS).map { "$hogPrefix $it" },
                difficulty = difficulty,
            )
    }
}

/** A team placed in a game slot, with its clan color and hog count. */
data class TeamSlot(
    val team: Team,
    val colorIndex: Int,
    val hogCount: Int = 4,
)
