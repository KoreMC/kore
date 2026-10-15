package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import org.koremc.registry.CreativeInventory
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.levelOf
import kotlin.random.Random

/** The enchantments that change what blocks drop, by the game's numbers for them. */
internal const val SILK_TOUCH = 16
internal const val FORTUNE = 18

private const val SHEARS = "minecraft:shears"

/** Blocks that drop another item than themselves, one of it. */
private val DROPS_INSTEAD = buildMap {
    val dirt = "minecraft:dirt"
    for (block in listOf("grass_block", "podzol", "mycelium", "farmland", "grass_path")) put("minecraft:$block", dirt)
    put("minecraft:stone", "minecraft:cobblestone")
    put("minecraft:deepslate", "minecraft:cobbled_deepslate")
    put("minecraft:ender_chest", "minecraft:obsidian")
    for (kind in FurnaceKind.entries) put(kind.litBlock, kind.block)
    put(UNLIT_REDSTONE_TORCH, REDSTONE_TORCH)
    put(LIT_REDSTONE_LAMP, REDSTONE_LAMP)
}

/**
 * Ores, by name, with what they drop, how many, from the fewest to the
 * most, and how Fortune adds to it: by times over, as for most ores, or by
 * one more for each level, as for redstone.
 */
private class Ore(val drop: String, val fewest: Int, val most: Int, val timesOver: Boolean = true)

private val ORES = buildMap {
    fun ore(name: String, ore: Ore) {
        put("minecraft:${name}_ore", ore)
        put("minecraft:deepslate_${name}_ore", ore)
    }
    ore("coal", Ore("minecraft:coal", 1, 1))
    ore("iron", Ore("minecraft:raw_iron", 1, 1))
    ore("copper", Ore("minecraft:raw_copper", 2, 5))
    ore("gold", Ore("minecraft:raw_gold", 1, 1))
    ore("diamond", Ore("minecraft:diamond", 1, 1))
    ore("emerald", Ore("minecraft:emerald", 1, 1))
    ore("lapis", Ore("minecraft:lapis_lazuli", 4, 9))
    ore("redstone", Ore("minecraft:redstone", 4, 5, timesOver = false))
    put("minecraft:lit_redstone_ore", Ore("minecraft:redstone", 4, 5, timesOver = false))
    put("minecraft:lit_deepslate_redstone_ore", Ore("minecraft:redstone", 4, 5, timesOver = false))
    put("minecraft:quartz_ore", Ore("minecraft:quartz", 1, 1))
    put("minecraft:nether_gold_ore", Ore("minecraft:gold_nugget", 2, 6))
}

/** The experience ores give when broken for what they drop, not taken whole with Silk Touch, from the least to the most. */
private val ORE_EXPERIENCE = mapOf(
    "coal" to 0..2, "diamond" to 3..7, "emerald" to 3..7, "lapis" to 2..5, "redstone" to 1..5,
).flatMap { (ore, xp) -> listOf("minecraft:${ore}_ore" to xp, "minecraft:deepslate_${ore}_ore" to xp, "minecraft:lit_${ore}_ore" to xp, "minecraft:lit_deepslate_${ore}_ore" to xp) }
    .toMap() + mapOf("minecraft:quartz_ore" to 2..5, "minecraft:nether_gold_ore" to 0..1)

/** Lit redstone ore, which Silk Touch takes as it is unlit. */
private val UNLIT_ORE = mapOf("minecraft:lit_redstone_ore" to "minecraft:redstone_ore", "minecraft:lit_deepslate_redstone_ore" to "minecraft:deepslate_redstone_ore")

/** Blocks that drop nothing, not even themselves, unless Silk Touch or shears say otherwise. */
private val DROPS_NOTHING = setOf(
    "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:short_grass", "minecraft:tall_grass",
    "minecraft:fern", "minecraft:large_fern", "minecraft:seagrass", "minecraft:web", "minecraft:fire", "minecraft:soul_fire",
    "minecraft:cake", "minecraft:cave_vines", "minecraft:budding_amethyst",
)

/** Blocks that only shears take whole, and nothing else does, Silk Touch included. */
private val SHEARS_ONLY = setOf(
    "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern", "minecraft:seagrass", "minecraft:deadbush",
    "minecraft:vine", "minecraft:glow_lichen", "minecraft:hanging_roots", "minecraft:nether_sprouts", "minecraft:twisting_vines",
    "minecraft:weeping_vines", "minecraft:pale_hanging_moss",
)

/** Blocks Silk Touch can't take whole, which drop as they would without it, or nothing. */
private val NO_SILK_TOUCH = SHEARS_ONLY + setOf(
    "minecraft:farmland", "minecraft:grass_path", "minecraft:fire", "minecraft:soul_fire", "minecraft:cake", "minecraft:cave_vines",
    "minecraft:budding_amethyst",
)

/** What each kind of leaves drops besides sticks, with its chance, if anything. */
private val LEAF_SAPLINGS = mapOf(
    "oak" to "oak_sapling", "spruce" to "spruce_sapling", "birch" to "birch_sapling", "jungle" to "jungle_sapling",
    "acacia" to "acacia_sapling", "dark_oak" to "dark_oak_sapling", "cherry" to "cherry_sapling", "pale_oak" to "pale_oak_sapling",
    "azalea" to "azalea",
).entries.associate { (kind, sapling) -> "minecraft:${kind}_leaves" to "minecraft:$sapling" } + ("minecraft:azalea_leaves_flowered" to "minecraft:flowering_azalea") +
    listOf("orange", "yellow", "red").associate { "minecraft:${it}_poplar_leaves" to "minecraft:poplar_sapling" }

// The chance of each of what leaves drop, by Fortune's level: a sapling, a jungle sapling, sticks, and an apple from oak and dark oak.
private val SAPLING_CHANCE = listOf(1 / 20.0, 1 / 16.0, 1 / 12.0, 1 / 10.0)
private val JUNGLE_SAPLING_CHANCE = listOf(1 / 40.0, 1 / 36.0, 1 / 32.0, 1 / 24.0)
private val STICK_CHANCE = listOf(1 / 50.0, 1 / 45.0, 1 / 40.0, 1 / 30.0)
private val APPLE_CHANCE = listOf(1 / 200.0, 1 / 180.0, 1 / 160.0, 1 / 120.0)

/** The chance gravel drops flint, rather than itself, by Fortune's level. */
private val FLINT_CHANCE = listOf(0.1, 1 / 7.0, 0.25, 1.0)

/** The chance grass and ferns drop a wheat seed. */
private const val SEED_CHANCE = 1 / 8.0

/** The chance grown potatoes drop a poisonous one too, and the most nether wart drops, Fortune and all. */
private const val POISONOUS_CHANCE = 0.02
private const val MOST_WART = 7

internal const val SWEET_BERRIES = "minecraft:sweet_berries"

/**
 * Blocks that break into more of something else, by name: what, from the
 * fewest to the most, and the most Fortune can make it, if it adds one for
 * each of its levels.
 */
private class Pieces(val drop: String, val fewest: Int, val most: Int, val withFortune: Int? = null)

private val PIECES = mapOf(
    "minecraft:glowstone" to Pieces("minecraft:glowstone_dust", 2, 4, withFortune = 4),
    "minecraft:sea_lantern" to Pieces("minecraft:prismarine_crystals", 2, 3, withFortune = 5),
    "minecraft:melon_block" to Pieces("minecraft:melon_slice", 3, 7, withFortune = 9),
    "minecraft:clay" to Pieces("minecraft:clay_ball", 4, 4),
    "minecraft:bookshelf" to Pieces("minecraft:book", 3, 3),
    "minecraft:snow" to Pieces("minecraft:snowball", 4, 4),
    "minecraft:deadbush" to Pieces("minecraft:stick", 0, 2),
)

/**
 * What blocks drop when players break them. Most drop their own item,
 * which shares the block's name and, in the creative inventory, carries the
 * block state it places. Some drop another, like grass dropping dirt, some
 * drop more of something, like ores and glowstone, and some drop nothing:
 * glass, ice and plain grasses. Leaves, gravel and grass drop things by
 * chance. A double slab drops two slabs, and a door, bed or tall plant
 * drops once. Blocks placed by an item of another name, like crops and
 * repeaters, drop that item, and beds and banners drop in the colour they
 * kept. What an item frame, flower pot or jukebox holds drops with it, as
 * does a ready composter's bone meal.
 *
 * The tool a player breaks a block with counts: Silk Touch takes a block
 * whole, as it is, shears take leaves, grasses and vines whole, and
 * Fortune makes more of what ores, glowstone and the like drop, and
 * chance drops likelier. Chance comes from [random].
 */
class Drops(items: List<ItemDefinition>, creative: CreativeInventory, blocks: BlockPalette, private val random: Random = Random.Default) {
    private val stacks = ItemStacks(items, creative, blocks)

    /**
     * What [state] drops, given the data it kept, [blockEntity], which a
     * player holding something that harvests it gets, broken with [tool]. A
     * shulker box keeps what it held, its [contents], in its item.
     */
    fun of(state: BlockState, blockEntity: NbtMap? = null, contents: List<ItemStack?> = emptyList(), tool: ItemStack? = null): List<ItemStack> {
        if (state.properties[UPPER_BLOCK] == 1.toByte() || state.properties[HEAD_PIECE] == 1.toByte()) return emptyList()
        val name = state.name
        val fortune = tool?.levelOf(FORTUNE)?.coerceIn(0, 3) ?: 0
        val whole = when {
            tool?.item?.name == SHEARS && (name in SHEARS_ONLY || name in LEAF_SAPLINGS || name == "minecraft:mangrove_leaves" || name == "minecraft:web") -> true
            tool != null && tool.levelOf(SILK_TOUCH) > 0 && name !in NO_SILK_TOUCH && !state.hasCandle -> true
            else -> false
        }
        val dropped = when {
            whole -> listOfNotNull(stacks.of(UNLIT_ORE[name] ?: itemPlacing(name) ?: name))
            else -> grown(state, fortune) ?: byChance(name, fortune) ?: own(state, blockEntity, contents)
        }
        return dropped + held(state, blockEntity)
    }

    /**
     * The experience breaking [state] with [tool] gives, besides what it
     * drops: ores give some, unless Silk Touch takes them whole.
     */
    fun experienceOf(state: BlockState, tool: ItemStack?): Int {
        if (tool != null && tool.levelOf(SILK_TOUCH) > 0) return 0
        return ORE_EXPERIENCE[state.name]?.let { random.nextInt(it.first, it.last + 1) } ?: 0
    }

    /** What [state] drops broken as it is, without a tool that changes that. A cake with a candle drops only the candle. */
    private fun own(state: BlockState, blockEntity: NbtMap?, contents: List<ItemStack?>): List<ItemStack> {
        if (dropsNothing(state.name)) return emptyList()
        val (name, count) = (DROPS_INSTEAD[state.name] ?: state.candle)?.let { it to 1 } ?: singleSlabOf(state.name)?.let { it to 2 }
            ?: ((itemPlacing(state.name) ?: state.name) to 1)
        // Beds and banners keep their colour in their block entity, as their items do in their aux value.
        val stack = stacks.of(name, count, aux = keptColour(blockEntity) ?: 0) ?: return emptyList()
        val held = contents.withIndex().mapNotNull { (slot, item) -> item?.let { slot to it } }
        if (state.storage == Storage.SHULKER_BOX && held.isNotEmpty()) return listOf(stack.copy(nbt = ItemStacks.holding(held)))
        return listOf(stack)
    }

    /** What a block that drops by chance, or more of something, drops broken with [fortune], or null for any other. */
    private fun byChance(name: String, fortune: Int): List<ItemStack>? {
        ORES[name]?.let { ore ->
            var count = random.nextInt(ore.fewest, ore.most + 1)
            count = if (ore.timesOver) count * (1 + maxOf(0, random.nextInt(fortune + 2) - 1)) else count + random.nextInt(fortune + 1)
            return listOfNotNull(stacks.of(ore.drop, count))
        }
        PIECES[name]?.let { pieces ->
            var count = random.nextInt(pieces.fewest, pieces.most + 1)
            pieces.withFortune?.let { cap -> count = minOf(cap, count + random.nextInt(fortune + 1)) }
            return listOfNotNull(stacks.of(pieces.drop, count).takeIf { count > 0 })
        }
        return when {
            name == "minecraft:gravel" -> listOfNotNull(stacks.of(if (random.nextDouble() < FLINT_CHANCE[fortune]) "minecraft:flint" else name))
            name.endsWith("_leaves") || name == "minecraft:azalea_leaves_flowered" -> buildList {
                LEAF_SAPLINGS[name]?.let { sapling ->
                    val chance = if (name == "minecraft:jungle_leaves") JUNGLE_SAPLING_CHANCE else SAPLING_CHANCE
                    if (random.nextDouble() < chance[fortune]) stacks.of(sapling)?.let(::add)
                }
                if (random.nextDouble() < STICK_CHANCE[fortune]) stacks.of("minecraft:stick", random.nextInt(1, 3))?.let(::add)
                if ((name == "minecraft:oak_leaves" || name == "minecraft:dark_oak_leaves") && random.nextDouble() < APPLE_CHANCE[fortune]) {
                    stacks.of("minecraft:apple")?.let(::add)
                }
            }
            // Up to two of the mushroom a huge mushroom's cap is of, most of the time none, and nothing from its stem.
            name == "minecraft:brown_mushroom_block" || name == "minecraft:red_mushroom_block" -> {
                val count = random.nextInt(-6, 3)
                if (count > 0) listOfNotNull(stacks.of(name.removeSuffix("_block"), count)) else emptyList()
            }
            name == "minecraft:mushroom_stem" -> emptyList()
            name in setOf("minecraft:short_grass", "minecraft:fern", "minecraft:tall_grass", "minecraft:large_fern") ->
                if (random.nextDouble() < SEED_CHANCE) listOfNotNull(stacks.of("minecraft:wheat_seeds", 1 + random.nextInt(fortune * 2 + 1))) else emptyList()
            else -> null
        }
    }

    /**
     * What a plant that grows drops, broken with [fortune], by how grown it
     * is, [state], or null for any other block. Grown wheat and beetroot
     * drop their crop and up to three seeds, and grown carrots and potatoes
     * one to four of themselves, potatoes now and then a poisonous one, all
     * of them more with Fortune; younger, each drops just its seed. Grown
     * nether wart drops two to four, and a grown cocoa pod two or three.
     * Sweet berry bushes drop the berries on them, and a pitcher crop its pod,
     * or the plant once grown.
     */
    private fun grown(state: BlockState, fortune: Int): List<ItemStack>? {
        fun ripe(stage: String, most: Int) = (state.properties[stage] as? Int ?: 0) >= most
        // Each of three, and one more for each level of Fortune, comes up four times in seven.
        fun extra() = (0 until 3 + fortune).count { random.nextInt(7) < 4 }
        return when (state.name) {
            "minecraft:wheat", "minecraft:beetroot" -> {
                val seeds = if (state.name == "minecraft:wheat") "minecraft:wheat_seeds" else "minecraft:beetroot_seeds"
                if (!ripe(GROWTH, MAX_CROP_GROWTH)) return listOfNotNull(stacks.of(seeds))
                listOfNotNull(stacks.of(state.name), extra().takeIf { it > 0 }?.let { stacks.of(seeds, it) })
            }
            "minecraft:carrots", "minecraft:potatoes" -> {
                val crop = itemPlacing(state.name) ?: return null
                if (!ripe(GROWTH, MAX_CROP_GROWTH)) return listOfNotNull(stacks.of(crop))
                val poisoned = state.name == "minecraft:potatoes" && random.nextDouble() < POISONOUS_CHANCE
                listOfNotNull(stacks.of(crop, 1 + random.nextInt(fortune + 1) + extra()), if (poisoned) stacks.of("minecraft:poisonous_potato") else null)
            }
            NETHER_WART -> listOfNotNull(stacks.of(NETHER_WART, if (ripe(AGE, MAX_WART_AGE)) minOf(MOST_WART, random.nextInt(2, 5) + random.nextInt(fortune + 1)) else 1))
            COCOA -> listOfNotNull(stacks.of("minecraft:cocoa_beans", if (ripe(AGE, MAX_COCOA_AGE)) random.nextInt(2, 4) else 1))
            SWEET_BERRY_BUSH -> berriesOn(state)?.let { count -> listOfNotNull(stacks.of(SWEET_BERRIES, count + random.nextInt(fortune + 1))) } ?: emptyList()
            PITCHER_CROP -> listOfNotNull(stacks.of(if (ripe(GROWTH, MAX_PITCHER_GROWTH)) "minecraft:pitcher_plant" else "minecraft:pitcher_pod"))
            else -> null
        }
    }

    /** How many berries the sweet berry bush [state] has on it: two or three full, one or two with some, or null with none. */
    private fun berriesOn(state: BlockState): Int? = when (state.properties[GROWTH] as? Int ?: 0) {
        MAX_BERRY_GROWTH -> random.nextInt(2, 4)
        MAX_BERRY_GROWTH - 1 -> random.nextInt(1, 3)
        else -> null
    }

    /**
     * What an item frame, flower pot or jukebox, [blockEntity], held, which
     * drops along with it, and the bone meal in a ready composter, [state].
     */
    private fun held(state: BlockState, blockEntity: NbtMap?): List<ItemStack> {
        val compost = if (state.isComposter && state.properties["composter_fill_level"] == READY) stacks.of(BONE_MEAL) else null
        blockEntity ?: return listOfNotNull(compost)
        val inFrame = blockEntity.getCompound("Item", null)?.let(stacks::fromNbt)?.second
        val inPot = blockEntity.getCompound("PlantBlock", null)?.getString("name", null)?.let { stacks.of(itemPlacing(it) ?: it) }
        val inJukebox = blockEntity.getCompound("RecordItem", null)?.let(stacks::fromNbt)?.second
        return listOfNotNull(inFrame, inPot, inJukebox, compost)
    }

    private fun dropsNothing(name: String) = name in DROPS_NOTHING || name.endsWith("_leaves") ||
        ((name.endsWith("glass") || name.endsWith("glass_pane")) && name != "minecraft:tinted_glass")
}

/** The slab two of make a double slab called [name], or null if it isn't one. */
internal fun singleSlabOf(name: String): String? = when {
    name.endsWith("double_cut_copper_slab") -> name.replace("double_cut_copper_slab", "cut_copper_slab")
    name.endsWith("_double_slab") -> name.removeSuffix("_double_slab") + "_slab"
    else -> null
}

/** The colour a bed or banner kept in its block entity, [blockEntity], as its item keeps it in its aux value, or null for any other. */
internal fun keptColour(blockEntity: NbtMap?): Int? = when {
    blockEntity == null -> null
    blockEntity.containsKey("color") -> blockEntity.getByte("color").toInt()
    blockEntity.containsKey("Base") -> blockEntity.getInt("Base")
    else -> null
}
