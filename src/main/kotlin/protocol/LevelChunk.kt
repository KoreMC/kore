package org.koremc.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.cloudburstmc.nbt.NbtMap
import org.koremc.world.BlockPos
import org.koremc.world.Chunk
import org.koremc.world.ChunkPos
import org.koremc.world.MIN_Y
import org.koremc.world.PalettedStorage

private const val LEVEL_CHUNK = 58
private const val NETWORK_CHUNK_PUBLISHER_UPDATE = 121
internal const val SUB_CHUNK_REQUEST = 175
private const val SUB_CHUNK = 174

private const val OVERWORLD = 0

/** The sub-chunk format in which each sub-chunk names its own height. */
private const val SUB_CHUNK_VERSION = 9

/** Marks a storage that repeats the one before it, sent instead of the storage. Only biomes use it. */
private const val REPEAT_PREVIOUS = (0x7F shl 1) or 1

/** How many blocks across a sub-chunk is, and its heightmap. */
private const val SIDE = 16

// What a sub-chunk's heightmap says: where in each column the highest block stopping light is, all above it, or all below it.
private const val HEIGHTS_GIVEN = 1
private const val HEIGHTS_ALL_ABOVE = 2
private const val HEIGHTS_ALL_BELOW = 3

/**
 * A chunk column, as the game's own server tells the client of it: its
 * biomes for the column's full height, and how many sub-chunks up from
 * the bottom of the world it has, up to the highest with any blocks. The
 * client then asks for the sub-chunks it wants of those with
 * [SubChunkRequest], each of which comes with what it needs to light it.
 */
data class LevelChunk(val position: ChunkPos, val chunk: Chunk) : VersionedPacket {
    override val id get() = LEVEL_CHUNK

    override fun encodeBody(out: ByteBuf, version: GameVersion) {
        out.writeVarInt(position.x)
        out.writeVarInt(position.z)
        out.writeVarInt(OVERWORLD)
        out.writeVarUInt(0) // No sub-chunks here: the client asks for them
        out.writeBoolean(true)
        out.writeVarInt(chunk.subChunks.size) // How many it asks for, from the bottom up
        out.writeBoolean(false) // Blob cache
        out.writeVarUInt(0) // Blob hashes, which only the cache uses

        val payload = Unpooled.buffer()
        chunk.biomes.forEachIndexed { i, biomes -> payload.writePalettedStorage(biomes, previous = chunk.biomes.getOrNull(i - 1)) }
        payload.writeByte(0) // Border blocks, an Education Edition feature
        out.writeVarUInt(payload.readableBytes())
        out.writeBytes(payload)
        if (version >= GameVersion.V1_26_60) out.writeBoolean(false) // Whether it only answers the client changing biomes, as the editor does
    }
}

/** A sub-chunk's place, by how many sub-chunks it's [x], [y] and [z] from another. */
data class SubChunkOffset(val x: Int, val y: Int, val z: Int)

/**
 * The client asking for sub-chunks, each by its offset from the sub-chunk
 * at [x], [y] and [z], in sub-chunks, [y] counting up from y 0
 * in sixteens, so the lowest overworld sub-chunk is -4.
 */
data class SubChunkRequest(val x: Int, val y: Int, val z: Int, val offsets: List<SubChunkOffset>) : ClientPacket

internal fun decodeSubChunkRequest(buffer: ByteBuf): SubChunkRequest {
    buffer.readVarInt() // The dimension
    val offsets = buffer.readList { SubChunkOffset(readByte().toInt(), readByte().toInt(), readByte().toInt()) }
    return SubChunkRequest(buffer.readIntLE(), buffer.readIntLE(), buffer.readIntLE(), offsets)
}

/** How a sub-chunk the client asked for comes back: with its blocks, as all air, or not at all, for a chunk it hasn't been sent or a height outside the world. */
enum class SubChunkResult(val id: Int) { FOUND(1), NO_CHUNK(2), OUT_OF_WORLD(5), ALL_AIR(6) }

/**
 * A sub-chunk the client asked for, at its [offset] from where it asked
 * from: whether it came, its blocks and the data its blocks keep, in
 * [payload], and the height in it, in each column, of the highest block
 * stopping light, [heights], z row by row, which the client lights it by:
 * from 0 to 15, or 16 for above it, and -1 for below it.
 */
class SubChunkEntry(val offset: SubChunkOffset, val result: SubChunkResult, val payload: ByteArray? = null, val heights: ByteArray? = null)

/**
 * The sub-chunk at [index] counting from the bottom of the world, of
 * [blocks], with the [liquid] inside them, if any, and the data its blocks
 * keep, [blockEntities], as the client is sent it.
 */
fun subChunkPayload(index: Int, blocks: PalettedStorage, liquid: PalettedStorage?, blockEntities: Collection<NbtMap>): ByteArray {
    val out = Unpooled.buffer()
    out.writeSubChunk(MIN_Y / SIDE + index, blocks, liquid)
    blockEntities.forEach(out::writeNbt)
    return ByteBufUtil.getBytes(out)
}

/** The sub-chunks the client asked for, by their offsets from the sub-chunk at [x], [y] and [z], answered in [entries]. */
class SubChunk(val x: Int, val y: Int, val z: Int, val entries: List<SubChunkEntry>) : ServerPacket {
    override val id get() = SUB_CHUNK

    override fun encodeBody(out: ByteBuf) {
        out.writeBoolean(false) // Blob cache
        out.writeVarInt(OVERWORLD)
        out.writeIntLE(x)
        out.writeIntLE(y)
        out.writeIntLE(z)
        out.writeVarUInt(entries.size)
        for (entry in entries) {
            out.writeByte(entry.offset.x).writeByte(entry.offset.y).writeByte(entry.offset.z)
            out.writeByte(entry.result.id)
            out.writeBoolean(entry.payload != null)
            entry.payload?.let { out.writeVarUInt(it.size).writeBytes(it) }
            // The heights the client lights by, then those it draws by, which are the same.
            repeat(2) { out.writeHeights(entry.heights) }
            out.writeBoolean(false) // Blob hash
        }
    }
}

/** A sub-chunk's [heights] as the client is sent them: how they're given, then them, if they're given, each z row a list of its own. */
private fun ByteBuf.writeHeights(heights: ByteArray?) {
    val kind = when {
        heights == null -> 0
        heights.all { it.toInt() == SIDE } -> HEIGHTS_ALL_ABOVE
        heights.all { it.toInt() == -1 } -> HEIGHTS_ALL_BELOW
        else -> HEIGHTS_GIVEN
    }
    writeByte(kind)
    writeBoolean(kind == HEIGHTS_GIVEN)
    if (kind != HEIGHTS_GIVEN) return
    for (z in 0 until SIDE) {
        writeVarUInt(SIDE)
        writeBytes(heights!!, z * SIDE, SIDE)
    }
}

/**
 * Where the player's view is centred and how far it reaches, in blocks.
 * The client keeps the chunks inside it, and drops the rest.
 */
data class NetworkChunkPublisherUpdate(val position: BlockPos, val radius: Int) : ServerPacket {
    override val id get() = NETWORK_CHUNK_PUBLISHER_UPDATE

    override fun encodeBody(out: ByteBuf) {
        out.writeBlockPos(position)
        out.writeVarUInt(radius)
        out.writeIntLE(0) // Chunks the server built for the client to save, counted without a varint
    }
}

/** A sub-chunk: its [blocks], and the [liquid] inside them, if there's any, as a second layer. */
private fun ByteBuf.writeSubChunk(index: Int, blocks: PalettedStorage, liquid: PalettedStorage?) {
    writeByte(SUB_CHUNK_VERSION)
    writeByte(if (liquid == null) 1 else 2) // Storage layers: the blocks, then the water inside them, if any
    writeByte(index) // Its height, in sub-chunks, so the lowest overworld sub-chunk is -4
    writePalettedStorage(blocks, previous = null)
    liquid?.let { writePalettedStorage(it, previous = null) }
}

/**
 * [storage] in the network format: a header byte with the bits per palette
 * index, then the indices packed into little-endian 32-bit words, as many
 * as fit whole, then the palette as varints. A single-value palette needs
 * no indices and no count.
 */
private fun ByteBuf.writePalettedStorage(storage: PalettedStorage, previous: PalettedStorage?) {
    if (storage === previous) {
        writeByte(REPEAT_PREVIOUS)
        return
    }

    val bits = storage.bitsPerIndex
    writeByte((bits shl 1) or 1) // The low bit says the palette holds network IDs, not NBT
    if (bits > 0) {
        storage.packedIndices().forEach(::writeIntLE)
        writeVarInt(storage.palette.size)
    }
    storage.palette.forEach { writeVarInt(it) }
}
