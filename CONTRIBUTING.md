# Contributing to Kore

Thanks for your interest in Kore. The project is deliberately small, and keeping it that way takes discipline from everyone who touches it. This guide explains what we expect from code and how changes get merged.

- [Before You Start](#before-you-start)
- [Development Setup](#development-setup)
- [Code Standards](#code-standards)
- [Leave Bukkit at the Door](#leave-bukkit-at-the-door)
- [Networking and Performance](#networking-and-performance)
- [Tests](#tests)
- [Protocol Research](#protocol-research)
- [Pull Request Process](#pull-request-process)
- [Reporting Bugs](#reporting-bugs)

## Before You Start

- **Small fixes** like typos, bug fixes and missing tests: open a pull request directly.
- **Bigger changes** like a new subsystem, a new dependency, or anything that reshapes existing code: open an issue first so we can agree on the approach before you spend time on it.
- Check the [milestones](README.md#-milestones) to see what's being worked on. Comment on the related issue to claim something so work isn't duplicated.

## Development Setup

You need **JDK 25**. Gradle provisions a matching toolchain if it isn't installed. IntelliJ IDEA is recommended and picks up the project code style automatically.

```bash
git submodule update --init   # the game data Kore reads on start, if you cloned without --recurse-submodules
./gradlew build               # compile and run all tests
./gradlew test                # tests only
./gradlew run                 # start the server on TCP 19132
```

The server won't start without `data/`. Tests that check against the real game data are skipped without it, so check it out before you trust a green build. A change that needs new game data goes to [KoreMC/data](https://github.com/KoreMC/data) first, then moves the submodule here in the same pull request as the code that uses it.

## Code Standards

### Style

- Follow the [official Kotlin code style](https://kotlinlang.org/docs/coding-conventions.html). It's already set in `gradle.properties` and `.idea/codeStyles`. Reformat before you commit.
- Directories mirror packages with the `org.koremc` root omitted: `org.koremc.network` lives in `src/main/kotlin/network/`.
- Name things for what they are. Avoid `Manager`, `Helper`, `Util`, `Impl`, and `Abstract*` names. They usually point to a design problem.
- Keep files focused. One main type per file, plus the small private helpers it needs.

### Write Kotlin, Not Java With Kotlin Syntax

- **Prefer immutability.** Use `val` over `var` and read-only collections over mutable ones. Mutable state should have exactly one owner.
- **Model data with the type system.** Use `data class` for values, `sealed interface` for closed sets like packets and connection states, and `enum class` for fixed constants.
- **Top-level and extension functions** replace utility classes and static helper objects.
- **Pass functions, not single-method interfaces.** A `() -> JoinInfo` parameter beats a `JoinInfoProvider` interface.
- **Composition over inheritance.** Inheritance belongs at the boundary with Java libraries (extending a Netty handler, for example). Don't build hierarchies of our own.
- **Use expressions.** Prefer `when` over long `if`/`else` chains and expression bodies for short functions.
- **Be honest about nullability.** Model absence with `?`. Don't use `!!`. Reach for `lateinit` only when there's no other option.
- **No magic.** No reflection, annotation scanning, or classpath discovery. Wiring should be readable in `Main.kt`.

### Comments

Explain *why*, not *what*. Public types get a short KDoc. Protocol behaviour that isn't obvious should say where it comes from (a packet capture, a wiki page, a client version).

## Leave Bukkit at the Door

Kore isn't trying to feel familiar to Bukkit, Spigot, or Nukkit developers. These patterns don't belong here:

| Instead of… | Do this |
| --- | --- |
| `getX()` / `setX()` pairs | Kotlin properties |
| `SomethingManager.getInstance()` singletons | Pass dependencies through constructors |
| Abstract base classes with override hooks | Plain functions and data |
| A global event bus with priorities and string channels | Direct calls, or a typed callback where decoupling is truly needed |
| `Map<String, Object>` metadata bags | Typed properties |
| `catch (Exception e) { e.printStackTrace(); }` | Handle the specific failure, or let it propagate and log it once with context |
| Static mutable state | State owned by one object and passed down |

A quick example of the difference:

```kotlin
// Avoid: a registry singleton, an abstract handler, and a subclass per packet
abstract class AbstractPacketHandler<T : Packet> {
    abstract fun handle(session: Session, packet: T)
}

object PacketHandlerManager {
    private val handlers = HashMap<Class<*>, AbstractPacketHandler<*>>()
    fun register(type: Class<*>, handler: AbstractPacketHandler<*>) { handlers[type] = handler }
}

// Prefer: a sealed type and one exhaustive `when`
sealed interface Packet
data class Login(val protocol: Int, val chain: String) : Packet
data class Text(val message: String) : Packet

fun Session.handle(packet: Packet) = when (packet) {
    is Login -> login(packet)
    is Text -> chat(packet.message)
}
```

The compiler now tells you when a new packet isn't handled. That's the kind of safety we want throughout the codebase.

## Networking and Performance

- **Never block an event loop.** No `Thread.sleep`, file I/O, blocking calls, or `.sync()` inside a handler. Move blocking work to a separate executor and hop back.
- **Own your buffers.** Every `ByteBuf` you `retain()` must be released. `SimpleChannelInboundHandler` releases the inbound message after `channelRead0`, so don't keep references to it.
- **Shared handlers are stateless.** A handler instance used by many channels must be `@Sharable` and hold no per-connection state.
- **Log with placeholders.** Use `log.debug("Got {} from {}", packet, remote)` rather than string templates, so disabled log levels cost nothing.
- **Measure before optimizing.** Include profiler output (JFR or async-profiler) or a benchmark with performance PRs.
- **Justify dependencies.** Every new library is something we maintain forever. Explain in the PR why it's worth it.

## Tests

- New behaviour comes with tests. Bug fixes come with a test that would have caught the bug.
- Test Netty handlers with `EmbeddedChannel` rather than opening real sockets. See `NetherNetSignalingHandlerTest` for the pattern.
- Name tests after the behaviour they check, in backticks: ``fun `unknown routes are not found`()``.

## Protocol Research

- **Clean-room only.** Don't commit Mojang binaries, decompiled or deobfuscated game code, or game assets. Base implementations on observed behaviour, packet captures, and public community documentation.
- **Game data lives in [KoreMC/data](https://github.com/KoreMC/data), not here.** The item and block state definitions clients expect come from [CloudburstMC/Data](https://github.com/CloudburstMC/Data). Changes to them go to that repository, and this one never commits data files.
- When a PR implements protocol behaviour, state the client version and platform you verified it against.

## Pull Request Process

1. **Fork** `KoreMC/kore`. KoreMC organization members can push branches to the main repository instead.
2. **Branch from `main`** with a short, descriptive name: `feat/sdp-answer`, `fix/keepalive-close`, `docs/readme-setup`.
3. **Commit** using [Conventional Commits](https://www.conventionalcommits.org): `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`. Write the summary in the imperative mood. A scope is welcome: `feat(network): answer SDP offers`.
4. **Run `./gradlew build`** and make sure it passes before you push.
5. **Open the PR against `main`** and include:
   - what changed and why
   - how you tested it
   - the client version, for protocol changes
   - a link to the related issue, if there is one
6. **Keep it focused.** One concern per PR. Large changes are easier to review as a series of smaller PRs.
7. **Review.** At least one approval from a KoreMC maintainer is required. Address feedback with new commits instead of force-pushing during review, so reviewers can see what changed.
8. **Merge.** A maintainer squash-merges the PR once it's approved and the build is green. The PR title becomes the commit message, so keep it in Conventional Commit form.

### Checklist

Copy this into your PR description:

```markdown
- [ ] `./gradlew build` passes locally
- [ ] New behaviour is covered by tests
- [ ] No new dependencies, or the PR explains why they're needed
- [ ] No blocking calls on Netty event loop threads
- [ ] Protocol changes list the client version they were tested against
```

## Reporting Bugs

Open an issue with:

- the Kore commit you're running
- client version and platform (Windows, Android, iOS, console)
- steps to reproduce
- relevant logs, with the `org.koremc` logger set to `DEBUG`

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
