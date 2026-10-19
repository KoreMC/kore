package org.koremc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.pattern.CompositeConverter
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** The logger for every packet batch in and out, which only the log file keeps. */
const val PACKET_LOG = "org.koremc.packets"

// The loggers for what players say and the commands they run, which the log tags apart from the rest.
private const val CHAT = "Chat"
private const val COMMANDS = "Commands"

val chatLog: Logger = LoggerFactory.getLogger(CHAT)
val commandLog: Logger = LoggerFactory.getLogger(COMMANDS)

/** Each tag's colour in the console, as a terminal's colour code: the time is grey, highlighted words yellow, and the rest the terminal's own. */
private val TAG_COLOURS = mapOf(
    "INF" to "94", "WRN" to "93", "ERR" to "1;91", "DBG" to "90",
    "CHT" to "92", "CMD" to "95",
)
private const val GREY = "90"
private const val YELLOW = "93"

/** Words in a message to highlight in the console, in backticks, as in "Type `help` for commands". */
private val HIGHLIGHTED = Regex("`([^`]+)`")

/**
 * The game's colours, by the code after a `§` that picks each, as Bedrock
 * draws them: Java's sixteen, then Bedrock's own for its materials, where
 * `§m` and `§n` are colours too, not strikethrough and underline.
 */
private val GAME_COLOURS = mapOf(
    '0' to 0x000000, '1' to 0x0000AA, '2' to 0x00AA00, '3' to 0x00AAAA, '4' to 0xAA0000, '5' to 0xAA00AA, '6' to 0xFFAA00, '7' to 0xAAAAAA,
    '8' to 0x555555, '9' to 0x5555FF, 'a' to 0x55FF55, 'b' to 0x55FFFF, 'c' to 0xFF5555, 'd' to 0xFF55FF, 'e' to 0xFFFF55, 'f' to 0xFFFFFF,
    'g' to 0xDDD605, 'h' to 0xE3D4D1, 'i' to 0xCECACA, 'j' to 0x443A3B, 'm' to 0x971607, 'n' to 0xB4684D, 'p' to 0xDEB12D,
    'q' to 0x47A036, 's' to 0x2CBAA8, 't' to 0x21497B, 'u' to 0x9A5CC6, 'v' to 0xEB7114,
)

// The game's codes for bold and italic text, and for going back to plain.
private const val GAME_BOLD = 'l'
private const val GAME_ITALIC = 'o'
private const val GAME_RESET = 'r'

/** A `§` and the code after it, which picks a colour or a style, like `§d` or `§l`. */
private val GAME_CODE = Regex("§(.?)")

/** The terminal's codes for bold and italic, and for going back to its own colours and style. */
private const val BOLD = "1"
private const val ITALIC = "3"
private const val RESET = "0"

/**
 * What a line of the log is, in three letters: an error, a warning, what a
 * player said, a command they ran, anything else that happened, or detail
 * for finding out what went wrong.
 */
fun tagOf(event: ILoggingEvent): String = when {
    event.level.isGreaterOrEqual(Level.ERROR) -> "ERR"
    event.level == Level.WARN -> "WRN"
    event.loggerName == CHAT -> "CHT"
    event.loggerName == COMMANDS -> "CMD"
    event.level == Level.INFO -> "INF"
    else -> "DBG"
}

/** [text] in the terminal's colour [code], and back to its own after. */
private fun coloured(code: String, text: String) = "\u001B[${code}m$text\u001B[0m"

/** The terminal's code for [style], like [BOLD], or a colour as 24-bit RGB. */
private fun ansi(style: String) = "\u001B[${style}m"

/**
 * [text] with the game's codes, like an item's name in `§d`, drawn in the
 * terminal's: each colour as the game has it, bold and italic as they are,
 * and `§r` back to plain. Codes a terminal has nothing for, like `§k` for
 * scrambled text, are left out. Anything coloured goes back to plain at
 * the end, so it doesn't run into the next line.
 */
fun gameColoured(text: String): String {
    if ('§' !in text) return text
    var styled = false
    val drawn = GAME_CODE.replace(text) { match ->
        val code = match.groupValues[1].lowercase().firstOrNull()
        val colour = GAME_COLOURS[code]
        when {
            colour != null -> ansi("38;2;${colour shr 16 and 0xFF};${colour shr 8 and 0xFF};${colour and 0xFF}")
            code == GAME_BOLD -> ansi(BOLD)
            code == GAME_ITALIC -> ansi(ITALIC)
            code == GAME_RESET -> ansi(RESET)
            else -> ""
        }.also { if (it.isNotEmpty() && code != GAME_RESET) styled = true }
    }
    return if (styled) drawn + ansi(RESET) else drawn
}

/** [text] without the game's colour and style codes, as the log file has it. */
fun gameUncoloured(text: String) = text.replace(GAME_CODE, "")

/** Whether [event] has what players typed in it, which is never highlighted, whatever backticks they typed. */
private fun typedByPlayers(event: ILoggingEvent) = event.loggerName == CHAT || event.loggerName == COMMANDS

/** A line's tag, as the log file has it: `%tag`. */
class TagConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String = tagOf(event)
}

/** A line's tag in its colour, as the console shows it: `%colouredTag`. */
class ColouredTagConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String = tagOf(event).let { coloured(TAG_COLOURS.getValue(it), it) }
}

/** What it wraps in grey, as the console shows a line's time: `%grey(...)`. */
class GreyConverter : CompositeConverter<ILoggingEvent>() {
    override fun transform(event: ILoggingEvent, text: String): String = coloured(GREY, text)
}

/**
 * What a line says, with the words in backticks in yellow, and the game's
 * colours, like a renamed sword's, as the console shows it:
 * `%highlightedMessage`.
 */
class HighlightedMessageConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String = gameColoured(
        if (typedByPlayers(event)) event.formattedMessage else HIGHLIGHTED.replace(event.formattedMessage) { coloured(YELLOW, it.groupValues[1]) },
    )
}

/**
 * What a line says, without the backticks that highlight words in the
 * console, or the game's colour codes, as the log file has it:
 * `%plainMessage`.
 */
class PlainMessageConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String = gameUncoloured(
        if (typedByPlayers(event)) event.formattedMessage else HIGHLIGHTED.replace(event.formattedMessage) { it.groupValues[1] },
    )
}

/**
 * What the logs keep, as kore.json has it: detail for working out what
 * went wrong in the console too, as the log file always has it, and what
 * players say and the commands they run, in both, or neither, to keep the
 * logs short.
 */
@Serializable
data class LogSettings(val debug: Boolean = false, val chat: Boolean = true, val commands: Boolean = true)
