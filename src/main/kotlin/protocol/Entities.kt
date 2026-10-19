package org.koremc.protocol

import io.netty.buffer.ByteBuf
import org.koremc.registry.ItemStack
import org.koremc.world.Vec3

private const val ADD_ACTOR = 13
private const val REMOVE_ACTOR = 14
private const val ADD_ITEM_ACTOR = 15
private const val TAKE_ITEM_ACTOR = 17
private const val MOVE_ACTOR_ABSOLUTE = 18
internal const val ACTOR_EVENT = 27
private const val SET_ACTOR_MOTION = 40
private const val INVENTORY_SLOT = 50

/** The window and container ID of a player's own items: the hotbar and the rest of the inventory. */
const val INVENTORY_WINDOW = 0

/** The windows of the player's offhand and armour. */
const val OFFHAND_WINDOW = 119
const val ARMOUR_WINDOW = 120

/** An item entity's size, a quarter of a block each way. */
private const val ITEM_SIZE = 0.25f

/** The actor data every item entity has: it falls, and it's an item's size. */
private val ITEM_DATA = listOf(
    flagsItem(setOf(ActorFlag.HAS_GRAVITY, ActorFlag.CAN_CLIMB)),
    FloatItem(ActorDataId.WIDTH, ITEM_SIZE),
    FloatItem(ActorDataId.HEIGHT, ITEM_SIZE),
)

/**
 * An item lying in the world, appearing to the client: the entity
 * [entityId], with the [stack] it holds, at [position] and moving at
 * [velocity] blocks a tick. Kore gives entities the same unique and
 * runtime IDs.
 */
data class AddItemActor(val entityId: Long, val stack: ItemStack, val position: Vec3, val velocity: Vec3) : ServerPacket {
    override val id get() = ADD_ITEM_ACTOR

    override fun encodeBody(out: ByteBuf) {
        out.writeVarLong(entityId)
        out.writeVarULong(entityId)
        out.writeItemInstance(stack)
        out.writeVec3(position)
        out.writeVec3(velocity)
        out.writeActorData(ITEM_DATA)
        out.writeBoolean(false) // Whether it came from fishing
    }
}

/**
 * An experience orb appearing to the client: the entity [entityId], worth
 * [value], at [position] and moving at [velocity] blocks a tick.
 */
data class AddExperienceOrb(val entityId: Long, val position: Vec3, val velocity: Vec3, val value: Int) : VersionedPacket {
    override val id get() = ADD_ACTOR

    override fun encodeBody(out: ByteBuf, version: GameVersion) {
        out.writeVarLong(entityId)
        out.writeVarULong(entityId)
        out.writeString("minecraft:xp_orb")
        out.writeVec3(position)
        out.writeVec3(velocity)
        out.writeFloatLE(0f).writeFloatLE(0f) // Pitch and yaw
        out.writeFloatLE(0f) // Head yaw
        out.writeFloatLE(0f) // Body yaw
        out.writeVarUInt(0) // Attributes
        out.writeActorData(listOf(flagsItem(setOf(ActorFlag.HAS_GRAVITY)), IntItem(ActorDataId.VALUE, value), FloatItem(ActorDataId.WIDTH, ITEM_SIZE), FloatItem(ActorDataId.HEIGHT, ITEM_SIZE)))
        out.writeVarUInt(0) // Int properties
        out.writeVarUInt(0) // Float properties
        out.writeRidesNothing(version)
    }
}

/**
 * A falling block appearing to the client: the entity [entityId], drawn
 * as the block [block], by its network ID, [size] each way, at [position]
 * and moving at [velocity] blocks a tick.
 */
data class AddFallingBlock(val entityId: Long, val block: Int, val position: Vec3, val velocity: Vec3, val size: Float) : VersionedPacket {
    override val id get() = ADD_ACTOR

    override fun encodeBody(out: ByteBuf, version: GameVersion) {
        out.writeVarLong(entityId)
        out.writeVarULong(entityId)
        out.writeString("minecraft:falling_block")
        out.writeVec3(position)
        out.writeVec3(velocity)
        out.writeFloatLE(0f).writeFloatLE(0f) // Pitch and yaw
        out.writeFloatLE(0f) // Head yaw
        out.writeFloatLE(0f) // Body yaw
        out.writeVarUInt(0) // Attributes
        out.writeActorData(
            listOf(
                // Falling, but not colliding: the server says where it is, and it starts inside the block it was.
                flagsItem(setOf(ActorFlag.HAS_GRAVITY)),
                IntItem(ActorDataId.VARIANT, block),
                FloatItem(ActorDataId.WIDTH, size),
                FloatItem(ActorDataId.HEIGHT, size),
            ),
        )
        out.writeVarUInt(0) // Int properties
        out.writeVarUInt(0) // Float properties
        out.writeRidesNothing(version)
    }
}

/** A lightning bolt striking, as the entity [entityId], which the client draws and flashes from [position] up. */
data class AddLightning(val entityId: Long, val position: Vec3) : VersionedPacket {
    override val id get() = ADD_ACTOR

    override fun encodeBody(out: ByteBuf, version: GameVersion) {
        out.writeVarLong(entityId)
        out.writeVarULong(entityId)
        out.writeString(LIGHTNING_BOLT)
        out.writeVec3(position)
        out.writeVec3(Vec3(0f, 0f, 0f))
        out.writeFloatLE(0f).writeFloatLE(0f) // Pitch and yaw
        out.writeFloatLE(0f) // Head yaw
        out.writeFloatLE(0f) // Body yaw
        out.writeVarUInt(0) // Attributes
        out.writeActorData(listOf(flagsItem(emptySet())))
        out.writeVarUInt(0) // Int properties
        out.writeVarUInt(0) // Float properties
        out.writeRidesNothing(version)
    }
}

/** That an entity appearing rides nothing: no entity, nor, from 1.26.60, a block, as someone sitting on one would. */
internal fun ByteBuf.writeRidesNothing(version: GameVersion) {
    writeVarUInt(0) // Entities it rides or is ridden by
    if (version >= GameVersion.V1_26_60) writeBoolean(false) // A block it rides
}

/** The game's name for lightning bolts, which the sound of their thunder names too. */
const val LIGHTNING_BOLT = "minecraft:lightning_bolt"

/** An entity gone from the client's world, by its unique ID. */
data class RemoveActor(val entityId: Long) : ServerPacket {
    override val id get() = REMOVE_ACTOR

    override fun encodeBody(out: ByteBuf) {
        out.writeVarLong(entityId)
    }
}

/** The item entity [itemId] flying to the entity [takerId] that picked it up. The client shows it, then the item goes. */
data class TakeItemActor(val itemId: Long, val takerId: Long) : ServerPacket {
    override val id get() = TAKE_ITEM_ACTOR

    override fun encodeBody(out: ByteBuf) {
        out.writeVarULong(itemId)
        out.writeVarULong(takerId)
    }
}

/**
 * An entity at a new [position], which the client moves it to smoothly
 * over a few ticks, or, for a [teleport], puts it at at once. The
 * position is the client's for the entity, which for an item is an
 * eighth of a block above where it lies.
 */
data class MoveActorAbsolute(val entityId: Long, val position: Vec3, val onGround: Boolean, val teleport: Boolean = false) : ServerPacket {
    override val id get() = MOVE_ACTOR_ABSOLUTE

    override fun encodeBody(out: ByteBuf) {
        out.writeVarULong(entityId)
        out.writeByte((if (onGround) ON_GROUND else 0) or (if (teleport) TELEPORT else 0))
        out.writeVec3(position)
        out.writeZero(3) // Pitch, yaw and head yaw, a byte each, which an item doesn't have
    }

    private companion object {
        const val ON_GROUND = 0b1
        const val TELEPORT = 0b10
    }
}

/**
 * The entity [entityId] set moving by [motion], in blocks a tick, as a
 * knock or a push sets it going, which the client carries on from as it
 * moves the entity, its own player too.
 */
data class SetActorMotion(val entityId: Long, val motion: Vec3) : ServerPacket {
    override val id get() = SET_ACTOR_MOTION

    override fun encodeBody(out: ByteBuf) {
        out.writeVarULong(entityId)
        out.writeVec3(motion)
        out.writeVarULong(0) // The server's tick, which only matters to corrections of where the client's player is
    }
}

/**
 * Something happening to the entity [entityId] that the client shows, like
 * [UPDATE_STACK_SIZE], with [data] about it. The client sends some of its
 * own player's, like [EATING_ITEM], for the server to show everyone, its
 * own player too.
 */
data class ActorEvent(val entityId: Long, val event: Int, val data: Int) : ServerPacket, ClientPacket {
    override val id get() = ACTOR_EVENT

    override fun encodeBody(out: ByteBuf) {
        out.writeVarULong(entityId)
        out.writeByte(event)
        out.writeVarInt(data)
        out.writeBoolean(false) // A point to fire at, which only some events aim for
    }

    companion object {
        /** An entity hurt: it flinches, tinted red, and a player's view shakes. */
        const val HURT = 2

        /** An entity dying: it falls over, tinted red. */
        const val DEATH = 3

        /** An entity that died coming back to life, as a player respawning does: it stands up again, whole. */
        const val SPAWN_ALIVE = 18

        /** A Totem of Undying saving an entity: its particles, and for the player it saved, the totem rising before their eyes. */
        const val TALISMAN_ACTIVATE = 65

        /**
         * A player eating or drinking, which the client sends every few
         * ticks while it does: the sound of it, and the bits of what they
         * eat that fly from their mouth. The data is the item's network ID,
         * shifted up 16 bits, and its data value.
         */
        const val EATING_ITEM = 57

        /** An item entity's stack changing size, as when items merge. The data is how many it holds now. */
        const val UPDATE_STACK_SIZE = 69
    }
}

/**
 * A slot of the player's own items holding [stack], with the ID [stackId],
 * or nothing. The client takes it as it is, as it does when the server
 * changes items outside a request of its own, like picking one up. The
 * [window] is the player's inventory, or their offhand or armour.
 */
data class InventorySlot(val slot: Int, val stack: ItemStack?, val stackId: Int, val window: Int = INVENTORY_WINDOW) : ServerPacket {
    override val id get() = INVENTORY_SLOT

    override fun encodeBody(out: ByteBuf) {
        out.writeVarUInt(window)
        out.writeVarUInt(slot)
        out.writeBoolean(false) // A container within the window, which the player's own items don't need
        out.writeBoolean(false) // A storage item, like a bundle, to put it in
        out.writeItemInstance(stack, stackId)
    }
}
