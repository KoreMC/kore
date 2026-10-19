package org.koremc.world

import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemVersion
import org.koremc.registry.SHARPNESS
import org.koremc.registry.withEnchantments
import kotlin.math.abs
import kotlin.random.Random
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
    fun `a shield wears by a hit it blocks, rounded up, from 3 up`() {
        assertEquals(0, shieldWear(2.9f))
        assertEquals(3, shieldWear(3f))
        assertEquals(8, shieldWear(7.5f))
    }

    @Test
    fun `an axe's hit disables a shield, and nothing else's does`() {
        assertTrue(disablesShields("minecraft:iron_axe"))
        assertFalse(disablesShields("minecraft:iron_pickaxe"))
        assertFalse(disablesShields(null))
    }

    @Test
    fun `a shield covers hits from in front, however high, but not from behind`() {
        val blocker = Vec3(0f, 0f, 0f)
        // Facing south, toward positive z.
        assertTrue(shieldCovers(blocker, 0f, Vec3(0.5f, 5f, 2f)))
        assertFalse(shieldCovers(blocker, 0f, Vec3(0f, 0f, -2f)))
        // Facing west, toward negative x.
        assertTrue(shieldCovers(blocker, 90f, Vec3(-2f, 0f, 0f)))
        assertFalse(shieldCovers(blocker, 90f, Vec3(2f, 0f, 1f)))
    }

    @Test
    fun `each level of Thorns makes a prick likelier, of anything from 1 to 5`() {
        val random = Random(1)
        val pricks = List(10_000) { thornsPrick(3, random) }
        val share = pricks.count { it != null } / 10_000f
        assertTrue(share in 0.42f..0.48f, "$share of hits pricked")
        assertTrue(pricks.filterNotNull().all { it >= 1f && it < 5f })
        assertEquals(null, thornsPrick(0, random))
        assertTrue(List(100) { thornsPrick(7, random) }.all { it != null }, "from seven levels, every time")
    }

    @Test
    fun `a mace's smash hurts 4 more a block for 3 blocks, then 2 for 5, then 1, and half more a block a level of Density`() {
        assertTrue(smashesWith("minecraft:mace"))
        assertFalse(smashesWith("minecraft:iron_sword"))
        assertEquals(6f, smashDamage(1.5f, density = 0))
        assertEquals(12f, smashDamage(3f, density = 0))
        assertEquals(16f, smashDamage(5f, density = 0))
        assertEquals(24f, smashDamage(10f, density = 0))
        assertEquals(34f, smashDamage(10f, density = 2))
    }

    @Test
    fun `Wind Burst launches a smasher up faster for each level`() {
        assertEquals(1.2f, windBurstLift(1))
        assertEquals(1.75f, windBurstLift(2))
        assertNear(2.2f, windBurstLift(3))
    }

    @Test
    fun `a bow draws harder the longer it's drawn, all the way in a second, and shoots nothing let go too soon`() {
        assertEquals(null, bowForce(2))
        assertNear(0.1075f, bowForce(3)!!)
        assertNear(0.4167f, bowForce(10)!!)
        assertEquals(1f, bowForce(20))
        assertEquals(1f, bowForce(60))
    }

    @Test
    fun `a crossbow loads in a second and a quarter, a quarter sooner for each level of Quick Charge, and sounds 16 ticks of every 25 in`() {
        assertEquals(listOf(25, 20, 15, 10), (0..3).map(::crossbowChargeTicks))
        assertEquals(listOf(16, 13, 10, 6), (0..3).map(::crossbowMiddleTick))
    }

    @Test
    fun `an arrow hurts by its speed, more for Power, and a critical one by chance up to half again and a point more`() {
        assertEquals(0f, powerDamage(0))
        assertEquals(1f, powerDamage(1))
        assertEquals(3f, powerDamage(5))
        assertEquals(6f, arrowDamage(2f, 3f, critical = false))
        val random = Random(1)
        val critical = List(1000) { arrowDamage(2f, 3f, critical = true, random) }
        assertTrue(critical.all { it in 6f..10f } && 10f in critical && 6f in critical)
    }

    @Test
    fun `Impaling makes a trident hit someone in water or rain two and a half points harder a level, rounded down`() {
        assertEquals(listOf(0f, 2f, 5f, 7f, 10f, 12f), (0..5).map(::impalingDamage))
    }

    @Test
    fun `each piece of netherite takes a tenth of a hit's knockback`() {
        assertNear(0.2f, knockbackResistance(listOf(item("netherite_helmet"), item("diamond_chestplate"), item("netherite_boots"), null)))
        assertEquals(0f, knockbackResistance(listOf(null, null, null, null)))
    }
}
