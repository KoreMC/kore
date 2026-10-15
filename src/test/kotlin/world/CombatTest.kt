package org.koremc.world

import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemVersion
import org.koremc.registry.SHARPNESS
import org.koremc.registry.withEnchantments
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How hard players hit, as Bedrock has it. */
class CombatTest {
    private fun item(name: String) = ItemStack(ItemDefinition("minecraft:$name", 1, componentBased = false, ItemVersion.NONE, maxStackSize = 1))

    private fun assertNear(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-4f, "$actual isn't $expected")

    @Test
    fun `weapons and tools hit by their kind and material, a point over their tooltip, and anything else as a hand`() {
        assertEquals(8f, attackDamage(item("diamond_sword")))
        assertEquals(5f, attackDamage(item("golden_sword")))
        assertEquals(5f, attackDamage(item("copper_axe")))
        assertEquals(3f, attackDamage(item("wooden_hoe")))
        assertEquals(6f, attackDamage(item("netherite_spear")))
        assertEquals(9f, attackDamage(item("trident")))
        assertEquals(1f, attackDamage(item("stick")))
        assertEquals(1f, attackDamage(null))
    }

    @Test
    fun `a hit wears a weapon a use, a tool two, and anything else not at all`() {
        assertEquals(1, wearFromHitting("minecraft:iron_sword"))
        assertEquals(1, wearFromHitting("minecraft:mace"))
        assertEquals(2, wearFromHitting("minecraft:iron_pickaxe"))
        assertEquals(2, wearFromHitting("minecraft:stone_axe"))
        assertEquals(0, wearFromHitting("minecraft:stick"))
    }

    @Test
    fun `Strength makes each hit 30 percent harder and a point more a level, and Weakness a fifth softer and half a point less`() {
        assertNear(11.4f, withStrength(8f, strength = 1, weakness = 0))
        assertNear(15.82f, withStrength(8f, strength = 2, weakness = 0))
        assertNear(5.9f, withStrength(8f, strength = 0, weakness = 1))
        assertEquals(0f, withStrength(0.5f, strength = 0, weakness = 1))
    }

    @Test
    fun `Sharpness adds a point and a quarter a level, rounded down, and a critical hit is half again harder`() {
        fun sharp(level: Int) = sharpnessDamage(item("iron_sword").withEnchantments(listOf(EnchantmentLevel(SHARPNESS, level))))
        assertEquals(listOf(1f, 2f, 3f, 5f, 6f), (1..5).map(::sharp))
        assertEquals(0f, sharpnessDamage(null))
        assertEquals(12f, critical(8f))
        assertFalse(critsWith("minecraft:iron_spear"))
        assertTrue(critsWith("minecraft:iron_sword") && critsWith(null))
    }

    @Test
    fun `in creative, swords, the mace and the trident only hit, while anything else breaks blocks`() {
        assertFalse(breaksInCreative("minecraft:diamond_sword"))
        assertFalse(breaksInCreative("minecraft:mace"))
        assertFalse(breaksInCreative("minecraft:trident"))
        assertTrue(breaksInCreative("minecraft:iron_spear"))
        assertTrue(breaksInCreative("minecraft:iron_axe"))
        assertTrue(breaksInCreative(null))
    }

    @Test
    fun `each piece of netherite takes a tenth of a hit's knockback`() {
        assertNear(0.2f, knockbackResistance(listOf(item("netherite_helmet"), item("diamond_chestplate"), item("netherite_boots"), null)))
        assertEquals(0f, knockbackResistance(listOf(null, null, null, null)))
    }
}
