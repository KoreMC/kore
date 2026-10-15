package org.koremc.world

import org.koremc.registry.AIR
import org.koremc.registry.BlockState
import org.koremc.registry.ItemStacks

private const val CAKE = "minecraft:cake"
private const val CANDLE_CAKE = "_cake"
private const val BITES = "bite_counter"
private const val LIT = "lit"

private const val ADD_CANDLE = "cake.add_candle"
private const val PUT_OUT_CANDLE = "extinguish.candle"

/** How many slices a cake has: the last bite of it takes the cake. */
private const val SLICES = 7

/** How full a comparator reads a cake with every slice left, as one with a candle always is. */
internal const val WHOLE_CAKE_FULLNESS = SLICES * 2

/** How much a slice of cake feeds: two points of food, and a little saturation. */
const val CAKE_NUTRITION = 2
const val CAKE_SATURATION = 0.1f

/** Whether an item called this is a candle, of any colour. */
fun isCandle(item: String) = item == "minecraft:candle" || item.endsWith("_candle")

/** Cake with a candle of any colour on it. */
val BlockState.hasCandle get() = name.endsWith("candle$CANDLE_CAKE")

/** Cake, whole or bitten, and cake with a candle on it. */
val BlockState.isCake get() = name == CAKE || hasCandle

/** The candle on this cake, by its item's name, or null for a cake without one, or any other block. */
val BlockState.candle get() = name.removeSuffix(CANDLE_CAKE).takeIf { hasCandle }

/** Whether this is a cake with a lit candle on it. */
val BlockState.candleLit get() = hasCandle && properties[LIT] == bit(true)

/**
 * A slice of the cake at [pos], [cake], eaten: a bite more out of it, or
 * the cake gone with its last slice. A candle on it comes off first and
 * drops, if the `doTileDrops` rule lets blocks drop. Returns what changed,
 * or null for a cake the palette has no bitten state of.
 */
fun FlatWorld.biteCake(pos: BlockPos, cake: BlockState, items: ItemStacks): ItemUsedOn? {
    val whole = if (cake.hasCandle) blocks.defaultOf(CAKE) ?: return null else cake
    val bites = whole.number(BITES) + 1
    val after = if (bites >= SLICES) blocks.stateOf(AIR) else blocks.find(CAKE, whole.properties + (BITES to bites)) ?: return null
    set(pos, after)
    val candle = cake.candle?.takeIf { rules.doTileDrops }?.let { items.of(it) }
    return ItemUsedOn(listOf(pos), dropped = listOfNotNull(candle), dropAt = pos)
}

/**
 * The candle [candle] put on top of the cake at [pos], [cake], which takes
 * one only while it's whole, out, in the candle's colour. Returns what
 * changed, with the candle used up, or null for a cake that's bitten or
 * has a candle already.
 */
fun FlatWorld.putCandleOn(pos: BlockPos, cake: BlockState, candle: String): ItemUsedOn? {
    if (cake.name != CAKE || cake.number(BITES) != 0 || !isCandle(candle)) return null
    val withCandle = blocks.defaultOf(candle + CANDLE_CAKE) ?: return null
    set(pos, blocks.find(withCandle.name, withCandle.properties + (LIT to bit(false))) ?: withCandle)
    return ItemUsedOn(listOf(pos), BlockSound(pos, ADD_CANDLE, -1), HeldAfter.USED_UP)
}

/** Puts out the lit candle on the cake at [pos], [cake]. Returns what changed, or null for a cake without a lit candle. */
fun FlatWorld.putOutCandle(pos: BlockPos, cake: BlockState): ItemUsedOn? {
    if (!cake.candleLit) return null
    set(pos, blocks.find(cake.name, cake.properties + (LIT to bit(false))) ?: return null)
    return ItemUsedOn(listOf(pos), BlockSound(pos, PUT_OUT_CANDLE, -1))
}
