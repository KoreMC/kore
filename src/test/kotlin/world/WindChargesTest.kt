package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.AIR
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindChargesTest {
    private val stone = BlockState("minecraft:stone", NbtMap.EMPTY, 18168865)
    private val palette = BlockPalette(listOf(BlockState(AIR, NbtMap.EMPTY, 18168865), stone))

    // One layer of stone at the bottom of the world, its top at y = -63.
    private val world = FlatWorld(palette, listOf(stone))

    @Test
    fun `a burst pushes away from itself, toward the eyes, the less the further off the feet are, to nothing at twice its power`() {
        val at = Vec3(0f, 0f, 0f)
        // Halfway out to its reach, square on: half as hard, toward the eyes.
        val push = assertNotNull(burstPush(at, power = 1.2, knockback = 1.22, feet = Vec3(1.2f, 0f, 0f), eyes = Vec3(1.2f, 1.62f, 0f), seen = 1.0))
        val toEyes = kotlin.math.sqrt(1.2 * 1.2 + 1.62 * 1.62)
        assertEquals(0.61 * 1.2 / toEyes, push.x.toDouble(), 1e-4)
        assertEquals(0.61 * 1.62 / toEyes, push.y.toDouble(), 1e-4)
        assertEquals(0f, push.z)

        // Half hidden, half as hard again.
        val hidden = assertNotNull(burstPush(at, 1.2, 1.22, Vec3(1.2f, 0f, 0f), Vec3(1.2f, 1.62f, 0f), seen = 0.5))
        assertEquals(push.x / 2, hidden.x, 1e-5f)

        assertNull(burstPush(at, 1.2, 1.22, Vec3(2.5f, 0f, 0f), Vec3(2.5f, 1.62f, 0f), seen = 1.0))
    }

    @Test
    fun `a burst reaches all of what's open to it, and none of what's behind a wall`() {
        val at = Vec3(0.5f, -62.5f, 0.5f)
        val box = Box.of(Vec3(2.5f, -63f, 0.5f), 0.6f, 1.8f)
        assertEquals(1.0, world.exposure(at, box))

        for (y in -63..-60) world[BlockPos(1, y, 0)] = stone
        assertEquals(0.0, world.exposure(at, box))
    }

    @Test
    fun `a wind charge pushes a player standing on it straight up, but one too far off not at all`() {
        val at = Vec3(0.5f, -63f, 0.5f)
        val feet = Vec3(0.5f, -63f, 0.5f)
        val push = assertNotNull(world.windBurstPush(at, feet, Vec3(0.5f, -61.38f, 0.5f), Box.of(feet, 0.6f, 1.8f)))
        assertEquals(Vec3(0f, 1.22f, 0f), push)

        val far = Vec3(3.5f, -63f, 0.5f)
        assertNull(world.windBurstPush(at, far, Vec3(3.5f, -61.38f, 0.5f), Box.of(far, 0.6f, 1.8f)))
    }

    @Test
    fun `a wind charge's burst reaches the blocks within a block or two of it, a little less far through blocks`() {
        val at = Vec3(0.5f, -62.5f, 0.5f)
        val strongest = world.reachedByWindBurst(at, rays(strongest = true))
        val weakest = world.reachedByWindBurst(at, rays(strongest = false))
        assertTrue(BlockPos(0, -63, 0) in weakest && BlockPos(1, -63, 0) in weakest)
        assertTrue(BlockPos(2, -63, 0) in strongest && BlockPos(2, -63, 0) !in weakest, "two blocks off, if the rays are strong enough")
        assertTrue(strongest.all { at.distanceTo(it.centre) < 2.7f }, "none further than two blocks and a bit: ${strongest.maxOf { at.distanceTo(it.centre) }}")

        // A wall right beside it is reached, but weakens the rays going through it, a step short of the far side.
        for (y in -63..-60) world[BlockPos(1, y, 0)] = stone
        val walled = world.reachedByWindBurst(at, rays(strongest = true))
        assertTrue(BlockPos(1, -63, 0) in walled && BlockPos(2, -63, 0) !in walled)
    }

    /** Rays all as strong as an explosion's can be, or all as weak. */
    private fun rays(strongest: Boolean) = object : Random() {
        override fun nextBits(bitCount: Int) = if (strongest && bitCount > 0) -1 ushr (Int.SIZE_BITS - bitCount) else 0
    }
}
