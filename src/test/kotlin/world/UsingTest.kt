package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.AIR
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsingTest {
    private val air = state(AIR)
    private val stone = state("minecraft:stone")

    private val palette = BlockPalette(
        listOf(air, stone) +
            listOf("down_east_west", "down_north_south", "up_east_west", "up_north_south", "north", "south", "west", "east").flatMap { direction ->
                (0..1).map { lever(direction, it) }
            } +
            listOf("minecraft:stone_button", "minecraft:wooden_button").flatMap { name ->
                (0..5).flatMap { facing -> (0..1).map { button(name, facing, it) } }
            } +
            listOf("minecraft:wooden_door", "minecraft:iron_door").flatMap { name ->
                listOf("south", "west", "north", "east").flatMap { direction ->
                    (0..1).flatMap { hinge -> (0..1).flatMap { open -> (0..1).map { upper -> door(name, direction, hinge, open, upper) } } }
                }
            } +
            (0..3).flatMap { direction -> (0..1).flatMap { open -> (0..1).map { upsideDown -> trapdoor(direction, open, upsideDown) } } } +
            listOf("south", "west", "north", "east").flatMap { direction -> (0..1).map { gate(direction, it) } } +
            (0..1).map { candle(it) },
    )

    // One layer of stone at the bottom of the world, with things to use standing on it.
    private val world = FlatWorld(palette, listOf(stone))
    private val spot = BlockPos(2, MIN_Y + 1, 2)

    @Test
    fun `levers flip on and off`() {
        world[spot] = lever("up_east_west", 0)

        // Naming the lever in the sound gives its own click.
        assertEquals(Used(listOf(spot), "power.on", lever("up_east_west", 1).networkId), world.use(spot, Face.NORTH))
        assertEquals(lever("up_east_west", 1), world.stateAt(spot))
        assertEquals(Used(listOf(spot), "power.off", lever("up_east_west", 0).networkId), world.use(spot, Face.NORTH))
        assertEquals(lever("up_east_west", 0), world.stateAt(spot))
    }

    @Test
    fun `buttons press in, stay in, and pop back out later`() {
        world[spot] = button("minecraft:wooden_button", Face.UP.ordinal, 0)

        assertEquals(Used(listOf(spot), "button.click_on", button("minecraft:wooden_button", Face.UP.ordinal, 1).networkId), world.use(spot, Face.NORTH))
        assertEquals(Used(emptyList()), world.use(spot, Face.NORTH)) // Already in
        repeat(29) { world.tickBlocks() }
        assertEquals(button("minecraft:wooden_button", Face.UP.ordinal, 1), world.stateAt(spot))
        val released = world.tickBlocks()
        assertEquals(button("minecraft:wooden_button", Face.UP.ordinal, 0), world.stateAt(spot))
        assertEquals(listOf(spot), released.reshaped)
        assertEquals(listOf(BlockSound(spot, "button.click_off", button("minecraft:wooden_button", Face.UP.ordinal, 0).networkId)), released.sounds)
        assertFalse(world.releaseButton(spot, mutableListOf()))

        // Stone buttons are quicker.
        world[spot] = button("minecraft:stone_button", Face.UP.ordinal, 0)
        world.use(spot, Face.NORTH)
        repeat(20) { world.tickBlocks() }
        assertEquals(button("minecraft:stone_button", Face.UP.ordinal, 0), world.stateAt(spot))
    }

    @Test
    fun `doors open and shut both halves, unless only redstone opens them`() {
        world.place(palette.defaultOf("minecraft:wooden_door")!!, spot.side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.NORTH))
        val top = spot.side(Face.UP)

        val opened = world.use(top, Face.SOUTH)
        assertEquals(Used(listOf(top, spot), "door.open", door("minecraft:wooden_door", "east", 0, 1, 1).networkId), opened)
        assertEquals(door("minecraft:wooden_door", "east", 0, 1, 0), world.stateAt(spot))
        assertEquals("door.close", world.use(spot, Face.SOUTH)?.sound)
        assertEquals(door("minecraft:wooden_door", "east", 0, 0, 1), world.stateAt(top))

        world[spot] = door("minecraft:iron_door", "east", 0, 0, 0)
        assertNull(world.use(spot, Face.SOUTH))
    }

    @Test
    fun `doors face a quarter turn clockwise from the player, hinge against a wall, and pair up`() {
        // Facing north, a wall to the right (east) puts the hinge on the right.
        world[spot.side(Face.EAST)] = stone
        world.place(palette.defaultOf("minecraft:wooden_door")!!, spot.side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.NORTH))
        assertEquals(door("minecraft:wooden_door", "east", 1, 0, 0), world.stateAt(spot))

        // A door placed to the right of another, its left, pairs with it.
        val next = BlockPos(6, MIN_Y + 1, 2)
        world.place(palette.defaultOf("minecraft:wooden_door")!!, next.side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.NORTH))
        world.place(palette.defaultOf("minecraft:wooden_door")!!, next.side(Face.EAST).side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.NORTH))
        assertEquals(0.toByte(), world.stateAt(next)?.properties?.get("door_hinge_bit"))
        assertEquals(1.toByte(), world.stateAt(next.side(Face.EAST))?.properties?.get("door_hinge_bit"))
    }

    @Test
    fun `a door falls a tick after the block it stands on, both halves`() {
        world.place(palette.defaultOf("minecraft:wooden_door")!!, spot.side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.NORTH))

        assertEquals(setOf(spot.side(Face.DOWN)), world.breakBlock(spot.side(Face.DOWN)).broken.keys)
        assertEquals(setOf(spot, spot.side(Face.UP)), world.tickBlocks().broken.keys)
        assertEquals(air, world.stateAt(spot.side(Face.UP)))
    }

    @Test
    fun `trapdoors hinge away from the player, and open and shut`() {
        world.place(trapdoor(0, 0, 0), spot.side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.EAST))
        assertEquals(trapdoor(1, 0, 0), world.stateAt(spot))

        assertEquals(Used(listOf(spot), "trapdoor.open", trapdoor(1, 1, 0).networkId), world.use(spot, Face.EAST))
        assertEquals("trapdoor.close", world.use(spot, Face.EAST)?.sound)
    }

    @Test
    fun `fence gates face the way the player does, and open away from them`() {
        world.place(gate("south", 0), spot.side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.NORTH))
        assertEquals(gate("north", 0), world.stateAt(spot))

        // Opened from its far side, facing south, the gate turns round first.
        assertEquals(Used(listOf(spot), "fence_gate.open", gate("south", 1).networkId), world.use(spot, Face.SOUTH))
        assertEquals("fence_gate.close", world.use(spot, Face.NORTH)?.sound)
        assertEquals(gate("south", 0), world.stateAt(spot))
    }

    @Test
    fun `a wind charge's burst flips levers, presses buttons, swings doors, trapdoors and gates but iron ones, and puts out candles`() {
        world[spot] = lever("up_east_west", 0)
        assertEquals(Used(listOf(spot), "power.on", lever("up_east_west", 1).networkId), world.blowOn(spot))

        world[spot] = button("minecraft:stone_button", Face.UP.ordinal, 0)
        assertEquals(button("minecraft:stone_button", Face.UP.ordinal, 1), world.blowOn(spot)?.let { world.stateAt(spot) })

        world[spot] = air
        world.place(palette.defaultOf("minecraft:wooden_door")!!, spot.side(Face.DOWN), Face.UP, clickY = 0f, Placer(Face.NORTH))
        val top = spot.side(Face.UP)
        assertEquals(Used(listOf(spot, top), "door.open", door("minecraft:wooden_door", "east", 0, 1, 0).networkId), world.blowOn(spot))
        assertEquals(door("minecraft:wooden_door", "east", 0, 1, 1), world.stateAt(top))

        world[top] = air
        world[spot] = door("minecraft:iron_door", "east", 0, 0, 0)
        assertNull(world.blowOn(spot))

        // A gate keeps facing the way it did.
        world[spot] = gate("north", 0)
        assertEquals("fence_gate.open", world.blowOn(spot)?.sound)
        assertEquals(gate("north", 1), world.stateAt(spot))

        world[spot] = trapdoor(0, 0, 0)
        assertEquals("trapdoor.open", world.blowOn(spot)?.sound)

        world[spot] = candle(1)
        assertEquals(Used(listOf(spot), "extinguish.candle"), world.blowOn(spot))
        assertEquals(candle(0), world.stateAt(spot))
        assertNull(world.blowOn(spot), "it's out")

        world[spot] = stone
        assertNull(world.blowOn(spot))
    }

    @Test
    fun `other blocks aren't used`() {
        assertNull(world.use(spot.side(Face.DOWN), Face.NORTH))
        assertFalse(stone.usable)
        assertTrue(lever("north", 0).usable)
    }

    private fun lever(direction: String, on: Int) = state("minecraft:lever", "lever_direction" to direction, "open_bit" to on.toByte())

    private fun button(name: String, facing: Int, pressed: Int) = state(name, "button_pressed_bit" to pressed.toByte(), "facing_direction" to facing)

    private fun door(name: String, direction: String, hinge: Int, open: Int, upper: Int) = state(
        name,
        "minecraft:cardinal_direction" to direction,
        "door_hinge_bit" to hinge.toByte(),
        "open_bit" to open.toByte(),
        "upper_block_bit" to upper.toByte(),
    )

    private fun trapdoor(direction: Int, open: Int, upsideDown: Int) =
        state("minecraft:trapdoor", "direction" to direction, "open_bit" to open.toByte(), "upside_down_bit" to upsideDown.toByte())

    private fun gate(direction: String, open: Int) =
        state("minecraft:fence_gate", "minecraft:cardinal_direction" to direction, "in_wall_bit" to 0.toByte(), "open_bit" to open.toByte())

    private fun candle(lit: Int) = state("minecraft:candle", "candles" to 0, "lit" to lit.toByte())

    private fun state(name: String, vararg properties: Pair<String, Any>) = BlockState(name, NbtMap.fromMap(mapOf(*properties)), 18168865)
}
