package org.koremc.command

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.Encoder
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.koremc.PACKET_LOG
import org.koremc.protocol.CommandMessage
import org.koremc.protocol.CommandPermission
import org.koremc.world.Vec3
import org.slf4j.LoggerFactory
import java.io.IOError

private val log = LoggerFactory.getLogger("Console")

/**
 * The server console, which runs commands as the server's owner, from the
 * world's spawn. What it types goes to every command as `Server`.
 */
class ConsoleSource(override val origin: Vec3) : CommandSource {
    override val name get() = "Server"
    override val permission get() = CommandPermission.OWNER
    override val yaw get() = 0f
    override val pitch get() = 0f
}

/** What the console shows before what's typed at it, and the command that stops the server. */
private const val PROMPT = "> "
private const val STOP = "stop"

/**
 * Reads commands typed at the server's console, a line each, and hands
 * each to [run]: in a terminal, on a line of their own below what the
 * server logs, with the commands typed before a press of the up arrow
 * away, and otherwise, as from a pipe, as they come. Ctrl+C stops the
 * server, as `stop` does. It reads on a thread of its own, which ends
 * with the input, and doesn't keep the server running.
 */
class Console(private val run: (String) -> Unit) {
    // Without a terminal, as in a pipe or an IDE, the lines are read as they come, which is no reason to warn.
    private val terminal: Terminal = TerminalBuilder.builder().system(true).dumb(true).build()
    private val reader: LineReader = LineReaderBuilder.builder().terminal(terminal).build()

    fun start() {
        val thread = Thread({
            ConsoleLogAppender.reader = reader
            while (true) {
                val line = try {
                    // Without a terminal, a prompt would only run into the lines logged after it.
                    reader.readLine(if (terminal.type.startsWith(Terminal.TYPE_DUMB)) "" else PROMPT)
                } catch (e: UserInterruptException) {
                    run(STOP)
                    break
                } catch (e: EndOfFileException) {
                    break
                } catch (e: IOError) {
                    // The terminal closing as the server stops.
                    break
                }
                if (line.isNotBlank()) run(line)
                // Stopping, there's nothing more to read, and no prompt to leave behind.
                if (line.trim().removePrefix("/").equals(STOP, ignoreCase = true)) break
            }
            log.debug("The console's input ended")
        }, "kore-console")
        thread.isDaemon = true
        thread.start()
    }

    /** Puts the terminal back as it was, the server's last lines written out as they come. */
    fun close() {
        ConsoleLogAppender.reader = null
        runCatching { terminal.close() }
    }
}

/**
 * Writes the log's lines for the console above the line being typed at it,
 * which goes back below them as it was, so that what the server logs never
 * lands in the middle of a command. Without a terminal to keep them apart,
 * or before the console reads, it writes them out as they come. Detail it
 * leaves to the log file, unless kore.jsonc asks for it, and every packet
 * it leaves there either way.
 */
class ConsoleLogAppender : AppenderBase<ILoggingEvent>() {
    /** Turns each event into its line, as logback.xml has it. */
    var encoder: Encoder<ILoggingEvent>? = null

    override fun append(event: ILoggingEvent) {
        // Detail, only when kore.jsonc asks for it, and never every packet, which would bury the rest.
        if (!event.level.isGreaterOrEqual(Level.INFO) && (!showDebug || event.loggerName == PACKET_LOG)) return
        val text = String(encoder?.encode(event) ?: return, Charsets.UTF_8)
        val reading = reader?.takeUnless { it.terminal.type.startsWith(Terminal.TYPE_DUMB) }
        if (reading == null || runCatching { reading.printAbove(text.trimEnd('\r', '\n')) }.isFailure) {
            print(text)
            System.out.flush()
        }
    }

    companion object {
        /** What reads the console, while it does. */
        @Volatile
        var reader: LineReader? = null

        /** Whether the console shows detail for working out what went wrong, as kore.jsonc says. */
        @Volatile
        var showDebug = false
    }
}

/**
 * The English of the messages commands send, for the console, which has
 * no client to look them up in its language. Each is as the game words
 * it, with its parameters in the game's `%s` and `%1$s` style.
 */
private val ENGLISH = mapOf(
    "commands.generic.unknown" to "Unknown command: %s. Please check that the command exists and that you have permission to use it.",
    "commands.generic.disabled" to "Cheats aren't enabled in this world.",
    "commands.generic.syntax" to "Syntax error: Unexpected \"%2\$s\": at \"%1\$s>>%2\$s<<%3\$s\"",
    "commands.generic.noTargetMatch" to "No targets matched selector",
    "commands.generic.tooManyTargets" to "Too many targets matched selector",
    "commands.generic.num.tooBig" to "The number you have entered (%1\$d) is too big, it must be at most %2\$d",
    "commands.generic.num.tooSmall" to "The number you have entered (%1\$d) is too small, it must be at least %2\$d",
    "commands.gamemode.success.self" to "Set own game mode to %1\$s",
    "commands.gamemode.success.other" to "Set %2\$s's game mode to %1\$s",
    "commands.gamemode.fail.invalid" to "Game mode '%1\$s' is invalid",
    "gameMode.survival" to "Survival Mode",
    "gameMode.creative" to "Creative Mode",
    "gameMode.adventure" to "Adventure Mode",
    "gameMode.spectator" to "Spectator Mode",
    "commands.give.success" to "Gave %1\$s * %2\$d to %3\$s",
    "commands.give.item.notFound" to "There is no such item with name %1\$d",
    "commands.clear.success" to "Cleared the inventory of %1\$s, removing %2\$d items",
    "commands.clear.failure.no.items" to "Could not clear the inventory of %1\$s, no items to remove",
    "commands.clear.testing" to "%1\$s has %2\$d items that match the criteria",
    "commands.tp.success" to "Teleported %1\$s to %2\$s",
    "commands.tp.success.coordinates" to "Teleported %1\$s to %2\$s, %3\$s, %4\$s",
    "commands.tp.safeTeleportFail" to "Unable to teleport %1\$s to %2\$s because the area wasn't clear of blocks.",
    "commands.tp.outOfWorld" to "Cannot teleport entities outside of the world",
    "commands.xp.success" to "Gave %1\$d experience to %2\$s",
    "commands.xp.success.levels" to "Gave %1\$d levels to %2\$s",
    "commands.xp.success.negative.levels" to "Taken %1\$d levels from %2\$s",
    "commands.xp.failure.widthdrawXp" to "Cannot give player negative experience points",
    "commands.enchant.success" to "Enchanting succeeded for %1\$s",
    "commands.enchant.noItem" to "The target doesn't hold an item: %1\$s",
    "commands.enchant.notFound" to "There is no such enchantment with ID %1\$d",
    "commands.enchant.invalidLevel" to "%1\$s does not support level %2\$d",
    "commands.enchant.cantEnchant" to "The selected enchantment can't be added to the target item: %1\$s",
    "commands.enchant.cantCombine" to "%1\$s can't be combined with %2\$s",
    "commands.message.display.outgoing" to "You whisper to %1\$s: %2\$s",
    "commands.message.sameTarget" to "You can't send a private message to yourself!",
    "commands.players.list" to "There are %1\$d/%2\$d players online:",
    "commands.players.list.names" to "%s",
    "commands.help.header" to "--- Showing help page %1\$d of %2\$d (/help <page>) ---",
    "commands.help.footer" to "Tip: Use the <tab> key while typing a command to auto-complete the command or its arguments",
    "commands.help.command.aliases" to "%s (also %s):",
    "commands.generic.usage.noparam" to "Usage:",
    "commands.help.description" to "Provides help/list of commands.",
    "commands.list.description" to "Lists players on the server.",
    "commands.me.description" to "Displays a message about yourself.",
    "commands.tell.description" to "Sends a private message to one or more players.",
    "commands.say.description" to "Sends a message in the chat to other players.",
    "commands.gamemode.description" to "Sets a player's game mode.",
    "commands.give.description" to "Gives an item to a player.",
    "commands.clear.description" to "Clears items from player inventory.",
    "commands.tp.description" to "Teleports entities (players, mobs, etc.).",
    "commands.xp.description" to "Adds or removes player experience.",
    "commands.enchant.description" to "Adds an enchantment to a player's selected item.",
    "commands.kick.description" to "Kicks a player from the server.",
    "commands.op.description" to "Grants operator status to a player.",
    "commands.deop.description" to "Revokes operator status from a player.",
    "commands.stop.description" to "Stops the server.",
    "commands.op.success" to "Opped: %s",
    "commands.op.failed" to "Could not op (already op or higher): %s",
    "commands.deop.success" to "De-opped: %s",
    "commands.deop.failed" to "Could not de-op (permission level too high): %s",
    "commands.kick.success" to "Kicked %1\$s from the game",
    "commands.kick.success.reason" to "Kicked %1\$s from the game: '%2\$s'",
    "commands.kick.not.found" to "Could not find player %1\$s",
    "commands.kick.not.yourself" to "You may not remove yourself from the game",
    "commands.time.description" to "Changes or queries the world's game time.",
    "commands.time.set" to "Set the time to %1\$d",
    "commands.time.added" to "Added %1\$d to the time",
    "commands.time.query.daytime" to "Daytime is %d",
    "commands.time.query.gametime" to "Gametime is %d",
    "commands.time.query.day" to "Day is %d",
    "commands.weather.description" to "Sets the weather.",
    "commands.weather.clear" to "Changing to clear weather",
    "commands.weather.rain" to "Changing to rainy weather",
    "commands.weather.thunder" to "Changing to rain and thunder",
    "commands.weather.query" to "Weather state is: %s",
    "commands.weather.query.clear" to "clear",
    "commands.weather.query.rain" to "rain",
    "commands.weather.query.thunder" to "thunder",
    "commands.toggledownfall.description" to "Toggles the weather.",
    "commands.downfall.success" to "Toggled downfall",
    "commands.daylock.description" to "Locks and unlocks the day-night cycle.",
    "commands.always.day.locked" to "Day-Night cycle locked",
    "commands.always.day.unlocked" to "Day-Night cycle unlocked",
    "commands.gamerule.description" to "Sets or queries a game rule value.",
    "commands.gamerule.success" to "Game rule %1\$s has been updated to %2\$s",
    "commands.stop.start" to "Stopping the server",
    "commands.kill.description" to "Kills entities (players, mobs, etc.).",
    "commands.kill.successful" to "Killed %1\$s",
    "commands.kill.attemptKillPlayerCreative" to "Players cannot be killed while they are in Creative mode.",
    "commands.setblock.description" to "Changes a block to another block.",
    "commands.setblock.success" to "Block placed",
    "commands.setblock.noChange" to "The block couldn't be placed",
    "commands.setblock.outOfWorld" to "Cannot place block outside of the world",
    "commands.setblock.notFound" to "There is no such block with ID/name %1\$s",
    "commands.fill.description" to "Fills all or parts of a region with a specific block.",
    "commands.fill.success" to "%1\$d blocks filled",
    "commands.fill.failed" to "No blocks filled",
    "commands.fill.outOfWorld" to "Cannot place blocks outside of the world",
    "commands.fill.tooManyBlocks" to "Too many blocks in the specified area (%1\$d > %2\$d)",
    "commands.clone.description" to "Clones blocks from one region to another.",
    "commands.clone.success" to "%1\$d blocks cloned",
    "commands.clone.failed" to "No blocks cloned",
    "commands.clone.noOverlap" to "Source and destination can not overlap",
    "commands.clone.tooManyBlocks" to "Too many blocks in the specified area (%1\$d > %2\$d)",
    "commands.generic.outOfWorld" to "Cannot access blocks outside of the world",
    "commands.testforblock.description" to "Tests whether a certain block is in a specific location.",
    "commands.testforblock.success" to "Successfully found the block at %1\$d,%2\$d,%3\$d.",
    "commands.testforblock.failed.tile" to "The block at %1\$d,%2\$d,%3\$d is %4\$s (expected: %5\$s).",
    "commands.testforblock.failed.data" to "The block at %1\$d,%2\$d,%3\$d did not match the expected block state.",
    "commands.testforblock.outOfWorld" to "Cannot test for block outside of the world",
    "commands.blockstate.stateError" to "Block State: '%1\$s' is invalid on Block: '%2\$s'.",
    "commands.blockstate.typeError" to "Invalid value type for Block State: '%1\$s'.",
    "commands.blockstate.valueError" to "Invalid value for Block State: '%1\$s'.",
    "commands.generic.parameter.invalid" to "'%1\$s' is not a valid parameter",
    "commands.effect.description" to "Add or remove status effects.",
    "commands.effect.success" to "Gave %1\$s * %2\$d to %3\$s for %4\$d seconds",
    "commands.effect.success.infinite" to "Gave %1\$s * %2\$d to %3\$s for an infinite duration",
    "commands.effect.success.removed" to "Took %1\$s from %2\$s",
    "commands.effect.success.removed.all" to "Took all effects from %1\$s",
    "commands.effect.failure.notActive" to "Couldn't take %1\$s from %2\$s as they do not have the effect",
    "commands.effect.failure.notActive.all" to "Couldn't take any effects from %1\$s as they do not have any",
    "commands.effect.failure.notAMob" to "%1\$s cannot have effects",
    "commands.difficulty.description" to "Sets the difficulty level.",
    "commands.difficulty.success" to "Set game difficulty to %1\$s",
    "options.difficulty.peaceful" to "Peaceful",
    "options.difficulty.easy" to "Easy",
    "options.difficulty.normal" to "Normal",
    "options.difficulty.hard" to "Hard",
    "commands.setworldspawn.description" to "Sets the world spawn.",
    "commands.setworldspawn.success" to "Set the world spawn point to (%1\$d, %2\$d, %3\$d)",
    "commands.spawnpoint.description" to "Sets the spawn point for a player.",
    "commands.spawnpoint.success.single" to "Set %1\$s's spawn point to (%2\$d, %3\$d, %4\$d)",
    "commands.spawnpoint.success.multiple.specific" to "Set spawn point for %1\$s to (%2\$d, %3\$d, %4\$d)",
    "commands.spawnpoint.success.multiple.generic" to "Set spawn point for %1\$s",
    "commands.clearspawnpoint.description" to "Removes the spawn point for a player.",
    "commands.clearspawnpoint.success.single" to "Removed %1\$s's spawn point",
    "commands.clearspawnpoint.success.multiple" to "Removed spawn points for %1\$s",
    "commands.title.description" to "Controls screen titles.",
    "commands.title.success" to "Title command successfully executed",
    "commands.titleraw.description" to "Controls screen titles with JSON messages.",
    "commands.titleraw.success" to "Titleraw command successfully executed",
    "commands.tellraw.description" to "Sends a JSON message to players.",
    "commands.tellraw.jsonStringException" to "Invalid json string data.",
    "commands.tellraw.error.noData" to "No data was provided.",
    "commands.tellraw.error.notArray" to "Rawtext object must contain an array. Example: \"rawtext\":[{..}]",
    "commands.tellraw.error.textNotString" to "text field in rawtext must contain a string. Example: \"rawtext\":[{\"text\": \"text to display\"}]",
    "commands.tellraw.error.translateNotString" to "translate field in rawtext must contain a language key. Example: \"rawtext\":[{\"translate\": \"gui.ok\"}]",
    "commands.tellraw.error.selectorNotString" to "selector field in rawtext must contain a selector as a string. Example: \"rawtext\":[{\"selector\": \"@e\"}]",
    "commands.tellraw.error.scoreNotObject" to "score field in rawtext must contain an object.",
    "commands.tellraw.error.scoreNameNotString" to "name field in the score field must be a string.",
    "commands.tellraw.error.scoreObjectiveNotString" to "objective field in the score field must be a string.",
    "commands.tellraw.error.withNotArrayOrRawText" to "with field in rawtext must contain a array or another rawtext object.",
    "commands.tellraw.error.itemIsNotObject" to "Json value in rawtext array was not an object. Example: \"rawtext\": [{ \"text\" : \"my text\" }]",
    "commands.playsound.description" to "Plays a sound.",
    "commands.playsound.success" to "Played sound '%1\$s' to %2\$s",
    "commands.playsound.playerTooFar" to "Player %1\$s is too far away to hear the sound",
    "commands.stopsound.description" to "Stops a sound.",
    "commands.stopsound.success" to "Stopped sound '%1\$s'",
    "commands.stopsound.success.all" to "Stopped all sounds",
    "commands.particle.description" to "Creates particles.",
    "commands.particle.success" to "Playing effect %1\$s for %2\$d times",
    "commands.testfor.description" to "Counts entities (players, mobs, items, etc.) matching specified conditions.",
    "commands.testfor.success" to "Found %1\$s",
    "commands.testforblocks.description" to "Tests whether the blocks in two regions match.",
    "commands.compare.success" to "%1\$d blocks compared",
    "commands.compare.failed" to "Source and destination are not identical",
    "commands.compare.tooManyBlocks" to "Too many blocks in the specified area (%1\$d > %2\$d)",
    "commands.setmaxplayers.description" to "Sets the maximum number of players for this game session.",
    "commands.setmaxplayers.success" to "Set max players to %1\$d.",
    "commands.setmaxplayers.success.lowerbound" to "(Bound to current player count)",
    "potion.moveSpeed" to "Speed",
    "potion.moveSlowdown" to "Slowness",
    "potion.digSpeed" to "Haste",
    "potion.digSlowDown" to "Mining Fatigue",
    "potion.damageBoost" to "Strength",
    "potion.heal" to "Instant Health",
    "potion.harm" to "Instant Damage",
    "potion.jump" to "Jump Boost",
    "potion.confusion" to "Nausea",
    "potion.regeneration" to "Regeneration",
    "potion.resistance" to "Resistance",
    "potion.fireResistance" to "Fire Resistance",
    "potion.waterBreathing" to "Water Breathing",
    "potion.invisibility" to "Invisibility",
    "potion.blindness" to "Blindness",
    "potion.nightVision" to "Night Vision",
    "potion.hunger" to "Hunger",
    "potion.weakness" to "Weakness",
    "potion.poison" to "Poison",
    "potion.wither" to "Wither",
    "potion.healthBoost" to "Health Boost",
    "potion.absorption" to "Absorption",
    "potion.saturation" to "Saturation",
    "potion.levitation" to "Levitation",
    "potion.slowFalling" to "Slow Falling",
    "potion.conduitPower" to "Conduit Power",
    "potion.windCharged" to "Wind Charged",
    "effect.badOmen" to "Bad Omen",
    "effect.villageHero" to "Hero of the Village",
    "effect.darkness" to "Darkness",
    "effect.weaving" to "Weaving",
    "effect.oozing" to "Oozing",
    "effect.infested" to "Infested",
)

/** A parameter in a message, numbered or in turn, as a string or a number. */
private val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[sd]""")

/** A message's colours, then the key it looks up after a `%`, as in `§e%commands.help.header`. */
private val COLOURED_KEY = Regex("""((?:§.)*)%?(.*)""")

/**
 * [message] in English, its parameters filled in, and any that are keys
 * themselves looked up too. A key can come after colours and a `%`, as in
 * `§2%commands.help.header`. Text that isn't a key stays as it is.
 */
fun english(message: CommandMessage): String = english(message.message, message.parameters)

/** [message], a key of the game's or text, in English, with [parameters] filled in, as [english] does a command's. */
private fun english(message: String, parameters: List<String>): String {
    val (colours, key) = COLOURED_KEY.matchEntire(message)!!.destructured
    val template = ENGLISH[key] ?: return message
    var next = 0
    return PLACEHOLDER.replace(template) { match ->
        val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: next++
        val parameter = parameters.getOrNull(index) ?: return@replace ""
        if (parameter.startsWith('%')) english(parameter, emptyList()) else parameter
    }.let { colours + it }
}

/** Shows what a command the console ran came to, in its colours, which the console draws and the log file leaves out. */
fun logOutput(result: CommandResult) {
    for (message in result.messages) {
        val text = english(message)
        if (message.success) log.info(text) else log.warn(text)
    }
}
