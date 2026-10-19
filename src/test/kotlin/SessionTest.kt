package org.koremc

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtUtils
import org.koremc.auth.MultiplayerTokenVerifier
import org.koremc.auth.TestAuthority
import org.koremc.auth.XUID
import org.koremc.auth.ecKeyPair
import org.koremc.inventory.HOTBAR_SIZE
import org.koremc.protocol.Animate
import org.koremc.protocol.BatchCodec
import org.koremc.protocol.ARMOUR_WINDOW
import org.koremc.protocol.ActorDataId
import org.koremc.protocol.ActorEvent
import org.koremc.protocol.ActorFlag
import org.koremc.protocol.Attribute
import org.koremc.protocol.BiomeDefinitionList
import org.koremc.protocol.BlockActorData
import org.koremc.protocol.BlockDefinitions
import org.koremc.protocol.BlockEvent
import org.koremc.protocol.ContainerClose
import org.koremc.protocol.ContainerOpen
import org.koremc.protocol.DeathInfo
import org.koremc.protocol.FloatItem
import org.koremc.protocol.GameMode
import org.koremc.protocol.GameVersion
import org.koremc.protocol.LevelEvent
import org.koremc.protocol.InventorySlot
import org.koremc.protocol.LevelSoundEvent
import org.koremc.protocol.MobEffect
import org.koremc.protocol.OpenSign
import org.koremc.protocol.PlayerStartItemCooldown
import org.koremc.world.strike
import org.koremc.protocol.SetTime
import org.koremc.protocol.AddLightning
import org.koremc.protocol.UpdateSoundData
import org.koremc.protocol.RecordStarted
import org.koremc.protocol.PlaySound
import org.koremc.protocol.RemoveActor
import org.koremc.protocol.Respawn
import org.koremc.protocol.TakeItemActor
import org.koremc.protocol.UpdateAttributes
import org.koremc.protocol.UpdateBlock
import org.koremc.protocol.SetActorData
import org.koremc.protocol.ShowEquipment
import org.koremc.protocol.SubChunk
import org.koremc.protocol.SubChunkEntry
import org.koremc.protocol.SubChunkOffset
import org.koremc.protocol.SubChunkResult
import org.koremc.protocol.subChunkPayload
import org.koremc.protocol.Text
import org.koremc.protocol.flagsItem
import org.koremc.protocol.ItemRegistry
import org.koremc.protocol.ResourcePackStack
import org.koremc.protocol.clientDataToken
import org.koremc.protocol.connectionRequest
import org.koremc.protocol.encode
import org.koremc.protocol.loginPacket
import org.koremc.protocol.tokenAuthentication
import org.koremc.protocol.writeBlockPos
import org.koremc.protocol.writeByteArray
import org.koremc.protocol.writeString
import org.koremc.protocol.writeVarInt
import org.koremc.protocol.readVarUInt
import org.koremc.protocol.writeVarUInt
import org.koremc.protocol.writeVarULong
import org.koremc.registry.AIR
import org.koremc.registry.AIR_NETWORK_ID
import org.koremc.registry.BiomeDefinition
import org.koremc.registry.BlockDefinition
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import org.koremc.registry.CreativeCategory
import org.koremc.registry.CreativeGroup
import org.koremc.registry.CreativeInventory
import org.koremc.registry.CreativeItem
import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.Food
import org.koremc.registry.FoodAction
import org.koremc.registry.ItemCooldown
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.ItemVersion
import org.koremc.registry.MENDING
import org.koremc.registry.Mining
import org.koremc.registry.ToolKind
import org.koremc.registry.damage
import org.koremc.registry.dyedColour
import org.koremc.registry.withDamage
import org.koremc.registry.withEnchantments
import org.koremc.world.BlockPos
import org.koremc.world.Effect
import org.koremc.world.EffectLevel
import org.koremc.world.ChunkPos
import org.koremc.world.DamageCause
import org.koremc.world.centre
import org.koremc.world.Drops
import org.koremc.world.tickItems
import org.koremc.world.Face
import org.koremc.world.Features
import org.koremc.world.FlatWorld
import org.koremc.world.PLAYER_EYE_HEIGHT
import org.koremc.world.PLAYER_MAX_AIR
import org.koremc.world.blockStateNbt
import org.koremc.world.Vec3
import org.koremc.world.chunksAround
import org.koremc.world.frameData
import org.koremc.world.frameItem
import org.koremc.world.plantInPot
import org.koremc.world.side
import org.koremc.world.signData
import org.slf4j.LoggerFactory
import java.security.interfaces.ECPublicKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROTOCOL = 2193

// Packet IDs, for telling apart what the client got.
private const val ADD_ACTOR = 13
private const val REMOVE_ACTOR = 14

// Input flags, by their numbers in PlayerAuthInput.
private const val SNEAKING = 8
private const val START_SPRINTING = 25
private const val STOP_SPRINTING = 26
private const val START_SWIMMING = 29
private const val STOP_SWIMMING = 30
private const val START_GLIDING = 32
private const val START_FLYING = 42
private const val VERTICAL_COLLISION = 50

/** The runtime IDs of the oak planks, oak door, oak fence, wooden pickaxe, stone hoe, poppy, bucket, music disc and leather cap items. */
private const val PLANKS_ITEM = 5
private const val DOOR_ITEM = 324
private const val FENCE_ITEM = 85
private const val PICKAXE_ITEM = 310
private const val HOE_ITEM = 333
private const val POPPY_ITEM = 38
private const val WATER_BUCKET_ITEM = 362
private const val BUCKET_ITEM = 361
private const val DISC_ITEM = 545
private const val HELMET_ITEM = 338
private const val ZOMBIE_HEAD_ITEM = 339
private const val APPLE_ITEM = 257
private const val STEW_ITEM = 260
private const val BOWL_ITEM = 281
private const val POTION_ITEM = 427
private const val GLASS_BOTTLE_ITEM = 428
private const val MILK_ITEM = 393
private const val CHORUS_ITEM = 597
private const val CANDLE_ITEM = 662
private const val SWORD_ITEM = 320
private const val TOTEM_ITEM = 568

/** The PlayerAction for breaking a block in creative. */
private const val CREATIVE_DESTROY_BLOCK = 13

// The steps of breaking a block in survival, numbered as PlayerActions are.
private const val START_DESTROY = 0
private const val ABORT_DESTROY = 1
private const val STOP_DESTROY = 2
private const val CRACK = 18

class SessionTest {
    private val authority = TestAuthority()
    private val client = ecKeyPair()
    private val clientKey = client.public as ECPublicKey
    private val authenticator = LoginAuthenticator(MultiplayerTokenVerifier(keys = { authority.keys }), onlineMode = true)

    /** Survival, unless a test switches to creative before the session starts. */
    private var config = ServerConfig(protocol = PROTOCOL, viewDistance = 8, gameMode = GameMode.SURVIVAL)
    private val itemRegistry = ItemRegistry(listOf(ItemDefinition("minecraft:stick", 323, componentBased = false, ItemVersion.NONE)))
    private val biomes = BiomeDefinitionList(listOf(BiomeDefinition("minecraft:plains", null, 0.8f, 0.4f, 0f, 0.125f, 0.05f, 0, rain = true, listOf("plains"))))

    // A world of one layer of stone, and a creative inventory of oak planks.
    private val air = BlockState(AIR, NbtMap.EMPTY, version = 18168865)
    private val stone = BlockState("minecraft:stone", NbtMap.EMPTY, version = 18168865)
    private val planks = BlockState("minecraft:oak_planks", NbtMap.EMPTY, version = 18168865)
    private val torch = BlockState("minecraft:torch", NbtMap.builder().putString("torch_facing_direction", "east").build(), version = 18168865)
    private val levers = (0..1).map { on -> state("minecraft:lever", "lever_direction" to "up_east_west", "open_bit" to on.toByte()) }
    private val buttons = (0..1).map { pressed -> state("minecraft:wooden_button", "button_pressed_bit" to pressed.toByte(), "facing_direction" to 1) }
    private val doors = listOf("south", "west", "north", "east").flatMap { direction ->
        (0..7).map { bits ->
            state(
                "minecraft:wooden_door",
                "minecraft:cardinal_direction" to direction,
                "door_hinge_bit" to (bits and 1).toByte(),
                "open_bit" to (bits shr 1 and 1).toByte(),
                "upper_block_bit" to (bits shr 2).toByte(),
            )
        }
    }
    /** Oak fences, joined to the east, the west, both or neither. */
    private val fences = (0..3).map { joins ->
        state(
            "minecraft:oak_fence",
            "minecraft:connection_north" to 0.toByte(),
            "minecraft:connection_east" to (joins and 1).toByte(),
            "minecraft:connection_south" to 0.toByte(),
            "minecraft:connection_west" to (joins shr 1).toByte(),
        )
    }
    private val sand = BlockState("minecraft:sand", NbtMap.EMPTY, version = 18168865)
    private val water = listOf("minecraft:water", "minecraft:flowing_water").map { BlockState(it, NbtMap.builder().putInt("liquid_depth", 0).build(), version = 18168865) }
    /** An oak sign on the floor, facing south. */
    private val sign = state("minecraft:standing_sign", "ground_sign_direction" to 0)
    private val grass = BlockState("minecraft:grass_block", NbtMap.EMPTY, version = 18168865)
    private val farmland = state("minecraft:farmland", "moisturized_amount" to 0)
    private val dirt = BlockState("minecraft:dirt", NbtMap.EMPTY, version = 18168865)
    private val wheat = state("minecraft:wheat", "growth" to 0)
    /** An item frame on the floor, and a flower pot, empty and full. */
    private val frame = state("minecraft:frame", "facing_direction" to 1, "item_frame_map_bit" to 0.toByte(), "item_frame_photo_bit" to 0.toByte())
    private val pots = (0..1).map { full -> state("minecraft:flower_pot", "update_bit" to full.toByte()) }
    /** Cake with each number of bites out of it, and cake with a candle, out and lit. */
    private val cakes = (0..6).map { bites -> state("minecraft:cake", "bite_counter" to bites) }
    private val candleCakes = (0..1).map { lit -> state("minecraft:candle_cake", "lit" to lit.toByte()) }
    private val poppy = BlockState("minecraft:poppy", NbtMap.EMPTY, version = 18168865)
    /** A cauldron at each level of water, by level, a jukebox and a note block. */
    private val cauldrons = (0..6).map { level -> state("minecraft:cauldron", "cauldron_liquid" to "water", "fill_level" to level) }
    private val jukebox = BlockState("minecraft:jukebox", NbtMap.EMPTY, version = 18168865)
    private val noteBlock = BlockState("minecraft:noteblock", NbtMap.EMPTY, version = 18168865)
    private val fire = state("minecraft:fire", "age" to 0)
    private val zombieHead = state("minecraft:zombie_head", "facing_direction" to 1)
    private val furnace = state("minecraft:furnace", "minecraft:cardinal_direction" to "north")
    private val palette = BlockPalette(listOf(air, stone, planks, torch, sand, sign, grass, farmland, dirt, wheat, frame, poppy, jukebox, noteBlock, fire, zombieHead, furnace) + pots + cauldrons + levers + buttons + doors + fences + water + cakes + candleCakes)
    private val world = FlatWorld(palette, listOf(stone))
    private val creative = CreativeInventory(
        listOf(CreativeGroup(CreativeCategory.CONSTRUCTION, "", icon = null)),
        listOf(
            CreativeItem(ItemStack(ItemDefinition("minecraft:oak_planks", PLANKS_ITEM, componentBased = false, ItemVersion.NONE), block = planks), group = 0),
            // Door items place their door, without a block state of their own.
            CreativeItem(ItemStack(ItemDefinition("minecraft:wooden_door", DOOR_ITEM, componentBased = false, ItemVersion.NONE)), group = 0),
            CreativeItem(ItemStack(ItemDefinition("minecraft:oak_fence", FENCE_ITEM, componentBased = false, ItemVersion.NONE), block = fences[0]), group = 0),
        ),
    )

    private val pickaxe = ItemDefinition("minecraft:wooden_pickaxe", PICKAXE_ITEM, componentBased = false, ItemVersion.NONE, maxDurability = 59)
    private val hoe = ItemDefinition("minecraft:stone_hoe", HOE_ITEM, componentBased = false, ItemVersion.NONE, maxDurability = 131)
    private val poppyItem = ItemDefinition("minecraft:poppy", POPPY_ITEM, componentBased = false, ItemVersion.NONE)
    private val waterBucket = ItemDefinition("minecraft:water_bucket", WATER_BUCKET_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 1)
    private val bucket = ItemDefinition("minecraft:bucket", BUCKET_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 16)
    private val disc = ItemDefinition("minecraft:music_disc_cat", DISC_ITEM, componentBased = true, ItemVersion.NONE, maxStackSize = 1)
    private val helmet = ItemDefinition("minecraft:leather_helmet", HELMET_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 55)
    private val zombieHeadItem = ItemDefinition("minecraft:zombie_head", ZOMBIE_HEAD_ITEM, componentBased = false, ItemVersion.NONE)
    private val apple = ItemDefinition("minecraft:apple", APPLE_ITEM, componentBased = false, ItemVersion.NONE, food = Food(4, 0.3f))
    private val stew = ItemDefinition("minecraft:mushroom_stew", STEW_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 1, food = Food(6, 0.6f, leaves = "minecraft:bowl"))
    private val bowl = ItemDefinition("minecraft:bowl", BOWL_ITEM, componentBased = false, ItemVersion.NONE)
    private val potion = ItemDefinition(
        "minecraft:potion", POTION_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 1,
        food = Food(0, 0f, canAlwaysEat = true, leaves = "minecraft:glass_bottle", action = FoodAction.POTION),
    )
    private val glassBottle = ItemDefinition("minecraft:glass_bottle", GLASS_BOTTLE_ITEM, componentBased = false, ItemVersion.NONE)
    private val milk = ItemDefinition(
        "minecraft:milk_bucket", MILK_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 1,
        food = Food(0, 0f, canAlwaysEat = true, leaves = "minecraft:bucket", action = FoodAction.MILK),
    )
    private val chorusFruit = ItemDefinition(
        "minecraft:chorus_fruit", CHORUS_ITEM, componentBased = false, ItemVersion.NONE,
        food = Food(4, 0.3f, canAlwaysEat = true, action = FoodAction.CHORUS_TELEPORT, cooldown = ItemCooldown("chorusfruit", 20)),
    )
    private val candle = ItemDefinition("minecraft:candle", CANDLE_ITEM, componentBased = false, ItemVersion.NONE)
    private val sword = ItemDefinition("minecraft:iron_sword", SWORD_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 250)
    private val totem = ItemDefinition("minecraft:totem_of_undying", TOTEM_ITEM, componentBased = false, ItemVersion.NONE, maxStackSize = 1)

    private val sent = mutableListOf<ByteArray>()

    /** Tasks the session has asked to run later, by how many ticks from then. Tests run them. */
    private val scheduled = mutableListOf<Pair<Int, () -> Unit>>()
    private val blockDefinitions = BlockDefinitions(listOf(BlockDefinition("minecraft:white_wool_stairs", NbtMap.builder().putInt("molangVersion", 13).build())))
    /** Stone takes 150 ticks to break by hand, as it needs a pickaxe. */
    private val mining = mapOf(stone.name to Mining(1.5, ToolKind.PICKAXE, harvestTier = 0))
    private val game = GameData(
        world, listOf(itemRegistry, biomes), blockDefinitions, creative, mining, Drops(creative.items.map { it.stack.item }, creative, palette),
        ItemStacks(creative.items.map { it.stack.item } + pickaxe + hoe + poppyItem + waterBucket + bucket + disc + helmet + zombieHeadItem + apple + stew + bowl + potion + glassBottle + milk + chorusFruit + candle + sword + totem, creative, palette),
    )
    private val session: Session by lazy {
        // The only player, so the only one to see what they change.
        Session(
            config, game, networkId = "9876543210123456789", entityId = 7, offerKey = clientKey, authenticator,
            viewers = { listOf(session) },
            schedule = { ticks, task -> scheduled += ticks to task },
        ) { sent += it }
    }

    /** The client's side of batching, which switches on compression when NetworkSettings arrives. */
    private val batches = BatchCodec()

    @Test
    fun `a matching protocol gets network settings`() {
        clientSends(requestNetworkSettings(PROTOCOL))

        assertContentEquals(bytes(0x8F, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), receivedPacket())
    }

    @Test
    fun `a client on any version Kore speaks gets network settings`() {
        clientSends(requestNetworkSettings(GameVersion.V1_26_60.protocol))

        assertContentEquals(bytes(0x8F, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00), receivedPacket())
    }

    @Test
    fun `older clients are told they're out of date`() {
        clientSends(requestNetworkSettings(PROTOCOL - 1))

        assertContentEquals(bytes(0x02, 0x00, 0x00, 0x00, 0x01), receivedPacket())
    }

    @Test
    fun `a client between versions Kore speaks is told it's out of date`() {
        clientSends(requestNetworkSettings(PROTOCOL + 1))

        assertContentEquals(bytes(0x02, 0x00, 0x00, 0x00, 0x01), receivedPacket())
    }

    @Test
    fun `newer clients are told the server is out of date, and nothing more`() {
        clientSends(requestNetworkSettings(GameVersion.NEWEST.protocol + 1))
        clientSends(requestNetworkSettings(PROTOCOL))

        assertEquals(1, sent.size)
        assertContentEquals(bytes(0x02, 0x00, 0x00, 0x00, 0x02), receivedPacket())
    }

    @Test
    fun `an authenticated login gets login success and the pack list together`() {
        negotiateSettings()

        // The skin makes the batch big enough to be compressed, as real logins are.
        clientSends(login(authority.multiplayerToken(clientKey), skinData = "A".repeat(4096)))

        val player = session.player
        assertEquals("Steve", player?.displayName)
        assertEquals(XUID, player?.xuid)
        assertEquals(true, player?.verified)
        assertEquals("1.26.50", session.login?.clientInfo?.gameVersion)

        val (loginSuccess, packsInfo) = batches.decode(sent.single())
        assertContentEquals(bytes(0x02, 0x00, 0x00, 0x00, 0x00), loginSuccess)
        assertEquals(0x06, packsInfo[0].toInt())
    }

    @Test
    fun `resource packs end with start game, the item registry and the biomes`() {
        logIn()

        clientSends(clientCacheStatus(supported = true))
        clientSends(packResponse(2, "downloadingfinished"))
        assertContentEquals(ResourcePackStack(baseGameVersion = "1.26.50").encode(), receivedPacket())
        assertFalse(session.isLoadingWorld)

        clientSends(packResponse(3, "resourcepackstackfinished"))
        val (jigsaws, voxelShapes, startGame, registry, biomeList) = batches.decode(sent.last())
        // The client disconnects unless these two come before StartGame.
        assertEquals(0xB9, jigsaws[0].toInt() and 0xFF)
        assertEquals(0xD1, voxelShapes[0].toInt() and 0xFF)
        assertEquals(0x0B, startGame[0].toInt())
        assertEquals(0x0E, startGame[1].toInt()) // Entity ID 7, zigzagged
        assertTrue("minecraft:white_wool_stairs" in startGame.decodeToString()) // The block definitions are in there
        assertContentEquals(itemRegistry.encode(), registry)
        assertContentEquals(biomes.encode(), biomeList)
        assertTrue(session.isLoadingWorld)
        assertTrue(session.cacheSupported)
    }

    @Test
    fun `the chunk radius is what the client asks for, within kore's view distance`() {
        loadWorld()

        clientSends(requestChunkRadius(radius = 6, maxRadius = 16))
        assertContentEquals(bytes(0x46, 0x0C), receivedPackets().first())

        clientSends(requestChunkRadius(radius = 32, maxRadius = 32))
        assertContentEquals(bytes(0x46, 0x10), receivedPackets().first())
        assertEquals(8, session.chunkRadius)

        clientSends(requestChunkRadius(radius = 12, maxRadius = 4))
        assertEquals(4, session.chunkRadius)
    }

    @Test
    fun `chunks in view go out nearest first, then the player may spawn`() {
        loadWorld()

        clientSends(requestChunkRadius(radius = 2, maxRadius = 2))
        val packets = receivedPackets()

        // ChunkRadiusUpdated, NetworkChunkPublisherUpdate, the 13 chunks within two of the spawn, then PlayStatus.
        assertEquals(listOf(0x46, 0x79) + List(13) { 0x3A } + 0x02, packets.map { it[0].toInt() })
        assertContentEquals(bytes(0x3A, 0x00, 0x00), packets[2].copyOf(3)) // Chunk 0, 0 first
        assertContentEquals(bytes(0x02, 0x00, 0x00, 0x00, 0x03), packets.last()) // PLAYER_SPAWN
        assertFalse(session.isInGame)
    }

    @Test
    fun `a wider view sends only the chunks the client doesn't have`() {
        loadWorld()
        clientSends(requestChunkRadius(radius = 2, maxRadius = 2))
        sent.clear()

        clientSends(requestChunkRadius(radius = 3, maxRadius = 3))

        // 29 chunks are in view now, and 13 of them went out already. The player doesn't spawn twice.
        assertEquals(listOf(0x46, 0x79) + List(16) { 0x3A }, receivedPackets().map { it[0].toInt() })
    }

    @Test
    fun `the player is in the game once the client initialises its own runtime ID`() {
        loadWorld()
        clientSends(requestChunkRadius(radius = 2, maxRadius = 2))

        clientSends(setLocalPlayerAsInitialised(runtimeId = 8))
        assertFalse(session.isInGame)

        clientSends(setLocalPlayerAsInitialised(runtimeId = 7))
        assertTrue(session.isInGame)

        // Then the client learns about its own player: its actor data, attributes and abilities, and the commands it may run; then the
        // time, and the weather, rain and thunder each stopped.
        assertEquals(listOf(0x27, 0x1D, 0xBB, 0x4C, 0x0A, 0x19, 0x19), batches.decode(sent.last()).map { it[0].toInt() and 0xFF })
    }

    @Test
    fun `everyone hears a player joined half a second after their loading screen closes`() {
        loadWorld()
        clientSends(requestChunkRadius(radius = 2, maxRadius = 2))
        clientSends(loadingScreen(type = 1))
        assertTrue(scheduled.isEmpty())

        // The screen closing and the player being initialised come together, as the client sends them.
        clientSends(loadingScreen(type = 2), setLocalPlayerAsInitialised(runtimeId = 7))
        sent.clear()
        val (ticks, announce) = scheduled.single()
        assertEquals(10, ticks)

        announce()
        val joined = Text(Text.TRANSLATION, "\u00a7e%multiplayer.player.joined", parameters = listOf("Steve"), needsTranslation = true)
        assertContentEquals(joined.encode(), receivedPacket())
    }

    @Test
    fun `every batch in and out goes to the packet log, and logins only by size`() {
        val packetLog = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger("org.koremc.packets") as Logger
        logger.addAppender(packetLog)
        try {
            loadWorld()
            clientSends(requestChunkRadius(radius = 2, maxRadius = 2))
        } finally {
            logger.detachAppender(packetLog)
        }

        val lines = packetLog.list.map { it.formattedMessage.substringBefore(" (") }
        assertTrue(lines.any { it.startsWith("<- network 9876543210123456789: Login(protocol=2193, ") })
        assertEquals(
            listOf(
                "<- Steve: RequestChunkRadius(radius=2, maxRadius=2)",
                "-> Steve: ChunkRadiusUpdated, NetworkChunkPublisherUpdate",
                "-> Steve: LevelChunk x13",
                "-> Steve: PlayStatus",
            ),
            lines.takeLast(4),
        )
    }

    @Test
    fun `moving within a chunk only updates where the player is`() {
        spawn()

        clientSends(playerAuthInput(Vec3(8.5f, -61.38f, 8.5f)))

        assertEquals(Vec3(8.5f, -61.38f, 8.5f), session.position)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `straying two chunks moves the view and sends the chunks that came into it`() {
        spawn()

        clientSends(playerAuthInput(Vec3(16.5f, -61.38f, 8.5f)))
        assertTrue(sent.isEmpty())

        clientSends(playerAuthInput(Vec3(32.5f, -61.38f, 8.5f)))
        val packets = receivedPackets()

        val cameIntoView = chunksAround(ChunkPos(2, 0), 2) - chunksAround(ChunkPos(0, 0), 2).toSet()
        // The view now centres on block 32, -62, 8, two chunks out (32 blocks), and has no saved chunks.
        assertContentEquals(bytes(0x79, 0x40, 0x7B, 0x10, 0x20, 0x00, 0x00, 0x00, 0x00), packets.first())
        assertEquals(List(cameIntoView.size) { 0x3A }, packets.drop(1).map { it[0].toInt() })
        assertEquals(8, cameIntoView.size)
    }

    @Test
    fun `stepping back and forth over a chunk border sends nothing`() {
        spawn()

        repeat(3) {
            clientSends(playerAuthInput(Vec3(16.5f, -61.38f, 16.5f))) // Diagonally into chunk 1, 1
            clientSends(playerAuthInput(Vec3(15.5f, -61.38f, 15.5f))) // And back into chunk 0, 0
        }

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `once the view has moved, stepping back doesn't move it again`() {
        spawn()
        clientSends(playerAuthInput(Vec3(32.5f, -61.38f, 8.5f)))
        sent.clear()

        clientSends(playerAuthInput(Vec3(31.5f, -61.38f, 8.5f))) // Back into chunk 1, 0, one from the new centre
        clientSends(playerAuthInput(Vec3(32.5f, -61.38f, 8.5f)))

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `impossible positions are ignored`() {
        spawn()
        val start = session.position

        clientSends(playerAuthInput(Vec3(Float.NaN, 0f, 0f)))
        clientSends(playerAuthInput(Vec3(4e7f, 0f, 0f)))

        assertEquals(start, session.position)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `sneaking shows in the player's flags and height, at the input's tick`() {
        spawn()

        clientSends(playerAuthInput(standing, inputs = listOf(SNEAKING), tick = 101))
        assertContentEquals(playerData(ActorFlag.SNEAKING, height = 1.49f, tick = 101), receivedPacket())
        assertTrue(session.sneaking)

        sent.clear()
        clientSends(playerAuthInput(standing, inputs = listOf(SNEAKING), tick = 102)) // Still holding it
        assertTrue(sent.isEmpty())

        clientSends(playerAuthInput(standing, tick = 103))
        assertContentEquals(playerData(height = 1.8f, tick = 103), receivedPacket())
        assertFalse(session.sneaking)
    }

    @Test
    fun `sprinting starts and stops with the client's flags`() {
        spawn()

        clientSends(playerAuthInput(standing, inputs = listOf(START_SPRINTING), tick = 101))
        assertContentEquals(playerData(ActorFlag.SPRINTING, height = 1.8f, tick = 101), receivedPacket())

        sent.clear()
        clientSends(playerAuthInput(standing, tick = 102)) // Still sprinting: there's no flag held for it
        assertTrue(sent.isEmpty())
        assertTrue(session.sprinting)

        clientSends(playerAuthInput(standing, inputs = listOf(STOP_SPRINTING), tick = 103))
        assertContentEquals(playerData(height = 1.8f, tick = 103), receivedPacket())
        assertFalse(session.sprinting)
    }

    @Test
    fun `swimming and gliding lie the player flat, as their flags and height say, or the client neither swims nor glides`() {
        spawn()

        clientSends(playerAuthInput(standing, inputs = listOf(START_SWIMMING), tick = 101))
        assertContentEquals(playerData(ActorFlag.SWIMMING, height = 0.6f, tick = 101), receivedPacket())
        clientSends(playerAuthInput(standing, inputs = listOf(STOP_SWIMMING), tick = 102))
        assertContentEquals(playerData(height = 1.8f, tick = 102), receivedPacket())
        clientSends(playerAuthInput(standing, inputs = listOf(START_GLIDING), tick = 103))
        assertContentEquals(playerData(ActorFlag.GLIDING, height = 0.6f, tick = 103), receivedPacket())
    }

    @Test
    fun `sneaking ends a sprint, except in flight`() {
        spawn()

        clientSends(playerAuthInput(standing, inputs = listOf(START_SPRINTING)))
        clientSends(playerAuthInput(standing, inputs = listOf(SNEAKING)))
        assertFalse(session.sprinting)
        assertTrue(session.sneaking)

        clientSends(playerAuthInput(standing, inputs = listOf(START_FLYING, START_SPRINTING)))
        clientSends(playerAuthInput(standing, inputs = listOf(SNEAKING))) // Descending
        assertTrue(session.sprinting)
        assertTrue(session.sneaking)
    }

    @Test
    fun `the inventory opens when the client asks, once`() {
        spawn()

        clientSends(interact(action = 6, target = 7))
        // The player's own inventory, at the block they're in: in this one-layer world, their eyes start at 0.5, -61.38, 0.5.
        assertContentEquals(ContainerOpen(0, 0xFF, BlockPos(0, -62, 0), entityId = -1).encode(), receivedPacket())

        sent.clear()
        clientSends(interact(action = 6, target = 7)) // Asked again before the answer arrived
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `closing the inventory is agreed to, and it can open again`() {
        spawn()
        clientSends(interact(action = 6, target = 7))

        clientSends(containerClose(window = 0, type = 0xFF))
        assertContentEquals(ContainerClose(0, 0).encode(), receivedPacket())

        clientSends(interact(action = 6, target = 7))
        assertEquals(0x2E, receivedPacket()[0].toInt())
    }

    @Test
    fun `chat closing the inventory needs no answer`() {
        spawn()
        clientSends(interact(action = 6, target = 7))
        sent.clear()

        clientSends(containerClose(window = 0xFF, type = 0xFF))
        assertTrue(sent.isEmpty())

        clientSends(interact(action = 6, target = 7))
        assertEquals(0x2E, receivedPacket()[0].toInt())
    }

    @Test
    fun `in game, the packet log only quietens movement`() {
        spawn()
        val packetLog = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger("org.koremc.packets") as Logger
        logger.level = ch.qos.logback.classic.Level.TRACE
        logger.addAppender(packetLog)
        try {
            clientSends(playerAuthInput(standing))
            clientSends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(351).writeZero(4))) // A packet Kore doesn't handle
        } finally {
            logger.detachAppender(packetLog)
            logger.level = null
        }

        assertEquals(listOf("TRACE", "DEBUG"), packetLog.list.map { it.level.toString() })
    }

    @Test
    fun `item stack requests get an answer each`() {
        spawn()

        // Request -1 picks the first creative item. This player isn't in creative mode, so it's turned down.
        val request = ByteBufUtil.getBytes(
            Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-1)
                .writeVarUInt(1).writeVarUInt(12).writeByte(14).writeVarUInt(1).writeByte(1)
                .writeVarUInt(0).writeIntLE(0),
        )
        clientSends(request)

        // ItemStackResponse: one response, rejected (1), for request -1 (zigzagged to 1), with no containers.
        assertContentEquals(bytes(0x94, 0x01, 0x01, 0x01, 0x01, 0x00), receivedPacket())
    }

    @Test
    fun `creative players break blocks, and see them go with particles`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()

        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, BlockPos(1, -64, 0)))

        val (update, particles) = batches.decode(sent.single())
        assertContentEquals(UpdateBlock(BlockPos(1, -64, 0), AIR_NETWORK_ID).encode(), update)
        assertContentEquals(LevelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, Vec3(1.5f, -63.5f, 0.5f), stone.networkId).encode(), particles)
        assertEquals(AIR_NETWORK_ID, world.blockAt(BlockPos(1, -64, 0)))

        // The client can report the same break twice, and the second changes nothing.
        sent.clear()
        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, BlockPos(1, -64, 0)))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `in creative, a sword only hits, so a break with one is put back`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        session.give(ItemStack(sword))
        sent.clear()

        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, BlockPos(1, -64, 0)))
        assertEquals(stone.networkId, world.blockAt(BlockPos(1, -64, 0)))
        assertContentEquals(UpdateBlock(BlockPos(1, -64, 0), stone.networkId).encode(), receivedPacket())
    }

    @Test
    fun `a block holding water leaves it when broken, which the client reporting the break twice doesn't break too`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        val pos = BlockPos(1, -63, 0)
        world[pos] = planks
        world.setLiquid(pos, water[0])

        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, pos))
        assertEquals(water[1], world.stateAt(pos))
        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, pos))
        assertEquals(water[1], world.stateAt(pos), "water doesn't break")
    }

    @Test
    fun `a torch falls a tick after the block holding it up, though it's in the next chunk`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        // Chunk 0, 0 starts at x 0, so the torch is in chunk -1, 0.
        world[BlockPos(0, -63, 1)] = stone
        world[BlockPos(-1, -63, 1)] = torch

        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, BlockPos(0, -63, 1)))
        assertEquals(torch, world.stateAt(BlockPos(-1, -63, 1)))
        listOf(session).tickBlocks(game, dropItems = false)

        val (block, fallen) = sent.map { batches.decode(it) }
        assertContentEquals(UpdateBlock(BlockPos(0, -63, 1), AIR_NETWORK_ID).encode(), block[0])
        assertContentEquals(UpdateBlock(BlockPos(-1, -63, 1), AIR_NETWORK_ID).encode(), fallen[0])
        assertContentEquals(LevelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, Vec3(-0.5f, -62.5f, 1.5f), torch.networkId).encode(), fallen[1])
    }

    @Test
    fun `a block that starts falling shows falling before it goes from where it was, and where it lands before it stops falling`() {
        spawn()
        val from = BlockPos(1, -60, 0)
        world[from] = sand
        fun packetId(packet: ByteArray) = Unpooled.wrappedBuffer(packet).readVarUInt() and 0x3FF

        repeat(3) { listOf(session).tickBlocks(game, dropItems = false) }
        val started = sent.flatMap { batches.decode(it) }
        val appeared = started.indexOfFirst { packetId(it) == ADD_ACTOR }
        val left = started.indexOfFirst { it.contentEquals(UpdateBlock(from, AIR_NETWORK_ID).encode()) }
        assertTrue(appeared in 0 until left, "the falling block appears at $appeared, before the block goes at $left")

        sent.clear()
        while (world.fallingBlocks.isNotEmpty()) listOf(session).tickBlocks(game, dropItems = false)
        val landed = sent.flatMap { batches.decode(it) }
        val placed = landed.indexOfFirst { it.contentEquals(UpdateBlock(BlockPos(1, -63, 0), sand.networkId).encode()) }
        val gone = landed.indexOfFirst { packetId(it) == REMOVE_ACTOR }
        assertTrue(placed in 0 until gone, "the block lands at $placed, before the falling block goes at $gone")
    }

    @Test
    fun `breaks kore refuses are put back`() {
        spawn()

        // A creative break, from a player in survival.
        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, BlockPos(1, -64, 0)))
        assertContentEquals(UpdateBlock(BlockPos(1, -64, 0), stone.networkId).encode(), receivedPacket())

        // A block 19 away, past even creative reach.
        clientSends(playerAction(CREATIVE_DESTROY_BLOCK, BlockPos(20, -64, 0)))
        assertContentEquals(UpdateBlock(BlockPos(20, -64, 0), stone.networkId).encode(), receivedPacket())
        assertEquals(stone.networkId, world.blockAt(BlockPos(1, -64, 0)))
    }

    @Test
    fun `a held block is placed, heard, and the client is told what's at both spots`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickPlanksIntoHotbar()

        clientSends(placeBlock(against = BlockPos(1, -64, 0), face = 1, slot = 0))

        val (shown, corrections) = sent.map { batches.decode(it) }
        assertContentEquals(UpdateBlock(BlockPos(1, -63, 0), planks.networkId).encode(), shown[0])
        assertContentEquals(LevelSoundEvent(LevelSoundEvent.PLACE, Vec3(1.5f, -62.5f, 0.5f), planks.networkId).encode(), shown[1])
        assertContentEquals(UpdateBlock(BlockPos(1, -64, 0), stone.networkId).encode(), corrections.single()) // The block clicked
        assertEquals(planks.networkId, world.blockAt(BlockPos(1, -63, 0)))
    }

    @Test
    fun `without the block in hand, nothing is placed, and the client is put right`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()

        clientSends(placeBlock(against = BlockPos(1, -64, 0), face = 1, slot = 0))

        val corrections = batches.decode(sent.single())
        assertContentEquals(UpdateBlock(BlockPos(1, -64, 0), stone.networkId).encode(), corrections[0])
        assertContentEquals(UpdateBlock(BlockPos(1, -63, 0), AIR_NETWORK_ID).encode(), corrections[1])
        assertEquals(AIR_NETWORK_ID, world.blockAt(BlockPos(1, -63, 0)))
    }

    @Test
    fun `a placement the client found doesn't work is left alone, without an answer`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickPlanksIntoHotbar()

        // Planks going inside the player, say. The client tries again every frame while the button is held.
        repeat(3) { clientSends(placeBlock(against = BlockPos(1, -64, 0), face = 1, slot = 0, predictedToWork = false)) }

        assertTrue(sent.isEmpty())
        assertEquals(AIR_NETWORK_ID, world.blockAt(BlockPos(1, -63, 0)))
    }

    @Test
    fun `clicking a lever flips it, for everyone with the chunk to see and hear`() {
        spawn()
        val lever = BlockPos(1, -63, 0)
        world[lever] = levers[0]

        clientSends(placeBlock(against = lever, face = 1, slot = 0, held = 0)) // With nothing in hand

        val (update, sound) = batches.decode(sent.single())
        assertContentEquals(UpdateBlock(lever, levers[1].networkId).encode(), update)
        assertContentEquals(LevelSoundEvent("power.on", Vec3(1.5f, -62.5f, 0.5f), levers[1].networkId).encode(), sound)
    }

    @Test
    fun `a hoe tills grass on a click the client leaves to the server, but not each time it tries again as the button's held`() {
        spawn()
        session.give(ItemStack(hoe))
        val tilled = BlockPos(1, -64, 0)
        world[tilled] = grass
        sent.clear()

        // The client finds tilling does nothing, as it leaves it to the server.
        clientSends(placeBlock(against = tilled, face = 1, slot = 0, held = HOE_ITEM, predictedToWork = false))
        assertEquals(farmland, world.stateAt(tilled))
        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(UpdateBlock(tilled, farmland.networkId).encode()) })
        assertTrue(packets.any { it.contentEquals(InventorySlot(0, ItemStack(hoe).withDamage(1), stackId = 1).encode()) }) // Worn a use
        assertTrue(packets.any { it.contentEquals(Animate(Animate.SWING_ARM, 7, swingSource = "interact").encode()) }) // Swinging the player's arm

        val held = BlockPos(2, -64, 0)
        world[held] = grass
        clientSends(placeBlock(against = held, face = 1, slot = 0, held = HOE_ITEM, predictedToWork = false, repeated = true))
        assertEquals(grass, world.stateAt(held))
    }

    @Test
    fun `a player falling onto farmland from high enough tramples it back to dirt, and the crop on it breaks, but walking on it doesn't`() {
        spawn()
        val field = BlockPos(0, -64, 0)
        world[field] = farmland
        world[field.side(Face.UP)] = wheat
        val top = field.y + 15 / 16f + PLAYER_EYE_HEIGHT // A player's eyes, standing on farmland

        // Walking onto it, no fall.
        clientSends(playerAuthInput(Vec3(0.5f, top, 0.5f), inputs = listOf(VERTICAL_COLLISION)))
        assertEquals(farmland, world.stateAt(field))

        // Two blocks fallen before the tick they land, which, as in the game, doesn't count.
        clientSends(playerAuthInput(Vec3(0.5f, top + 3, 0.5f)))
        clientSends(playerAuthInput(Vec3(0.5f, top + 2, 0.5f)))
        clientSends(playerAuthInput(Vec3(0.5f, top + 1, 0.5f)))
        sent.clear()
        clientSends(playerAuthInput(Vec3(0.5f, top, 0.5f), inputs = listOf(VERTICAL_COLLISION)))
        assertEquals(dirt, world.stateAt(field))
        assertTrue(receivedPackets().any { it.contentEquals(UpdateBlock(field, dirt.networkId).encode()) })
        listOf(session).tickBlocks(game, dropItems = false)
        assertEquals(air, world.stateAt(field.side(Face.UP)))
    }

    @Test
    fun `punching a frame in survival knocks its item out, and leaves the frame`() {
        spawn()
        val at = BlockPos(1, -63, 0)
        world[at] = frame
        world.setBlockEntity(at, frameData(frame).toBuilder().putCompound("Item", ItemStacks.nbtOf(creative.items[0].stack.copy(count = 1), 0)).build())
        sent.clear()

        // The client starts breaking it and stops at once.
        clientSends(breakingInput(tick = 200, START_DESTROY to at, ABORT_DESTROY to at))

        assertNull(world.frameItem(at, game.items))
        assertEquals(frame, world.stateAt(at))
        assertEquals("minecraft:oak_planks", world.items.single().stack.item.name)
    }

    @Test
    fun `a full flower pot takes a click with a plant, rather than the plant going beside it`() {
        spawn()
        session.give(ItemStack(poppyItem, block = poppy))
        val pot = BlockPos(1, -63, 0)
        world[pot] = pots[1]
        world.setBlockEntity(pot, NbtMap.builder().putString("id", "FlowerPot").putCompound("PlantBlock", blockStateNbt(poppy)).build())

        clientSends(placeBlock(against = pot, face = 1, slot = 0, held = POPPY_ITEM))

        assertEquals(AIR, world.stateAt(pot.side(Face.UP))?.name)
        assertEquals(poppy, world.plantInPot(pot))
        assertEquals(1, session.heldStack?.count)
    }

    @Test
    fun `a water bucket emptied into a cauldron swaps for an empty one in the player's hand`() {
        spawn()
        session.give(ItemStack(waterBucket))
        val at = BlockPos(1, -63, 0)
        world[at] = cauldrons[0]
        sent.clear()

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = WATER_BUCKET_ITEM))

        assertEquals(cauldrons[6], world.stateAt(at))
        assertEquals("minecraft:bucket", session.heldStack?.item?.name)
        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(LevelEvent(3506, Vec3(1.5f, -62.5f, 0.5f), 0).encode()) }) // The sound of it filling
        assertTrue(packets.any { it.contentEquals(InventorySlot(0, ItemStack(bucket), stackId = 2).encode()) })
    }

    @Test
    fun `leather dipped in dyed water comes out dyed, in creative too, and the client sees the cauldron afresh`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        session.give(ItemStack(helmet))
        val at = BlockPos(1, -63, 0)
        world[at] = cauldrons[6]
        val red = 0xFFB02E26.toInt()
        world.setBlockEntity(at, NbtMap.builder().putString("id", "Cauldron").putShort("PotionId", -1).putShort("PotionType", -1).putInt("CustomColor", red).build())
        sent.clear()

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = HELMET_ITEM))

        assertEquals(red, session.heldStack?.dyedColour)
        assertEquals(cauldrons[5], world.stateAt(at))
        // The client draws the water afresh only as the block changes, so it sees it change twice.
        val updates = receivedPackets().filter { it[0].toInt() == 21 }
        assertContentEquals(UpdateBlock(at, cauldrons[6].networkId).encode(), updates[0])
        assertContentEquals(UpdateBlock(at, cauldrons[5].networkId).encode(), updates[1])
    }

    @Test
    fun `a disc put in a jukebox plays, and everyone around is told what's playing`() {
        spawn()
        session.give(ItemStack(disc))
        val at = BlockPos(1, -63, 0)
        world[at] = jukebox
        sent.clear()

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = DISC_ITEM))

        assertNull(session.heldStack)
        val packets = receivedPackets()
        // The song plays by a handle, the world's first, tied to the jukebox, as songs play since 26.50.
        assertTrue(packets.any { it.contentEquals(PlaySound("record.cat", Vec3(1.5f, -62.5f, 0.5f), 4f, 1f, handle = 1).encode()) })
        assertTrue(packets.any { it.contentEquals(RecordStarted(at, 1).encode()) })
        val popup = Text(Text.JUKEBOX_POPUP, "record.nowPlaying", parameters = listOf("%item.record_cat.desc"), needsTranslation = true)
        assertTrue(packets.any { it.contentEquals(popup.encode()) })
    }

    @Test
    fun `taking a disc out of a jukebox stops its song, by the handle it plays by`() {
        spawn()
        session.give(ItemStack(disc))
        val at = BlockPos(1, -63, 0)
        world[at] = jukebox
        clientSends(placeBlock(against = at, face = 1, slot = 0, held = DISC_ITEM))
        sent.clear()

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))

        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(UpdateSoundData(1).encode()) })
    }

    @Test
    fun `using armour puts it on, and the client can go on naming it as it did`() {
        spawn()
        session.give(ItemStack(helmet))
        sent.clear()

        clientSends(useInAir(slot = 0, held = HELMET_ITEM, legacyRequestId = -38388))
        assertNull(session.heldStack)
        assertTrue(receivedPackets().any { it.contentEquals(InventorySlot(0, ItemStack(helmet), stackId = 1, window = ARMOUR_WINDOW).encode()) })

        // Request -9 drops the helmet from the armour slot, which the client names -38388.
        clientSends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-9).writeVarUInt(1)
                    .writeVarUInt(3).writeByte(3).writeByte(1).writeByte(6).writeBoolean(false).writeByte(0).writeIntLE(-38388).writeBoolean(false)
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )
        assertEquals(0, receivedPacket()[3].toInt()) // The response's result: accepted
    }

    @Test
    fun `players see lightning strike in their chunks, with its thunder, until it's gone, and hear the time once a second`() {
        spawn()
        val bolt = world.strike(BlockPos(3, -63, 3), mutableListOf(), mutableListOf())
        sent.clear()

        listOf(session).tickSky(game)
        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(AddLightning(bolt.id, Vec3(3.5f, -63f, 3.5f)).encode()) })
        assertTrue(packets.any { it.contentEquals(LevelSoundEvent("thunder", Vec3(3.5f, -63f, 3.5f), -1, entityType = "minecraft:lightning_bolt").encode()) })

        repeat(19) { listOf(session).tickSky(game) }
        val later = receivedPackets()
        assertTrue(later.any { it.contentEquals(RemoveActor(bolt.id).encode()) })
        assertTrue(later.any { it.contentEquals(SetTime(world.time).encode()) })
    }

    @Test
    fun `clicking a note block tunes it and plays it, the note rising out of it`() {
        spawn()
        val at = BlockPos(1, -63, 0)
        world[at] = noteBlock
        sent.clear()

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))

        val packets = receivedPackets()
        // Stone below plays the bass drum.
        assertTrue(packets.any { it.contentEquals(LevelSoundEvent("note", Vec3(1.5f, -62.5f, 0.5f), 1 shl 8 or 1).encode()) })
        assertTrue(packets.any { it.contentEquals(BlockEvent(at, 1, 1).encode()) })
    }

    @Test
    fun `a head used on top of a note block goes on it, without sneaking, rather than tuning it`() {
        spawn()
        val at = BlockPos(1, -63, 0)
        world[at] = noteBlock
        session.give(ItemStack(zombieHeadItem, block = zombieHead))
        sent.clear()

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = ZOMBIE_HEAD_ITEM))

        assertEquals("minecraft:zombie_head", world.stateAt(at.side(Face.UP))?.name)
        assertNull(world.blockEntityAt(at)?.getByte("note")?.takeIf { it > 0 }, "not tuned")
    }

    @Test
    fun `clicking a sign opens the side the player sees to write on, and what they write goes on it for everyone`() {
        spawn()
        // North of the player, facing south, so they see its front.
        val at = BlockPos(1, -63, -1)
        world[at] = sign
        world.setBlockEntity(at, signData(sign))

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))
        assertContentEquals(OpenSign(at, front = true).encode(), receivedPacket())
        sent.clear()

        clientSends(blockActorData(at, NbtMap.builder().putCompound("FrontText", NbtMap.builder().putString("Text", "Hello").build()).build()))
        val written = world.blockEntityAt(at)!!
        assertEquals("Hello", written.getCompound("FrontText").getString("Text"))
        assertEquals("", written.getCompound("BackText").getString("Text"))
        assertContentEquals(BlockActorData(at, written).encode(), receivedPacket())
    }

    @Test
    fun `a waxed sign doesn't open, but says so, and what a client writes on it anyway is put back`() {
        spawn()
        val at = BlockPos(1, -63, -1)
        world[at] = sign
        world.setBlockEntity(at, signData(sign).toBuilder().putBoolean("IsWaxed", true).build())

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))
        assertContentEquals(LevelSoundEvent("block.sign.waxed_interact_fail", Vec3(1.5f, -62.5f, -0.5f), -1).encode(), receivedPacket())
        sent.clear()

        clientSends(blockActorData(at, NbtMap.builder().putCompound("FrontText", NbtMap.builder().putString("Text", "Hello").build()).build()))
        val kept = world.blockEntityAt(at)!!
        assertEquals("", kept.getCompound("FrontText").getString("Text"))
        assertContentEquals(BlockActorData(at, kept).encode(), receivedPacket())
    }

    @Test
    fun `a pressed button pops back out by itself`() {
        spawn()
        val button = BlockPos(1, -63, 0)
        world[button] = buttons[0]
        clientSends(placeBlock(against = button, face = 1, slot = 0, held = 0))
        assertEquals(buttons[1], world.stateAt(button))
        sent.clear()

        // A wooden button stays in for 30 ticks.
        repeat(29) { listOf(session).tickBlocks(game, dropItems = false) }
        assertEquals(buttons[1], world.stateAt(button))
        assertTrue(sent.isEmpty())
        listOf(session).tickBlocks(game, dropItems = false)

        val (update, sound) = batches.decode(sent.single())
        assertContentEquals(UpdateBlock(button, buttons[0].networkId).encode(), update)
        assertContentEquals(LevelSoundEvent(LevelSoundEvent.BUTTON_CLICK_OFF, Vec3(1.5f, -62.5f, 0.5f), buttons[0].networkId).encode(), sound)
    }

    @Test
    fun `sneaking with a block in hand places it, rather than using the block clicked`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickIntoHotbar(creativeItem = 1, slot = 0)
        val lever = BlockPos(1, -63, 0)
        world[lever] = levers[0]
        clientSends(playerAuthInput(standing, inputs = listOf(SNEAKING)))
        sent.clear()

        clientSends(placeBlock(against = lever, face = 5, slot = 0))

        assertEquals(levers[0], world.stateAt(lever))
        assertEquals(planks, world.stateAt(lever.side(Face.EAST)))
    }

    @Test
    fun `a door item places its door, both halves`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickIntoHotbar(creativeItem = 2, slot = 1)

        clientSends(placeBlock(against = BlockPos(1, -64, 0), face = 1, slot = 1, held = DOOR_ITEM))

        val (lower, upper) = listOf(BlockPos(1, -63, 0), BlockPos(1, -62, 0)).map { world.stateAt(it) }
        assertEquals("minecraft:wooden_door", lower?.name)
        assertEquals(1.toByte(), upper?.properties?.get("upper_block_bit"))
        assertEquals(3, batches.decode(sent.first()).size) // Both halves and the sound
    }

    @Test
    fun `a fence placed beside another joins it, and the other joins back`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickIntoHotbar(creativeItem = 3, slot = 2)
        world[BlockPos(2, -63, 0)] = fences[0]

        clientSends(placeBlock(against = BlockPos(1, -64, 0), face = 1, slot = 2, held = FENCE_ITEM))

        // The new fence, its sound, then the fence it joined, all in the one batch for their chunk.
        val shown = batches.decode(sent.first())
        assertContentEquals(UpdateBlock(BlockPos(1, -63, 0), fences[1].networkId).encode(), shown[0])
        assertContentEquals(UpdateBlock(BlockPos(2, -63, 0), fences[2].networkId).encode(), shown[2])
        assertEquals(3, shown.size)
    }

    @Test
    fun `in survival, cracks spread over a block at the pace it breaks, and bits fly off it`() {
        spawn()
        val ground = BlockPos(1, -64, 0)

        clientSends(breakingInput(tick = 200, START_DESTROY to ground))
        // Stone by hand takes 150 ticks, so the cracks get 65535 / 150 of the way each tick.
        assertContentEquals(LevelEvent(LevelEvent.START_BLOCK_CRACKING, Vec3(1f, -64f, 0f), 436).encode(), receivedPacket())

        clientSends(breakingInput(tick = 201, CRACK to ground))
        assertContentEquals(LevelEvent(LevelEvent.PARTICLES_CRACK_BLOCK_DOWN + 1, Vec3(1f, -64f, 0f), stone.networkId).encode(), receivedPacket())

        clientSends(breakingInput(tick = 202, ABORT_DESTROY to ground))
        assertContentEquals(LevelEvent(LevelEvent.STOP_BLOCK_CRACKING, Vec3(1f, -64f, 0f), 0).encode(), receivedPacket())
    }

    @Test
    fun `hitting a block with fire on the face hit puts the fire out, with its sound, and leaves the block, in creative too`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        val ground = BlockPos(1, -64, 0)
        val onTop = ground.side(Face.UP)
        world[onTop] = fire
        sent.clear()

        clientSends(breakingInput(tick = 200, START_DESTROY to ground))
        assertEquals(air, world.stateAt(onTop))
        assertEquals(stone, world.stateAt(ground))
        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(UpdateBlock(onTop, air.networkId).encode()) })
        assertTrue(packets.any { it.contentEquals(LevelSoundEvent("extinguish.fire", onTop.centre, -1).encode()) })
    }

    @Test
    fun `a survival break that comes too soon is put back, and one that took long enough isn't`() {
        spawn()
        val ground = BlockPos(1, -64, 0)

        // Ten ticks in, of the 150 stone takes by hand.
        clientSends(breakingInput(tick = 200, START_DESTROY to ground))
        clientSends(breakingInput(tick = 210, STOP_DESTROY to ground, destroying = ground))
        assertEquals(stone.networkId, world.blockAt(ground))
        assertContentEquals(UpdateBlock(ground, stone.networkId).encode(), receivedPacket())

        clientSends(breakingInput(tick = 300, START_DESTROY to ground))
        clientSends(breakingInput(tick = 450, STOP_DESTROY to ground, destroying = ground))
        assertEquals(AIR_NETWORK_ID, world.blockAt(ground))
    }

    /** A middle click on the block at [at], with control held for [withData], holding hotbar [slot]. */
    private fun blockPick(at: BlockPos, withData: Boolean = false, slot: Int = 0) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(34).writeVarInt(at.x).writeVarInt(at.y).writeVarInt(at.z).writeBoolean(withData).writeByte(slot),
    )

    @Test
    fun `in creative, picking a block puts its item in the first empty hotbar slot and holds it, and picking it again holds that slot`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        session.give(ItemStack(hoe)) // In slot 0, which they hold
        val at = BlockPos(1, -63, 0)
        world[at] = planks
        val planksStack = ItemStack(creative.items[0].stack.item, block = planks)
        sent.clear()

        clientSends(blockPick(at, slot = 4)) // Into the first empty slot, not the held one
        val packets = receivedPackets()
        assertEquals(1, packets.count { it[0].toInt() == 50 }) // One slot filled
        assertTrue(packets.any { it.contentEquals(ShowEquipment(7, planksStack, 1).encode()) })

        clientSends(blockPick(at, slot = 1))
        assertEquals(listOf(ShowEquipment(7, planksStack, 1).encode().toList()), receivedPackets().map { it.toList() })
    }

    @Test
    fun `in survival, picking a block with the hotbar full swaps its item from the rest of the inventory with the held one, and picks nothing they don't have`() {
        spawn()
        session.give(ItemStack(hoe, count = 64 * HOTBAR_SIZE)) // A full hotbar
        val planksStack = ItemStack(creative.items[0].stack.item, block = planks)
        session.give(planksStack) // In slot 9
        val at = BlockPos(1, -63, 0)
        world[at] = planks
        sent.clear()

        clientSends(blockPick(at, slot = 5))
        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(InventorySlot(5, planksStack, stackId = 10).encode()) })
        assertTrue(packets.any { it.contentEquals(InventorySlot(9, ItemStack(hoe, count = 64), stackId = 6).encode()) })
        assertTrue(packets.any { it.contentEquals(ShowEquipment(7, planksStack, 5).encode()) })

        world[at] = torch
        clientSends(blockPick(at))
        assertTrue(receivedPackets().isEmpty())
    }

    @Test
    fun `picking a block moves its item from the rest of the inventory into the first empty hotbar slot, even left of the held one`() {
        spawn()
        session.give(ItemStack(hoe, count = 64 * HOTBAR_SIZE))
        val planksStack = ItemStack(creative.items[0].stack.item, block = planks)
        session.give(planksStack) // In slot 9
        clientSends(dropFromSlot(slot = 2, had = 64, dropping = 64, item = HOE_ITEM)) // Slot 2 emptied
        val at = BlockPos(1, -63, 0)
        world[at] = planks
        sent.clear()

        clientSends(blockPick(at, slot = 5))
        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(InventorySlot(2, planksStack, stackId = 10).encode()) })
        assertTrue(packets.any { it.contentEquals(InventorySlot(9, stack = null, stackId = 0).encode()) })
        assertTrue(packets.any { it.contentEquals(ShowEquipment(7, planksStack, 2).encode()) })
    }

    @Test
    fun `the client gets the sub-chunks it asks for, with the heights to light them by, all air above the ground, and none of chunks it wasn't sent`() {
        spawn()
        sent.clear()
        // From the bottom sub-chunk of chunk 0, 0: itself, the one above, and one in a chunk far out of view.
        val offsets = listOf(SubChunkOffset(0, 0, 0), SubChunkOffset(0, 1, 0), SubChunkOffset(100, 0, 0))
        clientSends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(175).writeVarInt(0).writeVarUInt(offsets.size)
                    .apply { offsets.forEach { writeByte(it.x).writeByte(it.y).writeByte(it.z) } }
                    .writeIntLE(0).writeIntLE(-4).writeIntLE(0),
            ),
        )

        // The stone at the bottom lets no light through, so the heightmap is a block up from the bottom of the world.
        val stoneLayer = world.chunkAt(ChunkPos(0, 0)).subChunks[0]
        val expected = SubChunk(
            0, -4, 0,
            listOf(
                SubChunkEntry(offsets[0], SubChunkResult.FOUND, subChunkPayload(0, stoneLayer, null, emptyList()), ByteArray(256) { 1 }),
                SubChunkEntry(offsets[1], SubChunkResult.ALL_AIR, heights = ByteArray(256) { -1 }),
                SubChunkEntry(offsets[2], SubChunkResult.NO_CHUNK),
            ),
        )
        assertContentEquals(expected.encode(), receivedPacket())
    }

    @Test
    fun `a furnace comes with its block entity in its sub-chunk, items and all, though no one's opened it, for the client to open`() {
        spawn()
        world[BlockPos(1, -63, 0)] = furnace
        sent.clear()
        clientSends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(175).writeVarInt(0).writeVarUInt(1).writeByte(0).writeByte(0).writeByte(0)
                    .writeIntLE(0).writeIntLE(-4).writeIntLE(0),
            ),
        )
        val reply = String(receivedPacket(), Charsets.ISO_8859_1)
        assertTrue("Furnace" in reply && "Items" in reply && "BurnTime" in reply, reply)
    }

    @Test
    fun `a player killed falls over, drops what they had, is told how they died, and once they choose to, respawns at the spawn`() {
        spawn()
        session.give(ItemStack(hoe))
        sent.clear()

        assertTrue(session.kill())
        val dying = receivedPackets()
        assertTrue(dying.any { it.contentEquals(ActorEvent(7, ActorEvent.DEATH, 0).encode()) })
        assertTrue(dying.any { it.contentEquals(DeathInfo("death.attack.generic", listOf(session.name)).encode()) })
        assertEquals(listOf(ItemStack(hoe)), world.items.map { it.stack })
        assertNull(session.heldStack)
        assertTrue(session.dead)

        clientSends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(45).writeZero(12).writeByte(Respawn.CLIENT_READY_TO_SPAWN).writeVarULong(7)))
        val eyes = Vec3(world.spawn.x + 0.5f, world.spawn.y + PLAYER_EYE_HEIGHT, world.spawn.z + 0.5f)
        assertTrue(receivedPackets().any { it.contentEquals(Respawn(eyes, Respawn.READY_TO_SPAWN, 7).encode()) })
        assertFalse(session.dead)
        assertEquals(eyes, session.position)
    }

    @Test
    fun `a fall hurts a player but for its first three blocks, and they and everyone see them flinch, but not in creative`() {
        spawn()
        // Up ten blocks, then down eight, a tick at a time, and onto the ground.
        for (feet in listOf(-53f, -55f, -57f, -59f, -61f)) clientSends(playerAuthInput(Vec3(0.5f, feet + PLAYER_EYE_HEIGHT, 0.5f)))
        sent.clear()
        clientSends(playerAuthInput(standing, inputs = listOf(VERTICAL_COLLISION)))
        assertEquals(15f, session.vitals.health)
        assertTrue(receivedPackets().any { it.contentEquals(ActorEvent(7, ActorEvent.HURT, DamageCause.FALL.id).encode()) })

        session.changeGameMode(GameMode.CREATIVE)
        for (feet in listOf(-53f, -57f, -61f)) clientSends(playerAuthInput(Vec3(0.5f, feet + PLAYER_EYE_HEIGHT, 0.5f)))
        clientSends(playerAuthInput(standing, inputs = listOf(VERTICAL_COLLISION)))
        assertEquals(15f, session.vitals.health)
    }

    @Test
    fun `a player under water holds their breath, and once it's gone drowns a little every second, until they die of it`() {
        spawn()
        world.rules.set("naturalRegeneration", false)
        world[BlockPos(0, -63, 0)] = water[0]
        world[BlockPos(0, -62, 0)] = water[0]

        repeat(PLAYER_MAX_AIR + 19) { session.tickHealth() }
        assertEquals(20f, session.vitals.health)
        session.tickHealth()
        assertEquals(18f, session.vitals.health)
        sent.clear()
        repeat(9 * 20) { session.tickHealth() }
        assertTrue(session.dead)
        assertTrue(receivedPackets().any { it.contentEquals(DeathInfo("death.attack.drown", listOf(session.name)).encode()) })
    }

    @Test
    fun `a swimming player's eyes are down by their feet, so water there is over their head, though not a standing player's`() {
        spawn()
        world.rules.set("naturalRegeneration", false)
        world[BlockPos(0, -63, 0)] = water[0]

        repeat(PLAYER_MAX_AIR + 20) { session.tickHealth() }
        assertEquals(20f, session.vitals.health)
        clientSends(playerAuthInput(standing, inputs = listOf(START_SWIMMING)))
        repeat(PLAYER_MAX_AIR + 20) { session.tickHealth() }
        assertEquals(18f, session.vitals.health)
    }

    @Test
    fun `a burning player's hurt carries its cause, for the client to sound it as burning, and the fire on them fizzes out in water`() {
        spawn()
        sent.clear()
        session.hurt(1f, DamageCause.BURNING)
        // The client sounds a hurt by its cause, burning as burning.
        assertTrue(receivedPackets().any { it.contentEquals(ActorEvent(7, ActorEvent.HURT, DamageCause.BURNING.id).encode()) })

        session.vitals.fireTicks = 100
        world[BlockPos(0, -63, 0)] = water[0]
        session.tickHealth()
        assertEquals(0, session.vitals.fireTicks)
        assertTrue(receivedPackets().any { it.contentEquals(LevelSoundEvent("fizz", standing, -1).encode()) })
    }

    @Test
    fun `a Totem of Undying in hand saves a player from dying, used up, with its effects, but not from the void`() {
        spawn()
        session.give(ItemStack(totem))
        session.vitals.health = 2f
        sent.clear()

        session.hurt(5f, DamageCause.FALL)
        assertFalse(session.dead)
        assertEquals(1f, session.vitals.health)
        assertEquals(8f, session.vitals.absorption)
        assertNull(session.heldStack)
        val got = receivedPackets()
        assertTrue(got.any { it.contentEquals(ActorEvent(7, ActorEvent.TALISMAN_ACTIVATE, 0).encode()) })
        assertTrue(got.any { it.contentEquals(MobEffect(7, MobEffect.ADD, Effect.REGENERATION, 1, 900).encode()) })

        session.give(ItemStack(totem))
        session.hurt(100f, DamageCause.VOID)
        assertTrue(session.dead)
    }

    @Test
    fun `nothing kills a player in creative`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        assertFalse(session.kill())
        assertFalse(session.dead)
    }

    @Test
    fun `a hungry player eats what they hold once it's taken as long as it takes, which fills their food bar and uses it up`() {
        spawn()
        session.give(ItemStack(apple, count = 2))
        session.hunger.food = 10
        session.hunger.saturation = 0f

        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        repeat(20) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0)) // Too soon: they're still eating
        assertEquals(10, session.hunger.food)
        repeat(12) { session.tickHealth() }
        sent.clear()
        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))

        assertEquals(14, session.hunger.food)
        assertEquals(4 * 0.3f * 2, session.hunger.saturation)
        assertEquals(1, session.heldStack?.count)
        val hunger = UpdateAttributes(
            7,
            listOf(
                Attribute("minecraft:player.hunger", 0f, 20f, 14f, default = 20f),
                Attribute("minecraft:player.saturation", 0f, 20f, 4 * 0.3f * 2, default = 20f),
                Attribute("minecraft:player.exhaustion", 0f, 5f, 0f, default = 0f),
            ),
        )
        assertTrue(receivedPackets().any { it.contentEquals(hunger.encode()) })
    }

    @Test
    fun `a full player can't start eating, and a bowl of stew eaten leaves the bowl in their hand`() {
        spawn()
        session.give(ItemStack(stew))

        clientSends(useInAir(slot = 0, held = STEW_ITEM, legacyRequestId = 0))
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = STEW_ITEM, legacyRequestId = 0))
        assertEquals("minecraft:mushroom_stew", session.heldStack?.item?.name)

        session.hunger.food = 10
        clientSends(useInAir(slot = 0, held = STEW_ITEM, legacyRequestId = 0))
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = STEW_ITEM, legacyRequestId = 0))
        assertEquals(16, session.hunger.food)
        assertEquals("minecraft:bowl", session.heldStack?.item?.name)
    }

    @Test
    fun `letting go of the use button stops a player eating, so the next use starts afresh`() {
        spawn()
        session.give(ItemStack(apple))
        session.hunger.food = 10

        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        repeat(20) { session.tickHealth() }
        clientSends(releaseItem(slot = 0, held = APPLE_ITEM))
        repeat(12) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        assertEquals(10, session.hunger.food)

        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        assertEquals(14, session.hunger.food)
    }

    @Test
    fun `with food switched off, food is only an item, and with hunger switched off, the food bar stays full`() {
        world.features = Features(hunger = false, food = false)
        spawn()
        session.give(ItemStack(apple))
        session.hunger.food = 10

        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        assertEquals(1, session.heldStack?.count)
        assertEquals(20, session.hunger.food)

        session.hunger.food = 10
        world[BlockPos(1, -63, 0)] = cakes[0]
        clientSends(placeBlock(against = BlockPos(1, -63, 0), face = 1, slot = 1, held = 0))
        assertEquals(cakes[0], world.stateAt(BlockPos(1, -63, 0)))
    }

    @Test
    fun `a well-fed player heals a point every four seconds, which tires them, and a starving one is hurt, down to half a heart on normal`() {
        spawn()
        session.vitals.health = 10f
        repeat(80) { session.tickHealth() }
        assertEquals(11f, session.vitals.health)
        assertEquals(4f, session.hunger.saturation) // Healing's 6 exhaustion took a point

        session.hunger.food = 0
        session.hunger.saturation = 0f
        session.vitals.health = 2f
        repeat(80) { session.tickHealth() }
        assertEquals(1f, session.vitals.health)
        repeat(160) { session.tickHealth() }
        assertEquals(1f, session.vitals.health)
    }

    @Test
    fun `sprinting on the ground tires a player by the block, which takes their saturation before their food, but a teleport doesn't`() {
        spawn()
        clientSends(playerAuthInput(standing, inputs = listOf(START_SPRINTING, VERTICAL_COLLISION), tick = 101))
        for (step in 1..45) clientSends(playerAuthInput(Vec3(standing.x + step, standing.y, standing.z), inputs = listOf(VERTICAL_COLLISION), tick = 101L + step))
        assertEquals(4f, session.hunger.saturation)
        assertEquals(20, session.hunger.food)

        val tired = session.hunger.exhaustion
        clientSends(playerAuthInput(Vec3(standing.x + 145, standing.y, standing.z), inputs = listOf(VERTICAL_COLLISION), tick = 200))
        assertEquals(tired, session.hunger.exhaustion)
    }

    @Test
    fun `a potion drunk gives its effects and leaves its bottle, though it doesn't feed, and a healing one heals at once`() {
        spawn()
        session.give(ItemStack(potion, aux = 28)) // Regeneration
        sent.clear()

        clientSends(useInAir(slot = 0, held = POTION_ITEM, legacyRequestId = 0)) // A full player can still drink
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = POTION_ITEM, legacyRequestId = 0))
        assertTrue(receivedPackets().any { it.contentEquals(MobEffect(7, MobEffect.ADD, Effect.REGENERATION, 0, 900).encode()) })
        assertEquals("minecraft:glass_bottle", session.heldStack?.item?.name)
        assertEquals(5f, session.hunger.saturation)

        session.give(ItemStack(potion, aux = 22)) // Healing II
        clientSends(useInAir(slot = 1, held = POTION_ITEM, legacyRequestId = 0))
        repeat(32) { session.tickHealth() }
        session.vitals.health = 10f
        clientSends(useInAir(slot = 1, held = POTION_ITEM, legacyRequestId = 0))
        assertEquals(18f, session.vitals.health)
    }

    @Test
    fun `milk takes every effect away, and leaves the bucket`() {
        spawn()
        session.setEffect(EffectLevel(Effect.SPEED), 600, particles = true)
        session.give(ItemStack(milk))
        sent.clear()

        clientSends(useInAir(slot = 0, held = MILK_ITEM, legacyRequestId = 0))
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = MILK_ITEM, legacyRequestId = 0))
        assertTrue(receivedPackets().any { it.contentEquals(MobEffect(7, MobEffect.REMOVE, Effect.SPEED).encode()) })
        assertEquals("minecraft:bucket", session.heldStack?.item?.name)
    }

    @Test
    fun `chorus fruit can't be started on again until its wait is over, which the client hears of`() {
        spawn()
        session.give(ItemStack(chorusFruit, count = 3))
        sent.clear()

        clientSends(useInAir(slot = 0, held = CHORUS_ITEM, legacyRequestId = 0))
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = CHORUS_ITEM, legacyRequestId = 0))
        assertEquals(2, session.heldStack?.count)
        assertTrue(receivedPackets().any { it.contentEquals(PlayerStartItemCooldown("chorusfruit", 20).encode()) })

        clientSends(releaseItem(slot = 0, held = CHORUS_ITEM))
        clientSends(useInAir(slot = 0, held = CHORUS_ITEM, legacyRequestId = 0)) // Too soon to start another
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = CHORUS_ITEM, legacyRequestId = 0)) // So this only starts it
        assertEquals(2, session.heldStack?.count)
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = CHORUS_ITEM, legacyRequestId = 0))
        assertEquals(1, session.heldStack?.count)
    }

    @Test
    fun `holding the use button down eats one after another, each use that finishes one starting on the next`() {
        spawn()
        session.give(ItemStack(apple, count = 3))
        session.hunger.food = 5

        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        assertEquals(9, session.hunger.food)
        repeat(32) { session.tickHealth() }
        clientSends(useInAir(slot = 0, held = APPLE_ITEM, legacyRequestId = 0))
        assertEquals(13, session.hunger.food)
        assertEquals(1, session.heldStack?.count)
    }

    @Test
    fun `the client's eating goes back to it and everyone around, by what the player holds, so they hear it`() {
        spawn()
        session.give(ItemStack(potion, aux = 5))
        sent.clear()

        clientSends(eatingEvent(data = 0))
        assertTrue(receivedPackets().any { it.contentEquals(ActorEvent(7, ActorEvent.EATING_ITEM, POTION_ITEM shl 16 or 5).encode()) })

        clientSends(mobEquipment(slot = 1)) // Nothing to eat in hand
        clientSends(eatingEvent(data = 0))
        assertTrue(receivedPackets().none { it.contentEquals(ActorEvent(7, ActorEvent.EATING_ITEM, POTION_ITEM shl 16 or 5).encode()) })
    }

    @Test
    fun `a hungry player eats cake a slice at a time, the last slice taking the cake, but a full one can't`() {
        spawn()
        val at = BlockPos(1, -63, 0)
        world[at] = cakes[0]

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))
        assertEquals(cakes[0], world.stateAt(at))

        session.hunger.food = 10
        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))
        assertEquals(cakes[1], world.stateAt(at))
        assertEquals(12, session.hunger.food)
        assertEquals(5f + 2 * 0.1f * 2, session.hunger.saturation)

        world[at] = cakes[6]
        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))
        assertEquals(AIR, world.stateAt(at)?.name)
    }

    @Test
    fun `a lit candle on a cake goes out at a click, and the next eats a slice, popping the candle off`() {
        spawn()
        val at = BlockPos(1, -63, 0)
        world[at] = candleCakes[1]
        session.hunger.food = 10

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))
        assertEquals(candleCakes[0], world.stateAt(at))
        assertEquals(10, session.hunger.food)

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = 0))
        assertEquals(cakes[1], world.stateAt(at))
        assertEquals(listOf(ItemStack(candle)), world.items.map { it.stack })
    }

    @Test
    fun `a candle goes on a whole cake, used up, but a bitten one is eaten instead`() {
        spawn()
        val at = BlockPos(1, -63, 0)
        world[at] = cakes[0]
        session.give(ItemStack(candle, count = 2))
        session.hunger.food = 10

        clientSends(placeBlock(against = at, face = 1, slot = 0, held = CANDLE_ITEM))
        assertEquals(candleCakes[0], world.stateAt(at))
        assertEquals(1, session.heldStack?.count)
        assertEquals(10, session.hunger.food)

        world[at] = cakes[1]
        clientSends(placeBlock(against = at, face = 1, slot = 0, held = CANDLE_ITEM))
        assertEquals(cakes[2], world.stateAt(at))
        assertEquals(1, session.heldStack?.count)
    }

    @Test
    fun `with the doTileDrops rule off, a survival break drops nothing`() {
        spawn()
        world.rules.set("doTileDrops", false)
        val block = BlockPos(1, -63, 0)
        world[block] = planks
        clientSends(breakingInput(tick = 200, START_DESTROY to block))
        clientSends(breakingInput(tick = 260, STOP_DESTROY to block, destroying = block))
        assertEquals(AIR_NETWORK_ID, world.blockAt(block))
        assertTrue(world.items.isEmpty())
    }

    @Test
    fun `a survival break drops the block, which the player sees land and picks up`() {
        spawn()
        val block = BlockPos(1, -63, 0)
        world[block] = planks
        clientSends(breakingInput(tick = 200, START_DESTROY to block))
        clientSends(breakingInput(tick = 260, STOP_DESTROY to block, destroying = block))

        val dropped = world.items.single()
        assertEquals(ItemStack(creative.items[0].stack.item, block = planks), dropped.stack)
        sent.clear()
        session.syncItems()
        assertEquals(0x0F, receivedPacket()[0].toInt()) // AddItemActor

        // It can't be picked up for ten ticks. It pops out a random way, so the player then walks over to it.
        session.collectItems()
        assertTrue(world.items.isNotEmpty())
        repeat(10) { world.tickItems() }
        clientSends(playerAuthInput(Vec3(dropped.position.x, dropped.position.y + 1.62f, dropped.position.z)))
        sent.clear()
        session.collectItems()

        val (slot, taken) = sent.map { batches.decode(it).single() }
        assertContentEquals(InventorySlot(0, dropped.stack, stackId = 1).encode(), slot)
        assertContentEquals(TakeItemActor(dropped.id, takerId = 7).encode(), taken)
        assertTrue(world.items.isEmpty())
        sent.clear()
        session.syncItems()
        assertContentEquals(RemoveActor(dropped.id).encode(), receivedPacket())
    }

    @Test
    fun `breaking a block wears the tool it's broken with, which the client hears`() {
        spawn()
        val ground = BlockPos(1, -64, 0)
        session.give(ItemStack(pickaxe))

        // A wooden pickaxe takes 23 ticks over stone. The client didn't predict the wear here.
        clientSends(breakingInput(tick = 200, START_DESTROY to ground))
        sent.clear()
        clientSends(breakingInput(tick = 223, STOP_DESTROY to ground, destroying = ground, held = PICKAXE_ITEM))

        assertEquals(AIR_NETWORK_ID, world.blockAt(ground))
        assertEquals(1, session.heldStack?.damage)
        val slot = InventorySlot(0, ItemStack(pickaxe).withDamage(1), stackId = 1).encode()
        assertEquals(1, receivedPackets().count { it.contentEquals(slot) })
    }

    @Test
    fun `the tool a break wears goes by the break's legacy request ID until the client hears what it is now`() {
        spawn()
        val ground = BlockPos(1, -64, 0)
        session.give(ItemStack(pickaxe))
        clientSends(breakingInput(tick = 200, START_DESTROY to ground))
        sent.clear()

        clientSends(breakingInput(tick = 223, STOP_DESTROY to ground, destroying = ground, held = PICKAXE_ITEM, legacyRequestId = -5062))
        val slot = InventorySlot(0, ItemStack(pickaxe).withDamage(1), stackId = 1).encode()
        assertEquals(1, receivedPackets().count { it.contentEquals(slot) }) // Once, not again for the acknowledgement

        // Request -9 drops the pickaxe the client still names -5062.
        clientSends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-9).writeVarUInt(1)
                    .writeVarUInt(3).writeByte(3).writeByte(1).writeByte(28).writeBoolean(false).writeByte(0).writeIntLE(-5062).writeBoolean(false)
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )
        assertEquals(0, receivedPacket()[3].toInt()) // The response's result: accepted
        assertNull(session.heldStack)
    }

    @Test
    fun `a tool worn out breaks, which everyone around hears`() {
        spawn()
        val ground = BlockPos(1, -64, 0)
        session.give(ItemStack(pickaxe).withDamage(59))
        clientSends(breakingInput(tick = 200, START_DESTROY to ground))
        sent.clear()

        clientSends(breakingInput(tick = 223, STOP_DESTROY to ground, destroying = ground, held = PICKAXE_ITEM))

        assertNull(session.heldStack)
        val packets = receivedPackets()
        assertTrue(packets.any { it.contentEquals(LevelSoundEvent(LevelSoundEvent.BREAK, standing, -1).encode()) })
        assertTrue(packets.any { it.contentEquals(InventorySlot(0, stack = null, stackId = 0).encode()) })
    }

    @Test
    fun `an orb mends a worn item with Mending, rather than give the player experience`() {
        spawn()
        val mending = ItemStack(pickaxe).withDamage(10).withEnchantments(listOf(EnchantmentLevel(MENDING, 1)))
        session.give(mending)
        clientSends(playerAuthInput(standing))
        world.spawnOrb(3, Vec3(standing.x, standing.y - 1.62f, standing.z), Vec3(0f, 0f, 0f))
        sent.clear()

        session.collectOrbs()

        // Two uses a point, and none of the three left over.
        val mended = mending.withDamage(4)
        assertEquals(mended, session.heldStack)
        val expected = listOf(LevelEvent(LevelEvent.SOUND_EXPERIENCE_ORB, standing, 0), InventorySlot(0, mended, stackId = 1))
        assertEquals(expected.map { it.encode().toList() }, receivedPackets().map { it.toList() })
    }

    @Test
    fun `a dropped item is thrown from the player, out of their reach for two seconds`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickPlanksIntoHotbar()

        // Request -3 drops 10 of the stack in hotbar slot 0, which is stack 1.
        clientSends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-3).writeVarUInt(1)
                    .writeVarUInt(3).writeByte(3).writeByte(10).writeByte(28).writeBoolean(false).writeByte(0).writeIntLE(1).writeBoolean(false)
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )

        val thrown = world.items.single()
        assertEquals(10, thrown.stack.count)
        assertEquals(40, thrown.pickupDelay)
        assertEquals(standing.y - 0.3f, thrown.position.y, 0.001f) // From just below the player's eyes
        assertTrue(thrown.velocity.z > 0) // The way they look: south, with a yaw of 0
    }

    @Test
    fun `items that merge go from the client's world but one, which it hears holds them all`() {
        spawn()
        val stack = ItemStack(creative.items[0].stack.item, block = planks)
        val smaller = world.spawnItem(stack, Vec3(5.5f, -63f, 5.5f), Vec3(0f, 0f, 0f), pickupDelay = 0)
        val larger = world.spawnItem(stack.copy(count = 2), Vec3(5.5f, -63f, 6f), Vec3(0f, 0f, 0f), pickupDelay = 0)
        session.syncItems()
        sent.clear()

        repeat(20) { world.tickItems() } // On the ground, they look for each other once a second
        session.syncItems()

        val expected = listOf(RemoveActor(smaller.id), ActorEvent(larger.id, ActorEvent.UPDATE_STACK_SIZE, 3))
        assertEquals(expected.map { it.encode().toList() }, receivedPackets().map { it.toList() })
    }

    @Test
    fun `pressing Q throws from a hotbar slot, if the client had there what Kore has`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickPlanksIntoHotbar()
        val planksStack = creative.items[0].stack

        clientSends(dropFromSlot(slot = 0, had = 64, dropping = 1))
        val thrown = world.items.single()
        assertEquals(1, thrown.stack.count)
        assertEquals(40, thrown.pickupDelay)
        assertContentEquals(InventorySlot(0, planksStack.copy(count = 63), stackId = 1).encode(), receivedPacket())

        // A client that still counts 64 is out of step: nothing is thrown, and it hears what the slot holds.
        clientSends(dropFromSlot(slot = 0, had = 64, dropping = 64))
        assertEquals(1, world.items.size)
        assertContentEquals(InventorySlot(0, planksStack.copy(count = 63), stackId = 1).encode(), receivedPacket())

        // Ctrl+Q throws the whole stack.
        clientSends(dropFromSlot(slot = 0, had = 63, dropping = 63))
        assertEquals(63, world.items.last().stack.count)
        assertContentEquals(InventorySlot(0, stack = null, stackId = 0).encode(), receivedPacket())
    }

    @Test
    fun `a transaction with a legacy request ID gets the slots it changed back, which go by that ID meanwhile`() {
        config = config.copy(gameMode = GameMode.CREATIVE)
        spawn()
        pickPlanksIntoHotbar()

        clientSends(placeBlock(against = BlockPos(1, -64, 0), face = 1, slot = 0, legacyRequestId = -6058))
        val slot = InventorySlot(0, creative.items[0].stack.copy(count = 64), stackId = 1).encode()
        assertTrue(sent.flatMap { batches.decode(it) }.any { it.contentEquals(slot) })

        // Request -9 destroys one of the stack the client still names -6058.
        sent.clear()
        clientSends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-9).writeVarUInt(1)
                    .writeVarUInt(4).writeByte(4).writeByte(1).writeByte(28).writeBoolean(false).writeByte(0).writeIntLE(-6058)
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )
        assertEquals(0, receivedPacket()[3].toInt()) // The response's result: accepted
    }

    @Test
    fun `placing in survival uses up the stack, and the client is put right if a placement fails`() {
        spawn()
        world.spawnItem(ItemStack(creative.items[0].stack.item, count = 2, block = planks), Vec3(1f, -63f, 0.5f), Vec3(0f, 0f, 0f), pickupDelay = 0)
        session.collectItems()

        clientSends(placeBlock(against = BlockPos(1, -64, 0), face = 1, slot = 0))
        clientSends(placeBlock(against = BlockPos(2, -64, 0), face = 1, slot = 0))
        assertEquals(planks, world.stateAt(BlockPos(2, -63, 0)))

        // Both are used up, so a third placement fails, and the client hears its slot is empty.
        sent.clear()
        clientSends(placeBlock(against = BlockPos(3, -64, 0), face = 1, slot = 0))
        assertEquals(AIR_NETWORK_ID, world.blockAt(BlockPos(3, -63, 0)))
        assertContentEquals(InventorySlot(0, stack = null, stackId = 0).encode(), receivedPacket())
    }

    @Test
    fun `the held slot follows the client`() {
        spawn()

        clientSends(mobEquipment(slot = 3))
        assertEquals(3, session.heldSlot)

        clientSends(mobEquipment(slot = 12)) // Not in the hotbar
        assertEquals(3, session.heldSlot)
    }

    @Test
    fun `a client that cancels during resource packs goes no further`() {
        logIn()

        clientSends(packResponse(0, "cancel"))
        clientSends(packResponse(2, "downloadingfinished"))

        assertTrue(sent.isEmpty())
        assertFalse(session.isLoadingWorld)
    }

    @Test
    fun `a login that doesn't authenticate is disconnected`() {
        negotiateSettings()

        clientSends(login(TestAuthority().multiplayerToken(clientKey)))
        clientSends(login(authority.multiplayerToken(clientKey)))

        // Disconnect (5) with reason NotAuthenticated (46, zigzagged to 92) and no custom message.
        assertContentEquals(bytes(0x05, 0x5C, 0x01), receivedPacket())
        assertEquals(1, sent.size)
        assertNull(session.player)
    }

    @Test
    fun `packets out of order are ignored`() {
        clientSends(login(authority.multiplayerToken(clientKey)))

        assertNull(session.player)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a malformed batch doesn't derail the session`() {
        negotiateSettings()

        session.receive(bytes(0x05, 0x01))
        clientSends(login(authority.multiplayerToken(clientKey)))

        assertEquals("Steve", session.player?.displayName)
    }

    private fun negotiateSettings() {
        clientSends(requestNetworkSettings(PROTOCOL))
        sent.clear()
        batches.enableCompression(256)
    }

    private fun logIn() {
        negotiateSettings()
        clientSends(login(authority.multiplayerToken(clientKey)))
        sent.clear()
    }

    private fun loadWorld() {
        logIn()
        clientSends(packResponse(2, "downloadingfinished"))
        clientSends(packResponse(3, "resourcepackstackfinished"))
        sent.clear()
    }

    private fun requestChunkRadius(radius: Int, maxRadius: Int) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(69).writeVarInt(radius).writeByte(maxRadius))

    private fun clientCacheStatus(supported: Boolean) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(129).writeBoolean(supported))

    private fun packResponse(tag: Int, name: String) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(8).writeVarUInt(tag).writeString(name))

    private fun clientSends(vararg packets: ByteArray) = session.receive(batches.encode(packets.toList()))

    /** The client's loading screen opening (1) or closing (2), with no ID. */
    private fun loadingScreen(type: Int) = ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(312).writeVarInt(type).writeBoolean(false))

    private fun receivedPacket() = batches.decode(sent.last()).single()

    /** Every packet sent since the last look, in order, across however many batches carried them. */
    private fun receivedPackets() = sent.flatMap { batches.decode(it) }.also { sent.clear() }

    /** Spawns the player into a view two chunks wide, and forgets what was sent on the way. */
    private fun spawn() {
        loadWorld()
        clientSends(requestChunkRadius(radius = 2, maxRadius = 2))
        clientSends(setLocalPlayerAsInitialised(runtimeId = 7))
        sent.clear()
    }

    /** A PlayerAuthInput with the player at [position], the input flags numbered in [inputs] set, and nothing else going on. */
    private fun playerAuthInput(position: Vec3, inputs: List<Int> = emptyList(), tick: Long = 100) = ByteBufUtil.getBytes(
        Unpooled.buffer()
            .writeVarUInt(144)
            .writeZero(8).writeFloatLE(position.x).writeFloatLE(position.y).writeFloatLE(position.z)
            .writeZero(12).writeVarUInt(inputs.size).apply { inputs.forEach { writeVarInt(it) } }
            .writeVarUInt(1).writeVarUInt(0).writeVarInt(1).writeZero(8)
            .writeVarULong(tick).writeZero(12).writeZero(5).writeZero(28),
    )

    /** The player's actor data as Kore sends it when they start or stop sprinting or sneaking. */
    private fun playerData(vararg flags: ActorFlag, height: Float, tick: Long): ByteArray {
        val always = setOf(ActorFlag.BREATHING, ActorFlag.HAS_GRAVITY, ActorFlag.HAS_COLLISION, ActorFlag.CAN_CLIMB)
        return SetActorData(7, listOf(flagsItem(always + flags), FloatItem(ActorDataId.HEIGHT, height)), tick).encode()
    }

    private val standing = Vec3(0.5f, -61.38f, 0.5f)

    private fun interact(action: Int, target: Long) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(33).writeByte(action).writeVarULong(target).writeBoolean(false))

    private fun containerClose(window: Int, type: Int) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(47).writeByte(window).writeByte(type).writeBoolean(false))

    /**
     * A PlayerAuthInput for the player standing still at the client's
     * [tick], carrying the steps of breaking a block in [actions], each
     * against its upper face, and [destroying] a block, if it's set.
     */
    private fun breakingInput(tick: Long, vararg actions: Pair<Int, BlockPos>, destroying: BlockPos? = null, held: Int = 0, legacyRequestId: Int = 0) = ByteBufUtil.getBytes(
        Unpooled.buffer()
            .writeVarUInt(144)
            .writeZero(8).writeFloatLE(standing.x).writeFloatLE(standing.y).writeFloatLE(standing.z)
            .writeZero(12).writeVarUInt(0)
            .writeVarUInt(1).writeVarUInt(0).writeVarInt(1).writeZero(8)
            .writeVarULong(tick).writeZero(12)
            .writeBoolean(destroying != null)
            .apply {
                if (destroying != null) {
                    writeVarInt(legacyRequestId).writeBoolean(false)
                    // A legacy request names the slot it changes, hotbar slot 0, wearing what's held there.
                    if (legacyRequestId == 0) writeVarUInt(0) else writeVarUInt(1).writeVarUInt(0).writeBoolean(true).writeByte(0).writeBoolean(false).writeVarUInt(0).heldItem(held).heldItem(held)
                    writeVarInt(2).writeByte(1).writeBlockPos(destroying).writeByte(1).writeVarInt(0).writeByte(0) // Destroy, from hotbar slot 0
                    heldItem(held)
                    writeZero(24).writeVarUInt(stone.networkId).writeByte(1).writeByte(0)
                }
            }
            .writeBoolean(false) // No item stack request
            .writeBoolean(actions.isNotEmpty())
            .apply {
                if (actions.isNotEmpty()) {
                    writeVarUInt(actions.size)
                    actions.forEach { (action, pos) -> writeVarInt(action).writeBlockPos(pos).writeVarInt(1) }
                }
            }
            .writeZero(2).writeZero(28), // No vehicle, then the move vectors and camera
    )

    private fun playerAction(action: Int, position: BlockPos) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(36).writeVarULong(7).writeVarInt(action).writeBlockPos(position).writeBlockPos(BlockPos(0, 0, 0)).writeVarInt(1),
    )

    /** A block's data, [data], as a client sends back a sign it's written on. */
    private fun blockActorData(position: BlockPos, data: NbtMap) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(56).writeBlockPos(position).also { NbtUtils.createNetworkWriter(ByteBufOutputStream(it)).writeTag(data) },
    )

    /** Picks a stack of oak planks from the creative inventory into hotbar slot 0, and forgets the answer. */
    private fun pickPlanksIntoHotbar() = pickIntoHotbar(creativeItem = 1, slot = 0)

    /** Picks a stack of [creativeItem] from the creative inventory into an empty hotbar [slot], and forgets the answer. */
    private fun pickIntoHotbar(creativeItem: Int, slot: Int) {
        clientSends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(147).writeVarUInt(1).writeVarInt(-1).writeVarUInt(2)
                    .writeVarUInt(12).writeByte(14).writeVarUInt(creativeItem).writeByte(1) // The creative item, once
                    .writeVarUInt(0).writeByte(0).writeByte(64) // Taking all 64
                    .writeByte(60).writeBoolean(false).writeByte(50).writeIntLE(-1) // From the created output
                    .writeByte(28).writeBoolean(false).writeByte(slot).writeIntLE(0) // Into the hotbar
                    .writeVarUInt(0).writeIntLE(0),
            ),
        )
        sent.clear()
    }

    /**
     * An inventory transaction clicking [face] of the block at [against] in
     * the middle, holding the item [held] in hotbar [slot]: oak planks,
     * unless it says otherwise, or nothing for 0. The client found the
     * click works unless [predictedToWork] says otherwise, and the player
     * clicked, rather than holding the button down, unless [repeated] does.
     */
    private fun placeBlock(
        against: BlockPos, face: Int, slot: Int, held: Int = PLANKS_ITEM, predictedToWork: Boolean = true, legacyRequestId: Int = 0, repeated: Boolean = false,
    ) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(30).writeVarInt(legacyRequestId).writeBoolean(false).writeVarUInt(2)
            .apply {
                // A legacy request names the slot it changes, here unchanged, as a creative player's is.
                if (legacyRequestId == 0) writeVarUInt(0) else writeVarUInt(1).writeVarUInt(0).writeBoolean(true).writeByte(0).writeBoolean(false).writeVarUInt(slot).heldItem(held).heldItem(held)
            }
            .writeVarInt(0).writeByte(if (repeated) 2 else 1).writeBlockPos(against).writeByte(face).writeVarInt(slot).writeByte(0)
            .heldItem(held)
            .writeFloatLE(standing.x).writeFloatLE(standing.y).writeFloatLE(standing.z)
            .writeFloatLE(0.5f).writeFloatLE(1f).writeFloatLE(0.5f)
            .writeVarUInt(stone.networkId).writeBoolean(predictedToWork).writeByte(0),
    )

    /**
     * An inventory transaction using the item [held], one of it, in hotbar
     * [slot] in the air, by legacy request [legacyRequestId], as putting on
     * armour does: the client has emptied the slot already.
     */
    private fun useInAir(slot: Int, held: Int, legacyRequestId: Int) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(30).writeVarInt(legacyRequestId).writeBoolean(false).writeVarUInt(2)
            .writeVarUInt(1).writeVarUInt(0).writeBoolean(true).writeByte(0).writeBoolean(false).writeVarUInt(slot).heldItem(held, 1).heldItem(0)
            .writeVarInt(1).writeByte(1).writeBlockPos(BlockPos(0, 0, 0)).writeByte(255).writeVarInt(slot).writeByte(0)
            .heldItem(held, 1)
            .writeFloatLE(standing.x).writeFloatLE(standing.y).writeFloatLE(standing.z)
            .writeFloatLE(0f).writeFloatLE(0f).writeFloatLE(0f)
            .writeVarUInt(0).writeBoolean(false).writeByte(0),
    )

    /** An inventory transaction letting go of the use button with [held] in hotbar [slot], as stopping eating does. */
    private fun releaseItem(slot: Int, held: Int) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(30).writeVarInt(0).writeBoolean(false).writeVarUInt(4).writeVarUInt(0)
            .writeVarUInt(0).writeVarInt(slot).heldItem(held, 1).writeZero(12),
    )

    /** The client saying its player is eating, as it does every few ticks while it eats, with [data] about what. */
    private fun eatingEvent(data: Int) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(27).writeVarULong(7).writeByte(ActorEvent.EATING_ITEM).writeVarInt(data).writeBoolean(false))

    private fun mobEquipment(slot: Int) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(31).writeVarULong(7).heldItem(PLANKS_ITEM).writeByte(slot).writeByte(slot).writeByte(0),
    )

    /**
     * An inventory transaction dropping [dropping] of the [had] of [item],
     * oak planks unless said, in hotbar [slot], as pressing Q does: they
     * leave the slot and go into the world.
     */
    private fun dropFromSlot(slot: Int, had: Int, dropping: Int, item: Int = PLANKS_ITEM) = ByteBufUtil.getBytes(
        Unpooled.buffer().writeVarUInt(30).writeVarInt(0).writeBoolean(false).writeVarUInt(0).writeVarUInt(2)
            .writeVarUInt(2).writeBoolean(false).writeBoolean(true).writeVarUInt(0).writeVarUInt(0) // Into the world, dropped
            .heldItem(0).heldItem(item, dropping)
            .writeVarUInt(0).writeBoolean(true).writeByte(0).writeBoolean(false).writeVarUInt(slot) // Out of the inventory
            .heldItem(item, had).heldItem(if (had > dropping) item else 0, had - dropping),
    )

    /** A stack of [count] of the item [id], as the client describes what it holds. */
    private fun ByteBuf.heldItem(id: Int, count: Int = if (id == 0) 0 else 64): ByteBuf =
        writeShortLE(id).writeShortLE(count).writeVarUInt(0).writeBoolean(id != 0).apply { if (id != 0) writeVarInt(1) }
            .writeVarUInt(if (id == PLANKS_ITEM) planks.networkId else 0)
            .writeByteArray(ByteArray(10))

    private fun state(name: String, vararg properties: Pair<String, Any>) = BlockState(name, NbtMap.fromMap(mapOf(*properties)), version = 18168865)

    private fun setLocalPlayerAsInitialised(runtimeId: Long) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(113).writeVarULong(runtimeId))

    private fun requestNetworkSettings(protocol: Int) =
        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(193).writeInt(protocol))

    private fun login(token: String, skinData: String = "") =
        loginPacket(PROTOCOL, connectionRequest(tokenAuthentication(token), clientDataToken(client.private, skinData)))

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
