# Changelog

Each release, newest first, with the client version it's for. What's done since the last release waits under Unreleased.

## Unreleased

- Players fight each other: hits hurt by the weapon, with Sharpness, Strength and Weakness, critical hits, knockback and Fire Aspect.
  The pvp game rule and a `combat` switch in `kore.jsonc` turn it off
- A Totem of Undying in either hand saves a player from dying, but in the void
- Shields: sneaking raises one, in either hand, which blocks hits from in front once it's up, wearing for it and knocking the
  attacker back. An axe's hit disables it for five seconds
- Thorns: each piece of armour with it may prick whoever hurts its wearer, hurting and knocking them back, and wears for it
- The console no longer shows players dying or respawning, only what they say and the commands they run
- Players join and respawn facing south, looking straight ahead
- Kore's own settings are in `kore.jsonc`, so editors take the comments in it. A `kore.json` from 0.1.0 is renamed to it
- Armour worn by a hurt, and items Mending repairs, show their new durability at once, not once they're moved
- What `/clear` or a used-up totem takes from the offhand goes from it, rather than seeming to stay

## 0.1.0

The first release, for Minecraft Bedrock **1.26.50** (protocol 2193). Kore is early: play it with friends, not on a public server yet.

### What works

- Joining over NetherNet with the Bedrock client, with online-mode authentication
- Flat worlds from the game's presets, saved and loaded as the game saves them
- Survival and creative: building, mining with tool wear, items and inventories, and the creative inventory
- Chests, furnaces, brewing stands, anvils, enchanting tables and the other workstations, and crafting
- Hoppers, dispensers, droppers and crafters
- Redstone, pistons, pressure plates and lamps
- Water, lava and fire; crops, trees and growth; time and weather
- Health, damage, death and respawning
- Hunger, eating and drinking: food, potions, milk and cake
- Beacons, maps, lecterns and books, note blocks and jukeboxes
- Players seeing each other, chat, and the game's commands with entity selectors
- Switches in `kore.json` to turn whole systems off, like redstone, hunger or food

### Not yet

- Mobs
- Players hitting each other
- Movement checks: the server takes the client's word for where a player is
- Worlds other than flat ones, and the Nether and the End
