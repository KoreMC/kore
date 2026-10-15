package org.koremc.world

/** The effects Kore gives, by the numbers the game gives them. */
object Effect {
    const val SPEED = 1
    const val SLOWNESS = 2
    const val HASTE = 3
    const val STRENGTH = 5
    const val JUMP_BOOST = 8
    const val INSTANT_HEALTH = 6
    const val INSTANT_DAMAGE = 7
    const val REGENERATION = 10
    const val RESISTANCE = 11
    const val FIRE_RESISTANCE = 12
    const val WATER_BREATHING = 13
    const val INVISIBILITY = 14
    const val BLINDNESS = 15
    const val NIGHT_VISION = 16
    const val HUNGER = 17
    const val WEAKNESS = 18
    const val POISON = 19
    const val WITHER = 20
    const val HEALTH_BOOST = 21
    const val ABSORPTION = 22
    const val SATURATION = 23
    const val LEVITATION = 24
    const val FATAL_POISON = 25
    const val SLOW_FALLING = 26
    const val CONDUIT_POWER = 27
    const val BAD_OMEN = 28
    const val WIND_CHARGED = 33
    const val WEAVING = 34
    const val OOZING = 35
    const val INFESTED = 36
}

/** An effect at a strength: its amplifier, which is 0 for level I. */
data class EffectLevel(val effect: Int, val amplifier: Int = 0)

/**
 * An effect the game has: the name commands call it by, its number, and its
 * name as players see it, the key of it in their language after a `%`, or
 * as it is, for the few the game has no key for.
 */
class EffectKind(val name: String, val id: Int, val title: String)

/** Every effect, in the game's order. */
val EFFECT_KINDS = listOf(
    EffectKind("speed", 1, "%potion.moveSpeed"),
    EffectKind("slowness", 2, "%potion.moveSlowdown"),
    EffectKind("haste", 3, "%potion.digSpeed"),
    EffectKind("mining_fatigue", 4, "%potion.digSlowDown"),
    EffectKind("strength", 5, "%potion.damageBoost"),
    EffectKind("instant_health", 6, "%potion.heal"),
    EffectKind("instant_damage", 7, "%potion.harm"),
    EffectKind("jump_boost", 8, "%potion.jump"),
    EffectKind("nausea", 9, "%potion.confusion"),
    EffectKind("regeneration", 10, "%potion.regeneration"),
    EffectKind("resistance", 11, "%potion.resistance"),
    EffectKind("fire_resistance", 12, "%potion.fireResistance"),
    EffectKind("water_breathing", 13, "%potion.waterBreathing"),
    EffectKind("invisibility", 14, "%potion.invisibility"),
    EffectKind("blindness", 15, "%potion.blindness"),
    EffectKind("night_vision", 16, "%potion.nightVision"),
    EffectKind("hunger", 17, "%potion.hunger"),
    EffectKind("weakness", 18, "%potion.weakness"),
    EffectKind("poison", 19, "%potion.poison"),
    EffectKind("wither", 20, "%potion.wither"),
    EffectKind("health_boost", 21, "%potion.healthBoost"),
    EffectKind("absorption", 22, "%potion.absorption"),
    EffectKind("saturation", 23, "%potion.saturation"),
    EffectKind("levitation", 24, "%potion.levitation"),
    EffectKind("fatal_poison", 25, "%potion.poison"),
    EffectKind("slow_falling", 26, "%potion.slowFalling"),
    EffectKind("conduit_power", 27, "%potion.conduitPower"),
    EffectKind("bad_omen", 28, "%effect.badOmen"),
    EffectKind("village_hero", 29, "%effect.villageHero"),
    EffectKind("darkness", 30, "%effect.darkness"),
    EffectKind("trial_omen", 31, "Trial Omen"),
    EffectKind("raid_omen", 32, "Raid Omen"),
    EffectKind("wind_charged", 33, "%potion.windCharged"),
    EffectKind("weaving", 34, "%effect.weaving"),
    EffectKind("oozing", 35, "%effect.oozing"),
    EffectKind("infested", 36, "%effect.infested"),
)

/** How many ticks are left of an effect that lasts for ever, as the client counts them too. */
const val INFINITE_EFFECT = -1

/**
 * An effect on a player: how strong, how many ticks it has left, or
 * [INFINITE_EFFECT], whether it's ambient, as a beacon's is, and whether
 * its particles show.
 */
class ActiveEffect(val amplifier: Int, var ticksLeft: Int, val ambient: Boolean, val particles: Boolean = true)

/** The effects on one player, which wear off as they tick. */
class Effects {
    private val active = HashMap<Int, ActiveEffect>()

    operator fun get(effect: Int): ActiveEffect? = active[effect]

    /** The level of [effect] on the player, from I, or 0 if they don't have it. */
    fun level(effect: Int): Int = active[effect]?.let { it.amplifier + 1 } ?: 0

    /**
     * Gives the player [given] for [ticks]. One stronger than what they
     * have of it takes its place, and one as strong lasting longer than
     * what's left of theirs lengthens it. Returns whether that changed
     * anything.
     */
    fun give(given: EffectLevel, ticks: Int, ambient: Boolean = false): Boolean {
        val had = active[given.effect]
        if (had != null && (had.ticksLeft == INFINITE_EFFECT || had.amplifier > given.amplifier || (had.amplifier == given.amplifier && had.ticksLeft >= ticks))) return false
        active[given.effect] = ActiveEffect(given.amplifier, ticks, ambient)
        return true
    }

    /**
     * Gives the player [given] for [ticks], or for ever, for
     * [INFINITE_EFFECT], in place of whatever they have of it, as `/effect`
     * does, with its [particles] showing or not.
     */
    fun set(given: EffectLevel, ticks: Int, particles: Boolean = true) {
        active[given.effect] = ActiveEffect(given.amplifier, ticks, ambient = false, particles)
    }

    /** Takes [effect] away. Returns whether the player had it. */
    fun remove(effect: Int): Boolean = active.remove(effect) != null

    /**
     * The effects whose particles show around the player, as the client
     * takes them: up to eight, by number, seven bits each, the effect's
     * number then whether it's ambient.
     */
    fun visible(): Long = active.entries.filter { it.value.particles }.sortedBy { it.key }.take(MAX_VISIBLE).fold(0L) { packed, (effect, on) ->
        (packed shl 7) or (effect.toLong() shl 1) or (if (on.ambient) 1L else 0L)
    }

    /** Takes every effect away, as dying does. Returns the ones there were. */
    fun clear(): List<Int> = active.keys.toList().also { active.clear() }

    /** Counts every effect down a tick. Returns the ones that wore off. */
    fun tick(): List<Int> {
        val ended = mutableListOf<Int>()
        val each = active.entries.iterator()
        while (each.hasNext()) {
            val (effect, on) = each.next()
            if (on.ticksLeft != INFINITE_EFFECT && --on.ticksLeft <= 0) {
                each.remove()
                ended += effect
            }
        }
        return ended
    }
}

/** The most effects whose particles show around an actor at once. */
private const val MAX_VISIBLE = 8

/** How much faster each level of Haste makes a player mine. */
const val HASTE_PER_LEVEL = 0.2
