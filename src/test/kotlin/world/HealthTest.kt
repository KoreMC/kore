package org.koremc.world

import org.koremc.registry.BlockState
import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.FEATHER_FALLING
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.PROTECTION
import org.koremc.registry.VanillaData
import org.koremc.registry.assumeGameData
import org.koremc.registry.vanillaData
import org.koremc.registry.withEnchantments
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How hurts are lessened and taken, and what a player's body touches, in a world of the game's own blocks. */
class HealthTest {
    private val blocks get() = data.blocks
    private lateinit var world: FlatWorld
    private lateinit var items: ItemStacks

    @BeforeTest
    fun startWorld() {
        assumeGameData()
        world = FlatWorld(blocks, listOf(block("minecraft:stone")), traits = data.traits)
        items = ItemStacks(data.items, data.creative, blocks)
    }

    private fun block(name: String): BlockState = blocks.defaultOf(name) ?: error("no block $name")

    private fun set(vararg names: String): List<ItemStack> = listOf("helmet", "chestplate", "leggings", "boots").zip(names).map { (piece, kind) -> items.of("minecraft:${kind}_$piece")!! }

    private fun assertNear(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-4f, "$actual isn't $expected")

    @Test
    fun `armour takes a share of a blow by its points, less of a hard one, but tough armour holds`() {
        assertEquals(2 to 0, armourOf("minecraft:iron_helmet"))
        assertEquals(8 to 3, armourOf("minecraft:netherite_chestplate"))
        assertEquals(2 to 0, armourOf("minecraft:turtle_helmet"))
        assertEquals(0 to 0, armourOf("minecraft:elytra"))

        val iron = set("iron", "iron", "iron", "iron")
        assertNear(6f, damageAfter(10f, DamageCause.LAVA, iron, resistance = 0))
        val diamond = set("diamond", "diamond", "diamond", "diamond")
        assertNear(3f, damageAfter(10f, DamageCause.LAVA, diamond, resistance = 0))
        // A fall gets past armour.
        assertNear(10f, damageAfter(10f, DamageCause.FALL, diamond, resistance = 0))
    }

    @Test
    fun `each level of Breach on what struck lets armour keep off 15% less of the blow`() {
        val iron = set("iron", "iron", "iron", "iron")
        assertNear(6f, damageAfter(10f, DamageCause.ATTACK, iron, resistance = 0))
        assertNear(8.4f, damageAfter(10f, DamageCause.ATTACK, iron, resistance = 0, breach = 4))
    }

    @Test
    fun `Resistance and protection enchantments lessen a blow, but nothing lessens the void`() {
        assertNear(6f, damageAfter(10f, DamageCause.FALL, emptyList(), resistance = 2))
        assertNear(0f, damageAfter(10f, DamageCause.FALL, emptyList(), resistance = 5))
        val protected = set("leather", "leather", "leather", "leather").map { it.withEnchantments(listOf(EnchantmentLevel(PROTECTION, 4))) }
        assertNear(3.6f, damageAfter(10f, DamageCause.FALL, protected, resistance = 0))
        val boots = listOf(items.of("minecraft:diamond_boots")!!.withEnchantments(listOf(EnchantmentLevel(FEATHER_FALLING, 4))))
        assertNear(5.2f, damageAfter(10f, DamageCause.FALL, boots, resistance = 0))
        assertNear(4f, damageAfter(4f, DamageCause.VOID, protected, resistance = 5))
        assertEquals(1, armourWear(3f))
        assertEquals(2, armourWear(9f))
    }

    @Test
    fun `after a hurt, only a harder blow hurts, by how much harder, and absorption goes first`() {
        val vitals = Vitals()
        assertEquals(5f, vitals.hurt(5f))
        assertNull(vitals.hurt(3f))
        assertEquals(3f, vitals.hurt(8f))
        assertEquals(12f, vitals.health)

        repeat(10) { vitals.tick() }
        vitals.absorption = 4f
        assertEquals(6f, vitals.hurt(6f))
        assertEquals(0f, vitals.absorption)
        assertEquals(10f, vitals.health)
        assertTrue(vitals.heal(20f))
        assertEquals(MAX_HEALTH, vitals.health)
        assertFalse(vitals.heal(1f))
    }

    @Test
    fun `a player's body finds the lava, fire and cactus it touches, the magma under it, and the block or water its eyes are in`() {
        val feet = BlockPos(0, MIN_Y + 1, 0)
        fun around(x: Float = 0.5f) = world.surroundings(Vec3(x, feet.y.toFloat(), 0.5f), PLAYER_HEIGHT, Vec3(x, feet.y + PLAYER_EYE_HEIGHT, 0.5f))
        assertFalse(around().lava)

        world[feet] = world.liquidState(Liquid.LAVA, 0)
        assertTrue(around().lava)
        world[feet] = block("minecraft:soul_fire")
        assertEquals(2, around().fire)
        world[feet] = block("minecraft:air")

        world[BlockPos(1, feet.y, 0)] = block("minecraft:cactus")
        assertFalse(around().cactus)
        assertTrue(around(x = 0.76f).cactus, "a sixteenth inside its block")
        world[BlockPos(0, feet.y - 1, 0)] = block("minecraft:magma")
        assertTrue(around().magma)

        world[feet.side(Face.UP)] = world.liquidState(Liquid.WATER, 0)
        assertTrue(around().underwater)
        assertTrue(around().water)
        world[feet.side(Face.UP)] = block("minecraft:stone")
        assertTrue(around().suffocating)
        world[feet.side(Face.UP)] = block("minecraft:glass")
        assertFalse(around().suffocating)
    }

    private companion object {
        val data: VanillaData get() = vanillaData
    }
}
