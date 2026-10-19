package org.koremc.protocol

import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemVersion
import org.koremc.world.Vec3
import kotlin.test.Test
import kotlin.test.assertContentEquals

class EntitiesTest {
    private val dirt = ItemDefinition("minecraft:dirt", 3, componentBased = false, ItemVersion.NONE)
    private val point = Vec3(0.5f, -62.875f, 0.5f)

    // An item as slots and item entities describe it: a 16-bit ID, count, aux value, maybe a stack ID,
    // block (none here) and ten bytes of extras, with no NBT and nothing it may be placed on or break.
    private val extras = "0a" + "00".repeat(10)

    @Test
    fun `an item entity appears with its item, where it is, how it moves, and that it falls`() {
        val packet = AddItemActor(5, ItemStack(dirt), point, Vec3(0f, 0.2f, 0f))

        val expected = "0f" + "0a" + "05" + // Unique ID 5 zigzagged, runtime ID 5
            "0300" + "0100" + "00" + "00" + "00" + extras + // Dirt, one of it, no stack ID, no block
            "0000003f00807bc20000003f" + "00000000cdcc4c3e00000000" +
            "03" + "000707" + "8080c08080808002" + "350303" + "0000803e" + "360303" + "0000803e" + // Flags, then width and height
            "00" // Not from fishing
        assertContentEquals(hex(expected), packet.encode())
    }

    @Test
    fun `from 1_26_60 an entity appearing says it rides no block`() {
        val orb = AddExperienceOrb(7, Vec3(0.5f, 1f, 0.5f), Vec3(0f, 0.2f, 0f), value = 3)
        assertContentEquals(orb.encode() + byteArrayOf(0), orb.encode(GameVersion.V1_26_60))
        val bolt = AddLightning(8, Vec3(0.5f, 1f, 0.5f))
        assertContentEquals(bolt.encode() + byteArrayOf(0), bolt.encode(GameVersion.V1_26_60))
    }

    @Test
    fun `entities go, and items fly to whoever picks them up`() {
        assertContentEquals(hex("0e0a"), RemoveActor(5).encode())
        assertContentEquals(hex("110501"), TakeItemActor(5, takerId = 1).encode())
    }

    @Test
    fun `entities move to a position, on the ground or not`() {
        assertContentEquals(hex("1205" + "01" + "0000003f00807bc20000003f" + "000000"), MoveActorAbsolute(5, point, onGround = true).encode())
    }

    @Test
    fun `an item entity's stack changing size is an entity event`() {
        assertContentEquals(hex("1b" + "05" + "45" + "06" + "00"), ActorEvent(5, ActorEvent.UPDATE_STACK_SIZE, 3).encode())
    }

    @Test
    fun `a slot of the player's items holds a stack by its ID`() {
        val expected = "32" + "00" + "02" + "00" + "00" + // The inventory window, slot 2, no container or storage item
            "0300" + "0500" + "00" + "01" + "12" + "00" + extras // Five dirt, stack 9
        assertContentEquals(hex(expected), InventorySlot(2, ItemStack(dirt, count = 5), stackId = 9).encode())
        assertContentEquals(hex("32000200000000000000000000"), InventorySlot(2, stack = null, stackId = 0).encode())
    }

    private fun hex(digits: String) = ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
