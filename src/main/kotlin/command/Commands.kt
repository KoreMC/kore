package org.koremc.command

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koremc.GameData
import org.koremc.ServerConfig
import org.koremc.Session
import org.koremc.dropBroken
import org.koremc.packetsFor
import org.koremc.protocol.CommandPermission.ADMIN
import org.koremc.protocol.CommandPermission.ANY
import org.koremc.protocol.CommandPermission.GAME_DIRECTORS
import org.koremc.protocol.CommandPermission.OWNER
import org.koremc.protocol.Difficulty
import org.koremc.protocol.GameMode
import org.koremc.protocol.PlaySound
import org.koremc.protocol.SetDifficulty
import org.koremc.protocol.SetSpawnPosition
import org.koremc.protocol.SetTitle
import org.koremc.protocol.SpawnParticleEffect
import org.koremc.protocol.StopSound
import org.koremc.protocol.Text
import org.koremc.registry.AIR
import org.koremc.registry.BOOK
import org.koremc.registry.BlockState
import org.koremc.registry.Enchantment
import org.koremc.registry.EnchantmentLevel
import org.koremc.registry.enchantments
import org.koremc.registry.withEnchantments
import org.koremc.showAround
import org.koremc.world.BlockBox
import org.koremc.world.DAY_LENGTH
import org.koremc.world.EFFECT_KINDS
import org.koremc.world.Edited
import org.koremc.world.EffectLevel
import org.koremc.world.FALLING_BLOCK_SIZE
import org.koremc.world.FillMode
import org.koremc.world.GAME_RULES_IN_USE
import org.koremc.world.GAME_RULE_DEFAULTS
import org.koremc.world.INFINITE_EFFECT
import org.koremc.world.ITEM_SIZE
import org.koremc.world.MAX_Y
import org.koremc.world.MIN_Y
import org.koremc.world.PLAYER_EYE_HEIGHT
import org.koremc.world.Vec3
import org.koremc.world.clone
import org.koremc.world.compare
import org.koremc.world.fill
import org.koremc.world.isClear
import org.koremc.world.setWeather
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** The most of an item `/give` hands out at once. */
private const val MAX_GIVE = 32767

/** The highest aux value an item can have. */
private const val MAX_DATA = 32767

/** How many lines of usage each page of `/help` shows. */
private const val HELP_PAGE_LINES = 7

/** How far from the middle of the world anything can go, across. */
private const val WORLD_EDGE = 30_000_000f

/** The times of day `/time set` takes by name, in ticks into the day, which starts at sunrise. */
private val TIMES_OF_DAY = mapOf("day" to 1000, "noon" to 6000, "sunset" to 12000, "night" to 13000, "midnight" to 18000, "sunrise" to 23000)

/** The longest `/weather` sets the weather for, in ticks. */
private const val MAX_WEATHER_TICKS = 1_000_000

/** The game rule for day turning to night, and the time of day `/daylock` locks the day at: an hour before noon. */
private const val DAYLIGHT_CYCLE = "dodaylightcycle"
private const val LOCKED_TIME = 5000

/** The game's names for the kinds of entity commands pick out besides players, and what those that aren't items are called. */
private const val ITEM_TYPE = "minecraft:item"
private const val ORB_TYPE = "minecraft:xp_orb"
private const val ORB_NAME = "Experience Orb"
private const val FALLING_BLOCK_TYPE = "minecraft:falling_block"
private const val FALLING_BLOCK_NAME = "Falling Block"

/** How far up something other than a player its eyes are, as a share of its height. */
private const val EYES_UP = 0.85f

/** How long `/effect` gives an effect for if it doesn't say, the most it gives one for, in seconds, and the strongest it gives one at. */
private const val DEFAULT_EFFECT_SECONDS = 30
private const val MAX_EFFECT_SECONDS = 1_000_000
private const val MAX_AMPLIFIER = 255

private const val TICKS_PER_SECOND = 20

/** How far a sound at full volume carries, in blocks, a louder one further, and how far beside a player one too far away for them comes from. */
private const val SOUND_RANGE = 16f
private const val SOUND_BESIDE = 2f

/**
 * Items the creative inventory leaves out that commands still hand out:
 * command and structure blocks, barriers and light blocks, the bosses'
 * spawn eggs, the petrified oak slab and the like.
 */
private val COMMAND_ONLY_ITEMS = listOf(
    "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart", "structure_block",
    "structure_void", "jigsaw", "barrier", "border_block", "allow", "deny", "frosted_ice", "ender_dragon_spawn_egg",
    "wither_spawn_egg", "filled_map", "lodestone_compass", "petrified_oak_slab",
) + (0..15).map { "light_block_$it" }

/**
 * The game's commands that Kore has, as the game writes them, with what
 * each does. Players run them from the chat, and the console as the
 * server's owner. The commands an operator may run that a player may not,
 * and whose they are, are in [operators]; [shutDown] stops the server.
 */
class Commands(
    private val config: ServerConfig,
    private val game: GameData,
    /** Every player's session. */
    private val sessions: () -> Collection<Session>,
    val operators: Operators = Operators(),
    private val shutDown: () -> Unit = {},
) {
    private val world get() = game.world

    /** The players in the game. */
    private val players get() = sessions().filter { it.isInGame }

    private val gameModes = EnumType("GameMode", listOf("default", "creative", "spectator", "survival", "adventure", "d", "c", "s", "a"))
    private val booleans = EnumType("Boolean", listOf("true", "false"))
    private val facing = EnumType("TeleportFacing", listOf("facing"))
    private val timeSet = EnumType("TimeSet", listOf("set"))
    private val query = EnumType("Query", listOf("query"))
    private val timesOfDay = EnumType("TimeSpec", TIMES_OF_DAY.keys.toList())
    private val timeQueries = EnumType("TimeQuery", listOf("daytime", "gametime", "day"))
    private val weathers = EnumType("WeatherType", listOf("clear", "rain", "thunder"))
    private val setBlockModes = EnumType("SetBlockMode", listOf("destroy", "keep", "replace"))
    private val fillModes = EnumType("FillMode", listOf("destroy", "hollow", "keep", "outline", "replace"))
    private val replaceMode = EnumType("ReplaceMode", listOf("replace"))
    private val maskModes = EnumType("MaskMode", listOf("masked", "replace"))
    private val filteredMode = EnumType("FilteredMode", listOf("filtered"))
    private val cloneModes = EnumType("CloneMode", listOf("force", "move", "normal"))

    /** The blocks, which the client completes by their short names, as it does items. */
    private val blockNames = SoftEnumType("Block", world.blocks.states.map { it.name.removePrefix("minecraft:") }.distinct().sorted(), namespace = "minecraft")

    private val effectNames = EnumType("Effect", EFFECT_KINDS.map { it.name })
    private val clearWord = EnumType("ClearEffects", listOf("clear"))
    private val infiniteWord = EnumType("InfiniteDuration", listOf("infinite"))
    private val difficulties = EnumType("Difficulty", listOf("peaceful", "easy", "normal", "hard", "p", "e", "n", "h"))
    private val titleClear = EnumType("TitleClear", listOf("clear"))
    private val titleReset = EnumType("TitleReset", listOf("reset"))
    private val titleSet = EnumType("TitleSet", listOf("title", "subtitle", "actionbar"))
    private val titleTimes = EnumType("TitleTimes", listOf("times"))
    private val compareModes = EnumType("TestForBlocksMode", listOf("all", "masked"))

    /**
     * The game rules `/gamerule` offers, those that do something in Kore,
     * on or off apart from those that are numbers, as commands write them:
     * in lower case, as the client takes no keyword in a command that isn't.
     */
    private val rulesInUse = GAME_RULE_DEFAULTS.filterKeys { it in GAME_RULES_IN_USE }
    private val switchRules = EnumType("BoolGameRule", rulesInUse.filterValues { it is Boolean }.keys.map { it.lowercase() })
    private val numberRules = EnumType("IntGameRule", rulesInUse.filterValues { it is Int }.keys.map { it.lowercase() })

    /** The enchantments, by the names the game's commands call them, which leave the curses' `_curse` off. */
    private val enchantmentNames = game.enchantments.all.associateBy { it.name.removeSuffix("_curse") }
    private val enchants = EnumType("Enchant", enchantmentNames.keys.sorted())

    /**
     * The items commands hand out: those in the creative inventory, and the
     * few only commands do. The rest of the item list is blocks' own items,
     * like a wall sign or a crop, and Education Edition's. The client
     * completes them by their short names, as it does in the game, and
     * takes them with the namespace too.
     */
    private val items = SoftEnumType(
        "Item",
        (game.creative.items.map { it.stack.item.name } + COMMAND_ONLY_ITEMS.map { "minecraft:$it" }.filter { game.items[it] != null })
            .map { it.removePrefix("minecraft:") }.distinct().sorted(),
        namespace = "minecraft",
    )

    val map = CommandMap(vanilla(), config.allowCheats)

    /** Runs [line] for [source]. */
    fun run(source: CommandSource, line: String) = map.run(source, line)

    private fun vanilla(): List<Command> {
        val commands = listOf(
            list(), me(), tell(), tellRaw(), say(), title(), titleRaw(), gameMode(), give(), clear(), teleport(), kill(), effect(), xp(), enchant(),
            time(), weather(), toggleDownfall(), dayLock(), difficulty(), gameRule(), setWorldSpawn(), spawnPoint(), clearSpawnPoint(),
            setBlock(), fill(), clone(), testForBlock(), testForBlocks(), testFor(), playSound(), stopSound(), particle(),
            kick(), op(), deop(), setMaxPlayers(), stop(),
        )
        return commands + help(commands.flatMap { listOf(it.name) + it.aliases } + listOf("help", "?"))
    }

    /**
     * Lists the commands, or how to write one. The client has a `/help` of
     * its own, which lists the commands it's been sent along with its own,
     * like `/gametips`, so players' clients don't hear of this one: sent it,
     * the client loses track of its own commands. The console runs it.
     */
    private fun help(names: List<String>) = command("help", ANY, cheat = false, aliases = listOf("?"), sentToClients = false) {
        overload(optional("command", EnumType("CommandName", names.sorted()))) {
            val name = stringOrNull("command") ?: return@overload helpPage(1)
            val command = map.find(name, source.permission) ?: fail("commands.generic.unknown", name)
            // In yellow, the command, with any other names, and what it does; then each way to write it.
            if (command.aliases.isEmpty()) success("§e${command.name}:") else success("§e%commands.help.command.aliases", command.name, command.aliases.joinToString(", "))
            success("§e%${command.description}")
            success("commands.generic.usage.noparam")
            command.usages.forEach { success("- $it") }
        }
        overload(required("page", IntType)) { helpPage(int("page")) }
    }

    /** A page of the usage of every command the player may run, the last if there aren't that many. */
    private fun CommandCall.helpPage(page: Int) {
        val lines = map.runnableBy(source.permission).flatMap { it.usages }
        val pages = (lines.size + HELP_PAGE_LINES - 1) / HELP_PAGE_LINES
        val shown = page.coerceIn(1, maxOf(pages, 1))
        success("§2%commands.help.header", shown, pages)
        lines.drop((shown - 1) * HELP_PAGE_LINES).take(HELP_PAGE_LINES).forEach { success(it) }
        success("§2%commands.help.footer")
    }

    private fun list() = command("list", ANY, cheat = false) {
        overload {
            val names = players.map { it.name }
            success("commands.players.list", names.size, game.maxPlayers)
            success("commands.players.list.names", names.joinToString(", "))
        }
    }

    private fun me() = command("me", ANY, cheat = false) {
        overload(required("message", MessageType)) {
            tellEveryone(Text(Text.TRANSLATION, "%chat.type.emote", parameters = listOf(source.name, expand(string("message"))), needsTranslation = true, xuid = xuidOf(source)))
            successes++
        }
    }

    /** A private message, which the players it's for see in grey italics, as does whoever sent it. */
    private fun tell() = command("tell", ANY, cheat = false, aliases = listOf("w", "msg")) {
        overload(required("target", TargetType), required("message", MessageType)) {
            val targets = players("target").filter { it !== source }.ifEmpty { fail("commands.message.sameTarget") }
            val message = expand(string("message"))
            for (target in targets) {
                target.message(Text(Text.TRANSLATION, "§7§o%commands.message.display.incoming", parameters = listOf(source.name, message), needsTranslation = true, xuid = xuidOf(source)))
                val sender = source as? Session
                if (sender == null) {
                    success("commands.message.display.outgoing", target.name, message)
                } else {
                    sender.message(Text(Text.TRANSLATION, "§7§o%commands.message.display.outgoing", parameters = listOf(target.name, message), needsTranslation = true))
                    successes++
                }
            }
        }
    }

    private fun say() = command("say", GAME_DIRECTORS, cheat = false) {
        overload(required("message", MessageType)) {
            tellEveryone(Text(Text.TRANSLATION, "%chat.type.announcement", parameters = listOf(source.name, expand(string("message"))), needsTranslation = true, xuid = xuidOf(source)))
            successes++
        }
    }

    private fun gameMode() = command("gamemode", GAME_DIRECTORS, cheat = true) {
        overload(required("gameMode", gameModes), optional("player", TargetType)) { setGameMode(checkNotNull(gameModeNamed(string("gameMode")))) }
        overload(required("gameMode", IntType), optional("player", TargetType)) {
            val id = int("gameMode")
            setGameMode(GameMode.entries.find { it.id == id } ?: fail("commands.gamemode.fail.invalid", id))
        }
    }

    /**
     * Puts the players the command names, or whoever ran it, in [mode]. The
     * default game mode is the world's. Someone else's player hears theirs
     * changed.
     */
    private fun CommandCall.setGameMode(mode: GameMode) {
        val resolved = if (mode == GameMode.DEFAULT) config.gameMode else mode
        val modeName = "%gameMode.${resolved.name.lowercase()}"
        for (target in playersOrSelf("player")) {
            target.changeGameMode(resolved)
            if (target === source) {
                success("commands.gamemode.success.self", modeName)
            } else {
                success("commands.gamemode.success.other", modeName, target.name)
                target.message(Text(Text.TRANSLATION, "%gameMode.changed", parameters = listOf(modeName), needsTranslation = true))
            }
        }
    }

    private fun give() = command("give", GAME_DIRECTORS, cheat = true) {
        overload(required("player", TargetType), required("itemName", items), optional("amount", IntType), optional("data", IntType)) {
            val targets = players("player")
            val amount = intOr("amount", 1)
            if (amount < 1) fail("commands.generic.num.tooSmall", amount, 1)
            if (amount > MAX_GIVE) fail("commands.generic.num.tooBig", amount, MAX_GIVE)
            val data = intOr("data", 0)
            if (data < 0) fail("commands.generic.num.tooSmall", data, 0)
            if (data > MAX_DATA) fail("commands.generic.num.tooBig", data, MAX_DATA)
            val name = itemName(string("itemName"))
            val stack = game.items.of(name, amount, data) ?: fail("commands.give.item.notFound", name)
            for (target in targets) {
                target.give(stack)
                success("commands.give.success", stack.item.displayName, amount, target.name)
                if (target !== source) target.message(Text(Text.TRANSLATION, "%commands.give.successRecipient", parameters = listOf(stack.item.displayName, amount.toString()), needsTranslation = true))
            }
        }
    }

    /**
     * Takes items from the players the command names, or whoever ran it:
     * everything, or those of one item, and one variant of it, up to a
     * number of them. Taking none counts them instead.
     */
    private fun clear() = command("clear", GAME_DIRECTORS, cheat = true) {
        overload(optional("player", TargetType), optional("itemName", items), optional("data", IntType), optional("maxCount", IntType)) {
            val item = stringOrNull("itemName")?.let(::itemName)
            val data = intOr("data", -1)
            val maxCount = intOr("maxCount", -1)
            for (target in playersOrSelf("player")) {
                val count = target.clearItems(maxCount) { (item == null || it.item.name == item) && (data == -1 || it.aux == data) }
                when {
                    count == 0 -> error("commands.clear.failure.no.items", target.name)
                    maxCount == 0 -> success("commands.clear.testing", target.name, count)
                    else -> success("commands.clear.success", target.name, count)
                }
            }
        }
    }

    /**
     * Moves players and other entities, the one running the command or those
     * it names: to a point, a player turned the way the command says or
     * looking at a point or entity, or to another entity. Checking for
     * blocks leaves each where it is if it wouldn't fit where it's going.
     */
    private fun teleport() = command("teleport", GAME_DIRECTORS, cheat = true, aliases = listOf("tp"), description = "commands.tp.description") {
        val victim = required("victim", TargetType)
        val toPoint = required("destination", PositionType)
        val toEntity = required("destination", TargetType)
        val yRot = optional("yRot", RelativeFloatType)
        val xRot = optional("xRot", RelativeFloatType)
        val facingWord = required("facing", facing)
        val lookAtPoint = required("lookAtPosition", PositionType)
        val lookAtEntity = required("lookAtEntity", TargetType)
        val check = optional("checkForBlocks", booleans)
        val force = optional("forceProvidedPositionOnDimensionChange", booleans)

        overload(toPoint, check, force) { teleport(self(), point("destination")) }
        overload(toPoint, yRot, xRot, check, force) { teleport(self(), point("destination"), turnedBy()) }
        overload(toPoint, facingWord, lookAtPoint, check, force) { teleport(self(), point("destination"), lookingAt(point("lookAtPosition"))) }
        overload(toPoint, facingWord, lookAtEntity, check, force) { teleport(self(), point("destination"), lookingAt(eyesOf(onlyEntity("lookAtEntity")))) }
        overload(victim, toPoint, yRot, xRot, check, force) { teleport(entities("victim"), point("destination"), turnedBy()) }
        overload(victim, toPoint, check, force) { teleport(entities("victim"), point("destination")) }
        overload(victim, toPoint, facingWord, lookAtPoint, check, force) { teleport(entities("victim"), point("destination"), lookingAt(point("lookAtPosition"))) }
        overload(victim, toPoint, facingWord, lookAtEntity, check, force) {
            teleport(entities("victim"), point("destination"), lookingAt(eyesOf(onlyEntity("lookAtEntity"))))
        }
        overload(toEntity) { teleportTo(self(), onlyEntity("destination")) }
        overload(victim, toEntity, check, force) { teleportTo(entities("victim"), onlyEntity("destination")) }
    }

    /** How a teleport turns a player: from how they face now to how they'll face. */
    private fun interface Turn {
        fun of(player: Session, at: Vec3): Pair<Float, Float>
    }

    /** Turned by the command's `yRot` and `xRot`, each as it is or from where the player looks now. */
    private fun CommandCall.turnedBy() = Turn { player, _ ->
        val yaw = relative("yRot")?.from(player.yaw.toDouble())?.toFloat() ?: player.yaw
        val pitch = relative("xRot")?.from(player.pitch.toDouble())?.toFloat()?.coerceIn(-90f, 90f) ?: player.pitch
        yaw to pitch
    }

    /** Turned to look at [target] from their eyes once they're there. */
    private fun lookingAt(target: Vec3) = Turn { _, at ->
        val dx = (target.x - at.x).toDouble()
        val dy = (target.y - (at.y + PLAYER_EYE_HEIGHT)).toDouble()
        val dz = (target.z - at.z).toDouble()
        Math.toDegrees(atan2(-dx, dz)).toFloat() to Math.toDegrees(-atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()
    }

    /** Where [entity] looks from: a player's eyes, and anything else's, most of the way up it. */
    private fun eyesOf(entity: Selectable) =
        Vec3(entity.feet.x, entity.feet.y + if (entity.isPlayer) PLAYER_EYE_HEIGHT else entity.height * EYES_UP, entity.feet.z)

    private fun CommandCall.teleport(victims: List<Selectable>, to: Vec3, turn: Turn = Turn { player, _ -> player.yaw to player.pitch }) {
        if (abs(to.x) > WORLD_EDGE || abs(to.z) > WORLD_EDGE) fail("commands.tp.outOfWorld")
        val coordinates = listOf(to.x, to.y, to.z).map { String.format(Locale.ROOT, "%.2f", it) }
        val moved = mutableListOf<String>()
        for (victim in victims) {
            if (!fits(victim, to)) {
                error("commands.tp.safeTeleportFail", victim.name, coordinates.joinToString(", "))
                continue
            }
            when (victim) {
                is Session -> turn.of(victim, to).let { (yaw, pitch) -> victim.teleport(to, yaw, pitch) }
                is WorldEntity -> victim.teleport(to)
            }
            moved += victim.name
        }
        if (moved.isNotEmpty()) success("commands.tp.success.coordinates", moved.joinToString(", "), coordinates[0], coordinates[1], coordinates[2])
    }

    private fun CommandCall.teleportTo(victims: List<Selectable>, destination: Selectable) {
        val moved = mutableListOf<String>()
        for (victim in victims) {
            if (!fits(victim, destination.feet)) {
                error("commands.tp.safeTeleportFail", victim.name, destination.name)
                continue
            }
            when (victim) {
                is Session -> victim.teleport(destination.feet, victim.yaw, victim.pitch)
                is WorldEntity -> victim.teleport(destination.feet)
            }
            moved += victim.name
        }
        if (moved.isNotEmpty()) success("commands.tp.success", moved.joinToString(", "), destination.name)
    }

    /** Whether [victim] fits at [feet], or the command doesn't care. */
    private fun CommandCall.fits(victim: Selectable, feet: Vec3) = stringOrNull("checkForBlocks") != "true" || world.isClear(feet, victim.width, victim.height)

    /**
     * Kills what the command names, or whoever ran it. A player dies, as
     * they would however they died, unless they're in creative, where
     * nothing kills them, and anything else is simply gone.
     */
    private fun kill() = command("kill", GAME_DIRECTORS, cheat = true) {
        overload(optional("target", TargetType)) {
            val killed = mutableListOf<String>()
            for (target in if (has("target")) entities("target") else self()) {
                when (target) {
                    is Session -> if (!target.kill()) {
                        error("commands.kill.attemptKillPlayerCreative")
                        continue
                    }
                    is WorldEntity -> target.remove()
                }
                killed += target.name
            }
            if (killed.isNotEmpty()) success("commands.kill.successful", killed.joinToString(", "))
        }
    }

    /**
     * Puts a block in place of the one at a point, with the states the
     * command gives, the rest as the block's default: replacing it, breaking
     * it, as mined, or only if it's air.
     */
    private fun setBlock() = command("setblock", GAME_DIRECTORS, cheat = true) {
        val position = required("position", BlockPositionType)
        val tileName = required("tileName", blockNames)
        val mode = optional("oldBlockHandling", setBlockModes)
        overload(position, tileName, required("blockStates", BlockStatesType), mode) { setBlock() }
        overload(position, tileName, mode) { setBlock() }
    }

    private fun CommandCall.setBlock() {
        val pos = block("position")
        if (pos.y !in MIN_Y..MAX_Y) fail("commands.setblock.outOfWorld")
        val edited = world.fill(BlockBox(pos, pos), blockState("tileName", "blockStates"), fillMode())
        if (edited.count == 0) fail("commands.setblock.noChange")
        show(edited)
        success("commands.setblock.success")
    }

    /**
     * Fills a box with a block, with the states the command gives: all of
     * it, breaking what was there, as mined, only the air in it, its outside
     * with air within, or its outside alone; or replacing only one block in
     * it, with the states the command gives, or any of its states.
     */
    private fun fill() = command("fill", GAME_DIRECTORS, cheat = true) {
        val from = required("from", BlockPositionType)
        val to = required("to", BlockPositionType)
        val tileName = required("tileName", blockNames)
        val states = required("blockStates", BlockStatesType)
        val mode = optional("oldBlockHandling", fillModes)
        val replace = required("oldBlockHandling", replaceMode)
        val replaceTileName = optional("replaceTileName", blockNames)
        val replaceStates = optional("replaceBlockStates", BlockStatesType)
        overload(from, to, tileName, states, mode) { fill() }
        overload(from, to, tileName, mode) { fill() }
        overload(from, to, tileName, states, replace, replaceTileName, replaceStates) { fill() }
        overload(from, to, tileName, replace, replaceTileName, replaceStates) { fill() }
    }

    private fun CommandCall.fill() {
        val box = BlockBox(block("from"), block("to"))
        val limit = config.commandLimits.fill
        if (limit != null && box.volume > limit) fail("commands.fill.tooManyBlocks", box.volume, limit)
        if (!box.inWorld) fail("commands.fill.outOfWorld")
        val state = blockState("tileName", "blockStates")
        val replacing = if (has("replaceTileName")) blockFilter("replaceTileName", "replaceBlockStates") else { _ -> true }
        val edited = world.fill(box, state, fillMode(), replacing)
        if (edited.count == 0) fail("commands.fill.failed")
        show(edited)
        success("commands.fill.success", edited.count)
    }

    /** How the command handles the blocks already where it puts its own: replacing them, unless it says otherwise. */
    private fun CommandCall.fillMode() = stringOrNull("oldBlockHandling")?.let { FillMode.valueOf(it.uppercase()) } ?: FillMode.REPLACE

    /**
     * Copies the blocks in a box so its lowest corner goes to a point: all
     * of them, those that aren't air, or only one block, with the states the
     * command gives, or any of its states. Moving them leaves air where they
     * were. The boxes can only overlap if the command forces it.
     */
    private fun clone() = command("clone", GAME_DIRECTORS, cheat = true) {
        val begin = required("begin", BlockPositionType)
        val end = required("end", BlockPositionType)
        val destination = required("destination", BlockPositionType)
        overload(begin, end, destination, optional("maskMode", maskModes), optional("cloneMode", cloneModes)) { clone() }
        overload(begin, end, destination, required("maskMode", filteredMode), required("cloneMode", cloneModes), required("tileName", blockNames), optional("blockStates", BlockStatesType)) {
            clone()
        }
    }

    private fun CommandCall.clone() {
        val source = BlockBox(block("begin"), block("end"))
        val target = source.movedTo(block("destination"))
        val limit = config.commandLimits.clone
        if (limit != null && source.volume > limit) fail("commands.clone.tooManyBlocks", source.volume, limit)
        if (!source.inWorld || !target.inWorld) fail("commands.generic.outOfWorld")
        val mode = stringOrNull("cloneMode") ?: "normal"
        if (mode != "force" && source.overlaps(target)) fail("commands.clone.noOverlap")
        val copies: (BlockState) -> Boolean = when (stringOrNull("maskMode")) {
            "masked" -> { state -> state.name != AIR }
            "filtered" -> blockFilter("tileName", "blockStates")
            else -> { _ -> true }
        }
        val edited = world.clone(source, target.min, move = mode == "move", game.items, copies)
        if (edited.count == 0) fail("commands.clone.failed")
        show(edited)
        success("commands.clone.success", edited.count)
    }

    /** Says whether the block at a point is the one the command names, with the states it gives, if any. */
    private fun testForBlock() = command("testforblock", GAME_DIRECTORS, cheat = true) {
        overload(required("position", BlockPositionType), required("tileName", blockNames), optional("blockStates", BlockStatesType)) {
            val pos = block("position")
            if (pos.y !in MIN_Y..MAX_Y) fail("commands.testforblock.outOfWorld")
            val there = world.stateAt(pos)?.name.orEmpty().removePrefix("minecraft:")
            if (there != string("tileName")) fail("commands.testforblock.failed.tile", pos.x, pos.y, pos.z, there, string("tileName"))
            if (!blockFilter("tileName", "blockStates")(world.stateAt(pos)!!)) fail("commands.testforblock.failed.data", pos.x, pos.y, pos.z)
            success("commands.testforblock.success", pos.x, pos.y, pos.z)
        }
    }

    /**
     * The state of the block the parameter called [name] names, with the
     * states the one called [statesName] gives, and the rest as the block's
     * default. A state the block doesn't have, or a value of the wrong kind
     * or not one it can take, fails the command.
     */
    private fun CommandCall.blockState(name: String, statesName: String): BlockState {
        val block = "minecraft:" + string(name)
        val default = world.blocks.defaultOf(block) ?: fail("commands.setblock.notFound", string(name))
        val given = blockStatesOrNull(statesName) ?: return default
        val properties = HashMap<String, Any>(default.properties)
        for ((state, value) in given) properties[state] = stateValue(block, default, state, value)
        return world.blocks.find(block, properties) ?: run {
            val states = world.blocks.states.filter { it.name == block }
            fail("commands.blockstate.valueError", given.keys.firstOrNull { key -> states.none { it.properties[key] == properties[key] } } ?: given.keys.first())
        }
    }

    /**
     * Whether a block is the one the parameter called [name] names, with the
     * states the one called [statesName] gives, whatever its others are, or
     * in any of its states, if it gives none.
     */
    private fun CommandCall.blockFilter(name: String, statesName: String): (BlockState) -> Boolean {
        val block = "minecraft:" + string(name)
        val default = world.blocks.defaultOf(block) ?: fail("commands.setblock.notFound", string(name))
        val wanted = blockStatesOrNull(statesName).orEmpty().mapValues { (state, value) -> stateValue(block, default, state, value) }
        return { it.name == block && wanted.all { (state, value) -> it.properties[state] == value } }
    }

    /**
     * [value], as the state [state] of [block] takes it: text, a whole
     * number, or true or false, which the game keeps as 1 or 0, and takes
     * written so too.
     */
    private fun CommandCall.stateValue(block: String, default: BlockState, state: String, value: Any): Any {
        val current = default.properties[state] ?: fail("commands.blockstate.stateError", state, block.removePrefix("minecraft:"))
        return when (current) {
            is String -> value as? String
            is Int -> value as? Int
            is Byte -> when (value) {
                is Boolean -> (if (value) 1 else 0).toByte()
                is Int -> value.takeIf { it == 0 || it == 1 }?.toByte()
                else -> null
            }
            else -> null
        } ?: fail("commands.blockstate.typeError", state)
    }

    /**
     * Shows everyone with the chunks the blocks a command [edited], drops
     * what it broke, as mined, and closes what anyone had open among them.
     */
    private fun show(edited: Edited) {
        val changes = edited.changes
        sessions().filter { it.isInGame }.showAround(world.packetsFor(changes))
        changes.broken.forEach { (pos, broken) -> game.dropBroken(pos, broken, harvested = true) }
        val changed = (changes.broken.keys + changes.reshaped).toSet()
        players.forEach { it.containerBroke(changed) }
    }

    /**
     * Gives experience to the players the command names, or whoever ran it:
     * points, which can't be fewer than none, or levels, written with an
     * `L`, which can, to take levels away.
     */
    private fun xp() = command("xp", GAME_DIRECTORS, cheat = true) {
        overload(required("amount", IntType), optional("player", TargetType)) {
            val amount = int("amount")
            if (amount < 0) fail("commands.xp.failure.widthdrawXp")
            for (target in playersOrSelf("player")) {
                target.addExperience(amount)
                success("commands.xp.success", amount, target.name)
            }
        }
        overload(required("amount", SuffixedIntType("L")), optional("player", TargetType)) {
            val levels = int("amount")
            for (target in playersOrSelf("player")) {
                target.addLevels(levels)
                if (levels < 0) success("commands.xp.success.negative.levels", -levels, target.name) else success("commands.xp.success.levels", levels, target.name)
            }
        }
    }

    /**
     * Enchants what the players the command names hold, by the
     * enchantment's number or name, at a level it goes to, 1 if the command
     * doesn't say. It must go on the item, and with what's on it already,
     * whose level of it it replaces.
     */
    private fun enchant() = command("enchant", GAME_DIRECTORS, cheat = true) {
        overload(required("player", TargetType), required("enchantmentId", IntType), optional("level", IntType)) {
            val id = int("enchantmentId")
            enchant(game.enchantments[id] ?: fail("commands.enchant.notFound", id))
        }
        overload(required("player", TargetType), required("enchantmentName", enchants), optional("level", IntType)) {
            enchant(enchantmentNames.getValue(string("enchantmentName")))
        }
    }

    private fun CommandCall.enchant(enchantment: Enchantment) {
        val level = intOr("level", 1)
        val name = enchantment.name.removeSuffix("_curse")
        if (level !in 1..enchantment.maxLevel) fail("commands.enchant.invalidLevel", name, level)
        for (target in players("player")) {
            val held = target.heldStack
            if (held == null) {
                error("commands.enchant.noItem", target.name)
                continue
            }
            if (!enchantment.goesOn(held.item) || held.item.name == BOOK) {
                error("commands.enchant.cantEnchant", held.item.displayName)
                continue
            }
            val existing = held.enchantments
            val clash = existing.mapNotNull { game.enchantments[it.id] }.find { !enchantment.compatibleWith(it) }
            if (clash != null) {
                error("commands.enchant.cantCombine", name, clash.name.removeSuffix("_curse"))
                continue
            }
            target.replaceHeld(held.withEnchantments(existing.filter { it.id != enchantment.id } + EnchantmentLevel(enchantment.id, level)))
            success("commands.enchant.success", target.name)
        }
    }

    /**
     * Sets the time of day, to a number of ticks or a time by name, adds to
     * it, or says what it is: the time of day, how long the world has run,
     * or which day it is. Every player's clock follows.
     */
    private fun time() = command("time", GAME_DIRECTORS, cheat = true) {
        overload(required("set", timeSet), required("amount", IntType)) { setTime(int("amount").toLong()) }
        overload(required("set", timeSet), required("time", timesOfDay)) { setTime(TIMES_OF_DAY.getValue(string("time")).toLong()) }
        overload(required("add", EnumType("TimeAdd", listOf("add"))), required("amount", IntType)) {
            world.time += int("amount")
            players.forEach { it.showTime() }
            success("commands.time.added", int("amount"))
        }
        overload(required("query", query), required("time", timeQueries)) {
            when (string("time")) {
                "daytime" -> success("commands.time.query.daytime", world.time % DAY_LENGTH)
                "gametime" -> success("commands.time.query.gametime", world.gameTime)
                else -> success("commands.time.query.day", world.time / DAY_LENGTH)
            }
        }
    }

    private fun CommandCall.setTime(time: Long) {
        world.time = time
        players.forEach { it.showTime() }
        success("commands.time.set", time)
    }

    /**
     * Sets the weather, clear, rain, or thunder with its rain, for as many
     * ticks as the command says, or for a while picked at random, and every
     * player sees it change. Or says what it is.
     */
    private fun weather() = command("weather", GAME_DIRECTORS, cheat = true) {
        overload(required("type", weathers), optional("duration", IntType)) {
            val duration = intOr("duration", 0)
            if (duration < 0) fail("commands.generic.num.tooSmall", duration, 0)
            if (duration > MAX_WEATHER_TICKS) fail("commands.generic.num.tooBig", duration, MAX_WEATHER_TICKS)
            val type = string("type")
            world.setWeather(raining = type != "clear", thundering = type == "thunder", ticks = duration.takeIf { it > 0 })
            players.forEach { it.showWeather() }
            success("commands.weather.$type")
        }
        overload(required("query", query)) {
            val now = when {
                world.weather.thundering -> "thunder"
                world.weather.raining -> "rain"
                else -> "clear"
            }
            success("commands.weather.query", "%commands.weather.query.$now")
        }
    }

    /** Starts rain, or stops whatever's falling. */
    private fun toggleDownfall() = command("toggledownfall", GAME_DIRECTORS, cheat = true) {
        overload {
            world.setWeather(raining = !world.weather.raining, thundering = false)
            players.forEach { it.showWeather() }
            success("commands.downfall.success")
        }
    }

    /**
     * Locks the day, as the Always Day option does: day turns to night no
     * more, and it's the middle of the morning; or unlocks it again.
     */
    private fun dayLock() = command("daylock", GAME_DIRECTORS, cheat = true, aliases = listOf("alwaysday")) {
        overload(optional("lock", booleans)) {
            val lock = stringOrNull("lock") != "false"
            world.rules.set(DAYLIGHT_CYCLE, !lock)
            if (lock) world.time = world.time - world.time % DAY_LENGTH + LOCKED_TIME
            players.forEach { it.showRules(mapOf(DAYLIGHT_CYCLE to !lock)); it.showTime() }
            success(if (lock) "commands.always.day.locked" else "commands.always.day.unlocked")
        }
    }

    /**
     * Sets a game rule, on or off, or to a number, which every player's
     * client hears of. Or says what one is, or with none named, lists them
     * all, as `rule = value`. Only the rules that do something in Kore.
     */
    private fun gameRule() = command("gamerule", GAME_DIRECTORS, cheat = true) {
        overload {
            success(rulesInUse.keys.joinToString(", ") { "$it = ${world.rules[it]}" })
        }
        overload(required("rule", switchRules), optional("value", booleans)) { setRule(stringOrNull("value")?.toBooleanStrict()) }
        // Until a rule that's a number does something, there's none to offer.
        if (numberRules.values.isNotEmpty()) {
            overload(required("rule", numberRules), optional("value", IntType)) { setRule(if (has("value")) int("value") else null) }
        }
    }

    /** Sets the rule the command names to [value], or says what it is, for none. */
    private fun CommandCall.setRule(value: Any?) {
        val rule = string("rule")
        if (value == null) return success("$rule = ${world.rules[rule]}")
        world.rules.set(rule, value)
        players.forEach { it.showRules(mapOf(rule to value)) }
        success("commands.gamerule.success", rule, value)
    }

    /**
     * Gives the players the command names an effect, at a strength, for
     * so many seconds, 30 if it doesn't say, or for ever, its particles
     * showing or not, whatever they had of it; for no seconds at all, takes
     * it away. Or takes one effect, or every effect, away. Only players have
     * effects.
     */
    private fun effect() = command("effect", GAME_DIRECTORS, cheat = true) {
        val player = required("player", TargetType)
        val kind = required("effect", effectNames)
        val amplifier = optional("amplifier", IntType)
        val hideParticles = optional("hideParticles", booleans)
        overload(player, required("mode", clearWord), optional("effect", effectNames)) { takeEffects() }
        overload(player, kind, optional("seconds", IntType), amplifier, hideParticles) { giveEffect(intOr("seconds", DEFAULT_EFFECT_SECONDS)) }
        overload(player, kind, required("infinite", infiniteWord), amplifier, hideParticles) { giveEffect(null) }
    }

    /** Gives the effect the command names for [seconds], or for ever, for null. */
    private fun CommandCall.giveEffect(seconds: Int?) {
        val kind = EFFECT_KINDS.first { it.name == string("effect") }
        val amplifier = intOr("amplifier", 0)
        if (seconds != null && seconds !in 0..MAX_EFFECT_SECONDS) fail(if (seconds < 0) "commands.generic.num.tooSmall" else "commands.generic.num.tooBig", seconds, if (seconds < 0) 0 else MAX_EFFECT_SECONDS)
        if (amplifier !in 0..MAX_AMPLIFIER) fail(if (amplifier < 0) "commands.generic.num.tooSmall" else "commands.generic.num.tooBig", amplifier, if (amplifier < 0) 0 else MAX_AMPLIFIER)
        val particles = stringOrNull("hideParticles") != "true"
        for (target in entities("player")) {
            if (target !is Session) {
                error("commands.effect.failure.notAMob", target.name)
                continue
            }
            when {
                seconds == 0 -> if (target.takeEffects(kind.id)) {
                    success("commands.effect.success.removed", kind.title, target.name)
                } else {
                    error("commands.effect.failure.notActive", kind.title, target.name)
                }
                seconds == null -> {
                    target.setEffect(EffectLevel(kind.id, amplifier), INFINITE_EFFECT, particles)
                    success("commands.effect.success.infinite", kind.title, amplifier, target.name)
                }
                else -> {
                    target.setEffect(EffectLevel(kind.id, amplifier), seconds * TICKS_PER_SECOND, particles)
                    success("commands.effect.success", kind.title, amplifier, target.name, seconds)
                }
            }
        }
    }

    /** Takes the effect the command names away, or all of them, if it names none. */
    private fun CommandCall.takeEffects() {
        val kind = stringOrNull("effect")?.let { name -> EFFECT_KINDS.first { it.name == name } }
        for (target in entities("player")) {
            when {
                target !is Session -> error("commands.effect.failure.notAMob", target.name)
                target.takeEffects(kind?.id) ->
                    if (kind == null) success("commands.effect.success.removed.all", target.name) else success("commands.effect.success.removed", kind.title, target.name)
                kind == null -> error("commands.effect.failure.notActive.all", target.name)
                else -> error("commands.effect.failure.notActive", kind.title, target.name)
            }
        }
    }

    /** Sets the world's difficulty, by name, its first letter, or its number, which every player hears of. */
    private fun difficulty() = command("difficulty", GAME_DIRECTORS, cheat = true) {
        overload(required("difficulty", difficulties)) {
            setDifficulty(Difficulty.entries.first { it.name.lowercase().startsWith(string("difficulty")) })
        }
        overload(required("difficulty", IntType)) {
            val id = int("difficulty")
            setDifficulty(Difficulty.entries.getOrNull(id) ?: fail("commands.generic.parameter.invalid", id))
        }
    }

    private fun CommandCall.setDifficulty(difficulty: Difficulty) {
        world.difficulty = difficulty
        players.forEach { it.tell(SetDifficulty(difficulty)) }
        success("commands.difficulty.success", "%options.difficulty.${difficulty.name.lowercase()}")
    }

    /** Moves the world's spawn, where new players appear and those without a spawn point respawn, to a block, or where the command runs. */
    private fun setWorldSpawn() = command("setworldspawn", GAME_DIRECTORS, cheat = true) {
        overload(optional("spawnPoint", BlockPositionType)) {
            val pos = if (has("spawnPoint")) block("spawnPoint") else source.origin.toBlockPos()
            if (pos.y !in MIN_Y..MAX_Y) fail("commands.generic.outOfWorld")
            world.spawn = pos
            players.forEach { it.tell(SetSpawnPosition(SetSpawnPosition.WORLD, pos)) }
            success("commands.setworldspawn.success", pos.x, pos.y, pos.z)
        }
    }

    /**
     * Sets where the players the command names, or whoever ran it, respawn:
     * at a block, or for each, where they stand.
     */
    private fun spawnPoint() = command("spawnpoint", GAME_DIRECTORS, cheat = true) {
        overload(optional("player", TargetType), optional("spawnPos", BlockPositionType)) {
            val targets = playersOrSelf("player")
            val given = if (has("spawnPos")) block("spawnPos") else null
            if (given != null && given.y !in MIN_Y..MAX_Y) fail("commands.generic.outOfWorld")
            targets.forEach { it.setSpawnPoint(given ?: it.feet.toBlockPos()) }
            val one = targets.singleOrNull()
            when {
                one != null -> one.spawnPoint!!.let { success("commands.spawnpoint.success.single", one.name, it.x, it.y, it.z) }
                given != null -> success("commands.spawnpoint.success.multiple.specific", targets.joinToString { it.name }, given.x, given.y, given.z)
                else -> success("commands.spawnpoint.success.multiple.generic", targets.joinToString { it.name })
            }
        }
    }

    /** Takes away the spawn points of the players the command names, or whoever ran it, who respawn at the world's spawn again. */
    private fun clearSpawnPoint() = command("clearspawnpoint", GAME_DIRECTORS, cheat = true) {
        overload(optional("player", TargetType)) {
            val targets = playersOrSelf("player")
            targets.forEach { it.setSpawnPoint(null) }
            val one = targets.singleOrNull()
            if (one != null) success("commands.clearspawnpoint.success.single", one.name) else success("commands.clearspawnpoint.success.multiple", targets.joinToString { it.name })
        }
    }

    /**
     * Shows the players the command names a title, a subtitle under it, or a
     * line above the hotbar, or sets how long titles fade in, stay and fade
     * out for, in ticks, or clears the title, or resets it all.
     */
    private fun title() = command("title", GAME_DIRECTORS, cheat = true) {
        titleOverloads(raw = false, required("titleText", MessageType))
    }

    /** As `/title` does, with rawtext, as `/tellraw` takes it. */
    private fun titleRaw() = command("titleraw", GAME_DIRECTORS, cheat = true) {
        titleOverloads(raw = true, required("raw json titleText", JsonType))
    }

    private fun CommandBuilder.titleOverloads(raw: Boolean, text: Param) {
        val player = required("player", TargetType)
        overload(player, required("clear", titleClear)) { showTitle(raw, SetTitle(SetTitle.CLEAR)) }
        overload(player, required("reset", titleReset)) { showTitle(raw, SetTitle(SetTitle.RESET)) }
        overload(player, required("titleLocation", titleSet), text) {
            val action = when (string("titleLocation")) {
                "title" -> SetTitle.TITLE
                "subtitle" -> SetTitle.SUBTITLE
                else -> SetTitle.ACTION_BAR
            }
            val written = string(text.name)
            showTitle(raw, if (raw) SetTitle(action + SetTitle.RAW, rawText(written)) else SetTitle(action, written))
        }
        overload(player, required("times", titleTimes), required("fadeIn", IntType), required("stay", IntType), required("fadeOut", IntType)) {
            showTitle(raw, SetTitle(SetTitle.TIMES, fadeIn = int("fadeIn"), stay = int("stay"), fadeOut = int("fadeOut")))
        }
    }

    private fun CommandCall.showTitle(raw: Boolean, title: SetTitle) {
        players("player").forEach { it.tell(title) }
        success(if (raw) "commands.titleraw.success" else "commands.title.success")
    }

    /** Sends the players the command names a message in rawtext, which the game puts together for each of them in their language. */
    private fun tellRaw() = command("tellraw", GAME_DIRECTORS, cheat = true) {
        overload(required("target", TargetType), required("raw json message", JsonType)) {
            val text = Text(Text.OBJECT, rawText(string("raw json message")))
            players("target").forEach { it.message(text) }
            successes++
        }
    }

    /**
     * The rawtext [json] gives, as `/tellraw` and `/titleraw` take it, with
     * each selector in it written out as the names of what it picks out,
     * which a client can't pick out for itself, and each score, which there
     * are none of yet, as nothing. Rawtext that isn't as the game has it
     * fails the command, saying how.
     */
    private fun CommandCall.rawText(json: String): String {
        if (json.isBlank()) fail("commands.tellraw.error.noData")
        val parsed = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            fail("commands.tellraw.jsonStringException")
        }
        return resolved(parsed as? JsonObject ?: fail("commands.tellraw.jsonStringException")).toString()
    }

    private fun CommandCall.resolved(raw: JsonObject): JsonObject {
        val parts = raw["rawtext"] as? JsonArray ?: fail("commands.tellraw.error.notArray")
        fun JsonElement.text(error: String) = (this as? JsonPrimitive)?.takeIf { it.isString } ?: fail(error)
        val resolved = parts.map { part ->
            val piece = part as? JsonObject ?: fail("commands.tellraw.error.itemIsNotObject")
            val out = LinkedHashMap<String, JsonElement>()
            piece["text"]?.let { out["text"] = it.text("commands.tellraw.error.textNotString") }
            piece["translate"]?.let { out["translate"] = it.text("commands.tellraw.error.translateNotString") }
            piece["with"]?.let { with ->
                out["with"] = when (with) {
                    is JsonArray -> with
                    is JsonObject -> resolved(with)
                    else -> fail("commands.tellraw.error.withNotArrayOrRawText")
                }
            }
            piece["selector"]?.let { selector ->
                val target = runCatching { readTarget(CommandReader(selector.text("commands.tellraw.error.selectorNotString").content)) }
                    .getOrElse { fail("commands.tellraw.error.selectorNotString") }
                out["text"] = JsonPrimitive(pickEntities(target).joinToString { it.name })
            }
            piece["score"]?.let { score ->
                val asked = score as? JsonObject ?: fail("commands.tellraw.error.scoreNotObject")
                asked["name"]?.text("commands.tellraw.error.scoreNameNotString")
                asked["objective"]?.text("commands.tellraw.error.scoreObjectiveNotString")
                out["text"] = JsonPrimitive("")
            }
            JsonObject(out)
        }
        return JsonObject(mapOf("rawtext" to JsonArray(resolved)))
    }

    /**
     * Plays a sound to the players the command names, or whoever ran it:
     * from a point, or where each of them is, at a volume and pitch. Out of
     * its range, they hear it only if the command gives a least volume, at
     * which it comes from beside them.
     */
    private fun playSound() = command("playsound", GAME_DIRECTORS, cheat = true) {
        overload(
            required("sound", StringType), optional("player", TargetType), optional("position", PositionType),
            optional("volume", FloatType), optional("pitch", FloatType), optional("minimumVolume", FloatType),
        ) {
            val sound = string("sound")
            val volume = doubleOr("volume", 1.0).toFloat()
            val pitch = doubleOr("pitch", 1.0).toFloat()
            val least = doubleOr("minimumVolume", 0.0).toFloat()
            val heard = mutableListOf<String>()
            for (player in playersOrSelf("player")) {
                val from = if (has("position")) point("position") else player.feet
                val dx = from.x - player.feet.x
                val dy = from.y - player.feet.y
                val dz = from.z - player.feet.z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)
                if (distance <= maxOf(volume, 1f) * SOUND_RANGE) {
                    player.tell(PlaySound(sound, from, volume, pitch))
                } else if (least > 0) {
                    val beside = Vec3(player.feet.x + dx / distance * SOUND_BESIDE, player.feet.y + dy / distance * SOUND_BESIDE, player.feet.z + dz / distance * SOUND_BESIDE)
                    player.tell(PlaySound(sound, beside, least, pitch))
                } else {
                    error("commands.playsound.playerTooFar", player.name)
                    continue
                }
                heard += player.name
            }
            if (heard.isNotEmpty()) success("commands.playsound.success", sound, heard.joinToString())
        }
    }

    /** Stops a sound for the players the command names, or every sound. */
    private fun stopSound() = command("stopsound", GAME_DIRECTORS, cheat = true) {
        overload(required("player", TargetType), optional("sound", StringType)) {
            val sound = stringOrNull("sound")
            players("player").forEach { it.tell(StopSound(sound)) }
            if (sound == null) success("commands.stopsound.success.all") else success("commands.stopsound.success", sound)
        }
    }

    /** Shows a particle effect at a point, or where the command runs, to everyone with the chunk. */
    private fun particle() = command("particle", GAME_DIRECTORS, cheat = true) {
        overload(required("effect", StringType), optional("position", PositionType)) {
            val at = if (has("position")) point("position") else source.origin
            sessions().filter { it.isInGame }.showAround(listOf(at.toBlockPos() to SpawnParticleEffect(string("effect"), at)))
            success("commands.particle.success", string("effect"), 1)
        }
    }

    /** Counts what a selector picks out, naming it. */
    private fun testFor() = command("testfor", GAME_DIRECTORS, cheat = true) {
        overload(required("victim", TargetType)) {
            val found = entities("victim")
            success("commands.testfor.success", found.joinToString { it.name })
            successes = found.size
        }
    }

    /** Says whether the blocks in one box are the same as those in another, all of them, or those that aren't air. */
    private fun testForBlocks() = command("testforblocks", GAME_DIRECTORS, cheat = true) {
        overload(required("begin", BlockPositionType), required("end", BlockPositionType), required("destination", BlockPositionType), optional("mode", compareModes)) {
            val source = BlockBox(block("begin"), block("end"))
            val target = source.movedTo(block("destination"))
            val limit = config.commandLimits.clone
            if (limit != null && source.volume > limit) fail("commands.compare.tooManyBlocks", source.volume, limit)
            if (!source.inWorld || !target.inWorld) fail("commands.generic.outOfWorld")
            val compared = world.compare(source, target.min, masked = stringOrNull("mode") == "masked") ?: fail("commands.compare.failed")
            success("commands.compare.success", compared)
        }
    }

    /** Sets how many players can be on the server at once, while it runs, though never fewer than are on. */
    private fun setMaxPlayers() = command("setmaxplayers", ADMIN, cheat = false) {
        overload(required("maxPlayers", IntType)) {
            val asked = int("maxPlayers")
            val max = maxOf(asked, players.size, 1)
            game.maxPlayers = max
            success("commands.setmaxplayers.success", max)
            if (max > asked && players.size > asked) success("commands.setmaxplayers.success.lowerbound")
        }
    }

    private fun kick() = command("kick", GAME_DIRECTORS, cheat = false) {
        overload(required("name", TargetType), required("reason", MessageType)) {
            val target = target("name")
            val found = pick(target).ifEmpty {
                if (target is Target.Name) fail("commands.kick.not.found", target.name) else fail("commands.generic.noTargetMatch")
            }
            val reason = string("reason")
            for (player in found) {
                if (player === source) {
                    error("commands.kick.not.yourself")
                    continue
                }
                player.kick(reason.ifEmpty { null })
                if (reason.isEmpty()) success("commands.kick.success", player.name) else success("commands.kick.success.reason", player.name, reason)
            }
        }
    }

    private fun op() = command("op", ADMIN, cheat = false) {
        overload(required("player", TargetType)) {
            for (player in players("player")) {
                val identity = player.player ?: continue
                if (!operators.add(identity.displayName, identity.xuid)) {
                    error("commands.op.failed", player.name)
                    continue
                }
                player.permissionsChanged()
                player.message(Text(Text.TRANSLATION, "%commands.op.message", needsTranslation = true))
                success("commands.op.success", player.name)
            }
        }
    }

    private fun deop() = command("deop", ADMIN, cheat = false) {
        overload(required("player", TargetType)) {
            for (player in players("player")) {
                val identity = player.player ?: continue
                if (!operators.remove(identity.displayName, identity.xuid)) {
                    error("commands.deop.failed", player.name)
                    continue
                }
                player.permissionsChanged()
                player.message(Text(Text.TRANSLATION, "%commands.deop.message", needsTranslation = true))
                success("commands.deop.success", player.name)
            }
        }
    }

    private fun stop() = command("stop", OWNER, cheat = true) {
        overload {
            success("commands.stop.start")
            shutDown()
        }
    }

    /** The players [target] means, for whoever runs the command, from where they are. */
    private fun CommandCall.pick(target: Target): List<Session> = target.select(players, source as? Session, source.origin, source.yaw, source.pitch)

    /** What [target] means among everything commands can pick out, for whoever runs the command, from where they are. */
    private fun CommandCall.pickEntities(target: Target): List<Selectable> = target.select(entities, source as? Session, source.origin, source.yaw, source.pitch)

    /** The players the parameter called [name] means, of whom there must be one at least. */
    private fun CommandCall.players(name: String): List<Session> = pick(target(name)).ifEmpty { fail("commands.generic.noTargetMatch") }

    /** The players the parameter called [name] means, or if it's left out, whoever ran the command, who must be a player. */
    private fun CommandCall.playersOrSelf(name: String): List<Session> = if (has(name)) players(name) else self()

    /** Whoever ran the command, as a player. */
    private fun CommandCall.self(): List<Session> = listOfNotNull(source as? Session).ifEmpty { fail("commands.generic.noTargetMatch") }

    /** The players and other entities the parameter called [name] means, of which there must be one at least. */
    private fun CommandCall.entities(name: String): List<Selectable> = pickEntities(target(name)).ifEmpty { fail("commands.generic.noTargetMatch") }

    /** The one player or other entity the parameter called [name] means. */
    private fun CommandCall.onlyEntity(name: String): Selectable = entities(name).singleOrNull() ?: fail("commands.generic.tooManyTargets")

    /**
     * Everything commands can pick out: the players in the game, and the
     * items, experience orbs, falling blocks and projectiles in the world.
     */
    private val entities: List<Selectable>
        get() = players +
            world.items.map { WorldEntity(ITEM_TYPE, it.stack.item.displayName, it.position, ITEM_SIZE, ITEM_SIZE, it::teleport) { world.removeItem(it.id) } } +
            world.orbs.map { WorldEntity(ORB_TYPE, ORB_NAME, it.position, ITEM_SIZE, ITEM_SIZE, it::teleport) { world.removeOrb(it.id) } } +
            world.fallingBlocks.map {
                WorldEntity(FALLING_BLOCK_TYPE, FALLING_BLOCK_NAME, it.position, FALLING_BLOCK_SIZE, FALLING_BLOCK_SIZE, it::teleport) { world.removeFallingBlock(it.id) }
            } +
            world.projectiles.map { WorldEntity(it.kind.entity, it.kind.title, it.position, it.kind.width, it.kind.height, it::teleport) { world.removeProjectile(it.id) } }

    /** [message] with each selector in it, like `@a`, written out as the names of what it means. */
    private fun CommandCall.expand(message: String): String = message.split(' ').joinToString(" ") { word ->
        val selector = if (word.startsWith('@')) runCatching { readTarget(CommandReader(word)) }.getOrNull() else null
        val found = selector?.let { pickEntities(it) }.orEmpty()
        if (found.isEmpty()) word else found.joinToString(", ") { it.name }
    }

    private fun tellEveryone(text: Text) = players.forEach { it.message(text) }

    private fun xuidOf(source: CommandSource) = (source as? Session)?.player?.xuid.orEmpty()

    /** An item's full name, from the name a command gave, which can leave out the namespace. */
    private fun itemName(name: String) = if (':' in name) name else "minecraft:$name"
}

/**
 * Something in the world besides a player that commands can pick out, by
 * the game's name for its kind, and what it's called, which for an item is
 * the item's name, as wide and tall as it is, which [teleport] moves and
 * [remove] takes away.
 */
private class WorldEntity(
    override val type: String,
    override val name: String,
    override val feet: Vec3,
    override val width: Float,
    override val height: Float,
    val teleport: (Vec3) -> Unit,
    val remove: () -> Unit,
) : Selectable {
    override val yaw get() = 0f
    override val pitch get() = 0f
    override val gameMode: GameMode? get() = null
}
