package org.koremc

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtUtils
import org.cloudburstmc.nbt.NbtType
import org.koremc.auth.MultiplayerTokenVerifier
import org.koremc.auth.TestAuthority
import org.koremc.auth.ecKeyPair
import org.koremc.inventory.SlotStack
import org.koremc.protocol.BatchCodec
import org.koremc.protocol.BlockDefinitions
import org.koremc.protocol.BlockEvent
import org.koremc.protocol.ClientboundMapItemData
import org.koremc.protocol.ContainerClose
import org.koremc.protocol.ContainerName
import org.koremc.protocol.ContainerOpen
import org.koremc.protocol.ContainerSetData
import org.koremc.protocol.ContainerType
import org.koremc.protocol.GameMode
import org.koremc.protocol.InventoryContent
import org.koremc.protocol.InventorySlot
import org.koremc.protocol.LevelEvent
import org.koremc.protocol.LevelSoundEvent
import org.koremc.protocol.MobEffect
import org.koremc.protocol.PlayerEnchantOptions
import org.koremc.protocol.SetActorMotion
import org.koremc.protocol.UI_WINDOW
import org.koremc.protocol.UpdateBlock
import org.koremc.protocol.clientDataToken
import org.koremc.protocol.connectionRequest
import org.koremc.protocol.encode
import org.koremc.protocol.loginPacket
import org.koremc.protocol.readBlockPos
import org.koremc.protocol.readVarUInt
import org.koremc.protocol.tokenAuthentication
import org.koremc.protocol.writeBlockPos
import org.koremc.protocol.writeByteArray
import org.koremc.protocol.writeString
import org.koremc.protocol.writeVarInt
import org.koremc.protocol.writeVarLong
import org.koremc.protocol.writeVarUInt
import org.koremc.protocol.writeVarULong
import org.koremc.registry.AIR
import org.koremc.registry.AIR_NETWORK_ID
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import org.koremc.registry.BlockTrait
import org.koremc.registry.BlockTraits
import org.koremc.registry.Tint
import org.koremc.inventory.BEACON_PAYMENT_SLOT
import org.koremc.storage.PlayerSaves
import org.koremc.inventory.CARTOGRAPHY_ADDITIONAL_SLOT
import org.koremc.inventory.CARTOGRAPHY_INPUT_SLOT
import org.koremc.inventory.CREATED_OUTPUT_SLOT
import org.koremc.registry.CreativeCategory
import org.koremc.registry.CreativeGroup
import org.koremc.registry.CreativeInventory
import org.koremc.registry.CreativeItem
import org.koremc.registry.Cost
import org.koremc.registry.Enchantment
import org.koremc.registry.Enchantments
import org.koremc.registry.Ingredient
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.ItemVersion
import org.koremc.registry.Recipes
import org.koremc.registry.ShapelessRecipe
import org.koremc.world.BlockPos
import org.koremc.world.Drops
import org.koremc.world.BOOK_PUT
import org.koremc.world.Effect
import org.koremc.world.EffectLevel
import org.koremc.world.INFINITE_EFFECT
import org.koremc.world.beaconPowers
import org.koremc.world.mapId
import org.koremc.world.setBeaconPowers
import org.koremc.world.FlatWorld
import org.koremc.world.lecternBook
import org.koremc.world.PLAYER_EYE_HEIGHT
import org.koremc.world.Vec3
import org.koremc.world.centre
import org.koremc.world.pairChest
import java.security.interfaces.ECPublicKey
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROTOCOL = 2193
private const val PLAYER_ENCHANT_OPTIONS = 146

class ContainersTest {
    private val authority = TestAuthority()
    private val authenticator = LoginAuthenticator(MultiplayerTokenVerifier(keys = { authority.keys }), onlineMode = true)
    private var config = ServerConfig(protocol = PROTOCOL, viewDistance = 8, gameMode = GameMode.CREATIVE)

    private fun state(name: String, vararg properties: Pair<String, Any>) = BlockState(name, NbtMap.fromMap(mapOf(*properties)), version = 18168865)

    private val stone = state("minecraft:stone")
    private val chest = state("minecraft:chest", "minecraft:cardinal_direction" to "south")
    private val enderChest = state("minecraft:ender_chest", "minecraft:cardinal_direction" to "south")
    private val barrel = state("minecraft:barrel", "facing_direction" to 1, "open_bit" to 0.toByte())
    private val openBarrel = state("minecraft:barrel", "facing_direction" to 1, "open_bit" to 1.toByte())
    private val shulkerBox = state("minecraft:white_shulker_box")
    private val craftingTable = state("minecraft:crafting_table")
    private val furnace = state("minecraft:furnace", "minecraft:cardinal_direction" to "south")
    private val litFurnace = state("minecraft:lit_furnace", "minecraft:cardinal_direction" to "south")
    private val enchantingTable = state("minecraft:enchanting_table")
    private val hopper = state("minecraft:hopper", "facing_direction" to 0, "toggle_bit" to 0.toByte())
    private val dispensers = (0..1).map { on -> state("minecraft:dispenser", "facing_direction" to 5, "triggered_bit" to on.toByte()) }
    private val levers = (0..1).map { on -> state("minecraft:lever", "lever_direction" to "up_east_west", "open_bit" to on.toByte()) }
    // Facing east, which pistons store as west.
    private val piston = state("minecraft:piston", "facing_direction" to 4)
    private val pistonHead = state("minecraft:piston_arm_collision", "facing_direction" to 4)
    private val upPiston = state("minecraft:piston", "facing_direction" to 1)
    private val upPistonHead = state("minecraft:piston_arm_collision", "facing_direction" to 1)
    private val movingBlock = state("minecraft:moving_block")
    private val lectern = state("minecraft:lectern", "minecraft:cardinal_direction" to "south", "powered_bit" to 0.toByte())
    private val crafter = state("minecraft:crafter", "orientation" to "north_up", "crafting" to 0.toByte(), "triggered_bit" to 0.toByte())
    private val beacon = state("minecraft:beacon")
    private val ironBlock = state("minecraft:iron_block")
    private val cartographyTable = state("minecraft:cartography_table")
    private val water = (0..15).map { state("minecraft:flowing_water", "liquid_depth" to it) }
    private val palette = BlockPalette(
        listOf(state(AIR), stone, chest, enderChest, barrel, openBarrel, shulkerBox, craftingTable, furnace, litFurnace, enchantingTable, hopper) + dispensers + levers +
            listOf(piston, pistonHead, upPiston, upPistonHead, movingBlock, lectern, crafter, beacon, ironBlock, cartographyTable) + water,
    )
    private val world = FlatWorld(palette, listOf(stone))

    private val stoneItem = ItemDefinition("minecraft:stone", 1, componentBased = false, ItemVersion.NONE)
    private val shulkerItem = ItemDefinition("minecraft:white_shulker_box", 218, componentBased = false, ItemVersion.NONE, maxStackSize = 1)
    private val rawIron = ItemDefinition("minecraft:raw_iron", 2, componentBased = false, ItemVersion.NONE)
    private val ironIngot = ItemDefinition("minecraft:iron_ingot", 3, componentBased = false, ItemVersion.NONE)
    private val coal = ItemDefinition("minecraft:coal", 4, componentBased = false, ItemVersion.NONE)
    private val sword = ItemDefinition("minecraft:diamond_sword", 5, componentBased = false, ItemVersion.NONE, maxStackSize = 1, enchantCategories = setOf("durability"), enchantability = 10)
    private val writtenBook = ItemDefinition("minecraft:written_book", 6, componentBased = false, ItemVersion.NONE, maxStackSize = 16)
    private val emptyMap = ItemDefinition("minecraft:empty_map", 7, componentBased = false, ItemVersion.NONE)
    private val filledMap = ItemDefinition("minecraft:filled_map", 8, componentBased = false, ItemVersion.NONE)
    private val glassPane = ItemDefinition("minecraft:glass_pane", 9, componentBased = false, ItemVersion.NONE)
    private val bucket = ItemDefinition("minecraft:bucket", 10, componentBased = false, ItemVersion.NONE, maxStackSize = 16)
    private val waterBucket = ItemDefinition("minecraft:water_bucket", 11, componentBased = false, ItemVersion.NONE, maxStackSize = 1)
    private val items = listOf(stoneItem, shulkerItem, rawIron, ironIngot, coal, sword, writtenBook, emptyMap, filledMap, glassPane, bucket, waterBucket)
    private val creative = CreativeInventory(
        listOf(CreativeGroup(CreativeCategory.CONSTRUCTION, "", icon = null)),
        listOf(CreativeItem(ItemStack(stoneItem, block = stone), group = 0), CreativeItem(ItemStack(shulkerItem, block = shulkerBox), group = 0)),
    )
    private val recipes = Recipes(
        listOf(ShapelessRecipe("iron", listOf(Ingredient.Item(rawIron.name, null, 1)), listOf(ItemStack(ironIngot)), "furnace", 0, UUID(0, 1), keepsData = false, 1)),
        emptyList(), emptyList(), emptyMap(),
    )
    // At any cost a table offers, so every offer has it.
    private val unbreaking = Enchantment(17, "unbreaking", 1, Cost(0, 1), Cost(0, 100), 5, treasure = false, curse = false, emptySet(), { "durability" in it.enchantCategories })
    private val game = GameData(
        world, emptyList(), BlockDefinitions(emptyList()), creative, emptyMap(), Drops(items, creative, palette), ItemStacks(items, creative, palette),
        recipes = recipes, enchantments = Enchantments(listOf(unbreaking)),
        // Stone shows grey on maps, and stops all light.
        traits = BlockTraits(mapOf(stone.networkId to BlockTrait(0xFF707070.toInt(), Tint.NONE, 15))),
        players = object : PlayerSaves {
            override fun load(player: UUID) = savedPlayers[player]

            override fun save(player: UUID, data: NbtMap) {
                savedPlayers[player] = data
            }
        },
    )

    /** What players saved as they left, by their identity. */
    private val savedPlayers = HashMap<UUID, NbtMap>()

    private val clients = mutableListOf<Client>()

    /** One player's client, with its own session on the shared server. */
    private inner class Client(val entityId: Long, name: String, xuid: String) {
        private val key = ecKeyPair()
        private val batches = BatchCodec()
        private val sent = mutableListOf<ByteArray>()
        val session: Session = Session(
            config, game, networkId = "network-$entityId", entityId, key.public as ECPublicKey, authenticator,
            viewers = { clients.map { it.session } },
            schedule = { _, _ -> },
        ) { sent += it }

        /** What the client got as its player came into the game. */
        val spawned: List<ByteArray>

        init {
            clients += this
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(193).writeInt(PROTOCOL)))
            batches.enableCompression(256)
            val token = authority.multiplayerToken(key.public as ECPublicKey, xuid = xuid, displayName = name)
            sends(loginPacket(PROTOCOL, connectionRequest(tokenAuthentication(token), clientDataToken(key.private))))
            sends(packResponse(2, "downloadingfinished"))
            sends(packResponse(3, "resourcepackstackfinished"))
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(69).writeVarInt(2).writeByte(2)))
            val before = sent.size
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(113).writeVarULong(entityId)))
            spawned = sent.drop(before).flatMap { batches.decode(it) }
            sent.clear()
        }

        fun sends(packet: ByteArray) = session.receive(batches.encode(listOf(packet)))

        /** Every packet the client got since the last look. */
        fun received(): List<ByteArray> = sent.flatMap { batches.decode(it) }.also { sent.clear() }

        /** Clicks the top of the block at [pos], holding the item numbered [held] in the hotbar's first slot, or nothing. */
        fun clicks(pos: BlockPos, held: Int = 0, block: Int = 0) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(30).writeVarInt(0).writeBoolean(false).writeVarUInt(2).writeVarUInt(0)
                    .writeVarInt(0).writeByte(1).writeBlockPos(pos).writeByte(1).writeVarInt(0).writeByte(0)
                    .item(held, block)
                    .writeFloatLE(0.5f).writeFloatLE(-61.38f).writeFloatLE(0.5f)
                    .writeFloatLE(0.5f).writeFloatLE(1f).writeFloatLE(0.5f)
                    .writeVarUInt(world.blockAt(pos)).writeBoolean(true).writeByte(0),
            ),
        )

        /** Moves [count] items from [from] to [to], each a container name, slot and the stack ID the client expects there. */
        fun moves(count: Int, from: Triple<Int, Int, Int>, to: Triple<Int, Int, Int>, requestId: Int = -1) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(requestId).writeVarUInt(1)
                    .writeVarUInt(0).writeByte(0).writeByte(count).slot(from).slot(to)
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )

        fun closes(window: Int) = sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(47).writeByte(window).writeByte(0).writeBoolean(false)))

        /** Uses the item numbered [held] in the hotbar's first slot in the air. */
        fun usesInAir(held: Int) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(30).writeVarInt(0).writeBoolean(false).writeVarUInt(2).writeVarUInt(0)
                    .writeVarInt(1).writeByte(1).writeBlockPos(BlockPos(0, 0, 0)).writeByte(255).writeVarInt(0).writeByte(0)
                    .item(held, 0)
                    .writeFloatLE(0.5f).writeFloatLE(-61.38f).writeFloatLE(0.5f)
                    .writeZero(12).writeVarUInt(0).writeBoolean(true).writeByte(0),
            ),
        )

        /** Picks [copyId] as the number of the locked copy of the map numbered [id], as a glass pane going in beside it does. */
        fun picksLockedCopy(id: Long, copyId: Long) = sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(131).writeVarLong(id).writeVarLong(copyId)))

        /**
         * Works the map [mapStack] and what goes with it, [withStack], in the
         * open cartography table, taking what it makes onto the cursor, in one
         * request, as taking a cartography table's result does.
         */
        fun cartographs(mapStack: Int, withStack: Int) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-11).writeVarUInt(4)
                    .writeVarUInt(13).writeByte(15).writeVarUInt(3288).writeIntLE(0)
                    .writeVarUInt(5).writeByte(5).writeByte(1).slot(Triple(ContainerName.CARTOGRAPHY_INPUT, CARTOGRAPHY_INPUT_SLOT, mapStack))
                    .writeVarUInt(5).writeByte(5).writeByte(1).slot(Triple(ContainerName.CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_ADDITIONAL_SLOT, withStack))
                    .writeVarUInt(0).writeByte(0).writeByte(1).slot(Triple(ContainerName.CREATED_OUTPUT, CREATED_OUTPUT_SLOT, -11)).slot(Triple(ContainerName.CURSOR, 0, 0))
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )

        /** Asks what the map numbered [id] shows, as the client does when the player holds one it hasn't seen. */
        fun asksAboutMap(id: Long) = sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(68).writeVarLong(id).writeIntLE(0)))

        /** Puts the stack [stackId] in the hotbar's first slot in the open beacon, pays it for [primary], and destroys the payment, in one request. */
        fun paysBeacon(stackId: Int, primary: Int) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-1).writeVarUInt(3)
                    .writeVarUInt(1).writeByte(1).writeByte(1).slot(Triple(ContainerName.HOTBAR, 0, stackId)).slot(Triple(ContainerName.BEACON_PAYMENT, BEACON_PAYMENT_SLOT, 0))
                    .writeVarUInt(8).writeByte(10).writeVarInt(primary).writeVarInt(0)
                    .writeVarUInt(4).writeByte(4).writeByte(1).slot(Triple(ContainerName.BEACON_PAYMENT, BEACON_PAYMENT_SLOT, stackId))
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )

        /** Switches [slot] of the crafter at [pos] off, if [disabled], or back on, as clicking it empty in the crafter's window does. */
        fun togglesCrafterSlot(pos: BlockPos, slot: Int, disabled: Boolean) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(306).writeIntLE(pos.x).writeIntLE(pos.y).writeIntLE(pos.z).writeByte(slot).writeBoolean(disabled),
            ),
        )

        /** Stands the player with their feet at [feet], moving by [delta], as the client reports where they are each tick. */
        fun standsAt(feet: Vec3, delta: Vec3 = Vec3(0f, 0f, 0f), pitch: Float = 0f, yaw: Float = 0f) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer()
                    .writeVarUInt(144)
                    .writeFloatLE(pitch).writeFloatLE(yaw).writeFloatLE(feet.x).writeFloatLE(feet.y + PLAYER_EYE_HEIGHT).writeFloatLE(feet.z)
                    .writeZero(8).writeFloatLE(0f) // Move vector, then head yaw
                    .writeVarUInt(0) // No input flags
                    .writeVarUInt(1).writeVarUInt(0).writeVarInt(1).writeZero(8)
                    .writeVarULong(100).writeFloatLE(delta.x).writeFloatLE(delta.y).writeFloatLE(delta.z).writeZero(5).writeZero(28),
            ),
        )

        /** Breaks the block at [pos], as a creative player does. */
        fun breaks(pos: BlockPos) = sends(
            ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(36).writeVarULong(entityId).writeVarInt(13).writeBlockPos(pos).writeBlockPos(BlockPos(0, 0, 0)).writeVarInt(1)),
        )

        private fun ByteBuf.item(id: Int, block: Int): ByteBuf =
            writeShortLE(id).writeShortLE(if (id == 0) 0 else 1).writeVarUInt(0).writeBoolean(false).writeVarUInt(block).writeByteArray(ByteArray(10))

        private fun ByteBuf.slot(slot: Triple<Int, Int, Int>): ByteBuf = writeByte(slot.first).writeBoolean(false).writeByte(slot.second).writeIntLE(slot.third)

        private fun packResponse(tag: Int, name: String) =
            ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(8).writeVarUInt(tag).writeString(name))
    }

    private val here = BlockPos(2, -63, 0)

    private fun List<ByteArray>.has(packet: ByteArray) = any { it.contentEquals(packet) }

    /** The window a chest's 27 slots show, with [stack] in the first. */
    private fun chestWith(window: Int, stack: Pair<ItemStack, Int>?, size: Int = 27) = InventoryContent(window, listOf(stack) + List(size - 1) { null }).encode()

    @Test
    fun `a bucket of water empties as a source, which an empty bucket takes back, swapping the bucket outside creative`() {
        config = config.copy(gameMode = GameMode.SURVIVAL)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.session.give(ItemStack(waterBucket))
        steve.received()
        alex.received()

        steve.clicks(here.copy(y = here.y - 1), held = waterBucket.runtimeId) // The floor under it
        assertEquals(water[0], world.stateAt(here))
        assertEquals(bucket, steve.session.heldStack?.item)
        assertTrue(alex.received().has(LevelSoundEvent("bucket.empty.water", here.centre, -1).encode()), "everyone hears it")

        steve.clicks(here, held = bucket.runtimeId)
        assertEquals(AIR, world.stateAt(here)?.name)
        assertEquals(waterBucket, steve.session.heldStack?.item)
        assertTrue(alex.received().has(LevelSoundEvent("bucket.fill.water", here.centre, -1).encode()))
    }

    @Test
    fun `a chest opens with what it holds, and its lid stays open until the last player looking in closes it`() {
        world[here] = chest
        world.containerAt(here)[0] = SlotStack(ItemStack(stoneItem, 5, block = stone), 900)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")

        steve.clicks(here)
        val opened = steve.received()
        assertTrue(opened.has(ContainerOpen(1, ContainerType.CONTAINER, here, entityId = -1).encode()))
        assertTrue(opened.has(chestWith(1, ItemStack(stoneItem, 5, block = stone) to 900)))
        val lidOpens = BlockEvent(here, BlockEvent.CHANGE_STATE, 1).encode()
        assertTrue(opened.has(lidOpens))
        assertTrue(alex.received().has(LevelSoundEvent("chest.open", here.centre, chest.networkId).encode()))

        alex.clicks(here)
        assertTrue(alex.received().has(ContainerOpen(1, ContainerType.CONTAINER, here, entityId = -1).encode()))
        assertTrue(steve.received().none { it.contentEquals(lidOpens) }) // It was open already

        val lidCloses = BlockEvent(here, BlockEvent.CHANGE_STATE, 0).encode()
        steve.closes(1)
        assertTrue(steve.received().has(ContainerClose(1, ContainerType.CONTAINER).encode()))
        assertTrue(alex.received().none { it.contentEquals(lidCloses) })
        alex.closes(1)
        assertTrue(steve.received().has(lidCloses))
    }

    @Test
    fun `items moved in or out of a chest show to everyone looking into it`() {
        world[here] = chest
        world.containerAt(here)[0] = SlotStack(ItemStack(stoneItem, 5, block = stone), 900)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.clicks(here)
        alex.clicks(here)
        alex.received()

        steve.moves(5, from = Triple(ContainerName.LEVEL_ENTITY, 0, 900), to = Triple(ContainerName.HOTBAR, 0, 0))

        assertNull(world.containerAt(here)[0])
        assertTrue(alex.received().has(chestWith(1, null)))
    }

    @Test
    fun `a double chest is one window, the chest on the left first`() {
        val right = here.copy(x = 3)
        world[here] = chest
        world[right] = chest
        world.pairChest(right)
        world.containerAt(here)[0] = SlotStack(ItemStack(stoneItem, block = stone), 900)
        val steve = Client(1, "Steve", "1111")

        // Facing the chests' fronts, which face south, west is on the left.
        steve.clicks(right)

        assertTrue(steve.received().has(chestWith(1, ItemStack(stoneItem, block = stone) to 900, size = 54)))
    }

    @Test
    fun `a solid block on a chest keeps it shut`() {
        world[here] = chest
        world[here.copy(y = -62)] = stone
        val steve = Client(1, "Steve", "1111")

        steve.clicks(here)

        assertTrue(steve.received().none { it.contentEquals(ContainerOpen(1, ContainerType.CONTAINER, here, entityId = -1).encode()) })
    }

    @Test
    fun `a broken chest drops what it held, and closes for whoever had it open`() {
        world[here] = chest
        world.containerAt(here)[4] = SlotStack(ItemStack(stoneItem, 5, block = stone), 900)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.clicks(here)

        alex.breaks(here)

        assertEquals(listOf(5), world.items.map { it.stack.count })
        assertTrue(steve.received().has(ContainerClose(1, ContainerType.CONTAINER, serverInitiated = true).encode()))
    }

    @Test
    fun `a shulker box keeps what it held in its item, and holds it again once placed`() {
        world[here] = shulkerBox
        world.containerAt(here)[3] = SlotStack(ItemStack(stoneItem, 7, block = stone), 900)
        val steve = Client(1, "Steve", "1111")

        steve.breaks(here)
        val dropped = world.items.single().stack
        assertEquals(shulkerItem, dropped.item)
        val held = dropped.nbt.getList("Items", NbtType.COMPOUND).single()
        assertEquals(listOf("minecraft:stone", 7.toByte(), 3.toByte()), listOf(held.getString("Name"), held.getByte("Count"), held.getByte("Slot")))

        steve.session.give(dropped)
        steve.clicks(BlockPos(4, -64, 0), held = shulkerItem.runtimeId, block = shulkerBox.networkId)
        val placed = BlockPos(4, -63, 0)
        assertEquals(shulkerBox, world.stateAt(placed))
        assertEquals(ItemStack(stoneItem, 7, block = stone), world.containerAt(placed)[3]?.stack)
    }

    @Test
    fun `a barrel opens by its state, and shuts again`() {
        world[here] = barrel
        val steve = Client(1, "Steve", "1111")

        steve.clicks(here)
        assertEquals(openBarrel, world.stateAt(here))
        steve.closes(1)
        assertEquals(barrel, world.stateAt(here))
    }

    @Test
    fun `an ender chest holds each player's own items`() {
        world[here] = enderChest
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.session.give(ItemStack(stoneItem, 5, block = stone)) // The first stack the world numbers
        steve.clicks(here)

        steve.moves(5, from = Triple(ContainerName.HOTBAR, 0, 1), to = Triple(ContainerName.LEVEL_ENTITY, 2, 0))
        alex.clicks(here)

        assertTrue(alex.received().has(chestWith(1, null)))
    }

    @Test
    fun `a furnace opens at work, shows how it's getting on, and gives back experience for what it made`() {
        world[here] = furnace
        val steve = Client(1, "Steve", "1111")
        steve.session.give(ItemStack(rawIron, 2))
        steve.session.give(ItemStack(coal))

        steve.clicks(here)
        val opened = steve.received()
        assertTrue(opened.has(ContainerOpen(1, ContainerType.FURNACE, here, entityId = -1).encode()))
        assertTrue(opened.has(InventoryContent(1, listOf(null, null, null)).encode()))
        assertTrue(opened.has(ContainerSetData(1, ContainerSetData.FURNACE_COOK_TIME, 0).encode()))

        // Only fuel goes under it.
        steve.moves(2, from = Triple(ContainerName.HOTBAR, 0, 1), to = Triple(ContainerName.FURNACE_FUEL, 1, 0))
        assertEquals(null, world.furnaceAt(here)!!.slots[1])
        steve.moves(2, from = Triple(ContainerName.HOTBAR, 0, 1), to = Triple(ContainerName.FURNACE_INGREDIENT, 0, 0), requestId = -3)
        steve.moves(1, from = Triple(ContainerName.HOTBAR, 1, 2), to = Triple(ContainerName.FURNACE_FUEL, 1, 0), requestId = -5)
        steve.received()

        fun tick() {
            clients.map { it.session }.tickMachines(game)
            clients.forEach { it.session.syncWindow() }
        }
        tick()
        assertEquals(litFurnace, world.stateAt(here))
        val lit = steve.received()
        assertTrue(lit.has(UpdateBlock(here, litFurnace.networkId).encode()))
        assertTrue(lit.has(ContainerSetData(1, ContainerSetData.FURNACE_LIT_DURATION, 1600).encode()))
        assertTrue(lit.has(InventorySlot(1, null, 0, window = 1).encode()))

        repeat(399) { tick() }
        val made = world.furnaceAt(here)!!.slots[2]!!
        assertEquals(ItemStack(ironIngot, 2), made.stack)
        assertTrue(steve.received().has(InventorySlot(2, made.stack, made.id, window = 1).encode()))

        steve.moves(2, from = Triple(ContainerName.FURNACE_RESULT, 2, made.id), to = Triple(ContainerName.HOTBAR, 5, 0), requestId = -7)
        assertNull(world.furnaceAt(here)!!.slots[2])
        // Two iron ingots earn 1.4 experience: one orb, or two.
        assertTrue(world.orbs.sumOf { it.value } in 1..2)
    }

    @Test
    fun `a hopper opens as a window of its own, and those looking into a chest see what a hopper passes into it`() {
        val above = BlockPos(here.x, here.y + 1, here.z)
        world[here] = chest
        world[above] = hopper
        world.hopperAt(above)!!.slots[0] = SlotStack(ItemStack(stoneItem, 2, block = stone), 900)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")

        steve.clicks(above)
        val opened = steve.received()
        assertTrue(opened.has(ContainerOpen(1, ContainerType.HOPPER, above, entityId = -1).encode()))
        assertTrue(opened.has(InventoryContent(1, listOf(ItemStack(stoneItem, 2, block = stone) to 900) + List(4) { null }).encode()))
        alex.clicks(here)
        alex.received()

        clients.map { it.session }.tickMachines(game)
        clients.forEach { it.session.syncWindow() }

        val passed = world.containerAt(here)[0]!!
        assertEquals(ItemStack(stoneItem, 1, block = stone), passed.stack)
        assertTrue(alex.received().has(InventorySlot(0, passed.stack, passed.id, window = 1).encode()))
        assertTrue(steve.received().has(InventorySlot(0, ItemStack(stoneItem, 1, block = stone), 900, window = 1).encode()))
    }

    @Test
    fun `a dispenser opens as a window of its own, and fires when a lever on it is switched on, for everyone around to see`() {
        val lever = BlockPos(here.x, here.y + 1, here.z)
        world[here] = dispensers[0]
        world[lever] = levers[0]
        world.dispenserAt(here)!!.slots[0] = SlotStack(ItemStack(stoneItem, 2, block = stone), 900)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")

        steve.clicks(here)
        assertTrue(steve.received().has(ContainerOpen(1, ContainerType.DISPENSER, here, entityId = -1).encode()))
        steve.closes(1)

        alex.clicks(lever)
        steve.received()
        repeat(5) {
            clients.map { it.session }.tickMachines(game)
            clients.map { it.session }.tickPower(game)
        }

        val seen = steve.received()
        assertTrue(seen.has(UpdateBlock(here, dispensers[1].networkId).encode()))
        assertTrue(seen.has(LevelEvent(LevelEvent.SOUND_CLICK, here.centre, 1000).encode()))
        assertTrue(seen.has(LevelEvent(LevelEvent.PARTICLES_SHOOT, here.centre, 5).encode())) // Out of its east side
        assertEquals(ItemStack(stoneItem, 1, block = stone), world.items.single().stack)
    }

    @Test
    fun `everyone around sees a piston's arm move out a tick at a time`() {
        world[here] = piston
        world.setBlockEntity(here, world.pistonArm(here, piston).data())
        val lever = BlockPos(here.x, here.y + 1, here.z)
        world[lever] = levers[0]
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")

        steve.clicks(lever)
        alex.received()
        val players = clients.map { it.session }
        repeat(6) {
            players.tickBlocks(game, dropItems = false)
            players.tickPower(game)
        }

        val progress = alex.received().filter { it[0] == 0x38.toByte() }.map { dataIn(it).getFloat("Progress") }
        assertEquals(listOf(0f, 0.5f, 1f), progress)
        assertEquals(pistonHead, world.stateAt(BlockPos(here.x + 1, here.y, here.z)))
    }

    @Test
    fun `a player walking on a block a piston pushes up rises with it, walking on, and sees it slide up from below them, while everyone else sees it move as it is`() {
        world[here] = upPiston
        world.setBlockEntity(here, world.pistonArm(here, upPiston).data())
        val start = BlockPos(here.x, here.y + 1, here.z)
        world[start] = stone
        val lever = BlockPos(here.x, here.y, here.z + 1)
        world[lever] = levers[0]
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        val landing = BlockPos(here.x, here.y + 2, here.z)
        steve.standsAt(Vec3(here.x + 0.5f, landing.y.toFloat(), here.z + 0.5f), delta = Vec3(0.2f, 0f, 0f))

        alex.clicks(lever)
        steve.received()
        alex.received()
        val players = clients.map { it.session }
        val ticks = (1..6).map {
            players.tickBlocks(game, dropItems = false)
            players.tickPower(game)
            steve.received()
        }

        // Walking east all the while.
        val up = SetActorMotion(steve.entityId, Vec3(0.2f, 0.5f, 0f)).encode()
        val stop = SetActorMotion(steve.entityId, Vec3(0.2f, 0f, 0f)).encode()
        val seen = ticks.flatten()
        assertEquals(listOf(up, up, stop).map { it.toList() }, seen.filter { it[0] == 0x28.toByte() }.map { it.toList() })
        assertTrue(seen.has(UpdateBlock(landing, AIR_NETWORK_ID).encode()))
        assertTrue(!seen.has(UpdateBlock(landing, movingBlock.networkId).encode()))
        // Where it started, sliding up from there, as if pulled.
        assertTrue(seen.has(UpdateBlock(start, movingBlock.networkId).encode()))
        val drawn = seen.filter { it[0] == 0x38.toByte() }.map(::dataIn).single { it.getString("id") == "MovingBlock" }
        assertEquals(listOf(start.x, start.y, start.z, 0), listOf(drawn.getInt("x"), drawn.getInt("y"), drawn.getInt("z"), drawn.getByte("expanding").toInt()))
        assertEquals("minecraft:stone", drawn.getCompound("movingBlock").getString("name"))
        // Then as it is, once it's landed: the stone, and a tick later, once that's drawn, the piston's head under it.
        val landed = ticks.indexOfFirst { it.has(UpdateBlock(landing, stone.networkId).encode()) }
        val headBack = ticks.indexOfFirst { it.has(UpdateBlock(start, upPistonHead.networkId).encode()) }
        assertTrue(landed > ticks.indexOfFirst { it.has(UpdateBlock(start, movingBlock.networkId).encode()) })
        assertEquals(landed + 1, headBack)

        val watched = alex.received()
        assertTrue(watched.has(UpdateBlock(landing, movingBlock.networkId).encode()))
        assertTrue(watched.none { it[0] == 0x28.toByte() })
        assertEquals(stone, world.stateAt(landing))
    }

    @Test
    fun `a player a piston's head pushes sees the head once it's out, though the client hasn't yet said they've moved`() {
        world[here] = piston
        world.setBlockEntity(here, world.pistonArm(here, piston).data())
        val lever = BlockPos(here.x, here.y + 1, here.z)
        world[lever] = levers[0]
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        val front = BlockPos(here.x + 1, here.y, here.z)
        steve.standsAt(Vec3(front.x + 0.5f, front.y.toFloat(), front.z + 0.5f))

        alex.clicks(lever)
        steve.received()
        val players = clients.map { it.session }
        val ticks = (1..8).map {
            players.tickBlocks(game, dropItems = false)
            players.tickPower(game)
            steve.received()
        }

        val shown = ticks.flatten().filter { it.contentEquals(UpdateBlock(front, AIR_NETWORK_ID).encode()) || it.contentEquals(UpdateBlock(front, pistonHead.networkId).encode()) }
        assertEquals(listOf(UpdateBlock(front, AIR_NETWORK_ID), UpdateBlock(front, pistonHead.networkId)).map { it.encode().toList() }, shown.map { it.toList() })
    }

    @Test
    fun `a chest a piston moves closes for whoever had it open, and keeps what it held`() {
        world[here] = piston
        world.setBlockEntity(here, world.pistonArm(here, piston).data())
        val lever = BlockPos(here.x, here.y + 1, here.z)
        world[lever] = levers[0]
        val box = BlockPos(here.x + 1, here.y, here.z)
        world[box] = chest
        world.containerAt(box)[0] = SlotStack(ItemStack(stoneItem, 5, block = stone), 900)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.clicks(box)
        steve.received()

        alex.clicks(lever)
        val players = clients.map { it.session }
        repeat(6) {
            players.tickBlocks(game, dropItems = false)
            players.tickPower(game)
        }

        assertTrue(steve.received().has(ContainerClose(1, ContainerType.CONTAINER, serverInitiated = true).encode()))
        val moved = BlockPos(here.x + 2, here.y, here.z)
        assertEquals(chest, world.stateAt(moved))
        assertEquals(5, world.containerAt(moved)[0]?.stack?.count)
    }

    @Test
    fun `a shulker box a piston breaks drops with what it held inside it, even in creative`() {
        world[here] = piston
        world.setBlockEntity(here, world.pistonArm(here, piston).data())
        val lever = BlockPos(here.x, here.y + 1, here.z)
        world[lever] = levers[0]
        val box = BlockPos(here.x + 1, here.y, here.z)
        world[box] = shulkerBox
        val held = ItemStack(stoneItem, 5, block = stone)
        world.containerAt(box)[3] = SlotStack(held, 900)
        val alex = Client(2, "Alex", "2222")

        alex.clicks(lever)
        val players = clients.map { it.session }
        repeat(6) {
            players.tickBlocks(game, dropItems = false)
            players.tickPower(game)
        }

        val dropped = world.items.single().stack
        assertEquals(shulkerItem.name, dropped.item.name)
        assertEquals(ItemStacks.holding(listOf(3 to held)), dropped.nbt)
    }

    @Test
    fun `a player puts a book on a lectern, which everyone with the chunk sees and hears, and outside creative it goes from their hand, then opens it to read`() {
        config = config.copy(gameMode = GameMode.SURVIVAL)
        world[here] = lectern
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.session.give(ItemStack(writtenBook))
        steve.received()
        alex.received()

        steve.clicks(here, held = writtenBook.runtimeId)
        assertEquals(writtenBook, world.lecternBook(here)?.item)
        assertTrue(steve.received().has(InventorySlot(0, null, 0).encode()))
        val shown = alex.received()
        assertTrue(shown.any { it[0] == 0x38.toByte() && dataIn(it).getString("id") == "Lectern" && dataIn(it).getByte("hasBook") == 1.toByte() })
        assertTrue(shown.has(LevelSoundEvent(BOOK_PUT, here.centre, -1).encode()))

        // Clicked again, the book opens to read, with no window of its own, and closing it gets an answer.
        steve.clicks(here)
        assertTrue(steve.received().has(ContainerOpen(-1, ContainerType.LECTERN, here, entityId = -1).encode()))
        steve.closes(0xFF)
        assertTrue(steve.received().has(ContainerClose(0xFF, 0).encode()))
    }

    @Test
    fun `a crafter opens with its grid, and a slot a player switches off shows for everyone with the chunk`() {
        world[here] = crafter
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.clicks(here)
        assertTrue(steve.received().has(ContainerOpen(1, ContainerType.CRAFTER, here, entityId = -1).encode()))
        alex.received()

        steve.togglesCrafterSlot(here, 4, disabled = true)
        assertEquals(1 shl 4, world.crafterAt(here)?.disabled)
        assertTrue(alex.received().any { it[0] == 0x38.toByte() && dataIn(it).getShort("disabled_slots").toInt() == 1 shl 4 })
    }

    /** Where a beacon stands, on a pyramid of one layer of iron. */
    private val beaconAt = BlockPos(2, -62, 0)

    private fun buildBeacon() {
        for (x in -1..1) for (z in -1..1) world[BlockPos(beaconAt.x + x, beaconAt.y - 1, beaconAt.z + z)] = ironBlock
        world[beaconAt] = beacon
    }

    @Test
    fun `a beacon opens for its powers, and paying for one shows everyone with the chunk the beacon's new powers, with their sound`() {
        config = config.copy(gameMode = GameMode.SURVIVAL)
        buildBeacon()
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        val payment = world.newStackId() + 1
        steve.session.give(ItemStack(ironIngot))
        steve.clicks(beaconAt)
        assertTrue(steve.received().has(ContainerOpen(1, ContainerType.BEACON, beaconAt, entityId = -1).encode()))
        alex.received()

        steve.paysBeacon(payment, Effect.SPEED)

        assertEquals(Effect.SPEED to 0, world.beaconPowers(beaconAt))
        val shown = alex.received()
        assertTrue(shown.any { it[0] == 0x38.toByte() && dataIn(it).getString("id") == "Beacon" && dataIn(it).getInt("primary") == Effect.SPEED })
        assertTrue(shown.has(LevelSoundEvent("beacon.power", beaconAt.centre, -1).encode()))
    }

    @Test
    fun `players a shining beacon reaches get its powers, which the client moves them by itself, and the powers wear off away from it`() {
        buildBeacon()
        world.setBeaconPowers(beaconAt, Effect.SPEED, 0)
        val steve = Client(1, "Steve", "1111")
        val sessions = clients.map { it.session }

        sessions.tickBeacons(game)

        val got = steve.received()
        assertTrue(got.has(LevelSoundEvent("beacon.activate", beaconAt.centre, -1).encode()))
        assertTrue(got.has(MobEffect(1, MobEffect.ADD, Effect.SPEED, duration = 200, ambient = true).encode()))
        assertTrue(got.none { it[0] == 29.toByte() }, "no walking speed, which would undo sprinting")
        repeat(200) { steve.session.tickEffects() }
        assertTrue(steve.received().has(MobEffect(1, MobEffect.REMOVE, Effect.SPEED).encode()))
    }

    @Test
    fun `a cartography table opens its own screen`() {
        world[here] = cartographyTable
        val steve = Client(1, "Steve", "1111")
        steve.clicks(here)
        assertTrue(steve.received().has(ContainerOpen(1, ContainerType.CARTOGRAPHY, here, entityId = -1).encode()))
    }

    @Test
    fun `an empty map used in the air becomes a map of where the player is, drawn at once, which the client asks to see, and they draw as they hold it`() {
        config = config.copy(gameMode = GameMode.SURVIVAL)
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.session.give(ItemStack(emptyMap))

        steve.usesInAir(emptyMap.runtimeId)

        val map = world.maps[steve.session.heldStack?.mapId!!]!!
        assertEquals(listOf(0, 0, 0, 1), listOf(map.scale, map.centreX, map.centreZ, map.nameIndex))
        assertEquals(5, world.maps.all.size, "with a parent at each scale above it")
        assertTrue(map.pixels.all { it == 0xFF707070.toInt() })
        steve.received()
        steve.asksAboutMap(map.id)
        val lineage = world.maps.lineage(map)
        assertTrue(steve.received().has(ClientboundMapItemData(map.id, BlockPos(0, 0, 0), 0, locked = false, patch = map.patch(), partOf = lineage).encode()))

        // Something changing within reach shows as the player holds the map, once, and only to clients that asked about it.
        world[BlockPos(5, -63, 5)] = stone
        val sessions = clients.map { it.session }
        steve.session.drawHeldMaps()
        sessions.syncMaps(world)
        assertEquals(1, steve.received().count { it[0] == 0x43.toByte() })
        steve.session.drawHeldMaps()
        sessions.syncMaps(world)
        assertEquals(0, steve.received().count { it[0] == 0x43.toByte() })
        assertTrue(alex.received().none { it[0] == 0x43.toByte() })

        // The client learned of the maps it zooms out to with it, and hears them drawn without asking.
        val parent = world.maps[map.parentId!!]!!
        parent[0, 0] = 1
        sessions.syncMaps(world)
        assertEquals(1, steve.received().count { it[0] == 0x43.toByte() })
    }

    @Test
    fun `a glass pane locks a map at a cartography table as the copy the client numbers as it goes in, which it sees then, and gets`() {
        config = config.copy(gameMode = GameMode.SURVIVAL)
        world[here] = cartographyTable
        val steve = Client(1, "Steve", "1111")
        val first = world.newStackId()
        steve.session.give(ItemStack(emptyMap))
        steve.usesInAir(emptyMap.runtimeId)
        steve.session.give(ItemStack(glassPane))
        val (mapStack, paneStack) = first + 2 to first + 3
        val map = world.maps[steve.session.heldStack?.mapId!!]!!
        steve.clicks(here)
        steve.moves(1, from = Triple(ContainerName.HOTBAR, 0, mapStack), to = Triple(ContainerName.CARTOGRAPHY_INPUT, CARTOGRAPHY_INPUT_SLOT, 0), requestId = -3)
        steve.moves(1, from = Triple(ContainerName.HOTBAR, 1, paneStack), to = Triple(ContainerName.CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_ADDITIONAL_SLOT, 0), requestId = -5)
        steve.received()

        steve.picksLockedCopy(map.id, 1971533702)

        val locked = world.maps[1971533702]
        assertTrue(locked != null && locked.locked && locked.nameIndex == map.nameIndex)
        assertTrue(steve.received().has(ClientboundMapItemData(locked.id, BlockPos(0, 0, 0), 0, locked = true, patch = locked.patch()).encode()))

        steve.cartographs(mapStack, paneStack)

        val onCursor = steve.received().filter { it[0] == 0x32.toByte() }.map { Unpooled.wrappedBuffer(it).skipBytes(1).readVarUInt() }
        assertEquals(listOf(UI_WINDOW), onCursor, "what's on the cursor, as Kore made it")
    }

    @Test
    fun `a player who leaves picks up where they left off next time, where they were, with their game mode, items and experience, facing south again`() {
        config = config.copy(gameMode = GameMode.SURVIVAL)
        val steve = Client(1, "Steve", "1111")
        steve.session.give(ItemStack(coal, 5))
        steve.standsAt(Vec3(10.5f, -63f, 3.5f), pitch = 30f, yaw = 120f)
        steve.session.leave()

        val again = Client(2, "Steve", "1111")

        assertEquals(Vec3(10.5f, -63f + PLAYER_EYE_HEIGHT, 3.5f), again.session.position)
        assertEquals(0f to 0f, again.session.yaw to again.session.pitch, "looking straight ahead, toward positive z")
        assertEquals(ItemStack(coal, 5), again.session.heldStack)
        assertEquals(GameMode.SURVIVAL, again.session.gameMode)
        val alex = Client(3, "Alex", "2222")
        assertNull(alex.session.heldStack, "someone else starts afresh")
    }

    @Test
    fun `a player who leaves keeps their effects, as long as each had left, and sees them again as they come back`() {
        val steve = Client(1, "Steve", "1111")
        steve.session.setEffect(EffectLevel(Effect.SPEED, 1), 600, particles = true)
        steve.session.setEffect(EffectLevel(Effect.NIGHT_VISION), INFINITE_EFFECT, particles = false)
        steve.session.giveEffect(EffectLevel(Effect.HEALTH_BOOST), 600, ambient = true)
        repeat(100) { steve.session.tickEffects() }
        steve.session.leave()

        val again = Client(2, "Steve", "1111")

        assertTrue(again.spawned.has(MobEffect(2, MobEffect.ADD, Effect.SPEED, 1, 500).encode()))
        assertTrue(again.spawned.has(MobEffect(2, MobEffect.ADD, Effect.NIGHT_VISION, 0, INFINITE_EFFECT, particles = false).encode()))
        assertTrue(again.spawned.has(MobEffect(2, MobEffect.ADD, Effect.HEALTH_BOOST, 0, 500, ambient = true).encode()))
        assertEquals(24f, again.session.vitals.maxHealth, "Health Boost's hearts come back with it")
        repeat(500) { again.session.tickEffects() }
        assertTrue(again.received().has(MobEffect(2, MobEffect.REMOVE, Effect.SPEED).encode()))
    }

    /** The data a BlockActorData packet gives a block. */
    private fun dataIn(packet: ByteArray): NbtMap {
        val buffer = Unpooled.wrappedBuffer(packet)
        buffer.readVarUInt()
        buffer.readBlockPos()
        return NbtUtils.createNetworkReader(ByteBufInputStream(buffer)).readTag() as NbtMap
    }

    @Test
    fun `an enchanting table offers what it would enchant the item put in it with`() {
        world[here] = enchantingTable
        val steve = Client(1, "Steve", "1111")
        steve.session.give(ItemStack(sword))
        steve.clicks(here)
        assertTrue(steve.received().has(ContainerOpen(1, ContainerType.ENCHANTMENT, here, entityId = -1).encode()))

        steve.moves(1, from = Triple(ContainerName.HOTBAR, 0, 1), to = Triple(ContainerName.ENCHANTING_INPUT, 14, 0))
        assertEquals(listOf(3), steve.received().mapNotNull(::offersIn))

        steve.moves(1, from = Triple(ContainerName.ENCHANTING_INPUT, 14, 1), to = Triple(ContainerName.HOTBAR, 0, 0), requestId = -3)
        assertTrue(steve.received().has(PlayerEnchantOptions(emptyList()).encode()))
    }

    /** How many offers [packet] makes, if it's a `PlayerEnchantOptions`, or null for another packet. */
    private fun offersIn(packet: ByteArray): Int? {
        val buffer = Unpooled.wrappedBuffer(packet)
        return if (buffer.readVarUInt() == PLAYER_ENCHANT_OPTIONS) buffer.readVarUInt() else null
    }

    @Test
    fun `a crafting table opens its grid, and what's left in it goes back among the player's items when it closes`() {
        world[here] = craftingTable
        val steve = Client(1, "Steve", "1111")
        steve.session.give(ItemStack(stoneItem, 5, block = stone))

        // Before the table opens, there's only the inventory's grid.
        steve.moves(5, from = Triple(ContainerName.HOTBAR, 0, 1), to = Triple(ContainerName.CRAFTING_INPUT, 32, 0))
        steve.clicks(here)
        assertTrue(steve.received().has(ContainerOpen(1, ContainerType.WORKBENCH, here, entityId = -1).encode()))
        steve.moves(5, from = Triple(ContainerName.HOTBAR, 0, 1), to = Triple(ContainerName.CRAFTING_INPUT, 32, 0), requestId = -3)
        steve.received()

        steve.closes(1)
        val closed = steve.received()
        assertTrue(closed.has(InventorySlot(32, null, 0, UI_WINDOW).encode()))
        assertTrue(closed.any { it.contentEquals(InventorySlot(0, ItemStack(stoneItem, 5, block = stone), 2).encode()) })
    }
}
