package org.koremc

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.InternetProtocolFamily
import io.netty.channel.socket.nio.NioDatagramChannel
import org.koremc.auth.AuthService
import org.koremc.command.Console
import org.koremc.command.ConsoleLogAppender
import org.koremc.network.LAN_DISCOVERY_PORT
import org.koremc.network.LanDiscovery
import org.koremc.network.NetherNetRtcHandler
import org.koremc.network.NetherNetSignalingHandler
import org.koremc.network.RtcServer
import org.koremc.network.ServerListPing
import org.koremc.network.ServerListPingHandler
import org.koremc.network.SignalingServer
import org.koremc.network.rtc.DtlsCertificate
import org.koremc.network.rtc.DtlsContext
import org.koremc.network.rtc.LocalPeer
import org.koremc.network.rtc.OperatorIdentity
import org.koremc.network.rtc.Peer
import org.koremc.network.rtc.Peers
import org.koremc.network.rtc.SdpOffer
import org.koremc.network.rtc.hostCandidates
import org.koremc.network.rtc.localAddresses
import org.koremc.network.rtc.maxSendableMessageSize
import org.koremc.network.rtc.verifiedClientKey
import org.koremc.registry.ItemStacks
import org.koremc.registry.VanillaData
import org.koremc.storage.FlatPreset
import org.koremc.storage.LevelType
import org.koremc.storage.WorldSettings
import org.koremc.storage.WorldStorage
import org.slf4j.LoggerFactory
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.FileSystems
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.random.Random
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("Kore")

fun main() {
    val started = System.nanoTime()
    // A crash on any thread lands in the log file too, not just the console.
    Thread.setDefaultUncaughtExceptionHandler { thread, e -> log.error("Uncaught exception on {}", thread.name, e) }
    // The jar says which Kore it is; run from the build, there's no telling.
    val kore = listOfNotNull("Kore", Sessions::class.java.`package`?.implementationVersion).joinToString(" ")
    log.info("Starting {} for Minecraft {}", kore, ServerConfig().version)
    val config = try {
        renameOldKoreSettings(OLD_KORE_SETTINGS, KORE_SETTINGS)
        loadServerConfig(SERVER_PROPERTIES).withKoreSettings(KORE_SETTINGS)
    } catch (e: IllegalStateException) {
        log.error(e.message)
        exitProcess(1)
    }
    ConsoleLogAppender.showDebug = config.logs.debug
    val vanilla = try {
        VanillaData.load(gameDataIn(config.dataDirectory))
    } catch (e: NoSuchFileException) {
        log.error("Missing {}. Kore needs the game data for {} in {}: clone https://github.com/KoreMC/data there.", e.file, config.version, config.dataDirectory.toAbsolutePath())
        exitProcess(1)
    }
    log.debug(
        "Loaded {} items, {} block states, {} biomes, {} recipes and {} enchantments for {}",
        vanilla.items.size, vanilla.blocks.states.size, vanilla.biomes.size, vanilla.recipes.recipes.size, vanilla.enchantments.all.size, config.version,
    )

    val addresses = config.advertisedAddresses.map(InetAddress::getByName).ifEmpty { localAddresses() }
    val candidates = hostCandidates(config.port, addresses)
    if (candidates.isEmpty()) log.warn("No routable addresses found; clients will have nowhere to connect")
    candidates.forEach { log.debug("ICE candidate: {}", it) }

    val certificate = DtlsCertificate.generate()
    val localPeer = LocalPeer(
        certificate = certificate,
        identity = OperatorIdentity.loadOrCreate(config.operatorKeyFile, config.operatorDomain, issuer = config.name),
        candidates = candidates,
    )
    // Offline mode never contacts Minecraft's services, so it works without an internet connection.
    val auth = AuthService(config.version)
    if (config.onlineMode) auth.start() else log.warn("Offline mode: players aren't authenticated, so anyone can join as anyone")

    val peers = Peers()
    val items = ItemStacks(vanilla.items, vanilla.creative, vanilla.blocks)
    val settings = WorldSettings(
        config.gameMode, config.difficulty, config.allowCheats, config.seed ?: Random.nextLong(), config.version, config.protocol, config.forceGameMode, config.levelType,
    )
    // A flat world is made from its preset; any other kind, which Kore can't make yet, is made a classic flat one.
    val preset = if (config.levelType == LevelType.FLAT) config.flatWorldPreset else FlatPreset.CLASSIC_FLAT
    val storage = try {
        WorldStorage.open(config.worldsDirectory.resolve(config.level), config.level, preset.layers(vanilla.blocks), vanilla.blocks, items, settings, vanilla.traits, vanilla.biomes)
    } catch (e: Exception) {
        log.error("Couldn't open the world in {}", config.worldsDirectory.resolve(config.level).toAbsolutePath(), e)
        exitProcess(1)
    }
    // A world made before keeps its own game mode, unless force-gamemode says otherwise, and its own seed.
    val running = config.copy(gameMode = storage.settings.gameMode, seed = storage.settings.seed)
    storage.world.features = config.features
    storage.world.difficulty = config.difficulty
    config.features.off.takeIf { it.isNotEmpty() }?.let { log.info("Running without {}, as {} switches them off", it.joinToString(), KORE_SETTINGS) }
    // One thread carries every client's UDP traffic and runs every session, so the world needs no locks.
    val loop = NioEventLoopGroup(1)
    // Completes when the server should stop: when `/stop` runs, or the signaling server closes.
    val stopping = CompletableFuture<Unit>()
    // Built once, on start, so no player's login pays to encode the definitions.
    val game = GameData.of(vanilla, storage.world, items, players = storage, maxPlayers = config.maxPlayers)
    val sessions = Sessions(
        running, game, LoginAuthenticator(auth.verifier, config.onlineMode), loop.next(),
        stop = { stopping.complete(Unit) }, saveWorld = storage::save,
    )

    // Saves the world one last time, on the sessions' thread as it always is, and tells everyone still on that the
    // server closed, then closes the world, however the server stops. It goes first, as closing the gameplay server
    // stops that thread.
    val closed = AtomicBoolean(false)
    fun closeWorld() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { loop.submit(sessions::shutDown).get(SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }.onFailure { log.error("Couldn't save the world as the server stopped", it) }
        storage.close()
        log.info("Saved the world")
    }

    // What the server list, LAN discovery and joining show of the server, as it is now.
    val status = { running.toJoinInfo(onlinePlayers = sessions.playerCount, maxPlayers = game.maxPlayers) }
    val serverList = ServerListPing(serverId = Random.nextLong(), port = config.port, portV6 = config.portV6, status = status)
    // A client's offer, over HTTP or LAN discovery, answered as the peer it'll connect as.
    val answerOffer = { networkId: String, offer: SdpOffer ->
        val clientKey = offer.verifiedClientKey(requireIdentity = config.onlineMode)
        localPeer.answer(offer).also { answer ->
            peers.add(Peer(networkId, answer.ice, offer.iceUfrag, offer.fingerprints, offer.maxSendableMessageSize, clientKey))
        }
    }
    val signaling = SignalingServer(
        address = InetSocketAddress(config.port),
        handler = NetherNetSignalingHandler(joinInfo = status, answerOffer = answerOffer),
    )
    val rtc = RtcServer(
        group = loop,
        address = InetSocketAddress(config.port),
        handler = NetherNetRtcHandler(
            peers = peers,
            dtls = DtlsContext(certificate),
            onConnect = sessions::connect,
            onMessage = sessions::receive,
            onDisconnect = sessions::disconnect,
            serverList = serverList,
        ),
    )

    val console = Console(sessions::runConsole)
    Runtime.getRuntime().addShutdownHook(Thread({ closeWorld(); signaling.close(); rtc.close(); auth.close(); console.close() }, "kore-shutdown"))

    try {
        val tcp = try {
            signaling.bind().also { rtc.bind() }
        } catch (e: BindException) {
            log.error("Port {} is taken, most likely by another server: stop it, or give this one another server-port in {}", config.port, SERVER_PROPERTIES)
            // The shutdown hook closes the world on the way out.
            exitProcess(1)
        }
        log.debug("Listening for NetherNet on {} (TCP signaling, UDP gameplay)", tcp.localAddress())
        if (candidates.isNotEmpty()) log.info("Players can join on port {}, at {}", config.port, addresses.joinToString { it.hostAddress })
        if (config.portV6 != config.port) bindServerListV6(loop, config.portV6, serverList)
        if (config.lanVisibility) bindLanDiscovery(loop, LanDiscovery(Random.nextLong(), config.onlineMode, status, answerOffer))
        console.start()
        log.info("Started in {} seconds. Type `help` for commands", String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e9))
        tcp.closeFuture().addListener { stopping.complete(Unit) }
        stopping.join()
    } finally {
        closeWorld()
        signaling.close()
        rtc.close()
        auth.close()
        console.close()
    }
}

/** How long the last save waits for the sessions' thread before the server stops without it. */
private const val SAVE_TIMEOUT_SECONDS = 30L

/**
 * Where the game data is: in [directory], if it's there, as in a checkout
 * of Kore, and otherwise in the server's jar, which carries it. Without
 * either, [directory], for the error to name.
 */
private fun gameDataIn(directory: Path): Path {
    if (directory.exists()) return directory
    val carried = VanillaData::class.java.getResource("/data/blocks.json")?.toURI() ?: return directory
    if (carried.scheme == "jar") runCatching { FileSystems.newFileSystem(carried, emptyMap<String, Any>()) }
    return Path.of(carried).parent
}

/**
 * Answers the server list's pings on [port], for players who reach the
 * server by an IPv6 address, as the game pings those there, on the threads
 * of [group], which closing it shuts down. If it can't, it carries on
 * without, as the server list still finds the server by IPv4.
 */
private fun bindServerListV6(group: EventLoopGroup, port: Int, ping: ServerListPing) {
    runCatching {
        Bootstrap()
            .group(group)
            .channelFactory { NioDatagramChannel(InternetProtocolFamily.IPv6) }
            .handler(ServerListPingHandler(ping))
            .bind(InetSocketAddress(port))
            .sync()
    }.onFailure {
        log.warn("The server list won't find the server by IPv6: port {} isn't free ({})", port, it.message)
    }
}

/**
 * Listens for games looking for others on the local network, on the
 * threads of [group], which closing it shuts down. Games share the port,
 * as the game itself listens on it to find other worlds, so the server
 * does too. If it can't, it carries on without, as games can still join
 * by its address.
 */
private fun bindLanDiscovery(group: EventLoopGroup, discovery: LanDiscovery) {
    runCatching {
        Bootstrap()
            .group(group)
            .channelFactory { NioDatagramChannel(InternetProtocolFamily.IPv4) }
            .option(ChannelOption.SO_BROADCAST, true)
            .option(ChannelOption.SO_REUSEADDR, true)
            .handler(discovery)
            .bind(InetSocketAddress(LAN_DISCOVERY_PORT))
            .sync()
    }.onSuccess {
        log.debug("Games on the local network can find the server, on port {}", LAN_DISCOVERY_PORT)
    }.onFailure {
        log.warn("Games on the local network won't find the server: port {} isn't free ({})", LAN_DISCOVERY_PORT, it.message)
    }
}
