package org.koremc.world

/**
 * The game's rules, by the names commands write them, with what each is
 * in a new world: on or off, or a number.
 */
val GAME_RULE_DEFAULTS: Map<String, Any> = linkedMapOf(
    "commandBlockOutput" to true, "commandBlocksEnabled" to true, "doDaylightCycle" to true, "doEntityDrops" to true,
    "doFireTick" to true, "doImmediateRespawn" to false, "doInsomnia" to true, "doLimitedCrafting" to false, "doMobLoot" to true,
    "doMobSpawning" to true, "doTileDrops" to true, "doWeatherCycle" to true, "drowningDamage" to true, "fallDamage" to true,
    "fireDamage" to true, "freezeDamage" to true, "functionCommandLimit" to 10000, "keepInventory" to false,
    "maxCommandChainLength" to 65535, "mobGriefing" to true, "naturalRegeneration" to true, "playersSleepingPercentage" to 100,
    "projectilesCanBreakBlocks" to true, "pvp" to true, "randomTickSpeed" to 1, "recipesUnlock" to true,
    "respawnBlocksExplode" to true, "sendCommandFeedback" to true, "showBorderEffect" to true, "showCoordinates" to false,
    "showDaysPlayed" to false, "showDeathMessages" to true, "showRecipeMessages" to true, "showTags" to true, "spawnRadius" to 5,
    "tntExplodes" to true, "tntExplosionDropDecay" to false,
)

/**
 * The rules that do something in Kore so far, which `/gamerule` offers:
 * those it runs, and those the client sees to itself, like showing
 * coordinates, or respawning straight away, which its death screen does
 * by itself. The rest are still kept with the world and sent to clients,
 * as the game does, so a world keeps its settings either way.
 */
val GAME_RULES_IN_USE = setOf(
    "doDaylightCycle", "doFireTick", "doImmediateRespawn", "doTileDrops", "doWeatherCycle", "drowningDamage", "fallDamage", "fireDamage",
    "keepInventory", "naturalRegeneration", "pvp", "randomTickSpeed", "showCoordinates", "showDaysPlayed", "showDeathMessages",
)

/**
 * A world's game rules: each on or off, or a number, by its name, which the
 * game keeps in lower case, as it saves them and sends them to clients, and
 * commands write as [GAME_RULE_DEFAULTS] does. They start as [saved] has
 * them, and at their defaults otherwise.
 */
class GameRules(saved: Map<String, Any> = emptyMap()) {
    private val values = LinkedHashMap<String, Any>()

    init {
        for ((name, default) in GAME_RULE_DEFAULTS) {
            val key = name.lowercase()
            values[key] = saved[key]?.takeIf { it::class == default::class } ?: default
        }
    }

    /** Every rule, by the name the game keeps it under, with its value. */
    val all: Map<String, Any> get() = values

    /** The value of the rule called [name], however it's written, or null for no such rule. */
    operator fun get(name: String): Any? = values[name.lowercase()]

    /**
     * Sets the rule called [name] to [value], which must be of its kind.
     * Returns whether it's a rule that took the value.
     */
    fun set(name: String, value: Any): Boolean {
        val key = name.lowercase()
        if (values[key]?.let { it::class == value::class } != true) return false
        values[key] = value
        return true
    }

    /** Whether time passes, day turning to night and back. */
    val doDaylightCycle get() = values["dodaylightcycle"] == true

    /** Whether the weather changes by itself. */
    val doWeatherCycle get() = values["doweathercycle"] == true

    /** Whether broken blocks drop what they drop, and ores their experience. What a container held drops either way. */
    val doTileDrops get() = values["dotiledrops"] == true

    /** Whether fire spreads, burns blocks away and burns out, and lightning and lava start it. Off, fire stays as it's lit. */
    val doFireTick get() = values["dofiretick"] == true

    /** Whether players keep what they have, and their experience, when they die. */
    val keepInventory get() = values["keepinventory"] == true

    /** Whether everyone hears how a player died. */
    val showDeathMessages get() = values["showdeathmessages"] == true

    /** Whether players are hurt by falling, by fire, lava and burning, and by running out of breath underwater. */
    val fallDamage get() = values["falldamage"] == true
    val fireDamage get() = values["firedamage"] == true
    val drowningDamage get() = values["drowningdamage"] == true

    /** Whether players can hurt each other. */
    val pvp get() = values["pvp"] == true

    /** Whether players heal over time by themselves. */
    val naturalRegeneration get() = values["naturalregeneration"] == true

    /** How many blocks in each sub-chunk around the players get a random tick each tick: what grows, how fast. */
    val randomTickSpeed get() = values["randomtickspeed"] as? Int ?: 1
}
