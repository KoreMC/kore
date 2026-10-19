package org.koremc.world

import org.koremc.registry.ItemCooldown
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** How fast something thrown leaves the hand, in blocks a tick. */
const val THROW_SPEED = 1.5f

/** How much faster a snowball or an egg falls each tick, in blocks a tick, and how much of its speed the air takes each tick. */
private const val GRAVITY = 0.03
private const val DRAG = 0.01

/** How much faster an ender pearl falls each tick, which the air doesn't slow. */
private const val ENDER_PEARL_GRAVITY = 0.025

/** How much faster an arrow falls each tick. */
private const val ARROW_GRAVITY = 0.05

/** How far below its thrower's eyes a snowball or an arrow leaves their hand. */
private const val EYE_DROP = 0.1f

/** How long a projectile passes through whoever threw it, in ticks, so it doesn't hit them as it leaves their hand. */
private const val THROWER_MISSED_FOR = 5

/** How much further than their box a projectile hits someone, each way. */
private const val HIT_REACH = 0.3

/** The furthest a projectile goes between looks at the blocks in its way, in blocks, so it can't pass through one. */
private const val STEP = 0.2

/** How little of a block a projectile must reach into for it to count. */
private const val EDGE = 1e-4

/** How far below the world a projectile falls before it's gone. */
private const val VOID_DEPTH = 64

/** How long an arrow stays stuck in a block before it's gone, in ticks: a minute. */
private const val ARROW_LIFETIME = 60 * 20

/** How much of its speed an arrow keeps, back the way it came, as it bounces off someone it didn't hurt. */
private const val BOUNCE = -0.1f

/** How long an ender pearl's thrower waits to throw another, in ticks: a second, and how much it hurts them where it takes them, as a fall does. */
private const val ENDER_PEARL_COOLDOWN = 20
const val ENDER_PEARL_DAMAGE = 5f

/** The game's egg, and its climate variant, which its look goes by, as the game's own egg entity has them. */
const val EGG_ENTITY = "minecraft:egg"
const val CLIMATE_VARIANT = "minecraft:climate_variant"
val CLIMATE_VARIANTS = listOf("temperate", "warm", "cold")

/**
 * What flies: the item it's thrown from, if it's thrown, and the entity it
 * flies as, as the game names them, as the game's own entities have them:
 * how much faster it falls each tick, how much of its speed the air takes,
 * how far below its thrower's eyes it leaves them, the wait before its
 * thrower can throw another, if there is one, an egg's climate variant, by
 * its place in [CLIMATE_VARIANTS], and whether it sticks in a block it
 * hits, as an arrow does, rather than breaking.
 */
enum class ProjectileKind(
    val item: String?,
    val entity: String,
    val gravity: Double = GRAVITY,
    val drag: Double = DRAG,
    val drop: Float = 0f,
    val cooldown: ItemCooldown? = null,
    val climate: Int? = null,
    val sticks: Boolean = false,
) {
    SNOWBALL("minecraft:snowball", "minecraft:snowball", drop = EYE_DROP),
    EGG("minecraft:egg", EGG_ENTITY, climate = 0),
    BROWN_EGG("minecraft:brown_egg", EGG_ENTITY, climate = 1),
    BLUE_EGG("minecraft:blue_egg", EGG_ENTITY, climate = 2),
    ENDER_PEARL("minecraft:ender_pearl", "minecraft:ender_pearl", ENDER_PEARL_GRAVITY, drag = 0.0, cooldown = ItemCooldown("ender_pearl", ENDER_PEARL_COOLDOWN)),
    ARROW(null, "minecraft:arrow", ARROW_GRAVITY, drop = EYE_DROP, sticks = true),
    ;

    /** Whether it's an egg, of any colour. */
    val egg get() = entity == EGG_ENTITY

    companion object {
        /** What [item] is thrown as, or null for an item that isn't thrown. */
        fun thrownFrom(item: String) = entries.find { it.item == item }
    }
}

/**
 * What an arrow carries from what shot it: its [damage] at a block a tick,
 * which it hurts by the more the faster it goes, whether it's [critical],
 * shot from a bow drawn all the way or a crossbow, the [punch] of the bow's
 * Punch, whether it [burns], from Flame, whether picking it up once it
 * lands gives the arrow back, [returned], as it does unless it was spared
 * being used up, the [potion] it's tipped with, if it is, and how many it
 * passes through, as [piercing], from a crossbow's Piercing.
 */
data class ArrowShot(
    val damage: Float,
    val critical: Boolean = false,
    val punch: Int = 0,
    val burns: Boolean = false,
    val returned: Boolean = true,
    val potion: Int? = null,
    val piercing: Int = 0,
)

/**
 * Something thrown or shot, flying through the world: the entity [id], a
 * [kind], thrown by the entity [thrower], with its bottom at [position],
 * moving at [velocity] blocks a tick, and for an arrow, what it was shot
 * with, [arrow].
 */
class Projectile(val id: Long, val kind: ProjectileKind, val thrower: Long, position: Vec3, velocity: Vec3, val arrow: ArrowShot? = null) {
    var position = position
        private set

    var velocity = velocity
        private set

    /** Ticks since it was thrown. */
    var age = 0
        private set

    /** Which way it points, as the way it last flew: its yaw and pitch, in degrees. */
    var yaw = yawOf(velocity)
        private set
    var pitch = pitchOf(velocity)
        private set

    /** The block an arrow's stuck in, if it's landed, and for how many ticks. */
    var stuckIn: BlockPos? = null
        private set
    var stuckFor = 0
        private set

    /** Whether it moved in the last tick, or landed. */
    var moved = false
        private set

    /** Those a piercing arrow's passed through, which it doesn't hit again. */
    val pierced = mutableSetOf<Long>()

    /** Flies it a tick, to [to], moving at [velocity] now. */
    internal fun flown(to: Vec3, velocity: Vec3) {
        position = to
        this.velocity = velocity
        if (velocity.x != 0f || velocity.y != 0f || velocity.z != 0f) {
            yaw = yawOf(velocity)
            pitch = pitchOf(velocity)
        }
        age++
        moved = true
    }

    /** Sticks it at [at], in the block at [pos], still, pointing the way it flew in. */
    internal fun stick(at: Vec3, pos: BlockPos) {
        position = at
        velocity = Vec3(0f, 0f, 0f)
        stuckIn = pos
        stuckFor = 0
        age++
        moved = true
    }

    /** Leaves it where it's stuck another tick. Returns how long it's been stuck. */
    internal fun stayStuck(): Int {
        moved = false
        return ++stuckFor
    }

    /** Frees it from the block it was stuck in, which went, to fall. */
    internal fun unstick() {
        stuckIn = null
        stuckFor = 0
    }

    /** Bounces it back off someone it didn't hurt, slower. */
    fun bounce() {
        velocity = Vec3(velocity.x * BOUNCE, velocity.y * BOUNCE, velocity.z * BOUNCE)
    }
}

/** The yaw and pitch something moving at [velocity] points along, in degrees. */
private fun yawOf(velocity: Vec3) = Math.toDegrees(atan2(velocity.x.toDouble(), velocity.z.toDouble())).toFloat()
private fun pitchOf(velocity: Vec3) = Math.toDegrees(atan2(velocity.y.toDouble(), hypot(velocity.x.toDouble(), velocity.z.toDouble()))).toFloat()

/** What a projectile hit, where it stopped: [at], its bottom there. */
sealed interface ProjectileHit {
    val projectile: Projectile
    val at: Vec3

    /** It hit the block at [pos]. */
    data class Block(override val projectile: Projectile, override val at: Vec3, val pos: BlockPos) : ProjectileHit

    /** It hit the entity [entityId]. */
    data class Entity(override val projectile: Projectile, override val at: Vec3, val entityId: Long) : ProjectileHit
}

/** The way someone looking as [yaw] and [pitch] say looks, a block long. */
fun lookVector(yaw: Float, pitch: Float): Vec3 {
    val turn = Math.toRadians(yaw.toDouble())
    val tilt = Math.toRadians(pitch.toDouble())
    return Vec3((-sin(turn) * cos(tilt)).toFloat(), (-sin(tilt)).toFloat(), (cos(turn) * cos(tilt)).toFloat())
}

/**
 * Flies every projectile a tick: the air slows it, it falls a little
 * faster, and it goes as far as it's going, unless it hits a block, or one
 * of [targets], boxes by their entity's ID, on the way there, but for
 * whoever threw it while it leaves their hand. One that hits stops where it
 * hit and goes, as one that falls out of the world does, but for an arrow:
 * one that hits a block sticks in it, until the block goes or a minute
 * passes, and one that hits someone stays, to go once it's hurt them, or
 * bounce off. Returns what each that hit, hit.
 */
internal fun FlatWorld.tickProjectiles(targets: Map<Long, Box>): List<ProjectileHit> {
    val hits = mutableListOf<ProjectileHit>()
    for (projectile in projectiles.toList()) {
        val stuck = projectile.stuckIn
        if (stuck != null) {
            if (stateAt(stuck)?.solidHeights() != null) {
                if (projectile.stayStuck() > ARROW_LIFETIME) removeProjectile(projectile.id)
                continue
            }
            projectile.unstick()
        }
        val v = projectile.velocity
        val kind = projectile.kind
        val velocity = Vec3((v.x * (1 - kind.drag)).toFloat(), (v.y * (1 - kind.drag) - kind.gravity).toFloat(), (v.z * (1 - kind.drag)).toFloat())
        val from = projectile.position
        val to = Vec3(from.x + velocity.x, from.y + velocity.y, from.z + velocity.z)
        val blocked = firstBlockBetween(from, to)
        val struck = targets.asSequence()
            .filter { (id, _) -> (id != projectile.thrower || projectile.age >= THROWER_MISSED_FOR) && id !in projectile.pierced }
            .mapNotNull { (id, box) -> box.grown(HIT_REACH).enteredAlong(from, to)?.let { id to it } }
            .minByOrNull { it.second }
        val hit = when {
            struck != null && (blocked == null || struck.second <= blocked.first) -> ProjectileHit.Entity(projectile, along(from, to, struck.second), struck.first)
            blocked != null -> ProjectileHit.Block(projectile, along(from, to, blocked.first), blocked.second)
            else -> null
        }
        if (hit is ProjectileHit.Block && kind.sticks) projectile.stick(hit.at, hit.pos) else projectile.flown(hit?.at ?: to, velocity)
        if (hit != null) hits += hit
        if ((hit != null && !kind.sticks) || to.y < MIN_Y - VOID_DEPTH) removeProjectile(projectile.id)
    }
    return hits
}

/** The point [share] of the way from [from] to [to]. */
private fun along(from: Vec3, to: Vec3, share: Double) =
    Vec3((from.x + (to.x - from.x) * share).toFloat(), (from.y + (to.y - from.y) * share).toFloat(), (from.z + (to.z - from.z) * share).toFloat())

/**
 * The first block whose solid part something a quarter of a block each
 * way meets going from [from] to [to], its bottom there, and how much of
 * the way it gets before it does, or null for a clear way.
 */
private fun FlatWorld.firstBlockBetween(from: Vec3, to: Vec3): Pair<Double, BlockPos>? {
    val length = sqrt(((to.x - from.x) * (to.x - from.x) + (to.y - from.y) * (to.y - from.y) + (to.z - from.z) * (to.z - from.z)).toDouble())
    val steps = maxOf(1, ceil(length / STEP).toInt())
    for (step in 1..steps) {
        val share = step.toDouble() / steps
        solidIn(Box.around(along(from, to, share)))?.let { return (step - 1).toDouble() / steps to it }
    }
    return null
}

/** The first block whose solid part [box] overlaps, or null if it's clear of every one. */
private fun FlatWorld.solidIn(box: Box): BlockPos? {
    for (x in blocksIn(box, 0)) for (y in blocksIn(box, 1)) for (z in blocksIn(box, 2)) {
        val pos = BlockPos(x, y, z)
        val heights = stateAt(pos)?.solidHeights() ?: continue
        if (y + heights.start < box.max[1] && y + heights.endInclusive > box.min[1]) return pos
    }
    return null
}

/** The blocks [box] reaches into along [axis], but one it only touches the edge of. */
private fun blocksIn(box: Box, axis: Int) = floor(box.min[axis]).toInt()..floor(box.max[axis] - EDGE).toInt()

/** This box, [by] further each way. */
internal fun Box.grown(by: Double) = Box(DoubleArray(3) { min[it] - by }, DoubleArray(3) { max[it] + by })

/** This box, [across] further each way across and [up] further up and down. */
internal fun Box.grown(across: Double, up: Double) = Box(
    doubleArrayOf(min[0] - across, min[1] - up, min[2] - across),
    doubleArrayOf(max[0] + across, max[1] + up, max[2] + across),
)

/** How much of the way from [from] to [to] a line goes before it enters this box, or null if it misses it. */
internal fun Box.enteredAlong(from: Vec3, to: Vec3): Double? {
    val start = doubleArrayOf(from.x.toDouble(), from.y.toDouble(), from.z.toDouble())
    val delta = doubleArrayOf((to.x - from.x).toDouble(), (to.y - from.y).toDouble(), (to.z - from.z).toDouble())
    var enter = 0.0
    var exit = 1.0
    for (axis in 0..2) {
        if (abs(delta[axis]) < 1e-9) {
            if (start[axis] < min[axis] || start[axis] > max[axis]) return null
            continue
        }
        val near = (min[axis] - start[axis]) / delta[axis]
        val far = (max[axis] - start[axis]) / delta[axis]
        enter = maxOf(enter, minOf(near, far))
        exit = minOf(exit, maxOf(near, far))
        if (enter > exit) return null
    }
    return enter
}
