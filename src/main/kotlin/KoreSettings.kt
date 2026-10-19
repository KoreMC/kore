package org.koremc

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.koremc.storage.FlatPreset
import org.koremc.world.CommandLimits
import org.koremc.world.Features
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger("org.koremc.KoreSettings")

/**
 * Where Kore keeps the settings of its own, which the game's dedicated
 * server doesn't have: beside server.properties, as JSON with comments,
 * named so editors take the comments.
 */
val KORE_SETTINGS: Path = Path.of("kore.jsonc")

/** Where Kore 0.1.0 kept them, named as plain JSON, which editors mark every comment in as wrong. */
val OLD_KORE_SETTINGS: Path = Path.of("kore.json")

/** Reads kore.jsonc with the comments explaining each setting, as JSON with comments has them, and a comma after the last if there is one. */
@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    allowComments = true
    allowTrailingComma = true
}

/** The settings in kore.jsonc: Kore's own, which server.properties, as the game's dedicated server has it, has no place for. */
@Serializable
private data class KoreSettings(
    val advertisedAddresses: List<String>,
    val operatorDomain: String,
    val shutdownMessage: String,
    val flatWorldPreset: String,
    val features: Features,
    val commandLimits: CommandLimits,
    val logs: LogSettings,
)

/** What each setting is for, written above it, and each of those within a setting, by the setting's name and its own. */
private val COMMENTS = mapOf(
    "advertisedAddresses" to "The addresses players reach the server at: IP addresses or host names.\n" +
        "Left empty, every address this computer has that others can reach.",
    "operatorDomain" to "Shown to players, as text the server can't vouch for, the first time they accept its key.",
    "shutdownMessage" to "What players still on the server are told as it stops.",
    "flatWorldPreset" to "The preset a new flat world is made from, named as the game names it: \"Classic Flat\", \"Tunneller's Dream\",\n" +
        "\"Water World\", \"Overworld\", \"Snowy Kingdom\", \"Bottomless Pit\", \"Desert\", \"Redstone Ready\" or \"The Void\".\n" +
        "Only with level-type FLAT in server.properties, and only as the world is made: one made before keeps its layers.",
    "features" to "The game's systems Kore runs: all on, the game as it is, and all off, blocks that are only blocks.\n" +
        "Off, a system doesn't run at all, and its blocks do nothing. The game rules switch off the rest,\n" +
        "like the weather with doweathercycle, or blocks' drops with dotiledrops.",
    "features.redstone" to "Power, and all it works: dust, torches, repeaters, comparators, observers, lamps, pistons,\n" +
        "pressure plates and daylight detectors, and the doors, note blocks, dispensers and hoppers it moves.\n" +
        "Off, levers and buttons still flip, but power goes nowhere.",
    "features.liquids" to "Water and lava flowing, what they make where they meet, and concrete powder hardening in water.\n" +
        "Off, they stay as they're put.",
    "features.fallingBlocks" to "Sand, gravel, anvils and the like falling with nothing under them.\n" +
        "Off, they stay where they are, and hanging dripstone and snow break instead.",
    "features.growth" to "Random ticks, bone meal, trampling farmland, and snow and ice forming: whatever grows, spreads, dries, decays, melts, freezes or weathers.",
    "features.machines" to "Furnaces, brewing stands, hoppers, dispensers, droppers, crafters and beacons working.\n" +
        "Off, they keep what's put in them, and do nothing with it.",
    "features.damage" to "Players being hurt: by falling, the void, lava, fire and burning, drowning, suffocating, magma, campfires,\n" +
        "cactus and berry bushes, poison and wither, and healing again over time. Off, nothing hurts them but /kill.",
    "features.hunger" to "The food bar going down as players sprint, swim, jump, mine and are hurt, and starving them once it's empty.\n" +
        "Off, it stays full.",
    "features.food" to "Eating and drinking: food and cake filling the food bar, and what food, potions and milk give, like a golden apple's effects.\n" +
        "Off, they're only items, and cake only a block.",
    "features.combat" to "Players hitting each other: damage by weapon, enchantments and effects, critical hits, knockback and Fire Aspect.\n" +
        "Off, a hit does nothing. The pvp game rule turns it off too, for a while.",
    "features.projectiles" to "Throwing snowballs, eggs and ender pearls, which hit players and knock them back, and an ender pearl taking its thrower\n" +
        "where it lands. Off, they're only items.",
    "commandLimits" to "The most blocks a command changes at once: a number, true for the game's own limit, or false for none.\n" +
        "Past the game's own, a big fill or clone can hold the server up a moment as it works, and players' games as they take it in.",
    "commandLimits.fill" to "The most blocks /fill fills. The game's own limit is 32768.",
    "commandLimits.clone" to "The most blocks /clone copies. The game's own limit is 655360.",
    "logs" to "What the console and log file keep. Off, what players say, or the commands they run, aren't logged at all,\n" +
        "to keep the logs short.",
    "logs.debug" to "Detail for working out what went wrong, DBG, in the console too, but for every packet. The log file always has it.",
    "logs.chat" to "What players say in the chat, CHT.",
    "logs.commands" to "The commands players run, CMD.",
)

/** Renames [old], an older Kore's settings file, to [file], unless there's one there already. */
fun renameOldKoreSettings(old: Path, file: Path) {
    if (!old.exists() || file.exists()) return
    old.moveTo(file)
    log.info("Renamed {} to {}", old, file)
}

/**
 * These settings, with Kore's own from [file], kore.jsonc. With no file,
 * writes one with every setting as these have it, each explained. A file
 * without some of them, or without some of a setting's own, as from an
 * older Kore, is written again with them, as these have them, keeping the
 * rest.
 */
fun ServerConfig.withKoreSettings(file: Path): ServerConfig {
    val defaults = json.encodeToJsonElement(
        KoreSettings.serializer(), KoreSettings(advertisedAddresses, operatorDomain, shutdownMessage, flatWorldPreset.title, features, commandLimits, logs),
    ).jsonObject
    if (!file.exists()) {
        file.writeText(written(defaults))
        log.info("Made {} with Kore's own settings", file)
        return this
    }
    val saved = try {
        json.parseToJsonElement(file.readText()).jsonObject
    } catch (e: SerializationException) {
        throw IllegalStateException("$file isn't JSON Kore can read: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("$file isn't a JSON object of settings: ${e.message}", e)
    }
    val missing = missingFrom(saved, defaults)
    // Kore's settings in their own order, then anything else the file has.
    val whole = filledIn(saved, defaults)
    if (missing.isNotEmpty()) {
        file.writeText(written(whole))
        log.info("Added {} to {}", missing.joinToString(), file)
    }
    val settings = try {
        json.decodeFromJsonElement(KoreSettings.serializer(), whole)
    } catch (e: SerializationException) {
        throw IllegalStateException("$file has a setting Kore can't read: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("$file has a setting Kore can't read: ${e.message}", e)
    }
    val preset = FlatPreset.named(settings.flatWorldPreset) ?: throw IllegalStateException(
        "$file has no flat world preset called \"${settings.flatWorldPreset}\": the game's are ${FlatPreset.entries.joinToString { "\"${it.title}\"" }}",
    )
    return copy(
        advertisedAddresses = settings.advertisedAddresses, operatorDomain = settings.operatorDomain, shutdownMessage = settings.shutdownMessage,
        flatWorldPreset = preset, features = settings.features, commandLimits = settings.commandLimits, logs = settings.logs,
    )
}

/** The settings in [defaults] that [saved] doesn't have, and those within one it has, as `setting.own`. */
private fun missingFrom(saved: JsonObject, defaults: JsonObject, prefix: String = ""): List<String> = defaults.flatMap { (key, default) ->
    val there = saved[key]
    when {
        there == null -> listOf(prefix + key)
        default is JsonObject && there is JsonObject -> missingFrom(there, default, "$prefix$key.")
        else -> emptyList()
    }
}

/** [saved] with what it's missing of [defaults], in the defaults' order, and anything else it has after. */
private fun filledIn(saved: JsonObject, defaults: JsonObject): JsonObject = JsonObject(
    defaults.mapValues { (key, default) ->
        val there = saved[key]
        if (default is JsonObject && there is JsonObject) filledIn(there, default) else there ?: default
    } + saved.filterKeys { it !in defaults },
)

/** [settings] as kore.jsonc has them: each on a line of its own, below what it's for, and those within one indented. */
private fun written(settings: JsonObject, prefix: String = "", indent: String = "    "): String =
    settings.entries.joinToString(",\n\n", prefix = "{\n", postfix = "\n${indent.dropLast(4)}}") { (key, value) ->
        val comment = COMMENTS[prefix + key]?.lines()?.joinToString("") { "$indent// $it\n" }.orEmpty()
        val shown = if (value is JsonObject) written(value, "$prefix$key.", "$indent    ") else Json.encodeToString(JsonElement.serializer(), value)
        "$comment$indent${JsonPrimitive(key)}: $shown"
    } + if (prefix.isEmpty()) "\n" else ""
