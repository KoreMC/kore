package org.koremc.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How a player's food bar goes down as they tire, and back up as they eat. */
class HungerTest {
    @Test
    fun `a new player starts full, with some saturation`() {
        val hunger = Hunger()
        assertEquals(20, hunger.food)
        assertEquals(5f, hunger.saturation)
        assertTrue(hunger.full)
    }

    @Test
    fun `every 4 exhaustion takes a point of saturation, then of food once it's gone`() {
        val hunger = Hunger()
        hunger.exhaust(3f, peace = false)
        assertEquals(5f, hunger.saturation)
        hunger.exhaust(1f, peace = false)
        assertEquals(4f, hunger.saturation)
        assertEquals(0f, hunger.exhaustion)

        hunger.exhaust(16f, peace = false)
        assertEquals(0f, hunger.saturation)
        assertEquals(20, hunger.food)
        hunger.exhaust(8f, peace = false)
        assertEquals(18, hunger.food)
        assertFalse(hunger.full)
    }

    @Test
    fun `in peace, tiring takes saturation but never food`() {
        val hunger = Hunger()
        hunger.saturation = 0f
        hunger.exhaust(40f, peace = true)
        assertEquals(20, hunger.food)
    }

    @Test
    fun `eating fills the bar up to full, with saturation by the food's modifier, never more than the food there is`() {
        val hunger = Hunger()
        hunger.food = 2
        hunger.saturation = 0f
        hunger.eat(4, 0.3f)
        assertEquals(6, hunger.food)
        assertEquals(4 * 0.3f * 2, hunger.saturation)

        // A golden carrot's saturation would be 14.4, but there's only 12 food to hold it up.
        hunger.eat(6, 1.2f)
        assertEquals(12, hunger.food)
        assertEquals(12f, hunger.saturation)

        hunger.eat(20, 0.1f)
        assertEquals(20, hunger.food)
    }

    @Test
    fun `respawning fills the bar again`() {
        val hunger = Hunger()
        hunger.food = 3
        hunger.saturation = 0f
        hunger.exhaustion = 2f
        hunger.reset()
        assertEquals(20, hunger.food)
        assertEquals(5f, hunger.saturation)
        assertEquals(0f, hunger.exhaustion)
    }
}
