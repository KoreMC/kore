package org.koremc.world

/** How far across a lingering potion's cloud spreads at first, and the least it spreads before it's gone. */
const val CLOUD_RADIUS = 3f
private const val CLOUD_SMALLEST = 0.5f

/** How long a cloud waits before it starts working, and then lasts, in ticks: half a second, and thirty seconds. */
private const val CLOUD_WAIT = 10
private const val CLOUD_DURATION = 30 * 20

/** How much a cloud shrinks each time it reaches someone, and how long it waits before it reaches them again, in ticks, as the game's lingering potion has it. */
private const val CLOUD_SHRINK_ON_USE = 0.5f
private const val CLOUD_REAPPLICATION = 40

/** How high a cloud reaches above where it lies, and how often it looks for whoever's in it, in ticks. */
private const val CLOUD_HEIGHT = 0.5
private const val CLOUD_LOOKS_EVERY = 5

/** How often those who see a cloud hear how far it spreads as it shrinks, in ticks. */
private const val CLOUD_SHOWN_EVERY = 10

/** The entity [entityId] a [cloud] reached this tick. */
data class CloudTouch(val cloud: EffectCloud, val entityId: Long)

/**
 * The cloud a lingering potion leaves where it bursts: the entity [id],
 * lying at [position], of the potion numbered [potion], thrown by the
 * entity [owner]. It spreads [CLOUD_RADIUS] blocks at first, shrinking
 * away to nothing over its thirty seconds, and the faster for each time
 * its effects reach someone.
 */
class EffectCloud(val id: Long, val potion: Int, val position: Vec3, val owner: Long) {
    var radius = CLOUD_RADIUS
        private set

    /** Ticks since it formed. */
    var age = 0
        private set

    /** Whether those who see it should hear how far it spreads now. */
    var reshaped = false
        private set

    /** Those it reached, and the [age] it can reach each again. */
    private val reached = HashMap<Long, Int>()

    /** Whether reaching someone shrinks it: a cloud of a potion of no effects, like water, gives nothing away. */
    private val shrinksOnUse = potionEffects(potion).isNotEmpty()

    /**
     * Ticks it, adding whoever of [targets], boxes by their entity's ID, it
     * reaches to [touched]: once it's waited half a second, it shrinks a
     * little, and every quarter of a second, it reaches those whose box is
     * in it, within its radius across, but those it reached in the last two
     * seconds. Returns whether it's still there: one shrunk away, or that's
     * lasted out its time, is gone.
     */
    internal fun tick(targets: Map<Long, Box>, touched: MutableList<CloudTouch>): Boolean {
        age++
        reshaped = false
        if (age < CLOUD_WAIT) return true
        if (age >= CLOUD_WAIT + CLOUD_DURATION) return false
        radius -= CLOUD_RADIUS / CLOUD_DURATION
        if (radius < CLOUD_SMALLEST) return false
        if (age % CLOUD_SHOWN_EVERY == 0) reshaped = true
        if (age % CLOUD_LOOKS_EVERY != 0) return true
        reached.values.removeIf { it <= age }
        val box = Box(
            doubleArrayOf(position.x - radius.toDouble(), position.y.toDouble(), position.z - radius.toDouble()),
            doubleArrayOf(position.x + radius.toDouble(), position.y + CLOUD_HEIGHT, position.z + radius.toDouble()),
        )
        for ((id, target) in targets) {
            if (id in reached || !target.overlaps(box)) continue
            val feet = target.bottom()
            val dx = feet.x - position.x
            val dz = feet.z - position.z
            if (dx * dx + dz * dz > radius * radius) continue
            reached[id] = age + CLOUD_REAPPLICATION
            touched += CloudTouch(this, id)
            if (!shrinksOnUse) continue
            radius -= CLOUD_SHRINK_ON_USE
            reshaped = true
            if (radius < CLOUD_SMALLEST) return false
        }
        return true
    }
}

/** Ticks every cloud lingering potions left, as [EffectCloud.tick] does, at [targets]. Returns whom each reached. */
internal fun FlatWorld.tickEffectClouds(targets: Map<Long, Box>): List<CloudTouch> {
    val touched = mutableListOf<CloudTouch>()
    for (cloud in clouds.toList()) if (!cloud.tick(targets, touched)) removeCloud(cloud.id)
    return touched
}
