package org.koremc.world

import org.koremc.protocol.Difficulty
import org.koremc.registry.AIR
import org.koremc.registry.BlockState
import org.koremc.registry.ItemStack
import kotlin.random.Random

internal const val FIRE = "minecraft:fire"
internal const val SOUL_FIRE = "minecraft:soul_fire"
internal const val FLINT_AND_STEEL = "minecraft:flint_and_steel"
internal const val FIRE_CHARGE = "minecraft:fire_charge"

/** How old fire gets: the older, the likelier it is to burn out, and the slower it spreads. */
private const val OLDEST = 15

/** Fire looks at what it's burning every this many ticks, and up to [FIRE_TICKS_MORE] more. */
private const val FIRE_TICKS = 30
private const val FIRE_TICKS_MORE = 10

/** How unlikely fire is to burn a block beside it away, below or above it, and on each other side. */
private const val BURN_AGAINST_VERTICAL = 250
private const val BURN_AGAINST_SIDE = 300

/** How unlikely fire is to spread to a space at its own height or one off, and how much less for each block higher than that. */
private const val SPREAD_AGAINST = 100

/** How many fires lightning starts around where it strikes, besides there. */
private const val LIGHTNING_FIRES = 4

private const val IGNITE = "ignite"
private const val EXTINGUISH = "extinguish.fire"
private const val EXTINGUISH_CANDLE = "extinguish.candle"

/** The sound of a fire charge used, which the game plays as a blaze's fireball. */
private const val BLAZE_FIREBALL = 1009

/** Blocks fire on top of burns for ever, which no rain puts out. */
private val BURNS_FOREVER = setOf("minecraft:netherrack", "minecraft:magma")

/** Blocks soul fire burns on, and only on. */
private val SOUL_GROUND = setOf("minecraft:soul_sand", "minecraft:soul_soil")

internal const val CAMPFIRE_OUT = "extinguished"
private const val LIT = "lit"

val BlockState.isFire get() = name == FIRE || name == SOUL_FIRE

private val BlockState.isCampfire get() = name == "minecraft:campfire" || name == "minecraft:soul_campfire"
private val BlockState.isCandle get() = name.endsWith("candle") || name.endsWith("candle_cake")

/**
 * Flint and steel, or a fire charge, [held], used on the block at [pos],
 * on its [face]: a campfire or candle that's out lights, unless it's in
 * water, and otherwise fire starts in the space against that face, if it's
 * empty and fire can burn there, or a nether portal opens there, inside an
 * empty frame. Flint and steel wears, and a fire charge is used up. Null
 * if nothing caught.
 */
fun FlatWorld.ignite(pos: BlockPos, face: Face, held: ItemStack, random: Random = Random.Default): ItemUsedOn? {
    val clicked = stateAt(pos) ?: return null
    val lit = when {
        isWet(pos) -> null
        clicked.isCampfire && clicked.properties[CAMPFIRE_OUT] == bit(true) -> blocks.find(clicked.name, clicked.properties + (CAMPFIRE_OUT to bit(false)))
        clicked.isCandle && clicked.properties[LIT] == bit(false) -> blocks.find(clicked.name, clicked.properties + (LIT to bit(true)))
        else -> null
    }
    val at = if (lit != null) pos else pos.side(face)
    val changed = if (lit != null) {
        set(pos, lit)
        listOf(pos)
    } else {
        if (stateAt(at)?.name != AIR) return null
        setFire(at, 0, random).ifEmpty { return null }
    }
    val charge = held.item.name == FIRE_CHARGE
    return ItemUsedOn(
        changed,
        sound = BlockSound(at, IGNITE, -1).takeUnless { charge },
        held = if (charge) HeldAfter.USED_UP else HeldAfter.WORN,
        events = listOfNotNull(WorldEvent(at, BLAZE_FIREBALL, 0).takeIf { charge }),
    )
}

/**
 * Puts out the fire on the [face] of the block at [pos], as a player
 * hitting it does, with its sound. Returns what changed, or null if
 * there's no fire there.
 */
fun FlatWorld.putOutFire(pos: BlockPos, face: Face): Changes? {
    val at = pos.side(face)
    if (stateAt(at)?.isFire != true) return null
    set(at, blocks.stateOf(AIR))
    return Changes(emptyMap(), listOf(at), listOf(BlockSound(at, EXTINGUISH, -1)))
}

/**
 * Douses the fire at [pos] and at each side of it, as a splash of water
 * does: fire goes out, and lit campfires and candles, each with its sound.
 * Returns what changed, or null if nothing was burning.
 */
fun FlatWorld.douse(pos: BlockPos): Changes? {
    val changed = mutableListOf<BlockPos>()
    val sounds = mutableListOf<BlockSound>()
    for (at in listOf(pos) + listOf(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST).map(pos::side)) {
        val state = stateAt(at) ?: continue
        val (out, sound) = when {
            state.isFire -> blocks.stateOf(AIR) to EXTINGUISH
            state.isCampfire && state.properties[CAMPFIRE_OUT] == bit(false) -> blocks.find(state.name, state.properties + (CAMPFIRE_OUT to bit(true))) to EXTINGUISH
            state.isCandle && state.properties[LIT] == bit(true) -> blocks.find(state.name, state.properties + (LIT to bit(false))) to EXTINGUISH_CANDLE
            else -> continue
        }
        set(at, out ?: continue)
        changed += at
        sounds += BlockSound(at, sound, -1)
    }
    return Changes(emptyMap(), changed, sounds).takeIf { changed.isNotEmpty() }
}

/**
 * Starts fire at [at], [age] old: soul fire on soul sand or soil, and fire
 * anywhere else it can burn, which looks at what it's burning now and
 * then. Inside an empty nether portal frame, the portal opens instead.
 * Returns where blocks changed, none if fire can't burn there.
 */
internal fun FlatWorld.setFire(at: BlockPos, age: Int, random: Random): List<BlockPos> {
    openNetherPortal(at)?.let { return it }
    val fire = if (stateAt(at.side(Face.DOWN))?.name in SOUL_GROUND) blocks.defaultOf(SOUL_FIRE) else blocks.find(FIRE, mapOf(AGE to age))
    if (fire == null || !fireStays(at, fire)) return emptyList()
    set(at, fire)
    if (fire.name == FIRE) schedule(at, FIRE_TICKS + random.nextInt(FIRE_TICKS_MORE), BlockUpdate.FIRE)
    return listOf(at)
}

/** Whether the fire [state] can burn at [pos]: soul fire on soul sand or soil, and fire on a whole top, or beside anything that catches. */
internal fun FlatWorld.fireStays(pos: BlockPos, state: BlockState): Boolean {
    val below = stateAt(pos.side(Face.DOWN))
    if (state.name == SOUL_FIRE) return below?.name in SOUL_GROUND
    return below?.hasFullFace(Face.UP) == true || catchesAround(pos)
}

/** Whether anything beside [pos] catches fire. */
private fun FlatWorld.catchesAround(pos: BlockPos) = Face.entries.any { flameOddsAt(pos.side(it)) > 0 }

/** How readily the block at [pos] catches fire, as the game has it: not at all with water in it. */
private fun FlatWorld.flameOddsAt(pos: BlockPos) = if (isWet(pos)) 0 else traits[blockAt(pos)].flameOdds

/** How readily the block at [pos] burns away, as the game has it: not at all with water in it. */
private fun FlatWorld.burnOddsAt(pos: BlockPos) = if (isWet(pos)) 0 else traits[blockAt(pos)].burnOdds

/** How readily fire spreads to the empty space at [pos], by what's beside it that catches most readily: not at all if it isn't empty. */
private fun FlatWorld.catchOdds(pos: BlockPos): Int {
    if (stateAt(pos)?.name != AIR) return 0
    return Face.entries.maxOf { flameOddsAt(pos.side(it)) }
}

/** Whether rain falls at [pos], or beside it. */
private fun FlatWorld.rainNear(pos: BlockPos) = rainingAt(pos) || AROUND.any { rainingAt(pos.side(it)) }

/**
 * Fire at [pos], [state], looking at what it's burning, as it does every
 * second and a half or so, while the `doFireTick` rule is on: as the game
 * does, it goes out if nothing holds it up, rain may put it out, and it
 * ages. With nothing beside it that catches, it goes out once it's no
 * longer young, or at once off a whole top, and old fire burns out on what
 * doesn't catch. On netherrack or magma it burns for ever. It burns away
 * what's beside it, now and then, sometimes leaving fire in its place,
 * and spreads to empty spaces around it, beside what catches, the harder
 * the game the faster. Soul fire does none of this. Returns where blocks
 * changed.
 */
internal fun FlatWorld.tickFire(pos: BlockPos, state: BlockState, random: Random): List<BlockPos> {
    if (state.name != FIRE) return emptyList()
    schedule(pos, FIRE_TICKS + random.nextInt(FIRE_TICKS_MORE), BlockUpdate.FIRE)
    if (!rules.doFireTick) return emptyList()
    val changed = mutableListOf<BlockPos>()
    fun goOut(): List<BlockPos> {
        set(pos, blocks.stateOf(AIR))
        return changed + pos
    }
    if (!fireStays(pos, state)) return goOut()
    val below = stateAt(pos.side(Face.DOWN))
    val forever = below?.name in BURNS_FOREVER
    val age = state.number(AGE)
    if (!forever && rainNear(pos) && random.nextFloat() < 0.2f + age * 0.03f) return goOut()
    val older = minOf(OLDEST, age + random.nextInt(3) / 2)
    if (older != age) blocks.find(FIRE, state.properties + (AGE to older))?.let { set(pos, it); changed += pos }
    if (!forever) {
        if (!catchesAround(pos)) return if (below?.hasFullFace(Face.UP) != true || age > 3) goOut() else changed
        if (age == OLDEST && random.nextInt(4) == 0 && flameOddsAt(pos.side(Face.DOWN)) == 0) return goOut()
    }
    for (side in Face.entries) burn(pos.side(side), if (side.dy != 0) BURN_AGAINST_VERTICAL else BURN_AGAINST_SIDE, age, random, changed)
    for (dx in -1..1) for (dz in -1..1) for (dy in -1..4) {
        val at = BlockPos(pos.x + dx, pos.y + dy, pos.z + dz)
        if ((dx == 0 && dy == 0 && dz == 0) || at.y !in MIN_Y..MAX_Y) continue
        val odds = catchOdds(at).takeIf { it > 0 } ?: continue
        val against = SPREAD_AGAINST + if (dy > 1) (dy - 1) * SPREAD_AGAINST else 0
        val chance = (odds + 40 + difficulty.ordinal * 7) / (age + 30)
        if (chance > 0 && random.nextInt(against) <= chance && !rainNear(at)) {
            changed += setFire(at, minOf(OLDEST, age + random.nextInt(5) / 4), random)
        }
    }
    return changed
}

/**
 * Fire, [age] old, burning the block at [pos] away, now and then, by how
 * readily it burns, against [against]: young fire, out of the rain, often
 * takes its place, and otherwise it's gone. Notes where it changed in
 * [changed].
 */
private fun FlatWorld.burn(pos: BlockPos, against: Int, age: Int, random: Random, changed: MutableList<BlockPos>) {
    if (pos.y !in MIN_Y..MAX_Y || random.nextInt(against) >= burnOddsAt(pos)) return
    set(pos, blocks.stateOf(AIR))
    changed += pos
    if (random.nextInt(age + 10) < 5 && !rainingAt(pos)) changed += setFire(pos, minOf(age + random.nextInt(5) / 4, OLDEST), random)
}

/**
 * The fires lightning starts, striking the space at [pos], while the
 * `doFireTick` rule is on, at normal difficulty or hard: there, and in a
 * few spaces around it, where fire can burn. Returns where blocks changed.
 */
internal fun FlatWorld.lightningFires(pos: BlockPos, random: Random): List<BlockPos> {
    if (!rules.doFireTick || difficulty < Difficulty.NORMAL) return emptyList()
    val changed = mutableListOf<BlockPos>()
    fun fireAt(at: BlockPos) {
        if (at.y in MIN_Y..MAX_Y && stateAt(at)?.name == AIR) changed += setFire(at, 0, random)
    }
    fireAt(pos)
    repeat(LIGHTNING_FIRES) { fireAt(BlockPos(pos.x + random.nextInt(3) - 1, pos.y + random.nextInt(3) - 1, pos.z + random.nextInt(3) - 1)) }
    return changed
}

/**
 * Lava at [pos] setting fire to what's near it, as a random tick on it
 * does, while the `doFireTick` rule is on: two times in three, it climbs
 * up a block or two, stepping about at random, and starts fire in the
 * first empty space beside anything that catches, unless it's stopped by a
 * solid block first, and otherwise it tries three spots around it at its
 * own height, starting fire on any that catches.
 */
internal fun FlatWorld.lavaSetsFire(pos: BlockPos, state: BlockState, growing: Growing) {
    if (!rules.doFireTick) return
    val random = growing.random
    val climbs = random.nextInt(3)
    if (climbs > 0) {
        var at = pos
        repeat(climbs) {
            at = BlockPos(at.x + random.nextInt(3) - 1, at.y + 1, at.z + random.nextInt(3) - 1)
            if (at.y > MAX_Y) return
            val there = stateAt(at) ?: return
            when {
                there.name == AIR -> if (catchesAround(at)) {
                    growing.changed += setFire(at, 0, random)
                    return
                }
                there.solidHeights() != null -> return
            }
        }
        return
    }
    repeat(3) {
        val beside = BlockPos(pos.x + random.nextInt(3) - 1, pos.y, pos.z + random.nextInt(3) - 1)
        val above = beside.side(Face.UP)
        if (above.y <= MAX_Y && stateAt(above)?.name == AIR && flameOddsAt(beside) > 0) growing.changed += setFire(above, 0, random)
    }
}
