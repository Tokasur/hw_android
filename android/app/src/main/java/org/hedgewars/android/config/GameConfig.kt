package org.hedgewars.android.config

/** Map generator ids (QTfrontend MapModel / engine e$mapgen). */
enum class MapGen(val id: Int) {
    REGULAR(0),
    MAZE(1),
    PERLIN(2),
    // DRAWN(3) — hand-drawn maps are not supported in this port yet
    /** One symmetric fort per clan; feature_size is the distance between them. */
    FORTS(4),
}

sealed class MapChoice {
    /** Randomly generated terrain with the given generator and theme. */
    data class Generated(
        val mapGen: MapGen = MapGen.REGULAR,
        val templateFilter: Int = 0,
        val featureSize: Int = 50,
        val mazeSize: Int = 0,
    ) : MapChoice()

    /** A named map from Data/Maps (mission maps carry their own script). */
    data class Named(val mapName: String) : MapChoice()
}

/**
 * Everything needed to configure one local game (solo vs CPU or hotseat).
 */
data class GameConfig(
    val teams: List<TeamSlot>,
    val scheme: Scheme = Scheme.DEFAULT,
    val weapons: WeaponSet = WeaponSet.DEFAULT,
    val map: MapChoice = MapChoice.Generated(),
    val theme: String = "Nature",
    val seed: String = newSeed(),
    /** Multiplayer style script, e.g. "Scripts/Multiplayer/Random_Weapon.lua". */
    val script: String? = null,
) {
    companion object {
        fun newSeed(): String = "{" + java.util.UUID.randomUUID().toString() + "}"
    }
}

/**
 * A single mission/training/campaign game: the engine gets one mission team
 * and the mission script; everything else comes from the script itself.
 */
data class MissionConfig(
    val team: Team,
    /** Script path relative to Data, e.g. "Missions/Training/Basic_Training_-_Bazooka.lua". */
    val script: String,
    /** Campaign name when this is a campaign mission (drives V-var persistence). */
    val campaign: String? = null,
)
