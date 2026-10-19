package org.koremc.protocol

import io.netty.buffer.ByteBuf
import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.BlockDefinition
import org.koremc.world.BlockPos
import org.koremc.world.Vec3
import java.util.UUID

private const val START_GAME = 11
private const val SET_DIFFICULTY = 60

/** Game modes, with their wire values. */
enum class GameMode(val id: Int) {
    SURVIVAL(0),
    CREATIVE(1),
    ADVENTURE(2),

    /** Follow the world's game mode. */
    DEFAULT(5),
    SPECTATOR(6),
}

/** Declared in wire order: each is sent as its ordinal. */
enum class Difficulty { PEACEFUL, EASY, NORMAL, HARD }

/** The world's difficulty changing, as `/difficulty` changes it. */
data class SetDifficulty(val difficulty: Difficulty) : ServerPacket {
    override val id get() = SET_DIFFICULTY

    override fun encodeBody(out: ByteBuf) {
        out.writeVarUInt(difficulty.ordinal)
    }
}

/** Declared in wire order: each is sent as its ordinal. */
enum class PermissionLevel { VISITOR, MEMBER, OPERATOR, CUSTOM }

/** How the world was generated, which shapes the client's sky and void. Declared in wire order. */
enum class Generator { LEGACY, OVERWORLD, FLAT, NETHER, THE_END, VOID }

/**
 * Sets up the world on the client: who the player is, where they stand,
 * and how the world behaves. It's the protocol's biggest packet, and the
 * client gives up on the connection if a single field is off, so every
 * field is written in order, including the many Kore leaves at a default.
 *
 * The layout follows Mojang's schema for 1.26.50, checked against
 * gophertunnel and minecraft-data. Where those disagree with the schema, it
 * omits optional fields entirely and marks a few one-byte fields as varints,
 * and the implementations, which real clients accept, win.
 */
data class StartGame(
    /** The player's entity ID, unique for the whole server. */
    val entityId: Long,
    /** The player's entity ID for this session. Kore uses the same number as [entityId]. */
    val runtimeId: Long,
    val playerGameMode: GameMode,
    /** Where the player's eyes are, which is 1.62 blocks above their feet. */
    val position: Vec3,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val seed: Long,
    val dimension: Int = 0,
    val generator: Generator = Generator.FLAT,
    val worldGameMode: GameMode,
    val difficulty: Difficulty,
    val worldSpawn: BlockPos,
    val playerPermissions: PermissionLevel = PermissionLevel.MEMBER,
    /** How many chunks around each player the world simulates. */
    val serverChunkTickRange: Int = 4,
    val worldName: String,
    /** The game version the server speaks, like "1.26.50". */
    val serverVersion: String,
    /** The game version whose vanilla content the client should load. See [ResourcePackStack.baseGameVersion]. */
    val baseGameVersion: String,
    /** How long the world has run, in ticks, whatever the time of day. */
    val time: Long = 0,
    /** Whether it's raining, from 0 to 1, and thundering. */
    val rainLevel: Float = 0f,
    val lightningLevel: Float = 0f,
    /** The world's game rules, by the names the game keeps them under. */
    val gameRules: Map<String, Any> = emptyMap(),
    /** Ties the session's telemetry together. Any unique string will do. */
    val multiplayerCorrelationId: String = UUID.randomUUID().toString(),
    /** The blocks defined in data, which the client can't show or use without their definitions. */
    val blockDefinitions: BlockDefinitions = BlockDefinitions(emptyList()),
) : VersionedPacket {
    override val id get() = START_GAME

    override fun encodeBody(out: ByteBuf, version: GameVersion) {
        out.writeVarLong(entityId)
        out.writeVarULong(runtimeId)
        out.writeVarInt(playerGameMode.id)
        out.writeVec3(position)
        out.writeFloatLE(pitch)
        out.writeFloatLE(yaw)
        writeLevelSettings(out, version)
        out.writeString("") // Level ID
        out.writeString(worldName)
        out.writeString("") // Template content identity
        out.writeBoolean(false) // Is trial

        // Movement settings. Movement is always server authoritative now, and the client keeps a
        // history of its inputs so a correction from the server can rewind and replay them. Forty
        // ticks is two seconds of history, a choice that should cover most latency.
        out.writeVarInt(40)
        out.writeBoolean(false) // Server-authoritative block breaking

        out.writeLongLE(time)
        out.writeVarInt(0) // Enchantment seed
        out.writeBytes(blockDefinitions.encoded)
        out.writeString(multiplayerCorrelationId)
        out.writeBoolean(true) // Server-authoritative inventory
        out.writeString(serverVersion)
        out.writeNbt(NbtMap.EMPTY) // Player property data
        out.writeLongLE(0) // Block registry checksum. Zero skips the client's check.
        out.writeUuid(UUID(0, 0)) // World template
        out.writeBoolean(false) // Client-side generation
        // Kore names blocks by the hash of their state rather than their place in a palette,
        // so it never has to match the client's palette order.
        out.writeBoolean(true)
        out.writeBoolean(false) // Server-authoritative sound
        out.writeBoolean(false) // Server join info (an optional, left out)
        repeat(4) { out.writeString("") } // Telemetry: server, scenario, world and owner IDs
    }

    private fun writeLevelSettings(out: ByteBuf, version: GameVersion) {
        out.writeLongLE(seed)
        out.writeShortLE(0) // Spawn biome type: default
        out.writeString("") // User-defined spawn biome
        out.writeVarInt(dimension)
        out.writeVarInt(generator.ordinal)
        out.writeVarInt(worldGameMode.id)
        out.writeBoolean(false) // Hardcore
        out.writeVarInt(difficulty.ordinal)
        out.writeBlockPos(worldSpawn)
        out.writeBoolean(true) // Achievements disabled
        out.writeVarInt(0) // Editor world type: not an editor world
        out.writeBoolean(false) // Created in editor
        out.writeBoolean(false) // Exported from editor
        if (version >= GameVersion.V1_26_60) out.writeByte(0) // How far the editor has moved the world on: not at all
        out.writeVarInt(0) // Day cycle stop time
        out.writeVarUInt(0) // Education edition offer
        out.writeBoolean(false) // Education features
        out.writeString("") // Education product ID
        out.writeFloatLE(rainLevel)
        out.writeFloatLE(lightningLevel)
        out.writeBoolean(false) // Confirmed platform locked content
        out.writeBoolean(true) // Multiplayer game
        out.writeBoolean(false) // LAN broadcast
        // Broadcast settings, zigzagged as Mojang's schema and gophertunnel have them. minecraft-data reads
        // them unsigned; either way they take a byte, so nothing after them shifts.
        out.writeVarInt(PUBLIC) // Xbox Live broadcast
        out.writeVarInt(PUBLIC) // Platform broadcast
        out.writeBoolean(true) // Commands enabled
        out.writeBoolean(false) // Texture packs required
        out.writeVarUInt(gameRules.size)
        for ((name, value) in gameRules) out.writeGameRule(name, value)
        out.writeIntLE(0) // Experiments, counted as a plain uint32
        out.writeBoolean(false) // Experiments ever toggled
        out.writeBoolean(false) // Bonus chest
        out.writeBoolean(false) // Start with map
        out.writeByte(playerPermissions.ordinal) // A single byte, though the schema says varint
        out.writeIntLE(serverChunkTickRange)
        // Locked behaviour pack, locked resource pack, from locked template, Microsoft account
        // gamertags only, from world template, template option locked, only v1 villagers,
        // persona disabled, custom skins disabled and emote chat muted.
        repeat(10) { out.writeBoolean(false) }
        out.writeString(baseGameVersion)
        out.writeIntLE(0) // Limited world width
        out.writeIntLE(0) // Limited world depth
        out.writeBoolean(true) // New nether
        out.writeString("") // Education shared resource button name
        out.writeString("") // Education shared resource link
        out.writeBoolean(false) // Experimental gameplay override (an optional, left out)
        out.writeByte(0) // Chat restriction: none
        out.writeBoolean(false) // Disable player interactions
        out.writeVarInt(0) // Editor connection policy: match the world
        out.writeBoolean(false) // Anonymous block drops in editor worlds
    }

    private companion object {
        /** The broadcast setting for anyone being able to join. */
        const val PUBLIC = 4
    }
}

/**
 * Block definitions as `StartGame` carries them: each block's name, then
 * its definition as network NBT. They're the same for every player and run
 * to tens of kilobytes, so they're encoded once.
 */
class BlockDefinitions(val blocks: List<BlockDefinition>) {
    internal val encoded = encodeOnce {
        writeVarUInt(blocks.size)
        for (block in blocks) {
            writeString(block.name)
            writeNbt(block.data)
        }
    }
}
