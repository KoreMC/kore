package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.koremc.registry.ItemStack
import kotlin.math.sqrt
import kotlin.random.Random

/** The game's name for firework rockets. */
const val FIREWORK_ROCKET = "minecraft:firework_rocket"

/** Where a firework rocket's NBT keeps how long it flies and the stars it bursts into, each with its shape, and whether it twinkles. */
private const val FIREWORKS = "Fireworks"
private const val FLIGHT = "Flight"
private const val EXPLOSIONS = "Explosions"
private const val SHAPE = "FireworkType"
private const val TWINKLES = "FireworkFlicker"

/** A star's shape that bursts the loudest: a large ball. */
private const val LARGE_BALL = 1

/** How long a rocket that doesn't say flies, as the game takes it. */
private const val DEFAULT_FLIGHT: Byte = 1

/**
 * How a rocket launched by hand climbs: each tick a little faster up, and
 * faster across, as the game has it, from a gentle start, drifting across
 * by up to [LAUNCH_DRIFT] either way.
 */
const val ROCKET_CLIMB = 0.04f
const val ROCKET_SPREAD = 1.15f
const val ROCKET_START = 0.05f
const val LAUNCH_DRIFT = 0.002297

/** How long a rocket flies for each level of flight it has, and one more, in ticks, and how much longer, by chance, either way. */
private const val TICKS_PER_FLIGHT = 10
private const val FUSE_MORE = 6
private const val FUSE_MORE_AGAIN = 7

/** How hard a rocket's burst hurts, and how much more for each star, and how far it reaches, in blocks. */
private const val BURST_DAMAGE = 5f
private const val BURST_DAMAGE_PER_STAR = 2f
const val BURST_REACH = 5.0

/** The stars a firework rocket bursts into, each as its NBT. */
val ItemStack.fireworkStars: List<NbtMap> get() = nbt.getCompound(FIREWORKS, NbtMap.EMPTY).getList(EXPLOSIONS, NbtType.COMPOUND, emptyList())

/**
 * What the client takes a firework rocket, [rocket], to be, which it
 * bursts into the stars of: its item's NBT, its flight and stars, which a
 * rocket without them has as none, flying as far as the game takes it.
 */
fun fireworkDisplay(rocket: ItemStack): NbtMap {
    if (rocket.nbt.containsKey(FIREWORKS)) return rocket.nbt
    val none = NbtMap.builder().putByte(FLIGHT, DEFAULT_FLIGHT).putList(EXPLOSIONS, NbtType.COMPOUND, emptyList()).build()
    return rocket.nbt.toBuilder().putCompound(FIREWORKS, none).build()
}

/** Whether a firework star bursts the loudest, as a large ball does, and whether it twinkles. */
val NbtMap.burstsLarge get() = getByte(SHAPE).toInt() == LARGE_BALL
val NbtMap.twinkles get() = getBoolean(TWINKLES)

/**
 * How long a firework rocket, [rocket], flies before it bursts, in ticks,
 * as the game works it out: half a second for each level of flight it
 * has, one for a rocket that doesn't say, and one more, and up to half a
 * second more by [random].
 */
fun fireworkFuse(rocket: ItemStack, random: Random = Random.Default): Int {
    val flight = rocket.nbt.getCompound(FIREWORKS, NbtMap.EMPTY).getByte(FLIGHT, DEFAULT_FLIGHT).toInt()
    return TICKS_PER_FLIGHT * (1 + flight) + random.nextInt(FUSE_MORE) + random.nextInt(FUSE_MORE_AGAIN)
}

/**
 * How hard a rocket with [stars] stars bursting [off] blocks away hurts:
 * five, and two for each star, falling away to nothing at [BURST_REACH],
 * as the game has it. Null for one with no stars, which hurts nobody, or
 * too far off.
 */
fun fireworkDamage(stars: Int, off: Double): Float? {
    if (stars <= 0 || off > BURST_REACH) return null
    return (BURST_DAMAGE + BURST_DAMAGE_PER_STAR * stars) * sqrt((BURST_REACH - off) / BURST_REACH).toFloat()
}
