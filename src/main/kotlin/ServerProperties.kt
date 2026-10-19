package org.koremc

import org.koremc.protocol.Difficulty
import org.koremc.protocol.GameMode
import org.koremc.storage.LevelType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger("org.koremc.ServerProperties")

/** Where Kore keeps its settings: in the folder it runs in, as the game's dedicated server does. */
val SERVER_PROPERTIES: Path = Path.of("server.properties")

/** The fewest chunks around each player the game's dedicated server lets the view distance be. */
private const val MIN_VIEW_DISTANCE = 5

/** The game modes a server can start players in. */
private val GAME_MODES = listOf(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE)

/** Characters a world's name can't have, as its folder's. */
private const val NOT_IN_FOLDER_NAMES = "\\/:*?\"<>|"

/**
 * A setting in server.properties: its [key], what it's for and the values
 * it takes, written below it, its value in a config, and that config with
 * a value read, or null for a value it can't take.
 */
private class Setting(
    val key: String,
    val comment: String,
    val valueOf: (ServerConfig) -> String,
    val read: ServerConfig.(String) -> ServerConfig?,
)

private fun boolean(value: String) = value.lowercase().toBooleanStrictOrNull()

/** One of [values] by its name, in any case, or its number as the dedicated server takes it too: its place among them. */
private fun <T : Enum<T>> named(values: List<T>, value: String): T? =
    values.find { it.name.equals(value, ignoreCase = true) } ?: value.toIntOrNull()?.let(values::getOrNull)

/** A world's seed from what's written: a number as it is, words as Java Edition makes a number of them, and nothing for a random one. */
private fun seedOf(value: String): Long? = if (value.isEmpty()) null else value.toLongOrNull() ?: value.hashCode().toLong()

/** The settings Kore has, in the order a new server.properties lists them. Kore's own are in kore.jsonc instead. */
private val SETTINGS = listOf(
    Setting("server-name", "The server's name, as players see it.\nAllowed values: any text without a semicolon", { it.name }) { value ->
        copy(name = value).takeIf { ';' !in value }
    },
    Setting("gamemode", "The game mode new players start in, and a new world's.\nAllowed values: \"survival\", \"creative\" or \"adventure\"", { it.gameMode.name.lowercase() }) { value ->
        named(GAME_MODES, value)?.let { copy(gameMode = it) }
    },
    Setting(
        "force-gamemode",
        "Whether gamemode applies to a world made before it was changed. If not, the world keeps the game mode it was made with.\nAllowed values: \"true\" or \"false\"",
        { it.forceGameMode.toString() },
    ) { value -> boolean(value)?.let { copy(forceGameMode = it) } },
    Setting("difficulty", "How hard the game is.\nAllowed values: \"peaceful\", \"easy\", \"normal\" or \"hard\"", { it.difficulty.name.lowercase() }) { value ->
        named(Difficulty.entries, value)?.let { copy(difficulty = it) }
    },
    Setting(
        "allow-cheats",
        "Whether commands that change the game, like /gamemode and /give, work. Only operators can run them.\nAllowed values: \"true\" or \"false\"",
        { it.allowCheats.toString() },
    ) { value -> boolean(value)?.let { copy(allowCheats = it) } },
    Setting("max-players", "The most players on the server at once.\nAllowed values: any whole number above 0", { it.maxPlayers.toString() }) { value ->
        value.toIntOrNull()?.takeIf { it > 0 }?.let { copy(maxPlayers = it) }
    },
    Setting(
        "online-mode",
        "Whether players have to be signed in to Xbox Live to join. Turn it off only to test: without it, anyone can join as anyone.\nAllowed values: \"true\" or \"false\"",
        { it.onlineMode.toString() },
    ) { value -> boolean(value)?.let { copy(onlineMode = it) } },
    Setting("server-port", "The port players connect to.\nAllowed values: whole numbers from 1 to 65535", { it.port.toString() }) { value ->
        value.toIntOrNull()?.takeIf { it in 1..65535 }?.let { copy(port = it) }
    },
    Setting(
        "server-portv6",
        "The port the server list finds the server on, for players who reach it by an IPv6 address.\nAllowed values: whole numbers from 1 to 65535",
        { it.portV6.toString() },
    ) { value -> value.toIntOrNull()?.takeIf { it in 1..65535 }?.let { copy(portV6 = it) } },
    Setting(
        "enable-lan-visibility",
        "Whether games on the local network find the server, as they find each other's worlds.\nAllowed values: \"true\" or \"false\"",
        { it.lanVisibility.toString() },
    ) { value -> boolean(value)?.let { copy(lanVisibility = it) } },
    Setting(
        "view-distance",
        "The most chunks around each player the server sends, whatever their own setting.\nAllowed values: whole numbers from $MIN_VIEW_DISTANCE up",
        { it.viewDistance.toString() },
    ) { value -> value.toIntOrNull()?.takeIf { it >= MIN_VIEW_DISTANCE }?.let { copy(viewDistance = it) } },
    Setting("level-name", "The world's name, and its folder in worlds.\nAllowed values: any text a folder can be called", { it.level }) { value ->
        copy(level = value).takeIf { value.isNotBlank() && value.none { it in NOT_IN_FOLDER_NAMES } }
    },
    Setting("level-seed", "The seed of a new world. Left empty, it's random.\nAllowed values: any text", { it.seed?.toString().orEmpty() }) { value ->
        copy(seed = seedOf(value))
    },
    Setting(
        "level-type",
        "What a new world is like. A world made before keeps its own. Kore makes only flat worlds so far.\nAllowed values: \"DEFAULT\", \"FLAT\" or \"LEGACY\"",
        { it.levelType.name },
    ) { value -> LevelType.entries.find { it.name.equals(value, ignoreCase = true) }?.let { copy(levelType = it) } },
)

private val SETTINGS_BY_KEY = SETTINGS.associateBy { it.key }

/**
 * The server's settings in [file], as the game's dedicated server writes
 * them: a `key=value` on each line, with `#` comments. A setting that's
 * missing keeps its default, as does one with a value it can't take, with
 * a warning. Settings Kore doesn't have yet are noted, and left alone.
 * With no file, writes one with every setting at its default, explained.
 */
fun loadServerConfig(file: Path, defaults: ServerConfig = ServerConfig()): ServerConfig {
    if (!file.exists()) {
        file.writeText(serverProperties(defaults))
        log.info("Made {} with the default settings", file)
        return defaults
    }
    val values = file.readLines().mapNotNull { line ->
        val setting = line.trim().takeUnless { it.isEmpty() || it.startsWith('#') } ?: return@mapNotNull null
        val (key, value) = setting.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        key.trim() to value.trim()
    }
    var config = defaults
    for ((key, value) in values) {
        val setting = SETTINGS_BY_KEY[key] ?: continue
        config = setting.read(config, value) ?: config.also { log.warn("{} in {} can't be \"{}\", so it's {}", key, file, value, setting.valueOf(it)) }
    }
    val unknown = values.map { it.first }.filter { it !in SETTINGS_BY_KEY }
    if (unknown.isNotEmpty()) log.info("Kore doesn't have these settings in {} yet: {}", file, unknown.joinToString())
    return config
}

/** [config] as server.properties, each setting with what it's for below it, as the game's dedicated server writes the file. */
fun serverProperties(config: ServerConfig): String = SETTINGS.joinToString("\n\n", postfix = "\n") { setting ->
    "${setting.key}=${setting.valueOf(config)}\n" + setting.comment.lines().joinToString("\n") { "# $it" }
}
