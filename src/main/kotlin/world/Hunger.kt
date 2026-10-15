package org.koremc.world

/** A full food bar, and the saturation a new player starts with. */
const val MAX_FOOD = 20
const val STARTING_SATURATION = 5f

/** How much food a player needs to heal by themselves. */
const val WELL_FED = 18

/** How much exhaustion takes a point of saturation, or of food once saturation's gone, and the most a player can have. */
private const val EXHAUSTION_PER_POINT = 4f
private const val MAX_EXHAUSTION = 40f

// How much what players do tires them, as the game has it: sprinting and swimming by the block, a jump, a sprinting jump,
// breaking a block, a hurt, healing a point by themselves, and the Hunger effect a tick, at level I.
const val SPRINT_EXHAUSTION = 0.1f
const val SWIM_EXHAUSTION = 0.01f
const val JUMP_EXHAUSTION = 0.05f
const val SPRINT_JUMP_EXHAUSTION = 0.2f
const val BREAK_EXHAUSTION = 0.005f
const val HURT_EXHAUSTION = 0.1f
const val REGENERATION_EXHAUSTION = 6f
const val HUNGER_EFFECT_EXHAUSTION = 0.005f

/**
 * A player's hunger: their food bar, [food], up to 20; the saturation that
 * keeps it from going down, [saturation], never more than the food they
 * have; and how tired they are, [exhaustion], which for every 4 takes a
 * point of saturation, or of food once saturation's gone, unless they're
 * at peace.
 */
class Hunger {
    var food = MAX_FOOD
    var saturation = STARTING_SATURATION
    var exhaustion = 0f

    val full get() = food >= MAX_FOOD

    /** Tires the player by [amount]. In [peace], their food bar never goes down. */
    fun exhaust(amount: Float, peace: Boolean) {
        exhaustion = minOf(exhaustion + amount, MAX_EXHAUSTION)
        while (exhaustion >= EXHAUSTION_PER_POINT) {
            exhaustion -= EXHAUSTION_PER_POINT
            if (saturation > 0) saturation = maxOf(saturation - 1, 0f) else if (!peace) food = maxOf(food - 1, 0)
        }
    }

    /**
     * Eats [nutrition] points of food, up to a full bar, which brings
     * saturation of twice [saturationModifier] a point, as the game counts
     * it, though never more than the food they have.
     */
    fun eat(nutrition: Int, saturationModifier: Float) {
        food = minOf(food + nutrition, MAX_FOOD)
        saturation = minOf(saturation + nutrition * saturationModifier * 2, food.toFloat())
    }

    /** A full bar again, as a player respawns. */
    fun reset() {
        food = MAX_FOOD
        saturation = STARTING_SATURATION
        exhaustion = 0f
    }
}
