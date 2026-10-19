package org.koremc.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtUtils
import org.koremc.world.BlockPos
import org.koremc.world.Vec3
import java.io.IOException

private const val UPDATE_BLOCK = 21
internal const val BLOCK_ACTOR_DATA = 56
private const val LEVEL_EVENT = 25
internal const val INVENTORY_TRANSACTION = 30
internal const val MOB_EQUIPMENT = 31
internal const val PLAYER_ACTION = 36
internal const val LEVEL_SOUND_EVENT = 123

/** Where an item's NBT keeps how worn it is. */
private const val DAMAGE = "Damage"

/** The client's prediction for an item use that works. One that fails is 0. */
private const val PREDICTED_SUCCESS = 1

/** What triggers an item use the client repeats, each tick the player holds the button down. A click of theirs is 1. */
private const val HELD_DOWN = 2

/** What the player did with their item. Declared in wire order: each is sent as its ordinal. */
enum class ItemUseAction { PLACE, USE, DESTROY, USE_AS_ATTACK }

/**
 * The player using their held item on a block, reported once the client
 * has predicted what happens: placing a block against [face] of the block
 * at [position], or destroying the block there.
 */
data class ItemUse(
    val action: ItemUseAction,
    val position: BlockPos,
    /** The face clicked, numbered as [org.koremc.world.Face] has them. */
    val face: Int,
    val hotbarSlot: Int,
    /** The runtime ID of the item the client says the player holds, or 0 for none. */
    val heldItem: Int,
    /** Where on [face] the player clicked, from the block's lowest corner, so each coordinate runs from 0 to 1. */
    val clickPosition: Vec3,
    /** The network ID of the block the client thinks is at [position]. */
    val clickedBlock: Int,
    /** Whether the client found the use works, and so changed its world as it expects the server to. */
    val predictedToWork: Boolean,
    /** Whether the client is trying the use again because the player holds the button down, rather than because they clicked. */
    val repeated: Boolean = false,
)

/**
 * The player using their held item on the entity [target], by its runtime
 * ID: attacking it, or interacting with it, as [action] says.
 */
data class ItemUseOnEntity(
    val target: Long,
    val action: Int,
    val hotbarSlot: Int,
    /** The runtime ID of the item the client says the player holds, or 0 for none. */
    val heldItem: Int,
) {
    companion object {
        const val INTERACT = 0
        const val ATTACK = 1
    }
}

/**
 * A transaction from the inventory system that came before item stack
 * requests. The client still sends one when the player uses an item on a
 * block, which Kore reads as [use], or on an entity, as [useOnEntity], and
 * when they drop what's in a hotbar slot outside the inventory screen, by
 * pressing Q, which is a [NORMAL] one. Its [actions] are the slot changes
 * the client predicts.
 */
data class InventoryTransaction(
    val type: Int,
    val actions: List<InventoryAction>,
    val use: ItemUse?,
    /**
     * The ID the client gives the stacks in the slots [actions] change, until
     * it hears what they hold now: below zero and even, or 0 for none.
     */
    val legacyRequestId: Int = 0,
    val useOnEntity: ItemUseOnEntity? = null,
) : ClientPacket {
    companion object {
        /** Items moving between slots, without anything else happening. */
        const val NORMAL = 0

        /** An item used on a block. */
        const val USE_ITEM = 2

        /** An item used on an entity, like a sword hitting a player. */
        const val USE_ITEM_ON_ENTITY = 3

        /** The player letting go of what they were using, like food they were eating. */
        const val RELEASE_ITEM = 4
    }
}

/**
 * A change the client predicts to one slot, from [before] to [after]. The
 * slot is in a container, the window [windowId], or in the world, where
 * the slot says what happens to the items, like [DROP_SLOT].
 */
data class InventoryAction(val source: Int, val windowId: Int?, val slot: Int, val before: ClientItem, val after: ClientItem) {
    companion object {
        /** A slot in a container, like the player's inventory. */
        const val CONTAINER = 0

        /** The world, which items go into and come out of. */
        const val WORLD = 2

        /** The world's slot for items the player drops. */
        const val DROP_SLOT = 0
    }
}

/**
 * An item as the client sends it outside item stack requests: its runtime
 * ID, which is 0 for none, how many there are, the ID of the stack it
 * comes from, when the client gives one, and how many of its uses are gone.
 */
data class ClientItem(val runtimeId: Int, val count: Int, val stackId: Int?, val damage: Int = 0)

/**
 * Something the player did that has no packet of its own, like breaking a
 * block in creative. Most such actions ride in `PlayerAuthInput` now.
 */
data class PlayerAction(val runtimeId: Long, val action: Int, val position: BlockPos, val face: Int) : ClientPacket {
    companion object {
        /** A survival player starting to break a block. */
        const val START_DESTROY_BLOCK = 0

        /** A survival player giving up on breaking a block. */
        const val ABORT_DESTROY_BLOCK = 1

        /** A survival player done breaking a block, whether it broke or not. */
        const val STOP_DESTROY_BLOCK = 2

        /** A creative player breaking a block, which the client has already done. */
        const val CREATIVE_DESTROY_BLOCK = 13

        /** A tick of a survival player breaking a block, for which the client expects bits to fly off it. */
        const val CRACK_BLOCK = 18
    }
}

/** A step in breaking a block, as `PlayerAuthInput` carries them: starting, cracking, stopping and so on, numbered as [PlayerAction]s are. */
data class BlockAction(val action: Int, val position: BlockPos, val face: Int)

/** The player choosing the item they hold, by picking a hotbar slot. */
data class MobEquipment(val runtimeId: Long, val heldItem: Int, val selectedSlot: Int, val windowId: Int) : ClientPacket

/**
 * A block that's now [networkId], which the client takes as it is,
 * whatever it predicted: the block itself, in [layer] 0, or the water
 * inside it, in layer 1.
 */
data class UpdateBlock(val position: BlockPos, val networkId: Int, val layer: Int = 0) : ServerPacket {
    override val id get() = UPDATE_BLOCK

    override fun encodeBody(out: ByteBuf) {
        out.writeBlockPos(position)
        out.writeVarUInt(networkId)
        out.writeVarUInt(BLOCK_UPDATE_NETWORK)
        out.writeVarUInt(layer)
    }

    private companion object {
        /** The flag for a change that comes over the network, rather than one the client made itself. */
        const val BLOCK_UPDATE_NETWORK = 0b10
    }
}

/**
 * The data the block at [position] keeps beyond its state, like a bed's
 * colour or a banner's patterns, as network NBT. It names its block
 * entity's kind with `id`, and where it is.
 */
data class BlockActorData(val position: BlockPos, val data: NbtMap) : ServerPacket {
    override val id get() = BLOCK_ACTOR_DATA

    override fun encodeBody(out: ByteBuf) {
        out.writeBlockPos(position)
        out.writeNbt(data)
    }
}

/** Something happening at a point in the world that the client shows, like a block's particles as it breaks. */
data class LevelEvent(val event: Int, val position: Vec3, val data: Int) : ServerPacket {
    override val id get() = LEVEL_EVENT

    override fun encodeBody(out: ByteBuf) {
        out.writeVarInt(event)
        out.writeVec3(position)
        out.writeVarInt(data)
    }

    companion object {
        /** The burst of particles and the sound of a block breaking. The data is the block's network ID. */
        const val PARTICLES_DESTROY_BLOCK = 2001

        /**
         * Rain or a thunderstorm starting, which the client fades in, or stopping.
         * Starting, the data is how hard it comes down, of 65535.
         */
        const val START_RAIN = 3001
        const val START_THUNDER = 3002
        const val STOP_RAIN = 3003
        const val STOP_THUNDER = 3004

        /**
         * A dispenser's click as it fires, and the higher one with nothing to
         * fire. The data is the pitch, in thousandths.
         */
        const val SOUND_CLICK = 1000
        const val SOUND_CLICK_FAIL = 1001

        /** Smoke puffing out of a dispenser as it fires. The data is which way, as [org.koremc.world.smokeDirection] has it. */
        const val PARTICLES_SHOOT = 2000

        /** An anvil clanging as a player uses it, or breaking as it wears out, and landing after a fall. */
        const val SOUND_ANVIL_BREAK = 1020
        const val SOUND_ANVIL_USE = 1021
        const val SOUND_ANVIL_LAND = 1022

        /** The chime of a player picking up experience. */
        const val SOUND_EXPERIENCE_ORB = 1051

        /** The sound of a Totem of Undying saving someone. */
        const val SOUND_TOTEM_USED = 1052

        /**
         * Cracks spreading over a block as it's broken. The data is how much
         * of the way they get each tick, out of 65535, and the client carries
         * on spreading them until they stop.
         */
        const val START_BLOCK_CRACKING = 3600
        const val STOP_BLOCK_CRACKING = 3601

        /**
         * Bits flying off the face of a block being broken, plus the face's
         * number, from down to east. The data is the block's network ID. An
         * older event packs the face into the ID's top byte, which a hashed
         * network ID has no room for.
         */
        const val PARTICLES_CRACK_BLOCK_DOWN = 3603
    }
}

/**
 * A sound at a point in the world, by the game's name for it, like
 * [PLACE]. A sound an entity makes names the entity's [entityType] and
 * [entityId], and a baby's is higher. The client sends the sounds its
 * player makes, like the block it's mining being hit, for the server to
 * play to everyone around, the player too.
 */
data class LevelSoundEvent(
    val sound: String,
    val position: Vec3,
    val data: Int,
    val entityType: String = ":",
    val baby: Boolean = false,
    val entityId: Long = -1,
) : ClientPacket, ServerPacket {
    override val id get() = LEVEL_SOUND_EVENT

    override fun encodeBody(out: ByteBuf) {
        out.writeString(sound)
        out.writeVec3(position)
        out.writeVarInt(data)
        out.writeString(entityType)
        out.writeBoolean(baby)
        out.writeBoolean(false) // Whether everyone hears it, wherever they are
        out.writeLongLE(entityId)
        out.writeBoolean(false) // A separate point to fire it at
    }

    companion object {
        /** A block being placed. The data is the block's network ID, so it makes the block's own sound. */
        const val PLACE = "place"

        /** A button popping back out. The data is the button's network ID, for the click of what it's made of. */
        const val BUTTON_CLICK_OFF = "button.click_off"

        /** An item wearing out and breaking. The data is -1, for no block. */
        const val BREAK = "break"
    }
}

/** Reads a sound the client's player made, leaving out whether everyone should hear it, which is for the server to say. */
internal fun decodeLevelSoundEvent(buffer: ByteBuf): LevelSoundEvent {
    val sound = buffer.readString()
    val position = buffer.readVec3()
    val data = buffer.readVarInt()
    val entityType = buffer.readString()
    val baby = buffer.readBoolean()
    buffer.readBoolean() // Whether everyone hears it, wherever they are
    val entityId = buffer.readLongLE()
    if (buffer.isReadable && buffer.readBoolean()) buffer.readVec3() // A separate point to fire it at
    return LevelSoundEvent(sound, position, data, entityType, baby, entityId)
}

internal fun decodeInventoryTransaction(buffer: ByteBuf, version: GameVersion): InventoryTransaction {
    val legacyRequestId = buffer.readLegacyRequest()
    val type = buffer.readVarUInt()
    val actions = buffer.readActions()
    return InventoryTransaction(
        type, actions, if (type == InventoryTransaction.USE_ITEM) buffer.readItemUse() else null, legacyRequestId,
        useOnEntity = if (type == InventoryTransaction.USE_ITEM_ON_ENTITY) buffer.readItemUseOnEntity(version) else null,
    )
}

internal fun decodePlayerAction(buffer: ByteBuf): PlayerAction {
    val runtimeId = buffer.readVarULong()
    val action = buffer.readVarInt()
    val position = buffer.readBlockPos()
    buffer.readBlockPos() // Where it resulted, which none of the actions Kore reads use
    return PlayerAction(runtimeId, action, position, face = buffer.readVarInt())
}

internal fun decodeMobEquipment(buffer: ByteBuf): MobEquipment {
    val runtimeId = buffer.readVarULong()
    val heldItem = buffer.readClientItem().runtimeId
    buffer.readUnsignedByte() // The slot, which for a player is the same hotbar slot as the next
    return MobEquipment(runtimeId, heldItem, selectedSlot = buffer.readUnsignedByte().toInt(), windowId = buffer.readUnsignedByte().toInt())
}

/** An item use as `PlayerAuthInput` carries it: a transaction without the type, which can only be this one. */
internal fun ByteBuf.readPackedItemUse(): InventoryTransaction {
    val legacyRequestId = readLegacyRequest()
    val actions = readActions()
    return InventoryTransaction(InventoryTransaction.USE_ITEM, actions, readItemUse(), legacyRequestId)
}

internal fun ByteBuf.readBlockAction() = BlockAction(readVarInt(), readBlockPos(), readVarInt())

/**
 * Reads a legacy request ID, and returns it, skipping the slots it asks to
 * have resent: Kore resends the slots the transaction changes instead.
 */
private fun ByteBuf.readLegacyRequest(): Int {
    val id = readVarInt()
    if (readBoolean()) readList { readUnsignedByte(); readByteArray() } // A container, then its slots
    return id
}

/** Reads the slot changes the client predicts a transaction makes. */
private fun ByteBuf.readActions() = readList {
    val source = readVarUInt()
    val windowId = if (readBoolean()) readByte().toInt() else null
    if (readBoolean()) readVarUInt() // Flags, for items from the world
    InventoryAction(source, windowId, slot = readVarUInt(), before = readClientItem(), after = readClientItem())
}

private fun ByteBuf.readItemUseOnEntity(version: GameVersion): ItemUseOnEntity {
    val target = readVarULong()
    val action = readVarInt()
    val hotbarSlot = readVarInt()
    // Unlike a use on a block, it doesn't say which hand holds the item before 1.26.60.
    if (version >= GameVersion.V1_26_60) readUnsignedByte() // The hand holding the item
    val heldItem = readClientItem().runtimeId
    readVec3() // Where the player was
    readVec3() // Where they hit it
    return ItemUseOnEntity(target, action, hotbarSlot, heldItem)
}

private fun ByteBuf.readItemUse(): ItemUse {
    val action = readVarInt().let { ItemUseAction.entries.getOrNull(it) ?: throw PacketException("unknown item use action $it") }
    val repeated = readUnsignedByte().toInt() == HELD_DOWN
    val position = readBlockPos()
    val face = readUnsignedByte().toInt()
    val hotbarSlot = readVarInt()
    readUnsignedByte() // The hand holding the item
    val heldItem = readClientItem().runtimeId
    readVec3() // Where the player was
    val clickPosition = readVec3()
    val clickedBlock = readVarUInt()
    val predictedToWork = readUnsignedByte().toInt() == PREDICTED_SUCCESS
    readUnsignedByte() // Whether the item was cooling down
    return ItemUse(action, position, face, hotbarSlot, heldItem, clickPosition, clickedBlock, predictedToWork, repeated)
}

/**
 * Reads an item as the client sends it outside item stack requests. These
 * items have a fixed 16-bit ID and may carry a stack ID, unlike those the
 * server sends. The aux value and block are read past, and of the extras,
 * only how worn the item is is kept.
 */
private fun ByteBuf.readClientItem(): ClientItem {
    val id = readShortLE().toInt()
    val count = readUnsignedShortLE()
    readVarUInt() // Aux value
    val stackId = if (readBoolean()) readVarInt() else null
    readVarUInt() // Block network ID
    return ClientItem(id, count, stackId, damage = damageIn(readByteArray()))
}

/**
 * How worn an item is, by the NBT its [extras] start with, if they have
 * any: a marker of -1 and the format's version come first. The blocks the
 * item may be placed on and break come after, and are left.
 */
private fun damageIn(extras: ByteArray): Int {
    val buffer = Unpooled.wrappedBuffer(extras)
    if (buffer.readableBytes() < 3 || buffer.readShortLE().toInt() != -1) return 0
    buffer.readByte()
    val nbt = try {
        NbtUtils.createReaderLE(ByteBufInputStream(buffer)).readTag() as? NbtMap
    } catch (e: IOException) {
        throw PacketException("an item's NBT is malformed")
    }
    return nbt?.getInt(DAMAGE, 0) ?: 0
}
