package org.koremc.protocol

import io.netty.buffer.ByteBuf

internal const val ITEM_STACK_REQUEST = 147
private const val ITEM_STACK_RESPONSE = 148

/** The containers Kore keeps, by the numbers the client names them with in item stack requests. */
object ContainerName {
    const val ANVIL_INPUT = 0
    const val ANVIL_MATERIAL = 1
    const val SMITHING_TABLE_INPUT = 3
    const val SMITHING_TABLE_MATERIAL = 4
    const val ARMOR = 6
    /** A chest's or an ender chest's items. */
    const val LEVEL_ENTITY = 7
    /** What a player pays a beacon with. */
    const val BEACON_PAYMENT = 8
    /** A brewing stand's reagent, its three bottles, and its blaze powder. */
    const val BREWING_INPUT = 9
    const val BREWING_RESULT = 10
    const val BREWING_FUEL = 11
    const val HOTBAR_AND_INVENTORY = 12
    /** The crafting grid: the inventory's two by two, or a crafting table's three by three. */
    const val CRAFTING_INPUT = 13
    const val ENCHANTING_INPUT = 22
    const val ENCHANTING_MATERIAL = 23
    /** A furnace's fuel, what it cooks and what it made. A blast furnace and a smoker name what they cook their own way. */
    const val FURNACE_FUEL = 24
    const val FURNACE_INGREDIENT = 25
    const val FURNACE_RESULT = 26
    const val HOTBAR = 28
    const val INVENTORY = 29
    const val SHULKER_BOX = 30
    const val OFFHAND = 34
    const val LOOM_INPUT = 41
    const val LOOM_DYE = 42
    const val LOOM_MATERIAL = 43
    const val BLAST_FURNACE_INGREDIENT = 45
    const val SMOKER_INGREDIENT = 46
    const val GRINDSTONE_INPUT = 50
    const val GRINDSTONE_ADDITIONAL = 51
    const val STONECUTTER_INPUT = 53
    /** A cartography table's map, and what goes with it. */
    const val CARTOGRAPHY_INPUT = 55
    const val CARTOGRAPHY_ADDITIONAL = 56
    const val BARREL = 58
    const val CURSOR = 59
    /** Where crafted and creative items appear, before the client moves them somewhere. */
    const val CREATED_OUTPUT = 60
    const val SMITHING_TABLE_TEMPLATE = 61
    const val CRAFTER = 62
}

/** A slot a request names: its container, the slot there, and the ID of the stack the client expects in it. */
data class SlotRef(val container: Int, val slot: Int, val stackId: Int)

/**
 * One step of an item stack request. Actions are numbered by Mojang's
 * current scheme; each also carries its number from before 1.26.40 removed
 * two, which Kore reads past.
 */
sealed interface StackAction {
    /** Moves [count] items from [source] to [destination]. The client's Take and Place are the same move to the server. */
    data class Transfer(val count: Int, val source: SlotRef, val destination: SlotRef) : StackAction

    data class Swap(val source: SlotRef, val destination: SlotRef) : StackAction

    data class Drop(val count: Int, val source: SlotRef) : StackAction

    /** Removes items for good, as the creative inventory's bin does. */
    data class Destroy(val count: Int, val source: SlotRef) : StackAction

    /** Puts a stack of the creative item numbered [creativeId] in the created output slot. */
    data class CraftCreative(val creativeId: Int, val times: Int) : StackAction

    /**
     * Crafts the recipe numbered [recipe], [times] over: from the crafting
     * grid, or with [auto], from wherever the player has the ingredients, as
     * the recipe book does. What it makes goes in the created output.
     */
    data class Craft(val recipe: Int, val times: Int, val auto: Boolean = false) : StackAction

    /** Uses up [count] of what's in [source] for a craft. */
    data class Consume(val count: Int, val source: SlotRef) : StackAction

    /** Puts the craft's result numbered [result] in the created output, for a recipe that makes more than one thing. */
    data class Create(val result: Int) : StackAction

    /**
     * Works the items in an anvil or a cartography table, as the recipe
     * numbered [recipe], naming what it makes by the request's filter string
     * numbered [nameIndex], if it has any.
     */
    data class CraftOptional(val recipe: Int, val nameIndex: Int) : StackAction

    /** Weaves the banner pattern called [pattern] into the banners in a loom, [times] over. */
    data class CraftLoom(val pattern: String, val times: Int) : StackAction

    /** Takes the enchantments off, and mends, what's in a grindstone. */
    data class CraftGrindstone(val recipe: Int, val times: Int) : StackAction

    /**
     * Pays the beacon the player has open for its powers: the effect
     * numbered [primary], and the one numbered [secondary], or 0 for none.
     */
    data class BeaconPayment(val primary: Int, val secondary: Int) : StackAction

    /** An action that only describes what others do, like a craft's results. It changes nothing itself. */
    data class Informational(val type: Int) : StackAction

    /** An action Kore can't carry out yet, like combining elements at a lab table, which fails its request. */
    data class Unsupported(val type: Int) : StackAction
}

/**
 * One request: actions the client has already predicted, which the server
 * accepts or rejects as a whole, and text the player typed for them, like
 * an anvil's new name.
 */
data class StackRequest(val id: Int, val actions: List<StackAction>, val filterStrings: List<String> = emptyList())

/** The client's item stack requests, sent whenever the player moves items around their inventory. */
data class ItemStackRequest(val requests: List<StackRequest>) : ClientPacket

/**
 * A changed slot, as a response reports it: how many items are now there,
 * the stack's ID (0 for none), and what the client can't tell from its own
 * prediction, the stack's anvil name and wear, which it takes as they are
 * here, even for a stack it only moved.
 */
data class SlotChange(val slot: Int, val count: Int, val stackId: Int, val customName: String = "", val damage: Int = 0)

/** The changed slots in one container, under the name the client used for it. */
data class ContainerChange(val container: Int, val slots: List<SlotChange>)

/** The answer to one request: accepted, with every slot it changed, or rejected, which makes the client undo it. */
data class StackResponse(val requestId: Int, val accepted: Boolean, val containers: List<ContainerChange> = emptyList())

data class ItemStackResponse(val responses: List<StackResponse>) : ServerPacket {
    override val id get() = ITEM_STACK_RESPONSE

    override fun encodeBody(out: ByteBuf) {
        out.writeVarUInt(responses.size)
        for (response in responses) {
            out.writeByte(if (response.accepted) 0 else 1)
            out.writeVarInt(response.requestId)
            out.writeBoolean(response.containers.isNotEmpty())
            if (response.containers.isEmpty()) continue
            out.writeVarUInt(response.containers.size)
            for (container in response.containers) {
                out.writeByte(container.container)
                out.writeBoolean(false) // Dynamic container ID, for bundles
                out.writeVarUInt(container.slots.size)
                for (slot in container.slots) {
                    out.writeByte(slot.slot)
                    out.writeByte(slot.slot) // The same slot again, as the hotbar slot
                    out.writeByte(slot.count)
                    out.writeBoolean(slot.stackId > 0)
                    if (slot.stackId > 0) out.writeVarInt(slot.stackId)
                    out.writeString(slot.customName)
                    out.writeBoolean(false) // Filtered custom name
                    out.writeVarInt(slot.damage) // Durability correction
                }
            }
        }
    }
}

/** Reads the requests in [buffer]. */
internal fun decodeItemStackRequest(buffer: ByteBuf, version: GameVersion): ItemStackRequest {
    val count = buffer.readVarUInt()
    if (count > 100) throw PacketException("$count item stack requests in one packet")
    return ItemStackRequest(List(count) { buffer.readStackRequest(version) })
}

/** Reads one request, as a client on [version] lays it out. */
internal fun ByteBuf.readStackRequest(version: GameVersion): StackRequest {
    val id = readVarInt()
    val count = readVarUInt()
    if (count > 100) throw PacketException("$count actions in one item stack request")
    val actions = List(count) { readStackAction(version) }
    val filterStrings = readList { readString() }
    readIntLE() // Why they need filtering
    return StackRequest(id, actions, filterStrings)
}

// Action types, as Mojang numbers them now, in 1.26.60.
private const val TAKE = 0
private const val PLACE = 1
private const val SWAP = 2
private const val DROP = 3
private const val DESTROY = 4
private const val CONSUME = 5
private const val CREATE = 6
private const val LAB_TABLE_COMBINE = 7
private const val BEACON_PAYMENT = 8
private const val MINE_BLOCK = 9
private const val CRAFT_RECIPE = 10
private const val CRAFT_RECIPE_AUTO = 11
private const val CRAFT_CREATIVE = 12
private const val CRAFT_RECIPE_OPTIONAL = 13
private const val CRAFT_REPAIR_AND_DISENCHANT = 14
private const val CRAFT_LOOM = 15
private const val CRAFT_RESERVED = 16
private const val CRAFT_NON_IMPLEMENTED = 17
private const val CRAFT_RESULTS = 18

private fun ByteBuf.readStackAction(version: GameVersion): StackAction {
    // 1.26.50 has no reserved action, so the two after it are one lower.
    val type = readVarUInt().let { if (version < GameVersion.V1_26_60 && it >= CRAFT_RESERVED) it + 1 else it }
    readUnsignedByte() // The type again, as it was numbered before 1.26.40
    return when (type) {
        TAKE, PLACE -> StackAction.Transfer(readUnsignedByte().toInt(), readSlotRef(), readSlotRef())
        SWAP -> StackAction.Swap(readSlotRef(), readSlotRef())
        DROP -> StackAction.Drop(readUnsignedByte().toInt(), readSlotRef()).also { readBoolean() } // Whether to throw it somewhere random
        DESTROY -> StackAction.Destroy(readUnsignedByte().toInt(), readSlotRef())
        CONSUME -> StackAction.Consume(readUnsignedByte().toInt(), readSlotRef())
        CREATE -> StackAction.Create(readUnsignedByte().toInt())
        LAB_TABLE_COMBINE, CRAFT_NON_IMPLEMENTED -> StackAction.Unsupported(type)
        BEACON_PAYMENT -> StackAction.BeaconPayment(readVarInt(), readVarInt())
        MINE_BLOCK -> StackAction.Unsupported(type).also { readVarInt(); readVarInt(); readIntLE() }
        CRAFT_RECIPE -> StackAction.Craft(readVarUInt(), readUnsignedByte().toInt())
        CRAFT_RECIPE_AUTO -> StackAction.Craft(readVarUInt(), readUnsignedByte().toInt(), auto = true).also { readList { skipIngredient() } }
        CRAFT_CREATIVE -> StackAction.CraftCreative(readVarUInt(), readUnsignedByte().toInt())
        CRAFT_RECIPE_OPTIONAL -> StackAction.CraftOptional(readVarUInt(), readIntLE())
        CRAFT_REPAIR_AND_DISENCHANT -> StackAction.CraftGrindstone(readIntLE(), readUnsignedByte().toInt()).also { readVarInt() } // What the client reckons it earns
        CRAFT_LOOM -> StackAction.CraftLoom(readString(), readUnsignedByte().toInt())
        CRAFT_RESERVED -> StackAction.Unsupported(type).also { readString(); readUnsignedByte() } // What it's for, and how many times
        CRAFT_RESULTS -> StackAction.Informational(type).also { readList { skipRequestItem() }; readUnsignedByte() }
        else -> throw PacketException("Unknown item stack request action $type")
    }
}

/** A slot reference: the container's name and dynamic ID, the slot, and the stack ID as a plain little-endian int. */
private fun ByteBuf.readSlotRef(): SlotRef {
    val container = readUnsignedByte().toInt()
    if (readBoolean()) readIntLE() // Dynamic container ID
    return SlotRef(container, readUnsignedByte().toInt(), readIntLE())
}

/**
 * Skips an ingredient as auto-crafting lists them, for the recipe book:
 * its kind, twice, as the variant and the kind before 1.26.40, then the
 * kind's own fields, and how many.
 */
private fun ByteBuf.skipIngredient() {
    val kind = readVarUInt()
    readUnsignedByte() // The kind again
    when (kind) {
        1 -> readString().also { readVarInt() } // An item, by name, and its aux value
        2 -> readString().also { readShortLE() } // A Molang expression, and its version
        3 -> readString() // A tag
    }
    readShortLE() // How many
}

/** Skips an item as the deprecated crafting results describe it: by name, then count, block and a buffer of extras. */
private fun ByteBuf.skipRequestItem() {
    val variant = readVarUInt()
    readUnsignedByte() // The variant again
    if (variant != 0) {
        readString() // Name
        readVarInt() // Aux value
    }
    readShortLE() // Count
    readVarUInt() // Block network ID
    readByteArray() // Extras
}
