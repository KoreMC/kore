package org.koremc.world

import org.koremc.registry.AIR
import org.koremc.registry.BlockState
import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.ItemStacks
import org.koremc.registry.VanillaData
import org.koremc.registry.assumeGameData
import org.koremc.registry.vanillaData
import org.koremc.registry.withEnchantments
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cake and candles on it, by the game's own blocks, as Bedrock has them. */
class CakesTest {
    private val blocks get() = data.blocks
    private lateinit var world: FlatWorld
    private lateinit var items: ItemStacks

    // A world of one layer of stone, with the cake on top.
    private val at = BlockPos(0, MIN_Y + 1, 0)

    @BeforeTest
    fun startWorld() {
        assumeGameData()
        world = FlatWorld(blocks, listOf(block("minecraft:stone")))
        items = ItemStacks(data.items, data.creative, blocks)
        world[at] = block("minecraft:cake")
    }

    private fun block(name: String): BlockState = blocks.defaultOf(name) ?: error("no block $name")

    private val cake get() = world.stateAt(at)!!

    @Test
    fun `a candle goes on a whole cake in its own colour, out, and the cake reads as whole to a comparator`() {
        assertEquals(HeldAfter.USED_UP, world.putCandleOn(at, cake, "minecraft:red_candle")?.held)
        assertEquals("minecraft:red_candle_cake", cake.name)
        assertTrue(cake.hasCandle && !cake.candleLit)
        assertEquals(14, world.fullness(at))
        // One candle a cake.
        assertNull(world.putCandleOn(at, cake, "minecraft:candle"))
    }

    @Test
    fun `a bitten cake takes no candle, and reads less for each slice gone`() {
        world.biteCake(at, cake, items)
        assertNull(world.putCandleOn(at, cake, "minecraft:candle"))
        assertEquals(12, world.fullness(at))
    }

    @Test
    fun `a lit candle on a cake goes out, and eating the cake drops the candle, if blocks drop`() {
        world.putCandleOn(at, cake, "minecraft:candle")
        world[at] = blocks.find(cake.name, cake.properties + ("lit" to bit(true)))!!
        assertTrue(cake.candleLit)
        assertEquals("extinguish.candle", world.putOutCandle(at, cake)?.sound?.sound)
        assertTrue(!cake.candleLit)
        assertNull(world.putOutCandle(at, cake))

        assertEquals(listOf("minecraft:candle"), world.biteCake(at, cake, items)?.dropped?.map { it.item.name })
        assertEquals("minecraft:cake", cake.name)

        world.rules.set("doTileDrops", false)
        world[at] = block("minecraft:candle_cake")
        assertEquals(emptyList(), world.biteCake(at, cake, items)?.dropped)
    }

    @Test
    fun `a cake with a candle breaks into the candle alone, Silk Touch or not, and a cake into nothing`() {
        val drops = Drops(data.items, data.creative, blocks)
        val silkTouch = items.of("minecraft:diamond_pickaxe")!!.withEnchantments(listOf(EnchantmentLevel(SILK_TOUCH, 1)))
        assertEquals(listOf("minecraft:red_candle"), drops.of(block("minecraft:red_candle_cake")).map { it.item.name })
        assertEquals(listOf("minecraft:candle"), drops.of(block("minecraft:candle_cake"), tool = silkTouch).map { it.item.name })
        assertEquals(emptyList(), drops.of(block("minecraft:cake")))
    }

    @Test
    fun `the seventh slice takes the cake`() {
        repeat(6) { world.biteCake(at, cake, items) }
        assertEquals(6, cake.number("bite_counter"))
        world.biteCake(at, cake, items)
        assertEquals(AIR, cake.name)
    }

    private companion object {
        val data: VanillaData get() = vanillaData
    }
}
