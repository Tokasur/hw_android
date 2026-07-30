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
    fun `a slot may keep its team or take a free one`() {
        val inPlay = slots("Alpha", "Beta")
        assertEquals(
            listOf("Alpha", "Gamma"),
            teamChoices(saved, inPlay, slotIndex = 0).map { it.name },
        )
        assertEquals(
            listOf("Beta", "Gamma"),
            teamChoices(saved, inPlay, slotIndex = 1).map { it.name },
        )
    }

    @Test
    fun `a team already playing elsewhere is not offered twice`() {
        val choices = teamChoices(saved, slots("Alpha", "Beta", "Gamma"), slotIndex = 2)
        assertEquals(listOf("Gamma"), choices.map { it.name })
    }

    @Test
    fun `an anonymous slot may take any team that is free`() {
        val inPlay = listOf(
            TeamSlot(saved[0], colorIndex = 0),
            TeamSlot(Team.default("Team 2", "Hog", difficulty = 3), colorIndex = 1),
        )
        assertEquals(
            listOf("Beta", "Gamma"),
            teamChoices(saved, inPlay, slotIndex = 1).map { it.name },
        )
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
