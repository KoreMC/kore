package org.koremc.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemVersion
import org.koremc.world.Vec3
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PlayersTest {
    private val dirt = ItemDefinition("minecraft:dirt", 3, componentBased = false, ItemVersion.NONE)
    private val steve = UUID(0x1111111111111111, 0x2222222222222222)
    private val eyes = Vec3(0.5f, -61.38f, 0.5f)

    /** A 2 by 1 skin with a blinking head, wide arms and no cape. */
    private val skin = Skin(
        fullId = "c18e65aa_Alex",
        playFabId = "pf",
        resourcePatch = "{}".encodeToByteArray(),
        image = SkinImage(2, 1, ByteArray(8) { (it + 1).toByte() }),
        animations = listOf(SkinAnimation(SkinImage(1, 1, ByteArray(4) { 9 }), type = 1, frames = 2f, expression = 1)),
        cape = SkinImage.NONE,
        capeId = "",
        geometry = "{}".encodeToByteArray(),
        geometryEngineVersion = "1.26.50".encodeToByteArray(),
        animationData = ByteArray(0),
        slimArms = false,
        colour = 0xFFB37B62.toInt(),
        premium = false,
        persona = true,
        capeOnClassicSkin = false,
        profileHash = "hash",
        id = "skin-1",
    )

    @Test
    fun `the player list adds players with their skins, and removes them by UUID`() {
        val packet = PlayerList(
            listOf(
                PlayerListEntry.Remove(UUID(0x0123456789ABCDEF, 0x0FEDCBA987654321)),
                PlayerListEntry.Add(steve, entityId = 5, "Steve", xuid = "2535", platform = 7, skin),
            ),
        )

        val expected = "3f02" +
            "0001" + "efcdab8967452301" + "21436587a9cbed0f" + // Remove, then the UUID's halves, each little-endian
            "0100" + "1111111111111111" + "2222222222222222" + "0a" + "055374657665" + "0432353335" + "00" + "07000000" +
            "06736b696e2d31" + "027066" + "027b7d" + // The skin's name, PlayFab ID and resource patch
            "02000000" + "01000000" + "080102030405060708" + // Its image
            "01" + "01000000" + "01000000" + "0409090909" + "01" + "00000040" + "01" + // A blinking head, two frames
            "00000000" + "00000000" + "00" + // No cape
            "027b7d" + "07312e32362e3530" + "00" + "00" + "0d63313865363561615f416c6578" + // Geometry, its version, animation data, cape and full ID
            "01" + "627bb3ff" + "00" + "00" + // Wide arms, the colour as little-endian ARGB, no persona pieces or tints
            "0001000001" + "0474727565" + "0468617368" + // Flags, trusted, profile hash
            "000000" + "00000000" // Not a teacher, host or split screen player, and no colour
        assertContentEquals(hex(expected), packet.encode())

        // From 1.26.60, the PlayFab ID comes after the XUID instead of in the skin.
        val newer = expected.replace("0432353335" + "00" + "07000000" + "06736b696e2d31" + "027066", "0432353335" + "027066" + "00" + "07000000" + "06736b696e2d31")
        assertContentEquals(hex(newer), packet.encode(GameVersion.V1_26_60))
    }

    @Test
    fun `another player appears with what they hold, their data and their abilities`() {
        val data = listOf(flagsItem(setOf(ActorFlag.SHOW_NAME)), StringItem(ActorDataId.NAME, "Steve"), ByteItem(ActorDataId.ALWAYS_SHOW_NAME, 1))
        val packet = AddPlayer(steve, "Steve", 5, eyes, pitch = 10f, yaw = 90f, headYaw = 95f, ItemStack(dirt), GameMode.SURVIVAL, data, abilitiesFor(GameMode.SURVIVAL), platform = 7)

        val expected = "0c" + "1111111111111111" + "2222222222222222" + "055374657665" + "05" + "00" +
            "0000003f1f8575c20000003f" + "000000000000000000000000" + "00002041" + "0000b442" + "0000be42" +
            "0300" + "0100" + "00" + "00" + "00" + "0a00000000000000000000" + // One dirt
            "00" + // Survival
            "03" + "000707808002" + "040404055374657665" + "51000001" + // Flags, name, and that it always shows
            "0000" + // No properties
            "0500000000000000" + "01" + "00" + "01" + "0100" + "ffff0f00" + "3f000000" + "cdcc4c3d" + "0000803f" + "cdcccc3d" +
            "00" + "00" + "07000000" // No links or device ID, then the platform
        assertContentEquals(hex(expected), packet.encode())

        // From 1.26.60, a block they might ride comes after the links.
        assertContentEquals(hex(expected.removeSuffix("00" + "00" + "07000000") + "00" + "00" + "00" + "07000000"), packet.encode(GameVersion.V1_26_60))
    }

    @Test
    fun `other players move, swing and show what they hold`() {
        assertContentEquals(
            hex("13" + "05" + "0000003f1f8575c20000003f" + "00002041" + "0000b442" + "0000be42" + "00" + "01" + "00" + "00" + "00"),
            MovePlayer(5, eyes, 10f, 90f, 95f, onGround = true).encode(),
        )
        assertContentEquals(hex("2c" + "01" + "05" + "00000000" + "01" + "046d696e65"), Animate(Animate.SWING_ARM, 5, swingSource = "mine").encode())
        assertContentEquals(hex("1f" + "05" + "030001000000000a00000000000000000000" + "02" + "02" + "00"), ShowEquipment(5, ItemStack(dirt), slot = 2).encode())
    }

    @Test
    fun `a client swinging its arm says why, or not`() {
        assertEquals(Animate(Animate.SWING_ARM, 5, swingSource = "mine"), decodeClientPacket(hex("2c" + "01" + "05" + "00000000" + "01" + "046d696e65")))
        assertEquals(Animate(Animate.SWING_ARM, 5), decodeClientPacket(hex("2c" + "01" + "05" + "00000000" + "00")))
    }

    @Test
    fun `from 1_26_60 a swing says which hand, if it's not the main one`() {
        val offhand = Animate(Animate.SWING_ARM, 5, swingSource = "mine", hand = 1)
        assertContentEquals(hex("2c" + "01" + "05" + "00000000" + "01" + "046d696e65" + "01" + "01"), offhand.encode(GameVersion.V1_26_60))
        assertEquals(offhand, decodeClientPacket(hex("2c" + "01" + "05" + "00000000" + "01" + "046d696e65" + "01" + "01"), GameVersion.V1_26_60))
        assertContentEquals(hex("2c" + "04" + "05" + "00005c42" + "00" + "00"), Animate(Animate.CRITICAL_HIT, 5, 55f).encode(GameVersion.V1_26_60))
        // 1.26.50 has no hand to say.
        assertContentEquals(hex("2c" + "01" + "05" + "00000000" + "01" + "046d696e65"), offhand.encode())
    }

    @Test
    fun `a skin comes from the client data, leaving out images the wrong size`() {
        val clientData = buildJsonObject {
            put("SkinId", "c18e65aa_Alex")
            put("SkinImageWidth", 2)
            put("SkinImageHeight", 1)
            put("SkinData", base64(ByteArray(8)))
            put("CapeImageWidth", 64)
            put("CapeImageHeight", 32)
            put("CapeData", base64(ByteArray(4))) // Far too small
            put("SkinResourcePatch", base64("""{"geometry":{"default":"geometry.humanoid.customSlim"}}""".encodeToByteArray()))
            put("ArmSize", "slim")
            put("SkinColor", "#b37b62")
            put("PersonaSkin", true)
            put(
                "AnimatedImageData",
                buildJsonArray {
                    add(buildJsonObject { put("ImageWidth", 1); put("ImageHeight", 1); put("Image", base64(ByteArray(4))); put("Type", 1); put("Frames", 2.0); put("AnimationExpression", 1) })
                    add(buildJsonObject { put("ImageWidth", 2); put("ImageHeight", 2); put("Image", base64(ByteArray(4))); put("Type", 1) })
                },
            )
            put("SkinGeometryData", JsonPrimitive(""))
        }

        val skin = skinFrom(clientData, gameVersion = "1.26.50")

        assertEquals(2, skin.image.width)
        assertEquals(0, skin.cape.width)
        assertEquals(1, skin.animations.size)
        assertEquals("""{"geometry":{"default":"geometry.humanoid.customSlim"}}""", skin.resourcePatch.decodeToString())
        assertEquals("{}", skin.geometry.decodeToString()) // No geometry of its own
        assertEquals("1.26.50", skin.geometryEngineVersion.decodeToString())
        assertEquals(true, skin.slimArms)
        assertEquals(0xFFB37B62.toInt(), skin.colour)
        assertEquals(true, skin.persona)
    }

    private fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun hex(digits: String) = ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
