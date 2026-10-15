package org.koremc.world

import org.koremc.inventory.PairedContainer
import org.koremc.registry.BlockState

/** The strongest redstone power, which a source gives, and dust carries a step less of each block from it. */
const val MAX_POWER = 15

internal const val REDSTONE_WIRE = "minecraft:redstone_wire"
internal const val REDSTONE_SIGNAL = "redstone_signal"
internal const val REDSTONE_TORCH = "minecraft:redstone_torch"
internal const val UNLIT_REDSTONE_TORCH = "minecraft:unlit_redstone_torch"
private const val REDSTONE_BLOCK = "minecraft:redstone_block"
internal const val UNPOWERED_REPEATER = "minecraft:unpowered_repeater"
internal const val POWERED_REPEATER = "minecraft:powered_repeater"
internal const val UNPOWERED_COMPARATOR = "minecraft:unpowered_comparator"
internal const val POWERED_COMPARATOR = "minecraft:powered_comparator"

/** A comparator's state for giving any power, which lights its torches. */
private const val OUTPUT_LIT = "output_lit_bit"

internal const val REDSTONE_LAMP = "minecraft:redstone_lamp"
internal const val LIT_REDSTONE_LAMP = "minecraft:lit_redstone_lamp"

/** How long a redstone lamp stays lit once its power goes, in ticks, as in Bedrock. */
private const val LAMP_DELAY = 6

internal const val OBSERVER = "minecraft:observer"
private const val OBSERVER_FACING = "minecraft:facing_direction"
private const val OBSERVER_POWERED = "powered_bit"

/** How long an observer waits to pulse once what it watches changes, and how long its pulse lasts, in ticks. */
private const val OBSERVER_DELAY = 2
private const val OBSERVER_PULSE = 2

/** How long a comparator takes to pass a change on, in ticks. */
private const val COMPARATOR_DELAY = 2

/** How long a redstone torch takes to answer the block it's on being powered, or not any more, in ticks. */
private const val TORCH_DELAY = 2

/**
 * A redstone torch put out this many times within [TOGGLE_MEMORY] ticks
 * burns out, and looks again [BURNOUT_TICKS] later, staying out until
 * those times are older than that.
 */
private const val BURNOUT_TOGGLES = 8
private const val TOGGLE_MEMORY = 60
private const val BURNOUT_TICKS = 160

/** The sound of a redstone torch burning out. */
private const val FIZZ = "fizz"

/**
 * How far from a block that changed redstone looks for what the change
 * may have powered, or stopped powering: two steps, from a lever to beside
 * the block it's fixed to.
 */
private const val POWER_REACH = 2

/** How much dust redstone follows from one change, at most, so that a vast spread of it can't hold the world up. */
private const val MAX_WIRE = 4096

/** How many times, at most, redstone goes round what changed in one tick, as one change leads to another. */
private const val MAX_PASSES = 16

/** Blocks besides dust that dust beside them points into: sources of power, and parts of redstone that carry it on. */
private val WIRE_JOINS = setOf(
    "lever", "redstone_torch", "unlit_redstone_torch", "redstone_block", "daylight_detector", "daylight_detector_inverted",
    "tripwire_hook", "trapped_chest", "target", "detector_rail", "lectern", "lightning_rod", "jukebox",
)
private val WIRE_JOIN_ENDINGS = listOf("button", "pressure_plate")

internal val BlockState.isWire get() = name == REDSTONE_WIRE

internal val BlockState.isRedstoneTorch get() = name == REDSTONE_TORCH || name == UNLIT_REDSTONE_TORCH

internal val BlockState.isRepeater get() = name == UNPOWERED_REPEATER || name == POWERED_REPEATER

internal val BlockState.isComparator get() = name == UNPOWERED_COMPARATOR || name == POWERED_COMPARATOR

private val BlockState.isDiode get() = isRepeater || isComparator

internal val BlockState.isObserver get() = name == OBSERVER

internal val BlockState.isLamp get() = name == REDSTONE_LAMP || name == LIT_REDSTONE_LAMP

/** The side an observer watches, out of its face. It gives power out of its back, across from it. */
private val BlockState.observerFace: Face get() = Face.entries.find { it.id == properties[OBSERVER_FACING] } ?: Face.UP

private val BlockState.observerPowered get() = properties[OBSERVER_POWERED] == 1.toByte()

/**
 * The side a repeater or comparator takes power in at, its back, which its
 * cardinal direction names. It gives power out of the side across.
 */
private val BlockState.diodeBack: Face get() = Face.entries.find { it.id == properties[CARDINAL] } ?: Face.SOUTH

/** How long a repeater waits to pass a change on, in ticks: two for each step its delay is set to. */
private val BlockState.repeaterDelay: Int get() = ((properties[REPEATER_DELAY] as? Int ?: 0) + 1) * 2

/**
 * Whether power passes through this block to what's around it: a whole
 * one, though not glass, leaves or ice, nor a redstone block or an
 * observer, which give power of their own instead, nor a piston.
 */
internal val BlockState.conductsPower get() = isFullCube && !letsLightThrough && name != REDSTONE_BLOCK && !isPiston && !isObserver

/** Whether the block at [pos] is powered: something beside it gives it any power. */
fun FlatWorld.isPowered(pos: BlockPos): Boolean = Face.entries.any { signal(pos.side(it), it.opposite) > 0 }

/**
 * The power the block at [from] gives the block beside it on its
 * [towards] side, from 0 to 15. A source gives what it gives, a jukebox
 * full power while it plays a song, a daylight detector what the sky gives
 * it, and a block power passes through gives the strongest power going
 * straight into it. Dust's power counts only [withWire]: dust takes none
 * from dust through a block, so a block that only dust powers powers no
 * other dust.
 */
internal fun FlatWorld.signal(from: BlockPos, towards: Face, withWire: Boolean = true): Int {
    val state = stateAt(from) ?: return 0
    if (state.isJukebox && playingSong(from)) return MAX_POWER
    if (state.conductsPower) return strongestInto(from, withWire)
    return when {
        state.givesPower || state.name == REDSTONE_BLOCK || state.lecternPowered || state.rodPowered -> MAX_POWER
        state.isDaylightDetector -> state.properties[REDSTONE_SIGNAL] as? Int ?: 0
        state.isPressurePlate -> state.platePower
        state.name == REDSTONE_TORCH -> if (towards == state.torchHeldFrom) 0 else MAX_POWER
        state.isDiode && towards == state.diodeBack.opposite -> diodeGives(from, state)
        state.isObserver -> observerGives(state, towards)
        state.isWire && withWire -> wireGives(from, state, towards)
        else -> 0
    }
}

/**
 * The strongest power going straight into the block at [pos], which it
 * passes on to what's beside it: from a lever or button fixed to it, a
 * pressure plate pressed on it, a lectern on it a page just turned in, or
 * a lightning rod on it struck a moment ago, a lit redstone torch under
 * it, a powered repeater, a comparator or a pulsing observer facing it,
 * or, [withWire], dust on it or pointing into it.
 */
private fun FlatWorld.strongestInto(pos: BlockPos, withWire: Boolean): Int = Face.entries.maxOf { face ->
    val from = pos.side(face)
    val towards = face.opposite
    val state = stateAt(from)
    when {
        state == null -> 0
        state.givesPower -> if (state.heldFrom == towards) MAX_POWER else 0
        state.isPressurePlate -> if (towards == Face.DOWN) state.platePower else 0
        state.lecternPowered -> if (towards == Face.DOWN) MAX_POWER else 0
        // A struck lightning rod powers what it's on, out of which it points.
        state.rodPowered -> if ((state.properties[FACING_DIRECTION] as? Int)?.let(Face::of)?.opposite == towards) MAX_POWER else 0
        state.name == REDSTONE_TORCH -> if (towards == Face.UP) MAX_POWER else 0
        state.isDiode && towards == state.diodeBack.opposite -> diodeGives(from, state)
        state.isObserver -> observerGives(state, towards)
        state.isWire && withWire -> wireGives(from, state, towards)
        else -> 0
    }
}

/** Dust's power into the block on its [towards] side: none up, all of it down into the block it's on, and sideways where it points. */
private fun FlatWorld.wireGives(pos: BlockPos, state: BlockState, towards: Face): Int {
    val power = state.properties[REDSTONE_SIGNAL] as? Int ?: 0
    return when {
        power == 0 || towards == Face.UP -> 0
        towards == Face.DOWN || towards in wirePoints(pos) -> power
        else -> 0
    }
}

/**
 * The sides dust at [pos] points to: those with dust it runs to, or a
 * source of power. Dust that points one way runs on across the other way
 * too, and dust with nothing around it points every way.
 */
internal fun FlatWorld.wirePoints(pos: BlockPos): List<Face> {
    val joined = AROUND.filter { side -> wireBeside(pos, side) != null || stateAt(pos.side(side))?.joinsWire(side) == true }
    return when (joined.size) {
        0 -> AROUND
        1 -> joined + joined[0].opposite
        else -> joined
    }
}

/**
 * The dust the dust at [pos] runs to on its [side], if any: right beside
 * it, a step up, on the block beside it, or a step down, beside that
 * block. A block power passes through cuts a step where it's on the lower
 * dust. As in Bedrock, power goes down a step as well as up it, even from
 * the top of glass.
 */
private fun FlatWorld.wireBeside(pos: BlockPos, side: Face): BlockPos? {
    val beside = pos.side(side)
    val state = stateAt(beside) ?: return null
    if (state.isWire) return beside
    val up = beside.side(Face.UP)
    if (stateAt(up)?.isWire == true && stateAt(pos.side(Face.UP))?.conductsPower != true) return up
    val down = beside.side(Face.DOWN)
    if (stateAt(down)?.isWire == true && !state.conductsPower) return down
    return null
}

/**
 * Whether dust beside this block, on its [side], points into it: a
 * source of power, or a repeater or comparator facing along the way to
 * the dust. Dust doesn't point into what power works, like doors.
 */
private fun BlockState.joinsWire(side: Face): Boolean {
    val name = name.removePrefix("minecraft:")
    return when {
        name in WIRE_JOINS || WIRE_JOIN_ENDINGS.any(name::endsWith) -> true
        name.endsWith("repeater") || name.endsWith("comparator") -> Face.entries.find { it.id == properties[CARDINAL] }?.axis == side.axis
        // An observer's back, which it gives power out of, faces the dust.
        name == "observer" -> observerFace == side
        else -> false
    }
}

/** The power a repeater or comparator at [pos] gives out of its front: a powered repeater all of it, and a comparator what it works out. */
private fun FlatWorld.diodeGives(pos: BlockPos, state: BlockState): Int = when {
    state.name == POWERED_REPEATER -> MAX_POWER
    state.isComparator -> comparatorPower[pos] ?: 0
    else -> 0
}

/** The power an observer gives the block on its [towards] side: all of it out of its back, while it pulses. */
private fun observerGives(state: BlockState, towards: Face) = if (state.observerPowered && towards == state.observerFace.opposite) MAX_POWER else 0

/** The side of a redstone torch the block it's on is: below it, unless it's on a wall. */
private val BlockState.torchHeldFrom: Face get() = heldFrom ?: Face.DOWN

/**
 * Brings what redstone powers in line with the blocks that changed since
 * it last looked, and those around them, then with what that changed, and
 * so on. Dust takes the power that reaches it first. A redstone torch
 * looks again two ticks later, once the block it's on has started or
 * stopped being powered, a repeater its delay later, once its back has,
 * unless it's locked, and a comparator two ticks after what it works out
 * from what reaches it changes; comparators look at what they read every
 * tick, since what's in a container changes by itself. An observer
 * pulses two ticks after the block it watches changes in any way, for two
 * ticks, unless it's pulsing already. A dispenser or dropper that power
 * has just reached fires a moment later, a note block plays at once, and a
 * hopper is locked for as long as it's powered. A door, trapdoor or fence
 * gate opens as power reaches it, and shuts as the power goes, a door if
 * either half is powered. Between times, a player can open or shut a
 * wooden one by hand, and it stays so until the power changes. Returns
 * where blocks changed, the sounds they made, and the notes played: none
 * with redstone switched off.
 */
fun FlatWorld.updatePower(): Changes {
    if (!features.redstone) return Changes(emptyMap(), emptyList())
    val changed = LinkedHashSet<BlockPos>()
    val sounds = mutableListOf<BlockSound>()
    val notes = mutableListOf<NotePlayed>()
    for (pos in comparators.toList()) answerPower(pos, changed, sounds, notes)
    var passes = 0
    while (powerChanges.isNotEmpty() && passes++ < MAX_PASSES) {
        val changedNow = powerChanges.toList()
        powerChanges.clear()
        for (pos in changedNow) observedChange(pos)
        val near = changedNow.flatMap { it.around(POWER_REACH) }.filterTo(LinkedHashSet()) { it.y in MIN_Y..MAX_Y }
        changed += updateWire(near)
        for (pos in near) answerPower(pos, changed, sounds, notes)
    }
    return Changes(emptyMap(), changed.toList(), sounds, notes = notes)
}

/**
 * Works out the power of the dust at any of [around], and all the dust it
 * runs to, as redstone carries it: each takes the strongest power given it
 * by anything but dust, or one less than the dust it runs to has, if
 * that's more. Returns the dust whose power changed.
 */
private fun FlatWorld.updateWire(around: Collection<BlockPos>): List<BlockPos> {
    val wire = LinkedHashSet<BlockPos>()
    val queue = ArrayDeque(around.filter { stateAt(it)?.isWire == true })
    while (queue.isNotEmpty() && wire.size < MAX_WIRE) {
        val pos = queue.removeFirst()
        if (wire.add(pos)) AROUND.mapNotNullTo(queue) { wireBeside(pos, it) }
    }
    val power = wire.associateWithTo(HashMap()) { pos -> Face.entries.maxOf { signal(pos.side(it), it.opposite, withWire = false) } }
    // From the strongest down, each dust lends the dust it runs to one less than it has.
    val byPower = Array(MAX_POWER + 1) { ArrayDeque<BlockPos>() }
    for ((pos, level) in power) byPower[level] += pos
    for (level in MAX_POWER downTo 2) {
        while (byPower[level].isNotEmpty()) {
            val pos = byPower[level].removeFirst()
            if (power[pos] != level) continue
            for (next in AROUND.mapNotNull { wireBeside(pos, it) }) {
                val now = power[next] ?: continue
                if (now >= level - 1) continue
                power[next] = level - 1
                byPower[level - 1] += next
            }
        }
    }
    val changed = mutableListOf<BlockPos>()
    for ((pos, level) in power) {
        val state = stateAt(pos) ?: continue
        if (state.properties[REDSTONE_SIGNAL] == level) continue
        blocks.find(state.name, state.properties + (REDSTONE_SIGNAL to level))?.let { set(pos, it); changed += pos }
    }
    return changed
}

/** Sets the observers watching the block at [pos], which has changed, to pulse, those not already. */
private fun FlatWorld.observedChange(pos: BlockPos) {
    for (face in Face.entries) {
        val watcher = pos.side(face)
        val state = stateAt(watcher)?.takeIf { it.isObserver && it.observerFace == face.opposite } ?: continue
        observe(watcher, state)
    }
}

/** Sets the observer [state] at [pos] to pulse in a moment, unless it's pulsing or about to. */
internal fun FlatWorld.observe(pos: BlockPos, state: BlockState) {
    if (state.observerPowered || !redstoneDue.add(pos)) return
    schedule(pos, OBSERVER_DELAY, BlockUpdate.OBSERVER)
}

/**
 * Puts down the observer [state] a piston moved at [pos], off, even if it
 * was pulsing as it went, since that pulse was where it came from, and
 * sets it to pulse, as in Bedrock.
 */
internal fun FlatWorld.landObserver(pos: BlockPos, state: BlockState) {
    val off = blocks.find(state.name, state.properties + (OBSERVER_POWERED to bit(false))) ?: state
    set(pos, off)
    observe(pos, off)
}

/**
 * Puts out the redstone lamp at [pos], [state], a moment after its power
 * went, unless power's back. Returns whether it went out.
 */
internal fun FlatWorld.tickLamp(pos: BlockPos, state: BlockState): Boolean {
    if (!state.isLamp) return false
    redstoneDue -= pos
    if (state.name != LIT_REDSTONE_LAMP || isPowered(pos)) return false
    blocks.find(REDSTONE_LAMP, state.properties)?.let { set(pos, it) } ?: return false
    return true
}

/**
 * Turns the observer at [pos] on, for its pulse, or off again once the
 * pulse is over. Returns whether it changed.
 */
internal fun FlatWorld.tickObserver(pos: BlockPos, state: BlockState): Boolean {
    if (!state.isObserver) return false
    redstoneDue -= pos
    val powered = state.observerPowered
    blocks.find(state.name, state.properties + (OBSERVER_POWERED to bit(!powered)))?.let { set(pos, it) } ?: return false
    if (!powered && redstoneDue.add(pos)) schedule(pos, OBSERVER_PULSE, BlockUpdate.OBSERVER)
    return true
}

/** Brings the block at [pos] in line with the power around it, if it answers power. */
private fun FlatWorld.answerPower(pos: BlockPos, changed: MutableSet<BlockPos>, sounds: MutableList<BlockSound>, notes: MutableList<NotePlayed>) {
    val state = stateAt(pos) ?: return
    when {
        state.isNoteBlock -> powerNote(pos, isPowered(pos))?.let(notes::add)
        state.opensWithPower -> {
            // A door goes by its lower half, which keeps both halves' power.
            val lower = if (state.properties[UPPER_BLOCK] == 1.toByte()) pos.side(Face.DOWN) else pos
            val powered = isPowered(lower) || (UPPER_BLOCK in state.properties && isPowered(lower.side(Face.UP)))
            if (powered == lower in poweredOpen) return
            if (powered) poweredOpen += lower else poweredOpen -= lower
            val swung = stateAt(lower)?.let { swingByPower(lower, it, powered) } ?: return
            changed += swung.changed
            swung.sound?.let { sounds += BlockSound(lower, it, swung.soundData) }
        }
        state.isRedstoneTorch -> {
            if (torchLit(pos, state) == (state.name == REDSTONE_TORCH) || !redstoneDue.add(pos)) return
            schedule(pos, TORCH_DELAY, BlockUpdate.TORCH)
        }
        state.isRepeater -> {
            val powered = state.name == POWERED_REPEATER
            if (repeaterLocked(pos, state) || powered == diodeInput(pos, state) > 0 || !redstoneDue.add(pos)) return
            schedule(pos, state.repeaterDelay, BlockUpdate.REPEATER)
        }
        state.isPiston -> {
            val arm = pistonArm(pos, state)
            val powered = pistonPowered(pos, state)
            if (arm.moving || powered == arm.extending || !redstoneDue.add(pos)) return
            arm.pending = powered
            schedule(pos, PISTON_DELAY, BlockUpdate.PISTON)
        }
        state.isComparator -> {
            val works = comparatorWorks(pos, state)
            val settled = works == (comparatorPower[pos] ?: 0) && (works > 0) == (state.name == POWERED_COMPARATOR)
            if (settled || !redstoneDue.add(pos)) return
            schedule(pos, COMPARATOR_DELAY, BlockUpdate.COMPARATOR)
        }
        state.isLamp -> {
            // A lamp lights as soon as power reaches it, but goes out a moment after the power goes.
            val powered = isPowered(pos)
            if (powered == (state.name == LIT_REDSTONE_LAMP)) return
            if (!powered) {
                if (redstoneDue.add(pos)) schedule(pos, LAMP_DELAY, BlockUpdate.LAMP)
                return
            }
            blocks.find(LIT_REDSTONE_LAMP, state.properties)?.let { set(pos, it); changed += pos }
        }
        state.name == HOPPER || state.dispenserKind != null || state.isCrafter -> {
            val property = if (state.name == HOPPER) HOPPER_LOCKED else TRIGGERED
            val powered = isPowered(pos)
            if ((state.properties[property] == 1.toByte()) == powered) return
            blocks.find(state.name, state.properties + (property to bit(powered)))?.let { set(pos, it); changed += pos }
            if (powered) dispenserAt(pos)?.fireIn = DISPENSE_DELAY
            if (powered) crafterAt(pos)?.craftIn = CRAFT_DELAY
        }
    }
}

/** Whether the redstone torch at [pos] is to be lit: while nothing powers the block it's on. */
private fun FlatWorld.torchLit(pos: BlockPos, state: BlockState): Boolean {
    val on = state.torchHeldFrom
    return signal(pos.side(on), on.opposite) == 0
}

/**
 * Lights the redstone torch at [pos], or puts it out, if the block it's on
 * has stopped or started being powered, as it looks again. A torch put out
 * eight times in three seconds burns out, with a fizz, and looks again in
 * eight seconds, when it lights if it's free to. Until then, it stays out.
 * Returns whether it changed.
 */
internal fun FlatWorld.tickTorch(pos: BlockPos, state: BlockState, sounds: MutableList<BlockSound>): Boolean {
    if (!state.isRedstoneTorch) return false
    redstoneDue -= pos
    val toggles = torchToggles.getOrPut(pos) { mutableListOf() }.apply { removeAll { blockTicks - it > TOGGLE_MEMORY } }
    val lit = state.name == REDSTONE_TORCH
    val lighting = torchLit(pos, state)
    if (lit == lighting || (lighting && toggles.size >= BURNOUT_TOGGLES)) return false
    blocks.find(if (lighting) REDSTONE_TORCH else UNLIT_REDSTONE_TORCH, state.properties)?.let { set(pos, it) } ?: return false
    if (!lighting) {
        toggles += blockTicks
        if (toggles.size >= BURNOUT_TOGGLES) {
            sounds += BlockSound(pos, FIZZ, -1)
            redstoneDue += pos
            schedule(pos, BURNOUT_TICKS, BlockUpdate.TORCH)
        }
    }
    return true
}

/**
 * The power going into the back of the repeater or comparator at [pos]:
 * what the block behind gives it, or what dust there has, whichever way
 * the dust points.
 */
private fun FlatWorld.diodeInput(pos: BlockPos, state: BlockState): Int {
    val back = state.diodeBack
    val behind = pos.side(back)
    val wire = stateAt(behind)?.takeIf { it.isWire }?.properties?.get(REDSTONE_SIGNAL) as? Int ?: 0
    return maxOf(signal(behind, back.opposite), wire)
}

/**
 * The strongest power the repeaters and comparators beside the one at
 * [pos], at its sides, give it, facing into them, and for a comparator,
 * dust and redstone blocks there too.
 */
private fun FlatWorld.diodeSides(pos: BlockPos, state: BlockState): Int {
    val back = state.diodeBack
    return listOf(back.clockwise, back.counterClockwise).maxOf { side ->
        val at = pos.side(side)
        val beside = stateAt(at)
        when {
            beside == null -> 0
            beside.isDiode && beside.diodeBack == side -> diodeGives(at, beside)
            !state.isComparator -> 0
            beside.isWire -> beside.properties[REDSTONE_SIGNAL] as? Int ?: 0
            beside.name == REDSTONE_BLOCK -> MAX_POWER
            else -> 0
        }
    }
}

/** Whether the repeater at [pos] is locked, by a repeater or comparator powering its side, so that it keeps as it is. */
private fun FlatWorld.repeaterLocked(pos: BlockPos, state: BlockState): Boolean = diodeSides(pos, state) > 0

/**
 * Passes on the change at the back of the repeater at [pos], its delay
 * after it came, unless it's locked: it powers what's in front of it
 * while its back is powered. Once on, it stays on for its delay at least,
 * however short the power behind it was. Returns whether it changed.
 */
internal fun FlatWorld.tickRepeater(pos: BlockPos, state: BlockState): Boolean {
    if (!state.isRepeater) return false
    redstoneDue -= pos
    if (repeaterLocked(pos, state)) return false
    val input = diodeInput(pos, state) > 0
    if (state.name == POWERED_REPEATER) {
        if (input) return false
        return blocks.find(UNPOWERED_REPEATER, state.properties)?.also { set(pos, it) } != null
    }
    blocks.find(POWERED_REPEATER, state.properties)?.let { set(pos, it) } ?: return false
    if (!input && redstoneDue.add(pos)) schedule(pos, state.repeaterDelay, BlockUpdate.REPEATER)
    return true
}

/**
 * What the comparator at [pos] gives out of its front, from what reaches
 * it. Comparing, it passes on the power at its back, unless that at its
 * sides is stronger; subtracting, it takes the power at its sides from
 * that at its back.
 */
private fun FlatWorld.comparatorWorks(pos: BlockPos, state: BlockState): Int {
    val back = comparatorBack(pos, state)
    if (back == 0) return 0
    val sides = diodeSides(pos, state)
    return when {
        sides > back -> 0
        state.properties[SUBTRACTING] == 1.toByte() -> back - sides
        else -> back
    }
}

/**
 * The power at the back of the comparator at [pos]: how full the block
 * behind it is, if it's one a comparator reads, or else the power going
 * in as into a repeater's. Short of full power, it reads through a block
 * power passes through, what's behind that.
 */
private fun FlatWorld.comparatorBack(pos: BlockPos, state: BlockState): Int {
    val back = state.diodeBack
    val behind = pos.side(back)
    fullness(behind)?.let { return it }
    val input = diodeInput(pos, state)
    if (input >= MAX_POWER || stateAt(behind)?.conductsPower != true) return input
    return fullness(behind.side(back)) ?: input
}

/**
 * How full the block at [pos] is, from 0 to 15, as a comparator reads it,
 * or null for a block it reads nothing of. A container is as full as its
 * slots are, each of the most it holds of a stack, a double chest as one:
 * 1 for anything in it at all, up to 15 with every slot full. A cake reads
 * more for each slice left, and as whole with a candle on it, a composter
 * for each level, a cauldron for each third, a respawn anchor for each
 * charge, and a beehive or bee nest for its honey. An end portal frame
 * reads full with an eye in it, an item frame by which of its eight ways
 * its item is turned, a lectern by how far through its book the reader
 * is, a crafter by how many of its slots are full or switched off, and a
 * jukebox by its disc.
 */
internal fun FlatWorld.fullness(pos: BlockPos): Int? {
    val state = stateAt(pos) ?: return null
    val properties = state.properties
    if (state.hasCandle) return WHOLE_CAKE_FULLNESS
    when (state.name) {
        CAULDRON -> return cauldronFullness(state)
        JUKEBOX -> return discPower(pos)
        "minecraft:cake" -> return (7 - (properties["bite_counter"] as? Int ?: 0)) * 2
        "minecraft:composter" -> return properties["composter_fill_level"] as? Int ?: 0
        "minecraft:respawn_anchor" -> return (properties["respawn_anchor_charge"] as? Int ?: 0) * MAX_POWER / 4
        "minecraft:beehive", "minecraft:bee_nest" -> return properties["honey_level"] as? Int ?: 0
        "minecraft:end_portal_frame" -> return if (properties["end_portal_eye_bit"] == 1.toByte()) MAX_POWER else 0
        "minecraft:frame", "minecraft:glow_frame" -> return frameTurns(pos)?.plus(1) ?: 0
        LECTERN -> return lecternPower(pos)
        CRAFTER -> return crafterAt(pos)?.taken ?: 0
    }
    val slots = when (state.storage) {
        Storage.CHEST -> chestHalves(pos).let { if (it.size == 2) PairedContainer(containerAt(it[0]), containerAt(it[1])) else containerAt(pos) }
        Storage.BARREL, Storage.SHULKER_BOX -> containerAt(pos)
        Storage.ENDER_CHEST -> return null
        null -> (furnaceAt(pos) ?: brewingStandAt(pos) ?: hopperAt(pos) ?: dispenserAt(pos))?.slots ?: return null
    }
    var full = 0f
    for (slot in slots.contents()) slot?.let { full += it.stack.count.toFloat() / it.stack.item.maxStackSize }
    full /= slots.size
    return if (full == 0f) 0 else 1 + (full * 14).toInt()
}

/**
 * Sets what the comparator at [pos] gives out of its front to what it
 * works out now, two ticks after what reaches it changed, its torches lit
 * while that's anything. Returns whether its state changed.
 */
internal fun FlatWorld.tickComparator(pos: BlockPos, state: BlockState): Boolean {
    if (!state.isComparator) return false
    redstoneDue -= pos
    val works = comparatorWorks(pos, state)
    if (works != (comparatorPower[pos] ?: 0)) {
        if (works == 0) comparatorPower -= pos else comparatorPower[pos] = works
        powerChanges += pos
    }
    val powered = works > 0
    if (powered == (state.name == POWERED_COMPARATOR)) return false
    val name = if (powered) POWERED_COMPARATOR else UNPOWERED_COMPARATOR
    return blocks.find(name, state.properties + (OUTPUT_LIT to bit(powered)))?.also { set(pos, it) } != null
}

/** The blocks no more than [reach] steps from this one, along the axes, this one among them. */
private fun BlockPos.around(reach: Int): List<BlockPos> {
    val around = mutableListOf<BlockPos>()
    for (dx in -reach..reach) for (dy in -reach..reach) for (dz in -reach..reach) {
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= reach) around += BlockPos(x + dx, y + dy, z + dz)
    }
    return around
}
