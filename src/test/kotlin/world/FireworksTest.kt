package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemVersion
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FireworksTest {
    private val firework = ItemDefinition(FIREWORK_ROCKET, 1, componentBased = false, ItemVersion.NONE)

    private fun rocket(flight: Int, vararg stars: NbtMap) = ItemStack(firework).copy(
        nbt = NbtMap.builder().putCompound("Fireworks", NbtMap.builder().putByte("Flight", flight.toByte()).putList("Explosions", NbtType.COMPOUND, stars.toList()).build()).build(),
    )

    private fun star(shape: Int, twinkles: Boolean) = NbtMap.builder().putByte("FireworkType", shape.toByte()).putBoolean("FireworkFlicker", twinkles).build()

    @Test
    fun `a rocket flies half a second for each level of flight and one more, and up to half a second longer by chance`() {
        val fuses = List(500) { fireworkFuse(rocket(1), Random(it)) }
        assertEquals(20, fuses.min())
        assertEquals(31, fuses.max())
        assertTrue(List(100) { fireworkFuse(rocket(3), Random(it)) }.all { it in 40..51 })
    }

    @Test
    fun `a rocket that doesn't say flies as one of flight one, and shows the client as one of no stars`() {
        val plain = ItemStack(firework)
        assertTrue(List(100) { fireworkFuse(plain, Random(it)) }.all { it in 20..31 })
        val shown = fireworkDisplay(plain).getCompound("Fireworks")
        assertEquals(1.toByte(), shown.getByte("Flight"))
        assertTrue(shown.getList("Explosions", NbtType.COMPOUND).isEmpty())
        val starred = rocket(2, star(1, twinkles = false))
        assertEquals(starred.nbt, fireworkDisplay(starred))
    }

    @Test
    fun `a rocket's burst hurts by five and two for each star, falling away to nothing five blocks off, and one with no stars hurts nobody`() {
        assertEquals(7f, fireworkDamage(1, 0.0))
        assertEquals(11f, fireworkDamage(3, 0.0))
        assertEquals(7f * kotlin.math.sqrt(0.5f), fireworkDamage(1, 2.5)!!, 1e-5f)
        assertEquals(0f, fireworkDamage(1, 5.0))
        assertNull(fireworkDamage(1, 5.1))
        assertNull(fireworkDamage(0, 0.0))
    }

    @Test
    fun `a rocket bursts into the stars its NBT has, a large ball louder, and some twinkling`() {
        val stars = rocket(2, star(1, twinkles = true), star(0, twinkles = false)).fireworkStars
        assertEquals(2, stars.size)
        assertTrue(stars[0].burstsLarge && stars[0].twinkles)
        assertFalse(stars[1].burstsLarge || stars[1].twinkles)
        assertTrue(ItemStack(firework).fireworkStars.isEmpty())
    }
}
