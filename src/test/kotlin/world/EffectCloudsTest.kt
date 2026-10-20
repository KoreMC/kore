package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.AIR
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectCloudsTest {
    private val stone = BlockState("minecraft:stone", NbtMap.EMPTY, 18168865)
    private val world = FlatWorld(BlockPalette(listOf(BlockState(AIR, NbtMap.EMPTY, 18168865), stone)), listOf(stone))

    // Someone standing on the stone where a cloud lies, and someone a little over three blocks off.
    private val inside = mapOf(1L to Box.player(Vec3(0.5f, -63f, 0.5f)))
    private val beyond = mapOf(2L to Box.player(Vec3(3.7f, -63f, 0.5f)))
    private val swiftness = 14

    @Test
    fun `a cloud waits half a second, then reaches whoever's in it, shrinking for it, but not again for two seconds`() {
        val cloud = world.spawnCloud(swiftness, Vec3(0.5f, -63f, 0.5f), owner = 1)
        repeat(9) { assertTrue(world.tickEffectClouds(inside + beyond).isEmpty()) }

        assertEquals(listOf(CloudTouch(cloud, 1)), world.tickEffectClouds(inside + beyond))
        assertEquals(CLOUD_RADIUS - 0.005f - 0.5f, cloud.radius, 1e-4f)
        assertTrue(cloud.reshaped)

        repeat(39) { assertTrue(world.tickEffectClouds(inside).isEmpty()) }
        assertEquals(listOf(CloudTouch(cloud, 1)), world.tickEffectClouds(inside))
    }

    @Test
    fun `a cloud of water reaches whoever's in it without shrinking for it`() {
        val cloud = world.spawnCloud(WATER_POTION, Vec3(0.5f, -63f, 0.5f), owner = 1)
        repeat(10) { world.tickEffectClouds(inside) }
        assertEquals(CLOUD_RADIUS - 0.005f, cloud.radius, 1e-4f)
    }

    @Test
    fun `a cloud nobody uses shrinks away to nothing in about twenty five seconds`() {
        val cloud = world.spawnCloud(swiftness, Vec3(0.5f, -63f, 0.5f), owner = 1)
        repeat(500) { world.tickEffectClouds(emptyMap()) }
        assertTrue(cloud in world.clouds)
        repeat(20) { world.tickEffectClouds(emptyMap()) }
        assertTrue(cloud !in world.clouds)
    }
}
