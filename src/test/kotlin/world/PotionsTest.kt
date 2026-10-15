package org.koremc.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What drinking each potion gives, as Bedrock has it. */
class PotionsTest {
    @Test
    fun `water and the potions brewing starts from give nothing`() {
        for (id in 0..4) assertTrue(potionEffects(id).isEmpty(), "potion $id")
    }

    @Test
    fun `extended potions last longer, and strong ones are a level up for less time`() {
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.SPEED), 3600)), potionEffects(14))
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.SPEED), 9600)), potionEffects(15))
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.SPEED, 1), 1800)), potionEffects(16))
    }

    @Test
    fun `Bedrock's extended poison and regeneration last two minutes, and the strong ones 22 and a half seconds`() {
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.POISON), 2400)), potionEffects(26))
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.POISON, 1), 450)), potionEffects(27))
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.REGENERATION), 2400)), potionEffects(29))
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.REGENERATION, 1), 450)), potionEffects(30))
    }

    @Test
    fun `the turtle master slows and protects at once, and healing is instant`() {
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.SLOWNESS, 3), 400), PotionEffect(EffectLevel(Effect.RESISTANCE, 2), 400)), potionEffects(37))
        assertEquals(listOf(PotionEffect(EffectLevel(Effect.INSTANT_HEALTH, 1), 0)), potionEffects(22))
    }

    @Test
    fun `a potion the game doesn't have gives nothing`() {
        assertTrue(potionEffects(47).isEmpty())
        assertTrue(potionEffects(-1).isEmpty())
    }
}
