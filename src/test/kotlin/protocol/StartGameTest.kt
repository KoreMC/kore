package org.koremc.protocol

import org.cloudburstmc.nbt.NbtMap
import org.koremc.registry.BlockDefinition
import org.koremc.world.BlockPos
import org.koremc.world.Vec3
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StartGameTest {
    /**
     * Every byte of this was decoded field by field with minecraft-data's
     * definitions for 1.26.51 (protocol 2193), an implementation that shares
     * nothing with Kore's. All 199 bytes were consumed and every field read
     * back as the value below. If this test fails, the layout has changed.
     */
    private val verified = HexFormat.of().parseHex(
        "0b542a020000003f713d83420000003f000020410000b442393000000000000000000000040000060580010e01000000" +
            "000000000000000000000000000100080801000000000000000000010400000000000000000000000000" +
            "07312e32362e3530" + // The base game version
            "00000000000000000100000000000000000a4b6f726520576f726c6400005000701700000000000000000e636f7272656c617469" +
            "6f6e2d69640107312e32362e35300a000000000000000000000000000000000000000000000000000000010000000000" +
            "00",
    )

    @Test
    fun `start game encodes exactly as verified`() {
        val packet = StartGame(
            entityId = 42,
            runtimeId = 42,
            playerGameMode = GameMode.CREATIVE,
            position = Vec3(0.5f, 65.62f, 0.5f),
            pitch = 10f,
            yaw = 90f,
            seed = 12345,
            worldGameMode = GameMode.SURVIVAL,
            difficulty = Difficulty.HARD,
            worldSpawn = BlockPos(-3, 64, 7),
            worldName = "Kore World",
            serverVersion = "1.26.50",
            baseGameVersion = "1.26.50",
            time = 6000,
            multiplayerCorrelationId = "correlation-id",
        )

        assertEquals(HexFormat.of().formatHex(verified), HexFormat.of().formatHex(packet.encode()))

        // From 1.26.60, how far the editor has moved the world on follows whether it was exported from the editor.
        val exported = 47
        val newer = verified.copyOfRange(0, exported) + byteArrayOf(0) + verified.copyOfRange(exported, verified.size)
        assertEquals(HexFormat.of().formatHex(newer), HexFormat.of().formatHex(packet.encode(GameVersion.V1_26_60)))
    }

    @Test
    fun `block definitions are a name and network NBT each`() {
        val definitions = BlockDefinitions(listOf(BlockDefinition("a:b", NbtMap.builder().putInt("v", 13).build())))

        // One block, "a:b", then a compound holding the int v, 13, zigzagged to 26.
        val expected = byteArrayOf(0x01, 0x03) + "a:b".toByteArray() + byteArrayOf(0x0A, 0x00, 0x03, 0x01, 'v'.code.toByte(), 26, 0x00)
        assertContentEquals(expected, definitions.encoded)
    }
}
