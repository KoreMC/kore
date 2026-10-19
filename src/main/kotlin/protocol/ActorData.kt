package org.koremc.protocol

import io.netty.buffer.ByteBuf

private const val SET_ACTOR_DATA = 39

/** Mojang's IDs for the actor data Kore sends. */
enum class ActorDataId(val id: Int) {
    /** A bitmask of [ActorFlag]s, the first 64 of them. */
    FLAGS(0),
    /** Which kind of an actor it is, like the block a falling block is, by its network ID. */
    VARIANT(2),
    /** The name above an actor's head. */
    NAME(4),
    /** Whose an actor is, like whoever threw a projectile, by their entity ID. */
    OWNER(5),
    /** What potion a tipped arrow shows it's tipped with, past the plain potions, as its number and 1, as a byte. */
    CUSTOM_DISPLAY(18),
    /** What potion a tipped arrow's tipped with, by its number, as a short. */
    AUX_VALUE_DATA(36),
    AIR_SUPPLY(7),
    /** How much experience an orb holds, which sets how big it looks. */
    VALUE(15),
    SCALE(38),
    AIR_SUPPLY_MAX(42),
    WIDTH(53),
    HEIGHT(54),
    /** Whether the name shows even when the actor is hidden from view, as a byte. */
    ALWAYS_SHOW_NAME(81),
    /** The rest of the [ActorFlag]s, from bit 64 up. */
    FLAGS_2(92),
    /** The effects whose particles show around an actor, packed into a long. */
    VISIBLE_MOB_EFFECTS(131),
}

/** Bits in an actor's [ActorDataId.FLAGS]: what it's doing, and what the game should do with it. */
enum class ActorFlag(val bit: Int) {
    ON_FIRE(0),
    SNEAKING(1),
    SPRINTING(3),
    /** An arrow shot from a bow drawn all the way, which trails sparks. */
    CRITICAL(13),
    /** Using what it holds, like eating. */
    USING_ITEM(4),
    INVISIBLE(5),
    SHOW_NAME(14),
    ALWAYS_SHOW_NAME(15),
    CAN_CLIMB(19),
    /** Flying with an elytra. */
    GLIDING(32),
    /** Without this, the client counts the player's air down as though they were underwater. */
    BREATHING(35),
    HAS_COLLISION(48),
    /** Without this, the player floats rather than falls. */
    HAS_GRAVITY(49),
    /** A trident thrown with an enchantment, which shines, and one flying back to whoever threw it, by its Loyalty. */
    ENCHANTED(52),
    RETURN_TRIDENT(53),
    SWIMMING(57),
    /** Holding up a shield. */
    BLOCKING(72),
}

/** One item of an actor's synced data: an ID, and a value of one of the types Mojang allows. */
sealed interface DataItem {
    val id: ActorDataId
}

data class ByteItem(override val id: ActorDataId, val value: Byte) : DataItem

data class ShortItem(override val id: ActorDataId, val value: Short) : DataItem

data class IntItem(override val id: ActorDataId, val value: Int) : DataItem

data class FloatItem(override val id: ActorDataId, val value: Float) : DataItem

data class LongItem(override val id: ActorDataId, val value: Long) : DataItem

data class StringItem(override val id: ActorDataId, val value: String) : DataItem

/** The flags item for [flags], of those below bit 64, which it holds. */
fun flagsItem(flags: Set<ActorFlag>) = LongItem(ActorDataId.FLAGS, bitsOf(flags, from = 0))

/** The second flags item for [flags], of those from bit 64 up, which it holds. */
fun secondFlagsItem(flags: Set<ActorFlag>) = LongItem(ActorDataId.FLAGS_2, bitsOf(flags, from = Long.SIZE_BITS))

/** [flags] as bits of a long, of those from bit [from] for as many as a long holds. */
private fun bitsOf(flags: Set<ActorFlag>, from: Int) =
    flags.filter { it.bit - from in 0 until Long.SIZE_BITS }.fold(0L) { bits, flag -> bits or (1L shl (flag.bit - from)) }

/**
 * Changes an actor's synced data. [tick] ties the change to one of the
 * client's input ticks, so it can rewind to apply it; 0 applies it now.
 */
data class SetActorData(val runtimeId: Long, val items: List<DataItem>, val tick: Long = 0) : ServerPacket {
    override val id get() = SET_ACTOR_DATA

    override fun encodeBody(out: ByteBuf) {
        out.writeVarULong(runtimeId)
        out.writeActorData(items)
        out.writeVarUInt(0) // Int properties, which only entities from behaviour packs have
        out.writeVarUInt(0) // Float properties, likewise
        out.writeVarULong(tick)
    }

}

// Each value's type, as Mojang numbers them.
private const val BYTE = 0
private const val SHORT = 1
private const val INT = 2
private const val FLOAT = 3
private const val STRING = 4
private const val LONG = 7

/** [items] as an actor's synced data list, as both `SetActorData` and the packets adding actors carry it. */
internal fun ByteBuf.writeActorData(items: List<DataItem>): ByteBuf {
    writeVarUInt(items.size)
    // In order of ID, which some of the client's handling relies on.
    for (item in items.sortedBy { it.id.id }) {
        writeVarUInt(item.id.id)
        when (item) {
            is ByteItem -> writeType(BYTE).writeByte(item.value.toInt())
            is ShortItem -> writeType(SHORT).writeShortLE(item.value.toInt())
            is IntItem -> writeType(INT).writeVarInt(item.value)
            is FloatItem -> writeType(FLOAT).writeFloatLE(item.value)
            is LongItem -> writeType(LONG).writeVarLong(item.value)
            is StringItem -> writeType(STRING).writeString(item.value)
        }
    }
    return this
}

/** A value's type goes out twice: as a varint, then again as a single byte, which 1.26.40 added. */
private fun ByteBuf.writeType(type: Int): ByteBuf = writeVarUInt(type).writeByte(type)
