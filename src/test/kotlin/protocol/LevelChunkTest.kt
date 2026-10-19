package org.koremc.protocol

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.koremc.world.BlockPos
import org.koremc.world.Chunk
import org.koremc.world.ChunkPos
import org.koremc.world.PalettedStorage
import org.koremc.world.SUB_CHUNK_COUNT
import org.koremc.world.SUB_CHUNK_VOLUME
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LevelChunkTest {
    private val plains = PalettedStorage.filled(1)

    @Test
    fun `a chunk tells of its biomes for the full height and how many sub-chunks it has, for the client to ask for them`() {
        val blocks = PalettedStorage.of(IntArray(SUB_CHUNK_VOLUME) { if (it == 0) 5 else 9 })
        val packet = LevelChunk(ChunkPos(1, -1), Chunk(listOf(blocks, blocks), List(SUB_CHUNK_COUNT) { plains })).encode()

        // The first biome storage in full: no index bits, then plains (1). The other 23 repeat it. Then no border blocks.
        val payload = bytes(0x01, 0x02) + ByteArray(SUB_CHUNK_COUNT - 1) { 0xFF.toByte() } + bytes(0x00)
        // ID 58, x 1 and z -1 zigzagged, the overworld, no sub-chunks here, two to ask for, no cache, no blob hashes.
        val header = bytes(0x3A, 0x02, 0x01, 0x00, 0x00, 0x01, 0x04, 0x00, 0x00)
        assertContentEquals(header + bytes(payload.size) + payload, packet)
    }

    @Test
    fun `from 1_26_60 a chunk says it isn't only biomes the client changed`() {
        val chunk = LevelChunk(ChunkPos(1, -1), Chunk(listOf(PalettedStorage.filled(9)), List(SUB_CHUNK_COUNT) { plains }))
        assertContentEquals(chunk.encode() + bytes(0x00), chunk.encode(GameVersion.V1_26_60))
    }

    @Test
    fun `a sub-chunk packs its height, then its blocks, the indices in words, then the palette`() {
        // Block 0 is the first palette entry (5) and every other block the second (9), so one bit each.
        val blocks = PalettedStorage.of(IntArray(SUB_CHUNK_VOLUME) { if (it == 0) 5 else 9 })
        val subChunk = bytes(0x09, 0x01, 0xFC) + // Version 9, one storage layer, at height -4
            bytes(0x03) + // One bit per index, network palette
            bytes(0xFE, 0xFF, 0xFF, 0xFF) + ByteArray(127 * 4) { -1 } + // 128 words of 32 indices
            bytes(0x04, 0x0A, 0x12) // Two palette entries, 5 and 9, zigzagged
        assertContentEquals(subChunk, subChunkPayload(0, blocks, liquid = null, blockEntities = emptyList()))
    }

    @Test
    fun `indices that don't divide 32 bits pad each word instead of straddling two`() {
        // Five palette entries need three bits each: ten to a word, with two bits spare.
        val blocks = PalettedStorage.of(IntArray(SUB_CHUNK_VOLUME) { it % 5 })
        val payload = Unpooled.wrappedBuffer(subChunkPayload(0, blocks, liquid = null, blockEntities = emptyList()))

        payload.skipBytes(3) // Sub-chunk header
        assertEquals((3 shl 1) or 1, payload.readUnsignedByte().toInt())
        assertEquals(0x23444688, payload.readIntLE())
        payload.skipBytes(408 * 4)
        assertEquals(0x4688, payload.readIntLE()) // The last word holds the six blocks left over
        assertEquals(5, payload.readVarInt())
        assertEquals(listOf(0, 1, 2, 3, 4), List(5) { payload.readVarInt() })
    }

    @Test
    fun `the client asks for sub-chunks by their offsets from one it names`() {
        val request = ByteBufUtil.getBytes(
            Unpooled.buffer().writeVarUInt(175).writeVarInt(0) // The overworld
                .writeVarUInt(2).writeByte(0).writeByte(-1).writeByte(1).writeByte(2).writeByte(0).writeByte(-2)
                .writeIntLE(3).writeIntLE(-4).writeIntLE(-5),
        )
        assertEquals(SubChunkRequest(3, -4, -5, listOf(SubChunkOffset(0, -1, 1), SubChunkOffset(2, 0, -2))), decodeClientPacket(request))
    }

    @Test
    fun `sub-chunks go back with their blocks and the heights the client lights them by, given, all above or all below`() {
        val heights = ByteArray(256) { if (it == 0) 3 else -1 }
        val packet = SubChunk(
            3, -4, -5,
            listOf(
                SubChunkEntry(SubChunkOffset(0, 0, 0), SubChunkResult.FOUND, payload = bytes(0xAB), heights = heights),
                SubChunkEntry(SubChunkOffset(1, 0, 0), SubChunkResult.ALL_AIR, heights = ByteArray(256) { 16 }),
                SubChunkEntry(SubChunkOffset(0, 9, 0), SubChunkResult.OUT_OF_WORLD),
            ),
        ).encode()

        val rows = (0 until 16).fold(ByteArray(0)) { all, z -> all + bytes(0x10) + heights.copyOfRange(z * 16, z * 16 + 16) }
        val expected = bytes(0xAE, 0x01) + // ID 174
            bytes(0x00, 0x00) + // No cache, the overworld
            bytes(0x03, 0x00, 0x00, 0x00, 0xFC, 0xFF, 0xFF, 0xFF, 0xFB, 0xFF, 0xFF, 0xFF) + // Where from, in little-endian ints
            bytes(0x03) + // Three entries
            bytes(0x00, 0x00, 0x00, 0x01, 0x01, 0x01, 0xAB) + bytes(0x01, 0x01) + rows + bytes(0x01, 0x01) + rows + bytes(0x00) +
            bytes(0x01, 0x00, 0x00, 0x06, 0x00) + bytes(0x02, 0x00, 0x02, 0x00) + bytes(0x00) +
            bytes(0x00, 0x09, 0x00, 0x05, 0x00) + bytes(0x00, 0x00, 0x00, 0x00) + bytes(0x00)
        assertContentEquals(expected, packet)
    }

    @Test
    fun `the publisher update centres the view in blocks and lists no saved chunks`() {
        val encoded = NetworkChunkPublisherUpdate(BlockPos(0, -60, 0), radius = 128).encode()

        // ID 121, the position as zigzagged varints, the radius as a varuint, then a little-endian zero count.
        assertContentEquals(bytes(0x79, 0x00, 0x77, 0x00, 0x80, 0x01, 0x00, 0x00, 0x00, 0x00), encoded)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
