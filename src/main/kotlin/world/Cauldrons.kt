package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.koremc.registry.BlockState
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.dyedColour
import org.koremc.registry.withDyedColour

internal const val CAULDRON = "minecraft:cauldron"
private const val LEVEL = "fill_level"
private const val CONTENTS = "cauldron_liquid"

/** What a cauldron holds, by the game's names: water, which a potion or dye goes in too, lava, or powder snow. */
private const val OF_WATER = "water"
private const val OF_LAVA = "lava"
private const val OF_POWDER_SNOW = "powder_snow"

/** How many levels a cauldron holds, and how many a bottle takes, and dipping something in to dye or wash it. */
private const val FULL = 6
private const val BOTTLE = 2
private const val DIP = 1

// What a cauldron's block entity keeps: the potion in it, by its number and the kind of bottle it came in, and its water's dye.
private const val POTION_ID = "PotionId"
private const val POTION_TYPE = "PotionType"
private const val CUSTOM_COLOUR = "CustomColor"
private const val NONE = -1

// What the client shows of a cauldron changing, its sound and particles, by the game's numbers.
private const val MIXED = 3501
private const val DYED_ITEM = 3502
private const val WASHED_ITEM = 3503
private const val FILLED_POTION = 3504
private const val TOOK_POTION = 3505
private const val FILLED_WATER = 3506
private const val TOOK_WATER = 3507
private const val ADDED_DYE = 3508
private const val WASHED_BANNER = 3509
private const val FILLED_LAVA = 3512
private const val TOOK_LAVA = 3513
private const val FILLED_POWDER_SNOW = 3514
private const val TOOK_POWDER_SNOW = 3515

private const val WATER_BUCKET = "minecraft:water_bucket"
private const val LAVA_BUCKET = "minecraft:lava_bucket"
private const val POWDER_SNOW_BUCKET = "minecraft:powder_snow_bucket"
private const val ARROW = "minecraft:arrow"
private const val BANNER = "minecraft:banner"
private const val PATTERNS = "Patterns"
private const val UNDYED_SHULKER_BOX = "minecraft:undyed_shulker_box"

/** The bucket each of what a cauldron holds goes in. */
private val BUCKETS = mapOf(OF_WATER to WATER_BUCKET, OF_LAVA to LAVA_BUCKET, OF_POWDER_SNOW to POWDER_SNOW_BUCKET)

/** The bottles a potion comes in, as cauldrons number them. */
private val POTION_BOTTLES = listOf("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion")

/** Items that take on the colour of dyed water, and lose it in plain water. */
private val DYEABLE = setOf(
    "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "leather_horse_armor", "wolf_armor",
).mapTo(HashSet()) { "minecraft:$it" }

/** What dyes water besides dyes, and the dye each counts as. */
private val DYE_LIKE = mapOf(
    "minecraft:ink_sac" to "minecraft:black_dye", "minecraft:cocoa_beans" to "minecraft:brown_dye",
    "minecraft:lapis_lazuli" to "minecraft:blue_dye", "minecraft:bone_meal" to "minecraft:white_dye",
)

/** The most arrows a cauldron's potion tips at once with this little left, and what tipping more costs. */
private const val LITTLE_LEFT = 3
private const val ARROWS_PER_TIP = 16

val BlockState.isCauldron get() = name == CAULDRON

/** A potion, in a cauldron or a bottle: which bottle it's in, as cauldrons number them, and which potion it is. */
private data class Potion(val bottle: Int, val id: Int)

/** The potion [stack] is, or null for anything else. Water in a bottle is a potion, numbered 0. */
private fun potionOf(stack: ItemStack): Potion? = POTION_BOTTLES.indexOf(stack.item.name).takeIf { it >= 0 }?.let { Potion(it, stack.aux) }

/** The block entity of an empty cauldron, or one of plain water. */
internal fun cauldronData(): NbtMap = cauldronData(null, null)

/** The block entity of a cauldron of [potion], or of water dyed [colour]. */
private fun cauldronData(potion: Potion?, colour: Int?): NbtMap {
    val data = NbtMap.builder().putString("id", "Cauldron")
        .putShort(POTION_ID, (potion?.id ?: NONE).toShort())
        .putShort(POTION_TYPE, (potion?.bottle ?: NONE).toShort())
    colour?.let { data.putInt(CUSTOM_COLOUR, it) }
    return data.build()
}

/** Fills the cauldron at [pos] to [level] with [contents], [potion] or water dyed [colour]. At 0, it's empty. */
private fun FlatWorld.fillCauldron(pos: BlockPos, contents: String, level: Int, potion: Potion? = null, colour: Int? = null) {
    val empty = level <= 0
    blocks.find(CAULDRON, mapOf<String, Any>(CONTENTS to if (empty) OF_WATER else contents, LEVEL to level.coerceIn(0, FULL)))?.let { set(pos, it) }
    setBlockEntity(pos, if (empty) cauldronData() else cauldronData(potion, colour))
}

/**
 * Uses [held] on the cauldron at [pos], [state], as a player clicking it
 * does, as Bedrock has it. Buckets fill a cauldron with water, lava or
 * powder snow, and empty ones take a full one's back. A bottle of water
 * fills a third of one and a glass bottle takes a third out, and any other
 * potion goes in to make a cauldron of that potion, from which bottles
 * take it, and arrows dipped come out tipped with it. What's held swaps for
 * what's left of it, made with [items]: an empty bucket or bottle, say.
 * Anything else that goes in mixes with what's there, which leaves the
 * cauldron empty. Dyes colour the water in a cauldron, mixing with any
 * dye already there, and leather armour dipped in takes the colour, while
 * in plain water it washes the colour out, as it does a banner's top
 * pattern and a shulker box's dye, each at a sixth of the water. What's
 * dipped changes, in creative too. Null for anything else, or what a
 * cauldron can't do as it is.
 */
fun FlatWorld.useCauldron(pos: BlockPos, state: BlockState, held: ItemStack?, items: ItemStacks): ItemUsedOn? {
    held ?: return null
    val name = held.item.name
    val level = state.properties[LEVEL] as? Int ?: 0
    val contents = state.properties[CONTENTS] as? String ?: OF_WATER
    val data = blockEntityAt(pos)
    val potion = data?.getShort(POTION_ID, NONE.toShort())?.toInt()?.takeIf { it >= 0 }
        ?.let { Potion(data.getShort(POTION_TYPE, 0).toInt().coerceIn(POTION_BOTTLES.indices), it) }
    val colour = data?.takeIf { it.containsKey(CUSTOM_COLOUR) }?.getInt(CUSTOM_COLOUR)
    val bottled = potionOf(held)

    return when {
        level == 0 -> when {
            name in BUCKETS.values -> {
                val filled = BUCKETS.entries.first { it.value == name }.key
                refill(pos, items, FULL, filled, filledEvent(filled), BUCKET)
            }
            bottled?.id == WATER_POTION -> refill(pos, items, BOTTLE, OF_WATER, FILLED_WATER, GLASS_BOTTLE)
            bottled != null -> refill(pos, items, BOTTLE, OF_WATER, FILLED_POTION, GLASS_BOTTLE, potion = bottled)
            else -> null
        }
        contents != OF_WATER -> when (name) {
            BUCKET -> if (level == FULL) refill(pos, items, 0, contents, if (contents == OF_LAVA) TOOK_LAVA else TOOK_POWDER_SNOW, BUCKETS.getValue(contents)) else null
            BUCKETS[contents] -> refill(pos, items, FULL, contents, filledEvent(contents), BUCKET)
            in BUCKETS.values -> mixInto(pos, held, items)
            else -> if (bottled != null) mixInto(pos, held, items) else null
        }
        potion != null -> when {
            name == GLASS_BOTTLE -> if (level >= BOTTLE) {
                refill(pos, items, level - BOTTLE, OF_WATER, TOOK_POTION, POTION_BOTTLES[potion.bottle], potion.id, potion)
            } else {
                null
            }
            bottled == potion -> if (level < FULL) refill(pos, items, minOf(FULL, level + BOTTLE), OF_WATER, FILLED_POTION, GLASS_BOTTLE, potion = potion) else null
            bottled != null || name in BUCKETS.values -> mixInto(pos, held, items)
            name == ARROW && held.aux == 0 -> tipArrows(pos, held, level, potion, items)
            else -> null
        }
        else -> useWater(pos, held, level, colour, items)
    }
}

/** What the client shows of a cauldron being filled with [contents] from a bucket. */
private fun filledEvent(contents: String) = when (contents) {
    OF_LAVA -> FILLED_LAVA
    OF_POWDER_SNOW -> FILLED_POWDER_SNOW
    else -> FILLED_WATER
}

/**
 * Fills the cauldron at [pos] to [level] with [contents], [potion] or water
 * dyed [colour], with the client showing [event], and swaps one of what's
 * held for [into], in its variant [aux], made with [items].
 */
private fun FlatWorld.refill(
    pos: BlockPos,
    items: ItemStacks,
    level: Int,
    contents: String,
    event: Int,
    into: String,
    aux: Int = 0,
    potion: Potion? = null,
    colour: Int? = null,
): ItemUsedOn {
    fillCauldron(pos, contents, level, potion, colour)
    return ItemUsedOn(listOf(pos), held = HeldAfter.SWAPPED, given = items.of(into, aux = aux), events = listOf(WorldEvent(pos, event, 0)))
}

/** Mixes [held] into the cauldron at [pos], which leaves it empty, and the bottle or bucket [held] was in. */
private fun FlatWorld.mixInto(pos: BlockPos, held: ItemStack, items: ItemStacks): ItemUsedOn =
    refill(pos, items, 0, OF_WATER, MIXED, if (potionOf(held) != null) GLASS_BOTTLE else BUCKET)

/** Uses [held] on the cauldron of water at [pos], [level] deep and dyed [colour], if it is, as [useCauldron] has it. */
private fun FlatWorld.useWater(pos: BlockPos, held: ItemStack, level: Int, colour: Int?, items: ItemStacks): ItemUsedOn? {
    val name = held.item.name
    val bottled = potionOf(held)
    val dye = DYE_COLOURS[DYE_LIKE[name] ?: name]
    return when {
        name == WATER_BUCKET -> refill(pos, items, FULL, OF_WATER, FILLED_WATER, BUCKET)
        name == BUCKET -> if (level == FULL) refill(pos, items, 0, OF_WATER, TOOK_WATER, WATER_BUCKET) else null
        name == GLASS_BOTTLE -> if (level >= BOTTLE) refill(pos, items, level - BOTTLE, OF_WATER, TOOK_WATER, POTION_BOTTLES[0], WATER_POTION, colour = colour) else null
        // A bottle of water washes the dye out, as it tops the water up.
        bottled?.id == WATER_POTION -> if (level < FULL || colour != null) refill(pos, items, minOf(FULL, level + BOTTLE), OF_WATER, FILLED_WATER, GLASS_BOTTLE) else null
        bottled != null || name in BUCKETS.values -> mixInto(pos, held, items)
        dye != null -> {
            val mixed = colour?.let { mixColours(it, opaque(dye)) } ?: opaque(dye)
            if (mixed == colour) return null
            fillCauldron(pos, OF_WATER, level, colour = mixed)
            ItemUsedOn(listOf(pos), held = HeldAfter.USED_UP, events = listOf(WorldEvent(pos, ADDED_DYE, 0)))
        }
        name in DYEABLE -> when {
            colour != null -> if (held.dyedColour != colour && level >= DIP) dip(pos, level - DIP, colour, DYED_ITEM, held.withDyedColour(colour)) else null
            held.dyedColour != null -> dip(pos, level - DIP, null, WASHED_ITEM, held.withDyedColour(null))
            else -> null
        }
        colour != null -> null
        name == BANNER -> {
            val patterns = held.nbt.getList(PATTERNS, NbtType.COMPOUND, emptyList())
            if (patterns.isEmpty()) return null
            val washed = held.nbt.toBuilder().apply { if (patterns.size == 1) remove(PATTERNS) else putList(PATTERNS, NbtType.COMPOUND, patterns.dropLast(1)) }
            dip(pos, level - DIP, null, WASHED_BANNER, held.copy(nbt = washed.build()))
        }
        name.endsWith("_shulker_box") && name != UNDYED_SHULKER_BOX ->
            items.of(UNDYED_SHULKER_BOX)?.let { dip(pos, level - DIP, null, WASHED_ITEM, it.copy(nbt = held.nbt)) }
        else -> null
    }
}

/** Dips one of what's held in the cauldron at [pos], which leaves it [level] deep, dyed [colour], and the item [dipped]. */
private fun FlatWorld.dip(pos: BlockPos, level: Int, colour: Int?, event: Int, dipped: ItemStack): ItemUsedOn {
    fillCauldron(pos, OF_WATER, level, colour = colour)
    return ItemUsedOn(listOf(pos), held = HeldAfter.CHANGED, given = dipped.copy(count = 1), events = listOf(WorldEvent(pos, event, 0)))
}

/**
 * Tips the arrows [held] in the cauldron of [potion] at [pos], [level]
 * deep, as Bedrock does: with a little left, up to sixteen, which takes
 * the rest, and otherwise a level for up to sixteen, four for up to
 * thirty-two, and the lot for more.
 */
private fun FlatWorld.tipArrows(pos: BlockPos, held: ItemStack, level: Int, potion: Potion, items: ItemStacks): ItemUsedOn? {
    val (tipped, cost) = when {
        level <= LITTLE_LEFT -> minOf(held.count, ARROWS_PER_TIP) to level
        held.count <= ARROWS_PER_TIP -> held.count to 1
        held.count <= ARROWS_PER_TIP * 2 || level < FULL -> minOf(held.count, ARROWS_PER_TIP * 2) to 4
        else -> held.count to FULL
    }
    // A tipped arrow is numbered one past its potion, as plain arrows are 0.
    val arrows = items.of(ARROW, tipped, aux = potion.id + 1) ?: return null
    fillCauldron(pos, OF_WATER, level - cost, potion)
    return ItemUsedOn(listOf(pos), held = HeldAfter.CHANGED, given = arrows, count = tipped, events = listOf(WorldEvent(pos, TOOK_POTION, 0)))
}

/** Two colours mixed, as dyes mix in a cauldron: half of each. */
private fun mixColours(one: Int, other: Int): Int {
    fun channel(shift: Int) = ((((one shr shift) and 0xFF) + ((other shr shift) and 0xFF)) / 2) shl shift
    return opaque(channel(16) or channel(8) or channel(0))
}

/**
 * Fills each cauldron with water above it, among [changed] or under it, as
 * water touching its top does in Bedrock: full, of plain water, whatever
 * it held but lava or powder snow, with the client showing it in [events].
 * Returns where cauldrons filled.
 */
internal fun FlatWorld.fillFromAbove(changed: Collection<BlockPos>, events: MutableList<WorldEvent>): List<BlockPos> {
    val filled = mutableListOf<BlockPos>()
    for (pos in changed.flatMap { listOf(it, it.side(Face.DOWN)) }.distinct()) {
        val state = stateAt(pos)?.takeIf { it.isCauldron } ?: continue
        if (stateAt(pos.side(Face.UP))?.liquid != Liquid.WATER || state.properties[CONTENTS] != OF_WATER) continue
        val data = blockEntityAt(pos)
        val plain = data == null || (data.getShort(POTION_ID, NONE.toShort()) < 0 && !data.containsKey(CUSTOM_COLOUR))
        if (state.properties[LEVEL] == FULL && plain) continue
        fillCauldron(pos, OF_WATER, FULL)
        events += WorldEvent(pos, FILLED_WATER, 0)
        filled += pos
    }
    return filled
}

/**
 * Adds a level of rain to the cauldron at [pos], [state], if it's empty or
 * of water, not full: dyed water stays its colour. Returns whether it did.
 */
internal fun FlatWorld.rainFills(pos: BlockPos, state: BlockState): Boolean {
    val level = state.properties[LEVEL] as? Int ?: 0
    val data = blockEntityAt(pos)
    val potion = data?.getShort(POTION_ID, NONE.toShort())?.let { it >= 0 } == true
    if (state.properties[CONTENTS] != OF_WATER || level >= FULL || potion) return false
    val colour = data?.takeIf { it.containsKey(CUSTOM_COLOUR) }?.getInt(CUSTOM_COLOUR)
    fillCauldron(pos, OF_WATER, level + 1, colour = colour)
    return true
}

/** Adds a level of snow, as powder snow, to the cauldron at [pos], [state], if it's empty or of powder snow, not full. Returns whether it did. */
internal fun FlatWorld.snowFills(pos: BlockPos, state: BlockState): Boolean {
    val level = state.properties[LEVEL] as? Int ?: 0
    if (level > 0 && (state.properties[CONTENTS] != OF_POWDER_SNOW || level >= FULL)) return false
    fillCauldron(pos, OF_POWDER_SNOW, level + 1)
    return true
}

/** Fills the cauldron at [pos], [state], with lava dripping into it, if it's empty. Returns whether it did. */
internal fun FlatWorld.lavaFills(pos: BlockPos, state: BlockState): Boolean {
    if ((state.properties[LEVEL] as? Int ?: 0) > 0) return false
    fillCauldron(pos, OF_LAVA, FULL)
    return true
}

/**
 * The network ID of the cauldron at [pos] a level off, for the client to
 * see before the cauldron as it is: the client draws a cauldron's water,
 * and its colour, afresh only as its block changes, so a change to the
 * colour alone wouldn't show. Null for a block that isn't a cauldron.
 */
internal fun FlatWorld.cauldronNudge(pos: BlockPos): Int? {
    val state = stateAt(pos)?.takeIf { it.isCauldron } ?: return null
    val level = state.properties[LEVEL] as? Int ?: 0
    return blocks.find(CAULDRON, state.properties + (LEVEL to if (level == FULL) level - 1 else level + 1))?.networkId
}

/** How full the cauldron [state] is, as a comparator reads it: a third for each third, and full of lava. */
internal fun cauldronFullness(state: BlockState): Int {
    if (state.properties[CONTENTS] == OF_LAVA && state.properties[LEVEL] != 0) return FULL / BOTTLE
    return ((state.properties[LEVEL] as? Int ?: 0) + 1) / BOTTLE
}
