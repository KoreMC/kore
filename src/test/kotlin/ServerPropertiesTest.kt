package org.koremc

import org.koremc.protocol.Difficulty
import org.koremc.protocol.GameMode
import org.koremc.storage.LevelType
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerPropertiesTest {
    private val directory = createTempDirectory("kore-properties")
    private val file = directory.resolve("server.properties")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `with no server properties, a new file has every setting at its default, explained, and reads back the same`() {
        assertEquals(ServerConfig(), loadServerConfig(file))

        val written = file.readText()
        assertTrue(written.startsWith("server-name=Kore Server\n# The server's name, as players see it.\n# Allowed values: "), written)
        assertTrue("\n\nlevel-name=Bedrock level\n#" in written, "the dedicated server's name for a world")
        assertTrue("\n\nlevel-seed=\n#" in written, "a random seed, by default")
        assertTrue("advertised-addresses" !in written && "operator-domain" !in written, "Kore's own settings are in kore.jsonc")
        assertEquals(ServerConfig(), loadServerConfig(file))
    }

    @Test
    fun `settings are read as the game's dedicated server writes them, in any case, and by name or number`() {
        file.writeText(
            """
            server-name=My Server
            # Comments are left alone.
            gamemode=Creative
            force-gamemode=true
            difficulty=3
            allow-cheats=false
            max-players=5
            online-mode=false
            server-port=19140
            view-distance=12
            level-name=My World
            level-seed=1234
            level-type=flat
            """.trimIndent(),
        )

        val expected = ServerConfig(
            name = "My Server", gameMode = GameMode.CREATIVE, forceGameMode = true, difficulty = Difficulty.HARD, allowCheats = false, maxPlayers = 5,
            onlineMode = false, port = 19140, viewDistance = 12, level = "My World", seed = 1234, levelType = LevelType.FLAT,
        )
        assertEquals(expected, loadServerConfig(file))
    }

    @Test
    fun `a value a setting can't take leaves it as it was, as do the dedicated server's settings Kore doesn't have yet`() {
        file.writeText(
            """
            gamemode=spectator
            max-players=0
            server-port=70000
            view-distance=2
            level-name=a/b
            allow-cheats=yes
            tick-distance=4
            player-idle-timeout=30
            """.trimIndent(),
        )

        assertEquals(ServerConfig(), loadServerConfig(file))
    }

    @Test
    fun `a seed in words becomes a number as in Java Edition, and an empty one is random`() {
        file.writeText("level-seed=kore\n")
        assertEquals("kore".hashCode().toLong(), loadServerConfig(file).seed)

        file.writeText("level-seed=\n")
        assertNull(loadServerConfig(file).seed)
    }
}
