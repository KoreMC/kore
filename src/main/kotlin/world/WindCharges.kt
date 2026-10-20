package org.koremc.world

import org.koremc.registry.AIR
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How strong a wind charge's burst is, which sets how far it reaches, and
 * how hard it pushes what it reaches, as the game's own wind charge has them.
 */
const val WIND_BURST_POWER = 1.2
private const val WIND_BURST_KNOCKBACK = 1.22

/** How far an explosion's rays go at a step, how much a step weakens them, and how much more one through a block does, which no block holds back more for a wind charge. */
private const val RAY_STEP = 0.3
private const val RAY_FADE = 0.225
private const val BLOCK_FADE = 0.3 * RAY_STEP

/** How many rays an explosion sends out along each edge of the cube they go out through, and how much stronger or weaker than its power each starts, either way. */
private const val RAYS_ACROSS = 16
private const val RAY_LEAST = 0.7
private const val RAY_SPREAD = 0.6

/** How far apart the points along a line between a burst and what it pushes are looked at for blocks in the way. */
private const val SIGHT_STEP = 0.1

/**
 * How hard an explosion of [power] at [at] pushes something whose feet
 * are at [feet] and eyes at [eyes], [seen] of it open to the explosion:
 * away from it, toward the eyes, by [knockback] at the explosion, and the
 * less the further off the feet are, to nothing at twice [power] blocks,
 * as the game works it out. Null for something out of its reach.
 */
fun burstPush(at: Vec3, power: Double, knockback: Double, feet: Vec3, eyes: Vec3, seen: Double): Vec3? {
    val off = at.distanceTo(feet) / (power * 2)
    if (off > 1) return null
    val dx = (eyes.x - at.x).toDouble()
    val dy = (eyes.y - at.y).toDouble()
    val dz = (eyes.z - at.z).toDouble()
    val length = sqrt(dx * dx + dy * dy + dz * dz).takeIf { it > 0 } ?: return null
    val strength = (1 - off) * seen * knockback
    return Vec3((dx / length * strength).toFloat(), (dy / length * strength).toFloat(), (dz / length * strength).toFloat())
}

/**
 * How hard a wind charge bursting at [at] pushes something in [box], its
 * feet at [feet] and eyes at [eyes], as [burstPush] works it out, or null
 * for something beyond its reach.
 */
internal fun FlatWorld.windBurstPush(at: Vec3, feet: Vec3, eyes: Vec3, box: Box): Vec3? {
    if (at.distanceTo(feet) > WIND_BURST_POWER * 2) return null
    return burstPush(at, WIND_BURST_POWER, WIND_BURST_KNOCKBACK, feet, eyes, exposure(at, box))
}

/**
 * How much of [box] an explosion at [at] reaches, from none of it to all:
 * the share of the points spread through it, as the game spreads them,
 * with nothing solid between them and the explosion.
 */
internal fun FlatWorld.exposure(at: Vec3, box: Box): Double {
    val steps = DoubleArray(3) { 1 / ((box.max[it] - box.min[it]) * 2 + 1) }
    val counts = IntArray(3) { floor(1 / steps[it]).toInt() + 1 }
    // Across, the points sit in the middle of the box, as the game has them.
    val inset = DoubleArray(3) { if (it == 1) 0.0 else (1 - floor(1 / steps[it]) * steps[it]) / 2 }
    var open = 0
    for (i in 0 until counts[0]) for (j in 0 until counts[1]) for (k in 0 until counts[2]) {
        val share = doubleArrayOf(i * steps[0], j * steps[1], k * steps[2])
        val point = DoubleArray(3) { box.min[it] + (box.max[it] - box.min[it]) * share[it] + inset[it] }
        if (clearBetween(Vec3(point[0].toFloat(), point[1].toFloat(), point[2].toFloat()), at)) open++
    }
    return open.toDouble() / (counts[0] * counts[1] * counts[2])
}

/** Whether nothing solid stands between [from] and [to]. */
private fun FlatWorld.clearBetween(from: Vec3, to: Vec3): Boolean {
    val steps = maxOf(1, (from.distanceTo(to) / SIGHT_STEP).toInt())
    for (step in 0..steps) {
        val share = step.toDouble() / steps
        val x = from.x + (to.x - from.x) * share
        val y = from.y + (to.y - from.y) * share
        val z = from.z + (to.z - from.z) * share
        val heights = stateAt(BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt()))?.solidHeights() ?: continue
        if (y - floor(y) in heights) return false
    }
    return true
}

/**
 * The blocks a wind charge bursting at [at] reaches, as an explosion's
 * rays do: out from it every way, each starting as strong as its power,
 * give or take three tenths, and weakening a step at a time, a little more
 * through a block, which none holds back more. Rays reach a block or two.
 */
internal fun FlatWorld.reachedByWindBurst(at: Vec3, random: Random = Random.Default): Set<BlockPos> {
    val reached = HashSet<BlockPos>()
    val edge = RAYS_ACROSS - 1
    for (i in 0..edge) for (j in 0..edge) for (k in 0..edge) {
        if (i != 0 && i != edge && j != 0 && j != edge && k != 0 && k != edge) continue
        val dx = i.toDouble() / edge * 2 - 1
        val dy = j.toDouble() / edge * 2 - 1
        val dz = k.toDouble() / edge * 2 - 1
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        var strength = WIND_BURST_POWER * (RAY_LEAST + random.nextDouble() * RAY_SPREAD)
        var x = at.x.toDouble()
        var y = at.y.toDouble()
        var z = at.z.toDouble()
        while (strength > 0) {
            val pos = BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
            if (pos.y !in MIN_Y..MAX_Y) break
            if (stateAt(pos)?.name != AIR) strength -= BLOCK_FADE
            if (strength > 0) reached += pos
            x += dx / length * RAY_STEP
            y += dy / length * RAY_STEP
            z += dz / length * RAY_STEP
            strength -= RAY_FADE
        }
    }
    return reached
}
