package org.koremc.world

import org.koremc.registry.BlockState
import org.koremc.registry.ItemStack
import org.koremc.registry.sameKindAs
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/** How long an item lies in the world before it's gone, in ticks: five minutes. */
private const val ITEM_LIFETIME = 6000

/** How long an item that drops from a block waits before anyone can pick it up, in ticks. */
internal const val DROPPED_PICKUP_DELAY = 10

/** How far below the world an item falls before it's gone. */
private const val VOID_DEPTH = 64

// How items move each tick, as in the game: they fall, slow in the air, and slow more along the ground.
private const val GRAVITY = 0.04
private const val DRAG = 0.98
private const val GROUND_FRICTION = 0.6

/** An item entity's size, a quarter of a block each way, as an experience orb's is. */
const val ITEM_SIZE = 0.25f

/** Half an item's width, and its height. */
private const val HALF_WIDTH = 0.125
private const val HEIGHT = 0.25

/** How far across an item reaches for others to merge with, beyond its own sides. */
private const val MERGE_REACH = 0.5

/** How often, in ticks of its age, an item on the ground looks for others to merge with: once a second. */
private const val MERGE_PERIOD = 20

/**
 * How close to a surface counts as touching it. Positions are kept as
 * floats, which near the overworld's floor are only good to about 1e-5,
 * and an item a hair inside a block mustn't fall through it.
 */
private const val TOUCHING = 1e-4

// How items move in water and lava, as in the game: deeper in than this, they float up, by a little each tick,
// while they rise slower than the limit, and slow more across, in lava most.
private const val FLOATS_DEEPER_THAN = 0.1
private const val RISE = 5e-4
private const val RISE_LIMIT = 0.06
private const val WATER_DRAG = 0.99
private const val LAVA_DRAG = 0.95

// How hard flowing water and lava push an item along, and the least push that moves one lying still.
private const val WATER_PUSH = 0.014
private const val LAVA_PUSH = 0.0023333333333333335
private const val LEAST_PUSH = 0.0045
private const val STILL = 0.003

/** How shallow a liquid's push is weakened at, by how deep it is. */
private const val SHALLOW = 0.4

/** How much harm an item takes before it's gone, and from lava each tick, and fire, soul fire, and burning once a second. */
private const val ITEM_HEALTH = 5
private const val LAVA_HARM = 4
private const val FIRE_HARM = 1
private const val SOUL_FIRE_HARM = 2

/** How long an item burns on, in ticks, after it's touched lava, or fire, and how often burning harms it. */
private const val LAVA_BURNS = 15 * 20
private const val FIRE_BURNS = 8 * 20
private const val BURN_PERIOD = 20

/**
 * An item lying in the world: a stack anyone can pick up once its pickup
 * delay has run out. [position] is the middle of its bottom, and
 * [velocity] is in blocks a tick.
 */
class ItemEntity(val id: Long, stack: ItemStack, position: Vec3, velocity: Vec3, pickupDelay: Int) {
    var stack = stack
        set(value) {
            field = value
            restacked = true
        }

    var position = position
        private set
    var velocity = velocity
        private set

    /** Ticks until it can be picked up. */
    var pickupDelay = pickupDelay
        private set

    var onGround = false
        private set

    /** Whether it moved in the last tick, so those who see it need to hear where it is now. */
    var moved = false
        private set

    /** Whether a piston or a command moved it this tick, which holds it up until the piston's done. */
    private var pushed = false

    /**
     * Whether a piston or a command moved it in the last tick, so those who
     * see it need to see it there at once, keeping up with the piston,
     * rather than have it glide there after.
     */
    var snapped = false
        private set

    /**
     * Whether its stack changed since the last tick began, by merging or
     * being partly picked up, so those who see it need to hear how many it
     * holds now.
     */
    var restacked = false
        internal set

    internal var age = 0

    /** How much more harm it can take before it's gone, from lava or fire. */
    private var health = ITEM_HEALTH

    /** How many more ticks it burns for, having touched lava or fire. */
    private var burning = 0

    /** Whether lava harmed it in the last tick, which fizzes. */
    var fizzed = false
        private set

    /**
     * Whether it can take in more items, or go into another stack: it's
     * landed, it isn't about to go, and its stack isn't full. Items in the
     * air pass each other by.
     */
    private val mergeable get() = onGround && age < ITEM_LIFETIME && stack.count < stack.item.maxStackSize

    /** Whether lava and fire leave it be: netherite, and ancient debris. */
    private val fireproof get() = "netherite" in stack.item.name || stack.item.name == "minecraft:ancient_debris"

    /**
     * Moves the item on a tick, as the game does: it falls, or floats up in
     * water or lava, which carry it along as they flow, and slows. Lava
     * harms it, with a fizz, and fire, and it burns on a while after,
     * unless it's in water, or fireproof. Returns whether it's still there:
     * one that's lain long enough, fallen out of the world, or burned is gone.
     */
    internal fun tick(world: FlatWorld): Boolean {
        age++
        fizzed = false
        if (pickupDelay > 0) pickupDelay--
        if (age >= ITEM_LIFETIME || position.y < MIN_Y - VOID_DEPTH) return false

        var (vx, vy, vz) = velocity.toDoubles()
        val water = world.liquidAround(Box.around(position), Liquid.WATER, vx, vz)
        val lava = world.liquidAround(Box.around(position), Liquid.LAVA, vx, vz)
        vx += water.push[0] + lava.push[0]
        vy += water.push[1] + lava.push[1]
        vz += water.push[2] + lava.push[2]
        val drag = when {
            water.depth > FLOATS_DEEPER_THAN -> WATER_DRAG
            lava.depth > FLOATS_DEEPER_THAN -> LAVA_DRAG
            else -> null
        }
        when {
            pushed -> {}
            drag != null -> {
                vx *= drag
                vz *= drag
                if (vy < RISE_LIMIT) vy += RISE
            }
            else -> vy -= GRAVITY
        }
        if (!burn(world, water.depth > 0, lava.depth > 0)) return false

        val start = position
        var box = Box.around(position)
        val dy = world.clip(box, Axis.Y, vy)
        box = box.shifted(Axis.Y, dy)
        val dx = world.clip(box, Axis.X, vx)
        box = box.shifted(Axis.X, dx)
        val dz = world.clip(box, Axis.Z, vz)
        box = box.shifted(Axis.Z, dz)

        onGround = vy < 0 && dy != vy
        // Hitting something stops the item that way.
        if (dy != vy) vy = 0.0
        if (dx != vx) vx = 0.0
        if (dz != vz) vz = 0.0
        val friction = if (onGround) DRAG * GROUND_FRICTION else DRAG
        velocity = Vec3((vx * friction).toFloat(), (vy * DRAG).toFloat(), (vz * friction).toFloat())
        position = box.bottom()
        moved = pushed || position != start
        snapped = pushed
        pushed = false
        return true
    }

    /**
     * Harms the item as the game does: [inLava], every tick, with a fizz,
     * and in fire, soul fire more, each setting it burning, which harms it once a second
     * after, but in lava, until it's out or [inWater] puts it out. Returns
     * whether it's still there.
     */
    private fun burn(world: FlatWorld, inWater: Boolean, inLava: Boolean): Boolean {
        if (fireproof) return true
        if (inLava) {
            burning = maxOf(burning, LAVA_BURNS)
            health -= LAVA_HARM
            fizzed = true
        }
        val fire = world.fireHarm(Box.around(position))
        if (fire > 0) {
            burning = maxOf(burning, FIRE_BURNS)
            health -= fire
        }
        if (inWater) burning = 0
        if (burning > 0) {
            if (burning % BURN_PERIOD == 0 && !inLava) health -= FIRE_HARM
            burning--
        }
        return health > 0
    }

    /**
     * Pushes it [by] along [axis], as a piston does, as far as the blocks in
     * its way let it, stopping it that way and holding it up for the tick,
     * so it keeps pace with the piston.
     */
    internal fun pushBy(world: FlatWorld, axis: Axis, by: Double) {
        position = world.pushed(position, axis, by)
        velocity = velocity.stoppedAlong(axis)
        pushed = true
    }

    /** Pushes it [by], as a wind charge's burst does, on top of how it was moving. */
    internal fun push(by: Vec3) {
        velocity = Vec3(velocity.x + by.x, velocity.y + by.y, velocity.z + by.z)
    }

    /** Puts it [at] a point, stopped, as a command does. */
    fun teleport(at: Vec3) {
        position = at
        velocity = Vec3(0f, 0f, 0f)
        pushed = true
    }

    /**
     * Once a second, merges this item with those of its kind close enough,
     * as the game does, until it goes into one of them: both must be on the
     * ground, and the side of one's box must come within [MERGE_REACH] of
     * the other's, at a height they share. Of two that merge, the smaller
     * stack goes into the larger, and into the other of two the same size.
     * They only merge when they fit in one stack. Returns the items that
     * went.
     */
    internal fun mergeWithNeighbours(world: FlatWorld): List<ItemEntity> {
        if (age % MERGE_PERIOD != 0 || !mergeable) return emptyList()
        val gone = mutableListOf<ItemEntity>()
        for (other in world.items) {
            if (other === this || !other.mergeable || !closeTo(other)) continue
            if (!other.stack.sameKindAs(stack) || other.stack.count + stack.count > stack.item.maxStackSize) continue
            if (other.stack.count < stack.count) {
                absorb(other)
                gone += other
            } else {
                other.absorb(this)
                gone += this
                break
            }
        }
        return gone
    }

    private fun closeTo(other: ItemEntity) =
        abs(other.position.x - position.x) < 2 * HALF_WIDTH + MERGE_REACH &&
            abs(other.position.z - position.z) < 2 * HALF_WIDTH + MERGE_REACH &&
            abs(other.position.y - position.y) < HEIGHT

    /** Takes in [other]'s items. The stack waits as long as either did to be picked up, and lasts as long as the newer. */
    private fun absorb(other: ItemEntity) {
        stack = stack.copy(count = stack.count + other.stack.count)
        pickupDelay = maxOf(pickupDelay, other.pickupDelay)
        age = minOf(age, other.age)
    }
}

/**
 * Drops [stacks] from the block at [pos], as a block breaking does: each
 * somewhere near the block's middle, popping up and out a little.
 */
fun FlatWorld.dropFromBlock(pos: BlockPos, stacks: List<ItemStack>) {
    for (stack in stacks) {
        fun jitter() = Random.nextDouble(-0.25, 0.25).toFloat()
        val at = Vec3(pos.x + 0.5f + jitter(), (pos.y + 0.5 - HEIGHT / 2).toFloat() + jitter(), pos.z + 0.5f + jitter())
        val velocity = Vec3(Random.nextDouble(-0.1, 0.1).toFloat(), 0.2f, Random.nextDouble(-0.1, 0.1).toFloat())
        spawnItem(stack, at, velocity, DROPPED_PICKUP_DELAY)
    }
}

/**
 * Moves every item along a tick: they fall, slide and land on blocks, and
 * merge with items of their kind close by. One that's lain for five
 * minutes, or fallen out of the world, is gone.
 */
fun FlatWorld.tickItems(): List<Vec3> {
    // Before any item moves, since one may merge into another that has yet to.
    items.forEach { it.restacked = false }
    val fizzes = mutableListOf<Vec3>()
    for (item in items.toList()) {
        if (itemWith(item.id) == null) continue // Merged into another this tick
        val alive = item.tick(this)
        if (item.fizzed) fizzes += item.position
        if (!alive) {
            removeItem(item.id)
            continue
        }
        item.mergeWithNeighbours(this).forEach { removeItem(it.id) }
    }
    return fizzes
}

/** How deep a liquid reaches up an item, from its bottom, and the way it pushes the item, in blocks a tick. */
private class InLiquid(val depth: Double, val push: DoubleArray)

/**
 * How deep [liquid] reaches up [box], and how it pushes what's in it, as
 * the game has it: the way it flows in each block of it the box is in,
 * less where it's shallow, together, at the liquid's own strength, or a
 * little more for an item lying still, moving [vx] and [vz] across.
 */
private fun FlatWorld.liquidAround(box: Box, liquid: Liquid, vx: Double, vz: Double): InLiquid {
    val inner = Box(DoubleArray(3) { box.min[it] + 0.001 }, DoubleArray(3) { box.max[it] - 0.001 })
    var depth = 0.0
    val flow = DoubleArray(3)
    var flowing = 0
    for (x in floor(inner.min[0]).toInt() until ceil(inner.max[0]).toInt()) {
        for (y in floor(inner.min[1]).toInt() until ceil(inner.max[1]).toInt()) {
            for (z in floor(inner.min[2]).toInt() until ceil(inner.max[2]).toInt()) {
                if (y !in MIN_Y..MAX_Y) continue
                val pos = BlockPos(x, y, z)
                val here = liquidHere(pos)?.takeIf { it.liquid == liquid } ?: continue
                // Liquid with more of it above fills its block.
                val top = liquidTop(pos, here)
                if (top < inner.min[1]) continue
                depth = maxOf(depth, top - inner.min[1])
                val way = flowAt(pos, liquid, here.depth)
                val weight = if (depth < SHALLOW) depth else 1.0
                for (axis in 0..2) flow[axis] += way[axis] * weight
                flowing++
            }
        }
    }
    val length = sqrt(flow.sumOf { it * it })
    if (length == 0.0) return InLiquid(depth, DoubleArray(3))
    var push = DoubleArray(3) { flow[it] / length * if (liquid == Liquid.WATER) WATER_PUSH else LAVA_PUSH }
    if (abs(vx) < STILL && abs(vz) < STILL && sqrt(push.sumOf { it * it }) < LEAST_PUSH) push = DoubleArray(3) { flow[it] / length * LEAST_PUSH }
    return InLiquid(depth, push)
}

/**
 * The way [liquid] at [pos], [depth] steps from its source, flows, as the
 * game works it out: from it toward where beside it is lower, or empty,
 * with liquid lower still below, and falling liquid down a wall beside it.
 * Unit length, or none for still liquid.
 */
private fun FlatWorld.flowAt(pos: BlockPos, liquid: Liquid, depth: Int): DoubleArray {
    val own = liquidHeight(depth)
    var x = 0.0
    var z = 0.0
    for (side in AROUND) {
        val next = pos.side(side)
        val there = liquidHere(next)
        if (there != null && there.liquid != liquid) continue
        val beside = there?.let { liquidHeight(it.depth) } ?: 0.0
        val drop = when {
            beside > 0 -> own - beside
            stateAt(next)?.solidHeights() != null -> 0.0
            else -> liquidHere(next.side(Face.DOWN))?.takeIf { it.liquid == liquid }?.let { own - (liquidHeight(it.depth) - 8 / 9.0) } ?: 0.0
        }
        x += side.dx * drop
        z += side.dz * drop
    }
    var y = 0.0
    if (depth >= FALLING_DEPTH && AROUND.any { side -> listOf(pos.side(side), pos.side(side).side(Face.UP)).any { stateAt(it)?.hasFullFace(side.opposite) == true } }) {
        val across = sqrt(x * x + z * z)
        if (across > 0) {
            x /= across
            z /= across
        }
        y = -6.0
    }
    val length = sqrt(x * x + y * y + z * z)
    return if (length == 0.0) DoubleArray(3) else doubleArrayOf(x / length, y / length, z / length)
}

/** How much the fire [box] is in harms it a tick: soul fire more than fire, and none out of it. */
private fun FlatWorld.fireHarm(box: Box): Int {
    var harm = 0
    for (x in floor(box.min[0]).toInt()..floor(box.max[0] - TOUCHING).toInt()) {
        for (y in floor(box.min[1]).toInt()..floor(box.max[1] - TOUCHING).toInt()) {
            for (z in floor(box.min[2]).toInt()..floor(box.max[2] - TOUCHING).toInt()) {
                if (y !in MIN_Y..MAX_Y) continue
                val state = stateAt(BlockPos(x, y, z)) ?: continue
                if (state.isFire) harm = maxOf(harm, if (state.name == SOUL_FIRE) SOUL_FIRE_HARM else FIRE_HARM)
            }
        }
    }
    return harm
}

internal enum class Axis { X, Y, Z }

/** A box in the world, from its lowest corner to its highest. */
internal class Box(val min: DoubleArray, val max: DoubleArray) {
    fun shifted(axis: Axis, by: Double) = Box(min.copyOf().also { it[axis.ordinal] += by }, max.copyOf().also { it[axis.ordinal] += by })

    fun bottom() = Vec3(((min[0] + max[0]) / 2).toFloat(), min[1].toFloat(), ((min[2] + max[2]) / 2).toFloat())

    /** Whether this box and [other] share any space, more than touching. */
    fun overlaps(other: Box) = (0..2).all { max[it] > other.min[it] + TOUCHING && min[it] < other.max[it] - TOUCHING }

    companion object {
        /** The box of an item, or anything else a quarter of a block each way, standing at [bottom]. */
        fun around(bottom: Vec3) = Box(
            doubleArrayOf(bottom.x - HALF_WIDTH, bottom.y.toDouble(), bottom.z - HALF_WIDTH),
            doubleArrayOf(bottom.x + HALF_WIDTH, bottom.y + HEIGHT, bottom.z + HALF_WIDTH),
        )

        /** The box of something [width] across and [height] tall standing at [bottom]. */
        fun of(bottom: Vec3, width: Float, height: Float): Box {
            val half = width / 2.0
            return Box(doubleArrayOf(bottom.x - half, bottom.y.toDouble(), bottom.z - half), doubleArrayOf(bottom.x + half, bottom.y + height.toDouble(), bottom.z + half))
        }

        /** The box of a player standing at [feet]. */
        fun player(feet: Vec3): Box {
            val half = PLAYER_WIDTH / 2.0
            return Box(doubleArrayOf(feet.x - half, feet.y.toDouble(), feet.z - half), doubleArrayOf(feet.x + half, feet.y + PLAYER_HEIGHT.toDouble(), feet.z + half))
        }
    }
}

/**
 * How far [box] can move along [axis] of the distance it wants to, [by],
 * before it meets a block's solid part.
 */
internal fun FlatWorld.clip(box: Box, axis: Axis, by: Double): Double {
    if (by == 0.0) return 0.0
    val a = axis.ordinal
    val reach = Box(box.min.copyOf().also { it[a] += minOf(by, 0.0) }, box.max.copyOf().also { it[a] += maxOf(by, 0.0) })
    var allowed = by
    for (x in blocksAcross(reach, 0)) for (y in blocksAcross(reach, 1)) for (z in blocksAcross(reach, 2)) {
        val heights = stateAt(BlockPos(x, y, z))?.solidHeights() ?: continue
        val block = Box(doubleArrayOf(x.toDouble(), y + heights.start, z.toDouble()), doubleArrayOf(x + 1.0, y + heights.endInclusive, z + 1.0))
        // Only a block the box overlaps on the other two axes is in the way.
        if ((0..2).any { it != a && (box.max[it] <= block.min[it] + TOUCHING || box.min[it] >= block.max[it] - TOUCHING) }) continue
        if (by > 0 && box.max[a] <= block.min[a] + TOUCHING) allowed = minOf(allowed, block.min[a] - box.max[a])
        if (by < 0 && box.min[a] >= block.max[a] - TOUCHING) allowed = maxOf(allowed, block.max[a] - box.min[a])
    }
    // Rounding can leave the box a hair inside a block, which would move it back out the way it came: it stays put instead.
    // A hair of a gap the way it's going it crosses, so it comes to rest on what it meets, rather than hovering just short of it.
    return if (allowed * by < 0) 0.0 else allowed
}

private fun blocksAcross(box: Box, axis: Int) = floor(box.min[axis]).toInt()..floor(box.max[axis] - TOUCHING).toInt()

/**
 * Where something a quarter of a block each way, an item or an orb,
 * standing at [bottom], ends up pushed [by] along [axis], as far as the
 * blocks in its way let it.
 */
internal fun FlatWorld.pushed(bottom: Vec3, axis: Axis, by: Double): Vec3 {
    val box = Box.around(bottom)
    return box.shifted(axis, clip(box, axis, by)).bottom()
}

/** This motion, but none along [axis]. */
internal fun Vec3.stoppedAlong(axis: Axis) = when (axis) {
    Axis.X -> Vec3(0f, y, z)
    Axis.Y -> Vec3(x, 0f, z)
    Axis.Z -> Vec3(x, y, 0f)
}

/**
 * Whether something [width] across and [height] tall, standing at
 * [bottom], would be clear of the solid part of every block, by the same
 * reckoning items fall by.
 */
fun FlatWorld.isClear(bottom: Vec3, width: Float, height: Float): Boolean {
    val half = width / 2.0
    val box = Box(
        doubleArrayOf(bottom.x - half, bottom.y.toDouble(), bottom.z - half),
        doubleArrayOf(bottom.x + half, bottom.y + height.toDouble(), bottom.z + half),
    )
    for (x in blocksAcross(box, 0)) for (y in blocksAcross(box, 1)) for (z in blocksAcross(box, 2)) {
        val heights = stateAt(BlockPos(x, y, z))?.solidHeights() ?: continue
        if (y + heights.start < box.max[1] - TOUCHING && y + heights.endInclusive > box.min[1] + TOUCHING) return false
    }
    return true
}

/**
 * The heights within a block that items land on and bump into, or null for
 * a block they fall through. Whole cubes fill the block, slabs fill their
 * half, and stairs at least the half they stand on. Other blocks that hold
 * items up, like chests and carpets, do from the floor to their top. A big
 * dripleaf's leaf holds items up until it tips right over, and a hopper's
 * bowl holds them in it.
 */
internal fun BlockState.solidHeights(): ClosedFloatingPointRange<Double>? = when {
    isFullCube -> 0.0..1.0
    name == HOPPER -> 0.0..HOPPER_BOWL
    name == BIG_DRIPLEAF -> leafHeights()
    properties["minecraft:vertical_half"] == "top" -> 0.5..1.0
    properties["minecraft:vertical_half"] == "bottom" -> 0.0..0.5
    "weirdo_direction" in properties -> if (properties["upside_down_bit"] == 1.toByte()) 0.5..1.0 else 0.0..0.5
    else -> partialHeight()?.let { 0.0..it }
}

/**
 * How high the top of a block that isn't a whole cube, but holds items up
 * all the same, is, or null for one items fall through. A cauldron's is
 * the floor inside it.
 */
private fun BlockState.partialHeight(): Double? {
    val name = name.removePrefix("minecraft:")
    return PARTIAL_HEIGHTS[name] ?: PARTIAL_HEIGHT_ENDINGS.firstNotNullOfOrNull { (ending, height) -> height.takeIf { name.endsWith(ending) } }
}

/** The heights of blocks that hold items up, by their sixteenths, and of the families whose names end alike. */
private val PARTIAL_HEIGHTS = mapOf(
    "enchanting_table" to 12, "end_portal_frame" to 13, "stonecutter_block" to 9, "bed" to 9, "cake" to 8,
    "daylight_detector" to 6, "daylight_detector_inverted" to 6, "campfire" to 7, "soul_campfire" to 7,
    "powered_repeater" to 2, "unpowered_repeater" to 2, "powered_comparator" to 2, "unpowered_comparator" to 2, "cauldron" to 4, "lectern" to 15,
).mapValues { it.value / 16.0 }
private val PARTIAL_HEIGHT_ENDINGS = listOf("chest" to 14, "carpet" to 1, "anvil" to 16, "piston_arm_collision" to 16).map { (ending, sixteenths) -> ending to sixteenths / 16.0 }

private fun Vec3.toDoubles() = Triple(x.toDouble(), y.toDouble(), z.toDouble())
