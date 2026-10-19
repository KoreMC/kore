package org.koremc.world

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.AIR
import org.koremc.registry.BlockState
import org.koremc.registry.ItemStacks

/** The most blocks `/fill` fills at once, as the game has it. */
const val MAX_FILL = 32768

/** The most blocks `/clone` copies at once, as the game has it. */
const val MAX_CLONE = 655360

/**
 * The most blocks `/fill` fills, and `/clone` copies, at once, or null for
 * no limit: the game's own limits, unless kore.jsonc changes them.
 */
@Serializable
data class CommandLimits(
    @Serializable(with = FillLimit::class) val fill: Int? = MAX_FILL,
    @Serializable(with = CloneLimit::class) val clone: Int? = MAX_CLONE,
)

/**
 * A limit as kore.jsonc gives it: a number, true for the game's own limit,
 * [own], or false for none, which reads as null. Each limit reads with its
 * own, as [FillLimit] and [CloneLimit].
 */
sealed class LimitSerializer(private val own: Int) : KSerializer<Int?> {
    // Nullable, to write no limit as false itself, rather than have it written as null.
    override val descriptor = PrimitiveSerialDescriptor("org.koremc.world.${this::class.simpleName}", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: Int?) = (encoder as JsonEncoder).encodeJsonElement(value?.let(::JsonPrimitive) ?: JsonPrimitive(false))

    override fun deserialize(decoder: Decoder): Int? {
        val given = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
        val limit = if (given == null || given.isString) null else given.booleanOrNull?.let { on -> if (on) own else return null } ?: given.intOrNull
        if (limit == null || limit <= 0) throw SerializationException("a limit is a number of blocks, true for the game's own, $own, or false for none, not $given")
        return limit
    }
}

object FillLimit : LimitSerializer(MAX_FILL)

object CloneLimit : LimitSerializer(MAX_CLONE)

/**
 * How `/fill` fills its box, and `/setblock` its block: putting the block
 * everywhere, breaking what was there first, dropping it as mined, only
 * where there's air, all over its outside with air within, or all over
 * its outside, leaving what's within.
 */
enum class FillMode { REPLACE, DESTROY, KEEP, HOLLOW, OUTLINE }

/** What a command editing blocks did: how many blocks it changed, and what that changed, around them too, with what broke. */
class Edited(val count: Int, val changes: Changes)

/** The box from [from] to [to], taking in both, whichever corners they are. */
class BlockBox(from: BlockPos, to: BlockPos) {
    val min = BlockPos(minOf(from.x, to.x), minOf(from.y, to.y), minOf(from.z, to.z))
    val max = BlockPos(maxOf(from.x, to.x), maxOf(from.y, to.y), maxOf(from.z, to.z))

    /** How many blocks it takes in, which may be more than an int holds. */
    val volume get() = (max.x - min.x + 1).toLong() * (max.y - min.y + 1) * (max.z - min.z + 1)

    /** Whether it's within the overworld's height. */
    val inWorld get() = min.y >= MIN_Y && max.y <= MAX_Y

    /** Whether it shares a block with [other]. */
    fun overlaps(other: BlockBox) =
        min.x <= other.max.x && max.x >= other.min.x && min.y <= other.max.y && max.y >= other.min.y && min.z <= other.max.z && max.z >= other.min.z

    /** Whether [pos] is on its outside. */
    fun onOutside(pos: BlockPos) = pos.x == min.x || pos.x == max.x || pos.y == min.y || pos.y == max.y || pos.z == min.z || pos.z == max.z

    /** The same box moved so its lowest corner is at [corner]. */
    fun movedTo(corner: BlockPos) = BlockBox(corner, BlockPos(corner.x + max.x - min.x, corner.y + max.y - min.y, corner.z + max.z - min.z))

    /** Every block in it, along z, then y, then x. */
    fun positions(): Sequence<BlockPos> = sequence {
        for (x in min.x..max.x) for (y in min.y..max.y) for (z in min.z..max.z) yield(BlockPos(x, y, z))
    }
}

/**
 * Fills [box] with [state], as `/fill` does, by [mode], and only where the
 * block there is one [replacing] takes, for a plain fill. A block put down
 * by a command stays whether it could stand there or not, until something
 * beside it changes, but what's around takes it in, as it does any change:
 * sand over air falls, water flows, and a torch on a block that went
 * breaks. What a replaced block kept goes with it, a chest's items too,
 * unless it's destroyed, which drops it and them. Blocks already as the
 * fill would make them stay as they are, with all they keep.
 */
fun FlatWorld.fill(box: BlockBox, state: BlockState, mode: FillMode = FillMode.REPLACE, replacing: (BlockState) -> Boolean = { true }): Edited {
    val air = blocks.stateOf(AIR)
    val broken = linkedMapOf<BlockPos, BrokenBlock>()
    val songs = mutableListOf<SongChange>()
    val unpaired = mutableListOf<BlockPos>()
    val changed = mutableListOf<BlockPos>()
    inBulk {
        for (pos in box.positions()) {
            val there = stateAt(pos) ?: continue
            val put = when (mode) {
                FillMode.HOLLOW -> if (box.onOutside(pos)) state else air
                FillMode.OUTLINE -> if (box.onOutside(pos)) state else continue
                FillMode.KEEP -> if (there.name == AIR) state else continue
                FillMode.REPLACE -> if (replacing(there)) state else continue
                FillMode.DESTROY -> state
            }
            if (there == put) continue
            stopSong(pos)?.let(songs::add)
            val partner = if (mode == FillMode.DESTROY && there.name != AIR && there.liquid == null) breakAt(pos, there, broken) else blockEntityAt(pos)?.let(::unpairChestFrom)
            partner?.let(unpaired::add)
            forget(pos)
            set(pos, put)
            changed += pos
        }
    }
    val settled = settleEdited(changed, alike = true)
    return Edited(changed.size, Changes(broken, (changed + unpaired + settled.reshaped).distinct(), songs = songs))
}

/**
 * Copies the blocks in [box] that [copies] takes so that its lowest corner
 * goes to [corner], as `/clone` does: each with the water inside it and all
 * it keeps, a chest's items and a furnace's work. Moving them leaves air
 * where they were, but where the copy lands. The copy is taken whole
 * before any of it lands, so the two boxes can overlap. What's around them
 * takes them in, as [fill] has it.
 */
fun FlatWorld.clone(box: BlockBox, corner: BlockPos, move: Boolean, items: ItemStacks, copies: (BlockState) -> Boolean = { true }): Edited {
    val dx = corner.x - box.min.x
    val dy = corner.y - box.min.y
    val dz = corner.z - box.min.z
    val copied = box.positions().mapNotNull { pos ->
        val state = stateAt(pos)?.takeIf(copies) ?: return@mapNotNull null
        Copied(pos, state, liquidAt(pos), savedBlockEntity(pos))
    }.toList()
    val landing = copied.mapTo(HashSet()) { BlockPos(it.pos.x + dx, it.pos.y + dy, it.pos.z + dz) }
    val air = blocks.stateOf(AIR)
    val songs = mutableListOf<SongChange>()
    val changed = mutableListOf<BlockPos>()
    fun put(pos: BlockPos, state: BlockState, liquid: BlockState?, blockEntity: NbtMap?) {
        stopSong(pos)?.let(songs::add)
        blockEntityAt(pos)?.let(::unpairChestFrom)?.let(changed::add)
        forget(pos)
        if (liquidAt(pos) != null) setLiquid(pos, null)
        set(pos, state)
        if (liquidAt(pos) != liquid) setLiquid(pos, liquid)
        blockEntity?.let { restoreBlockEntity(it.movedBy(dx, dy, dz), items) }
        changed += pos
    }
    inBulk {
        if (move) copied.filter { it.pos !in landing }.forEach { put(it.pos, air, null, null) }
        for ((pos, state, liquid, blockEntity) in copied) put(BlockPos(pos.x + dx, pos.y + dy, pos.z + dz), state, liquid, blockEntity)
    }
    val settled = settleEdited(changed, alike = false)
    return Edited(copied.size, Changes(emptyMap(), (changed + settled.reshaped).distinct(), songs = songs))
}

/**
 * How many blocks in [box] are the same as those where its lowest corner
 * would go to [corner], as `/testforblocks` compares them: all of them, or
 * those that aren't air, if [masked]. Null if any isn't.
 */
fun FlatWorld.compare(box: BlockBox, corner: BlockPos, masked: Boolean): Int? {
    var compared = 0
    for (pos in box.positions()) {
        val state = stateAt(pos) ?: continue
        if (masked && state.name == AIR) continue
        if (stateAt(BlockPos(pos.x + corner.x - box.min.x, pos.y + corner.y - box.min.y, pos.z + corner.z - box.min.z)) != state) return null
        compared++
    }
    return compared
}

/** A block `/clone` copies, from [pos], with the water inside it and its block entity as the game saves it. */
private data class Copied(val pos: BlockPos, val state: BlockState, val liquid: BlockState?, val blockEntity: NbtMap?)

/** This block entity, as the game saves it, moved [dx], [dy] and [dz] along, with the chest it pairs with. */
private fun NbtMap.movedBy(dx: Int, dy: Int, dz: Int): NbtMap = toBuilder().apply {
    putInt("x", getInt("x") + dx)
    putInt("y", getInt("y") + dy)
    putInt("z", getInt("z") + dz)
    if (containsKey(PAIR_X)) putInt(PAIR_X, getInt(PAIR_X) + dx)
    if (containsKey(PAIR_Z)) putInt(PAIR_Z, getInt(PAIR_Z) + dz)
}.build()

/**
 * Brings the world around the many blocks a command [changed] in line with
 * them, as [settle] does around any change, but only around those at their
 * edge, beside blocks the command left as they were: those within are beside
 * only blocks it changed, and only need to join them, if they're walls, fences
 * or the like, which they do here. Blocks all [alike] within, as a fill
 * leaves them, don't fall, flow or carry power among themselves either, so
 * only those at the edge are looked at for that, next tick. Returns where
 * blocks changed shape.
 */
private fun FlatWorld.settleEdited(changed: List<BlockPos>, alike: Boolean): Changes = inBulk {
    val within = changed.toHashSet()
    val (edge, inside) = changed.partition { pos -> Face.entries.any { pos.side(it) !in within } }
    if (alike) {
        val skipped = inside.toHashSet()
        blockChanges.removeAll(skipped)
        powerChanges.removeAll(skipped)
    }
    val joined = inside.mapNotNull { pos ->
        val state = stateAt(pos) ?: return@mapNotNull null
        joined(pos, state).takeIf { it != state }?.let { shaped -> pos to shaped }
    }
    // All shaped from their neighbours as they were, before any of them changes.
    joined.forEach { (pos, shaped) -> set(pos, shaped) }
    val settled = settle(edge)
    Changes(emptyMap(), joined.map { it.first } + settled.reshaped)
}
