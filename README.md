<div align="center">

# ⛏️ Kore

**A lean, modern Minecraft: Bedrock Edition server engine, written from scratch in Kotlin.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-25-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net)
[![Netty](https://img.shields.io/badge/Netty-4.1-1E8C45)](https://netty.io)
[![Transport](https://img.shields.io/badge/transport-NetherNet-62B47A)](https://minecraft.wiki/w/NetherNet)
[![Status](https://img.shields.io/badge/status-pre--alpha-orange)](#-milestones)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-join%20us-5865F2?logo=discord&logoColor=white)](https://discord.gg/hNG9t24NNK)

[Philosophy](#-philosophy) · [Getting Started](#-getting-started) · [Milestones](#-milestones) · [Contributing](CONTRIBUTING.md)

</div>

---

> [!WARNING]
> Kore is in early development. Players can join a flat world, build, mine, craft, wire up redstone and pistons, and play together, and the world is saved, but there are no mobs yet.
> Follow the [milestones](#-milestones) to see where things stand.

## 🧭 Philosophy

Kore is not a fork, a wrapper, or a port of an existing server. It's a ground-up engine built around a few rules:

- **Simple beats clever.** Small files, flat packages, plain functions. If a newcomer can't follow a code path in one sitting, it's too complicated.
- **Fast by default.** Non-blocking I/O end to end, no reflection-driven plugin layer, no work on the event loop that doesn't belong there.
- **Real Kotlin.** Data classes, sealed types, extension functions and lambdas. Not Java written with Kotlin syntax.
- **No legacy baggage.** No `Manager` singletons, no abstract base class towers, no Bukkit-era patterns kept around for familiarity.
- **NetherNet only.** RakNet is deprecated for Bedrock Dedicated Server. Kore targets the WebRTC-based NetherNet transport and nothing else.
- **Vanilla behaviour.** Kore plays like Bedrock Edition. Server policy, like rate limits, is for whoever builds on it.

## 🧰 Tech Stack

| Layer | Choice |
| --- | --- |
| Language | **Kotlin 2.4** on **JDK 25** |
| Build | **Gradle** (Kotlin DSL) |
| Networking | **Netty 4**, for TCP signaling and UDP gameplay |
| WebRTC | ICE-lite, DTLS 1.2 from the **JDK**, and **SCTP written in Kotlin**, with no native libraries |
| NBT | **Cloudburst NBT** |
| Game data | **[KoreMC/data](https://github.com/KoreMC/data)**, dumped from the game by CloudburstMC, with mining and stack data from minecraft-data |
| Worlds | **LevelDB**, as the game saves worlds, through Hive's [leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) |
| Logging | **SLF4J + Logback** |

## 🚀 Getting Started

**Requirements:** JDK 25. Gradle downloads a matching toolchain if you don't have one.

```bash
git clone --recurse-submodules https://github.com/KoreMC/kore.git
cd kore
./gradlew run
```

The game data Kore needs comes with it, as the `data` submodule, for the same client release (1.26.50, protocol `2193`). `./gradlew jar` builds a single jar with the data inside, to run anywhere with `java -jar`.

Kore uses port `19132` for signaling (TCP) and gameplay (UDP), so open both in your firewall.

### ⚙️ Configuration

Settings live in `server.properties`, as the dedicated server names them, and Kore's own in `kore.json`. Both are written on first run, each setting explained. Behind NAT, set `advertisedAddresses` in `kore.json` to your public IP.

### 🔑 The Operator Key

On first run, Kore creates `operator.key`, which signs every SDP answer. Players are asked to trust it once, and the client remembers it.

- **Keep it safe.** Anyone holding it can sign answers as your server.
- **Don't regenerate it.** A new key prompts every returning player again.
- **Share it across servers** if you run several, so players trust them all at once.

## 🗂️ Project Layout

Packages leave out the common root, so `org.koremc.network` lives in `src/main/kotlin/network/`.

| Package | What's in it |
| --- | --- |
| [`network`](src/main/kotlin/network) | Signaling, and the WebRTC stack: ICE, DTLS and SCTP |
| [`protocol`](src/main/kotlin/protocol) | Packets and how they're written on the wire |
| [`auth`](src/main/kotlin/auth) | Login tokens and Minecraft's signing keys |
| [`registry`](src/main/kotlin/registry) | The game's items, block states, biomes, recipes and enchantments |
| [`world`](src/main/kotlin/world) | Blocks, items, redstone, pistons and everything else that happens in the world |
| [`storage`](src/main/kotlin/storage) | Saving and loading worlds and players as the game does |
| [`inventory`](src/main/kotlin/inventory) | Players' inventories, item stack requests, crafting and workstations |
| [`command`](src/main/kotlin/command) | Commands, selectors, operators and the console |

[`Session.kt`](src/main/kotlin/Session.kt) takes each player from login into the game, and [`Main.kt`](src/main/kotlin/Main.kt) wires it all together.

## 🗺️ Milestones

### Phase 1: Transport
- [x] NetherNet signaling: discovery and SDP offer/answer
- [x] ICE-lite, DTLS 1.2 and SCTP data channels
- [x] Message framing over reliable and unreliable channels

### Phase 2: Protocol
- [x] Batches, compression and the packet codec
- [x] Login, with online-mode authentication
- [x] `StartGame`, the item registry, block states and biomes
- [ ] Packet registry for the rest of the protocol

### Phase 3: World
- [x] Chunk streaming in a flat world
- [x] Player spawning and movement, taking the client's word for its position
- [x] World loading and saving (LevelDB)
- [ ] Movement simulation and corrections

### Phase 4: Gameplay
- [x] Building, survival mining and tool wear
- [x] Items, inventories and the creative inventory
- [x] Containers, crafting, furnaces, brewing and workstations
- [x] Hoppers, dispensers, droppers and crafters
- [x] Lecterns, and writing and signing books
- [x] Redstone, pistons, pressure plates and lamps
- [x] Players seeing each other, chat and commands
- [x] Beacons, maps and cartography tables
- [x] Water, lava and fire
- [x] Growth, time and weather
- [x] Health, damage and respawning
- [ ] Hunger and eating
- [ ] Mobs
- [ ] The rest of the game's commands

## 🤝 Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. It covers the code standards and the review process.

## 📚 References

- [NetherNet HTTP Signaling: Partner Onboarding Guide](https://mojang.github.io/bedrock-protocol-docs/guides/nether-net-onboarding-guide/) from Mojang
- [NetherNet](https://minecraft.wiki/w/NetherNet) and [Bedrock Edition protocol](https://minecraft.wiki/w/Bedrock_Edition_protocol) on the Minecraft Wiki
- [CloudburstMC](https://github.com/CloudburstMC) for the [NBT library](https://github.com/CloudburstMC/NBT) and the [game data](https://github.com/CloudburstMC/Data) Kore reads
- [gophertunnel](https://github.com/Sandertv/gophertunnel) and [Dragonfly](https://github.com/df-mc/dragonfly), which Kore checks its encodings against

## 📄 License

Kore is released under the [MIT License](LICENSE).

<sub>Kore is not an official Minecraft product and is not approved by or associated with Mojang Studios or Microsoft.</sub>
