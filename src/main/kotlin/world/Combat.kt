package org.koremc.world

import org.koremc.registry.ItemStack
import org.koremc.registry.SHARPNESS
import org.koremc.registry.levelOf
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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

/** How many sparks fly from a critical hit, and glints from an enchanted weapon's hit. */
const val CRITICAL_HIT_PARTICLES = 55
const val ENCHANTED_HIT_PARTICLES = 15

/** A shield, as the game names it, and the kind of item its wait between uses is for, which an axe starts. */
const val SHIELD = "minecraft:shield"
const val SHIELD_COOLDOWN = "shield"

/** How long a shield takes to rise before it blocks, in ticks: a quarter of a second. */
const val SHIELD_RAISE_TICKS = 5

/** How long an axe's hit disables the shield that blocks it, in ticks: five seconds. */
const val SHIELD_DISABLED_TICKS = 5 * 20

/** The least a hit a shield blocks must do to wear it. */
private const val SHIELD_WEAR_FROM = 3f

/** How likely each level of Thorns makes a piece of armour prick whoever hurts its wearer. */
private const val THORNS_CHANCE_PER_LEVEL = 0.15f

/** The least and most a prick of Thorns hurts, which needn't be whole points. */
private const val THORNS_LEAST = 1f
private const val THORNS_MOST = 5f

/** How much more a piece of armour wears for each prick of its Thorns. */
const val THORNS_WEAR = 2

/** The least a fall must be, in blocks, for a mace's hit to smash, and the most for it to land lightly, as its sound has it. */
const val SMASH_FALL = 1.5f
const val HEAVY_SMASH_FALL = 5f

/** How much more each level of Density makes a smash hurt for each block fallen. */
private const val DENSITY_PER_BLOCK = 0.5f

/** The least a bow must be drawn to shoot, in ticks, and the least force it must have then. */
private const val LEAST_DRAW_TICKS = 3
private const val LEAST_FORCE = 0.1f

/** How fast an arrow leaves a bow drawn all the way, in blocks a tick, and a crossbow. */
const val BOW_SPEED = 3f
const val CROSSBOW_SPEED = 3.15f

/**
 * How fast a firework rocket leaves a crossbow, in blocks a tick, as a
 * dispenser fires one, which takes a rocket of the least flight a dozen
 * blocks or so before it bursts, as Bedrock's do, and how much a crossbow
 * wears for firing one.
 */
const val CROSSBOW_FIREWORK_SPEED = 0.5f
const val CROSSBOW_FIREWORK_WEAR = 3

/** How long a trident must be drawn back before it's thrown, in ticks, how fast it flies, in blocks a tick, and how hard it hits. */
const val TRIDENT_CHARGE_TICKS = 10
const val TRIDENT_SPEED = 4f
const val TRIDENT_DAMAGE = 8f

/** How much harder each level of Impaling makes a trident hit someone in water or rain. */
private const val IMPALING_PER_LEVEL = 2.5f

/** How long a crossbow takes to load, in ticks, and how much sooner each level of Quick Charge has it loaded. */
private const val CROSSBOW_CHARGE_TICKS = 25
private const val QUICK_CHARGE_TICKS = 5

/** How far either way of the middle a crossbow with Multishot shoots its other arrows, in degrees. */
const val MULTISHOT_SPREAD = 10f

/** How hard a plain arrow hurts at a block a tick, and how much more each level of Power after the first makes it. */
const val ARROW_DAMAGE = 2f
private const val POWER_PER_LEVEL = 0.5f

/** How much further each level of Punch knocks back what an arrow hits. */
const val PUNCH_PER_LEVEL = 0.25f

/** How long a burning arrow sets what it hits burning, in ticks: five seconds. */
const val FLAME_TICKS = 5 * 20

/** How much of a potion's effects a tipped arrow gives: an eighth of how long the potion's last. */
const val TIPPED_SHARE = 8

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

/** How much a shield wears from blocking a hit of [damage]: by the damage, rounded up, from 3 up, and not at all for less. */
fun shieldWear(damage: Float): Int = if (damage < SHIELD_WEAR_FROM) 0 else ceil(damage).toInt()

/** Whether a hit with [item] disables the shield that blocks it, as an axe's does. */
fun disablesShields(item: String?) = item?.endsWith("_axe") == true

/**
 * Whether a shield raised at [blocker], by someone facing the way [yaw]
 * says, covers a hit from [from]: anything in front of them, going by
 * where it is across, and not how high.
 */
fun shieldCovers(blocker: Vec3, yaw: Float, from: Vec3): Boolean {
    val facing = Math.toRadians(yaw.toDouble())
    return -sin(facing) * (from.x - blocker.x) + cos(facing) * (from.z - blocker.z) > 0
}

/**
 * How hard a piece of armour with Thorns at [level] pricks whoever just
 * hurt its wearer, or null if it doesn't this time: it does by chance,
 * 15% for each level, by anything from 1 to 5.
 */
fun thornsPrick(level: Int, random: Random = Random): Float? {
    if (level <= 0 || random.nextFloat() >= THORNS_CHANCE_PER_LEVEL * level) return null
    return THORNS_LEAST + random.nextFloat() * (THORNS_MOST - THORNS_LEAST)
}

/** Whether a hit with [item] smashes, struck from a fall: a mace's does. */
fun smashesWith(item: String?) = item == "minecraft:mace"

/**
 * How much more a mace's smash hurts for a fall of [fallen] blocks: 4 a
 * block for the first 3, 2 a block for the next 5, and 1 a block after
 * that, and half a point a block more for each level of [density].
 */
fun smashDamage(fallen: Float, density: Int): Float {
    val bonus = when {
        fallen <= 3f -> 4 * fallen
        fallen <= 8f -> 12 + 2 * (fallen - 3)
        else -> 22 + (fallen - 8)
    }
    return bonus + DENSITY_PER_BLOCK * density * fallen
}

/** How fast Wind Burst at [level] launches a player up after a smash, in blocks a tick: about seven blocks high at the first level. */
fun windBurstLift(level: Int): Float = when (level) {
    1 -> 1.2f
    2 -> 1.75f
    else -> 1.15f + 0.35f * level
}

/**
 * How hard a bow drawn for [ticks] shoots, from a tenth up to 1 for all
 * the way, which takes a second, or null if it's let go too soon to shoot.
 */
fun bowForce(ticks: Int): Float? {
    if (ticks < LEAST_DRAW_TICKS) return null
    val seconds = ticks / 20f
    return minOf((seconds * seconds + seconds * 2) / 3, 1f).takeIf { it >= LEAST_FORCE }
}

/** How long a crossbow with Quick Charge at [level] takes to load, in ticks: a second and a quarter, less a quarter a level. */
fun crossbowChargeTicks(level: Int) = maxOf(0, CROSSBOW_CHARGE_TICKS - QUICK_CHARGE_TICKS * level)

/** How many ticks into loading a crossbow with Quick Charge at [level] sounds partway: 16 of every 25 it takes. */
fun crossbowMiddleTick(level: Int) = (crossbowChargeTicks(level) * LOADING_MIDDLE_SHARE).roundToInt()

/** How far through loading a crossbow sounds partway. */
private const val LOADING_MIDDLE_SHARE = 16f / 25

/** How much harder Impaling at [level] makes a trident hit someone in water or rain: two and a half points a level, rounded down. */
fun impalingDamage(level: Int) = floor(IMPALING_PER_LEVEL * level)

/** How much harder Power at [level] makes an arrow hurt, at a block a tick. */
fun powerDamage(level: Int) = if (level > 0) POWER_PER_LEVEL * (level + 1) else 0f

/**
 * How hard an arrow of [damage] hurts, going at [speed] blocks a tick: its
 * damage times its speed, rounded up, and if it's [critical], by chance up
 * to half again and a point more.
 */
fun arrowDamage(damage: Float, speed: Float, critical: Boolean, random: Random = Random): Float {
    val hit = ceil(damage * speed)
    return if (critical) hit + random.nextInt(hit.toInt() / 2 + 2) else hit
}

/** How much of a hit's knockback a player wearing [armour] shakes off, from 0 to 1. */
fun knockbackResistance(armour: List<ItemStack?>): Float =
    (armour.count { it?.item?.name?.startsWith("minecraft:netherite_") == true } * NETHERITE_KNOCKBACK_RESISTANCE).coerceAtMost(1f)
