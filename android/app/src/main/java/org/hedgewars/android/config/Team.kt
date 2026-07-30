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
    /**
     * Fort used in "Forts" map mode — a name under Data/Forts, or
     * [FORT_RANDOM] to draw a different one at every match (the default, so
     * forts vary out of the box like they do on the desktop, where every team
     * gets a random fort when it is created).
     */
    val fort: String = FORT_RANDOM,
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

        /**
         * "Pick a fort at random for this match". Never sent to the engine:
         * it is resolved at launch (see config/FortPicker) because an unknown
         * fort name is fatal — the engine loads `<name>L.png` as a critical
         * asset while generating a forts map (uLand.pas MakeFortsMap).
         *
         * A star cannot collide with a real fort name (they come from file
         * names under Data/Forts and from content packs).
         */
        const val FORT_RANDOM = "*"

        /** Last-resort fort, for the impossible case of an empty Data/Forts. */
        const val FORT_FALLBACK = "Plane"

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
