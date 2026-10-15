package org.koremc.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.cloudburstmc.nbt.NbtUtils
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.math.roundToInt

private val json = Json { ignoreUnknownKeys = true }

/**
 * The game's own definitions, which clients expect the server to agree with:
 * every item, block state and biome, the creative inventory, recipes and
 * enchantments. They're dumped from the game by CloudburstMC, with how
 * blocks take to mining, how many of each item stack, what items are made
 * of and the enchantments, from PrismarineJS's minecraft-data, and which
 * items each tag takes in from PocketMine's BedrockData, and the colour
 * each block state shows on maps and the light it stops from AllayMC's
 * BedrockData, kept apart from
 * Kore's code in https://github.com/KoreMC/data, and read once, on start.
 */
data class VanillaData(
    val items: List<ItemDefinition>,
    val blocks: BlockPalette,
    val biomes: List<BiomeDefinition>,
    val creative: CreativeInventory,
    /** The blocks defined in data, which go to every client in `StartGame`. */
    val blockDefinitions: List<BlockDefinition>,
    /** How each block takes to mining, by block name. */
    val mining: Map<String, Mining>,
    val recipes: Recipes = Recipes.NONE,
    val enchantments: Enchantments = Enchantments.NONE,
    /** The colour each block state shows on maps, and the light it stops, by network ID. */
    val traits: BlockTraits = BlockTraits.NONE,
) {
    companion object {
        /**
         * Reads the data from [directory], which holds the files from KoreMC/data
         * under their sources' names. A missing file throws [java.nio.file.NoSuchFileException].
         */
        fun load(directory: Path): VanillaData {
            val items = readItems(
                itemStates = directory.resolve("runtime_item_states.json").readText(),
                components = readNbt(directory.resolve("item_components.nbt")),
                details = directory.resolve("items.json").readText(),
            )
            val blocks = readBlockPalette(readNbt(directory.resolve("block_palette.nbt")))
            val blockDefinitions = readBlockDefinitions(readNbt(directory.resolve("data_driven_blocks.nbt")))
            val mining = readMining(directory.resolve("blocks.json").readText()) + readDataDrivenMining(blockDefinitions)
            val creative = readCreativeInventory(directory.resolve("creative_items.json").readText(), items, blocks)
            val tags = readItemTags(directory.resolve("item_tags.json").readText(), items)
            return VanillaData(
                items = items,
                blocks = blocks,
                biomes = readBiomes(directory.resolve("stripped_biome_definitions.json").readText()),
                creative = creative,
                blockDefinitions = blockDefinitions,
                mining = withNewerBlocks(mining, blocks),
                recipes = readRecipes(directory.resolve("recipes.json").readText(), ItemStacks(items, creative, blocks), blocks, tags),
                enchantments = readEnchantments(directory.resolve("enchantments.json").readText(), items),
                traits = readBlockTraits(directory.resolve("block_states.json").readText()),
            )
        }
    }
}

/** An entry in `runtime_item_states.json`. */
@Serializable
private class ItemState(val name: String, val id: Int, val version: Int, val componentBased: Boolean)

/** An entry in minecraft-data's `items.json`, which names items without their namespace. */
@Serializable
private class ItemDetails(
    val name: String,
    val stackSize: Int,
    val displayName: String,
    val maxDurability: Int = 0,
    val repairWith: List<String> = emptyList(),
    val enchantCategories: List<String> = emptyList(),
    /** The item's components, as NBT written out in JSON: each tag a `type` and a `value`. */
    val nbt: JsonObject? = null,
)

/** The tag called [name] in this compound tag, as NBT written out in JSON has it. */
private fun JsonObject.tag(name: String): JsonObject? = (this["value"] as? JsonObject)?.get(name) as? JsonObject

private val JsonObject.number get() = (this["value"] as? JsonPrimitive)?.doubleOrNull
private val JsonObject.text get() = (this["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The values of this list tag, as NBT written out in JSON has it: each a tag of the list's type. */
private val JsonObject.items get() = ((this["value"] as? JsonObject)?.get("value") as? JsonArray).orEmpty()

/** What the effects of suspicious stew are, by its data value: the effect of the flower in it, and for how many ticks. */
private val SUSPICIOUS_STEW_EFFECTS = listOf(
    16 to 100, 8 to 100, 18 to 140, 15 to 140, 19 to 220, 23 to 6, 23 to 6, 12 to 60, 10 to 140, 20 to 140, 16 to 100, 15 to 140, 9 to 140,
)

/** The effect suspicious stew with the data value [aux] gives, and for how many ticks, or null for one the game doesn't have. */
fun suspiciousStewEffect(aux: Int): Pair<Int, Int>? = SUSPICIOUS_STEW_EFFECTS.getOrNull(aux)

/**
 * The drinks the game's item data has no food component for: a potion and
 * milk, which leave their bottle and bucket, and an ominous bottle, which
 * is gone once it's drunk. Any of them can be drunk on a full food bar.
 */
private val DRINKS = mapOf(
    "minecraft:potion" to Food(0, 0f, canAlwaysEat = true, leaves = "minecraft:glass_bottle", action = FoodAction.POTION),
    "minecraft:milk_bucket" to Food(0, 0f, canAlwaysEat = true, leaves = "minecraft:bucket", action = FoodAction.MILK),
    "minecraft:ominous_bottle" to Food(0, 0f, canAlwaysEat = true, action = FoodAction.OMINOUS),
)

/**
 * What eating an item does, from its components, [nbt], if they have the
 * game's food component: its nutrition, saturation and effects, what it
 * leaves, how many ticks eating it takes, from the component for it, in
 * ticks, or the one for using items, in seconds, and the wait before
 * another of its kind, like chorus fruit's.
 */
private fun foodOf(nbt: JsonObject?): Food? {
    val components = nbt?.tag("components") ?: return null
    val food = components.tag("minecraft:food") ?: return null
    val seconds = components.tag("minecraft:use_modifiers")?.tag("use_duration")?.number
    val ticks = components.tag("minecraft:use_duration")?.number?.toInt() ?: seconds?.let { (it * 20).roundToInt() } ?: DEFAULT_EATING_TICKS
    val effects = food.tag("effects")?.items.orEmpty().mapNotNull { entry ->
        val effect = (entry as? JsonObject)?.let { JsonObject(mapOf("value" to it)) } ?: return@mapNotNull null
        FoodEffect(
            effect = effect.tag("id")?.number?.toInt() ?: return@mapNotNull null,
            amplifier = effect.tag("amplifier")?.number?.toInt() ?: 0,
            seconds = effect.tag("duration")?.number?.toInt() ?: 0,
            chance = effect.tag("chance")?.number?.toFloat() ?: 1f,
        )
    }
    val cooldown = food.tag("cooldown_type")?.text?.takeIf { it.isNotEmpty() }
        ?.let { category -> food.tag("cooldown_time")?.number?.toInt()?.takeIf { it > 0 }?.let { ItemCooldown(category, it) } }
    return Food(
        nutrition = food.tag("nutrition")?.number?.toInt() ?: 0,
        saturationModifier = food.tag("saturation_modifier")?.number?.toFloat() ?: 0f,
        canAlwaysEat = food.tag("can_always_eat")?.number == 1.0,
        ticks = ticks,
        leaves = food.tag("using_converts_to")?.text?.takeIf { it.isNotEmpty() }?.let { "minecraft:$it" },
        effects = effects,
        cures = food.tag("remove_effects")?.items.orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull },
        action = when (food.tag("on_use_action")?.number?.toInt()) {
            0 -> FoodAction.CHORUS_TELEPORT
            1 -> FoodAction.SUSPICIOUS_STEW
            else -> null
        },
        cooldown = cooldown,
    )
}

/**
 * Reads the item list from `runtime_item_states.json`, giving each item its
 * entry from `item_components.nbt`, which is keyed by item name. How many
 * of an item a slot holds, what it's called, how long it lasts, what mends
 * it, which enchantments it takes and what eating it does come from the
 * [details] in minecraft-data's `items.json`, unless its components say.
 * The drinks those have no food for drink as [DRINKS] has them. Items
 * newer than those details stack to 64, and get a name made from their
 * identifier.
 */
fun readItems(itemStates: String, components: NbtMap, details: String = "[]"): List<ItemDefinition> {
    val byName = json.decodeFromString<List<ItemDetails>>(details).associateBy { "minecraft:${it.name}" }
    val items = json.decodeFromString<List<ItemState>>(itemStates).map { item ->
        val itemComponents = components.getCompound(item.name, NbtMap.EMPTY)
        val detail = byName[item.name]
        val categories = detail?.enchantCategories.orEmpty().toSet()
        ItemDefinition(
            name = item.name,
            runtimeId = item.id,
            componentBased = item.componentBased,
            version = ItemVersion.entries.getOrNull(item.version) ?: error("${item.name} has unknown item version ${item.version}"),
            components = itemComponents,
            maxStackSize = itemComponents.maxStackSize() ?: detail?.stackSize ?: 64,
            displayName = detail?.displayName ?: displayNameOf(item.name),
            maxDurability = itemComponents.maxDurability() ?: detail?.maxDurability ?: 0,
            repairItems = detail?.repairWith.orEmpty().mapTo(HashSet()) { "minecraft:$it" },
            enchantCategories = categories,
            enchantability = itemComponents.enchantability() ?: enchantabilityOf(item.name, categories),
            food = foodOf(detail?.nbt) ?: DRINKS[item.name],
        )
    }
    items.find { it.runtimeId !in Short.MIN_VALUE..Short.MAX_VALUE }?.let { error("${it.name}'s runtime ID ${it.runtimeId} doesn't fit in 16 bits") }
    items.checkUnique("item names", ItemDefinition::name)
    items.checkUnique("item runtime IDs", ItemDefinition::runtimeId)
    return items
}

/** Reads every block state from `block_palette.nbt`, which lists them as `{name, states, version}` compounds under `blocks`. */
fun readBlockPalette(palette: NbtMap): BlockPalette {
    val blocks = palette.getList("blocks", NbtType.COMPOUND)
    check(blocks.isNotEmpty()) { "The block palette has no blocks" }

    return BlockPalette(blocks.map { block ->
        val name = block.getString("name").ifEmpty { error("A block state in the palette has no name") }
        BlockState(name, block.getCompound("states"), block.getInt("version"))
    })
}

/** An entry in `stripped_biome_definitions.json`, which keys them by name. Fields the protocol has since dropped are ignored. */
@Serializable
private class BiomeEntry(
    val id: Int? = null,
    val temperature: Float,
    val downfall: Float,
    val foliageSnow: Float,
    val depth: Float,
    val scale: Float,
    val mapWaterColor: Argb,
    val rain: Boolean,
    val tags: List<String>? = null,
)

@Serializable
private class Argb(val a: Int, val r: Int, val g: Int, val b: Int) {
    fun pack() = (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Reads every biome from `stripped_biome_definitions.json`: the definitions
 * the game sends, without the world generation data that only a client
 * generating its own terrain needs.
 */
fun readBiomes(definitions: String): List<BiomeDefinition> {
    val biomes = json.decodeFromString<Map<String, BiomeEntry>>(definitions).map { (name, biome) ->
        BiomeDefinition(
            name = name,
            id = biome.id,
            temperature = biome.temperature,
            downfall = biome.downfall,
            foliageSnow = biome.foliageSnow,
            depth = biome.depth,
            scale = biome.scale,
            mapWaterColor = biome.mapWaterColor.pack(),
            rain = biome.rain,
            tags = biome.tags.orEmpty(),
        )
    }
    check(biomes.isNotEmpty()) { "There are no biome definitions" }
    // 0xFFFF is what a biome without an ID goes out as, so no biome can have it.
    biomes.find { it.id != null && it.id !in 0 until 0xFFFF }?.let { error("${it.name}'s ID ${it.id} isn't between 0 and 65534") }
    biomes.mapNotNull { it.id }.checkUnique("biome IDs") { it }
    return biomes
}

/** Reads `data_driven_blocks.nbt`, which holds each block's definition under its name. */
fun readBlockDefinitions(definitions: NbtMap): List<BlockDefinition> = definitions.map { (name, data) ->
    BlockDefinition(name, data as? NbtMap ?: error("Block definition $name isn't a compound"))
}

/** `creative_items.json`: the groups, then the items, each naming its group by index. */
@Serializable
private class CreativeFile(val groups: List<CreativeGroupEntry>, val items: List<CreativeItemEntry>)

@Serializable
private class CreativeGroupEntry(val name: String, val category: String, val icon: CreativeItemEntry)

/** An item in `creative_items.json`. Its block state and NBT are little-endian NBT, in base64. */
@Serializable
private class CreativeItemEntry(
    val id: String,
    val groupId: Int = 0,
    val damage: Int = 0,
    @SerialName("block_state_b64") val blockState: String? = null,
    @SerialName("nbt_b64") val nbt: String? = null,
)

/**
 * Reads the creative inventory from `creative_items.json`, finding each item
 * among [items] by name and each block state in [blocks], so an item or
 * state the client wouldn't know fails here, on start.
 */
fun readCreativeInventory(creative: String, items: List<ItemDefinition>, blocks: BlockPalette): CreativeInventory {
    val file = json.decodeFromString<CreativeFile>(creative)
    val byName = items.associateBy { it.name }

    // A group's icon of air means it has none.
    fun stackOf(entry: CreativeItemEntry): ItemStack? {
        if (entry.id == AIR) return null
        val item = byName[entry.id] ?: error("Creative item ${entry.id} isn't in the item list")
        val block = entry.blockState?.let(::readBase64Nbt)?.let { blocks.stateOf(it.getString("name"), it.getCompound("states")) }
        return ItemStack(item, aux = entry.damage, block = block, nbt = entry.nbt?.let(::readBase64Nbt) ?: NbtMap.EMPTY)
    }

    val groups = file.groups.map { group ->
        val category = CreativeCategory.entries.find { it.name.equals(group.category, ignoreCase = true) }
            ?: error("Creative group ${group.name} is in an unknown tab, ${group.category}")
        CreativeGroup(category, group.name, stackOf(group.icon))
    }
    val entries = file.items.map { entry ->
        check(entry.groupId in groups.indices) { "Creative item ${entry.id} is in group ${entry.groupId}, which doesn't exist" }
        CreativeItem(stackOf(entry) ?: error("The creative inventory lists air as an item"), entry.groupId)
    }
    return CreativeInventory(groups, entries)
}

private fun readBase64Nbt(base64: String): NbtMap =
    NbtUtils.createReaderLE(Base64.getDecoder().decode(base64).inputStream()).readTag() as? NbtMap
        ?: error("Creative data holds NBT that isn't a compound")

private fun <T> List<T>.checkUnique(what: String, key: (T) -> Any) {
    val repeated = groupingBy(key).eachCount().filterValues { it > 1 }.keys
    check(repeated.isEmpty()) { "Repeated $what: ${repeated.take(5)}" }
}

/** Reads one of Cloudburst's NBT files: gzipped, big-endian, with a compound at the root. */
private fun readNbt(file: Path): NbtMap =
    NbtUtils.createGZIPReader(file.inputStream()).use { it.readTag() } as? NbtMap ?: error("$file doesn't hold an NBT compound")
