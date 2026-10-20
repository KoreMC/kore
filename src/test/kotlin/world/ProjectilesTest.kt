package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.AIR
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemVersion
import org.koremc.registry.LOYALTY
import org.koremc.registry.withEnchantments
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectilesTest {
    private fun state(name: String) = BlockState(name, NbtMap.EMPTY, version = 1)

    private val stone = state("minecraft:stone")
    private val palette = BlockPalette(listOf(state(AIR), stone))

    // One layer of stone at the bottom of the world: its top is at y -63.
    private val world = FlatWorld(palette, listOf(stone))

    private fun assertNear(expected: Vec3, actual: Vec3) =
        assertTrue(abs(expected.x - actual.x) < 1e-4f && abs(expected.y - actual.y) < 1e-4f && abs(expected.z - actual.z) < 1e-4f, "$actual isn't $expected")

    @Test
    fun `something thrown flies, the air slowing it and falling faster each tick`() {
        val snowball = world.spawnProjectile(ProjectileKind.SNOWBALL, thrower = 1, Vec3(0.5f, -40f, 0.5f), Vec3(1f, 0f, 0f))

        assertEquals(emptyList(), world.tickProjectiles(emptyMap()))
        assertNear(Vec3(0.99f, -0.03f, 0f), snowball.velocity)
        assertNear(Vec3(1.49f, -40.03f, 0.5f), snowball.position)
    }

    @Test
    fun `an ender pearl falls slower, and the air doesn't slow it`() {
        val pearl = world.spawnProjectile(ProjectileKind.ENDER_PEARL, thrower = 1, Vec3(0.5f, -40f, 0.5f), Vec3(1f, 0f, 0f))

        world.tickProjectiles(emptyMap())
        assertNear(Vec3(1f, -0.025f, 0f), pearl.velocity)
    }

    @Test
    fun `it stops at the first block in its way, and goes`() {
        for (y in -63..-55) world[BlockPos(3, y, 0)] = stone
        val snowball = world.spawnProjectile(ProjectileKind.SNOWBALL, thrower = 1, Vec3(0.5f, -59.5f, 0.5f), Vec3(2f, 0f, 0f))

        world.tickProjectiles(emptyMap())
        val hit = assertIs<ProjectileHit.Block>(world.tickProjectiles(emptyMap()).single())
        assertEquals(BlockPos(3, -60, 0), hit.pos)
        assertTrue(hit.at.x < 3f - 0.125f, "it stops short of the wall, at ${hit.at}")
        assertTrue(snowball !in world.projectiles)
    }

    @Test
    fun `an arrow sticks in a block it hits, until the block goes, and goes after a minute`() {
        val arrow = world.spawnProjectile(ProjectileKind.ARROW, thrower = 1, Vec3(0.5f, -62.5f, 0.5f), Vec3(0f, -1f, 0f), ArrowShot(2f))
        val hit = assertIs<ProjectileHit.Block>(world.tickProjectiles(emptyMap()).single())
        assertEquals(BlockPos(0, -64, 0), hit.pos)
        assertEquals(hit.pos, arrow.stuckIn)
        assertEquals(emptyList(), world.tickProjectiles(emptyMap()))
        assertEquals(hit.at, arrow.position, "it stays put")

        world[BlockPos(0, -64, 0)] = state(AIR)
        world.tickProjectiles(emptyMap())
        assertEquals(null, arrow.stuckIn, "it falls, with the block gone")

        val other = world.spawnProjectile(ProjectileKind.ARROW, thrower = 1, Vec3(2.5f, -62.5f, 0.5f), Vec3(0f, -1f, 0f), ArrowShot(2f))
        repeat(1201) { world.tickProjectiles(emptyMap()) }
        assertTrue(other in world.projectiles)
        world.tickProjectiles(emptyMap())
        assertTrue(other !in world.projectiles)
    }

    private val trident = ItemDefinition("minecraft:trident", 1, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 251)

    @Test
    fun `a trident stays where it lands for good, but one with Loyalty heads back to whoever threw it, through what's in the way`() {
        val plain = world.spawnProjectile(ProjectileKind.TRIDENT, thrower = 1, Vec3(0.5f, -62.5f, 0.5f), Vec3(0f, -1f, 0f), item = ItemStack(trident))
        val loyal = world.spawnProjectile(
            ProjectileKind.TRIDENT, thrower = 1, Vec3(4.5f, -62.5f, 0.5f), Vec3(0f, -1f, 0f), item = ItemStack(trident).withEnchantments(listOf(EnchantmentLevel(LOYALTY, 3))),
        )
        val eyes = mapOf(1L to Vec3(4.5f, -50f, 0.5f))
        world.tickProjectiles(emptyMap(), eyes)
        assertTrue(plain.stuckIn != null && loyal.stuckIn != null)

        repeat(5) { world.tickProjectiles(emptyMap(), eyes) }
        assertTrue(loyal.returning && loyal.setOffBack)
        repeat(10) { world.tickProjectiles(emptyMap(), eyes) }
        assertTrue(loyal.position.y > -60f, "it's on its way up to them, at ${loyal.position}")

        repeat(1300) { world.tickProjectiles(emptyMap(), eyes) }
        assertTrue(plain in world.projectiles && plain.stuckIn != null, "it never goes")
    }

    @Test
    fun `a trident with Loyalty that falls out of the world heads back rather than going`() {
        val loyal = world.spawnProjectile(
            ProjectileKind.TRIDENT, thrower = 1, Vec3(0.5f, MIN_Y - 63.5f, 0.5f), Vec3(0f, -1f, 0f), item = ItemStack(trident).withEnchantments(listOf(EnchantmentLevel(LOYALTY, 1))),
        )
        world.tickProjectiles(emptyMap(), mapOf(1L to Vec3(0.5f, -61.38f, 0.5f)))
        assertTrue(loyal in world.projectiles && loyal.returning)
    }

    @Test
    fun `a potion's thrown a little above the way its thrower looks, the tilt lifting it rather than turning it all`() {
        val ahead = throwVector(yaw = -90f, pitch = 0f, tilt = -20f)
        val lift = kotlin.math.sin(Math.toRadians(20.0))
        val length = kotlin.math.sqrt(1 + lift * lift)
        assertEquals((1 / length).toFloat(), ahead.x, 1e-6f)
        assertEquals((lift / length).toFloat(), ahead.y, 1e-6f)
        // Looking straight down, it goes straight down.
        val down = throwVector(yaw = 0f, pitch = 90f, tilt = -20f)
        assertEquals(-1f, down.y, 1e-6f)
        assertEquals(lookVector(30f, 10f), throwVector(30f, 10f, tilt = 0f))
    }

    @Test
    fun `a wind charge flies straight on, neither slowing nor falling, until it bursts high enough above the world`() {
        val charge = world.spawnProjectile(ProjectileKind.WIND_CHARGE, thrower = 1, Vec3(0.5f, -50f, 0.5f), Vec3(1.5f, 0f, 0f))
        repeat(10) { world.tickProjectiles(emptyMap()) }
        assertEquals(Vec3(15.5f, -50f, 0.5f), charge.position)
        assertEquals(Vec3(1.5f, 0f, 0f), charge.velocity)

        val up = world.spawnProjectile(ProjectileKind.WIND_CHARGE, thrower = 1, Vec3(0.5f, MAX_Y + 29f, 0.5f), Vec3(0f, 1.5f, 0f))
        val hits = world.tickProjectiles(emptyMap())
        assertEquals(listOf<ProjectileHit>(ProjectileHit.Air(up, Vec3(0.5f, MAX_Y + 30.5f, 0.5f))), hits)
        assertTrue(up !in world.projectiles)
    }

    @Test
    fun `a wind charge turned back flies the new way, as whoever turned it back's, but not again for half a second`() {
        val charge = world.spawnProjectile(ProjectileKind.WIND_CHARGE, thrower = 1, Vec3(0.5f, -50f, 0.5f), Vec3(1.5f, 0f, 0f))
        assertFalse(charge.deflectable, "just thrown")
        repeat(10) { world.tickProjectiles(emptyMap()) }
        assertTrue(charge.deflectable)

        charge.deflect(2, Vec3(-1f, 0f, 0f))
        assertEquals(2, charge.thrower)
        assertFalse(charge.deflectable)
        world.tickProjectiles(emptyMap())
        assertEquals(Vec3(14.5f, -50f, 0.5f), charge.position)
        assertFalse(world.spawnProjectile(ProjectileKind.SNOWBALL, thrower = 1, Vec3(0.5f, -50f, 0.5f), Vec3(1.5f, 0f, 0f)).also { repeat(10) { world.tickProjectiles(emptyMap()) } }.deflectable)
    }

    @Test
    fun `it hits someone in its way, but not whoever threw it as it leaves their hand`() {
        val them = Box.player(Vec3(2.5f, -63f, 0.5f))
        val egg = world.spawnProjectile(ProjectileKind.EGG, thrower = 7, Vec3(0.5f, -62f, 0.5f), Vec3(3f, 0f, 0f))
        assertEquals(emptyList(), world.tickProjectiles(mapOf(7L to them)))
        world.removeProjectile(egg.id)

        world.spawnProjectile(ProjectileKind.EGG, thrower = 1, Vec3(0.5f, -62f, 0.5f), Vec3(3f, 0f, 0f))
        val hit = assertIs<ProjectileHit.Entity>(world.tickProjectiles(mapOf(7L to them)).single())
        assertEquals(7L, hit.entityId)
        assertTrue(hit.at.x < 2.2f, "it stops where it meets them, at ${hit.at}")
    }

    @Test
    fun `one that falls out of the world goes`() {
        val pearl = world.spawnProjectile(ProjectileKind.ENDER_PEARL, thrower = 1, Vec3(0.5f, MIN_Y - 63.5f, 0.5f), Vec3(0f, -1f, 0f))

        world.tickProjectiles(emptyMap())
        assertTrue(pearl !in world.projectiles)
    }

    @Test
    fun `snowballs, eggs and ender pearls are thrown, and an ender pearl has its thrower wait a second`() {
        assertEquals(ProjectileKind.SNOWBALL, ProjectileKind.thrownFrom("minecraft:snowball"))
        // Eggs of every colour are the game's egg, in their climate variant.
        assertEquals(listOf(0, 1, 2), listOf("minecraft:egg", "minecraft:brown_egg", "minecraft:blue_egg").map { ProjectileKind.thrownFrom(it)?.climate })
        assertTrue(ProjectileKind.thrownFrom("minecraft:blue_egg")!!.egg)
        assertEquals(ProjectileKind.ENDER_PEARL, ProjectileKind.thrownFrom("minecraft:ender_pearl"))
        assertEquals(null, ProjectileKind.thrownFrom("minecraft:stone"))
        assertEquals(20, ProjectileKind.ENDER_PEARL.cooldown?.ticks)
    }

    @Test
    fun `a look of no yaw is south, toward positive z, and of 90 west`() {
        assertNear(Vec3(0f, 0f, 1f), lookVector(0f, 0f))
        assertNear(Vec3(-1f, 0f, 0f), lookVector(90f, 0f))
        assertNear(Vec3(0f, -1f, 0f), lookVector(0f, 90f))
    }
}
