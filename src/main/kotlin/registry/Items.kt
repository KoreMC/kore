package org.koremc.registry

import org.cloudburstmc.nbt.NbtMap

/** How the client builds an item: from its legacy built-in definition, from data-driven components, or neither. Sent as its ordinal. */
enum class ItemVersion { LEGACY, DATA_DRIVEN, NONE }

/**
 * An item as the client's item registry lists it. From then on, items
 * travel by [runtimeId], and that list is the only place the client learns
 * which ID means which item.
 */
data class ItemDefinition(
    /** Its identifier, like `minecraft:stick`. */
    val name: String,
    /** Fits in 16 bits. Items that place blocks have IDs below 256, often negative ones. */
    val runtimeId: Int,
    /** Whether components define the item, rather than the game's code. */
    val componentBased: Boolean,
    val version: ItemVersion,
    /** The item's component data, which most items leave empty. */
    val components: NbtMap = NbtMap.EMPTY,
    /** The most of this item one slot holds. Data-driven items say; for the rest, the game's item data does. */
    val maxStackSize: Int = components.maxStackSize() ?: 64,
    /** What the item is called in English, like `Diamond Sword`. */
    val displayName: String = displayNameOf(name),
    /** How many uses the item has in it, like a tool, or 0 for an item that doesn't wear out. */
    val maxDurability: Int = components.maxDurability() ?: 0,
    /** The items that mend it on an anvil, a quarter of its durability each, like diamonds a diamond sword. */
    val repairItems: Set<String> = emptySet(),
    /** The kinds of enchantment it takes, like `sharp_weapon` or `foot_armor`, as minecraft-data names them. */
    val enchantCategories: Set<String> = emptySet(),
    /** How well it takes to enchanting at a table: the higher, the more and better enchantments. 0 for an item a table can't enchant. */
    val enchantability: Int = components.enchantability() ?: 0,
    /** What eating or drinking it does, or null for an item that's neither food nor a drink. */
    val food: Food? = null,
)

/**
 * What eating a food, or drinking a drink, does, as the game's item data
 * has it: how much it fills the food bar, [nutrition], and how much
 * saturation that brings, as a share of it; whether it can be eaten on a
 * full bar; how many ticks eating it takes; what it leaves in hand, like a
 * bowl; the effects it gives, each with a chance; the effects it cures;
 * anything more it does, like chorus fruit's teleport; and how long before
 * another of its kind can be eaten, if there's a wait.
 */
data class Food(
    val nutrition: Int,
    val saturationModifier: Float,
    val canAlwaysEat: Boolean = false,
    val ticks: Int = DEFAULT_EATING_TICKS,
    val leaves: String? = null,
    val effects: List<FoodEffect> = emptyList(),
    val cures: List<Int> = emptyList(),
    val action: FoodAction? = null,
    val cooldown: ItemCooldown? = null,
) {
    /** Whether it fills the food bar at all: food does, and drinks like potions and milk don't. */
    val feeds get() = nutrition > 0
}

/** An effect a food gives: by the game's number for it, [amplifier] levels above its first, for [seconds], with this [chance] of it. */
data class FoodEffect(val effect: Int, val amplifier: Int, val seconds: Int, val chance: Float)

/**
 * What a food or drink does beyond feeding: chorus fruit teleports,
 * suspicious stew gives the effect of the flower in it, a potion the
 * effects of the potion it is, milk takes every effect away, and an
 * ominous bottle gives Bad Omen at its level.
 */
enum class FoodAction { CHORUS_TELEPORT, SUSPICIOUS_STEW, POTION, MILK, OMINOUS }

/** A wait of [ticks] before an item of the kind the game calls [category], like chorus fruit's `chorusfruit`, can be used again. */
data class ItemCooldown(val category: String, val ticks: Int)

/** How many ticks eating takes, for a food that doesn't say: 1.6 seconds. */
const val DEFAULT_EATING_TICKS = 32

/** The most of an item one slot holds, if its [components][ItemDefinition.components] say. */
internal fun NbtMap.maxStackSize(): Int? =
    getCompound("components", NbtMap.EMPTY).getCompound("item_properties", NbtMap.EMPTY).takeIf { it.containsKey("max_stack_size") }?.getInt("max_stack_size")

/** The component called [name], if these components have it. */
internal fun NbtMap.component(name: String): NbtMap? = getCompound("components", NbtMap.EMPTY).getCompound(name, null)

/** A name for the item called [name] when the game's data has none: `minecraft:black_wool_slab` is `Black Wool Slab`. */
internal fun displayNameOf(name: String) = name.substringAfter(':').split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }

/**
 * Some of an item, as inventories and the creative inventory hold it: how
 * many, which variant (its aux value, like an arrow's potion), the block
 * state it places, and its NBT, like an enchanted book's enchantment.
 */
data class ItemStack(
    val item: ItemDefinition,
    val count: Int = 1,
    val aux: Int = 0,
    val block: BlockState? = null,
    val nbt: NbtMap = NbtMap.EMPTY,
)

/** Whether [other] is the same item, variant, block and NBT as this, so the two could share a slot. */
fun ItemStack.sameKindAs(other: ItemStack) = item == other.item && aux == other.aux && block == other.block && nbt == other.nbt

/** How many uses an item has in it, if its components say. */
internal fun NbtMap.maxDurability(): Int? = (component("minecraft:durability")?.get("max_durability") as? Number)?.toInt()

/** How well an item takes to enchanting, if its components say, which keep it as a byte. */
internal fun NbtMap.enchantability(): Int? = (component("minecraft:enchantable")?.get("value") as? Number)?.toInt()
