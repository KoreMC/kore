package org.koremc

import org.koremc.network.JoinInfo
import org.koremc.protocol.Difficulty
import org.koremc.protocol.GameMode
import org.koremc.storage.FlatPreset
import org.koremc.storage.LevelType
import org.koremc.world.CommandLimits
import org.koremc.world.Features
import java.nio.file.Path

/**
 * The server's settings, as server.properties and kore.jsonc give them (see
 * [loadServerConfig] and [withKoreSettings]). The defaults are what new files
 * start with.
 */
data class ServerConfig(
    /** TCP port for signaling and UDP port for gameplay. */
    val port: Int = 19132,
    /** The port the server answers the server list's pings on, besides [port], for players who reach it by an IPv6 address. */
    val portV6: Int = 19133,
    /**
     * Whether players must prove who they are through Xbox Live. Turn this
     * off only for local testing: offline mode admits anyone as anyone.
     */
    val onlineMode: Boolean = true,
    /** Addresses to advertise as ICE candidates. Empty means every routable local address. */
    val advertisedAddresses: List<String> = emptyList(),
    /** Where the operator identity key lives. Clients pin it, so keep it safe and don't regenerate it. */
    val operatorKeyFile: Path = Path.of("operator.key"),
    /** Shown to players as untrusted text when they first accept this server's key. */
    val operatorDomain: String = "localhost",
    /** What players still on the server are told as it stops, which the game has no words of its own for. */
    val shutdownMessage: String = "Server closed",
    /** Where the game's item and block data lives: a checkout of https://github.com/KoreMC/data matching [version]. */
    val dataDirectory: Path = Path.of("data"),
    val name: String = "Kore Server",
    /** The world's name, and the folder it's saved in, under [worldsDirectory]. */
    val level: String = "Bedrock level",
    /** What a new world is like. A world made before keeps its own. */
    val levelType: LevelType = LevelType.FLAT,
    /** The preset a new world is made from, if [levelType] makes it flat. A world made before keeps its own layers. */
    val flatWorldPreset: FlatPreset = FlatPreset.CLASSIC_FLAT,
    /** Where worlds are saved, each in a folder of its own, as the game's dedicated server keeps them. */
    val worldsDirectory: Path = Path.of("worlds"),
    /** The most players on the server at once. Anyone else who tries to join is told it's full. */
    val maxPlayers: Int = 10,
    /** Whether games on the local network find the server, as they find each other's worlds. */
    val lanVisibility: Boolean = true,
    /** The game mode new players start in, and a new world's. */
    val gameMode: GameMode = GameMode.SURVIVAL,
    /** Whether [gameMode] applies to a world made before, rather than the game mode it keeps. */
    val forceGameMode: Boolean = false,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /**
     * Whether commands that change the game, like `/gamemode` and `/give`,
     * work. Only operators may run those anyway, and the console runs them
     * either way.
     */
    val allowCheats: Boolean = true,
    /** Where the operators are kept, as the game's dedicated server keeps them, by XUID. */
    val permissionsFile: Path = Path.of("permissions.json"),
    /** The seed of a new world, or null for a random one. A world made before keeps its own. */
    val seed: Long? = null,
    /** The most chunks around each player Kore will send, whatever the client asks for. */
    val viewDistance: Int = 8,
    /** The game's systems Kore runs, all of them unless kore.jsonc switches some off. */
    val features: Features = Features(),
    /** The most blocks a command changes at once: the game's own limits, unless kore.jsonc changes them. */
    val commandLimits: CommandLimits = CommandLimits(),
    /** What the logs keep: what players say and the commands they run, and detail in the console, as kore.jsonc says. */
    val logs: LogSettings = LogSettings(),
    // Must match the client's protocol number and version string.
    val protocol: Int = 2193,
    val version: String = "1.26.50",
)

fun ServerConfig.toJoinInfo(onlinePlayers: Int, maxPlayers: Int = this.maxPlayers) = JoinInfo(
    name = name,
    protocol = protocol,
    version = version,
    level = level,
    players = onlinePlayers,
    maxPlayers = maxPlayers,
    gameType = gameMode.id,
)
