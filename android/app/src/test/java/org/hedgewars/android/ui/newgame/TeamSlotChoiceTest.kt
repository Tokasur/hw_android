package org.hedgewars.android.ui.newgame

import org.hedgewars.android.config.Team
import org.hedgewars.android.config.TeamSlot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which team a local-multiplayer slot may hold. The player picks; the port used
 * to hand out saved teams in list order, which made choosing pointless.
 */
class TeamSlotChoiceTest {

    private fun team(name: String, difficulty: Int = 0) = Team(
        name = name,
        hogNames = (1..Team.MAX_HOGS).map { "$name$it" },
        difficulty = difficulty,
    )

    private val saved = listOf(team("Alpha"), team("Beta", difficulty = 3), team("Gamma"))

    private fun slots(vararg names: String) = names.mapIndexed { i, n ->
        TeamSlot(saved.first { it.name == n }, colorIndex = i)
    }

    @Test
    fun `taking a free team just replaces the one on that line`() {
        val after = pickTeamInto(slots("Alpha", "Beta"), slotIndex = 0, picked = saved[2])
        assertEquals(listOf("Gamma", "Beta"), after.map { it.team.name })
    }

    @Test
    fun `taking a team that already plays trades the two lines`() {
        // The case that matters with only two teams saved: swapping who is who.
        val after = pickTeamInto(slots("Alpha", "Beta"), slotIndex = 0, picked = saved[1])
        assertEquals(listOf("Beta", "Alpha"), after.map { it.team.name })
    }

    @Test
    fun `trading teams leaves each line with its own human or CPU setting`() {
        val inPlay = listOf(
            TeamSlot(saved[0].copy(difficulty = 0), colorIndex = 0),   // human seat
            TeamSlot(saved[1].copy(difficulty = 4), colorIndex = 1),   // CPU seat
        )
        val after = pickTeamInto(inPlay, slotIndex = 0, picked = saved[1])
        assertEquals(listOf("Beta", "Alpha"), after.map { it.team.name })
        assertEquals(listOf(0, 4), after.map { it.team.difficulty })
        // Colours belong to the seat too.
        assertEquals(listOf(0, 1), after.map { it.colorIndex })
    }

    @Test
    fun `picking the team already on the line changes nothing`() {
        val inPlay = slots("Alpha", "Beta")
        assertEquals(inPlay, pickTeamInto(inPlay, slotIndex = 1, picked = saved[1]))
    }

    @Test
    fun `an anonymous line can take a saved team without disturbing the rest`() {
        val inPlay = listOf(
            TeamSlot(saved[0], colorIndex = 0),
            TeamSlot(Team.default("Team 2", "Hog", difficulty = 3), colorIndex = 1),
        )
        val after = pickTeamInto(inPlay, slotIndex = 1, picked = saved[2])
        assertEquals(listOf("Alpha", "Gamma"), after.map { it.team.name })
        assertEquals(3, after[1].team.difficulty)
    }

    @Test
    fun `a new participant starts on the first team not playing yet`() {
        val next = nextFreeTeam(saved, slots("Alpha"))
        assertEquals("Beta", next.name)
        // …and keeps the human/CPU level it was saved with.
        assertEquals(3, next.difficulty)
    }

    @Test
    fun `once every team plays a new participant is anonymous`() {
        val next = nextFreeTeam(saved, slots("Alpha", "Beta", "Gamma"))
        assertEquals("Team 4", next.name)
        assertEquals(3, next.difficulty)
        assertEquals(Team.MAX_HOGS, next.hogNames.size)
    }

    @Test
    fun `no saved team at all still yields a playable participant`() {
        val next = nextFreeTeam(emptyList(), emptyList())
        assertEquals("Team 1", next.name)
        assertEquals(Team.FORT_RANDOM, next.fort)
    }
}
