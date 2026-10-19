package org.koremc

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.koremc.auth.MultiplayerTokenVerifier
import org.koremc.auth.TestAuthority
import org.koremc.auth.ecKeyPair
import org.koremc.protocol.ActorDataId
import org.koremc.protocol.ActorEvent
import org.koremc.protocol.ARMOUR_WINDOW
import org.koremc.protocol.ActorFlag
import org.koremc.protocol.Animate
import org.koremc.protocol.BatchCodec
import org.koremc.protocol.DeathInfo
import org.koremc.protocol.Disconnect
import org.koremc.protocol.FloatItem
import org.koremc.protocol.GameMode
import org.koremc.protocol.InventoryContent
import org.koremc.protocol.InventorySlot
import org.koremc.protocol.LevelSoundEvent
import org.koremc.protocol.MovePlayer
import org.koremc.protocol.OFFHAND_WINDOW
import org.koremc.protocol.RemoveActor
import org.koremc.protocol.Respawn
import org.koremc.protocol.SetActorData
import org.koremc.protocol.SetActorMotion
import org.koremc.protocol.ShowArmour
import org.koremc.protocol.ShowEquipment
import org.koremc.protocol.Text
import org.koremc.protocol.clientDataToken
import org.koremc.protocol.connectionRequest
import org.koremc.protocol.encode
import org.koremc.protocol.PlayerStartItemCooldown
import org.koremc.protocol.flagsItem
import org.koremc.protocol.secondFlagsItem
import org.koremc.protocol.loginPacket
import org.koremc.protocol.readString
import org.koremc.protocol.readVarUInt
import org.koremc.protocol.readVarULong
import org.koremc.protocol.readVec3
import org.koremc.protocol.tokenAuthentication
import org.koremc.protocol.writeBlockPos
import org.koremc.protocol.writeByteArray
import org.koremc.protocol.writeString
import org.koremc.protocol.writeVarInt
import org.koremc.protocol.writeVarUInt
import org.koremc.protocol.writeVarULong
import org.koremc.registry.AIR
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import org.koremc.registry.CreativeInventory
import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.ItemVersion
import org.koremc.registry.THORNS
import org.koremc.registry.damage
import org.koremc.registry.withCustomName
import org.koremc.registry.withDamage
import org.koremc.registry.withEnchantments
import org.koremc.storage.PlayerSaves
import org.koremc.world.BlockPos
import org.koremc.world.DamageCause
import org.koremc.world.Drops
import org.koremc.world.Features
import org.koremc.world.FlatWorld
import org.koremc.world.Vec3
import java.security.interfaces.ECPublicKey
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PROTOCOL = 2193

// Packet IDs, for telling apart what a client got.
private const val ADD_PLAYER = 12
private const val REMOVE_ACTOR = 14
private const val PLAYER_LIST = 63
private const val MOB_EQUIPMENT = 31
private const val MOB_ARMOR_EQUIPMENT = 32

/** The input flag set on every tick a player sneaks. */
private const val SNEAKING = 8

/** The network IDs of the diamond sword and iron axe the tests hit with. */
private const val SWORD = 4
private const val AXE = 5

class PlayersTest {
    private val authority = TestAuthority()
    private val authenticator = LoginAuthenticator(MultiplayerTokenVerifier(keys = { authority.keys }), onlineMode = true)
    private var config = ServerConfig(protocol = PROTOCOL, viewDistance = 8, gameMode = GameMode.SURVIVAL)

    private val stone = BlockState("minecraft:stone", NbtMap.EMPTY, version = 18168865)
    private val palette = BlockPalette(listOf(BlockState(AIR, NbtMap.EMPTY, version = 18168865), stone))
    private val world = FlatWorld(palette, listOf(stone))
    private val creative = CreativeInventory(emptyList(), emptyList())
    private val helmet = ItemDefinition("minecraft:iron_helmet", 1, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 165)
    private val chestplate = item("minecraft:iron_chestplate", 2)
    private val shield = ItemDefinition("minecraft:shield", 3, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 336)
    private val sword = ItemDefinition("minecraft:diamond_sword", SWORD, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 1561)
    private val axe = ItemDefinition("minecraft:iron_axe", AXE, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 250)

    /** What every player had when they last left, or null for players new to the world. */
    private var saved: NbtMap? = null
    private val game = GameData(
        world, emptyList(), org.koremc.protocol.BlockDefinitions(emptyList()), creative, emptyMap(), Drops(emptyList(), creative, palette),
        items = ItemStacks(listOf(helmet, chestplate, shield, sword, axe), creative, palette),
        players = object : PlayerSaves {
            override fun load(player: UUID) = saved

            override fun save(player: UUID, data: NbtMap) {}
        },
    )

    private fun item(name: String, id: Int) = ItemDefinition(name, id, componentBased = false, ItemVersion.NONE, maxStackSize = 1)

    private val clients = mutableListOf<Client>()

    /** Tasks the sessions have asked to run later. Tests run them. */
    private val scheduled = mutableListOf<() -> Unit>()

    /** One player's client, with its own session on the shared server. */
    private inner class Client(val entityId: Long, val name: String, xuid: String) {
        private val key = ecKeyPair()
        private val batches = BatchCodec()
        private val sent = mutableListOf<ByteArray>()
        val session: Session = Session(
            config, game, networkId = "network-$entityId", entityId, key.public as ECPublicKey, authenticator,
            viewers = { clients.map { it.session } },
            schedule = { _, task -> scheduled += task },
        ) { sent += it }

        /** Every packet the client got as it joined. */
        val joining: List<ByteArray>

        init {
            clients += this
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(193).writeInt(PROTOCOL)))
            batches.enableCompression(256)
            sent.clear() // The network settings, from before compression
            val token = authority.multiplayerToken(key.public as ECPublicKey, xuid = xuid, displayName = name)
            sends(loginPacket(PROTOCOL, connectionRequest(tokenAuthentication(token), clientDataToken(key.private))))
            sends(packResponse(2, "downloadingfinished"))
            sends(packResponse(3, "resourcepackstackfinished"))
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(69).writeVarInt(2).writeByte(2)))
            // The loading screen closes as the player is initialised, in one batch.
            session.receive(
                batches.encode(
                    listOf(
                        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(312).writeVarInt(2).writeBoolean(false)),
                        ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(113).writeVarULong(entityId)),
                    ),
                ),
            )
            joining = received()
        }

        fun sends(packet: ByteArray) = session.receive(batches.encode(listOf(packet)))

        /** Every packet the client got since the last look. */
        fun received(): List<ByteArray> = sent.flatMap { batches.decode(it) }.also { sent.clear() }

        /** Moves the player's eyes to [position], looking the way [pitch] and [yaw] say, with the input flags numbered in [inputs]. */
        fun moves(position: Vec3, pitch: Float = 0f, yaw: Float = 0f, inputs: List<Int> = emptyList()) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer()
                    .writeVarUInt(144)
                    .writeFloatLE(pitch).writeFloatLE(yaw).writeFloatLE(position.x).writeFloatLE(position.y).writeFloatLE(position.z)
                    .writeZero(8).writeFloatLE(yaw) // Move vector, then head yaw
                    .writeVarUInt(inputs.size).apply { inputs.forEach { writeVarInt(it) } }
                    .writeVarUInt(1).writeVarUInt(0).writeVarInt(1).writeZero(8)
                    .writeVarULong(100).writeZero(12).writeZero(5).writeZero(28),
            ),
        )

        /** Hits the player [target] with the item [held] in hotbar [slot], or a bare hand, for 0, as the client says. */
        fun attacks(target: Long, held: Int = 0, slot: Int = 0) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(30).writeVarInt(0).writeBoolean(false).writeVarUInt(3).writeVarUInt(0)
                    .writeVarULong(target).writeVarInt(1).writeVarInt(slot)
                    .writeShortLE(held).writeShortLE(if (held == 0) 0 else 1).writeVarUInt(0).writeBoolean(false).writeVarUInt(0).writeByteArray(ByteArray(0))
                    .writeZero(24), // Where they stood, and where they hit
            ),
        )

        private fun packResponse(tag: Int, name: String) =
            ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(8).writeVarUInt(tag).writeString(name))
    }

    /** A tick of showing players to each other, as the server runs it. */
    private fun tick() = clients.forEach { it.session.syncPlayers() }

    @Test
    fun `a full server turns away anyone else who joins, telling them so, until someone leaves`() {
        game.maxPlayers = 1
        val steve = Client(1, "Steve", "1111")

        val alex = Client(2, "Alex", "2222")
        assertFalse(alex.session.isInGame)
        assertContentEquals(Disconnect(Disconnect.Reason.SERVER_FULL, "disconnectionScreen.serverFull").encode(), alex.joining.single())

        steve.session.leave()
        assertTrue(Client(3, "Alex", "2222").session.isInGame)
    }

    @Test
    fun `players in the game are on each other's lists, and appear to those who have their chunk`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        // A moment after each loading screen closes, everyone in the game hears who joined.
        assertTrue(steve.received().isEmpty())
        scheduled.forEach { it() }
        assertEquals(listOf(joined("Steve"), joined("Alex")).map { it.toList() }, steve.received().map { it.toList() })
        assertEquals(listOf(joined("Steve"), joined("Alex")).map { it.toList() }, alex.received().map { it.toList() })

        tick()

        // Each list has both players, the client's own included, before the other appears.
        val (list, added) = steve.received()
        assertEquals(listOf(PLAYER_LIST, ADD_PLAYER), listOf(idOf(list), idOf(added)))
        assertEquals(listOf(PLAYER_LIST, ADD_PLAYER), alex.received().map(::idOf))
        // Alex stands at spawn: their feet on the stone, where moves would give their eyes.
        val feet = positionIn(added)
        assertEquals(-63f, feet.y, 0.001f)
        tick()
        assertTrue(steve.received().isEmpty()) // Nothing changed
    }

    @Test
    fun `players see each other move, sneak, change what they hold and swing`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        tick()
        steve.received()

        alex.moves(Vec3(2.5f, -61.38f, 3.5f), pitch = 10f, yaw = 90f, inputs = listOf(SNEAKING))
        alex.sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(31).writeVarULong(2).writeZero(8).writeByte(3).writeByte(3).writeByte(0)))
        tick()

        val sneaking = setOf(ActorFlag.BREATHING, ActorFlag.HAS_GRAVITY, ActorFlag.HAS_COLLISION, ActorFlag.CAN_CLIMB, ActorFlag.SHOW_NAME, ActorFlag.ALWAYS_SHOW_NAME, ActorFlag.SNEAKING)
        val expected = listOf(
            MovePlayer(2, Vec3(2.5f, -61.38f, 3.5f), 10f, 90f, 90f, onGround = false),
            SetActorData(2, listOf(flagsItem(sneaking), FloatItem(ActorDataId.HEIGHT, 1.49f))),
            ShowEquipment(2, stack = null, slot = 3),
        )
        assertEquals(expected.map { it.encode().toList() }, steve.received().map { it.toList() })

        alex.received()

        // Swinging at nothing, as when mining.
        alex.sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(44).writeByte(1).writeVarULong(2).writeFloatLE(0f).writeBoolean(true).writeString("mine")))
        assertContentEquals(Animate(Animate.SWING_ARM, 2, swingSource = "mine").encode(), steve.received().single())
        assertTrue(alex.received().isEmpty()) // Their own client already showed it
    }

    @Test
    fun `a player hits another with what they hold, hurting them by it and knocking them away, and the weapon wears`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        alex.moves(Vec3(2.5f, -61.38f, 0.5f))
        tick()
        steve.session.give(ItemStack(sword))
        alex.received()
        val next = world.newStackId()

        steve.attacks(2, held = SWORD)
        assertEquals(12f, alex.session.vitals.health)
        assertEquals(1, steve.session.heldStack?.damage)
        // The client doesn't foresee the wear, so it's told, under a new stack ID, which it takes notice of.
        assertTrue(steve.received().any { it.contentEquals(InventorySlot(0, ItemStack(sword).withDamage(1), next + 1).encode()) })
        val got = alex.received()
        assertTrue(got.any { it.contentEquals(ActorEvent(2, ActorEvent.HURT, DamageCause.ATTACK.id).encode()) })
        assertTrue(got.any { it.contentEquals(SetActorMotion(2, Vec3(0.45f, 0.3608f, 0f)).encode()) })

        // Straight after, in the grace, a hit no harder does nothing.
        steve.attacks(2, held = SWORD)
        assertEquals(12f, alex.session.vitals.health)
    }

    @Test
    fun `a player sneaking with a shield raises it, which blocks hits from in front once it's up, wearing for it and knocking the hitter back`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        alex.session.give(ItemStack(shield))
        // East of Steve, facing west, toward him.
        alex.moves(Vec3(2.5f, -61.38f, 0.5f), yaw = 90f, inputs = listOf(SNEAKING))
        steve.session.give(ItemStack(sword))
        tick()
        steve.received()
        alex.session.tickHealth()
        tick()
        assertTrue(steve.received().any { it.contentEquals(SetActorData(2, listOf(secondFlagsItem(setOf(ActorFlag.BLOCKING)))).encode()) })

        // Only just up, it doesn't block yet.
        steve.attacks(2, held = SWORD)
        assertEquals(12f, alex.session.vitals.health)

        // A quarter of a second on, and after the grace from that hit, it does.
        repeat(10) { alex.session.tickHealth() }
        val health = alex.session.vitals.health
        steve.received()
        alex.received()
        val next = world.newStackId()
        steve.attacks(2, held = SWORD)
        assertEquals(health, alex.session.vitals.health)
        assertEquals(8, alex.session.heldStack?.damage)
        assertTrue(alex.received().any { it.contentEquals(InventorySlot(0, ItemStack(shield).withDamage(8), next + 1).encode()) })
        val got = steve.received()
        assertTrue(got.any { it.contentEquals(SetActorMotion(1, Vec3(-0.45f, 0.3608f, 0f)).encode()) })
        assertTrue(got.any { it.contentEquals(LevelSoundEvent(LevelSoundEvent.SHIELD_BLOCK, alex.session.position, -1).encode()) })
    }

    @Test
    fun `a shield doesn't cover hits from behind, and an axe's hit disables it for five seconds`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        alex.session.give(ItemStack(shield))
        // East of Steve, facing east, away from him.
        alex.moves(Vec3(2.5f, -61.38f, 0.5f), yaw = -90f, inputs = listOf(SNEAKING))
        steve.session.give(ItemStack(axe))
        tick()
        repeat(10) { alex.session.tickHealth() }
        steve.attacks(2, held = AXE)
        assertEquals(14f, alex.session.vitals.health)

        // Facing him, the shield blocks the axe once, wearing for it, and is disabled.
        alex.moves(Vec3(2.5f, -61.38f, 0.5f), yaw = 90f, inputs = listOf(SNEAKING))
        repeat(10) { alex.session.tickHealth() }
        alex.received()
        val health = alex.session.vitals.health
        steve.attacks(2, held = AXE)
        assertEquals(health, alex.session.vitals.health)
        assertEquals(6, alex.session.heldStack?.damage)
        assertTrue(alex.received().any { it.contentEquals(PlayerStartItemCooldown("shield", 100).encode()) })

        // Down, it lets the next hit through.
        steve.attacks(2, held = AXE)
        assertEquals(health - 6f, alex.session.vitals.health)
    }

    @Test
    fun `armour with Thorns pricks whoever hurts its wearer, knocking them back, wearing for it, and may kill them`() {
        // Everyone wears a helmet with seven levels of Thorns, which prick every time.
        val thorny = ItemStack(helmet).withEnchantments(listOf(EnchantmentLevel(THORNS, 7)))
        saved = NbtMap.builder().putList("Armor", NbtType.COMPOUND, listOf(ItemStacks.nbtOf(thorny, 0))).build()
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        alex.moves(Vec3(2.5f, -61.38f, 0.5f))
        tick()
        steve.received()
        alex.received()
        val next = world.newStackId()

        steve.attacks(2)
        // Between 1 and 5, less a little for Steve's own helmet.
        val pricked = 20f - steve.session.vitals.health
        assertTrue(pricked > 0.9f && pricked < 5f, "Steve lost $pricked")
        val got = steve.received()
        assertTrue(got.any { it.contentEquals(SetActorMotion(1, Vec3(-0.45f, 0.3608f, 0f)).encode()) })
        assertTrue(got.any { it.contentEquals(ActorEvent(1, ActorEvent.HURT, DamageCause.THORNS.id).encode()) })
        assertTrue(got.any { it.contentEquals(LevelSoundEvent(LevelSoundEvent.THORNS, steve.session.position, -1).encode()) })
        // The hit wears Alex's helmet a use, and the prick two more.
        assertTrue(alex.received().any { it.contentEquals(InventorySlot(0, thorny.withDamage(3), next + 2, ARMOUR_WINDOW).encode()) })

        repeat(10) { steve.session.tickHealth() }
        repeat(10) { alex.session.tickHealth() }
        steve.session.vitals.health = 0.5f
        steve.attacks(2)
        assertTrue(steve.session.dead)
        assertTrue(steve.received().any { it.contentEquals(DeathInfo("death.attack.thorns", listOf("Steve", "Alex")).encode()) })
    }

    @Test
    fun `a hit struck falling is critical, half again harder, with sparks for everyone to see`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        alex.moves(Vec3(2.5f, -61.38f, 0.5f))
        tick()
        steve.moves(Vec3(0.5f, -59.38f, 0.5f))
        steve.moves(Vec3(0.5f, -60.38f, 0.5f))
        alex.received()

        steve.attacks(2)
        assertEquals(18.5f, alex.session.vitals.health)
        assertTrue(alex.received().any { it.contentEquals(Animate(Animate.CRITICAL_HIT, 2, 55f).encode()) })
    }

    @Test
    fun `a player killed by another was slain by them, as everyone hears`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        tick()
        alex.session.vitals.health = 1f
        steve.received()

        steve.attacks(2)
        assertTrue(alex.session.dead)
        val slain = Text(Text.TRANSLATION, "%death.attack.player", parameters = listOf("Alex", "Steve"), needsTranslation = true)
        assertTrue(steve.received().any { it.contentEquals(slain.encode()) })
    }

    @Test
    fun `a player who respawns gets up for those who saw them die, rather than going and coming back, which loses skins`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        tick()
        alex.session.vitals.health = 1f
        steve.attacks(2)
        steve.received()

        alex.sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(45).writeZero(12).writeByte(Respawn.CLIENT_READY_TO_SPAWN).writeVarULong(2)))
        tick()
        val got = steve.received()
        assertTrue(got.any { it.contentEquals(ActorEvent(2, ActorEvent.SPAWN_ALIVE, 0).encode()) })
        assertTrue(got.none { it.contentEquals(RemoveActor(2).encode()) })
        assertFalse(alex.session.dead)
        // Their size again, which the client shrank as they fell, or their hitbox stays down by their feet.
        val standing = setOf(ActorFlag.BREATHING, ActorFlag.HAS_GRAVITY, ActorFlag.HAS_COLLISION, ActorFlag.CAN_CLIMB, ActorFlag.SHOW_NAME, ActorFlag.ALWAYS_SHOW_NAME)
        val size = SetActorData(2, listOf(flagsItem(standing), FloatItem(ActorDataId.WIDTH, 0.6f), FloatItem(ActorDataId.HEIGHT, 1.8f)))
        assertTrue(got.any { it.contentEquals(size.encode()) })
    }

    @Test
    fun `a player killed with a named weapon was slain using it, by its name`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        tick()
        steve.session.give(ItemStack(sword).withCustomName("Super Swatter"))
        alex.session.vitals.health = 1f
        steve.received()

        steve.attacks(2, held = SWORD)
        val slain = Text(Text.TRANSLATION, "%death.attack.player.item", parameters = listOf("Alex", "Steve", "Super Swatter"), needsTranslation = true)
        assertTrue(steve.received().any { it.contentEquals(slain.encode()) })
    }

    @Test
    fun `with the pvp rule off, or combat switched off, a hit does nothing`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        tick()

        world.rules.set("pvp", false)
        steve.attacks(2)
        assertEquals(20f, alex.session.vitals.health)

        world.rules.set("pvp", true)
        world.features = Features(combat = false)
        steve.attacks(2)
        assertEquals(20f, alex.session.vitals.health)
    }

    @Test
    fun `players see what each other wear and hold in their offhand, as they appear and as it changes`() {
        // Everyone joins with the helmet and shield they left with, and a chestplate in hand.
        saved = NbtMap.builder()
            .putList("Inventory", NbtType.COMPOUND, listOf(ItemStacks.nbtOf(ItemStack(chestplate), 0)))
            .putList("Armor", NbtType.COMPOUND, listOf(ItemStacks.nbtOf(ItemStack(helmet), 0)))
            .putList("Offhand", NbtType.COMPOUND, listOf(ItemStacks.nbtOf(ItemStack(shield), 0)))
            .build()
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        tick()

        val seen = steve.received()
        assertEquals(listOf(PLAYER_LIST, ADD_PLAYER, MOB_EQUIPMENT, MOB_ARMOR_EQUIPMENT), seen.map(::idOf))
        assertContentEquals(ShowEquipment(2, ItemStack(shield), 0, OFFHAND_WINDOW).encode(), seen[2])
        assertContentEquals(ShowArmour(2, listOf(ItemStack(helmet), null, null, null)).encode(), seen[3])

        // Alex puts the chestplate on, using it from their hand.
        alex.sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(30).writeVarInt(-38388).writeBoolean(false).writeVarUInt(2)
                    .writeVarUInt(1).writeVarUInt(0).writeBoolean(true).writeByte(0).writeBoolean(false).writeVarUInt(0).heldItem(2).heldItem(0)
                    .writeVarInt(1).writeByte(1).writeBlockPos(BlockPos(0, 0, 0)).writeByte(255).writeVarInt(0).writeByte(0)
                    .heldItem(2)
                    .writeFloatLE(0.5f).writeFloatLE(-61.38f).writeFloatLE(0.5f)
                    .writeFloatLE(0f).writeFloatLE(0f).writeFloatLE(0f)
                    .writeVarUInt(0).writeBoolean(false).writeByte(0),
            ),
        )
        tick()
        assertEquals(
            listOf(ShowEquipment(2, null, 0).encode().toList(), ShowArmour(2, listOf(ItemStack(helmet), ItemStack(chestplate), null, null)).encode().toList()),
            steve.received().map { it.toList() },
        )
    }

    @Test
    fun `clearing the offhand tells the client all of it, which it takes notice of, as it may not of the one slot`() {
        saved = NbtMap.builder().putList("Offhand", NbtType.COMPOUND, listOf(ItemStacks.nbtOf(ItemStack(shield), 0))).build()
        val steve = Client(1, "Steve", "1111")
        steve.received()

        assertEquals(1, steve.session.clearItems(-1) { true })
        assertTrue(steve.received().any { it.contentEquals(InventoryContent(OFFHAND_WINDOW, listOf(null)).encode()) })
    }

    /** A stack of one of the item [id], as the client describes what it holds, the first stack it was given, or nothing for 0. */
    private fun ByteBuf.heldItem(id: Int): ByteBuf = writeShortLE(id).writeShortLE(if (id == 0) 0 else 1).writeVarUInt(0).writeBoolean(id != 0)
        .apply { if (id != 0) writeVarInt(1) }.writeVarUInt(0).writeByteArray(ByteArray(10))

    @Test
    fun `players go from view when they walk out of it, and from the list when they leave`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        tick()
        steve.received()

        // Steve sees five chunks across, so 100 blocks away is out of view.
        alex.moves(Vec3(100.5f, -61.38f, 0.5f))
        tick()
        assertContentEquals(RemoveActor(2).encode(), steve.received().single())

        // Alex's session ends, and Steve hears Alex left, then sees Alex go from the list.
        clients.remove(alex)
        alex.session.leave()
        assertContentEquals(
            Text(Text.TRANSLATION, "\u00a7e%multiplayer.player.left", parameters = listOf("Alex"), needsTranslation = true).encode(),
            steve.received().single(),
        )
        tick()
        assertEquals(listOf(PLAYER_LIST), steve.received().map(::idOf))
    }

    @Test
    fun `as the server stops, nobody hears that the others left, as everyone's leaving`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.received()

        alex.session.disconnect(Disconnect.Reason.SHUTDOWN, "Server closed")
        assertTrue(steve.received().isEmpty())
        assertFalse(alex.session.isInGame)
    }

    @Test
    fun `chat goes to everyone in the game, under the name the player logged in with`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.received()

        alex.sends(Text(Text.CHAT, "  hello  ", sourceName = "Notch").encode())

        // Alex sees it too: the client only shows its own messages once the server sends them back.
        val expected = Text(Text.CHAT, "hello", sourceName = "Alex", xuid = "2222").encode()
        assertContentEquals(expected, steve.received().single())
        assertContentEquals(expected, alex.received().single())

        alex.sends(Text(Text.CHAT, "   ").encode())
        assertTrue(steve.received().isEmpty())
    }

    private fun joined(name: String) = Text(Text.TRANSLATION, "\u00a7e%multiplayer.player.joined", parameters = listOf(name), needsTranslation = true).encode()

    @Test
    fun `the sounds a player makes, like the block they're mining being hit, reach everyone around, them too`() {
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.received()
        alex.received()
        val near = Vec3(world.spawn.x + 1.5f, world.spawn.y - 0.5f, world.spawn.z + 0.5f)

        alex.sends(LevelSoundEvent("hit", near, 0x1234, entityType = "", entityId = 2).encode())

        val heard = LevelSoundEvent("hit", near, 0x1234, entityType = "", entityId = 2).encode()
        assertContentEquals(heard, steve.received().single())
        assertContentEquals(heard, alex.received().single())

        // One far beyond what the player reaches goes nowhere.
        alex.sends(LevelSoundEvent("hit", Vec3(near.x + 100f, near.y, near.z), 0x1234).encode())
        assertTrue(steve.received().isEmpty())
    }

    private fun idOf(packet: ByteArray) = Unpooled.wrappedBuffer(packet).readVarUInt() and 0x3FF

    /** Where an AddPlayer puts its player: after the ID, UUID, name, runtime ID and platform chat ID. */
    private fun positionIn(addPlayer: ByteArray): Vec3 {
        val buffer = Unpooled.wrappedBuffer(addPlayer)
        buffer.readVarUInt()
        buffer.skipBytes(16)
        buffer.readString()
        buffer.readVarULong()
        buffer.readString()
        return buffer.readVec3()
    }
}
