package org.koremc.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ItemStackRequestTest {
    @Test
    fun `a creative pick is a creative craft, then a take from the created output`() {
        val packet = request(id = -3) {
            action(12, legacy = 14) { writeVarUInt(7).writeByte(1) } // Creative item 7, once
            action(0, legacy = 0) { writeByte(64); slot(60, 50, stackId = -3); slot(59, 0, stackId = 0) }
        }

        val expected = StackRequest(
            -3,
            listOf(
                StackAction.CraftCreative(creativeId = 7, times = 1),
                StackAction.Transfer(64, SlotRef(ContainerName.CREATED_OUTPUT, 50, -3), SlotRef(ContainerName.CURSOR, 0, 0)),
            ),
        )
        assertEquals(ItemStackRequest(listOf(expected)), decodeClientPacket(packet))
    }

    @Test
    fun `every kind of action is read, even those Kore won't carry out`() {
        val packet = request(id = -5) {
            action(2, legacy = 2) { slot(28, 0, 1); slot(29, 9, 2) } // Swap
            action(3, legacy = 3) { writeByte(1); slot(28, 1, 3); writeBoolean(false) } // Drop
            action(4, legacy = 4) { writeByte(2); slot(59, 0, 4) } // Destroy
            action(5, legacy = 5) { writeByte(1); slot(13, 28, 5) } // Consume
            action(8, legacy = 10) { writeVarInt(1).writeVarInt(10) } // Pay a beacon for Speed and Regeneration
            action(10, legacy = 12) { writeVarUInt(9).writeByte(1) } // Craft a recipe
            action(17, legacy = 19) { // The deprecated results: one named item
                writeVarUInt(1).writeVarUInt(1).writeByte(1).writeString("minecraft:stick").writeVarInt(0)
                writeShortLE(4).writeVarUInt(0).writeVarUInt(2).writeShortLE(0)
                writeByte(1)
            }
        }

        val actions = (decodeClientPacket(packet) as ItemStackRequest).requests.single().actions
        assertEquals(
            listOf(
                StackAction.Swap(SlotRef(28, 0, 1), SlotRef(29, 9, 2)),
                StackAction.Drop(1, SlotRef(28, 1, 3)),
                StackAction.Destroy(2, SlotRef(59, 0, 4)),
                StackAction.Consume(1, SlotRef(13, 28, 5)),
                StackAction.BeaconPayment(primary = 1, secondary = 10),
                StackAction.Craft(recipe = 9, times = 1),
                StackAction.Informational(18),
            ),
            actions,
        )
    }

    @Test
    fun `from 1_26_60 a reserved action comes before the last two, which are numbered one higher`() {
        val packet = request(id = -5) {
            action(16, legacy = 18) { writeString("future").writeByte(1) } // Reserved
            action(18, legacy = 20) { writeVarUInt(0).writeByte(1) } // The deprecated results: nothing
        }

        val actions = (decodeClientPacket(packet, GameVersion.V1_26_60) as ItemStackRequest).requests.single().actions
        assertEquals(listOf(StackAction.Unsupported(16), StackAction.Informational(18)), actions)
    }

    @Test
    fun `a dynamic container ID is read past`() {
        val packet = request(id = -1) {
            action(0, legacy = 0) {
                writeByte(1)
                writeByte(28).writeBoolean(true).writeIntLE(99).writeByte(4).writeIntLE(8) // A bundle's dynamic ID
                slot(59, 0, 0)
            }
        }

        assertEquals(StackAction.Transfer(1, SlotRef(28, 4, 8), SlotRef(59, 0, 0)), (decodeClientPacket(packet) as ItemStackRequest).requests.single().actions.single())
    }

    @Test
    fun `auto-crafting lists its ingredients, which are read past`() {
        val packet = request(id = -1) {
            action(11, legacy = 13) {
                writeVarUInt(3).writeByte(1) // Recipe 3, once
                writeVarUInt(2)
                writeVarUInt(1).writeByte(1).writeString("minecraft:oak_planks").writeVarInt(32767).writeShortLE(2) // Two planks
                writeVarUInt(3).writeByte(3).writeString("minecraft:logs").writeShortLE(1) // A log
            }
            action(5, legacy = 5) { writeByte(2); slot(12, 9, 5) }
        }

        val expected = listOf(StackAction.Craft(recipe = 3, times = 1, auto = true), StackAction.Consume(2, SlotRef(12, 9, 5)))
        assertEquals(ItemStackRequest(listOf(StackRequest(-1, expected))), decodeClientPacket(packet))
    }

    @Test
    fun `responses list each changed slot by container, or nothing when rejected`() {
        val responses = ItemStackResponse(
            listOf(
                StackResponse(-1, accepted = true, listOf(ContainerChange(ContainerName.CURSOR, listOf(SlotChange(0, 64, 5))))),
                StackResponse(-3, accepted = false),
            ),
        )

        val accepted = bytes(0x00, 0x01, 0x01, 0x01) + // Accepted, request -1, one container
            bytes(59, 0x00, 0x01) + // The cursor, no dynamic ID, one slot
            bytes(0x00, 0x00, 64, 0x01, 0x0A, 0x00, 0x00, 0x00) // Slot 0 twice, 64 of stack 5, no name, nothing filtered, no durability change
        assertContentEquals(bytes(0x94, 0x01, 0x02) + accepted + bytes(0x01, 0x05, 0x00), responses.encode())
    }

    @Test
    fun `a slot in a response carries its stack's name and wear`() {
        val response = ItemStackResponse(listOf(StackResponse(-1, accepted = true, listOf(ContainerChange(ContainerName.HOTBAR, listOf(SlotChange(4, 1, 5, "Edge", damage = 120)))))))

        val slot = bytes(0x04, 0x04, 0x01, 0x01, 0x0A, 0x04) + "Edge".encodeToByteArray() + bytes(0x00, 0xF0, 0x01) // Named, nothing filtered, 120 uses gone
        assertContentEquals(bytes(0x94, 0x01, 0x01, 0x00, 0x01, 0x01, 0x01, ContainerName.HOTBAR, 0x00, 0x01) + slot, response.encode())
    }

    private fun request(id: Int, actions: RequestBuilder.() -> Unit): ByteArray {
        val builder = RequestBuilder().apply(actions)
        val buffer = Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(id).writeVarUInt(builder.count).writeBytes(builder.bytes)
        return ByteBufUtil.getBytes(buffer.writeVarUInt(0).writeIntLE(0))
    }

    private class RequestBuilder {
        val bytes: ByteBuf = Unpooled.buffer()
        var count = 0

        fun action(type: Int, legacy: Int, payload: ByteBuf.() -> Unit) {
            count++
            bytes.writeVarUInt(type).writeByte(legacy)
            bytes.payload()
        }
    }

    private fun ByteBuf.slot(container: Int, slot: Int, stackId: Int) {
        writeByte(container).writeBoolean(false).writeByte(slot).writeIntLE(stackId)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
