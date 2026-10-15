package org.koremc.registry

import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.cloudburstmc.nbt.NbtUtils
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VanillaDataTest {
    @Test
    fun `items get their components by name, or none`() {
        val food = NbtMap.builder().putCompound("components", NbtMap.builder().putInt("nutrition", 4).build()).build()

        val items = readItems(
            """
            [
                {"name": "minecraft:apple", "id": 882, "version": 1, "componentBased": true},
                {"name": "minecraft:stick", "id": 323, "version": 2, "componentBased": false}
            ]
            """,
            NbtMap.builder().putCompound("minecraft:apple", food).build(),
        )

        assertEquals(
            listOf(
                ItemDefinition("minecraft:apple", 882, componentBased = true, ItemVersion.DATA_DRIVEN, food),
                ItemDefinition("minecraft:stick", 323, componentBased = false, ItemVersion.NONE),
            ),
            items,
        )
    }

    @Test
    fun `items stack and are named as the game data says, unless their components say how many stack`() {
        val properties = NbtMap.builder().putCompound("item_properties", NbtMap.builder().putInt("max_stack_size", 1).build()).build()
        val items = readItems(
            """
            [
                {"name": "minecraft:diamond_sword", "id": 335, "version": 1, "componentBased": true},
                {"name": "minecraft:ender_pearl", "id": 444, "version": 2, "componentBased": false},
                {"name": "minecraft:black_wool_slab", "id": -1200, "version": 2, "componentBased": false}
            ]
            """,
            NbtMap.builder().putCompound("minecraft:diamond_sword", NbtMap.builder().putCompound("components", properties).build()).build(),
            """
            [
                {"id": 335, "name": "diamond_sword", "displayName": "Diamond Sword", "stackSize": 64},
                {"id": 444, "name": "ender_pearl", "displayName": "Ender Pearl", "stackSize": 16}
            ]
            """,
        ).associateBy { it.name }

        assertEquals(1, items.getValue("minecraft:diamond_sword").maxStackSize)
        assertEquals(16, items.getValue("minecraft:ender_pearl").maxStackSize)
        assertEquals("Ender Pearl", items.getValue("minecraft:ender_pearl").displayName)
        // Newer than the data: the usual stack, and a name from its identifier.
        assertEquals(64, items.getValue("minecraft:black_wool_slab").maxStackSize)
        assertEquals("Black Wool Slab", items.getValue("minecraft:black_wool_slab").displayName)
    }

    @Test
    fun `item lists that couldn't go on the wire are rejected`() {
        assertFailsWith<IllegalStateException> { readItems(items("a" to 1, "b" to 1), NbtMap.EMPTY) }
        assertFailsWith<IllegalStateException> { readItems(items("a" to 1, "a" to 2), NbtMap.EMPTY) }
        assertFailsWith<IllegalStateException> { readItems(items("a" to 40_000), NbtMap.EMPTY) }
        assertFailsWith<IllegalStateException> { readItems("""[{"name": "a", "id": 1, "version": 3, "componentBased": false}]""", NbtMap.EMPTY) }
    }

    @Test
    fun `biomes keep their tags and pack their map water colour as ARGB`() {
        val biomes = readBiomes(
            """
            {
                "minecraft:plains": {
                    "id": null, "temperature": 0.8, "downfall": 0.4, "redSporeDensity": 0.0, "foliageSnow": 0.0, "depth": 0.125, "scale": 0.05,
                    "mapWaterColor": {"a": 165, "r": 68, "g": 175, "b": 245}, "rain": true, "tags": ["animal", "plains"], "chunkGenData": null
                }
            }
            """,
        )

        assertEquals(
            listOf(BiomeDefinition("minecraft:plains", null, 0.8f, 0.4f, 0f, 0.125f, 0.05f, 0xA544AFF5.toInt(), rain = true, listOf("animal", "plains"))),
            biomes,
        )
    }

    @Test
    fun `biome IDs must be distinct and leave room for the missing ID marker`() {
        assertFailsWith<IllegalStateException> { readBiomes(biomes("a" to 65535)) }
        assertFailsWith<IllegalStateException> { readBiomes(biomes("a" to 3, "b" to 3)) }
        assertFailsWith<IllegalStateException> { readBiomes("{}") }
        assertEquals(listOf(3, null), readBiomes(biomes("a" to 3, "b" to null)).map { it.id })
    }

    @Test
    fun `creative items find their item by name and their block in the palette`() {
        val planksState = BlockState("minecraft:oak_planks", NbtMap.EMPTY, 18168865)
        val enchantment = NbtMap.builder().putShort("lvl", 1).build()
        val creative = """
            {
                "groups": [
                    {"name": "itemGroup.name.planks", "category": "construction", "icon": {"id": "minecraft:oak_planks", "block_state_b64": "${blockStateNbt("minecraft:oak_planks")}"}},
                    {"name": "", "category": "items", "icon": {"id": "minecraft:air"}}
                ],
                "items": [
                    {"id": "minecraft:oak_planks", "groupId": 0, "block_state_b64": "${blockStateNbt("minecraft:oak_planks")}"},
                    {"id": "minecraft:arrow", "groupId": 1, "damage": 6},
                    {"id": "minecraft:enchanted_book", "groupId": 1, "nbt_b64": "${base64(enchantment)}"}
                ]
            }
        """

        val inventory = readCreativeInventory(creative, listOf(planks, arrow, book), BlockPalette(listOf(planksState)))

        assertEquals(
            listOf(
                CreativeGroup(CreativeCategory.CONSTRUCTION, "itemGroup.name.planks", ItemStack(planks, block = planksState)),
                CreativeGroup(CreativeCategory.ITEMS, "", icon = null),
            ),
            inventory.groups,
        )
        assertEquals(
            listOf(
                CreativeItem(ItemStack(planks, block = planksState), group = 0),
                CreativeItem(ItemStack(arrow, aux = 6), group = 1),
                CreativeItem(ItemStack(book, nbt = enchantment), group = 1),
            ),
            inventory.items,
        )
    }

    @Test
    fun `creative items the client wouldn't know are rejected`() {
        val group = """{"name": "", "category": "items", "icon": {"id": "minecraft:air"}}"""
        val palette = BlockPalette(emptyList())

        assertFailsWith<IllegalStateException> { readCreativeInventory("""{"groups": [$group], "items": [{"id": "minecraft:nope"}]}""", listOf(arrow), palette) }
        assertFailsWith<IllegalStateException> { readCreativeInventory("""{"groups": [$group], "items": [{"id": "minecraft:arrow", "groupId": 1}]}""", listOf(arrow), palette) }
        assertFailsWith<IllegalArgumentException> {
            readCreativeInventory("""{"groups": [$group], "items": [{"id": "minecraft:arrow", "block_state_b64": "${blockStateNbt("minecraft:nope")}"}]}""", listOf(arrow), palette)
        }
    }

    @Test
    fun `mining comes from minecraft-data, its harvest tools counted into a tier`() {
        val mining = readMining(
            """[
                {"name": "stone", "hardness": 1.5, "material": "mineable/pickaxe", "harvestTools": {"1": true, "2": true, "3": true, "4": true, "5": true, "6": true, "7": true}},
                {"name": "diamond_ore", "hardness": 3, "material": "incorrect_for_wooden_tool", "harvestTools": {"5": true, "6": true, "7": true}},
                {"name": "oak_leaves", "hardness": 0.2, "material": "leaves;mineable/hoe"},
                {"name": "white_wool", "hardness": 0.8, "material": "wool"},
                {"name": "jigsaw", "hardness": -1, "material": "default", "harvestTools": {}}
            ]""",
        )

        assertEquals(Mining(1.5, ToolKind.PICKAXE, harvestTier = 0), mining["minecraft:stone"])
        assertEquals(Mining(3.0, ToolKind.PICKAXE, harvestTier = 2), mining["minecraft:diamond_ore"])
        assertEquals(Mining(0.2, ToolKind.HOE), mining["minecraft:oak_leaves"])
        assertEquals(Mining(0.8, ToolKind.SHEARS), mining["minecraft:white_wool"])
        assertTrue(mining.getValue("minecraft:jigsaw").unbreakable)
        assertFailsWith<IllegalStateException> { readMining("""[{"name": "odd", "hardness": 1, "harvestTools": {"1": true}}]""") }
    }

    @Test
    fun `blocks defined in data say how they take to mining, and newer blocks borrow from their family`() {
        val stairs = BlockDefinition(
            "minecraft:white_concrete_stairs",
            NbtMap.builder()
                .putList("blockTags", NbtType.STRING, listOf("minecraft:is_pickaxe_item_destructible"))
                .putCompound("components", NbtMap.builder().putCompound("minecraft:destructible_by_mining", NbtMap.builder().putFloat("value", 1.8f).build()).build())
                .putCompound("vanilla_block_data", NbtMap.builder().putBoolean("requires_correct_tool_for_drops", true).build())
                .build(),
        )
        assertEquals(mapOf(stairs.name to Mining(1.8f.toDouble(), ToolKind.PICKAXE, harvestTier = 0)), readDataDrivenMining(listOf(stairs)))

        val spruce = Mining(2.0, ToolKind.AXE)
        val palette = BlockPalette(listOf("minecraft:spruce_planks", "minecraft:poplar_planks", "minecraft:red_poplar_leaves", "minecraft:oak_leaves", "minecraft:mystery").map { BlockState(it, NbtMap.EMPTY, 1) })
        val filled = withNewerBlocks(mapOf("minecraft:spruce_planks" to spruce, "minecraft:oak_leaves" to Mining(0.2)), palette)
        assertEquals(spruce, filled["minecraft:poplar_planks"])
        assertEquals(Mining(0.2), filled["minecraft:red_poplar_leaves"])
        assertEquals(null, filled["minecraft:mystery"])
    }

    @Test
    fun `the game's data loads`() {
        assumeGameData()
        val vanilla = VanillaData.load(gameData)
        val items = vanilla.items.associateBy { it.name }

        assertTrue(items.size > 1000)
        val shield = items.getValue("minecraft:shield")
        assertEquals(ItemDefinition("minecraft:shield", 358, componentBased = false, ItemVersion.NONE, maxStackSize = 1), shield.copy(maxDurability = 0, repairItems = emptySet(), enchantCategories = emptySet()))
        // A shield wears out, planks mend it, and it takes Unbreaking, but a table can't enchant it.
        assertEquals(336, shield.maxDurability)
        assertTrue("minecraft:oak_planks" in shield.repairItems && "durability" in shield.enchantCategories)
        assertEquals(0, shield.enchantability)
        assertEquals(10, items.getValue("minecraft:diamond_sword").enchantability)
        assertEquals(9, items.getValue("minecraft:iron_chestplate").enchantability)
        assertEquals(15, items.getValue("minecraft:netherite_spear").enchantability) // From its own components
        assertEquals(16, items.getValue("minecraft:ender_pearl").maxStackSize)
        assertTrue(items.getValue("minecraft:apple").components.getCompound("components").containsKey("item_properties"))

        assertTrue(vanilla.blocks.states.size > 10_000)
        assertEquals("minecraft:air", vanilla.blocks[blockStateHash("minecraft:air", emptyMap())]?.name)

        val plains = vanilla.biomes.single { it.name == "minecraft:plains" }
        assertTrue(vanilla.biomes.size > 50)
        assertEquals(null, plains.id)
        assertTrue("plains" in plains.tags)

        // The blocks 26.50 defines in data, which clients can't show without their definitions.
        val defined = vanilla.blockDefinitions.map { it.name }
        assertTrue("minecraft:white_wool_stairs" in defined && "minecraft:red_concrete_slab" in defined && "minecraft:shelf_mushroom" in defined)
        assertTrue(vanilla.blockDefinitions.all { it.name in vanilla.blocks.states.map(BlockState::name) })

        // Every block takes to mining somehow, those newer than minecraft-data's table and those defined in data included.
        val unknown = vanilla.blocks.states.map { it.name }.distinct().filter { it !in vanilla.mining }
        assertEquals(emptyList(), unknown)
        assertEquals(Mining(1.5, ToolKind.PICKAXE, harvestTier = 0), vanilla.mining["minecraft:stone"])
        assertEquals(1, vanilla.mining["minecraft:iron_ore"]?.harvestTier)
        assertEquals(2, vanilla.mining["minecraft:diamond_ore"]?.harvestTier)
        assertEquals(3, vanilla.mining["minecraft:obsidian"]?.harvestTier)
        assertEquals(vanilla.mining["minecraft:spruce_log"], vanilla.mining["minecraft:poplar_log"])
        assertEquals(ToolKind.SHEARS, vanilla.mining["minecraft:white_wool_stairs"]?.tool)
        assertTrue(vanilla.mining.getValue("minecraft:bedrock").unbreakable)

        // Every block state's map colour and the light it stops and gives off, by the same network IDs Kore hashes.
        val missing = vanilla.blocks.states.filter { it.networkId !in vanilla.traits }
        assertEquals(emptyList(), missing.map { it.name }.distinct())
        assertEquals(BlockTrait(0xFF707070.toInt(), Tint.NONE, 15), vanilla.traits[blockStateHash("minecraft:stone", emptyMap())])
        assertEquals(Tint.GRASS, vanilla.traits[blockStateHash("minecraft:grass_block", emptyMap())].tint)
        assertEquals(0, vanilla.traits[blockStateHash("minecraft:glass", emptyMap())].mapColour)
        assertEquals(15, vanilla.traits[blockStateHash("minecraft:glowstone", emptyMap())].lightEmission)

        // Recent additions, which a creative inventory from older data would be missing.
        val creative = vanilla.creative.items.map { it.stack.item.name }.toSet()
        assertTrue(vanilla.creative.items.size > 1000)
        for (name in listOf("minecraft:shelf_mushroom", "minecraft:poplar_shelf", "minecraft:white_wool_stairs", "minecraft:red_concrete_slab")) {
            assertTrue(name in creative, "$name is missing from the creative inventory")
        }
    }

    @Test
    fun `block states' map colours come as RGBA hex, and their network IDs unsigned`() {
        val traits = readBlockTraits(
            """
            [
                {"blockStateHash": 4121722107, "mapColor": "#707070ff", "name": "minecraft:bedrock", "tintMethod": "None", "lightDampening": 15, "friction": 0.6},
                {"blockStateHash": 1211861802, "mapColor": "#00000000", "name": "minecraft:water", "tintMethod": "Water", "lightDampening": 1}
            ]
            """,
        )

        assertEquals(BlockTrait(0xFF707070.toInt(), Tint.NONE, 15), traits[4121722107L.toInt()])
        assertEquals(BlockTrait(0, Tint.WATER, 1), traits[1211861802])
        assertEquals(BlockTrait(0, Tint.NONE, 0), traits[5])
    }

    @Test
    fun `what eating an item does comes from the game's food component`() {
        assumeGameData()
        val food = vanillaData.items.associate { it.name to it.food }

        assertEquals(Food(4, 0.3f), food["minecraft:apple"])
        assertEquals(Food(6, 0.6f, leaves = "minecraft:bowl"), food["minecraft:mushroom_stew"])
        assertEquals(Food(1, 0.1f, ticks = 16), food["minecraft:dried_kelp"])
        assertEquals(
            Food(4, 1.2f, canAlwaysEat = true, effects = listOf(FoodEffect(10, 1, 5, 1f), FoodEffect(22, 0, 120, 1f))),
            food["minecraft:golden_apple"],
        )
        assertEquals(listOf(FoodEffect(17, 0, 30, 0.8f)), food["minecraft:rotten_flesh"]?.effects)
        // Honey cures poison, and leaves its bottle.
        assertEquals(Food(6, 0.1f, canAlwaysEat = true, ticks = 40, leaves = "minecraft:glass_bottle", cures = listOf(19)), food["minecraft:honey_bottle"])
        assertEquals(FoodAction.CHORUS_TELEPORT, food["minecraft:chorus_fruit"]?.action)
        assertEquals(ItemCooldown("chorusfruit", 20), food["minecraft:chorus_fruit"]?.cooldown)
        assertEquals(null, food["minecraft:apple"]?.cooldown)
        assertEquals(FoodAction.SUSPICIOUS_STEW, food["minecraft:suspicious_stew"]?.action)
        assertEquals(null, food["minecraft:stick"])
    }

    @Test
    fun `potions, milk and ominous bottles drink, though the game's data has no food for them`() {
        assumeGameData()
        val food = vanillaData.items.associate { it.name to it.food }

        assertEquals(Food(0, 0f, canAlwaysEat = true, leaves = "minecraft:glass_bottle", action = FoodAction.POTION), food["minecraft:potion"])
        assertEquals(Food(0, 0f, canAlwaysEat = true, leaves = "minecraft:bucket", action = FoodAction.MILK), food["minecraft:milk_bucket"])
        assertEquals(Food(0, 0f, canAlwaysEat = true, action = FoodAction.OMINOUS), food["minecraft:ominous_bottle"])
        // Thrown, not drunk.
        assertEquals(null, food["minecraft:splash_potion"])
    }

    @Test
    fun `a missing data directory names the file it needed`() {
        val missing = assertFailsWith<NoSuchFileException> { VanillaData.load(Path.of("no-such-data")) }

        assertEquals(Path.of("no-such-data", "runtime_item_states.json").toString(), missing.file)
    }

    private val planks = ItemDefinition("minecraft:oak_planks", 5, componentBased = false, ItemVersion.NONE)
    private val arrow = ItemDefinition("minecraft:arrow", 303, componentBased = false, ItemVersion.DATA_DRIVEN)
    private val book = ItemDefinition("minecraft:enchanted_book", 530, componentBased = false, ItemVersion.NONE)

    /** A block state as creative_items.json has it: little-endian NBT in base64, with no properties here. */
    private fun blockStateNbt(name: String) = base64(NbtMap.builder().putString("name", name).putCompound("states", NbtMap.EMPTY).build())

    private fun base64(tag: NbtMap) =
        Base64.getEncoder().encodeToString(ByteArrayOutputStream().also { NbtUtils.createWriterLE(it).writeTag(tag) }.toByteArray())

    private fun biomes(vararg entries: Pair<String, Int?>) = entries.joinToString(",", "{", "}") { (name, id) ->
        """"$name": {"id": $id, "temperature": 0, "downfall": 0, "foliageSnow": 0, "depth": 0, "scale": 0, "mapWaterColor": {"a": 0, "r": 0, "g": 0, "b": 0}, "rain": false}"""
    }

    private fun items(vararg entries: Pair<String, Int>) =
        entries.joinToString(",", "[", "]") { (name, id) -> """{"name": "$name", "id": $id, "version": 2, "componentBased": false}""" }
}
