plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
}

group = "org.koremc"
// The release being worked towards, a snapshot until it's out. After each release this moves on to the next one's snapshot.
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // Netty (Handles the TCP HTTP Signaling and the UDP WebRTC traffic)
    implementation("io.netty:netty-all:4.1.101.Final")

    // Kotlinx Serialization (To parse the NetherNet HTTP JSON handshakes and the vanilla data)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Cloudburst NBT (Reads the vanilla data files, and writes NBT for packets and block state hashes)
    implementation("org.cloudburstmc:nbt:3.0.5.Final")

    // LevelDB as the game stores worlds, with Mojang's compression, for saving and loading them.
    // The uber jar carries its API and its own copy of Guava, which it needs and doesn't declare.
    implementation("com.hivemc.leveldb:leveldb:1.1.0:uber")

    // Logback for clean server console logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // JLine, so commands typed at the console keep their own line below what the server logs
    implementation("org.jline:jline:3.30.17")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass = "org.koremc.MainKt"
}

// The jar is the whole server: Kore, what it runs on, and the game data from data/, to run with java -jar wherever it's put.
tasks.jar {
    // The console reaches the terminal through native code, which Java asks the jar to allow.
    manifest { attributes("Main-Class" to application.mainClass, "Implementation-Version" to version, "Enable-Native-Access" to "ALL-UNNAMED") }
    from(configurations.runtimeClasspath.map { files -> files.map { if (it.isDirectory) it else zipTree(it) } })
    from("data") { into("data") }
    // A signed library's signatures don't hold for a jar of everything.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()

    // Some tests check against the game data in data/, so they rerun when it changes. Without it, they're skipped.
    inputs.files(fileTree("data")).withPropertyName("gameData").withPathSensitivity(PathSensitivity.RELATIVE)
}

// The console reads commands from the server's input, which Gradle passes on only if asked.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
