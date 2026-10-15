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

/** How long an ominous bottle's Bad Omen lasts: 100 minutes. */
const val OMINOUS_TICKS = 100 * 60 * 20

/** The most an ominous bottle's level goes to, as its data value says: Bad Omen V. */
const val MAX_OMINOUS_AMPLIFIER = 4
