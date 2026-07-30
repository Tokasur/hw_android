package org.hedgewars.android.config

import kotlin.random.Random

/**
 * Turns [Team.FORT_RANDOM] into a real fort name, once per match.
 *
 * The desktop rolls a random fort when a team is *created*
 * (QTfrontend/hwform.cpp, pageeditteam.cpp) and re-rolls everything for every
 * quick game (QTfrontend/game.cpp SendQuickConfig). This port keeps a "random"
 * marker on the team instead, so a team the player never gave a fort to shows
 * a different one at every match, while an explicit choice is honoured
 * forever.
 *
 * Two rules matter for how a forts map looks:
 * - the engine draws one fort per CLAN, taken from the clan's first team
 *   (uLand.pas MakeFortsMap), so clans must not end up with the same fort —
 *   four identical castles is exactly what this fixes;
 * - a fort name that has no image is a fatal engine error, so an empty list
 *   falls back to [Team.FORT_FALLBACK] rather than to the marker.
 */
object FortPicker {

    /** Resolves the marker for one team (mission games have a single team). */
    fun resolve(fort: String, available: List<String>, random: Random = Random): String =
        if (!needsRoll(fort, available)) fort else pick(available, emptySet(), random)

    /**
     * A fort needs rolling when it is the marker — or when its images are gone
     * (the content pack that brought it was uninstalled). Sending a name the
     * engine cannot load would kill the match while the land is generated, so
     * a vanished fort is treated as "no fort chosen" rather than passed on.
     */
    private fun needsRoll(fort: String, available: List<String>): Boolean =
        fort == Team.FORT_RANDOM || (available.isNotEmpty() && fort !in available)

    /**
     * Resolves every slot, handing out distinct forts as long as there are
     * enough to go round. Slots of the same clan (same [TeamSlot.colorIndex])
     * share the one the engine will actually draw.
     */
    fun resolveSlots(
        slots: List<TeamSlot>,
        available: List<String>,
        random: Random = Random,
    ): List<TeamSlot> {
        val taken = slots.map { it.team.fort }
            .filterNot { needsRoll(it, available) }
            .toMutableSet()
        val perClan = mutableMapOf<Int, String>()
        return slots.map { slot ->
            if (!needsRoll(slot.team.fort, available)) return@map slot
            val fort = perClan.getOrPut(slot.colorIndex) {
                pick(available, taken, random).also { taken += it }
            }
            slot.copy(team = slot.team.copy(fort = fort))
        }
    }

    private fun pick(available: List<String>, taken: Set<String>, random: Random): String {
        if (available.isEmpty()) return Team.FORT_FALLBACK
        // Prefer an unused one; with more clans than forts, repeats are fine
        // (the desktop allows two teams to share a fort too).
        val fresh = available.filterNot { it in taken }
        val pool = fresh.ifEmpty { available }
        return pool[random.nextInt(pool.size)]
    }
}
