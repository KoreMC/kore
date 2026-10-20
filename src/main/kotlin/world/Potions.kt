package org.koremc.world

import kotlin.math.roundToInt

/** An effect a potion gives, [level], for [ticks]. An instant one, like Healing, takes no time. */
data class PotionEffect(val level: EffectLevel, val ticks: Int)

private fun effect(effect: Int, amplifier: Int, seconds: Double) = PotionEffect(EffectLevel(effect, amplifier), (seconds * 20).roundToInt())

private fun instant(effect: Int, amplifier: Int) = PotionEffect(EffectLevel(effect, amplifier), 0)

/**
 * What drinking each potion gives, by the number a bottle's data value
 * gives it, as Bedrock has them: some last longer than Java's, like Poison
 * and Regeneration extended. Water, mundane, thick and awkward potions
 * give nothing.
 */
private val POTION_EFFECTS: Map<Int, List<PotionEffect>> = mapOf(
    5 to listOf(effect(Effect.NIGHT_VISION, 0, 180.0)),
    6 to listOf(effect(Effect.NIGHT_VISION, 0, 480.0)),
    7 to listOf(effect(Effect.INVISIBILITY, 0, 180.0)),
    8 to listOf(effect(Effect.INVISIBILITY, 0, 480.0)),
    9 to listOf(effect(Effect.JUMP_BOOST, 0, 180.0)),
    10 to listOf(effect(Effect.JUMP_BOOST, 0, 480.0)),
    11 to listOf(effect(Effect.JUMP_BOOST, 1, 90.0)),
    12 to listOf(effect(Effect.FIRE_RESISTANCE, 0, 180.0)),
    13 to listOf(effect(Effect.FIRE_RESISTANCE, 0, 480.0)),
    14 to listOf(effect(Effect.SPEED, 0, 180.0)),
    15 to listOf(effect(Effect.SPEED, 0, 480.0)),
    16 to listOf(effect(Effect.SPEED, 1, 90.0)),
    17 to listOf(effect(Effect.SLOWNESS, 0, 90.0)),
    18 to listOf(effect(Effect.SLOWNESS, 0, 240.0)),
    19 to listOf(effect(Effect.WATER_BREATHING, 0, 180.0)),
    20 to listOf(effect(Effect.WATER_BREATHING, 0, 480.0)),
    21 to listOf(instant(Effect.INSTANT_HEALTH, 0)),
    22 to listOf(instant(Effect.INSTANT_HEALTH, 1)),
    23 to listOf(instant(Effect.INSTANT_DAMAGE, 0)),
    24 to listOf(instant(Effect.INSTANT_DAMAGE, 1)),
    25 to listOf(effect(Effect.POISON, 0, 45.0)),
    26 to listOf(effect(Effect.POISON, 0, 120.0)),
    27 to listOf(effect(Effect.POISON, 1, 22.5)),
    28 to listOf(effect(Effect.REGENERATION, 0, 45.0)),
    29 to listOf(effect(Effect.REGENERATION, 0, 120.0)),
    30 to listOf(effect(Effect.REGENERATION, 1, 22.5)),
    31 to listOf(effect(Effect.STRENGTH, 0, 180.0)),
    32 to listOf(effect(Effect.STRENGTH, 0, 480.0)),
    33 to listOf(effect(Effect.STRENGTH, 1, 90.0)),
    34 to listOf(effect(Effect.WEAKNESS, 0, 90.0)),
    35 to listOf(effect(Effect.WEAKNESS, 0, 240.0)),
    // Decay, which only commands give.
    36 to listOf(effect(Effect.WITHER, 1, 40.0)),
    37 to listOf(effect(Effect.SLOWNESS, 3, 20.0), effect(Effect.RESISTANCE, 2, 20.0)),
    38 to listOf(effect(Effect.SLOWNESS, 3, 40.0), effect(Effect.RESISTANCE, 2, 40.0)),
    39 to listOf(effect(Effect.SLOWNESS, 5, 20.0), effect(Effect.RESISTANCE, 3, 20.0)),
    40 to listOf(effect(Effect.SLOW_FALLING, 0, 90.0)),
    41 to listOf(effect(Effect.SLOW_FALLING, 0, 240.0)),
    42 to listOf(effect(Effect.SLOWNESS, 3, 20.0)),
    43 to listOf(effect(Effect.WIND_CHARGED, 0, 180.0)),
    44 to listOf(effect(Effect.WEAVING, 0, 180.0)),
    45 to listOf(effect(Effect.OOZING, 0, 180.0)),
    46 to listOf(effect(Effect.INFESTED, 0, 180.0)),
)

/** The effects drinking the potion numbered [id] gives, or none for one that gives nothing, or one the game doesn't have. */
fun potionEffects(id: Int): List<PotionEffect> = POTION_EFFECTS[id].orEmpty()

/** The water bottle, and the last of the potions with no effect of their own: mundane, thick and awkward. */
const val WATER_POTION = 0
private const val LAST_PLAIN_POTION = 4

/** Whether the potion numbered [id] is one with no effect of its own, like water or an awkward potion, which only puts out the fire on whoever it splashes. */
fun isPlainPotion(id: Int) = id in WATER_POTION..LAST_PLAIN_POTION

/** The colour of each effect a potion gives, as RGB, as the game has them. */
private val EFFECT_COLOURS = mapOf(
    Effect.SPEED to 0x33EBFF, Effect.SLOWNESS to 0x8BAFE0, Effect.STRENGTH to 0xFFC700, Effect.INSTANT_HEALTH to 0xF82423,
    Effect.INSTANT_DAMAGE to 0xA9656A, Effect.JUMP_BOOST to 0xFDFF84, Effect.REGENERATION to 0xCD5CAB, Effect.RESISTANCE to 0x9146F0,
    Effect.FIRE_RESISTANCE to 0xFF9900, Effect.WATER_BREATHING to 0x98DAC0, Effect.INVISIBILITY to 0xF6F6F6, Effect.NIGHT_VISION to 0xC2FF66,
    Effect.WEAKNESS to 0x484D48, Effect.POISON to 0x87A363, Effect.WITHER to 0x736156, Effect.SLOW_FALLING to 0xF3CFB9,
    Effect.WIND_CHARGED to 0xBDC9FF, Effect.WEAVING to 0x78695A, Effect.OOZING to 0x99FFA3, Effect.INFESTED to 0x8C9B8C,
)

/** The colour of water, which a potion of no effects is. */
private const val WATER_COLOUR = 0x385DC6

/**
 * The colour of the potion numbered [id], as ARGB, as a splash of it and
 * the cloud it leaves show: its effects' colours mixed, the stronger each,
 * the more of it, or water's, for a potion of none.
 */
fun potionColour(id: Int): Int {
    val effects = potionEffects(id).ifEmpty { return OPAQUE or WATER_COLOUR }
    val weight = effects.sumOf { it.level.amplifier + 1 }
    fun mixed(shift: Int) = effects.sumOf { ((EFFECT_COLOURS[it.level.effect] ?: WATER_COLOUR) shr shift and 0xFF) * (it.level.amplifier + 1) } / weight
    return OPAQUE or (mixed(16) shl 16) or (mixed(8) shl 8) or mixed(0)
}

private const val OPAQUE = 0xFF shl 24

/** How long an ominous bottle's Bad Omen lasts: 100 minutes. */
const val OMINOUS_TICKS = 100 * 60 * 20

/** The most an ominous bottle's level goes to, as its data value says: Bad Omen V. */
const val MAX_OMINOUS_AMPLIFIER = 4
