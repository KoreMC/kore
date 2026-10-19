package org.koremc.protocol

import io.netty.buffer.ByteBuf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import org.koremc.auth.string
import java.util.Base64
import java.util.UUID

/** An image in a skin: RGBA pixels, four bytes each, row by row. */
class SkinImage(val width: Int, val height: Int, val pixels: ByteArray) {
    companion object {
        val NONE = SkinImage(0, 0, ByteArray(0))
    }
}

/** An animation a skin plays by itself, like blinking: its frames, side by side in one image. */
class SkinAnimation(val image: SkinImage, val type: Int, val frames: Float, val expression: Int)

/**
 * How a player looks to everyone else, as their client described it when
 * they logged in: the skin's image and the geometry that wraps it around
 * them, any animations and cape, and a few flags.
 *
 * A persona skin, made in the game's character creator, comes with the
 * pieces it's made of and their tints too. Kore leaves those out, as
 * Dragonfly does: the image already shows them, and the client draws it.
 */
class Skin(
    /** What the skin is called, which the client knows it by. */
    val fullId: String,
    val playFabId: String,
    /** JSON naming the geometry and any animations to use. */
    val resourcePatch: ByteArray,
    val image: SkinImage,
    val animations: List<SkinAnimation>,
    val cape: SkinImage,
    val capeId: String,
    /** The JSON geometry, or "{}" for the one the resource patch names from the game. */
    val geometry: ByteArray,
    val geometryEngineVersion: ByteArray,
    val animationData: ByteArray,
    val slimArms: Boolean,
    /** The skin's base colour, as ARGB. */
    val colour: Int,
    val premium: Boolean,
    val persona: Boolean,
    val capeOnClassicSkin: Boolean,
    val profileHash: String,
    /**
     * A name for this copy of the skin. Each player's skin gets one of its
     * own, so a client that has seen another skin of the same name doesn't
     * mistake one for the other.
     */
    val id: String = UUID.randomUUID().toString(),
)

/**
 * The skin described by a client's [clientData]. An image the wrong size
 * for its width and height is left out: a skin without one, which the
 * client draws as a default skin, or no cape, or one animation fewer. A
 * skin that doesn't say which version of the game its geometry needs is
 * taken to need [gameVersion].
 */
fun skinFrom(clientData: JsonObject, gameVersion: String): Skin {
    fun bytes(key: String) = clientData.string(key)?.let(::decodeBase64) ?: ByteArray(0)
    fun int(key: String) = (clientData[key] as? JsonPrimitive)?.intOrNull ?: 0
    fun flag(key: String) = (clientData[key] as? JsonPrimitive)?.booleanOrNull ?: false

    val animations = (clientData["AnimatedImageData"] as? JsonArray).orEmpty().mapNotNull { element ->
        val animation = element as? JsonObject ?: return@mapNotNull null
        fun number(key: String) = animation[key] as? JsonPrimitive
        val image = image(number("ImageWidth")?.intOrNull ?: 0, number("ImageHeight")?.intOrNull ?: 0, animation.string("Image")?.let(::decodeBase64))
            ?: return@mapNotNull null
        SkinAnimation(image, number("Type")?.intOrNull ?: 0, number("Frames")?.floatOrNull ?: 1f, number("AnimationExpression")?.intOrNull ?: 0)
    }
    return Skin(
        fullId = clientData.string("SkinId").orEmpty(),
        playFabId = clientData.string("PlayFabId").orEmpty(),
        resourcePatch = bytes("SkinResourcePatch"),
        image = image(int("SkinImageWidth"), int("SkinImageHeight"), bytes("SkinData")) ?: SkinImage.NONE,
        animations = animations,
        cape = image(int("CapeImageWidth"), int("CapeImageHeight"), bytes("CapeData")) ?: SkinImage.NONE,
        capeId = clientData.string("CapeId").orEmpty(),
        geometry = bytes("SkinGeometryData").takeIf { it.isNotEmpty() } ?: "{}".encodeToByteArray(),
        geometryEngineVersion = bytes("SkinGeometryDataEngineVersion").takeIf { it.isNotEmpty() } ?: gameVersion.encodeToByteArray(),
        animationData = bytes("SkinAnimationData"),
        slimArms = clientData.string("ArmSize") == "slim",
        colour = colour(clientData.string("SkinColor")),
        premium = flag("PremiumSkin"),
        persona = flag("PersonaSkin"),
        capeOnClassicSkin = flag("CapeOnClassicSkin"),
        profileHash = clientData.string("ProfileHash").orEmpty(),
    )
}

/** An image of [width] by [height] pixels, or null if [pixels] isn't that many, four bytes each. */
private fun image(width: Int, height: Int, pixels: ByteArray?): SkinImage? =
    pixels?.takeIf { width >= 0 && height >= 0 && it.size.toLong() == width.toLong() * height * 4 }?.let { SkinImage(width, height, it) }

private fun decodeBase64(text: String): ByteArray? = try {
    Base64.getDecoder().decode(text)
} catch (e: IllegalArgumentException) {
    null
}

/**
 * A colour written as `#rrggbb`, which is opaque, or `#aarrggbb`, as ARGB.
 * Anything else, like the `#0` that stands for none, is read as it is.
 */
private fun colour(text: String?): Int {
    val digits = text?.removePrefix("#") ?: return 0
    val value = digits.toLongOrNull(16) ?: return 0
    return if (digits.length == 6) (0xFF000000 or value).toInt() else value.toInt()
}

/** [skin] as the player list carries it, to a client on [version]. */
internal fun ByteBuf.writeSkin(skin: Skin, version: GameVersion): ByteBuf {
    writeString(skin.id)
    if (version < GameVersion.V1_26_60) writeString(skin.playFabId) // Which the player list carries instead from 1.26.60
    writeByteArray(skin.resourcePatch)
    writeSkinImage(skin.image)
    writeVarUInt(skin.animations.size)
    for (animation in skin.animations) {
        writeSkinImage(animation.image)
        writeVarUInt(animation.type)
        writeFloatLE(animation.frames)
        writeVarUInt(animation.expression)
    }
    writeSkinImage(skin.cape)
    writeByteArray(skin.geometry)
    writeByteArray(skin.geometryEngineVersion)
    writeByteArray(skin.animationData)
    writeString(skin.capeId)
    writeString(skin.fullId)
    writeByte(if (skin.slimArms) SLIM_ARMS else WIDE_ARMS)
    writeIntLE(skin.colour)
    writeVarUInt(0) // Persona pieces
    writeVarUInt(0) // Their tints
    writeBoolean(skin.premium)
    writeBoolean(skin.persona)
    writeBoolean(skin.capeOnClassicSkin)
    writeBoolean(false) // Whether it's the primary user's, on a client with split screen
    writeBoolean(true) // Whether it overrides how the player looks otherwise
    writeString("true") // Whether the skin is trusted, which a client set to show only trusted skins needs
    return writeString(skin.profileHash)
}

private fun ByteBuf.writeSkinImage(image: SkinImage): ByteBuf = writeIntLE(image.width).writeIntLE(image.height).writeByteArray(image.pixels)

// How wide a skin's arms are.
private const val SLIM_ARMS = 0
private const val WIDE_ARMS = 1
