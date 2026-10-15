package org.koremc.world

import org.koremc.registry.ItemStack
import org.koremc.registry.SHARPNESS
import org.koremc.registry.levelOf
import kotlin.math.floor

/** How hard a bare hand hits, and anything that isn't a weapon or tool: half a heart. */
private const val HAND_DAMAGE = 1f

/** The materials weapons and tools come in, weakest first, as the damage tables run. */
private val MATERIALS = listOf("wooden", "golden", "stone", "copper", "iron", "diamond", "netherite")

/**
 * How hard each weapon and tool hits, as Bedrock has it, a point more than
 * its tooltip says: by kind, for each of [MATERIALS] in turn.
 */
private val DAMAGE_BY_KIND = mapOf(
    "sword" to listOf(5, 5, 6, 6, 7, 8, 9),
    "axe" to listOf(4, 4, 5, 5, 6, 7, 8),
    "spear" to listOf(2, 2, 3, 3, 4, 5, 6),
    "pickaxe" to listOf(3, 3, 4, 4, 5, 6, 7),
    "shovel" to listOf(2, 2, 3, 3, 4, 5, 6),
    "hoe" to listOf(3, 3, 4, 4, 5, 6, 7),
)

private val WEAPON_DAMAGE: Map<String, Float> = DAMAGE_BY_KIND.flatMap { (kind, damage) ->
    MATERIALS.zip(damage) { material, points -> "minecraft:${material}_$kind" to points.toFloat() }
}.toMap() + mapOf("minecraft:trident" to 9f, "minecraft:mace" to 6f)

/** Kinds of tool, rather than weapon, which wear twice as much from a hit. */
private val TOOL_KINDS = setOf("axe", "pickaxe", "shovel", "hoe")

/** How much harder a critical hit is, and how much more each level of Sharpness hits. */
private const val CRITICAL_MULTIPLIER = 1.5f
private const val SHARPNESS_PER_LEVEL = 1.25f

/** What each level of Strength and Weakness does to a hit, as Bedrock has it: a share more or less, and a little besides. */
private const val STRENGTH_MULTIPLIER = 1.3f
private const val STRENGTH_ADDED = 1f
private const val WEAKNESS_MULTIPLIER = 0.8f
private const val WEAKNESS_TAKEN = 0.5f

/** How far a hit knocks a player back, in blocks a tick across and up, and how much further each level of Knockback, or a sprint, does. */
const val KNOCKBACK_FORCE = 0.45f
const val KNOCKBACK_HEIGHT = 0.3608f
const val KNOCKBACK_PER_LEVEL = 0.5f

/** How much of a hit's knockback each piece of netherite armour takes. */
private const val NETHERITE_KNOCKBACK_RESISTANCE = 0.1f

/** How long each level of Fire Aspect sets what's hit burning, in ticks: four seconds. */
const val FIRE_ASPECT_TICKS = 4 * 20

/** How hard [held], or a bare hand, hits, before effects and enchantments. */
fun attackDamage(held: ItemStack?): Float = held?.let { WEAPON_DAMAGE[it.item.name] } ?: HAND_DAMAGE

/** How much a hit wears [item], a weapon or tool: weapons a use, tools two, and anything else nothing. */
fun wearFromHitting(item: String): Int = when {
    item !in WEAPON_DAMAGE -> 0
    item.substringAfterLast('_') in TOOL_KINDS -> 2
    else -> 1
}

/** Weapons that can't break blocks in creative, where a click with one only hits: swords, the mace and the trident. */
fun breaksInCreative(item: String?) = item == null || !(item.endsWith("_sword") || item == "minecraft:mace" || item == "minecraft:trident")

/** Whether a hit with [item] can be critical: any, but a spear's. */
fun critsWith(item: String?) = item == null || !item.endsWith("_spear")

/**
 * How hard a hit of [damage] is, with Strength at [strength] and Weakness
 * at [weakness], each a level above 0, as Bedrock has it: each level of
 * Strength makes it 30% harder and a point more, and each of Weakness a
 * fifth softer and half a point less, though never less than nothing.
 */
fun withStrength(damage: Float, strength: Int, weakness: Int): Float {
    var hit = damage
    repeat(strength) { hit = hit * STRENGTH_MULTIPLIER + STRENGTH_ADDED }
    repeat(weakness) { hit = hit * WEAKNESS_MULTIPLIER - WEAKNESS_TAKEN }
    return maxOf(hit, 0f)
}

/** How much more [held] hits for its Sharpness: 1.25 a level, rounded down, as Bedrock has it. */
fun sharpnessDamage(held: ItemStack?): Float = floor((held?.levelOf(SHARPNESS) ?: 0) * SHARPNESS_PER_LEVEL)

/** A hit of [damage] made critical. */
fun critical(damage: Float) = damage * CRITICAL_MULTIPLIER

/** How much of a hit's knockback a player wearing [armour] shakes off, from 0 to 1. */
fun knockbackResistance(armour: List<ItemStack?>): Float =
    (armour.count { it?.item?.name?.startsWith("minecraft:netherite_") == true } * NETHERITE_KNOCKBACK_RESISTANCE).coerceAtMost(1f)
