package org.koremc.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.koremc.registry.BiomeDefinition
import org.koremc.registry.ItemDefinition
import org.koremc.world.BlockPos
import org.koremc.world.Vec3
import java.util.EnumSet
import java.util.UUID

// Packet IDs and layouts follow Mojang's protocol schemas for 1.26.50 (protocol 2193):
// https://github.com/Mojang/bedrock-protocol-docs
private const val LOGIN = 1
private const val PLAY_STATUS = 2
private const val DISCONNECT = 5
private const val RESOURCE_PACKS_INFO = 6
private const val RESOURCE_PACK_STACK = 7
private const val RESOURCE_PACK_CLIENT_RESPONSE = 8
private const val PACKET_VIOLATION_WARNING = 156
private const val BLOCK_PICK_REQUEST = 34
private const val INTERACT = 33
private const val CONTAINER_OPEN = 46
private const val CONTAINER_CLOSE = 47
private const val REQUEST_CHUNK_RADIUS = 69
private const val CHUNK_RADIUS_UPDATED = 70
private const val SET_LOCAL_PLAYER_AS_INITIALISED = 113
private const val LOADING_SCREEN = 312
private const val BIOME_DEFINITION_LIST = 122
private const val CLIENT_CACHE_STATUS = 129
private const val PLAYER_AUTH_INPUT = 144
private const val ITEM_REGISTRY = 162
private const val NETWORK_SETTINGS = 143
private const val REQUEST_NETWORK_SETTINGS = 193
private const val JIGSAW_STRUCTURE_DATA = 313
private const val VOXEL_SHAPES = 337

/** The low ten bits of a packet header hold its ID. The rest name split-screen sub-clients, which Kore doesn't support. */
private const val PACKET_ID_MASK = 0x3FF

/** A packet from the client. */
sealed interface ClientPacket

/** The first packet of every connection, carrying nothing but the client's protocol version. */
data class RequestNetworkSettings(val protocol: Int) : ClientPacket

/** The client's protocol version again, and its connection request: who it claims to be, plus its client data. */
class Login(val protocol: Int, val connectionRequest: ByteArray) : ClientPacket {
    // The connection request holds the player's tokens, so logs only get its size.
    override fun toString() = "Login(protocol=$protocol, ${connectionRequest.size} bytes)"
}

/** Whether the client supports the blob cache, which saves resending chunk data it already has. */
data class ClientCacheStatus(val supported: Boolean) : ClientPacket

/** The client's answer during resource pack negotiation. */
data class ResourcePackClientResponse(
    val response: Response,
    /** The packs the client wants, when it's [Response.DOWNLOADING]. */
    val packs: List<String> = emptyList(),
) : ClientPacket {
    /** Declared in wire order. Each is sent as its ordinal, followed by its name as a string. */
    enum class Response {
        /** The client won't accept the packs and is leaving. */
        CANCEL,

        /** The client wants [packs] sent. */
        DOWNLOADING,

        /** The client has every pack it was offered. */
        DOWNLOADING_FINISHED,

        /** The client has applied the pack stack and is ready for the world. */
        RESOURCE_PACK_STACK_FINISHED,
    }
}

/** How many chunks around the player the client wants to see, and the most it can handle. */
data class RequestChunkRadius(val radius: Int, val maxRadius: Int) : ClientPacket

/**
 * The input flags Kore acts on, by their number in `PlayerAuthInput`.
 * Mojang's schema lists the flags without `NorthJump` (2), so its names
 * sit one below these numbers, which minecraft-data and gophertunnel agree
 * on.
 */
enum class InputFlag(val id: Int) {
    /** Set on every tick the player is sneaking. */
    SNEAKING(8),
    START_SPRINTING(25),
    STOP_SPRINTING(26),
    START_SWIMMING(29),
    STOP_SWIMMING(30),
    START_JUMPING(31),
    START_GLIDING(32),
    STOP_GLIDING(33),

    /** Set on the first tick after the client moved its player where a teleport put them. */
    HANDLED_TELEPORT(37),
    START_FLYING(42),
    STOP_FLYING(43),

    /** Set on every tick the player bumps into something above or below, which is mostly standing on the ground. */
    VERTICAL_COLLISION(50),
}

private val INPUT_FLAGS = InputFlag.entries.associateBy(InputFlag::id)

/**
 * The client's input for one tick, with where it predicts its player is
 * now. It arrives every tick once the player is in the world. Some actions
 * ride along with it, so the server sees them in step with the movement:
 * using an item on a block, a request to change items, and the steps of
 * breaking a block. Kore reads as far as those; vehicles come after.
 */
data class PlayerAuthInput(
    val pitch: Float,
    val yaw: Float,
    val headYaw: Float,
    /** Where the player's eyes are. */
    val position: Vec3,
    /** How far the player moved this tick. */
    val delta: Vec3,
    /** The client's tick, which corrections name to say which moment they apply to. */
    val tick: Long,
    /** The flags set this tick that Kore acts on. Any others are left out. */
    val inputs: Set<InputFlag> = emptySet(),
    /** An item used on a block, as a transaction with the slot changes the client predicts, like the wear on a tool that broke a block. */
    val itemUse: InventoryTransaction? = null,
    val stackRequest: StackRequest? = null,
    val blockActions: List<BlockAction> = emptyList(),
) : ClientPacket {
    /** Whether the player only moved this tick, with no action riding along. */
    val onlyMoves get() = itemUse == null && stackRequest == null && blockActions.isEmpty()
}

/** The client has the chunks around it and has put its player in the world, naming the player's runtime ID from `StartGame`. */
data class SetLocalPlayerAsInitialised(val runtimeId: Long) : ClientPacket

/** The client's loading screen opening or closing, as when it joins and loads the world. */
data class LoadingScreen(val type: Int) : ClientPacket {
    companion object {
        const val START = 1
        const val END = 2
    }
}

/** The player interacting with an entity, or with nothing, as when they press the key to open their inventory. */
data class Interact(val action: Int, val targetRuntimeId: Long) : ClientPacket {
    companion object {
        const val OPEN_INVENTORY = 6
    }
}

/**
 * A container window closing. The client sends it when the player closes
 * one, and the server sends it back to agree, or to close one itself.
 */
data class ContainerClose(val windowId: Int, val containerType: Int, val serverInitiated: Boolean = false) : ClientPacket, ServerPacket {
    override val id get() = CONTAINER_CLOSE

    override fun encodeBody(out: ByteBuf) {
        out.writeByte(windowId)
        out.writeByte(containerType)
        out.writeBoolean(serverInitiated)
    }
}

/**
 * The client saying a packet it got from Kore was wrong, by the packet's
 * ID, with what it made of it: how wrong, [severity], and in what way,
 * [context]. It disconnects over the worst of them.
 */
data class PacketViolation(val type: Int, val severity: Int, val packetId: Int, val context: String) : ClientPacket

/**
 * The player picking the block at [position], with a middle click, for its
 * item in their hand, and [withData], with control held, for the item to
 * carry what the block keeps too. They held [hotbarSlot] as they picked it.
 */
data class BlockPick(val position: BlockPos, val withData: Boolean, val hotbarSlot: Int) : ClientPacket

/** A packet Kore doesn't decode yet. */
class UnhandledPacket(val id: Int, val payload: ByteArray) : ClientPacket {
    override fun toString() = "UnhandledPacket(id=$id, ${payload.size} bytes)"
}

/** A packet to the client. */
sealed interface ServerPacket {
    val id: Int
    fun encodeBody(out: ByteBuf)
}

/** Tells the client how to compress from now on. Everything after this packet, both ways, uses it. */
data class NetworkSettings(
    val compressionThreshold: Int,
    val compressionAlgorithm: CompressionAlgorithm = CompressionAlgorithm.ZLIB,
    val clientThrottle: Boolean = false,
    val clientThrottleThreshold: Int = 0,
    val clientThrottleScalar: Float = 0f,
) : ServerPacket {
    override val id get() = NETWORK_SETTINGS

    override fun encodeBody(out: ByteBuf) {
        out.writeShortLE(compressionThreshold)
        out.writeShortLE(compressionAlgorithm.id)
        out.writeBoolean(clientThrottle)
        out.writeByte(clientThrottleThreshold)
        out.writeFloatLE(clientThrottleScalar)
    }
}

data class PlayStatus(val status: Status) : ServerPacket {
    /** Declared in wire order: each status is sent as its ordinal. */
    enum class Status {
        LOGIN_SUCCESS,
        LOGIN_FAILED_CLIENT_OLD,
        LOGIN_FAILED_SERVER_OLD,
        PLAYER_SPAWN,
        LOGIN_FAILED_INVALID_TENANT,
        LOGIN_FAILED_EDITION_MISMATCH_EDU_TO_VANILLA,
        LOGIN_FAILED_EDITION_MISMATCH_VANILLA_TO_EDU,
        LOGIN_FAILED_SERVER_FULL_SUB_CLIENT,
        LOGIN_FAILED_EDITOR_MISMATCH_EDITOR_TO_VANILLA,
        LOGIN_FAILED_EDITOR_MISMATCH_VANILLA_TO_EDITOR,
    }

    override val id get() = PLAY_STATUS

    override fun encodeBody(out: ByteBuf) {
        out.writeInt(status.ordinal)
    }
}

/** Ends the client's session, showing [message] if there is one, or a stock screen for [reason] if not. */
data class Disconnect(
    val reason: Reason,
    val message: String? = null,
    /** The message with profanity filtered, for players who've asked for that. */
    val filteredMessage: String = message.orEmpty(),
) : ServerPacket {
    /** The handful of the client's 150 disconnect reasons Kore uses, with their wire values. */
    enum class Reason(val id: Int) {
        UNKNOWN(0),
        SERVER_FULL(25),
        OUTDATED_SERVER(34),
        OUTDATED_CLIENT(35),
        NOT_AUTHENTICATED(46),
        KICKED(55),
        SHUTDOWN(68),
    }

    override val id get() = DISCONNECT

    override fun encodeBody(out: ByteBuf) {
        out.writeVarInt(reason.id)
        // The messages are an optional variant: 0 says they follow, 1 says there are none.
        if (message == null) {
            out.writeByte(1)
        } else {
            out.writeByte(0)
            out.writeString(message)
            out.writeString(filteredMessage)
        }
    }
}

/**
 * Lists the resource packs the client needs before joining. Kore has none
 * yet, so it sends an empty list, and the client replies that it has
 * everything already.
 */
data class ResourcePacksInfo(
    val packsRequired: Boolean = false,
    val forceDisableVibrantVisuals: Boolean = false,
) : ServerPacket {
    override val id get() = RESOURCE_PACKS_INFO

    override fun encodeBody(out: ByteBuf) {
        out.writeBoolean(packsRequired)
        out.writeBoolean(false) // Has add-on packs
        out.writeBoolean(false) // Has client scripts
        out.writeBoolean(forceDisableVibrantVisuals)
        out.writeUuid(UUID(0, 0)) // No world template
        out.writeString("") // World template version
        out.writeVarUInt(0) // Resource packs
    }
}

/** The order to apply packs in. Kore's is empty: just the vanilla packs the client already has. */
data class ResourcePackStack(
    /**
     * The game version whose vanilla packs to build on, like "1.26.50".
     * Sending `*` instead left blocks new in 26.50 without textures, and
     * other servers send their game version.
     */
    val baseGameVersion: String,
    val experiments: Map<String, Boolean> = emptyMap(),
) : ServerPacket {
    override val id get() = RESOURCE_PACK_STACK

    override fun encodeBody(out: ByteBuf) {
        out.writeBoolean(false) // Packs required
        out.writeVarUInt(0) // Resource packs
        out.writeString(baseGameVersion)
        // Experiments count as a plain little-endian uint32 here, not a varint.
        out.writeIntLE(experiments.size)
        experiments.forEach { (name, enabled) ->
            out.writeString(name)
            out.writeBoolean(enabled)
        }
        out.writeBoolean(experiments.isNotEmpty()) // Experiments ever toggled
        out.writeBoolean(false) // Include editor packs
    }
}

/**
 * The items in the world, with the runtime IDs the protocol refers to them
 * by. The client waits for this after `StartGame`, and knows no item it
 * doesn't list.
 *
 * Every player gets the same thousands of items, so the packet encodes them
 * once, when it's made, and every send reuses those bytes.
 */
class ItemRegistry(val items: List<ItemDefinition>) : ServerPacket {
    override val id get() = ITEM_REGISTRY

    private val body = encodeOnce {
        writeVarUInt(items.size)
        for (item in items) {
            writeString(item.name)
            writeShortLE(item.runtimeId)
            writeBoolean(item.componentBased)
            writeVarInt(item.version.ordinal)
            writeNbt(item.components)
        }
    }

    override fun encodeBody(out: ByteBuf) {
        out.writeBytes(body)
    }
}

/**
 * Every biome in the world, with the climate and colours the client renders
 * it with. Names and tags go once each into a string list at the end, and
 * the definitions refer to them by index. Like the item registry, it's the
 * same for every player, so it's encoded once.
 */
class BiomeDefinitionList(val biomes: List<BiomeDefinition>) : ServerPacket {
    override val id get() = BIOME_DEFINITION_LIST

    private val body = encodeOnce {
        val strings = LinkedHashMap<String, Int>()
        fun indexOf(string: String) = strings.getOrPut(string) { strings.size }

        writeVarUInt(biomes.size)
        for (biome in biomes) {
            writeShortLE(indexOf(biome.name))
            writeShortLE(biome.id ?: NO_BIOME_ID)
            writeFloatLE(biome.temperature)
            writeFloatLE(biome.downfall)
            writeFloatLE(biome.foliageSnow)
            writeFloatLE(biome.depth)
            writeFloatLE(biome.scale)
            writeIntLE(biome.mapWaterColor)
            writeBoolean(biome.rain)
            writeBoolean(true) // Tags present
            writeVarUInt(biome.tags.size)
            biome.tags.forEach { writeShortLE(indexOf(it)) }
            writeBoolean(false) // World generation data, which only a client generating its own terrain needs
        }
        check(strings.size <= Short.MAX_VALUE) { "${strings.size} biome names and tags is more than a 16-bit index can reach" }
        writeVarUInt(strings.size)
        strings.keys.forEach { writeString(it) }
    }

    override fun encodeBody(out: ByteBuf) {
        out.writeBytes(body)
    }

    private companion object {
        /** What a biome without an ID sends instead, as the game's own server does for vanilla biomes. */
        const val NO_BIOME_ID = -1
    }
}

/**
 * The game's jigsaw structure rules, which a client generating its own
 * terrain would use. Kore's clients don't, so every list is empty, but the
 * client still disconnects ("Missing structure data from server") unless
 * this arrives before `StartGame`.
 */
data object JigsawStructureData : ServerPacket {
    override val id get() = JIGSAW_STRUCTURE_DATA

    private val empty = NbtMap.builder().apply {
        for (key in listOf("jigsaws", "processors", "structure_sets", "template_pools")) putList(key, NbtType.COMPOUND, emptyList())
    }.build()

    override fun encodeBody(out: ByteBuf) {
        out.writeNbt(empty)
    }
}

/**
 * Collision shapes for custom blocks. Kore has none, but Mojang's docs say
 * to always send this before `StartGame` too.
 */
data object VoxelShapes : ServerPacket {
    override val id get() = VOXEL_SHAPES

    override fun encodeBody(out: ByteBuf) {
        out.writeVarUInt(0) // Shapes
        out.writeVarUInt(0) // Shape names
        out.writeShortLE(0) // Custom shapes among them
    }
}

/**
 * Opens a container window on the client. For the player's own inventory,
 * that's window 0, of type -1, with no entity behind it.
 */
data class ContainerOpen(val windowId: Int, val containerType: Int, val position: BlockPos, val entityId: Long) : ServerPacket {
    override val id get() = CONTAINER_OPEN

    override fun encodeBody(out: ByteBuf) {
        out.writeByte(windowId)
        out.writeByte(containerType)
        out.writeBlockPos(position)
        out.writeVarLong(entityId)
    }
}

/** The view distance the server settled on, in chunks. */
data class ChunkRadiusUpdated(val radius: Int) : ServerPacket {
    override val id get() = CHUNK_RADIUS_UPDATED

    override fun encodeBody(out: ByteBuf) {
        out.writeVarInt(radius)
    }
}

private fun decodePlayerAuthInput(buffer: ByteBuf): PlayerAuthInput {
    val pitch = buffer.readFloatLE()
    val yaw = buffer.readFloatLE()
    val position = buffer.readVec3()
    buffer.skipBytes(8) // Move vector
    val headYaw = buffer.readFloatLE()
    val inputs = buffer.readList { readVarInt() }.mapNotNullTo(EnumSet.noneOf(InputFlag::class.java)) { INPUT_FLAGS[it] }
    buffer.readVarUInt() // Input mode: mouse, touch, gamepad and so on
    buffer.readVarUInt() // Play mode
    buffer.readVarInt() // Interaction model
    buffer.skipBytes(8) // Interact rotation
    val tick = buffer.readVarULong()
    val delta = buffer.readVec3()
    // Each action is there if a flag before it says so.
    val itemUse = if (buffer.readBoolean()) buffer.readPackedItemUse() else null
    val stackRequest = if (buffer.readBoolean()) buffer.readStackRequest() else null
    val blockActions = if (buffer.readBoolean()) buffer.readList { readBlockAction() } else emptyList()
    return PlayerAuthInput(pitch, yaw, headYaw, position, delta, tick, inputs, itemUse, stackRequest, blockActions)
}

fun decodeClientPacket(packet: ByteArray): ClientPacket {
    val buffer = Unpooled.wrappedBuffer(packet)
    return try {
        when (val id = buffer.readVarUInt() and PACKET_ID_MASK) {
            REQUEST_NETWORK_SETTINGS -> RequestNetworkSettings(protocol = buffer.readInt())
            LOGIN -> Login(protocol = buffer.readInt(), connectionRequest = buffer.readByteArray())
            CLIENT_CACHE_STATUS -> ClientCacheStatus(supported = buffer.readBoolean())
            REQUEST_CHUNK_RADIUS -> RequestChunkRadius(radius = buffer.readVarInt(), maxRadius = buffer.readUnsignedByte().toInt())
            SET_LOCAL_PLAYER_AS_INITIALISED -> SetLocalPlayerAsInitialised(runtimeId = buffer.readVarULong())
            LOADING_SCREEN -> LoadingScreen(type = buffer.readVarInt()).also { if (buffer.readBoolean()) buffer.readUnsignedIntLE() } // An ID, for a screen the server opened
            INTERACT -> Interact(action = buffer.readUnsignedByte().toInt(), targetRuntimeId = buffer.readVarULong())
            CONTAINER_CLOSE -> ContainerClose(buffer.readUnsignedByte().toInt(), buffer.readUnsignedByte().toInt(), buffer.readBoolean())
            ITEM_STACK_REQUEST -> decodeItemStackRequest(buffer)
            INVENTORY_TRANSACTION -> decodeInventoryTransaction(buffer)
            LEVEL_SOUND_EVENT -> decodeLevelSoundEvent(buffer)
            MOB_EQUIPMENT -> decodeMobEquipment(buffer)
            ACTOR_EVENT -> ActorEvent(entityId = buffer.readVarULong(), event = buffer.readUnsignedByte().toInt(), data = buffer.readVarInt())
            ANIMATE -> decodeAnimate(buffer)
            TEXT -> decodeText(buffer)
            COMMAND_REQUEST -> decodeCommandRequest(buffer)
            PLAYER_ACTION -> decodePlayerAction(buffer)
            PLAYER_AUTH_INPUT -> decodePlayerAuthInput(buffer)
            LECTERN_UPDATE -> decodeLecternUpdate(buffer)
            BLOCK_ACTOR_DATA -> decodeEditedBlockData(buffer)
            ITEM_FRAME_DROP_ITEM -> decodeItemFrameDropItem(buffer)
            BOOK_EDIT -> decodeBookEdit(buffer)
            PLAYER_TOGGLE_CRAFTER_SLOT_REQUEST -> decodeToggleCrafterSlot(buffer)
            MAP_INFO_REQUEST -> decodeMapInfoRequest(buffer)
            MAP_CREATE_LOCKED_COPY -> decodeMapCreateLockedCopy(buffer)
            RESOURCE_PACK_CLIENT_RESPONSE -> decodeResourcePackClientResponse(buffer)
            PACKET_VIOLATION_WARNING -> PacketViolation(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readString())
            BLOCK_PICK_REQUEST -> BlockPick(buffer.readBlockPos(), buffer.readBoolean(), buffer.readUnsignedByte().toInt())
            SUB_CHUNK_REQUEST -> decodeSubChunkRequest(buffer)
            RESPAWN -> Respawn(buffer.readVec3(), buffer.readUnsignedByte().toInt(), buffer.readVarULong())
            else -> UnhandledPacket(id, ByteBufUtil.getBytes(buffer))
        }
    } catch (e: IndexOutOfBoundsException) {
        throw PacketException("packet ends early")
    }
}

private fun decodeResourcePackClientResponse(buffer: ByteBuf): ResourcePackClientResponse {
    val tag = buffer.readVarUInt()
    val response = ResourcePackClientResponse.Response.entries.getOrNull(tag) ?: throw PacketException("unknown resource pack response $tag")
    buffer.readString() // The response's name, which only repeats the tag
    val packs = if (response == ResourcePackClientResponse.Response.DOWNLOADING) buffer.readList { readString() } else emptyList()
    return ResourcePackClientResponse(response, packs)
}

/** Encodes a body now, for packets that keep their bytes to send again and again. */
internal fun encodeOnce(write: ByteBuf.() -> Unit): ByteArray = ByteBufUtil.getBytes(Unpooled.buffer().apply(write))

fun ServerPacket.encode(): ByteArray {
    val buffer = Unpooled.buffer()
    buffer.writeVarUInt(id)
    encodeBody(buffer)
    return ByteBufUtil.getBytes(buffer)
}
