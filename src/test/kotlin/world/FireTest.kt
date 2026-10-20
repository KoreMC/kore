package org.koremc.world

import org.koremc.protocol.Difficulty
import org.koremc.registry.AIR
import org.koremc.registry.BlockState
import org.koremc.registry.ItemStacks
import org.koremc.registry.VanillaData
import org.koremc.registry.assumeGameData
import org.koremc.registry.vanillaData
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fire, and the nether portals it opens, in a world of the game's own blocks. */
class FireTest {
    private val blocks get() = data.blocks
    private lateinit var world: FlatWorld
    private lateinit var items: ItemStacks

    // A world of stone, dirt and grass on top, and the space above it free.
    private val ground = BlockPos(0, MIN_Y + 2, 0)
    private val above = ground.side(Face.UP)

    private val flintAndSteel get() = items.of(FLINT_AND_STEEL)!!

    @BeforeTest
    fun startWorld() {
        assumeGameData()
        world = FlatWorld(blocks, listOf("minecraft:stone", "minecraft:dirt", "minecraft:grass_block").map(::block), traits = data.traits)
        items = ItemStacks(data.items, data.creative, blocks)
    }

    private fun block(name: String, vararg properties: Pair<String, Any>): BlockState {
        val default = blocks.defaultOf(name) ?: error("no block $name")
        return if (properties.isEmpty()) default else blocks.stateOf(name, default.properties + properties)
    }

    private fun nameAt(pos: BlockPos) = world.stateAt(pos)?.name

    /** Ticks the world's blocks [times] times, by [random]. */
    private fun tick(times: Int, random: Random = Random(1)) = repeat(times) { world.tickBlocks(random) }

    @Test
    fun `flint and steel starts fire on the face it's used on, wearing, and a fire charge does, used up`() {
        val lit = world.ignite(ground, Face.UP, flintAndSteel)
        assertNotNull(lit)
        assertEquals(FIRE, nameAt(above))
        assertEquals(HeldAfter.WORN, lit.held)
        assertEquals("ignite", lit.sound?.sound)

        val beside = BlockPos(3, above.y, 0)
        val charged = world.ignite(beside.side(Face.DOWN), Face.UP, items.of(FIRE_CHARGE)!!)
        assertEquals(HeldAfter.USED_UP, charged?.held)
        assertEquals(listOf(1009), charged?.events?.map { it.event })
        assertEquals(FIRE, nameAt(beside))
    }

    @Test
    fun `fire needs a whole top under it, or something beside it that catches, and soul fire burns on soul sand`() {
        val floating = BlockPos(0, above.y + 3, 0)
        world[floating] = block("minecraft:stone")
        assertNull(world.ignite(floating, Face.EAST, flintAndSteel), "in the air beside stone")
        world[floating.side(Face.EAST, 2)] = block("minecraft:oak_planks")
        assertNotNull(world.ignite(floating, Face.EAST, flintAndSteel), "beside planks, which catch")

        world[ground] = block("minecraft:soul_sand")
        world.ignite(ground, Face.UP, flintAndSteel)
        assertEquals(SOUL_FIRE, nameAt(above))
        assertNull(world.ignite(BlockPos(9, above.y, 9), Face.UP, flintAndSteel), "not in a block")
    }

    @Test
    fun `flint and steel lights a campfire or candle that's out, rather than starting fire`() {
        world[above] = block("minecraft:campfire", "extinguished" to bit(true))
        world.ignite(above, Face.UP, flintAndSteel)
        assertEquals(bit(false), world.stateAt(above)?.properties?.get("extinguished"))
        assertEquals(AIR, nameAt(above.side(Face.UP)))

        world[above] = block("minecraft:candle")
        world.ignite(above, Face.UP, flintAndSteel)
        assertEquals(bit(true), world.stateAt(above)?.properties?.get("lit"))
    }

    @Test
    fun `a splash of water douses fire, campfires and candles where it lands and on each side, but no further`() {
        world[above] = block(FIRE)
        world[above.side(Face.EAST)] = block("minecraft:campfire", "extinguished" to 0.toByte())
        world[above.side(Face.NORTH)] = block("minecraft:candle", "lit" to 1.toByte())
        val far = above.side(Face.EAST).side(Face.EAST)
        world[far] = block(FIRE)

        val doused = assertNotNull(world.douse(above))
        assertEquals(setOf(above, above.side(Face.EAST), above.side(Face.NORTH)), doused.reshaped.toSet())
        assertEquals(AIR, nameAt(above))
        assertEquals(1.toByte(), world.stateAt(above.side(Face.EAST))?.properties?.get("extinguished"))
        assertEquals(0.toByte(), world.stateAt(above.side(Face.NORTH))?.properties?.get("lit"))
        assertEquals(FIRE, nameAt(far))
        assertNull(world.douse(above), "nothing's left burning")
    }

    @Test
    fun `hitting fire puts it out, with its sound`() {
        world.ignite(ground, Face.UP, flintAndSteel)
        val out = world.putOutFire(ground, Face.UP)
        assertEquals("extinguish.fire", out?.sounds?.single()?.sound)
        assertEquals(AIR, nameAt(above))
        assertNull(world.putOutFire(ground, Face.UP))
    }

    @Test
    fun `fire on what doesn't catch burns out before long, unless on netherrack, or with the doFireTick rule off`() {
        world.ignite(ground, Face.UP, flintAndSteel)
        tick(2000)
        assertEquals(AIR, nameAt(above))

        world[ground] = block("minecraft:netherrack")
        world.ignite(ground, Face.UP, flintAndSteel)
        tick(2000)
        assertEquals(FIRE, nameAt(above), "on netherrack, for ever")

        world[ground] = block("minecraft:grass_block")
        world.set(above, block(AIR))
        world.rules.set("doFireTick", false)
        world.ignite(ground, Face.UP, flintAndSteel)
        tick(2000)
        assertEquals(FIRE, nameAt(above))
    }

    @Test
    fun `fire spreads through wood, burning it away`() {
        // A floor of planks, three blocks up, and fire lit on it.
        val floor = (0..6).flatMap { x -> (0..6).map { z -> BlockPos(x, above.y + 2, z) } }
        floor.forEach { world[it] = block("minecraft:oak_planks") }
        world.ignite(floor.first(), Face.UP, flintAndSteel)
        tick(3000, Random(7))
        val burnt = floor.count { nameAt(it) != "minecraft:oak_planks" }
        assertTrue(burnt > floor.size / 2, "$burnt of ${floor.size} burnt")
    }

    @Test
    fun `lightning starts fires where it strikes, at normal difficulty, but not on peaceful`() {
        world.difficulty = Difficulty.PEACEFUL
        world.strike(above, mutableListOf(), mutableListOf(), Random(1))
        assertEquals(AIR, nameAt(above))

        world.difficulty = Difficulty.NORMAL
        val changed = mutableListOf<BlockPos>()
        world.strike(above, changed, mutableListOf(), Random(1))
        assertEquals(FIRE, nameAt(above))
        assertTrue(above in changed)
    }

    @Test
    fun `lava sets fire to what catches near it`() {
        world[above] = world.liquidState(Liquid.LAVA, 0)
        for (x in -1..1) for (z in -1..1) if (x != 0 || z != 0) world[BlockPos(x, above.y, z)] = block("minecraft:oak_planks")
        val growing = Growing(Random(3))
        repeat(200) { world.lavaSetsFire(above, world.stateAt(above)!!, growing) }
        assertTrue(growing.changed.any { nameAt(it) == FIRE })
    }

    /** An obsidian frame around an inside [width] across and [height] up, its bottom corner at [corner], running [across], without its corners. */
    private fun frame(corner: BlockPos, width: Int, height: Int, across: Face = Face.EAST) {
        val obsidian = block("minecraft:obsidian")
        for (step in 0 until width) {
            world[corner.side(across, step).side(Face.DOWN)] = obsidian
            world[corner.side(across, step).side(Face.UP, height)] = obsidian
        }
        for (up in 0 until height) {
            world[corner.side(across.opposite).side(Face.UP, up)] = obsidian
            world[corner.side(across, width).side(Face.UP, up)] = obsidian
        }
    }

    private fun inside(corner: BlockPos, width: Int, height: Int, across: Face = Face.EAST) =
        (0 until width).flatMap { step -> (0 until height).map { up -> corner.side(across, step).side(Face.UP, up) } }

    @Test
    fun `fire in an obsidian frame opens a nether portal across its whole inside, along either axis, corners or not`() {
        val corner = BlockPos(0, above.y + 1, 0)
        frame(corner, 2, 3)
        world.ignite(corner.side(Face.DOWN), Face.UP, flintAndSteel)
        assertTrue(inside(corner, 2, 3).all { world.stateAt(it)?.let { s -> s.name == NETHER_PORTAL && s.properties["portal_axis"] == "x" } == true })

        val other = BlockPos(10, above.y + 1, 0)
        frame(other, 4, 5, Face.SOUTH)
        world.ignite(other.side(Face.SOUTH, 2), Face.UP, flintAndSteel) // On the frame's bottom, from inside
        assertTrue(inside(other, 4, 5, Face.SOUTH).all { world.stateAt(it)?.properties?.get("portal_axis") == "z" })
    }

    @Test
    fun `a frame too small, too big, or with something inside opens no portal, and fire burns there instead`() {
        val small = BlockPos(0, above.y + 1, 0)
        frame(small, 1, 3)
        world.ignite(small.side(Face.DOWN), Face.UP, flintAndSteel)
        assertEquals(FIRE, nameAt(small))

        val big = BlockPos(10, above.y + 1, 0)
        frame(big, 22, 3)
        world.ignite(big.side(Face.DOWN), Face.UP, flintAndSteel)
        assertEquals(FIRE, nameAt(big))

        val blocked = BlockPos(40, above.y + 1, 0)
        frame(blocked, 2, 3)
        world[blocked.side(Face.UP, 2)] = block("minecraft:stone")
        world.ignite(blocked.side(Face.DOWN), Face.UP, flintAndSteel)
        assertEquals(FIRE, nameAt(blocked))

        val largest = BlockPos(80, above.y + 1, 0)
        frame(largest, 21, 21)
        world.ignite(largest.side(Face.DOWN), Face.UP, flintAndSteel)
        assertTrue(inside(largest, 21, 21).all { nameAt(it) == NETHER_PORTAL })
    }

    @Test
    fun `breaking a portal's frame closes all of it at once, breaking and dropping nothing`() {
        val corner = BlockPos(0, above.y + 1, 0)
        frame(corner, 3, 4)
        world.ignite(corner.side(Face.DOWN), Face.UP, flintAndSteel)
        val frameBlock = corner.side(Face.WEST).side(Face.UP)
        world[frameBlock] = block(AIR)
        world.settle(listOf(frameBlock))
        val broken = (1..2).flatMap { world.tickBlocks().broken.keys }
        assertEquals(inside(corner, 3, 4).toSet(), broken.toSet())
        assertTrue(inside(corner, 3, 4).all { nameAt(it) == AIR })
    }

    private companion object {
        val data: VanillaData get() = vanillaData
    }
}
