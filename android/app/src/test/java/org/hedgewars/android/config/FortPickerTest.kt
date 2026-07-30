package org.hedgewars.android.config

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FortPickerTest {

    private val forts = listOf("Cake", "Castle", "Earth", "Plane", "UFO")

    private fun team(name: String, fort: String) = Team(
        name = name,
        hogNames = (1..Team.MAX_HOGS).map { "$name$it" },
        fort = fort,
    )

    private fun slots(vararg forts: String) = forts.mapIndexed { i, f ->
        TeamSlot(team("T$i", f), colorIndex = i)
    }

    @Test
    fun `an explicit fort is never touched`() {
        val resolved = FortPicker.resolveSlots(slots("Castle", "Cake"), forts, Random(1))
        assertEquals(listOf("Castle", "Cake"), resolved.map { it.team.fort })
    }

    @Test
    fun `the marker is replaced by an installed fort`() {
        val resolved = FortPicker.resolveSlots(slots(Team.FORT_RANDOM), forts, Random(7))
        assertTrue(resolved.single().team.fort in forts)
    }

    @Test
    fun `clans get different forts`() {
        val resolved = FortPicker.resolveSlots(
            slots(Team.FORT_RANDOM, Team.FORT_RANDOM, Team.FORT_RANDOM),
            forts,
            Random(3),
        )
        val picked = resolved.map { it.team.fort }
        assertEquals(3, picked.toSet().size)
        assertTrue(picked.all { it in forts })
    }

    @Test
    fun `a random fort avoids the ones already chosen explicitly`() {
        // Two forts installed, one already taken: a correct implementation has
        // exactly one option left, whatever the roll.
        val resolved = FortPicker.resolveSlots(
            listOf(
                TeamSlot(team("A", "Castle"), colorIndex = 0),
                TeamSlot(team("B", Team.FORT_RANDOM), colorIndex = 1),
            ),
            listOf("Castle", "Cake"),
            Random(11),
        )
        assertEquals("Cake", resolved[1].team.fort)
    }

    @Test
    fun `a fort whose content pack is gone is rolled again`() {
        // Sending a name the engine cannot load kills the match while the land
        // is generated, so a vanished fort must not be passed through.
        val resolved = FortPicker.resolveSlots(
            slots("Rocketfort"),
            listOf("Cake"),
            Random(4),
        )
        assertEquals("Cake", resolved.single().team.fort)
        assertEquals("Cake", FortPicker.resolve("Rocketfort", listOf("Cake"), Random(4)))
    }

    @Test
    fun `an unknown fort survives when nothing is installed to replace it`() {
        // Empty list means we cannot know better (and cannot do better than
        // leaving the stored name alone).
        assertEquals("Rocketfort", FortPicker.resolve("Rocketfort", emptyList()))
    }

    @Test
    fun `a team with no fort of its own defaults to the random marker`() {
        // This default IS the fix for "forts never change": every team the
        // player did not give a fort to rolls one per match.
        assertEquals(
            Team.FORT_RANDOM,
            Team(name = "T", hogNames = (1..Team.MAX_HOGS).map { "H$it" }).fort,
        )
        assertEquals(Team.FORT_RANDOM, Team.default("T", "H").fort)
    }

    @Test
    fun `the overloads the launcher calls roll a fresh fort each time`() {
        // GameLauncher passes no Random: a fixed seed baked into the default
        // parameter would silently reinstate "always the same fort".
        val single = (1..40).map { FortPicker.resolve(Team.FORT_RANDOM, forts) }
        assertTrue("single-team rolls never varied: $single", single.toSet().size > 1)

        val perSlot = (1..40).map {
            FortPicker.resolveSlots(slots(Team.FORT_RANDOM), forts).single().team.fort
        }
        assertTrue("slot rolls never varied: $perSlot", perSlot.toSet().size > 1)
    }

    @Test
    fun `teams of the same clan share the fort the engine will draw`() {
        val resolved = FortPicker.resolveSlots(
            listOf(
                TeamSlot(team("A", Team.FORT_RANDOM), colorIndex = 0),
                TeamSlot(team("B", Team.FORT_RANDOM), colorIndex = 0),
                TeamSlot(team("C", Team.FORT_RANDOM), colorIndex = 1),
            ),
            forts,
            Random(5),
        )
        assertEquals(resolved[0].team.fort, resolved[1].team.fort)
        assertNotEquals(resolved[0].team.fort, resolved[2].team.fort)
    }

    @Test
    fun `more clans than forts repeats instead of failing`() {
        val two = listOf("Cake", "Castle")
        val resolved = FortPicker.resolveSlots(
            slots(Team.FORT_RANDOM, Team.FORT_RANDOM, Team.FORT_RANDOM, Team.FORT_RANDOM),
            two,
            Random(2),
        )
        assertEquals(4, resolved.size)
        assertTrue(resolved.all { it.team.fort in two })
        assertEquals(2, resolved.take(2).map { it.team.fort }.toSet().size)
    }

    @Test
    fun `an empty fort list falls back to a real name`() {
        val resolved = FortPicker.resolveSlots(slots(Team.FORT_RANDOM), emptyList(), Random(1))
        assertEquals(Team.FORT_FALLBACK, resolved.single().team.fort)
        assertEquals(Team.FORT_FALLBACK, FortPicker.resolve(Team.FORT_RANDOM, emptyList()))
    }

    @Test
    fun `a single team resolves too`() {
        assertEquals("Castle", FortPicker.resolve("Castle", forts))
        assertTrue(FortPicker.resolve(Team.FORT_RANDOM, forts, Random(9)) in forts)
    }

    @Test
    fun `successive matches do not always pick the same fort`() {
        // With 5 forts, 40 draws landing on a single name would mean the roll
        // is not random at all (which is the bug this guards).
        val picked = (1..40).map {
            FortPicker.resolve(Team.FORT_RANDOM, forts, Random(it)).also { f ->
                assertTrue(f in forts)
            }
        }
        assertTrue("forts never varied: $picked", picked.toSet().size > 1)
    }
}
