package org.koremc

import org.koremc.storage.FlatPreset
import org.koremc.world.CommandLimits
import org.koremc.world.Features
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KoreSettingsTest {
    private val directory = createTempDirectory("kore-settings")
    private val file = directory.resolve("kore.jsonc")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `with no kore jsonc, a new file has Kore's own settings at their defaults, each explained, and reads back the same`() {
        assertEquals(ServerConfig(), ServerConfig().withKoreSettings(file))

        assertEquals(
            """
            {
                // The addresses players reach the server at: IP addresses or host names.
                // Left empty, every address this computer has that others can reach.
                "advertisedAddresses": [],

                // Shown to players, as text the server can't vouch for, the first time they accept its key.
                "operatorDomain": "localhost",

                // What players still on the server are told as it stops.
                "shutdownMessage": "Server closed",

                // The preset a new flat world is made from, named as the game names it: "Classic Flat", "Tunneller's Dream",
                // "Water World", "Overworld", "Snowy Kingdom", "Bottomless Pit", "Desert", "Redstone Ready" or "The Void".
                // Only with level-type FLAT in server.properties, and only as the world is made: one made before keeps its layers.
                "flatWorldPreset": "Classic Flat",

                // The game's systems Kore runs: all on, the game as it is, and all off, blocks that are only blocks.
                // Off, a system doesn't run at all, and its blocks do nothing. The game rules switch off the rest,
                // like the weather with doweathercycle, or blocks' drops with dotiledrops.
                "features": {
                    // Power, and all it works: dust, torches, repeaters, comparators, observers, lamps, pistons,
                    // pressure plates and daylight detectors, and the doors, note blocks, dispensers and hoppers it moves.
                    // Off, levers and buttons still flip, but power goes nowhere.
                    "redstone": true,

                    // Water and lava flowing, what they make where they meet, and concrete powder hardening in water.
                    // Off, they stay as they're put.
                    "liquids": true,

                    // Sand, gravel, anvils and the like falling with nothing under them.
                    // Off, they stay where they are, and hanging dripstone and snow break instead.
                    "fallingBlocks": true,

                    // Random ticks, bone meal, trampling farmland, and snow and ice forming: whatever grows, spreads, dries, decays, melts, freezes or weathers.
                    "growth": true,

                    // Furnaces, brewing stands, hoppers, dispensers, droppers, crafters and beacons working.
                    // Off, they keep what's put in them, and do nothing with it.
                    "machines": true,

                    // Players being hurt: by falling, the void, lava, fire and burning, drowning, suffocating, magma, campfires,
                    // cactus and berry bushes, poison and wither, and healing again over time. Off, nothing hurts them but /kill.
                    "damage": true,

                    // The food bar going down as players sprint, swim, jump, mine and are hurt, and starving them once it's empty.
                    // Off, it stays full.
                    "hunger": true,

                    // Eating and drinking: food and cake filling the food bar, and what food, potions and milk give, like a golden apple's effects.
                    // Off, they're only items, and cake only a block.
                    "food": true,

                    // Players hitting each other: damage by weapon, enchantments and effects, critical hits, knockback and Fire Aspect.
                    // Off, a hit does nothing. The pvp game rule turns it off too, for a while.
                    "combat": true,

                    // Throwing snowballs, eggs and ender pearls, which hit players and knock them back, and an ender pearl taking its thrower
                    // where it lands. Off, they're only items.
                    "projectiles": true
                },

                // The most blocks a command changes at once: a number, true for the game's own limit, or false for none.
                // Past the game's own, a big fill or clone can hold the server up a moment as it works, and players' games as they take it in.
                "commandLimits": {
                    // The most blocks /fill fills. The game's own limit is 32768.
                    "fill": 32768,

                    // The most blocks /clone copies. The game's own limit is 655360.
                    "clone": 655360
                },

                // What the console and log file keep. Off, what players say, or the commands they run, aren't logged at all,
                // to keep the logs short.
                "logs": {
                    // Detail for working out what went wrong, DBG, in the console too, but for every packet. The log file always has it.
                    "debug": false,

                    // What players say in the chat, CHT.
                    "chat": true,

                    // The commands players run, CMD.
                    "commands": true
                }
            }

            """.trimIndent(),
            file.readText(),
        )
        assertEquals(ServerConfig(), ServerConfig().withKoreSettings(file))
    }

    @Test
    fun `a flat world preset is read by the game's name for it, and one the game doesn't have is refused, naming those it has`() {
        file.writeText("""{ "flatWorldPreset": "Tunneller's Dream" }""")
        assertEquals(FlatPreset.TUNNELLERS_DREAM, ServerConfig().withKoreSettings(file).flatWorldPreset)

        file.writeText("""{ "flatWorldPreset": "the void" }""")
        val refused = assertFailsWith<IllegalStateException> { ServerConfig().withKoreSettings(file) }
        assertTrue("\"the void\"" in refused.message!! && "\"The Void\"" in refused.message!!, refused.message)
    }

    @Test
    fun `a command limit is a number, true for the game's own, or false for none, which stays false in the file`() {
        file.writeText("""{ "commandLimits": { "fill": 400000 } }""")
        assertEquals(CommandLimits(fill = 400000, clone = 655360), ServerConfig().withKoreSettings(file).commandLimits)

        file.writeText("""{ "commandLimits": { "fill": false, "clone": true } }""")
        assertEquals(CommandLimits(fill = null, clone = 655360), ServerConfig().withKoreSettings(file).commandLimits)
        assertTrue("\"fill\": false" in file.readText())

        for (refused in listOf("0", "-5", "\"lots\"", "1.5")) {
            file.writeText("""{ "commandLimits": { "fill": $refused } }""")
            assertFailsWith<IllegalStateException>(refused) { ServerConfig().withKoreSettings(file) }
        }
    }

    @Test
    fun `with no command limit, the file says so with false`() {
        assertEquals(CommandLimits(fill = null, clone = 655360), ServerConfig(commandLimits = CommandLimits(fill = null)).withKoreSettings(file).commandLimits)
        assertTrue("\"fill\": false" in file.readText(), file.readText())
    }

    @Test
    fun `the logs can show detail in the console, and leave out chat and commands`() {
        file.writeText("""{ "logs": { "debug": true, "chat": false } }""")
        assertEquals(LogSettings(debug = true, chat = false, commands = true), ServerConfig().withKoreSettings(file).logs)
        assertTrue("\"commands\": true" in file.readText())
    }

    @Test
    fun `systems switched off in the features are read, and switches the file lacks are added on`() {
        file.writeText("""{ "features": { "redstone": false, "growth": false } }""")

        val features = ServerConfig().withKoreSettings(file).features

        assertEquals(Features(redstone = false, growth = false), features)
        assertEquals(listOf("redstone", "growth"), features.off)
        val written = file.readText()
        assertTrue("\"redstone\": false" in written && "\"liquids\": true" in written && "\"machines\": true" in written, written)
        assertEquals(features, ServerConfig().withKoreSettings(file).features)
    }

    @Test
    fun `Kore's own settings are read from the file, with comments of any kind and a comma after the last`() {
        file.writeText(
            """
            {
                // Where players find us
                "advertisedAddresses": ["203.0.113.7", "play.example.com"],
                /* Ours */ "operatorDomain": "example.com",
                "shutdownMessage": "Back soon!", // Restarting nightly
            }
            """.trimIndent(),
        )

        val config = ServerConfig().withKoreSettings(file)

        assertEquals(listOf("203.0.113.7", "play.example.com"), config.advertisedAddresses)
        assertEquals("example.com", config.operatorDomain)
        assertEquals("Back soon!", config.shutdownMessage)
    }

    @Test
    fun `a file without some settings has them added at their defaults, keeping what it had`() {
        file.writeText("""{ "someoneElses": 1, "operatorDomain": "example.com" }""")

        val config = ServerConfig().withKoreSettings(file)

        assertEquals("example.com", config.operatorDomain)
        val written = file.readText()
        assertTrue("\"shutdownMessage\": \"Server closed\"" in written && "// What players still on the server" in written, written)
        assertTrue(written.indexOf("advertisedAddresses") < written.indexOf("operatorDomain"), "Kore's in its own order")
        assertTrue(written.indexOf("operatorDomain") < written.indexOf("\"someoneElses\": 1"), "then the rest")
    }

    @Test
    fun `an older Kore's kore json is renamed kore jsonc, unless there's one already`() {
        val old = directory.resolve("kore.json")
        old.writeText("""{ "operatorDomain": "example.com" }""")
        renameOldKoreSettings(old, file)
        assertEquals("example.com", ServerConfig().withKoreSettings(file).operatorDomain)
        assertTrue(!old.exists())

        old.writeText("""{ "operatorDomain": "elsewhere.com" }""")
        renameOldKoreSettings(old, file)
        assertEquals("example.com", ServerConfig().withKoreSettings(file).operatorDomain, "the new one stays")
    }

    @Test
    fun `a file Kore can't read stops it, saying why`() {
        file.writeText("""{ "operatorDomain": ["not", "text"] }""")
        assertFailsWith<IllegalStateException> { ServerConfig().withKoreSettings(file) }

        file.writeText("not JSON")
        assertFailsWith<IllegalStateException> { ServerConfig().withKoreSettings(file) }
    }
}
