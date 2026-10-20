package org.koremc.world

import kotlinx.serialization.Serializable

/**
 * The game's systems Kore runs, each of which a server can switch off in
 * kore.jsonc to go without it: all on, the game as it is, and all off,
 * blocks that are only blocks. A system switched off doesn't run at all,
 * and the blocks that are part of it do nothing. What the game's own rules
 * already switch off, like the weather or blocks' drops, the game rules do.
 */
@Serializable
data class Features(
    /**
     * Power, and all it works: dust, torches, repeaters, comparators,
     * observers, lamps, pistons, pressure plates and daylight detectors, and
     * the doors, note blocks, dispensers and hoppers it moves. Off, levers and
     * buttons still flip, but power goes nowhere.
     */
    val redstone: Boolean = true,
    /** Water and lava flowing, what they make where they meet, and concrete powder hardening in water. Off, they stay as they're put. */
    val liquids: Boolean = true,
    /** Sand, gravel, anvils and the like falling with nothing under them. Off, they stay where they are, and hanging dripstone and snow break instead. */
    val fallingBlocks: Boolean = true,
    /** Random ticks, bone meal, trampling farmland, and snow and ice forming: whatever grows, spreads, dries, decays, melts, freezes or weathers. */
    val growth: Boolean = true,
    /** Furnaces, brewing stands, hoppers, dispensers, droppers, crafters and beacons working. Off, they keep what's put in them, and do nothing with it. */
    val machines: Boolean = true,
    /**
     * Players being hurt: by falling, the void, lava, fire and burning,
     * drowning, suffocating, magma, campfires, cactus and berry bushes,
     * poison and wither, and healing again over time. Off, nothing hurts them
     * but `/kill`.
     */
    val damage: Boolean = true,
    /**
     * The food bar going down as players sprint, swim, jump, mine and are
     * hurt, and starving them once it's empty. Off, it stays full.
     */
    val hunger: Boolean = true,
    /**
     * Eating and drinking: food and cake filling the food bar, and what
     * food, potions and milk give, like a golden apple's effects. Off,
     * they're only items, and cake only a block.
     */
    val food: Boolean = true,
    /**
     * Players hitting each other: the hit's damage, by weapon, Sharpness,
     * Strength and Weakness, critical hits, knockback and Fire Aspect, and
     * weapons wearing from it. Off, a hit does nothing. The pvp game rule
     * turns it off too, for a while.
     */
    val combat: Boolean = true,
    /**
     * Throwing and shooting: snowballs, eggs, ender pearls, wind charges,
     * splash and lingering potions, bows, crossbows and tridents, which fly
     * and hit players, an ender pearl taking whoever threw it where it
     * lands, and a wind charge or potion bursting. Off, they're only items.
     */
    val projectiles: Boolean = true,
) {
    /** The names of the systems switched off, as kore.jsonc names them. */
    val off: List<String>
        get() = listOfNotNull(
            "redstone".takeUnless { redstone }, "liquids".takeUnless { liquids }, "fallingBlocks".takeUnless { fallingBlocks },
            "growth".takeUnless { growth }, "machines".takeUnless { machines }, "damage".takeUnless { damage },
            "hunger".takeUnless { hunger }, "food".takeUnless { food }, "combat".takeUnless { combat }, "projectiles".takeUnless { projectiles },
        )
}
