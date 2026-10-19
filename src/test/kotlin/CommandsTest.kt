package org.koremc

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.cloudburstmc.nbt.NbtMap
import org.koremc.auth.MultiplayerTokenVerifier
import org.koremc.auth.TestAuthority
import org.koremc.auth.ecKeyPair
import org.koremc.command.Commands
import org.koremc.command.ConsoleSource
import org.koremc.command.Operators
import org.koremc.command.english
import org.koremc.protocol.BatchCodec
import org.koremc.protocol.BlockDefinitions
import org.koremc.protocol.CommandMessage
import org.koremc.protocol.Disconnect
import org.koremc.protocol.Difficulty
import org.koremc.protocol.GameMode
import org.koremc.protocol.GameRulesChanged
import org.koremc.protocol.LevelEvent
import org.koremc.protocol.MobEffect
import org.koremc.protocol.MovePlayer
import org.koremc.protocol.PlaySound
import org.koremc.protocol.SetDifficulty
import org.koremc.protocol.SetSpawnPosition
import org.koremc.protocol.SetTitle
import org.koremc.protocol.SpawnParticleEffect
import org.koremc.protocol.StopSound
import org.koremc.protocol.SetTime
import org.koremc.protocol.SetPlayerGameType
import org.koremc.protocol.Text
import org.koremc.protocol.UpdatePlayerGameType
import org.koremc.protocol.clientDataToken
import org.koremc.protocol.connectionRequest
import org.koremc.protocol.encode
import org.koremc.protocol.loginPacket
import org.koremc.protocol.readList
import org.koremc.protocol.readString
import org.koremc.protocol.readVarUInt
import org.koremc.protocol.tokenAuthentication
import org.koremc.protocol.writeString
import org.koremc.protocol.writeUuid
import org.koremc.protocol.writeVarInt
import org.koremc.protocol.writeVarUInt
import org.koremc.protocol.writeVarULong
import org.koremc.registry.AIR
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import org.koremc.registry.CreativeCategory
import org.koremc.registry.CreativeGroup
import org.koremc.registry.CreativeInventory
import org.koremc.registry.CreativeItem
import org.koremc.registry.Cost
import org.koremc.registry.Enchantment
import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.Enchantments
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.ItemVersion
import org.koremc.registry.damage
import org.koremc.registry.enchantments
import org.koremc.world.BlockPos
import org.koremc.world.Drops
import org.koremc.world.FlatWorld
import org.koremc.world.Vec3
import java.security.interfaces.ECPublicKey
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val PROTOCOL = 2193

// Packet IDs, for telling apart what a client got.
private const val DISCONNECT = 5
private const val MOVE_PLAYER = 19
private const val INVENTORY_SLOT = 50
private const val AVAILABLE_COMMANDS = 76
private const val COMMAND_OUTPUT = 79
private const val UPDATE_ABILITIES = 187
private const val UPDATE_BLOCK = 21

/** The input flag set on the tick after the client moved its player where a teleport put them. */
private const val HANDLED_TELEPORT = 37

class CommandsTest {
    private val authority = TestAuthority()
    private val authenticator = LoginAuthenticator(MultiplayerTokenVerifier(keys = { authority.keys }), onlineMode = true)
    private var config = ServerConfig(protocol = PROTOCOL, viewDistance = 8, gameMode = GameMode.SURVIVAL)

    private val stone = BlockState("minecraft:stone", NbtMap.EMPTY, version = 18168865)
    private val palette = BlockPalette(listOf(BlockState(AIR, NbtMap.EMPTY, version = 18168865), stone))
    private val world = FlatWorld(palette, listOf(stone))

    private val diamond = ItemDefinition("minecraft:diamond", 304, componentBased = false, ItemVersion.NONE)
    private val sword = ItemDefinition("minecraft:diamond_sword", 307, componentBased = false, ItemVersion.NONE, maxStackSize = 1, maxDurability = 1561, enchantCategories = setOf("sharp_weapon"))
    private val stoneItem = ItemDefinition("minecraft:stone", 1, componentBased = false, ItemVersion.NONE)
    private val items = listOf(
        ItemDefinition(AIR, -158, componentBased = false, ItemVersion.NONE), diamond, sword, stoneItem,
        ItemDefinition("minecraft:barrier", -161, componentBased = false, ItemVersion.NONE),
        ItemDefinition("minecraft:wall_sign", -192, componentBased = false, ItemVersion.NONE),
    )
    private val creative = CreativeInventory(
        listOf(CreativeGroup(CreativeCategory.CONSTRUCTION, "", icon = null)),
        listOf(CreativeItem(ItemStack(stoneItem, block = stone), group = 0), CreativeItem(ItemStack(diamond), group = 0), CreativeItem(ItemStack(sword), group = 0)),
    )
    private val enchantments = Enchantments(
        listOf(
            Enchantment(9, "sharpness", 5, Cost(11, -10), Cost(11, 10), 10, treasure = false, curse = false, setOf("smite"), { "sharp_weapon" in it.enchantCategories }),
            Enchantment(10, "smite", 5, Cost(8, -3), Cost(8, 17), 5, treasure = false, curse = false, setOf("sharpness"), { "sharp_weapon" in it.enchantCategories }),
        ),
    )
    private val game = GameData(
        world, emptyList(), BlockDefinitions(emptyList()), creative, emptyMap(), Drops(items, creative, palette), ItemStacks(items, creative, palette),
        enchantments = enchantments,
    )

    private val permissions = createTempDirectory("kore-commands").resolve("permissions.json")
    private val operators = Operators(permissions)
    private var stopped = false
    private val commands by lazy { Commands(config, game, { clients.map { it.session } }, operators, shutDown = { stopped = true }) }

    private val clients = mutableListOf<Client>()

    /** One player's client, with its own session on the shared server. */
    private inner class Client(val entityId: Long, val name: String, val xuid: String) {
        private val key = ecKeyPair()
        private val batches = BatchCodec()
        private val sent = mutableListOf<ByteArray>()
        val session: Session = Session(
            config, game, networkId = "network-$entityId", entityId, key.public as ECPublicKey, authenticator,
            viewers = { clients.map { it.session } },
            schedule = { _, _ -> },
            commands = commands,
        ) { sent += it }

        init {
            clients += this
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(193).writeInt(PROTOCOL)))
            batches.enableCompression(256)
            val token = authority.multiplayerToken(key.public as ECPublicKey, xuid = xuid, displayName = name)
            sends(loginPacket(PROTOCOL, connectionRequest(tokenAuthentication(token), clientDataToken(key.private))))
            sends(packResponse(2, "downloadingfinished"))
            sends(packResponse(3, "resourcepackstackfinished"))
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(69).writeVarInt(2).writeByte(2)))
            sends(ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(113).writeVarULong(entityId)))
            sent.clear()
        }

        fun sends(packet: ByteArray) = session.receive(batches.encode(listOf(packet)))

        /** Types [command] in the chat. */
        fun runs(command: String) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer().writeVarUInt(77).writeString(command)
                    .writeString("player").writeUuid(ORIGIN).writeString("").writeLongLE(0) // Where it came from
                    .writeBoolean(false).writeString("52"), // Not internal, and the command version
            ),
        )

        /** Every packet the client got since the last look. */
        fun received(): List<ByteArray> = sent.flatMap { batches.decode(it) }.also { sent.clear() }

        /** What the last command the player ran came to, from the packets they got since the last look. */
        fun output(): List<CommandMessage> = outputOf(received())

        /** Moves the player's eyes to [position], with the input flags numbered in [inputs]. */
        fun moves(position: Vec3, inputs: List<Int> = emptyList()) = sends(
            ByteBufUtil.getBytes(
                Unpooled.buffer()
                    .writeVarUInt(144)
                    .writeFloatLE(0f).writeFloatLE(0f).writeFloatLE(position.x).writeFloatLE(position.y).writeFloatLE(position.z)
                    .writeZero(8).writeFloatLE(0f)
                    .writeVarUInt(inputs.size).apply { inputs.forEach { writeVarInt(it) } }
                    .writeVarUInt(1).writeVarUInt(0).writeVarInt(1).writeZero(8)
                    .writeVarULong(100).writeZero(12).writeZero(5).writeZero(28),
            ),
        )

        private fun packResponse(tag: Int, name: String) =
            ByteBufUtil.getBytes(Unpooled.buffer().writeVarUInt(8).writeVarUInt(tag).writeString(name))
    }

    private fun steveAndAlex(steveIsOperator: Boolean = true): Pair<Client, Client> {
        if (steveIsOperator) operators.add("Steve", "1111")
        val steve = Client(1, "Steve", "1111")
        val alex = Client(2, "Alex", "2222")
        steve.received()
        return steve to alex
    }

    @Test
    fun `an operator changes their own game mode, and everyone hears of it`() {
        val (steve, alex) = steveAndAlex()

        steve.runs("/gamemode c")
        val received = steve.received()
        assertEquals(listOf(CommandMessage("commands.gamemode.success.self", true, listOf("%gameMode.creative"))), outputOf(received))
        assertTrue(received.any { it.contentEquals(SetPlayerGameType(GameMode.CREATIVE).encode()) })
        assertTrue(received.any { idOf(it) == UPDATE_ABILITIES })
        assertEquals(GameMode.CREATIVE, steve.session.gameMode)
        assertTrue(alex.received().any { it.contentEquals(UpdatePlayerGameType(GameMode.CREATIVE, 1).encode()) })
    }

    @Test
    fun `a game mode by number, and for someone else, who hears theirs changed`() {
        val (steve, alex) = steveAndAlex()

        steve.runs("/gamemode 1 Alex")
        assertEquals(listOf(CommandMessage("commands.gamemode.success.other", true, listOf("%gameMode.creative", "Alex"))), steve.output())
        val heard = Text(Text.TRANSLATION, "%gameMode.changed", parameters = listOf("%gameMode.creative"), needsTranslation = true).encode()
        assertTrue(alex.received().any { it.contentEquals(heard) })
        assertEquals(GameMode.CREATIVE, alex.session.gameMode)

        steve.runs("/gamemode 3")
        assertEquals(listOf(CommandMessage("commands.gamemode.fail.invalid", false, listOf("3"))), steve.output())
        // The default is the world's.
        steve.runs("/gamemode d @a")
        assertEquals(GameMode.SURVIVAL, alex.session.gameMode)
    }

    @Test
    fun `a player who isn't an operator can't run an operator's command, as though it didn't exist`() {
        val (_, alex) = steveAndAlex()

        alex.runs("/gamemode c")
        assertEquals(listOf(CommandMessage("commands.generic.unknown", false, listOf("gamemode"))), alex.output())
        assertEquals(GameMode.SURVIVAL, alex.session.gameMode)
    }

    @Test
    fun `without cheats, players can't run cheats, but the console can`() {
        config = config.copy(allowCheats = false)
        val (steve, _) = steveAndAlex()

        steve.runs("/gamemode c")
        assertEquals(listOf(CommandMessage("commands.generic.disabled", false)), steve.output())
        commands.run(ConsoleSource(Vec3(0f, 0f, 0f)), "gamemode c Steve")
        assertEquals(GameMode.CREATIVE, steve.session.gameMode)
    }

    @Test
    fun `a line that fits no way to write the command is a syntax error, quoting the line around it`() {
        val (steve, _) = steveAndAlex()

        steve.runs("/give @s dimond 5")
        assertEquals(listOf(CommandMessage("commands.generic.syntax", false, listOf("give @s ", "dimond", " 5"))), steve.output())
        steve.runs("/gamemode")
        assertEquals(listOf(CommandMessage("commands.generic.syntax", false, listOf("gamemode", "", ""))), steve.output())
    }

    @Test
    fun `give fills a slot at a time, as many as the item stacks to, and drops what doesn't fit`() {
        val (steve, _) = steveAndAlex()

        steve.runs("/give @s diamond_sword 3")
        assertEquals(listOf(CommandMessage("commands.give.success", true, listOf("Diamond Sword", "3", "Steve"))), outputOf(steve.received()))
        steve.runs("/give Steve minecraft:diamond 2300")
        val slots = steve.received().count { idOf(it) == INVENTORY_SLOT }
        assertEquals(33, slots) // The 33 slots the swords left fill with 2112 diamonds, and the other 188 drop
        assertEquals(listOf(60, 64, 64), world.items.map { it.stack.count }.sorted())
    }

    @Test
    fun `give's data is how worn an item that wears is`() {
        val (steve, _) = steveAndAlex()

        steve.runs("/give @s diamond_sword 1 1500")
        val given = steve.session.heldStack!!
        assertEquals(1500, given.damage)
        assertEquals(0, given.aux)
    }

    @Test
    fun `clear takes items, all of them or some, and counts them when asked to take none`() {
        val (steve, _) = steveAndAlex()
        steve.runs("/give @s diamond 10")
        steve.runs("/give @s diamond_sword")
        steve.received()

        steve.runs("/clear @s diamond -1 0")
        assertEquals(listOf(CommandMessage("commands.clear.testing", true, listOf("Steve", "10"))), steve.output())
        steve.runs("/clear @s diamond -1 4")
        assertEquals(listOf(CommandMessage("commands.clear.success", true, listOf("Steve", "4"))), steve.output())
        steve.runs("/clear")
        assertEquals(listOf(CommandMessage("commands.clear.success", true, listOf("Steve", "7"))), steve.output())
        steve.runs("/clear")
        assertEquals(listOf(CommandMessage("commands.clear.failure.no.items", false, listOf("Steve"))), steve.output())
    }

    @Test
    fun `teleporting moves the player there, and their view follows, believing the client once it's caught up`() {
        val (steve, _) = steveAndAlex()
        val before = steve.session.position

        steve.runs("/tp 100 -60 100")
        val received = steve.received()
        val eyes = Vec3(100.5f, -60 + 1.62f, 100.5f)
        assertTrue(received.any { it.contentEquals(MovePlayer(1, eyes, 0f, 0f, 0f, onGround = false, teleport = true).encode()) })
        assertTrue(received.count { idOf(it) == 58 } > 0) // The chunks around where they are now
        assertEquals(listOf(CommandMessage("commands.tp.success.coordinates", true, listOf("Steve", "100.50", "-60.00", "100.50"))), outputOf(received))

        // Input sent before the client heard still has the player where they were.
        steve.moves(before)
        assertEquals(eyes, steve.session.position)
        val landed = Vec3(100.5f, -61 + 1.62f, 100.5f)
        steve.moves(landed, inputs = listOf(HANDLED_TELEPORT))
        assertEquals(landed, steve.session.position)
    }

    @Test
    fun `time sets, adds to and tells the time, and everyone's clock follows`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/time set noon")
        assertEquals(listOf(CommandMessage("commands.time.set", true, listOf("6000"))), steve.output())
        assertTrue(alex.received().any { it.contentEquals(SetTime(6000).encode()) })

        world.time += 2 * 24000
        steve.runs("/time add 500")
        assertEquals(listOf(CommandMessage("commands.time.added", true, listOf("500"))), steve.output())
        steve.runs("/time query daytime")
        assertEquals(listOf(CommandMessage("commands.time.query.daytime", true, listOf("6500"))), steve.output())
        steve.runs("/time query day")
        assertEquals(listOf(CommandMessage("commands.time.query.day", true, listOf("2"))), steve.output())
    }

    @Test
    fun `weather sets the weather for a while, thunder bringing rain, and says what it is`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/weather thunder 200")
        assertEquals(listOf(CommandMessage("commands.weather.thunder", true)), steve.output())
        assertTrue(world.weather.raining && world.weather.thundering)
        assertEquals(200, world.weather.rainTime)
        assertTrue(alex.received().any { it.contentEquals(LevelEvent(LevelEvent.START_THUNDER, Vec3(0f, 0f, 0f), 65535).encode()) })

        steve.runs("/weather query")
        assertEquals(listOf(CommandMessage("commands.weather.query", true, listOf("%commands.weather.query.thunder"))), steve.output())
        steve.runs("/weather clear 2000000")
        assertEquals(listOf(CommandMessage("commands.generic.num.tooBig", false, listOf("2000000", "1000000"))), steve.output())
        steve.runs("/toggledownfall")
        assertEquals(listOf(CommandMessage("commands.downfall.success", true)), steve.output())
        assertFalse(world.weather.raining || world.weather.thundering)
    }

    @Test
    fun `gamerule sets a rule, on or off or a number, which every client hears of, or says what it is`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/gamerule dodaylightcycle false")
        assertEquals(listOf(CommandMessage("commands.gamerule.success", true, listOf("dodaylightcycle", "false"))), steve.output())
        assertFalse(world.rules.doDaylightCycle)
        assertTrue(alex.received().any { it.contentEquals(GameRulesChanged(mapOf("dodaylightcycle" to false)).encode()) })

        steve.runs("/gamerule showCoordinates true")
        assertEquals(listOf(CommandMessage("commands.gamerule.success", true, listOf("showcoordinates", "true"))), steve.output()) // As commands write it
        steve.runs("/gamerule showcoordinates")
        assertEquals(listOf(CommandMessage("showcoordinates = true", true)), steve.output())

        steve.runs("/gamerule randomtickspeed 3")
        assertEquals(listOf(CommandMessage("commands.gamerule.success", true, listOf("randomtickspeed", "3"))), steve.output())
        assertEquals(3, world.rules.randomTickSpeed)

        // Rules that do nothing in Kore yet aren't offered, though the world keeps them.
        steve.runs("/gamerule functioncommandlimit 5")
        assertEquals("commands.generic.syntax", steve.output().single().message)
        assertEquals(10000, world.rules["functionCommandLimit"])
    }

    @Test
    fun `daylock stops the day at an hour before noon, and lets it go again`() {
        val (steve, _) = steveAndAlex()
        world.time = 24000 + 15000

        steve.runs("/daylock")
        assertEquals(listOf(CommandMessage("commands.always.day.locked", true)), steve.output())
        assertEquals(24000L + 5000, world.time)
        assertFalse(world.rules.doDaylightCycle)

        steve.runs("/alwaysday false")
        assertEquals(listOf(CommandMessage("commands.always.day.unlocked", true)), steve.output())
        assertTrue(world.rules.doDaylightCycle)
    }

    @Test
    fun `players teleport to other players, and the ones a command names, relative to where it runs`() {
        val (steve, alex) = steveAndAlex()
        alex.moves(Vec3(20.5f, -63 + 1.62f, 3.5f))

        steve.runs("/tp Alex")
        assertEquals(listOf(CommandMessage("commands.tp.success", true, listOf("Steve", "Alex"))), steve.output())
        assertNear(alex.session.feet, steve.session.feet)

        steve.runs("/tp Alex ~ ~10 ~-3")
        assertNear(Vec3(20.5f, -53f, 0.5f), alex.session.feet)
        steve.runs("/tp @a[name=!Steve] 0 -50 0 checkForBlocks")
        assertEquals(listOf(CommandMessage("commands.generic.syntax", false, listOf("] 0 -50 0 ", "checkForBlocks", ""))), steve.output())
        // Stone fills the layer under the spawn, so a player doesn't fit there.
        steve.runs("/tp Alex 0 -64 0 true")
        assertEquals(listOf(CommandMessage("commands.tp.safeTeleportFail", false, listOf("Alex", "0.50, -64.00, 0.50"))), steve.output())
    }

    @Test
    fun `op makes a player an operator, who gets an operator's commands, and the server remembers`() {
        val (steve, alex) = steveAndAlex()

        steve.runs("/op Alex")
        assertEquals(listOf(CommandMessage("commands.op.success", true, listOf("Alex"))), steve.output())
        val received = alex.received()
        assertTrue(received.any { idOf(it) == UPDATE_ABILITIES })
        assertTrue(received.any { idOf(it) == AVAILABLE_COMMANDS })
        assertTrue(alex.session.isOperator)
        assertTrue("\"name\": \"Alex\",\n        \"permission\": \"operator\",\n        \"xuid\": \"2222\"" in permissions.readText(), "name, permission, then XUID")
        assertTrue(Operators(permissions).contains("Alex", "2222"))

        steve.runs("/op Alex")
        assertEquals(listOf(CommandMessage("commands.op.failed", false, listOf("Alex"))), steve.output())
        steve.runs("/deop Alex")
        assertEquals(listOf(CommandMessage("commands.deop.success", true, listOf("Alex"))), steve.output())
        assertFalse(alex.session.isOperator)
        assertFalse(Operators(permissions).contains("Alex", "2222"))
    }

    @Test
    fun `an operator's XUID is who they are, so someone else can't take their name`() {
        operators.add("Steve", "1111")

        assertTrue(operators.contains("steve", "1111"))
        assertFalse(operators.contains("Steve", "9999"))
        assertFalse(operators.contains("Steve", null))
    }

    @Test
    fun `kick disconnects a player, with the reason, and everyone hears they left`() {
        val (steve, alex) = steveAndAlex()

        steve.runs("/kick Steve")
        assertEquals(listOf(CommandMessage("commands.kick.not.yourself", false)), steve.output())
        steve.runs("/kick Alex griefing")
        assertEquals(listOf(CommandMessage("commands.kick.success.reason", true, listOf("Alex", "griefing"))), steve.output())
        assertTrue(alex.received().any { it.contentEquals(Disconnect(Disconnect.Reason.KICKED, "griefing").encode()) })
        assertFalse(alex.session.isInGame)
        steve.runs("/kick Nobody")
        assertEquals(listOf(CommandMessage("commands.kick.not.found", false, listOf("Nobody"))), steve.output())
    }

    @Test
    fun `tell whispers to the players it names, and say and me tell everyone, with selectors written out`() {
        val (steve, alex) = steveAndAlex(steveIsOperator = false)

        steve.runs("/w Alex hello there")
        val whisper = Text(Text.TRANSLATION, "§7§o%commands.message.display.incoming", parameters = listOf("Steve", "hello there"), needsTranslation = true, xuid = "1111")
        assertTrue(alex.received().any { it.contentEquals(whisper.encode()) })
        val outgoing = Text(Text.TRANSLATION, "§7§o%commands.message.display.outgoing", parameters = listOf("Alex", "hello there"), needsTranslation = true)
        assertTrue(steve.received().any { it.contentEquals(outgoing.encode()) })
        steve.runs("/tell @s hi")
        assertEquals(listOf(CommandMessage("commands.message.sameTarget", false)), steve.output())

        steve.runs("/me waves at @a")
        val emote = Text(Text.TRANSLATION, "%chat.type.emote", parameters = listOf("Steve", "waves at Steve, Alex"), needsTranslation = true, xuid = "1111")
        assertTrue(alex.received().any { it.contentEquals(emote.encode()) })
        // Only operators may say things for everyone.
        steve.runs("/say hi")
        assertEquals(listOf(CommandMessage("commands.generic.unknown", false, listOf("say"))), steve.output())
    }

    @Test
    fun `list names the players in the game, and help pages through the commands a player may run`() {
        val (_, alex) = steveAndAlex()

        alex.runs("/list")
        assertEquals(
            listOf(CommandMessage("commands.players.list", true, listOf("2", "10")), CommandMessage("commands.players.list.names", true, listOf("Steve, Alex"))),
            alex.output(),
        )
        alex.runs("/help")
        val help = alex.output()
        assertEquals(CommandMessage("§2%commands.help.header", true, listOf("1", "1")), help.first())
        assertEquals(CommandMessage("§2%commands.help.footer", true), help.last())
        assertTrue(CommandMessage("/tell <target: target> <message: message>", true) in help)
        assertTrue(help.none { it.message.startsWith("/gamemode") })
        alex.runs("/? tell")
        assertEquals(
            listOf(
                CommandMessage("§e%commands.help.command.aliases", true, listOf("tell", "w, msg")),
                CommandMessage("§e%commands.tell.description", true),
                CommandMessage("commands.generic.usage.noparam", true),
                CommandMessage("- /tell <target: target> <message: message>", true),
            ),
            alex.output(),
        )
        alex.runs("/help list")
        assertEquals(CommandMessage("§elist:", true), alex.output().first())
    }

    @Test
    fun `a player who becomes an operator gets the commands an operator may run, which complete item names`() {
        val (steve, _) = steveAndAlex()

        val available = commands.map.availableTo(steve.session.permission)
        assertTrue(available.commands.any { it.name == "give" })
        // Items go by their short names, in a list the client doesn't add its own to. Blocks' own items
        // aren't for handing out, but those only commands hand out are.
        val items = available.softEnums.single { it.name == "Item" }.values
        assertEquals(listOf("barrier", "diamond", "diamond_sword", "stone"), items)
        val give = available.commands.single { it.name == "give" }.overloads.single()
        assertEquals(0x4100000, give[1].symbol)
        // The client has a /help of its own.
        assertTrue(available.commands.none { it.name == "help" })
        val teleport = available.commands.single { it.name == "teleport" }
        assertEquals(listOf("teleport", "tp"), available.enums[teleport.aliases].values.map { available.enumValues[it] })
        // The client drops any keyword that isn't in lower case.
        assertEquals(emptyList(), available.enumValues.filter { it != it.lowercase() })
    }

    @Test
    fun `xp gives points, which fill the bar, or levels written with an L, which can take levels away too`() {
        val (steve, alex) = steveAndAlex()

        steve.runs("/xp 10")
        assertEquals(listOf(CommandMessage("commands.xp.success", true, listOf("10", "Steve"))), steve.output())
        assertEquals(1, steve.session.experienceLevel)
        steve.runs("/xp 5L Alex")
        assertEquals(listOf(CommandMessage("commands.xp.success.levels", true, listOf("5", "Alex"))), steve.output())
        steve.runs("/xp -2l Alex")
        assertEquals(listOf(CommandMessage("commands.xp.success.negative.levels", true, listOf("2", "Alex"))), steve.output())
        assertEquals(3, alex.session.experienceLevel)
        steve.runs("/xp -1")
        assertEquals(listOf(CommandMessage("commands.xp.failure.widthdrawXp", false)), steve.output())

        // The client can't take the levels with their L, so it only hears of the points.
        val available = commands.map.availableTo(steve.session.permission)
        assertEquals(listOf(listOf(0x100001, 0x100008)), available.commands.single { it.name == "xp" }.overloads.map { overload -> overload.map { it.symbol } })
    }

    @Test
    fun `enchant enchants what a player holds, at a level the enchantment goes to, alongside what's on it already`() {
        val (steve, alex) = steveAndAlex()
        steve.runs("/give @s diamond_sword")
        steve.received()

        steve.runs("/enchant @s sharpness 3")
        assertEquals(listOf(CommandMessage("commands.enchant.success", true, listOf("Steve"))), steve.output())
        assertEquals(listOf(EnchantmentLevel(9, 3)), steve.session.heldStack?.enchantments)
        steve.runs("/enchant @s 10")
        assertEquals(listOf(CommandMessage("commands.enchant.cantCombine", false, listOf("smite", "sharpness"))), steve.output())
        steve.runs("/enchant @s sharpness 6")
        assertEquals(listOf(CommandMessage("commands.enchant.invalidLevel", false, listOf("sharpness", "6"))), steve.output())
        steve.runs("/enchant @s 99")
        assertEquals(listOf(CommandMessage("commands.enchant.notFound", false, listOf("99"))), steve.output())
        steve.runs("/enchant Alex sharpness")
        assertEquals(listOf(CommandMessage("commands.enchant.noItem", false, listOf("Alex"))), steve.output())
        assertEquals(null, alex.session.heldStack)
    }

    @Test
    fun `stop asks the server to shut down, which disconnects everyone`() {
        steveAndAlex()

        val result = commands.run(ConsoleSource(Vec3(0f, 0f, 0f)), "stop")
        assertEquals(listOf(CommandMessage("commands.stop.start", true)), result.messages)
        assertTrue(stopped)
    }

    @Test
    fun `selectors pick out items, which teleport to a player and can be killed, while nothing kills a creative player`() {
        val (steve, _) = steveAndAlex()
        world.spawnItem(ItemStack(diamond), Vec3(20.5f, -63f, 20.5f), Vec3(0f, 0f, 0f), pickupDelay = 100)

        steve.runs("/tp @e[type=item] @s")
        assertEquals(listOf(CommandMessage("commands.tp.success", true, listOf("Diamond", "Steve"))), steve.output())
        assertEquals(steve.session.feet, world.items.single().position)
        steve.runs("/kill @e[type=item,name=diamond]")
        assertEquals(listOf(CommandMessage("commands.kill.successful", true, listOf("Diamond"))), steve.output())
        assertTrue(world.items.isEmpty())

        steve.runs("/gamemode c")
        steve.runs("/kill")
        assertEquals(listOf(CommandMessage("commands.kill.attemptKillPlayerCreative", false)), steve.output())
    }

    @Test
    fun `effect gives an effect for a while or for ever, takes one or all away, and only players have effects`() {
        val (steve, alex) = steveAndAlex()

        steve.runs("/effect Alex speed 10 2")
        assertEquals(listOf(CommandMessage("commands.effect.success", true, listOf("%potion.moveSpeed", "2", "Alex", "10"))), steve.output())
        assertTrue(alex.received().any { it.contentEquals(MobEffect(2, MobEffect.ADD, 1, 2, 200).encode()) })
        steve.runs("/effect @s night_vision infinite 0 true")
        assertEquals(listOf(CommandMessage("commands.effect.success.infinite", true, listOf("%potion.nightVision", "0", "Steve"))), steve.output())

        steve.runs("/effect Alex clear")
        assertEquals(listOf(CommandMessage("commands.effect.success.removed.all", true, listOf("Alex"))), steve.output())
        steve.runs("/effect Alex clear speed")
        assertEquals(listOf(CommandMessage("commands.effect.failure.notActive", false, listOf("%potion.moveSpeed", "Alex"))), steve.output())
        world.spawnItem(ItemStack(diamond), Vec3(0.5f, -63f, 0.5f), Vec3(0f, 0f, 0f), pickupDelay = 100)
        steve.runs("/effect @e[type=item] speed")
        assertEquals(listOf(CommandMessage("commands.effect.failure.notAMob", false, listOf("Diamond"))), steve.output())
    }

    @Test
    fun `difficulty sets the world's difficulty by name, letter or number, which everyone hears of`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/difficulty h")
        assertEquals(listOf(CommandMessage("commands.difficulty.success", true, listOf("%options.difficulty.hard"))), steve.output())
        assertEquals(Difficulty.HARD, world.difficulty)
        assertTrue(alex.received().any { it.contentEquals(SetDifficulty(Difficulty.HARD).encode()) })
        steve.runs("/difficulty 0")
        assertEquals(Difficulty.PEACEFUL, world.difficulty)
        steve.runs("/difficulty 7")
        assertEquals(listOf(CommandMessage("commands.generic.parameter.invalid", false, listOf("7"))), steve.output())
    }

    @Test
    fun `setworldspawn moves the world's spawn, and spawnpoint sets where players respawn, which clearspawnpoint takes away`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/setworldspawn 5 -60 5")
        assertEquals(listOf(CommandMessage("commands.setworldspawn.success", true, listOf("5", "-60", "5"))), steve.output())
        assertEquals(BlockPos(5, -60, 5), world.spawn)
        assertTrue(alex.received().any { it.contentEquals(SetSpawnPosition(SetSpawnPosition.WORLD, BlockPos(5, -60, 5)).encode()) })

        steve.runs("/spawnpoint Alex 10 -62 10")
        assertEquals(listOf(CommandMessage("commands.spawnpoint.success.single", true, listOf("Alex", "10", "-62", "10"))), steve.output())
        assertEquals(BlockPos(10, -62, 10), alex.session.spawnPoint)
        steve.runs("/spawnpoint @a")
        assertEquals(listOf(CommandMessage("commands.spawnpoint.success.multiple.generic", true, listOf("Steve, Alex"))), steve.output())
        steve.runs("/clearspawnpoint Alex")
        assertEquals(listOf(CommandMessage("commands.clearspawnpoint.success.single", true, listOf("Alex"))), steve.output())
        assertEquals(null, alex.session.spawnPoint)
    }

    @Test
    fun `title shows a title, and tellraw and titleraw send rawtext, with its selectors written out as names`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/title Alex title Hello there")
        assertEquals(listOf(CommandMessage("commands.title.success", true)), steve.output())
        assertTrue(alex.received().any { it.contentEquals(SetTitle(SetTitle.TITLE, "Hello there").encode()) })
        steve.runs("""/tellraw @a {"rawtext":[{"text":"Hi "},{"selector":"@a"}]}""")
        assertTrue(alex.received().any { it.contentEquals(Text(Text.OBJECT, """{"rawtext":[{"text":"Hi "},{"text":"Steve, Alex"}]}""").encode()) })
        steve.runs("""/titleraw Alex actionbar {"rawtext":[{"translate":"gui.ok"}]}""")
        assertTrue(alex.received().any { it.contentEquals(SetTitle(SetTitle.ACTION_BAR + SetTitle.RAW, """{"rawtext":[{"translate":"gui.ok"}]}""").encode()) })

        steve.runs("""/tellraw @a {"rawtext":{"text":"no"}}""")
        assertEquals(listOf(CommandMessage("commands.tellraw.error.notArray", false)), steve.output())
        steve.runs("/tellraw @a {not json")
        assertEquals(listOf(CommandMessage("commands.tellraw.jsonStringException", false)), steve.output())
    }

    @Test
    fun `playsound plays to the players in range, stopsound stops it, and particle shows an effect`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/playsound random.levelup Alex")
        assertEquals(listOf(CommandMessage("commands.playsound.success", true, listOf("random.levelup", "Alex"))), steve.output())
        assertTrue(alex.received().any { it.contentEquals(PlaySound("random.levelup", alex.session.feet, 1f, 1f).encode()) })
        steve.runs("/playsound random.levelup Alex 100 -60 100")
        assertEquals(listOf(CommandMessage("commands.playsound.playerTooFar", false, listOf("Alex"))), steve.output())
        steve.runs("/stopsound Alex")
        assertEquals(listOf(CommandMessage("commands.stopsound.success.all", true)), steve.output())
        assertTrue(alex.received().any { it.contentEquals(StopSound(null).encode()) })

        steve.runs("/particle minecraft:heart_particle 0 -62 0")
        assertEquals(listOf(CommandMessage("commands.particle.success", true, listOf("minecraft:heart_particle", "1"))), steve.output())
        assertTrue(alex.received().any { it.contentEquals(SpawnParticleEffect("minecraft:heart_particle", Vec3(0.5f, -62f, 0.5f)).encode()) })
    }

    @Test
    fun `setblock and fill change blocks for everyone to see, within their limits, and testfor and testforblocks look`() {
        val (steve, alex) = steveAndAlex()
        alex.received()

        steve.runs("/setblock 0 -60 0 stone")
        assertEquals(listOf(CommandMessage("commands.setblock.success", true)), steve.output())
        assertEquals(stone, world.stateAt(BlockPos(0, -60, 0)))
        assertTrue(alex.received().any { idOf(it) == UPDATE_BLOCK })
        steve.runs("/setblock 0 -60 0 stone")
        assertEquals(listOf(CommandMessage("commands.setblock.noChange", false)), steve.output())
        steve.runs("""/setblock 0 -60 0 stone ["no"=1]""")
        assertEquals(listOf(CommandMessage("commands.blockstate.stateError", false, listOf("no", "stone"))), steve.output())
        steve.runs("/fill 0 -60 0 2 -60 2 stone keep")
        assertEquals(listOf(CommandMessage("commands.fill.success", true, listOf("8"))), steve.output())
        steve.runs("/fill 0 -60 0 100 -50 100 stone")
        assertEquals(listOf(CommandMessage("commands.fill.tooManyBlocks", false, listOf("112211", "32768"))), steve.output())

        steve.runs("/testfor @a")
        assertEquals(listOf(CommandMessage("commands.testfor.success", true, listOf("Steve, Alex"))), steve.output())
        steve.runs("/testforblocks 0 -60 0 3 -60 0 10 -60 0")
        assertEquals(listOf(CommandMessage("commands.compare.failed", false)), steve.output())
        steve.runs("/testforblocks 0 -60 0 3 -60 0 10 -60 0 masked")
        assertEquals(listOf(CommandMessage("commands.compare.failed", false)), steve.output())
        steve.runs("/clone 0 -60 0 3 -60 0 10 -60 0")
        steve.runs("/testforblocks 0 -60 0 3 -60 0 10 -60 0")
        assertEquals(listOf(CommandMessage("commands.compare.success", true, listOf("4"))), steve.output())
        steve.runs("/testforblocks 0 -60 0 3 -60 0 10 -60 0 masked")
        assertEquals(listOf(CommandMessage("commands.compare.success", true, listOf("3"))), steve.output())
    }

    @Test
    fun `setmaxplayers sets how many can be on at once, though never fewer than are`() {
        val (steve, _) = steveAndAlex()

        steve.runs("/setmaxplayers 1")
        assertEquals(listOf(CommandMessage("commands.setmaxplayers.success", true, listOf("2")), CommandMessage("commands.setmaxplayers.success.lowerbound", true)), steve.output())
        assertEquals(2, game.maxPlayers)
        steve.runs("/setmaxplayers 10")
        assertEquals(10, game.maxPlayers)
    }

    @Test
    fun `the console shows output in English, with the keys in it filled in`() {
        assertEquals(
            "Set Steve's game mode to Creative Mode",
            english(CommandMessage("commands.gamemode.success.other", true, listOf("%gameMode.creative", "Steve"))),
        )
        assertEquals("Gave Diamond * 3 to Steve", english(CommandMessage("commands.give.success", true, listOf("Diamond", "3", "Steve"))))
        assertEquals("/list", english(CommandMessage("/list", true)))
        assertEquals("§2--- Showing help page 1 of 4 (/help <page>) ---", english(CommandMessage("§2%commands.help.header", true, listOf("1", "4"))))
        assertEquals("§eSets a player's game mode.", english(CommandMessage("§e%commands.gamemode.description", true)))
        assertEquals("Gave Speed * 2 to Alex for 10 seconds", english(CommandMessage("commands.effect.success", true, listOf("%potion.moveSpeed", "2", "Alex", "10"))))
    }

    private fun idOf(packet: ByteArray) = Unpooled.wrappedBuffer(packet).readVarUInt() and 0x3FF

    /** Points a hair apart are the same: a player's eyes and feet are 1.62 apart, which a float doesn't hold exactly. */
    private fun assertNear(expected: Vec3, actual: Vec3) =
        assertTrue(listOf(expected.x - actual.x, expected.y - actual.y, expected.z - actual.z).all { kotlin.math.abs(it) < 0.001f }, "$actual isn't $expected")

    private fun outputOf(packets: List<ByteArray>): List<CommandMessage> {
        val buffer = Unpooled.wrappedBuffer(assertNotNull(packets.lastOrNull { idOf(it) == COMMAND_OUTPUT }, "a command output"))
        buffer.readVarUInt()
        assertEquals("player", buffer.readString())
        assertEquals(ORIGIN, UUID(buffer.readLongLE(), buffer.readLongLE()))
        buffer.readString() // Request ID
        buffer.readLongLE() // Player ID
        assertEquals("alloutput", buffer.readString())
        buffer.readIntLE() // How many times it worked
        return buffer.readList { readMessage() }.also { buffer.readBoolean() }
    }

    private fun ByteBuf.readMessage() = CommandMessage(readString(), readBoolean(), readList { readString() })

    private companion object {
        /** The UUID the client gives every command it sends in these tests. */
        val ORIGIN = UUID(1, 2)
    }
}
