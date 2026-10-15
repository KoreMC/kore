package org.koremc.registry

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** A cost that grows with an enchantment's level: [perLevel] for each level, plus [base]. */
data class Cost(val perLevel: Int, val base: Int) {
    fun at(level: Int) = perLevel * level + base
}

/**
 * An enchantment, by the number items keep it under. An enchanting table
 * offers a level of it when the table's cost for an option falls between
 * that level's [minCost] and [maxCost], picking among those it could offer
 * by [weight]. Treasure enchantments, like Mending, never come from a
 * table, and curses stay on through a grindstone.
 */
class Enchantment(
    val id: Int,
    val name: String,
    val maxLevel: Int,
    val minCost: Cost,
    val maxCost: Cost,
    val weight: Int,
    val treasure: Boolean,
    val curse: Boolean,
    /** The enchantments this one can't share an item with, by name. */
    private val exclusive: Set<String>,
    /** The items it goes on, by an anvil or a command. */
    private val supports: (ItemDefinition) -> Boolean,
    /** The items an enchanting table offers it for, which can be fewer. */
    private val primary: (ItemDefinition) -> Boolean = supports,
) {
    /** Whether it goes on [item], by an anvil or a command. A book takes any. */
    fun goesOn(item: ItemDefinition) = item.name in BOOKS || supports(item)

    /** Whether an enchanting table may offer it for [item]. A book takes any but treasure. */
    fun offeredFor(item: ItemDefinition) = !treasure && (item.name == BOOK || primary(item))

    /** Whether it can share an item with [other]: itself, or one neither rules out. */
    fun compatibleWith(other: Enchantment) = other.id == id || (other.name !in exclusive && name !in other.exclusive)

    /**
     * How many levels an anvil charges for each level of it, by how rare it
     * is: 1 for the commonest, up to 8 for the rarest.
     */
    val anvilMultiplier: Int
        get() = when {
            weight >= 10 -> 1
            weight >= 5 -> 2
            weight >= 2 -> 4
            else -> 8
        }

    override fun toString() = name
}

/**
 * Every enchantment, by its number and its name, and the [book] an
 * enchanting table enchants into an [enchantedBook], which a grindstone
 * turns back.
 */
class Enchantments(val all: List<Enchantment>, val book: ItemDefinition? = null, val enchantedBook: ItemDefinition? = null) {
    private val byId = all.associateBy { it.id }
    private val byName = all.associateBy { it.name }

    operator fun get(id: Int): Enchantment? = byId[id]

    /** The enchantment called [name], like `sharpness`. */
    fun named(name: String): Enchantment? = byName[name.removePrefix("minecraft:")]

    companion object {
        val NONE = Enchantments(emptyList())
    }
}

// The enchantments that do something beyond what they cost, by the numbers items keep them under.
const val PROTECTION = 0
const val FIRE_PROTECTION = 1
const val FEATHER_FALLING = 2
const val RESPIRATION = 6
const val SHARPNESS = 9
const val KNOCKBACK = 12
const val FIRE_ASPECT = 13
const val EFFICIENCY = 15
const val UNBREAKING = 17
const val FROST_WALKER = 25
const val MENDING = 26
const val VANISHING = 28

internal const val BOOK = "minecraft:book"
internal const val ENCHANTED_BOOK = "minecraft:enchanted_book"
private val BOOKS = setOf(BOOK, ENCHANTED_BOOK)

/** An entry in minecraft-data's `enchantments.json`. */
@Serializable
private class EnchantmentDetails(
    val id: Int,
    val name: String,
    val maxLevel: Int,
    val minCost: CostDetails,
    val maxCost: CostDetails,
    val treasureOnly: Boolean,
    val curse: Boolean,
    val exclude: List<String>,
    val category: String,
    val weight: Int,
)

@Serializable
private class CostDetails(val a: Int, val b: Int)

/**
 * Reads minecraft-data's `enchantments.json`, which has the enchantments up
 * to Swift Sneak, and adds those the game has had since: the mace's three
 * and the spear's Lunge. Which items each takes comes from the kinds of
 * enchantment the items name in `items.json`.
 */
fun readEnchantments(enchantments: String, items: List<ItemDefinition> = emptyList()): Enchantments {
    val read = json.decodeFromString<List<EnchantmentDetails>>(enchantments).map { details ->
        val (supports, primary) = itemsTaking(details.name, details.category)
        Enchantment(
            details.id, details.name, details.maxLevel, Cost(details.minCost.a, details.minCost.b), Cost(details.maxCost.a, details.maxCost.b),
            details.weight, details.treasureOnly, details.curse, details.exclude.toSet() + NEWER_EXCLUSIONS[details.name].orEmpty(), supports, primary,
        )
    }
    return Enchantments(read + NEWER.filter { newer -> read.none { it.id == newer.id } }, items.find { it.name == BOOK }, items.find { it.name == ENCHANTED_BOOK })
}

/** Which enchantments of the mace's each older one rules out. */
private val NEWER_EXCLUSIONS = mapOf(
    "smite" to setOf("density", "breach"),
    "bane_of_arthropods" to setOf("density", "breach"),
)

/** Items that take enchantments of the [kind] minecraft-data names, like `sharp_weapon`. */
private fun taking(kind: String): (ItemDefinition) -> Boolean = { kind in it.enchantCategories }

/** The enchantments newer than minecraft-data's list, with the game's own numbers, levels, costs and rarities. */
private val NEWER = listOf(
    Enchantment(38, "wind_burst", 3, Cost(9, 6), Cost(9, 56), 2, treasure = true, curse = false, emptySet(), taking("mace")),
    Enchantment(39, "density", 5, Cost(8, -3), Cost(8, 17), 5, treasure = false, curse = false, setOf("breach", "smite", "bane_of_arthropods"), taking("mace")),
    Enchantment(40, "breach", 4, Cost(9, 6), Cost(9, 56), 2, treasure = false, curse = false, setOf("density", "smite", "bane_of_arthropods"), taking("mace")),
    Enchantment(41, "lunge", 3, Cost(8, -3), Cost(8, 17), 5, treasure = false, curse = false, emptySet(), taking("lunge")),
)

/**
 * The items an enchantment of minecraft-data's [category] goes on, and
 * those a table offers it for, by the kinds of enchantment items take.
 * Sharpness and its rivals go on any sword, axe or spear, and Smite and
 * Bane of Arthropods on a mace too. Thorns goes on any armour, but a table
 * only offers it for chestplates. Efficiency goes on shears too, but a
 * table only offers it for tools that mine.
 */
private fun itemsTaking(name: String, category: String): Pair<(ItemDefinition) -> Boolean, (ItemDefinition) -> Boolean> {
    fun same(test: (ItemDefinition) -> Boolean) = test to test
    return when (category) {
        "armor" -> same(taking("armor"))
        "armor_head" -> same(taking("head_armor"))
        "armor_legs" -> same(taking("leg_armor"))
        "armor_feet" -> same(taking("foot_armor"))
        "armor_chest" -> taking("armor") to { item -> "armor" in item.enchantCategories && item.name.endsWith("_chestplate") }
        "weapon" -> when (name) {
            "sharpness" -> same(taking("sharp_weapon"))
            "smite", "bane_of_arthropods" -> taking("weapon") to taking("sharp_weapon")
            "fire_aspect" -> taking("fire_aspect") to taking("melee_weapon")
            else -> same(taking("melee_weapon"))
        }
        "digger" -> if (name == "efficiency") taking("mining") to taking("mining_loot") else same(taking("mining_loot"))
        "breakable" -> same(taking("durability"))
        "bow" -> same(taking("bow"))
        "fishing_rod" -> same(taking("fishing"))
        "trident" -> same(taking("trident"))
        "crossbow" -> same(taking("crossbow"))
        "wearable" -> same(taking("equippable"))
        "vanishable" -> same(taking("vanishing"))
        else -> same { false }
    }
}

/**
 * How well the item called [name], which takes the enchantment
 * [categories] it names, takes to an enchanting table: by what it's made
 * of, the game's numbers for each, which armour and tools of some metals
 * differ in. Items a table can't enchant, like shears, get 0. Data-driven
 * items say for themselves.
 */
internal fun enchantabilityOf(name: String, categories: Set<String>): Int {
    if (name == BOOK) return 1
    if (categories.isEmpty()) return 0
    val item = name.substringAfter(':')
    val armour = "armor" in categories
    return when {
        item in setOf("bow", "crossbow", "fishing_rod", "trident") -> 1
        item == "mace" -> 15
        item == "turtle_helmet" -> 9
        item.startsWith("leather_") -> 15
        item.startsWith("chainmail_") -> 12
        item.startsWith("wooden_") -> 15
        item.startsWith("stone_") -> 5
        item.startsWith("iron_") -> if (armour) 9 else 14
        item.startsWith("golden_") -> if (armour) 25 else 22
        item.startsWith("diamond_") -> 10
        item.startsWith("netherite_") -> 15
        item.startsWith("copper_") -> if (armour) 8 else 13
        else -> 0
    }
}
