package org.koremc.world

import org.koremc.protocol.LevelSoundEvent
import org.koremc.registry.BlockState

private const val LEVER = "minecraft:lever"
private const val OPEN = "open_bit"
private const val PRESSED = "button_pressed_bit"
private const val LIT = "lit"
private const val PUT_OUT_CANDLE = "extinguish.candle"
internal const val CARDINAL = "minecraft:cardinal_direction"

/** How long a button stays pressed, in ticks: a second for stone ones, half as long again for wooden ones. */
private const val STONE_BUTTON_TICKS = 20
private const val WOODEN_BUTTON_TICKS = 30

/** Doors and trapdoors that only redstone opens, not a hand. */
private val OPENED_BY_REDSTONE = setOf("minecraft:iron_door", "minecraft:iron_trapdoor")

/**
 * What using a block did: where blocks changed, and the sound it made, by
 * the game's name for it.
 *
 * The client plays a sound as the block it names makes it, from the
 * block's sound set: a lever's clicks are its own, a wooden button's are
 * wood, and so on. With no block named, the client plays a plain click.
 */
data class Used(
    val changed: List<BlockPos>,
    val sound: String? = null,
    /** The sound's data: the network ID of the block making it, or -1 for none. */
    val soundData: Int = -1,
)

/** Daylight detectors, which a click switches between sensing day and night. */
private val DAYLIGHT_DETECTORS = listOf("minecraft:daylight_detector", "minecraft:daylight_detector_inverted")

internal const val REPEATER_DELAY = "repeater_delay"
internal const val SUBTRACTING = "output_subtract_bit"

/** Whether clicking this block uses it, rather than placing the held item against it. */
val BlockState.usable: Boolean
    get() = name == LEVER || name.endsWith("button") || name in DAYLIGHT_DETECTORS ||
        REPEATER_DELAY in properties || SUBTRACTING in properties ||
        (name !in OPENED_BY_REDSTONE && (name.endsWith("_door") || name.endsWith("trapdoor") || name.endsWith("fence_gate")))

/**
 * Blocks that open a screen, or do something else of their own, when a
 * player clicks them: containers, workstations and the like, beds, bells,
 * cake, note blocks, item frames, decorated pots, shelves and signs. Kore
 * doesn't do all of these yet, but a click on one never places the held
 * block beside it, unless the player sneaks.
 */
private val OPENS_ENDINGS = listOf(
    "chest", "shulker_box", "furnace", "smoker", "anvil", "_shelf", "standing_sign", "wall_sign", "_hanging_sign", "command_block",
    "candle_cake",
)
private val OPENS = setOf(
    "barrel", "crafting_table", "cartography_table", "smithing_table", "loom", "stonecutter_block", "grindstone",
    "enchanting_table", "brewing_stand", "beacon", "hopper", "dispenser", "dropper", "crafter", "bed", "straw_bed", "bell",
    "cake", "noteblock", "frame", "glow_frame", "decorated_pot", "dragon_egg", "structure_block", "jigsaw",
).mapTo(HashSet()) { "minecraft:$it" }

/** Whether clicking this block does something of its own, which places nothing, whether or not Kore does it yet. */
val BlockState.opensOnClick: Boolean get() = name in OPENS || OPENS_ENDINGS.any(name::endsWith)

/**
 * Uses the block at [pos] as a player facing [facing] does by clicking it:
 * a lever flips, a button presses in, and doors, trapdoors and fence gates
 * open or shut, a door's two halves together and a gate away from the
 * player. A daylight detector switches to sensing night, or back, a
 * repeater waits a tick longer, or back to one after four, and a
 * comparator switches between comparing and subtracting. A button already
 * pressed does nothing. Null for a block that isn't [usable].
 */
fun FlatWorld.use(pos: BlockPos, facing: Face): Used? {
    val state = stateAt(pos)?.takeIf { it.usable } ?: return null
    val name = state.name
    return when {
        name in DAYLIGHT_DETECTORS -> {
            val other = DAYLIGHT_DETECTORS.first { it != name }
            blocks.find(other, state.properties)?.let { set(pos, it) }
            Used(listOf(pos))
        }
        REPEATER_DELAY in state.properties -> {
            change(pos, state, REPEATER_DELAY to ((state.properties[REPEATER_DELAY] as Int) + 1) % 4)
            Used(listOf(pos))
        }
        SUBTRACTING in state.properties -> {
            change(pos, state, SUBTRACTING to bit(state.properties[SUBTRACTING] == 0.toByte()))
            Used(listOf(pos))
        }
        name == LEVER -> {
            val on = state.properties[OPEN] == 0.toByte()
            change(pos, state, OPEN to bit(on))
            Used(listOf(pos), if (on) "power.on" else "power.off", blockAt(pos))
        }
        name.endsWith("button") -> {
            if (state.properties[PRESSED] == 1.toByte()) return Used(emptyList())
            change(pos, state, PRESSED to bit(true))
            schedule(pos, if (name.endsWith("stone_button")) STONE_BUTTON_TICKS else WOODEN_BUTTON_TICKS, BlockUpdate.BUTTON)
            Used(listOf(pos), "button.click_on", blockAt(pos))
        }
        name.endsWith("fence_gate") -> {
            val opening = state.properties[OPEN] == 0.toByte()
            // A gate swings away from the player, so one opened from its far side turns round first.
            val turnsRound = opening && state.properties[CARDINAL] == facing.opposite.id
            change(pos, state, OPEN to bit(opening), CARDINAL to if (turnsRound) facing.id else state.properties.getValue(CARDINAL))
            Used(listOf(pos), if (opening) "fence_gate.open" else "fence_gate.close", blockAt(pos))
        }
        else -> swing(pos, state, opening = state.properties[OPEN] == 0.toByte())
    }
}

/**
 * Blows on the block at [pos] as a wind charge's burst reaching it does:
 * a lever flips, a button presses in, a door, trapdoor or fence gate opens
 * or shuts, but iron ones, a door's two halves together and a gate facing
 * the way it did, and a lit candle, or one on a cake, goes out. Null for a
 * block the wind leaves be.
 */
fun FlatWorld.blowOn(pos: BlockPos): Used? {
    val state = stateAt(pos) ?: return null
    val name = state.name
    return when {
        name == LEVER || name.endsWith("button") -> use(pos, Face.NORTH)
        name in OPENED_BY_REDSTONE -> null
        state.opensWithPower -> swingByPower(pos, state, opening = state.properties[OPEN] == 0.toByte())
        (name.endsWith("candle") || name.endsWith("candle_cake")) && state.properties[LIT] == 1.toByte() -> {
            change(pos, state, LIT to bit(false))
            Used(listOf(pos), PUT_OUT_CANDLE)
        }
        else -> null
    }
}

/** Doors, trapdoors and fence gates, which power opens and shuts: iron ones only by power, and the rest by hand too. */
internal val BlockState.opensWithPower: Boolean
    get() = name.endsWith("_door") || name.endsWith("trapdoor") || name.endsWith("fence_gate")

/**
 * Opens the door, trapdoor or fence gate at [pos] as power reaches it, or
 * shuts it as the power goes, with its sound, unless it's that way
 * already. A gate keeps facing the way it did. Null if nothing changed.
 */
internal fun FlatWorld.swingByPower(pos: BlockPos, state: BlockState, opening: Boolean): Used? {
    if ((state.properties[OPEN] == 1.toByte()) == opening) return null
    if (!state.name.endsWith("fence_gate")) return swing(pos, state, opening)
    change(pos, state, OPEN to bit(opening))
    return Used(listOf(pos), if (opening) "fence_gate.open" else "fence_gate.close", blockAt(pos))
}

/** Opens or shuts the door or trapdoor at [pos], a door's two halves together, with the sound of its kind. */
private fun FlatWorld.swing(pos: BlockPos, state: BlockState, opening: Boolean): Used {
    change(pos, state, OPEN to bit(opening))
    val other = state.otherHalf(pos)?.takeIf { stateAt(it)?.name == state.name }
    other?.let { half -> stateAt(half)?.let { change(half, it, OPEN to bit(opening)) } }
    val kind = if (state.name.endsWith("trapdoor")) "trapdoor" else "door"
    return Used(listOfNotNull(pos, other), "$kind.${if (opening) "open" else "close"}", blockAt(pos))
}

/** Whether this is a source of redstone power that's on: a lever switched on, or a button pressed in. */
internal val BlockState.givesPower: Boolean
    get() = (name == LEVER && properties[OPEN] == 1.toByte()) || (name.endsWith("button") && properties[PRESSED] == 1.toByte())

/** Lets the button at [pos] pop back out, with its click, which goes in [sounds], if it's still pressed in. Returns whether it was. */
internal fun FlatWorld.releaseButton(pos: BlockPos, sounds: MutableList<BlockSound>): Boolean {
    val button = stateAt(pos)?.takeIf { it.name.endsWith("button") && it.properties[PRESSED] == 1.toByte() } ?: return false
    change(pos, button, PRESSED to bit(false))
    sounds += BlockSound(pos, LevelSoundEvent.BUTTON_CLICK_OFF, blockAt(pos))
    return true
}

/** Puts [state] at [pos] with [changes] to its properties, if the game has that state. */
private fun FlatWorld.change(pos: BlockPos, state: BlockState, vararg changes: Pair<String, Any>) {
    blocks.find(state.name, state.properties + changes)?.let { set(pos, it) }
}

/** A bit property's value, which block states keep as a byte. */
internal fun bit(on: Boolean): Byte = if (on) 1 else 0
