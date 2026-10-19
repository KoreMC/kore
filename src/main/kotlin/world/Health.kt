package org.koremc.world

import org.koremc.registry.FEATHER_FALLING
import org.koremc.registry.FIRE_PROTECTION
import org.koremc.registry.ItemStack
import org.koremc.registry.PROTECTION
import org.koremc.registry.levelOf
import kotlin.math.floor

/** A player's health when whole, in half hearts, before Health Boost adds to it. */
const val MAX_HEALTH = 20f

/** How long after a hurt only a harder blow hurts a player, in ticks: half a second. */
private const val HURT_GRACE = 10

/** The most protection enchantments on what a player wears count for, each point taking a twenty-fifth off. */
private const val MAX_PROTECTION = 20

/**
 * What hurt a player: the game's number for it, which the client hears
 * with the hurt and picks its sound by, so burning and drowning sound as
 * they do; the key of the message that says they died of it; whether
 * armour takes a share of it; whether it's fire, which Fire Protection
 * lessens, or a fall, which Feather Falling does; and whether Resistance
 * lessens it. Nothing lessens the void.
 */
enum class DamageCause(
    val id: Int,
    val message: String,
    val armour: Boolean,
    val fire: Boolean = false,
    val fall: Boolean = false,
    val resisted: Boolean = true,
    val void: Boolean = false,
) {
    FALL(5, "death.attack.fall", armour = false, fall = true),
    VOID(12, "death.attack.outOfWorld", armour = false, resisted = false, void = true),
    LAVA(8, "death.attack.lava", armour = true, fire = true),
    FIRE(6, "death.attack.inFire", armour = true, fire = true),
    BURNING(7, "death.attack.onFire", armour = false, fire = true),
    MAGMA(22, "death.attack.magma", armour = true, fire = true),
    // The game counts both as touching what pricks.
    CACTUS(1, "death.attack.cactus", armour = true),
    BERRY_BUSH(1, "death.attack.sweetBerry", armour = true),
    SUFFOCATION(4, "death.attack.inWall", armour = false),
    DROWNING(9, "death.attack.drown", armour = false),
    LIGHTNING(24, "death.attack.lightningBolt", armour = true),
    // A hit from another player, and a prick from the Thorns of one hit, whose name the message has too.
    ATTACK(2, "death.attack.player", armour = true),
    THORNS(18, "death.attack.thorns", armour = true),
    // Something another player threw or shot, which the message names them for.
    PROJECTILE(3, "death.attack.thrown", armour = true),
    ARROW(3, "death.attack.arrow", armour = true),
    MAGIC(14, "death.attack.magic", armour = false),
    WITHER(15, "death.attack.wither", armour = false),
    STARVATION(16, "death.attack.starve", armour = false, resisted = false),
}

/** Each kind of armour's points, worn on the head, chest, legs and feet, and its toughness, which keeps it from giving way to a hard blow. */
private class ArmourKind(val points: IntArray, val toughness: Int = 0)

private val ARMOUR = mapOf(
    "leather" to ArmourKind(intArrayOf(1, 3, 2, 1)),
    "chainmail" to ArmourKind(intArrayOf(2, 5, 4, 1)),
    "copper" to ArmourKind(intArrayOf(2, 4, 3, 1)),
    "iron" to ArmourKind(intArrayOf(2, 6, 5, 2)),
    "golden" to ArmourKind(intArrayOf(2, 5, 3, 1)),
    "diamond" to ArmourKind(intArrayOf(3, 8, 6, 3), toughness = 2),
    "netherite" to ArmourKind(intArrayOf(3, 8, 6, 3), toughness = 3),
)

private val PIECES = listOf("helmet", "chestplate", "leggings", "boots")

/** The armour points and toughness [item] gives worn, or none if it isn't armour. */
fun armourOf(item: String): Pair<Int, Int> {
    val name = item.removePrefix("minecraft:")
    if (name == "turtle_helmet") return 2 to 0
    val piece = PIECES.indexOf(name.substringAfterLast('_'))
    val kind = ARMOUR[name.substringBeforeLast('_')]
    return if (piece < 0 || kind == null) 0 to 0 else kind.points[piece] to kind.toughness
}

/**
 * [amount] of damage of [cause], as it comes through what a player has
 * [worn], with [resistance] levels of Resistance, as the game works it out:
 * armour takes a twenty-fifth for each of its points, but a hard blow
 * gets through more of it, less so the tougher it is, though it always
 * takes a fifth of what it would, and 15% less for each level of [breach]
 * on what struck; Resistance a fifth for each level; and
 * protection enchantments a twenty-fifth for each point of protection,
 * up to twenty: Protection one a level, Fire Protection two against
 * fire, and Feather Falling three against a fall.
 */
fun damageAfter(amount: Float, cause: DamageCause, worn: List<ItemStack?>, resistance: Int, breach: Int = 0): Float {
    var damage = amount
    val wearing = worn.filterNotNull()
    if (cause.armour) {
        val armour = wearing.map { armourOf(it.item.name) }
        val points = armour.sumOf { it.first }.toFloat()
        val toughness = armour.sumOf { it.second }.toFloat()
        val blocked = (points - damage / (2 + toughness / 4)).coerceIn(points / 5, MAX_PROTECTION.toFloat())
        damage *= 1 - blocked / 25 * (1 - BREACH_PER_LEVEL * breach).coerceAtLeast(0f)
    }
    if (cause.resisted && resistance > 0) damage *= maxOf(0, 25 - resistance * 5) / 25f
    if (!cause.void) {
        val protection = wearing.sumOf { stack ->
            stack.levelOf(PROTECTION) + (if (cause.fire) 2 * stack.levelOf(FIRE_PROTECTION) else 0) + (if (cause.fall) 3 * stack.levelOf(FEATHER_FALLING) else 0)
        }
        damage *= 1 - minOf(protection, MAX_PROTECTION) / 25f
    }
    return damage
}

/** How much less of a blow each level of Breach on what struck it lets armour keep off. */
private const val BREACH_PER_LEVEL = 0.15f

/** How many uses a blow of [amount] wears each piece of armour by: one for every four, and at least one. */
fun armourWear(amount: Float): Int = maxOf(1, floor(amount / 4).toInt())

/**
 * A player's health, in half hearts, up to [maxHealth], with [absorption]
 * on top of it, which a hurt takes first; their breath, in ticks, which
 * runs out underwater; and how long they burn for, in ticks. After a hurt,
 * a moment's grace holds off any blow but a harder one, which hurts only
 * by how much harder it is.
 */
class Vitals {
    var health = MAX_HEALTH
    var maxHealth = MAX_HEALTH
        set(value) {
            field = value
            health = minOf(health, value)
        }
    var absorption = 0f
    var air = PLAYER_MAX_AIR.toInt()
    var fireTicks = 0

    /** How many ticks are left of the grace after the last hurt, and how hard that hurt was. */
    private var grace = 0
    private var lastHurt = 0f

    val isDead get() = health <= 0

    /** Whether the grace after the last hurt is still on, when only a harder one hurts, and by only as much harder as it is. */
    val inGrace get() = grace > 0

    /**
     * Takes [damage], from absorption first and then health, unless the
     * grace after the last hurt holds it off. Returns what it took, or null
     * for none.
     */
    fun hurt(damage: Float): Float? {
        // A hurt of nothing, like a snowball's, still starts the grace.
        if (damage < 0) return null
        val taken = if (grace > 0) {
            if (damage <= lastHurt) return null
            damage - lastHurt
        } else {
            grace = HURT_GRACE
            damage
        }
        lastHurt = damage
        val absorbed = minOf(absorption, taken)
        absorption -= absorbed
        health = maxOf(0f, health - (taken - absorbed))
        return taken
    }

    /** Heals [amount], up to [maxHealth]. Returns whether it healed anything. */
    fun heal(amount: Float): Boolean {
        if (health >= maxHealth || isDead) return false
        health = minOf(maxHealth, health + amount)
        return true
    }

    /** Counts the grace after a hurt down a tick. */
    fun tick() {
        if (grace > 0) grace--
    }

    /** Whole again, breathing and not burning, as a player respawns. */
    fun reset() {
        health = maxHealth
        absorption = 0f
        air = PLAYER_MAX_AIR.toInt()
        fireTicks = 0
        grace = 0
        lastHurt = 0f
    }
}

/**
 * What a player's body touches that can hurt them, or put out their fire:
 * [lava]; [fire], as the harm it does a tick, more for soul fire, and a lit
 * campfire's [campfire] likewise; a [cactus] or a [berryBush] with berries
 * growing on it; [water], and whether their eyes are under it, [underwater];
 * a [magma] block under their feet; and whether their eyes are inside a
 * solid block, [suffocating].
 */
class Surroundings(
    val lava: Boolean = false,
    val fire: Int = 0,
    val campfire: Int = 0,
    val cactus: Boolean = false,
    val berryBush: Boolean = false,
    val water: Boolean = false,
    val underwater: Boolean = false,
    val magma: Boolean = false,
    val suffocating: Boolean = false,
)

/** How far past a player's sides a cactus pricks them, which stands a sixteenth inside its block. */
private const val CACTUS_REACH = 0.0625

/** How far below a player's feet the block they stand on is looked for. */
private const val UNDERFOOT = 0.01

private const val MAGMA = "minecraft:magma"
private const val SOUL_CAMPFIRE = "minecraft:soul_campfire"

/**
 * What a player's body touches, standing with their [feet] where they are,
 * [height] tall, their [eyes] where they are: what's in the blocks their box
 * reaches into, the block under them, and the block their eyes are in.
 */
fun FlatWorld.surroundings(feet: Vec3, height: Float, eyes: Vec3): Surroundings {
    val half = PLAYER_WIDTH / 2.0
    val reach = half + CACTUS_REACH
    var lava = false
    var fire = 0
    var campfire = 0
    var cactus = false
    var berryBush = false
    var water = false
    for (x in floor(feet.x - reach).toInt()..floor(feet.x + reach).toInt()) {
        for (z in floor(feet.z - reach).toInt()..floor(feet.z + reach).toInt()) {
            for (y in floor(feet.y.toDouble()).toInt()..floor(feet.y + height - 0.001).toInt()) {
                if (y !in MIN_Y..MAX_Y) continue
                val pos = BlockPos(x, y, z)
                // Only a cactus reaches further than the block it's in.
                val inside = x + 1 > feet.x - half && x < feet.x + half && z + 1 > feet.z - half && z < feet.z + half
                val state = stateAt(pos) ?: continue
                if (state.name == CACTUS) cactus = true
                if (!inside) continue
                val here = liquidHere(pos)
                if (here?.liquid == Liquid.LAVA && feet.y < liquidTop(pos, here)) lava = true
                if (here?.liquid == Liquid.WATER) water = true
                if (state.isFire) fire = maxOf(fire, if (state.name == SOUL_FIRE) 2 else 1)
                if (state.name.endsWith("campfire") && state.properties[CAMPFIRE_OUT] == 0.toByte()) campfire = maxOf(campfire, if (state.name == SOUL_CAMPFIRE) 2 else 1)
                if (state.name == SWEET_BERRY_BUSH && (state.properties[GROWTH] as? Int ?: 0) > 0) berryBush = true
            }
        }
    }
    val eyeBlock = eyes.toBlockPos()
    val atEyes = stateAt(eyeBlock)
    val eyeLiquid = liquidHere(eyeBlock)
    return Surroundings(
        lava = lava, fire = fire, campfire = campfire, cactus = cactus, berryBush = berryBush, water = water,
        underwater = eyeLiquid?.liquid == Liquid.WATER && eyes.y < liquidTop(eyeBlock, eyeLiquid),
        magma = stateAt(Vec3(feet.x, feet.y - UNDERFOOT.toFloat(), feet.z).toBlockPos())?.name == MAGMA,
        suffocating = atEyes != null && atEyes.isFullCube && atEyes.solidHeights() != null && dampening(atEyes) >= MAX_LIGHT,
    )
}
