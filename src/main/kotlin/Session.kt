package org.koremc

import org.cloudburstmc.nbt.NbtMap
import org.cloudburstmc.nbt.NbtType
import org.koremc.command.CommandSource
import org.koremc.command.Commands
import org.koremc.command.ConsoleSource
import org.koremc.command.Operators
import org.koremc.command.Selectable
import org.koremc.command.logOutput
import org.koremc.inventory.CURSOR_SLOT
import org.koremc.inventory.Container
import org.koremc.inventory.Crafted
import org.koremc.inventory.Crafter
import org.koremc.inventory.ENCHANTING_INPUT_SLOT
import org.koremc.inventory.EnchantingOffer
import org.koremc.inventory.HOTBAR_SIZE
import org.koremc.inventory.Location
import org.koremc.inventory.MAX_BOOKSHELVES
import org.koremc.inventory.OpenContainer
import org.koremc.inventory.PairedContainer
import org.koremc.inventory.PlayerInventory
import org.koremc.inventory.SlotStack
import org.koremc.inventory.StackRequestHandler
import org.koremc.inventory.Workstation
import org.koremc.inventory.offersFor
import org.koremc.inventory.runes
import org.koremc.inventory.writeInBook
import org.koremc.network.DisconnectReason
import org.koremc.network.NetherNetConnection
import org.koremc.protocol.ARMOUR_WINDOW
import org.koremc.protocol.Ability
import org.koremc.protocol.ActorDataId
import org.koremc.protocol.ActorEvent
import org.koremc.protocol.ActorFlag
import org.koremc.protocol.AddExperienceOrb
import org.koremc.protocol.AddFallingBlock
import org.koremc.protocol.AddItemActor
import org.koremc.protocol.AddLightning
import org.koremc.protocol.AddPlayer
import org.koremc.protocol.AddProjectile
import org.koremc.protocol.Animate
import org.koremc.protocol.Attribute
import org.koremc.protocol.BatchCodec
import org.koremc.protocol.BlockAction
import org.koremc.protocol.BlockActorData
import org.koremc.protocol.BlockEvent
import org.koremc.protocol.BlockPick
import org.koremc.protocol.BookAction
import org.koremc.protocol.BookEdit
import org.koremc.protocol.ByteItem
import org.koremc.protocol.ChunkRadiusUpdated
import org.koremc.protocol.ClientCacheStatus
import org.koremc.protocol.ClientPacket
import org.koremc.protocol.ClientboundMapItemData
import org.koremc.protocol.CommandOutput
import org.koremc.protocol.CommandPermission
import org.koremc.protocol.CommandRequest
import org.koremc.protocol.ContainerClose
import org.koremc.protocol.ContainerName
import org.koremc.protocol.ContainerOpen
import org.koremc.protocol.ContainerSetData
import org.koremc.protocol.ContainerType
import org.koremc.protocol.DeathInfo
import org.koremc.protocol.Difficulty
import org.koremc.protocol.Disconnect
import org.koremc.protocol.EditedBlockData
import org.koremc.protocol.EnchantOption
import org.koremc.protocol.EnumProperty
import org.koremc.protocol.FloatItem
import org.koremc.protocol.GameMode
import org.koremc.protocol.GameRulesChanged
import org.koremc.protocol.GameVersion
import org.koremc.protocol.InputFlag
import org.koremc.protocol.Interact
import org.koremc.protocol.InventoryAction
import org.koremc.protocol.InventoryContent
import org.koremc.protocol.InventorySlot
import org.koremc.protocol.InventoryTransaction
import org.koremc.protocol.ItemFrameDropItem
import org.koremc.protocol.ItemStackRequest
import org.koremc.protocol.ItemStackResponse
import org.koremc.protocol.ItemUse
import org.koremc.protocol.ItemUseAction
import org.koremc.protocol.ItemUseOnEntity
import org.koremc.protocol.JigsawStructureData
import org.koremc.protocol.LIGHTNING_BOLT
import org.koremc.protocol.LecternUpdate
import org.koremc.protocol.LevelChunk
import org.koremc.protocol.LevelEvent
import org.koremc.protocol.LevelSoundEvent
import org.koremc.protocol.LoadingScreen
import org.koremc.protocol.Login
import org.koremc.protocol.LoginRequest
import org.koremc.protocol.LongItem
import org.koremc.protocol.MapCreateLockedCopy
import org.koremc.protocol.MapInfoRequest
import org.koremc.protocol.MapPatch
import org.koremc.protocol.MobEffect
import org.koremc.protocol.MobEquipment
import org.koremc.protocol.MoveActorAbsolute
import org.koremc.protocol.MovePlayer
import org.koremc.protocol.NetworkChunkPublisherUpdate
import org.koremc.protocol.NetworkSettings
import org.koremc.protocol.OFFHAND_WINDOW
import org.koremc.protocol.OfferedEnchantment
import org.koremc.protocol.OpenSign
import org.koremc.protocol.PacketException
import org.koremc.protocol.PacketViolation
import org.koremc.protocol.PermissionLevel
import org.koremc.protocol.PlaySound
import org.koremc.protocol.PlayStatus
import org.koremc.protocol.PlayerAction
import org.koremc.protocol.PlayerAuthInput
import org.koremc.protocol.PlayerEnchantOptions
import org.koremc.protocol.PlayerList
import org.koremc.protocol.PlayerListEntry
import org.koremc.protocol.PlayerStartItemCooldown
import org.koremc.protocol.RecordStarted
import org.koremc.protocol.RemoveActor
import org.koremc.protocol.RequestChunkRadius
import org.koremc.protocol.RequestNetworkSettings
import org.koremc.protocol.ResourcePackClientResponse
import org.koremc.protocol.ResourcePackStack
import org.koremc.protocol.ResourcePacksInfo
import org.koremc.protocol.Respawn
import org.koremc.protocol.ServerPacket
import org.koremc.protocol.SetActorData
import org.koremc.protocol.SetActorMotion
import org.koremc.protocol.SetLocalPlayerAsInitialised
import org.koremc.protocol.SetPlayerGameType
import org.koremc.protocol.SetSpawnPosition
import org.koremc.protocol.SetTime
import org.koremc.protocol.ShortItem
import org.koremc.protocol.ShowArmour
import org.koremc.protocol.ShowEquipment
import org.koremc.protocol.Skin
import org.koremc.protocol.StackRequest
import org.koremc.protocol.StartGame
import org.koremc.protocol.StringItem
import org.koremc.protocol.SubChunk
import org.koremc.protocol.SubChunkEntry
import org.koremc.protocol.SubChunkOffset
import org.koremc.protocol.SubChunkRequest
import org.koremc.protocol.SubChunkResult
import org.koremc.protocol.SyncActorProperty
import org.koremc.protocol.TakeItemActor
import org.koremc.protocol.Text
import org.koremc.protocol.ToggleCrafterSlot
import org.koremc.protocol.UI_WINDOW
import org.koremc.protocol.UpdateAbilities
import org.koremc.protocol.UpdateAttributes
import org.koremc.protocol.UpdateBlock
import org.koremc.protocol.UpdatePlayerGameType
import org.koremc.protocol.UpdateSoundData
import org.koremc.protocol.VoxelShapes
import org.koremc.protocol.abilitiesFor
import org.koremc.protocol.decodeClientPacket
import org.koremc.protocol.encode
import org.koremc.protocol.flagsItem
import org.koremc.protocol.parseConnectionRequest
import org.koremc.protocol.secondFlagsItem
import org.koremc.protocol.skinFrom
import org.koremc.protocol.subChunkPayload
import org.koremc.registry.AIR
import org.koremc.registry.AIR_NETWORK_ID
import org.koremc.registry.BREACH
import org.koremc.registry.BlockState
import org.koremc.registry.CHANNELING
import org.koremc.registry.DENSITY
import org.koremc.registry.EFFICIENCY
import org.koremc.registry.FIRE_ASPECT
import org.koremc.registry.FIRE_PROTECTION
import org.koremc.registry.FLAME
import org.koremc.registry.FROST_WALKER
import org.koremc.registry.Food
import org.koremc.registry.FoodAction
import org.koremc.registry.IMPALING
import org.koremc.registry.INFINITY
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.KNOCKBACK
import org.koremc.registry.MENDING
import org.koremc.registry.MULTISHOT
import org.koremc.registry.PIERCING
import org.koremc.registry.POWER
import org.koremc.registry.PUNCH
import org.koremc.registry.QUICK_CHARGE
import org.koremc.registry.RESPIRATION
import org.koremc.registry.RIPTIDE
import org.koremc.registry.THORNS
import org.koremc.registry.VANISHING
import org.koremc.registry.WIND_BURST
import org.koremc.registry.customName
import org.koremc.registry.damage
import org.koremc.registry.enchantments
import org.koremc.registry.levelOf
import org.koremc.registry.mendedBy
import org.koremc.registry.sameKindAs
import org.koremc.registry.suspiciousStewEffect
import org.koremc.registry.worn
import org.koremc.world.ARROW_DAMAGE
import org.koremc.world.ATTACK_EXHAUSTION
import org.koremc.world.ArrowShot
import org.koremc.world.BARREL
import org.koremc.world.BARREL_OPEN
import org.koremc.world.BEACON_INTERVAL
import org.koremc.world.BONE_MEAL
import org.koremc.world.BOOK_PUT
import org.koremc.world.BOW_SPEED
import org.koremc.world.BREAK_EXHAUSTION
import org.koremc.world.BREWING_BOTTLES
import org.koremc.world.BREWING_FUEL
import org.koremc.world.BREWING_REAGENT
import org.koremc.world.BREWING_STAND
import org.koremc.world.BREWS_PER_FUEL
import org.koremc.world.BlockPos
import org.koremc.world.BlockSound
import org.koremc.world.Box
import org.koremc.world.BrewingStand
import org.koremc.world.BrokenBlock
import org.koremc.world.CAKE_NUTRITION
import org.koremc.world.CAKE_SATURATION
import org.koremc.world.CLIMATE_VARIANT
import org.koremc.world.CLIMATE_VARIANTS
import org.koremc.world.CRAFTER_SLOTS
import org.koremc.world.CRITICAL_HIT_PARTICLES
import org.koremc.world.CROSSBOW_SPEED
import org.koremc.world.Cartographed
import org.koremc.world.Changes
import org.koremc.world.ChunkPos
import org.koremc.world.Crafter as CrafterMachine
import org.koremc.world.DISPENSER_SLOTS
import org.koremc.world.DamageCause
import org.koremc.world.Dispenser
import org.koremc.world.EFFECT_KINDS
import org.koremc.world.EGG_ENTITY
import org.koremc.world.EMPTY_MAP
import org.koremc.world.ENCHANTED_HIT_PARTICLES
import org.koremc.world.ENDER_EYE
import org.koremc.world.ENDER_PEARL_DAMAGE
import org.koremc.world.Effect
import org.koremc.world.EffectLevel
import org.koremc.world.Effects
import org.koremc.world.ExperienceOrb
import org.koremc.world.FALLING_BLOCK_SIZE
import org.koremc.world.FILLED_MAP
import org.koremc.world.FIRE_ASPECT_TICKS
import org.koremc.world.FIRE_CHARGE
import org.koremc.world.FLAME_TICKS
import org.koremc.world.FLINT_AND_STEEL
import org.koremc.world.FURNACE_FUEL
import org.koremc.world.FURNACE_INPUT
import org.koremc.world.FURNACE_OUTPUT
import org.koremc.world.Face
import org.koremc.world.Fired
import org.koremc.world.FlatWorld
import org.koremc.world.Furnace
import org.koremc.world.GROWTH
import org.koremc.world.HEAVY_SMASH_FALL
import org.koremc.world.HOPPER
import org.koremc.world.HOPPER_SLOTS
import org.koremc.world.HUNGER_EFFECT_EXHAUSTION
import org.koremc.world.HURT_EXHAUSTION
import org.koremc.world.HeldAfter
import org.koremc.world.Hopper
import org.koremc.world.Hunger
import org.koremc.world.INFINITE_EFFECT
import org.koremc.world.ItemEntity
import org.koremc.world.ItemUsedOn
import org.koremc.world.JUMP_EXHAUSTION
import org.koremc.world.KNOCKBACK_FORCE
import org.koremc.world.KNOCKBACK_HEIGHT
import org.koremc.world.KNOCKBACK_PER_LEVEL
import org.koremc.world.LOCATOR
import org.koremc.world.Liquid
import org.koremc.world.MAX_BERRY_GROWTH
import org.koremc.world.MAX_FOOD
import org.koremc.world.MAX_HEALTH
import org.koremc.world.MAX_OMINOUS_AMPLIFIER
import org.koremc.world.MAX_Y
import org.koremc.world.MIN_Y
import org.koremc.world.MULTISHOT_SPREAD
import org.koremc.world.Machine
import org.koremc.world.MapData
import org.koremc.world.NOTE_SOUND
import org.koremc.world.OMINOUS_TICKS
import org.koremc.world.PISTON_MOVE_TICKS
import org.koremc.world.PLAYER_EYE_HEIGHT
import org.koremc.world.PLAYER_HEIGHT
import org.koremc.world.PLAYER_MAX_AIR
import org.koremc.world.PLAYER_SNEAKING_HEIGHT
import org.koremc.world.PLAYER_SWIMMING_EYE_HEIGHT
import org.koremc.world.PLAYER_SWIMMING_HEIGHT
import org.koremc.world.PLAYER_WALK_SPEED
import org.koremc.world.PLAYER_WIDTH
import org.koremc.world.PUNCH_PER_LEVEL
import org.koremc.world.Placer
import org.koremc.world.PlayerExperience
import org.koremc.world.Projectile
import org.koremc.world.ProjectileHit
import org.koremc.world.ProjectileKind
import org.koremc.world.REGENERATION_EXHAUSTION
import org.koremc.world.SHIELD
import org.koremc.world.SHIELD_COOLDOWN
import org.koremc.world.SHIELD_DISABLED_TICKS
import org.koremc.world.SHIELD_RAISE_TICKS
import org.koremc.world.SMASH_FALL
import org.koremc.world.SNOW_LAYER
import org.koremc.world.SPRINT_EXHAUSTION
import org.koremc.world.SPRINT_JUMP_EXHAUSTION
import org.koremc.world.STARTING_SATURATION
import org.koremc.world.STORAGE_SLOTS
import org.koremc.world.SUB_CHUNK_COUNT
import org.koremc.world.SWEET_BERRY_BUSH
import org.koremc.world.SWIM_EXHAUSTION
import org.koremc.world.SongChange
import org.koremc.world.Storage
import org.koremc.world.Surroundings
import org.koremc.world.THORNS_WEAR
import org.koremc.world.THROW_SPEED
import org.koremc.world.TIPPED_SHARE
import org.koremc.world.TRIDENT_CHARGE_TICKS
import org.koremc.world.TRIDENT_DAMAGE
import org.koremc.world.TRIDENT_SPEED
import org.koremc.world.Vec3
import org.koremc.world.Vitals
import org.koremc.world.WELL_FED
import org.koremc.world.WRITABLE_BOOK
import org.koremc.world.WRITTEN_BOOK
import org.koremc.world.WorldEvent
import org.koremc.world.armourOf
import org.koremc.world.armourWear
import org.koremc.world.arrowDamage
import org.koremc.world.attackDamage
import org.koremc.world.beaconLevel
import org.koremc.world.bit
import org.koremc.world.biteCake
import org.koremc.world.blockPlacedBy
import org.koremc.world.bowForce
import org.koremc.world.breakBlock
import org.koremc.world.breakTicks
import org.koremc.world.breaksInCreative
import org.koremc.world.cauldronNudge
import org.koremc.world.centre
import org.koremc.world.chestHalves
import org.koremc.world.chunksAround
import org.koremc.world.corner
import org.koremc.world.critical
import org.koremc.world.critsWith
import org.koremc.world.crossbowChargeTicks
import org.koremc.world.crossbowMiddleTick
import org.koremc.world.damageAfter
import org.koremc.world.disablesShields
import org.koremc.world.dispenserKind
import org.koremc.world.drawMap
import org.koremc.world.dropExperience
import org.koremc.world.dropFromBlock
import org.koremc.world.emptyBucket
import org.koremc.world.facesFront
import org.koremc.world.facing
import org.koremc.world.fertilize
import org.koremc.world.fertilizeSeabed
import org.koremc.world.fillBucket
import org.koremc.world.fillPortalFrame
import org.koremc.world.furnaceKind
import org.koremc.world.goesOnLectern
import org.koremc.world.grown
import org.koremc.world.harvests
import org.koremc.world.hasCandle
import org.koremc.world.hasGlowBerries
import org.koremc.world.heightMap
import org.koremc.world.ignite
import org.koremc.world.impalingDamage
import org.koremc.world.isBlockedOpen
import org.koremc.world.isCake
import org.koremc.world.isCandle
import org.koremc.world.isCauldron
import org.koremc.world.isClear
import org.koremc.world.isComposter
import org.koremc.world.isCrafter
import org.koremc.world.isFlowerPot
import org.koremc.world.isItemFrame
import org.koremc.world.isJukebox
import org.koremc.world.isLectern
import org.koremc.world.isNoteBlock
import org.koremc.world.isSign
import org.koremc.world.isWet
import org.koremc.world.knockBookOff
import org.koremc.world.knockbackResistance
import org.koremc.world.landOn
import org.koremc.world.lecternBook
import org.koremc.world.less
import org.koremc.world.liquid
import org.koremc.world.lookAtBeacons
import org.koremc.world.lookVector
import org.koremc.world.mapId
import org.koremc.world.markSign
import org.koremc.world.movingFrom
import org.koremc.world.number
import org.koremc.world.opensOnClick
import org.koremc.world.otherHalf
import org.koremc.world.pick
import org.koremc.world.pickedItem
import org.koremc.world.pistonMotions
import org.koremc.world.place
import org.koremc.world.playNote
import org.koremc.world.potionEffects
import org.koremc.world.powerDamage
import org.koremc.world.pressPlates
import org.koremc.world.putCandleOn
import org.koremc.world.putOnLectern
import org.koremc.world.putOutCandle
import org.koremc.world.putOutFire
import org.koremc.world.rainingAt
import org.koremc.world.restoreBlockData
import org.koremc.world.savedBlockEntitiesIn
import org.koremc.world.setBeaconPowers
import org.koremc.world.settle
import org.koremc.world.sharpnessDamage
import org.koremc.world.shieldCovers
import org.koremc.world.shieldWear
import org.koremc.world.showing
import org.koremc.world.side
import org.koremc.world.signWaxed
import org.koremc.world.smashDamage
import org.koremc.world.smashesWith
import org.koremc.world.smokeDirection
import org.koremc.world.snapsUp
import org.koremc.world.solidHeights
import org.koremc.world.songTitleIn
import org.koremc.world.stepOn
import org.koremc.world.storage
import org.koremc.world.strike
import org.koremc.world.surroundings
import org.koremc.world.takeFromFrame
import org.koremc.world.thornsPrick
import org.koremc.world.tickBlocks
import org.koremc.world.tickItems
import org.koremc.world.tickOrbs
import org.koremc.world.tickProjectiles
import org.koremc.world.tickRandomly
import org.koremc.world.tickSky
import org.koremc.world.toggleCrafterSlot
import org.koremc.world.tuneNote
import org.koremc.world.turnPage
import org.koremc.world.updatePower
import org.koremc.world.usable
import org.koremc.world.use
import org.koremc.world.useCauldron
import org.koremc.world.useComposter
import org.koremc.world.useFrame
import org.koremc.world.useJukebox
import org.koremc.world.usePot
import org.koremc.world.useToolOn
import org.koremc.world.wearFromBreaking
import org.koremc.world.wearFromHitting
import org.koremc.world.wholeExperience
import org.koremc.world.windBurstLift
import org.koremc.world.withBlockData
import org.koremc.world.withStrength
import org.koremc.world.writeSign
import org.slf4j.LoggerFactory
import java.security.interfaces.ECPublicKey
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val log = LoggerFactory.getLogger(Session::class.java)

/** Every batch in and out, by packet. The console leaves it out, and each run's log file has it all. */
private val packetLog = LoggerFactory.getLogger(PACKET_LOG)

/** Batches this size or larger are compressed. Small ones cost more to compress than they save. */
private const val COMPRESSION_THRESHOLD = 256

/** How long the game's tick is. */
private const val TICK_MILLIS = 50L

/** How many of the client's reports of where the player is to log after a piston moves them, to see how far it took them. */
private const val PISTON_WATCH = 10

/** How long an item a player throws waits, long enough for it to clear them: two seconds. */
private const val THROWN_PICKUP_DELAY = 40

/** How fast a thrown item leaves the player's hand, in blocks a tick, and the lift it gets. */
private const val DROP_SPEED = 0.3
private const val DROP_LIFT = 0.1f

/** How high above where an item lies the client puts it, an eighth of a block. */
private const val ITEM_NETWORK_OFFSET = 0.125f

/** How high above where a falling block's bottom is the client puts it, about half its height, as it draws the block around that point. */
private const val FALLING_BLOCK_NETWORK_OFFSET = 0.49f

/** What snow that falls where it can't land drops. */
private const val SNOWBALL = "minecraft:snowball"

/** How far under a player's feet the block they land on is looked for: a little, so that farmland, a little short of a block, counts. */
private const val LANDING_DEPTH = 0.2f

/** What players climb, and so don't fall on: ladders, vines and scaffolding. */
private val CLIMBABLE = setOf(
    "minecraft:ladder", "minecraft:vine", "minecraft:scaffolding", "minecraft:cave_vines", "minecraft:cave_vines_body_with_berries",
    "minecraft:cave_vines_head_with_berries", "minecraft:twisting_vines", "minecraft:weeping_vines",
)

/** How long after knocking an item out of a frame a break of that frame is the same hit, reported again, in milliseconds. */
private const val KNOCK_OUT_MILLIS = 250L

/** What the client says swung a player's arm as they used something on a block. */
private const val INTERACT_SWING = "interact"

/** The game's message for what a jukebox has just started playing, which the song's title fills in. */
private const val NOW_PLAYING = "record.nowPlaying"

/** How loud a jukebox's song plays: four times as far as a sound at full volume carries. */
private const val JUKEBOX_VOLUME = 4f

/** Where the weather happens, which is everywhere: the client takes no notice of the point. */
private val ORIGIN = Vec3(0f, 0f, 0f)

/** How hard rain and thunder come down, of 65535. */
private const val FULL_WEATHER = 65535

// The sounds of lightning striking: its thunder, by the bolt, and its blast.
private const val THUNDER = "thunder"
private const val EXPLODE = "explode"

/** How often players hear what the time is, in ticks, to keep their clocks in step. */
private const val TIME_SYNC_TICKS = 20

/** How the names of armour end, by the armour slot each piece goes in, from the head down. */
private val ARMOUR_PIECES = listOf("_helmet", "_chestplate", "_leggings", "_boots")

private const val ELYTRA = "minecraft:elytra"

/** The armour slot the item called [name] is worn in, or null for something that isn't worn. */
private fun armourSlotFor(name: String): Int? = if (name == ELYTRA) 1 else ARMOUR_PIECES.indexOfFirst(name::endsWith).takeIf { it >= 0 }

/** A player eating: the item, from the hotbar [slot] they hold, and the client's tick they started on. */
private class Eating(val slot: Int, val item: String, val since: Long)

/** Buckets: empty, and full of each liquid. */
private const val BUCKET = "minecraft:bucket"
private val BUCKET_LIQUIDS = mapOf("minecraft:water_bucket" to Liquid.WATER, "minecraft:lava_bucket" to Liquid.LAVA)
private val LIQUID_BUCKETS = BUCKET_LIQUIDS.entries.associate { (bucket, liquid) -> liquid to bucket }
private val BUCKETS = BUCKET_LIQUIDS.keys + BUCKET

/** How far players reach to pick items up: a block either way of the item, and half a block up and down. */
private const val PICKUP_REACH = 1f
private const val PICKUP_REACH_UP = 0.5f

/** An item entity's half width and height. */
private const val ITEM_HALF_WIDTH = 0.125f
private const val ITEM_HEIGHT = 0.25f

/** The smallest view distance Kore settles on, whatever the client asks for. */
private const val MIN_CHUNK_RADIUS = 2

/** Chunks per batch, so the client can start building terrain before the last of them arrives. */
private const val CHUNKS_PER_BATCH = 16

/** No real world reaches this far out, and it keeps block and chunk coordinates well inside an Int. */
private const val MAX_COORDINATE = 30_000_000f

/**
 * How many chunks a player can stray from the centre of their view before
 * it follows them. Players often step back and forth over a chunk border;
 * if the view followed every step, each one would resend the same row of
 * chunks. Strays of less than this send nothing.
 */
private const val RECENTRE_DISTANCE = 2

/** An empty slot, as the game saves one in a list of slots that keeps their order. */
private val EMPTY_SLOT = NbtMap.builder().putString("Name", "").putByte("Count", 0).putShort("Damage", 0).putByte("WasPickedUp", 0).build()

/** How often the world saves by itself, in ticks: every minute. */
private const val AUTOSAVE_TICKS = 60 * 20L

/** The window and container type of a player's own inventory. */
private const val INVENTORY_WINDOW = 0
private const val INVENTORY_CONTAINER = 0xFF

/** The window a client closes when chat opens over a container. It gets no reply. */
private const val CHAT_WINDOW = 0xFF

/** The window of a screen with none of its own, like a lectern's book, which the client closes as [CHAT_WINDOW]. */
private const val NO_WINDOW = -1

/** The highest window ID a container gets, after which they start from 1 again. */
private const val LAST_WINDOW = 99

/** The workstations players open, by block, with the screen the client shows for each. */
private val WORKSTATIONS = mapOf(
    "crafting_table" to (Workstation.CRAFTING_TABLE to ContainerType.WORKBENCH),
    "stonecutter_block" to (Workstation.STONECUTTER to ContainerType.STONECUTTER),
    "smithing_table" to (Workstation.SMITHING_TABLE to ContainerType.SMITHING_TABLE),
    "anvil" to (Workstation.ANVIL to ContainerType.ANVIL),
    "chipped_anvil" to (Workstation.ANVIL to ContainerType.ANVIL),
    "damaged_anvil" to (Workstation.ANVIL to ContainerType.ANVIL),
    "grindstone" to (Workstation.GRINDSTONE to ContainerType.GRINDSTONE),
    "loom" to (Workstation.LOOM to ContainerType.LOOM),
    "enchanting_table" to (Workstation.ENCHANTING_TABLE to ContainerType.ENCHANTMENT),
    "beacon" to (Workstation.BEACON to ContainerType.BEACON),
    "cartography_table" to (Workstation.CARTOGRAPHY_TABLE to ContainerType.CARTOGRAPHY),
).mapKeys { "minecraft:${it.key}" }

/** What an anvil wears into each time it does, until a damaged one breaks. */
private val WORN_ANVILS = mapOf("minecraft:anvil" to "minecraft:chipped_anvil", "minecraft:chipped_anvil" to "minecraft:damaged_anvil")

/** The chance an anvil wears each time a player outside creative uses it. */
private const val ANVIL_WEAR_CHANCE = 0.12f

private const val BOOKSHELF = "minecraft:bookshelf"

/** How long a player waits after picking up an experience orb before they can pick up another, in ticks. */
private const val ORB_PICKUP_DELAY = 2

/** The sound of a player reaching a level that's a multiple of five. */
private const val LEVEL_UP = "levelup"

/** The sound of a brewing stand finishing a brew. */
private const val BREWED = "potion.brewed"

/** The sound of an item burning in lava, and of the fire on a player going out in water or rain. */
private const val FIZZ = "fizz"

/** The sound of a beacon taking the powers a player paid for. */
private const val BEACON_POWER = "beacon.power"

/** The pitch of a dispenser's click as it fires, and with nothing to fire, in thousandths. */
private const val CLICK_PITCH = 1000
private const val CLICK_FAIL_PITCH = 1200

/**
 * How far players reach to build and break, from their eyes to the
 * block's middle, in and out of creative. These are a little beyond the
 * game's own reach, so a client at its limit isn't turned down.
 */
private const val SURVIVAL_REACH = 8f
private const val CREATIVE_REACH = 14f

/** How far cracks have to spread for a block to break, in the units `LevelEvent` counts their pace in. */
private const val CRACKED = 65535

/**
 * How near to where a teleport put a player the client has to say they
 * are, if it doesn't say it moved them there, for Kore to believe it.
 */
private const val TELEPORT_SLACK = 4f

/** How far from a player's eyes the sounds they make can be, a little beyond what they reach. */
private const val SOUND_REACH = 16f

/** How long after a player's loading screen closes everyone hears they joined, in ticks. */
private const val JOIN_MESSAGE_DELAY = 10

/** The messages, in each client's own language, that a player joined or left: yellow, with their name filled in. */
private const val JOINED = "\u00a7e%multiplayer.player.joined"
private const val LEFT = "\u00a7e%multiplayer.player.left"

/** How far from a player's eyes they can hit another, in blocks, and further in creative, as the client reaches. */
private const val ATTACK_REACH = 8f
private const val CREATIVE_ATTACK_REACH = 14f

/** The sounds of a hit that hurts, and of one that doesn't, as a player makes them. */
private const val ATTACK_STRONG = "attack.strong"
private const val ATTACK_NO_DAMAGE = "attack.nodamage"
private const val PLAYER_ENTITY = "minecraft:player"

/** A bow, and a crossbow, and what they shoot, as the game names them, and where a loaded crossbow's NBT keeps what it's loaded with. */
private const val BOW = "minecraft:bow"
private const val CROSSBOW = "minecraft:crossbow"
private const val TRIDENT = "minecraft:trident"
private const val ARROW = "minecraft:arrow"
private const val CHARGED_ITEM = "chargedItem"

/** How much sooner than it should be loaded a crossbow's client's word that it is is taken, in ticks. */
private const val CROSSBOW_CHARGE_SLACK = 3

/** The message for a player who died, as `/kill` kills them. */
private const val DIED = "death.attack.generic"

/** A Totem of Undying, and what it gives the player it saves, for how many ticks, as Bedrock has it. */
private const val TOTEM = "minecraft:totem_of_undying"
private val TOTEM_EFFECTS = listOf(
    EffectLevel(Effect.REGENERATION, 1) to 45 * 20,
    EffectLevel(Effect.FIRE_RESISTANCE) to 40 * 20,
    EffectLevel(Effect.ABSORPTION, 1) to 5 * 20,
)

/** Which way a player faces as they join and respawn: south, toward positive z, looking straight ahead. */
private const val SPAWN_YAW = 0f
private const val SPAWN_PITCH = 0f

/** The game's names for a player's health and hunger, as attributes. */
private const val HEALTH = "minecraft:health"
private const val ABSORPTION = "minecraft:absorption"
private const val FOOD = "minecraft:player.hunger"
private const val SATURATION = "minecraft:player.saturation"
private const val EXHAUSTION = "minecraft:player.exhaustion"

/** The most exhaustion the client shows, at which a point of saturation or food goes. */
private const val SHOWN_MAX_EXHAUSTION = 5f

/** The furthest a player moves in a tick that tires them: anything further is a teleport. */
private const val MAX_TIRING_STEP = 4f

/** How long a player lies dead before others stop seeing them, in ticks: long enough to fall over. */
private const val DEAD_BODY_TICKS = 22

/** How far below their eyes a player drops what they had as they die, and how far out it flies, at most, and how long before anyone can pick it up. */
private const val DEATH_DROP_BELOW_EYES = 0.3f
private const val DEATH_DROP_SPREAD = 0.5f
private const val DEATH_PICKUP_DELAY = 40

/** How much of their experience a player drops as they die: so much a level, up to a limit. */
private const val DEATH_EXPERIENCE_PER_LEVEL = 7
private const val MAX_DEATH_EXPERIENCE = 100

/** How far below the world the void starts hurting players, and how much it hurts them a time. */
private const val VOID_DEPTH = 17
private const val VOID_DAMAGE = 4f

/** How much lava hurts a player a time, and how long it and fire set them burning for, in ticks, which hurts them once a second. */
private const val LAVA_DAMAGE = 4f
private const val LAVA_BURNS = 15 * 20
private const val FIRE_BURNS = 8 * 20
private const val BURN_INTERVAL = 20

/** How far a player falls before it hurts them, and how much less a fall onto hay or honey, or a bed, hurts. */
private const val SAFE_FALL = 3f
private const val FALL_SLACK = 0.01f
private const val SOFT_LANDING = 0.2f
private const val BED_LANDING = 0.5f

/** How much drowning hurts a player once their breath runs out, how many ticks apart, and how much breath they get back a tick above water. */
private const val DROWNING_DAMAGE = 2f
private const val DROWNING_INTERVAL = 20
private const val BREATH_REGAINED = 4

/** How often, in ticks, a player heals by themselves while well fed, and in peace. */
private const val REGENERATION_INTERVAL = 80
private const val PEACEFUL_REGENERATION_INTERVAL = 20

/** How often, in ticks, a player's food bar fills again by a point, in peace. */
private const val PEACEFUL_FEEDING_INTERVAL = 10

/** How far starving takes a player's health down at each difficulty but hard, where it kills them. */
private val STARVES_DOWN_TO = mapOf(Difficulty.PEACEFUL to MAX_HEALTH, Difficulty.EASY to 10f, Difficulty.NORMAL to 1f)

/** How far up the item's network ID goes in the data of the event of a player eating it. */
private const val EATING_ITEM_SHIFT = 16

/** How many ticks short of the time a food takes to eat the client may finish, as its ticks and the server's drift apart. */
private const val EATING_SLACK = 2

private const val TICKS_PER_SECOND = 20

/** The sounds of a player finishing what they eat, and of chorus fruit teleporting them. */
private const val BURP = "burp"
private const val TELEPORT_SOUND = "teleport"

/** How far chorus fruit teleports a player at most, each way, and how many places it tries. */
private const val CHORUS_RANGE = 8
private const val CHORUS_TRIES = 16

/** How often, in ticks, Regeneration heals, poison hurts and wither hurts at level I, twice as often a level up. */
private const val REGENERATION_EVERY = 50
private const val POISON_EVERY = 25
private const val WITHER_EVERY = 40

/** How much Instant Health heals and Instant Damage hurts at level I, twice as much a level up. */
private const val INSTANT_HEALTH = 4f
private const val INSTANT_DAMAGE = 6f

/** How much more health Health Boost gives, and Absorption gives on top of it, a level. */
private const val BOOST_PER_LEVEL = 4f

/** How many times an effect's strength doubles at most, level on level, before it's more than a number holds. */
private const val MAX_DOUBLINGS = 24

/** How hard lightning hurts the players it strikes near, and how near, across. */
private const val LIGHTNING_DAMAGE = 5f
private const val LIGHTNING_REACH = 3f

/** How little a player must move across to be pricked by a berry bush they're in. */
private const val BERRY_PRICK_MOVE = 0.003f

/** How much shorter each level of Fire Protection makes a player burn, in hundredths. */
private const val FIRE_PROTECTION_SHARE = 15

/** The armour slots of a player's head and feet. */
private const val HEAD_SLOT = 0
private const val FEET_SLOT = 3

/** What a player turned away from a full server is told, in their own language. */
private const val SERVER_FULL = "disconnectionScreen.serverFull"
/** The device a player plays on, when their client doesn't say. */
private const val UNKNOWN_PLATFORM = -1

/** The flags a player always has: they breathe, fall, collide and climb. */
private val PLAYER_FLAGS = setOf(ActorFlag.BREATHING, ActorFlag.HAS_GRAVITY, ActorFlag.HAS_COLLISION, ActorFlag.CAN_CLIMB)

/**
 * One player's protocol session. It owns the batch codec, so it decides
 * when compression switches on, and walks the login sequence one packet
 * at a time:
 *
 * 1. `RequestNetworkSettings` names the client's protocol. One Kore doesn't
 *    speak gets a failed `PlayStatus`; one it does gets `NetworkSettings`,
 *    after which every batch is compressed, and every packet is as the
 *    client's version lays it out.
 * 2. `Login` carries the client's identity and client data, which must
 *    authenticate. A login that doesn't gets a `Disconnect`. One that does
 *    gets `PlayStatus(LOGIN_SUCCESS)` straight away: NetherNet has no
 *    encryption handshake, since DTLS already covers it.
 * 3. Resource packs. Kore offers none, the client answers that it has them
 *    all, Kore sends the (vanilla) pack stack, and the client confirms it
 *    has applied it.
 * 4. `StartGame` sets up the world. The jigsaw structure data and voxel
 *    shapes go just before it, as the client insists, and the game's
 *    definitions just after:
 *    the `ItemRegistry`, with every item and the runtime ID it travels by,
 *    then the biomes and the creative inventory.
 * 5. The client asks how far it can see. Kore settles on a chunk radius,
 *    sends every chunk within it, nearest first, then
 *    `PlayStatus(PLAYER_SPAWN)`.
 * 6. Once its player is in the world, the client sends
 *    `SetLocalPlayerAsInitialised`, and the player is in the game. Kore
 *    tells the client about its player: that it breathes and falls, its
 *    attributes, and what its game mode lets it do.
 *
 * From then on, `PlayerAuthInput` reports where the client predicts its
 * player is, every tick. Kore takes its word for it, as Mojang's movement
 * guide allows a server without physics to, and moves the player's view
 * with them once they stray from its centre, sending the chunks that come
 * into it. Its flags say when the player sprints and sneaks, which Kore
 * mirrors in the player's actor data.
 *
 * Players build and break as the client predicts, which Kore checks and
 * then shows to everyone who has the chunk. Placing comes as an inventory
 * transaction using the held item on a block. Breaking comes as one riding
 * in `PlayerAuthInput`, or in creative as a `PlayerAction`. Clicking a
 * lever, button, door, trapdoor or fence gate uses it instead of placing
 * against it, unless the player sneaks with something in hand.
 *
 * A survival player's input also carries the steps of breaking a block:
 * starting, a crack every tick, and stopping. Kore shows everyone with the
 * chunk the cracks spreading at the pace the block breaks at, and checks a
 * reported break didn't come too soon after the player started on it. The
 * sounds of the block being hit come from the client, for everyone around
 * to hear, the player too.
 *
 * Players see each other. Each tick, the client hears who's on the player
 * list, with their skins, and sees the players in the chunks it has appear,
 * move, sneak, change what they hold and go. Their arm swings reach it as
 * they come in. Their chat reaches it too, as do players joining and
 * leaving.
 *
 * Players open chests, barrels, shulker boxes and ender chests, and
 * crafting tables, and craft. Everyone looking into the same chest sees
 * the items others move in and out of it, and its lid stays open until the
 * last of them closes it. They open furnaces and brewing stands, and watch
 * them work, and the workstations: stonecutters, smithing tables, anvils,
 * grindstones, looms, enchanting tables and cartography tables. They pick
 * up the experience orbs that fly to them, and spend levels at anvils and
 * enchanting tables. They pay beacons for powers, which beacons give them
 * as effects, and draw maps of the world as they hold them.
 *
 * Players run commands from the chat, as themselves. Each has their own
 * game mode, which starts as the world's and commands can change, and is
 * an operator or not, which sets the commands they may run.
 *
 * Runs on the UDP event loop, with the connection callbacks that drive it.
 */
class Session(
    private val config: ServerConfig,
    /** The world and the game's definitions, shared by every session. */
    private val game: GameData,
    private val networkId: String,
    /** The player's entity ID, unique across the server. */
    val entityId: Long,
    /** The key the client proved when it connected, which its login must match. */
    private val offerKey: ECPublicKey?,
    private val authenticator: LoginAuthenticator,
    /** Every player's session, this one included, for showing them what this player changes in the world. */
    private val viewers: () -> Collection<Session>,
    /** Runs a task a number of ticks from now, on the same thread as the sessions, like telling everyone a player joined. */
    private val schedule: (ticks: Int, task: () -> Unit) -> Unit,
    /** The commands players run, which the whole server shares. */
    private val commands: Commands = Commands(config, game, viewers),
    private val send: (ByteArray) -> Unit,
) : CommandSource, Selectable {
    private enum class State { AWAITING_NETWORK_SETTINGS_REQUEST, AWAITING_LOGIN, NEGOTIATING_RESOURCE_PACKS, LOADING_WORLD, SPAWNING, IN_GAME, CLOSED }

    private val batches = BatchCodec()

    /** The version of the game the client is on, which its packets are laid out as, both ways. */
    private var gameVersion = GameVersion.OLDEST
    private val world = game.world
    private val inventory = PlayerInventory(world::newStackId)

    /** The player's experience: their level, and how far into the next. */
    private var experience = PlayerExperience()

    /** Picks what enchanting tables offer the player, until they next enchant something. */
    private var enchantmentSeed = Random.nextInt()

    /** The crafts at workstations the requests just handled made, to carry out once they're answered. */
    private val crafts = mutableListOf<Crafted>()

    private val stackRequests = StackRequestHandler(
        inventory, game.creative, creativeMode = { gameMode == GameMode.CREATIVE }, onDrop = ::throwItem, recipes = game.recipes,
        enchantments = game.enchantments,
        crafter = object : Crafter {
            override val experienceLevel get() = experience.level

            override fun enchantingOffers(stack: ItemStack) = this@Session.enchantingOffers(stack)

            override fun beaconGives(primary: Int, secondary: Int): Boolean {
                val beacon = window?.pos?.takeIf { inventory.workstation == Workstation.BEACON } ?: return false
                return org.koremc.world.beaconGives(world.beaconLevel(beacon), primary, secondary)
            }

            override fun cartograph(input: ItemStack?, additional: ItemStack?, name: String?): Cartographed? {
                val copy = lockedCopy?.takeIf { (mapId, _) -> mapId == input?.mapId || mapId == additional?.mapId }?.second
                return org.koremc.world.cartograph(input, additional, name, world.maps, copy)
            }
        },
        onCrafted = crafts::add,
    )

    /** The player's ender chest, the same whichever ender chest they open. */
    private val enderChest = Container(STORAGE_SLOTS)

    /** The window open besides the inventory, if any. */
    private var window: Window? = null

    private var lastWindowId = 0

    /**
     * A window open besides the inventory: a container's, which shows the
     * items in [view] of the blocks at [blocks], a furnace's, brewing
     * stand's, hopper's, dispenser's or dropper's, which shows its
     * [machine] at work too, or a workstation's.
     */
    private class Window(
        val id: Int,
        val type: Int,
        val pos: BlockPos,
        val storage: Storage?,
        val blocks: List<BlockPos>,
        val view: Container?,
        val machine: Machine? = null,
    ) {
        /** What the client was last shown of the container: each slot, and each number a machine's screen shows. */
        var shownSlots: List<SlotStack?>? = null
        var shownData: List<Int> = emptyList()
    }

    /** The item in the open enchanting table that the client was last told the offers for. */
    private var offeredFor: ItemStack? = null

    /** Ticks until the player can pick up another experience orb. */
    private var orbPickupDelay = 0

    /** The orbs the client has been shown, by entity ID. */
    private val shownOrbs = HashSet<Long>()

    /** The falling blocks the client has been shown, by entity ID. */
    private val shownFallingBlocks = HashSet<Long>()
    private var state = State.AWAITING_NETWORK_SETTINGS_REQUEST

    /** The player, once their login has authenticated. */
    var player: PlayerIdentity? = null
        private set

    /** The authenticated login, kept for its client data: settings, skin and so on, which only the player's key vouches for. */
    var login: LoginRequest? = null
        private set

    /** Whether the client can reuse chunk data it has cached, as it said once logged in. */
    var cacheSupported = false
        private set

    /** Whether `StartGame` has gone out and the client is loading the world. */
    val isLoadingWorld get() = state == State.LOADING_WORLD

    /** Whether the player has spawned. */
    val isInGame get() = state == State.IN_GAME

    /** How many chunks around the player to send, once the client has asked. */
    var chunkRadius: Int? = null
        private set

    /** The chunks the client has, as far as Kore knows. */
    private val sentChunks = HashSet<ChunkPos>()

    /** Where the player's eyes are, as the client last reported. */
    var position = Vec3(world.spawn.x + 0.5f, world.spawn.y + PLAYER_EYE_HEIGHT, world.spawn.z + 0.5f)
        private set

    /** Whether the player is sprinting, as their input last said. */
    var sprinting = false
        private set

    /** Whether the player is sneaking, as their input last said. */
    var sneaking = false
        private set

    /** Whether the player is flying, where sneaking means descending rather than slowing down. */
    private var flying = false

    /** Whether the player is swimming, or gliding with an elytra, lying flat either way. */
    private var swimming = false
    private var gliding = false

    /** Which way the player looks, around the vertical, as the client last reported. Blocks they place turn by it. */
    override var yaw = 0f
        private set

    /** How far the player looks up or down, as the client last reported. Items they throw go that way. */
    override var pitch = 0f
        private set

    /** The player's game mode, which starts as the world's. */
    override var gameMode = config.gameMode
        private set

    /** Where a teleport put the player's eyes, until the client says it has moved them there. */
    private var teleportedTo: Vec3? = null

    /** Whether the player has died and not yet respawned. */
    var dead = false
        private set

    /** Whether the player has lain dead long enough for others to stop seeing them. */
    private var bodyGone = false

    /** The player's health, breath and fire. */
    val vitals = Vitals()

    /** The player's food bar, saturation and exhaustion. */
    val hunger = Hunger()

    /** What the player's eating, while they eat. */
    private var eating: Eating? = null

    /** When the player can next use an item of each kind with a wait between uses, by the game's name for the kind, in [aliveTicks]. */
    private val cooldowns = HashMap<String, Int>()

    /** The bow the player's drawing, while they hold use down. */
    private var drawing: Drawing? = null

    /** A bow drawn, or a crossbow loading, in hotbar [slot], since when, in [aliveTicks]. */
    private class Drawing(val slot: Int, val since: Int)

    /** The crossbow the player's loading, while they hold use down. */
    private var loading: Drawing? = null

    /** Whether the use the client asked for has already told it what it holds now, which its answer then needn't again. */
    private var heldShown = false

    /** When the player raised the shield they hold up, in [aliveTicks], while they hold it up. */
    private var shieldRaisedAt: Int? = null

    /** The food and saturation the client last heard the player has. */
    private var shownHunger = MAX_FOOD to STARTING_SATURATION

    /** Ticks since the player last healed by themselves, and since they joined, for effects that last for ever to count by. */
    private var sinceRegeneration = 0
    private var aliveTicks = 0

    /** Where the player respawns, as `/spawnpoint` set it, or null for the world's spawn. */
    var spawnPoint: BlockPos? = null
        private set

    /**
     * Blocks the player sees otherwise while a piston moves them through
     * the player, and the block tick by which it's done, when they see them
     * as they are.
     */
    private val hiddenByPistons = HashMap<BlockPos, Long>()

    /** How the player last moved, as the client reported it. */
    private var delta = Vec3(0f, 0f, 0f)

    /** How far the player has fallen since they last stood on something, in blocks. */
    private var fallDistance = 0f

    /** Whether the player has a lectern's book open, which closes with no window of its own. */
    private var reading = false

    /** The effects on the player, like the powers of a beacon they're near. */
    private val effects = Effects()

    /** The maps the client has asked about, which it hears of whenever they change. */
    internal val knownMaps = HashSet<Long>()

    /** The map the client last picked a number for a locked copy of, at a cartography table, with that number. */
    private var lockedCopy: Pair<Long, Long>? = null

    /** How pistons last moved the player, until they're done with them. */
    private var pistonMotion: Vec3? = null

    /** How many more of the client's reports of where the player is to log, after a piston moved them. */
    private var pistonWatch = 0

    /** The player's name, or their network's until they've logged in. */
    override val name get() = player?.displayName ?: "network $networkId"

    /** Where the player's feet are. */
    override val feet get() = Vec3(position.x, position.y - PLAYER_EYE_HEIGHT, position.z)

    /** Commands the player runs start from their feet. */
    override val origin get() = feet

    /** The player's experience level, which selectors can pick them out by. */
    override val level get() = experience.level

    /** Whether the player is one of the server's operators. */
    val isOperator get() = player?.let { commands.operators.contains(it.displayName, it.xuid) } == true

    /** What the player may do with commands: an operator's commands, or everyone's. */
    override val permission get() = if (isOperator) CommandPermission.ADMIN else CommandPermission.ANY

    private val permissionLevel get() = if (isOperator) PermissionLevel.OPERATOR else PermissionLevel.MEMBER

    /** Which way the player's head faces, around the vertical, which can be a little off their body's. */
    var headYaw = 0f
        private set

    /** Whether the player stood on something, as their input last said. */
    var onGround = false
        private set

    /** How the player looks to everyone else, from their login. */
    private var skin: Skin? = null

    /** Whether the client's loading screen has closed since it joined. */
    private var loadingScreenClosed = false

    /** Whether everyone has been told the player joined, or soon will be. */
    private var joinAnnounced = false

    /** The players on the client's player list, by entity ID. */
    private val listedPlayers = HashMap<Long, UUID>()

    /** What the client was last shown of each other player in view, by entity ID. */
    private val shownPlayers = HashMap<Long, Look>()

    /** The items the client has been shown, by entity ID. */
    private val shownItems = HashSet<Long>()

    /** The hotbar slot the player holds, as the client last said. */
    var heldSlot = 0
        private set

    /** The block a survival player is breaking, from the client tick they started, and how many ticks it takes. */
    private var breaking: Breaking? = null

    private class Breaking(val pos: BlockPos, val startTick: Long, val ticks: Int?)

    /** Whether the player's inventory is open, which the client can't do without Kore's say-so. */
    private var inventoryOpen = false

    /** The chunk the player's view is centred on. */
    private var viewCentre = world.spawn.chunk

    fun receive(message: ByteArray) {
        val packets = try {
            batches.decode(message).map { decodeClientPacket(it, gameVersion) }
        } catch (e: PacketException) {
            log.warn("Dropped a batch from network {}: {}", networkId, e.message)
            return
        }
        // Movement arrives every tick in game, so batches of nothing else only go to trace.
        if (state == State.IN_GAME && packets.all { it is PlayerAuthInput && it.onlyMoves }) {
            packetLog.trace("<- {}: {} ({} bytes)", who, packets.joinToString(), message.size)
        } else {
            packetLog.debug("<- {}: {} ({} bytes)", who, packets.joinToString(), message.size)
        }
        packets.forEach(::handle)
    }

    private fun handle(packet: ClientPacket) {
        // Whatever the client is doing, a packet it found wrong is worth knowing of.
        if (packet is PacketViolation) return log.warn("Network {} found packet {} from Kore wrong: {}", networkId, packet.packetId, packet.context)
        when (state) {
            State.AWAITING_NETWORK_SETTINGS_REQUEST -> if (packet is RequestNetworkSettings) onRequestNetworkSettings(packet) else unexpected(packet)
            State.AWAITING_LOGIN -> if (packet is Login) onLogin(packet) else unexpected(packet)
            State.NEGOTIATING_RESOURCE_PACKS -> when (packet) {
                is ClientCacheStatus -> cacheSupported = packet.supported
                is ResourcePackClientResponse -> onResourcePackResponse(packet)
                else -> unexpected(packet)
            }
            State.LOADING_WORLD -> when (packet) {
                is RequestChunkRadius -> onRequestChunkRadius(packet)
                is SubChunkRequest -> onSubChunkRequest(packet)
                is LoadingScreen -> {} // The loading screen opening, as the world loads
                else -> unexpected(packet)
            }
            State.SPAWNING, State.IN_GAME -> when (packet) {
                // A dead player lies where they fell, doing nothing, until they respawn.
                is PlayerAuthInput -> if (!dead) {
                    onMove(packet)
                    // The item use first: a block breaking this tick is checked against when the player
                    // started on it, before the step that ends the breaking clears that.
                    packet.itemUse?.let { onTransaction(it, packet.tick) }
                    packet.stackRequest?.let { onStackRequests(listOf(it)) }
                    onBlockActions(packet.blockActions, packet.tick)
                }
                is Interact -> if (packet.action == Interact.OPEN_INVENTORY) openInventory()
                is ContainerClose -> onContainerClose(packet)
                is ItemStackRequest -> onStackRequests(packet.requests)
                is InventoryTransaction -> onTransaction(packet)
                is PlayerAction -> if (packet.action == PlayerAction.CREATIVE_DESTROY_BLOCK) onBreak(packet.position, creativeOnly = true)
                is MobEquipment -> onEquip(packet)
                is ActorEvent -> if (packet.event == ActorEvent.EATING_ITEM) onEating()
                is Animate -> if (packet.action == Animate.SWING_ARM) viewers().forEach { it.showSwing(entityId, packet.swingSource, packet.hand) }
                is LevelSoundEvent -> onSound(packet)
                is LecternUpdate -> onLecternUpdate(packet)
                is EditedBlockData -> onEditedBlockData(packet)
                is ItemFrameDropItem -> knockOutOfFrame(packet.position)
                is BookEdit -> onBookEdit(packet)
                is ToggleCrafterSlot -> onToggleCrafterSlot(packet)
                is MapInfoRequest -> onMapInfoRequest(packet)
                is MapCreateLockedCopy -> onLockedCopy(packet)
                is BlockPick -> onBlockPick(packet)
                is Respawn -> onRespawn(packet)
                is Text -> onChat(packet)
                is CommandRequest -> onCommand(packet)
                is RequestChunkRadius -> onRequestChunkRadius(packet)
                is SubChunkRequest -> onSubChunkRequest(packet)
                is SetLocalPlayerAsInitialised -> if (state == State.SPAWNING) onPlayerInitialised(packet) else unexpected(packet)
                is LoadingScreen -> if (packet.type == LoadingScreen.END) {
                    loadingScreenClosed = true
                    announceJoin()
                }
                // Movement and the rest of gameplay arrive many times a second, and Kore has nothing for them yet.
                else -> log.trace("Ignored {} from network {}", packet, networkId)
            }
            State.CLOSED -> unexpected(packet)
        }
    }

    private fun onRequestNetworkSettings(packet: RequestNetworkSettings) {
        if (!acceptsProtocol(packet.protocol)) return

        // NetworkSettings itself goes out uncompressed. Compression starts with the next batch, both ways.
        send(NetworkSettings(compressionThreshold = COMPRESSION_THRESHOLD))
        batches.enableCompression(COMPRESSION_THRESHOLD)
        state = State.AWAITING_LOGIN
    }

    private fun onLogin(packet: Login) {
        if (!acceptsProtocol(packet.protocol)) return

        val request = try {
            parseConnectionRequest(packet.connectionRequest)
        } catch (e: PacketException) {
            log.warn("Network {} sent a login Kore can't read: {}", networkId, e.message)
            reject()
            return
        }

        val player = try {
            authenticator.authenticate(request, offerKey)
        } catch (e: AuthenticationException) {
            // The reason stays in the log. The player only learns that they couldn't be verified.
            log.info("Rejected the login from network {}, claiming to be {}: {}", networkId, request.identity.displayName, e.message)
            reject()
            return
        }
        if (viewers().count { it !== this && it.player != null && it.state != State.CLOSED } >= game.maxPlayers) {
            log.info("Turned away {}: the server is full, with {} players", player.displayName, game.maxPlayers)
            send(Disconnect(Disconnect.Reason.SERVER_FULL, SERVER_FULL))
            state = State.CLOSED
            return
        }
        this.player = player
        game.players.load(player.uuid)?.let(::restoreFrom)
        login = request
        skin = skinFrom(request.clientData, config.version)

        val client = request.clientInfo
        log.debug(
            "{} ({}, {}) logged in on network {}{}: {} {}, OS {}, {}",
            player.displayName, player.xuid ?: "no XUID", player.uuid, networkId,
            if (player.verified) "" else " without verification (offline mode)",
            request.authenticationType ?: "unknown sign-in", client.gameVersion, client.deviceOs, client.languageCode,
        )

        // The pack list follows login success in the same batch, as the client asks for it next anyway.
        send(PlayStatus(PlayStatus.Status.LOGIN_SUCCESS), ResourcePacksInfo())
        state = State.NEGOTIATING_RESOURCE_PACKS
    }

    private fun onResourcePackResponse(packet: ResourcePackClientResponse) {
        when (packet.response) {
            ResourcePackClientResponse.Response.DOWNLOADING_FINISHED -> send(ResourcePackStack(baseGameVersion = config.version))

            // Kore offered nothing, so there's nothing to download. Carry on as though the client had everything.
            ResourcePackClientResponse.Response.DOWNLOADING -> {
                log.debug("Network {} asked for packs Kore never offered: {}", networkId, packet.packs)
                send(ResourcePackStack(baseGameVersion = config.version))
            }

            ResourcePackClientResponse.Response.RESOURCE_PACK_STACK_FINISHED -> startGame()

            ResourcePackClientResponse.Response.CANCEL -> {
                log.info("{} cancelled joining during resource packs", player?.displayName)
                state = State.CLOSED
            }
        }
    }

    private fun startGame() {
        val spawn = world.spawn
        send(
            JigsawStructureData,
            VoxelShapes,
            StartGame(
                entityId = entityId,
                runtimeId = entityId,
                playerGameMode = gameMode,
                position = position,
                seed = config.seed ?: 0,
                worldGameMode = config.gameMode,
                difficulty = world.difficulty,
                playerPermissions = permissionLevel,
                worldSpawn = spawn,
                worldName = config.level,
                serverVersion = config.version,
                baseGameVersion = config.version,
                blockDefinitions = game.blockDefinitions,
                time = world.gameTime,
                rainLevel = if (world.weather.raining) 1f else 0f,
                lightningLevel = if (world.weather.thundering) 1f else 0f,
                gameRules = world.rules.all,
            ),
            // The client can't show an egg without hearing of its climate variant first.
            SyncActorProperty(EGG_ENTITY, listOf(EnumProperty(CLIMATE_VARIANT, CLIMATE_VARIANTS))),
            *game.definitions.toTypedArray(),
        )
        state = State.LOADING_WORLD
        log.debug("{} is loading the world, spawning at {}", player?.displayName, spawn)
    }

    /**
     * Settles the view distance: what the client wants, within what it can
     * handle and what Kore allows. Then sends the chunks in view that the
     * client doesn't have, and the first time, lets the player spawn.
     */
    private fun onRequestChunkRadius(packet: RequestChunkRadius) {
        val wanted = if (packet.maxRadius > 0) minOf(packet.radius, packet.maxRadius) else packet.radius
        val radius = wanted.coerceIn(MIN_CHUNK_RADIUS, config.viewDistance)
        chunkRadius = radius
        val sent = updateView(radius, ChunkRadiusUpdated(radius))
        log.debug("{} asked for {} chunks and gets {}, {} of them new", player?.displayName, packet.radius, radius, sent)

        if (state == State.LOADING_WORLD) {
            send(PlayStatus(PlayStatus.Status.PLAYER_SPAWN))
            state = State.SPAWNING
        }
    }

    /**
     * Takes the client's word for where its player is. Straying far enough
     * from the centre of their view moves it onto them, which sends the
     * chunks coming into it.
     */
    private fun onMove(packet: PlayerAuthInput) {
        val reported = packet.position
        if (!reported.isPlausible()) {
            log.debug("{} reported an impossible position, {}", player?.displayName, reported)
            return
        }
        // Until the client has moved its player where a teleport put them, it reports where they were.
        teleportedTo?.let { to ->
            if (InputFlag.HANDLED_TELEPORT !in packet.inputs && !reported.within(TELEPORT_SLACK, to)) {
                onInputs(packet)
                return
            }
            teleportedTo = null
        }
        val fellFrom = feet.y
        val from = position
        position = reported
        delta = packet.delta
        if (pistonWatch > 0) {
            pistonWatch--
            log.debug("{} is at {}, moving {}, at tick {}", player?.displayName, feet, delta, packet.tick)
        }
        yaw = packet.yaw
        pitch = packet.pitch
        headYaw = packet.headYaw
        onGround = InputFlag.VERTICAL_COLLISION in packet.inputs
        onInputs(packet)
        fall(fellFrom)
        tire(from)

        val chunk = reported.toBlockPos().chunk
        val radius = chunkRadius ?: return
        val dx = chunk.x - viewCentre.x
        val dz = chunk.z - viewCentre.z
        if (dx * dx + dz * dz < RECENTRE_DISTANCE * RECENTRE_DISTANCE) return

        viewCentre = chunk
        val sent = updateView(radius)
        log.debug("{}'s view moved to chunk {}, {}, so {} chunks came into it", player?.displayName, chunk.x, chunk.z, sent)
    }

    /**
     * Counts how far the player falls, their feet having been at [fromY]:
     * every block down while they're off the ground, as the game counts it,
     * and none in water, on something they climb, or flying. Once they land,
     * they land on what's under them, having fallen that far.
     */
    private fun fall(fromY: Float) {
        val at = feet.toBlockPos()
        when {
            flying || gliding || gameMode == GameMode.SPECTATOR || world.isWet(at) || world.stateAt(at)?.name in CLIMBABLE -> fallDistance = 0f
            onGround && delta.y <= 0f -> {
                if (fallDistance > 0f) landOn(fallDistance)
                fallDistance = 0f
            }
            feet.y < fromY -> fallDistance += fromY - feet.y
        }
    }

    /**
     * Tires the player for moving from [from], where their eyes were, to
     * where they are: swimming, or under water, by every block they went,
     * wading by every block across, and sprinting on the ground by every
     * block across, as the game counts it. A move too far to have been a
     * step, like a teleport, doesn't tire them.
     */
    private fun tire(from: Vec3) {
        val dx = position.x - from.x
        val dy = position.y - from.y
        val dz = position.z - from.z
        val across = sqrt(dx * dx + dz * dz)
        val moved = sqrt(dx * dx + dy * dy + dz * dz)
        if (moved == 0f || moved > MAX_TIRING_STEP) return
        when {
            swimming || world.isWet(eyes().toBlockPos()) -> exhaust(SWIM_EXHAUSTION * moved)
            world.isWet(feet.toBlockPos()) -> exhaust(SWIM_EXHAUSTION * across)
            sprinting && onGround -> exhaust(SPRINT_EXHAUSTION * across)
        }
    }

    /**
     * The player landing, having fallen [distance] blocks, on the block just
     * under their feet: farmland may be trampled back to dirt, which breaks
     * the crop on it a tick later, as everyone with the chunk sees. And the
     * fall hurts them, but for the first three blocks of it, and Jump Boost's
     * level, less onto hay, honey or a bed, not at all onto slime, unless
     * they sneak, nor with Slow Falling.
     */
    private fun landOn(distance: Float) {
        val on = BlockPos(floor(feet.x).toInt(), floor(feet.y - LANDING_DEPTH).toInt(), floor(feet.z).toInt())
        hurtByFall(distance, world.stateAt(on)?.name)
        if (!world.landOn(on, distance)) return
        val changes = world.settle(listOf(on))
        showAround(world.packetsFor(Changes(emptyMap(), (listOf(on) + changes.reshaped).distinct())))
        log.debug("{} trampled the farmland at {}", player?.displayName, on)
    }

    /**
     * Follows the player's sprinting, swimming and gliding, from the
     * client's flags for starting and stopping each, and their sneaking,
     * from the flag set while they sneak. Starting to sneak ends a sprint,
     * unless the player is flying. A change goes back to the client as the
     * player's actor data, tagged with the input's tick so the client
     * applies it at the moment it predicted it: without it, the client
     * doesn't swim or glide, lying flat.
     *
     * The sprint's speed boost is left to the client, which already applies
     * it: with Kore sending a walking speed of 0.1, a logged test measured
     * sprint-jumping at the vanilla 7.1 blocks a second.
     */
    private fun onInputs(packet: PlayerAuthInput) {
        val inputs = packet.inputs
        val wasSprinting = sprinting
        val wasSneaking = sneaking
        val wasSwimming = swimming
        val wasGliding = gliding

        if (InputFlag.START_FLYING in inputs) flying = true
        if (InputFlag.STOP_FLYING in inputs) flying = false
        if (InputFlag.START_SWIMMING in inputs) swimming = true
        if (InputFlag.STOP_SWIMMING in inputs) swimming = false
        if (InputFlag.START_GLIDING in inputs) gliding = true
        if (InputFlag.STOP_GLIDING in inputs) gliding = false
        if (InputFlag.START_JUMPING in inputs) exhaust(if (sprinting) SPRINT_JUMP_EXHAUSTION else JUMP_EXHAUSTION)
        if (InputFlag.START_SPIN_ATTACK in inputs) log.debug("{} started spinning", name)
        if (InputFlag.STOP_SPIN_ATTACK in inputs) log.debug("{} stopped spinning", name)
        if (InputFlag.START_SPRINTING in inputs) {
            sprinting = true
            sneaking = false
        }
        if (InputFlag.STOP_SPRINTING in inputs) sprinting = false
        if (InputFlag.SNEAKING in inputs != sneaking) {
            sneaking = !sneaking
            if (sneaking && !flying) sprinting = false
        }

        if (sprinting == wasSprinting && sneaking == wasSneaking && swimming == wasSwimming && gliding == wasGliding) return
        send(SetActorData(entityId, listOf(flagsItem(playerFlags()), FloatItem(ActorDataId.HEIGHT, playerHeight())), packet.tick))
        log.debug("{} is now {}", player?.displayName, listOf("sprinting" to sprinting, "sneaking" to sneaking, "swimming" to swimming, "gliding" to gliding).joinToString { (what, doing) -> if (doing) what else "not $what" })
    }

    private fun hurtByFall(distance: Float, landedOn: String?) {
        if (!world.rules.fallDamage || effects[Effect.SLOW_FALLING] != null) return
        val share = when (landedOn) {
            "minecraft:hay_block", "minecraft:honey_block" -> SOFT_LANDING
            "minecraft:slime" -> if (sneaking) 1f else 0f
            else -> if (landedOn?.endsWith("bed") == true) BED_LANDING else 1f
        }
        // A hair over a whole number of blocks, as a float has it, isn't another block.
        val damage = ceil((distance - SAFE_FALL - effects.level(Effect.JUMP_BOOST)) * share - FALL_SLACK)
        if (damage > 0) hurt(damage, DamageCause.FALL)
    }

    /**
     * The player's own flags: those every player has, but for breathing,
     * which they stop doing under water, and whether they sprint, sneak,
     * swim, glide, burn or eat.
     */
    private fun playerFlags(): Set<ActorFlag> = PLAYER_FLAGS - ActorFlag.BREATHING +
        listOfNotNull(
            ActorFlag.BREATHING.takeIf { breathing }, ActorFlag.SPRINTING.takeIf { sprinting }, ActorFlag.SNEAKING.takeIf { sneaking },
            ActorFlag.ON_FIRE.takeIf { vitals.fireTicks > 0 }, ActorFlag.SWIMMING.takeIf { swimming }, ActorFlag.GLIDING.takeIf { gliding },
            ActorFlag.USING_ITEM.takeIf { eating != null || drawing != null || loading != null }, ActorFlag.BLOCKING.takeIf { shieldRaisedAt != null },
        )

    /** Whether the player breathes, rather than holding their breath underwater. */
    private var breathing = true

    private fun playerHeight() = when {
        swimming || gliding -> PLAYER_SWIMMING_HEIGHT
        sneaking -> PLAYER_SNEAKING_HEIGHT
        else -> PLAYER_HEIGHT
    }

    /** Where the player's eyes are: lower than where the client says the player is, which stays the standing height above their feet, while they swim or glide. */
    private fun eyes(): Vec3 = if (swimming || gliding) Vec3(feet.x, feet.y + PLAYER_SWIMMING_EYE_HEIGHT, feet.z) else position

    /**
     * Centres the client's view on [viewCentre], after any packets in
     * [first], then sends the chunks in view that the client doesn't have,
     * nearest first, each for the client to ask for its sub-chunks. Returns
     * how many.
     */
    private fun updateView(radius: Int, vararg first: ServerPacket): Int {
        send(*first, NetworkChunkPublisherUpdate(position.toBlockPos(), radius * 16))

        val inView = chunksAround(viewCentre, radius)
        // The client drops chunks that fall out of view, so those need sending again if they come back.
        sentChunks.retainAll(inView.toSet())
        val unsent = inView.filter(sentChunks::add)
        unsent.chunked(CHUNKS_PER_BATCH).forEach { batch -> send(*batch.map { LevelChunk(it, world.chunkAt(it)) }.toTypedArray()) }
        return unsent.size
    }

    /**
     * The sub-chunks the client asks for, of the chunks it's been sent, as
     * [subChunkEntry] has them. Those of chunks it hasn't been sent, or
     * outside the world, don't come.
     */
    private fun onSubChunkRequest(request: SubChunkRequest) {
        val heightMaps = HashMap<ChunkPos, IntArray>()
        val entries = request.offsets.map { offset ->
            val at = ChunkPos(request.x + offset.x, request.z + offset.z)
            val index = request.y + offset.y - MIN_Y / 16
            when {
                index !in 0 until SUB_CHUNK_COUNT -> SubChunkEntry(offset, SubChunkResult.OUT_OF_WORLD)
                at !in sentChunks -> SubChunkEntry(offset, SubChunkResult.NO_CHUNK)
                else -> subChunkEntry(at, index, offset, heightMaps.getOrPut(at) { world.heightMap(at) })
            }
        }
        send(SubChunk(request.x, request.y, request.z, entries))
    }

    /**
     * The sub-chunk [index] up from the bottom of the world of the chunk at
     * [at], at [offset], as it is now: its blocks and the data they keep, as
     * the game saves it, a furnace's items and all, so the client has them, or
     * that it's all air, with where the chunk's [heightMap] is in it, in
     * each column, which the client lights it by.
     */
    private fun subChunkEntry(at: ChunkPos, index: Int, offset: SubChunkOffset, heightMap: IntArray): SubChunkEntry {
        val chunk = world.chunkAt(at)
        val bottom = MIN_Y + index * 16
        val heights = heightMap.map { height ->
            when {
                height >= bottom + 16 -> 16
                height < bottom -> -1
                else -> height - bottom
            }.toByte()
        }.toByteArray()
        val blocks = chunk.subChunks.getOrNull(index)?.takeUnless { it.palette == listOf(AIR_NETWORK_ID) }
            ?: return SubChunkEntry(offset, SubChunkResult.ALL_AIR, heights = heights)
        val blockEntities = world.savedBlockEntitiesIn(at, index)
        return SubChunkEntry(offset, SubChunkResult.FOUND, subChunkPayload(index, blocks, chunk.liquids.getOrNull(index), blockEntities), heights)
    }

    private fun Vec3.within(distance: Float, of: Vec3) = abs(x - of.x) <= distance && abs(y - of.y) <= distance && abs(z - of.z) <= distance

    private fun Vec3.isPlausible() =
        listOf(x, y, z).all { it.isFinite() } && abs(x) <= MAX_COORDINATE && abs(z) <= MAX_COORDINATE

    /**
     * Opens the player's inventory, which with server-authoritative
     * inventories the client only does when Kore says so. The client can
     * ask again before the first answer arrives, and two answers crash it.
     */
    private fun openInventory() {
        if (inventoryOpen) return
        inventoryOpen = true
        send(ContainerOpen(INVENTORY_WINDOW, INVENTORY_CONTAINER, position.toBlockPos(), entityId = -1))
    }

    /**
     * Agrees to the client closing a window, except one closed by chat
     * opening, which needs no answer. What the player left on the cursor or
     * in the crafting grid goes back among their items. A lectern's book
     * closes with the same window as chat, and gets its answer.
     */
    private fun onContainerClose(packet: ContainerClose) {
        when (packet.windowId) {
            INVENTORY_WINDOW -> {
                inventoryOpen = false
                returnCraftingItems()
                send(ContainerClose(INVENTORY_WINDOW, containerType = 0))
            }
            CHAT_WINDOW -> if (reading) {
                reading = false
                send(ContainerClose(packet.windowId, packet.containerType))
            } else {
                inventoryOpen = false
                closeWindow(reply = false)
            }
            window?.id -> closeWindow(reply = true)
            else -> send(ContainerClose(packet.windowId, packet.containerType))
        }
    }

    /**
     * Carries out the player's item stack requests, and answers them. When
     * they change a container others are looking into too, those others see
     * the change.
     */
    private fun onStackRequests(requests: List<StackRequest>) {
        val before = window?.view?.contents()
        val responses = requests.map(stackRequests::handle)
        send(ItemStackResponse(responses))
        crafts.toList().also { crafts.clear() }.forEach(::afterCraft)
        val open = window ?: return
        // The client knows what its requests changed already, though not what else did since it last saw, like a hopper.
        val shown = open.shownSlots
        if (before != null && shown != null) open.shownSlots = open.view!!.contents().mapIndexed { slot, now -> if (now != before[slot]) now else shown[slot] }
        open.machine?.let { machine ->
            // Taking what a furnace made takes the experience it earned.
            val tookOutput = responses.any { response -> response.accepted && response.containers.any { it.container == ContainerName.FURNACE_RESULT } }
            if (machine is Furnace && tookOutput) wholeExperience(machine.takeExperience()).takeIf { it > 0 }?.let { world.dropExperience(position, it) }
        }
        if (inventory.workstation == Workstation.ENCHANTING_TABLE) offerEnchantments()
        val storage = open.storage ?: return
        if (storage == Storage.ENDER_CHEST) return
        if (responses.none { response -> response.accepted && response.containers.any { it.container == storage.containerName } }) return
        viewers().filter { it !== this }.forEach { it.refreshWindow(open.blocks) }
    }

    /**
     * Opens the chest, barrel, shulker box or ender chest at [pos], of
     * [storage], for the player, if they can: they're in reach, aren't a
     * spectator, and nothing blocks it, like a solid block on a chest. A
     * chest beside another opens with it, as one window. The first player
     * to open it shows everyone with the chunk it opening, and they hear it.
     */
    private fun openStorage(pos: BlockPos, storage: Storage) {
        if (gameMode == GameMode.SPECTATOR || !inReach(pos) || world.isBlockedOpen(pos)) {
            log.debug("{} can't open {} at {}", name, world.stateAt(pos)?.name, pos)
            return
        }
        closeWindow(reply = true)
        val blocks = if (storage == Storage.CHEST) world.chestHalves(pos) else listOf(pos)
        val view = when {
            storage == Storage.ENDER_CHEST -> enderChest
            blocks.size == 2 -> PairedContainer(world.containerAt(blocks[0]), world.containerAt(blocks[1]))
            else -> world.containerAt(pos)
        }
        val first = blocks.none(::seenByOthers)
        val opened = Window(nextWindowId(), ContainerType.CONTAINER, pos, storage, blocks, view)
        opened.shownSlots = view.contents()
        window = opened
        inventory.opened = OpenContainer.named(storage.containerName, view)
        send(ContainerOpen(opened.id, ContainerType.CONTAINER, pos, entityId = -1), contentsOf(opened))
        if (first) showOpening(opened, opening = true)
        log.debug("{} opened {} at {}", name, world.stateAt(pos)?.name, pos)
    }

    /**
     * Opens the [workstation] at [pos] for the player, whose screen the
     * client shows as [type]: a crafting table's grid, three by three, or a
     * stonecutter, smithing table, anvil, grindstone, loom, enchanting
     * table, beacon or cartography table.
     */
    private fun openWorkstation(pos: BlockPos, workstation: Workstation, type: Int) {
        if (gameMode == GameMode.SPECTATOR || !inReach(pos)) return
        closeWindow(reply = true)
        val opened = Window(nextWindowId(), type, pos, storage = null, blocks = listOf(pos), view = null)
        window = opened
        inventory.workstation = workstation
        offeredFor = null
        send(ContainerOpen(opened.id, type, pos, entityId = -1))
        log.debug("{} opened {} at {}", name, world.stateAt(pos)?.name, pos)
    }

    /**
     * Opens the furnace, brewing stand, hopper, dispenser, dropper or
     * crafter at [pos] for the player, which they see at work for as long
     * as it's open, whoever else is looking.
     */
    private fun openMachine(pos: BlockPos) {
        if (gameMode == GameMode.SPECTATOR || !inReach(pos)) return
        val machine: Machine = world.furnaceAt(pos) ?: world.brewingStandAt(pos) ?: world.hopperAt(pos) ?: world.dispenserAt(pos) ?: world.crafterAt(pos) ?: return
        closeWindow(reply = true)
        val (type, names) = when (machine) {
            is Furnace -> machine.kind.window to mapOf(
                machine.kind.ingredient to FURNACE_INPUT..FURNACE_INPUT,
                ContainerName.FURNACE_FUEL to FURNACE_FUEL..FURNACE_FUEL,
                ContainerName.FURNACE_RESULT to FURNACE_OUTPUT..FURNACE_OUTPUT,
            )
            is BrewingStand -> ContainerType.BREWING_STAND to mapOf(
                ContainerName.BREWING_INPUT to BREWING_REAGENT..BREWING_REAGENT,
                ContainerName.BREWING_RESULT to BREWING_BOTTLES,
                ContainerName.BREWING_FUEL to BREWING_FUEL..BREWING_FUEL,
            )
            is Hopper -> ContainerType.HOPPER to mapOf(ContainerName.LEVEL_ENTITY to (0 until HOPPER_SLOTS))
            is Dispenser -> machine.kind.window to mapOf(ContainerName.LEVEL_ENTITY to (0 until DISPENSER_SLOTS))
            is CrafterMachine -> ContainerType.CRAFTER to mapOf(ContainerName.CRAFTER to (0 until CRAFTER_SLOTS), ContainerName.LEVEL_ENTITY to (0 until CRAFTER_SLOTS))
        }
        val opened = Window(nextWindowId(), type, pos, storage = null, blocks = listOf(pos), view = machine.slots, machine = machine)
        window = opened
        inventory.opened = OpenContainer(machine.slots, names, limitsOf(machine))
        send(ContainerOpen(opened.id, type, pos, entityId = -1))
        syncWindow()
        log.debug("{} opened {} at {}", name, world.stateAt(pos)?.name, pos)
    }

    /** How much of each stack each of [machine]'s slots holds, as the game's rules for it say. */
    private fun limitsOf(machine: Machine): (Int, ItemStack) -> Int = { slot, stack -> machine.limit(slot, stack, game.recipes, game.fuel) }

    /**
     * Shows the client how the container it has open is getting on: what's
     * in the slots that changed since it last saw, whoever changed them,
     * like a hopper, and for a furnace or brewing stand, the numbers its
     * screen shows, like how far along cooking is. Run once a tick.
     */
    fun syncWindow() {
        val open = window ?: return
        val view = open.view ?: return
        val slots = view.contents()
        val shown = open.shownSlots
        val packets = mutableListOf<ServerPacket>()
        if (shown == null) {
            packets += contentsOf(open)
        } else {
            slots.forEachIndexed { slot, stack -> if (stack != shown[slot]) packets += InventorySlot(slot, stack?.stack, stack?.id ?: 0, open.id) }
        }
        open.shownSlots = slots
        val data = when (val machine = open.machine) {
            is Furnace -> listOf(machine.cookTime, machine.litTime, machine.litDuration)
            is BrewingStand -> listOf(machine.brewTime, machine.fuel, BREWS_PER_FUEL)
            is Hopper, is Dispenser, is CrafterMachine, null -> emptyList()
        }
        data.forEachIndexed { property, value -> if (open.shownData.getOrNull(property) != value) packets += ContainerSetData(open.id, property, value) }
        open.shownData = data
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /** What the enchanting table the player has open offers for [stack], by the bookshelves around it and the player's seed. */
    private fun enchantingOffers(stack: ItemStack): List<EnchantingOffer> {
        val table = window?.pos?.takeIf { inventory.workstation == Workstation.ENCHANTING_TABLE } ?: return emptyList()
        return game.enchantments.offersFor(stack, bookshelvesAround(table), enchantmentSeed)
    }

    /**
     * How many bookshelves power the enchanting table at [table], up to
     * fifteen: those two blocks out from it, on its level or the one above,
     * with nothing but air between.
     */
    private fun bookshelvesAround(table: BlockPos): Int {
        var shelves = 0
        fun shelf(x: Int, y: Int, z: Int) = world.stateAt(BlockPos(table.x + x, table.y + y, table.z + z))?.name == BOOKSHELF
        for (x in -1..1) for (z in -1..1) for (y in 0..1) {
            if (x == 0 && z == 0) continue
            if (world.blockAt(BlockPos(table.x + x, table.y + y, table.z + z)) != AIR_NETWORK_ID) continue
            if (shelf(x * 2, y, z * 2)) shelves++
            if (x != 0 && z != 0) {
                if (shelf(x * 2, y, z)) shelves++
                if (shelf(x, y, z * 2)) shelves++
            }
        }
        return minOf(shelves, MAX_BOOKSHELVES)
    }

    /**
     * Tells the client what the enchanting table it has open offers for the
     * item in it, whenever that item changes: three offers, each in runes,
     * or none for an item the table can't enchant.
     */
    private fun offerEnchantments() {
        val stack = inventory.ui[ENCHANTING_INPUT_SLOT]?.stack
        if (stack == offeredFor) return
        offeredFor = stack
        val runes = Random(enchantmentSeed)
        val options = stack?.let(::enchantingOffers).orEmpty().mapIndexedNotNull { index, offer ->
            if (offer.enchantments.isEmpty()) return@mapIndexedNotNull null
            EnchantOption(offer.cost, index, offer.enchantments.map { OfferedEnchantment(it.id, it.level) }, runes(runes), game.recipes.nextNetworkId + index)
        }
        send(PlayerEnchantOptions(options))
    }

    /**
     * Carries out what a craft at a workstation did besides moving items,
     * once its request has been answered: the levels it cost, the
     * experience it gave back, and an anvil's clang and wear. Enchanting
     * changes what tables offer the player next, paying a beacon gives it
     * its powers, and a map a cartography table zoomed out or locked is a
     * new map.
     */
    private fun afterCraft(crafted: Crafted) {
        if (crafted.levels > 0) addLevels(-crafted.levels)
        if (crafted.experience > 0) world.dropExperience(position, crafted.experience)
        when (crafted.workstation) {
            Workstation.ANVIL -> useAnvil()
            Workstation.ENCHANTING_TABLE -> enchantmentSeed = Random.nextInt()
            Workstation.BEACON -> crafted.beaconPowers?.let { (primary, secondary) -> powerBeacon(primary, secondary) }
            Workstation.CARTOGRAPHY_TABLE -> {
                crafted.newMaps.forEach(world.maps::add)
                crafted.made?.let(::showMade)
            }
            else -> {}
        }
        log.debug("{} crafted at {}, for {} levels", name, crafted.workstation, crafted.levels)
    }

    /**
     * Shows the client the items like [made] it has, wherever the request
     * that made them put them: on the cursor or among the player's items.
     * The client makes a cartography table's maps its own way, and those
     * Kore made, with numbers of their own, are the ones that count.
     */
    private fun showMade(made: ItemStack) {
        val packets = mutableListOf<ServerPacket>()
        inventory.items.contents().forEachIndexed { slot, there -> if (there != null && there.stack.sameKindAs(made)) packets += slotOf(slot) }
        inventory.ui[CURSOR_SLOT]?.takeIf { it.stack.sameKindAs(made) }?.let { packets += InventorySlot(CURSOR_SLOT, it.stack, it.id, UI_WINDOW) }
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /** Gives the beacon the player has open the powers they paid for, which everyone with its chunk sees in its data, and hears. */
    private fun powerBeacon(primary: Int, secondary: Int) {
        val pos = window?.pos ?: return
        world.setBeaconPowers(pos, primary, secondary)
        showAround(pos, BlockActorData(pos, world.blockEntityAt(pos)!!), LevelSoundEvent(BEACON_POWER, pos.centre, -1))
        log.debug("{} gave the beacon at {} the powers {} and {}", player?.displayName, pos, primary, secondary)
    }

    /**
     * The anvil the player has open clangs for everyone with its chunk.
     * Outside creative, it may wear, chipped and then damaged, until a
     * damaged one breaks, which closes it for whoever had it open.
     */
    private fun useAnvil() {
        val pos = window?.pos ?: return
        val state = world.stateAt(pos) ?: return
        if (!creative && Random.nextFloat() < ANVIL_WEAR_CHANCE) {
            val worn = WORN_ANVILS[state.name]?.let { world.blocks.find(it, state.properties) }
            if (worn == null) {
                val changes = world.breakBlock(pos)
                showAround(world.packetsFor(changes) + (pos to LevelEvent(LevelEvent.SOUND_ANVIL_BREAK, pos.centre, 0)))
                changes.broken.filterKeys { it != pos }.forEach { (at, broken) -> game.dropBroken(at, broken, harvested = true) }
                viewers().forEach { it.containerBroke(changes.broken.keys) }
                return
            }
            world[pos] = worn
            showAround(pos, UpdateBlock(pos, world.blockAt(pos)))
        }
        showAround(pos, LevelEvent(LevelEvent.SOUND_ANVIL_USE, pos.centre, 0))
    }

    private fun nextWindowId(): Int {
        lastWindowId = lastWindowId % LAST_WINDOW + 1
        return lastWindowId
    }

    /**
     * Closes the window the player has open besides the inventory, telling
     * the client so if [reply], and with [byServer] if it's Kore closing it.
     * What they left in the crafting grid goes back among their items. The
     * last player to close a container shows everyone it closing.
     */
    private fun closeWindow(reply: Boolean, byServer: Boolean = false) {
        val open = window ?: return
        window = null
        returnCraftingItems()
        inventory.opened = null
        inventory.workstation = null
        if (reply) send(ContainerClose(open.id, open.type, serverInitiated = byServer))
        if (open.storage != null && open.blocks.none(::seenByOthers)) showOpening(open, opening = false)
    }

    /** Whether another player in the game is looking into the block at [pos]. */
    private fun seenByOthers(pos: BlockPos) = viewers().any { it !== this && it.isInGame && it.window?.blocks?.contains(pos) == true }

    /**
     * Shows everyone with the chunk a container opening, or closing: a
     * chest's, shulker box's or ender chest's lid, or a barrel's state, with
     * the sound it makes.
     */
    private fun showOpening(open: Window, opening: Boolean) {
        val storage = open.storage ?: return
        if (world.stateAt(open.pos)?.storage != storage) return
        val sound = open.pos to LevelSoundEvent(if (opening) storage.openSound else storage.closeSound, open.pos.centre, world.blockAt(open.pos))
        if (storage.lid) {
            showAround(open.blocks.map { it to BlockEvent(it, BlockEvent.CHANGE_STATE, if (opening) 1 else 0) } + sound)
            return
        }
        val barrel = world.stateAt(open.pos)?.takeIf { it.name == BARREL } ?: return
        world.blocks.find(barrel.name, barrel.properties + (BARREL_OPEN to bit(opening)))?.let { world[open.pos] = it }
        showAround(listOf(open.pos to UpdateBlock(open.pos, world.blockAt(open.pos)), sound))
    }

    /** What every slot of the container [open] shows holds, for the client. */
    private fun contentsOf(open: Window) = InventoryContent(open.id, open.view!!.contents().map { slot -> slot?.let { it.stack to it.id } })

    /** Shows the client what the container it has open holds now, if it shows a block at [blocks]. */
    private fun refreshWindow(blocks: List<BlockPos>) {
        val open = window ?: return
        if (open.view == null || open.blocks.none { it in blocks }) return
        send(contentsOf(open))
        open.shownSlots = open.view.contents()
    }

    /** Closes the player's window if it shows a block that broke, or that a piston moved away, at one of [positions]. */
    internal fun containerBroke(positions: Collection<BlockPos>) {
        if (window?.blocks?.any { it in positions } == true) closeWindow(reply = true, byServer = true)
    }

    /**
     * Puts what the player left on the cursor, in the crafting grids and in
     * the workstation they have open back among their items, as closing the
     * screen does, and drops what doesn't fit.
     */
    private fun returnCraftingItems() {
        val changed = mutableListOf<ServerPacket>()
        for (slot in inventory.heldInScreen) {
            val there = inventory.ui[slot] ?: continue
            inventory.ui[slot] = null
            changed += InventorySlot(slot, null, 0, UI_WINDOW)
            val (filled, left) = inventory.add(there.stack)
            changed += filled.map(::slotOf)
            if (left > 0) throwItem(there.stack.copy(count = left))
        }
        if (changed.isNotEmpty()) send(*changed.toTypedArray())
    }

    /** Follows the hotbar slot the player holds. The offhand reports its item this way too, and changes nothing. */
    private fun onEquip(packet: MobEquipment) {
        if (packet.windowId != INVENTORY_WINDOW || packet.selectedSlot !in 0 until HOTBAR_SIZE) return
        heldSlot = packet.selectedSlot
        // Holding something else stops them using what they held.
        if (stopUsingWhatsGone()) send(SetActorData(entityId, listOf(flagsItem(playerFlags()))))
    }

    /**
     * Stops the player eating, drawing or loading what they no longer hold,
     * dropped, moved or swapped for something else, which their client lets
     * go of without a word. Left, they'd be shown still using it, and their
     * client would take it up again by itself when they next held it.
     * Returns whether they stopped.
     */
    private fun stopUsingWhatsGone(): Boolean {
        val held = heldStack?.item?.name
        val was = eating != null || drawing != null || loading != null
        if (eating?.let { it.slot != heldSlot || it.item != held } == true) eating = null
        if (drawing?.let { it.slot != heldSlot || (held != BOW && held != TRIDENT) } == true) drawing = null
        if (loading?.let { it.slot != heldSlot || held != CROSSBOW } == true) loading = null
        return was && eating == null && drawing == null && loading == null
    }

    /**
     * The player picking the block they look at, with a middle click, as
     * the game has it: the hotbar slot holding its item, if one does, or
     * else its item moved into their hotbar from the rest of their items,
     * into the first empty slot, or the one they hold if none is, swapping
     * places with what was there. In creative, a block they
     * have none of goes into their hand that way, what was there moving
     * among their other items, and with control held, it carries what the
     * block keeps, like a chest's items. They're shown the slots that
     * changed, and hold the one with the block's item.
     */
    private fun onBlockPick(packet: BlockPick) {
        val pos = packet.position
        if (gameMode == GameMode.SPECTATOR || !inReach(pos)) return
        if (packet.hotbarSlot in 0 until HOTBAR_SIZE) heldSlot = packet.hotbarSlot
        val state = world.stateAt(pos) ?: return
        var picked = pickedItem(state, world.blockEntityAt(pos), game.items) ?: return
        if (packet.withData && creative) picked = world.withBlockData(picked, pos) ?: picked
        val items = inventory.items
        val found = (0 until items.size).firstOrNull { slot ->
            items[slot]?.stack?.let { it.item == picked.item && it.aux == picked.aux && it.nbt == picked.nbt } == true
        }
        val changed = mutableListOf<Int>()
        when {
            found != null && found < HOTBAR_SIZE -> heldSlot = found
            found != null -> {
                heldSlot = hotbarSlotForPick()
                items[heldSlot] = items[found].also { items[found] = items[heldSlot] }
                changed += listOf(heldSlot, found)
            }
            !creative -> return
            else -> {
                heldSlot = hotbarSlotForPick()
                val free = (0 until items.size).firstOrNull { items[it] == null }
                if (items[heldSlot] != null && free != null) {
                    items[free] = items[heldSlot]
                    changed += free
                }
                items[heldSlot] = SlotStack(picked, inventory.newStackId())
                changed += heldSlot
            }
        }
        send(*changed.map(::slotOf).toTypedArray(), ShowEquipment(entityId, items[heldSlot]?.stack, heldSlot))
        log.debug("{} picked {} at {}, into slot {}", player?.displayName, picked.item.name, pos, heldSlot)
    }

    /** The hotbar slot a picked item goes in: the first empty one, from the left, or else the one held. */
    private fun hotbarSlotForPick(): Int = (0 until HOTBAR_SIZE).firstOrNull { inventory.items[it] == null } ?: heldSlot

    /**
     * A transaction from before item stack requests, which the client still
     * sends for a few things, or the item use riding in the player's input
     * at the client's [tick]. When a use changes what the player holds in a
     * way the client didn't predict, like wearing out a tool it thought had
     * a use left, the client hears what the slot holds now.
     */
    private fun onTransaction(packet: InventoryTransaction, tick: Long? = null) {
        val held = inventory.items[heldSlot]
        heldShown = false
        when (packet.type) {
            InventoryTransaction.USE_ITEM -> packet.use?.let { onItemUse(it, tick, packet.legacyRequestId) }
            InventoryTransaction.RELEASE_ITEM -> {
                eating = null
                drawing?.let { if (inventory.items[it.slot]?.stack?.item?.name == TRIDENT) throwTrident(it) else loose(it) }
                drawing = null
                loading = null
            }
            InventoryTransaction.USE_ITEM_ON_ENTITY -> packet.useOnEntity?.takeIf { it.action == ItemUseOnEntity.ATTACK }?.let(::attack)
            InventoryTransaction.NORMAL -> onDropFromSlot(packet.actions)
        }
        val acknowledged = if (packet.legacyRequestId != 0) acknowledge(packet) else emptyList()
        if (packet.type == InventoryTransaction.USE_ITEM && inventory.items[heldSlot] != held && heldSlot !in acknowledged && !heldShown) send(slotOf(heldSlot))
    }

    /**
     * Tells the client what the slots of the player's items [transaction]
     * changed hold now, with their stacks' IDs. Until it hears, the client
     * names those stacks by the transaction's legacy request ID, which Kore
     * remembers for the item stack requests that do, like moving the items
     * placing a candle on a candle left behind, or the tool that just broke
     * a block. Returns the slots the client heard about.
     */
    private fun acknowledge(transaction: InventoryTransaction): List<Int> {
        val slots = transaction.actions
            .filter { it.source == InventoryAction.CONTAINER && it.windowId == INVENTORY_WINDOW && it.slot in 0 until inventory.items.size }
            .map { it.slot }
            .distinct()
        if (slots.isEmpty()) return slots
        stackRequests.rememberLegacy(transaction.legacyRequestId, slots)
        send(*slots.map(::slotOf).toTypedArray())
        return slots
    }

    /**
     * Throws items from one of the player's slots, as pressing Q outside
     * the inventory screen does: one item, or the whole stack with Ctrl. On
     * mobile, the slot needn't be the one held. The client sends it as two
     * slot changes it has already made, the items leaving the slot and going
     * into the world, rather than as an item stack request. Kore throws them
     * if what it has in the slot is what the client had there. Either way,
     * the client hears what the slot holds now, which puts back a drop Kore
     * refused.
     */
    private fun onDropFromSlot(actions: List<InventoryAction>) {
        val drop = actions.singleOrNull { it.source == InventoryAction.WORLD && it.slot == InventoryAction.DROP_SLOT }
        val from = actions.singleOrNull { it.source == InventoryAction.CONTAINER && it.windowId == INVENTORY_WINDOW }
        if (actions.size != 2 || drop == null || from == null || from.slot !in 0 until inventory.items.size) {
            log.debug("Ignored a transaction of {} from {}", actions, player?.displayName)
            return
        }
        val there = inventory.items[from.slot]
        val count = drop.after.count
        // The client's stack must be Kore's: the same item, and as many of it.
        val same = there != null && there.stack.item.runtimeId == from.before.runtimeId && there.stack.count == from.before.count
        if (same && count in 1..there.stack.count && gameMode != GameMode.SPECTATOR) {
            inventory.remove(from.slot, count)
            throwItem(there.stack.copy(count = count))
        } else {
            log.debug("Refused {} dropping {} of {} from slot {}", player?.displayName, count, there?.stack?.item?.name, from.slot)
        }
        send(slotOf(from.slot))
        if (stopUsingWhatsGone()) send(SetActorData(entityId, listOf(flagsItem(playerFlags()))))
    }

    /**
     * The player using their item, at the client's [tick] when it came in
     * their input, in a transaction the client names the stacks it changes
     * by, [legacyRequestId], if it isn't 0.
     */
    private fun onItemUse(use: ItemUse, tick: Long? = null, legacyRequestId: Int = 0) {
        when (use.action) {
            ItemUseAction.PLACE -> onClick(use)
            ItemUseAction.DESTROY -> onBreak(use.position, creativeOnly = false, tick)
            ItemUseAction.USE -> useInAir(use, tick, legacyRequestId)
            // Attacking with an item is for later.
            ItemUseAction.USE_AS_ATTACK -> {}
        }
    }

    /**
     * The player using what they hold in the air. Armour goes on, swapping
     * places with what they wore there, as the client has it already, naming
     * the piece it put on by [legacyRequestId]. An empty map becomes a map of
     * the square of the world they're in, which they draw as they hold it,
     * and an empty locator map a locator map, in creative too. With more than
     * one empty map, the map goes among their items, or drops at their feet
     * if there's no room. Food is eaten, as [eatFrom] has it, by the client's
     * [tick], snowballs, eggs and ender pearls are thrown, and a bow drawn.
     */
    private fun useInAir(use: ItemUse, tick: Long?, legacyRequestId: Int) {
        if (use.hotbarSlot in 0 until HOTBAR_SIZE) heldSlot = use.hotbarSlot
        val held = inventory.items[heldSlot]?.takeIf { use.hotbarSlot == heldSlot && it.stack.item.runtimeId == use.heldItem } ?: return
        if (gameMode == GameMode.SPECTATOR) return
        armourSlotFor(held.stack.item.name)?.let { return putOn(it, held, legacyRequestId) }
        held.stack.item.food?.let { return eatFrom(held, it, tick ?: aliveTicks.toLong()) }
        ProjectileKind.thrownFrom(held.stack.item.name)?.let { return throwHeld(it) }
        if (held.stack.item.name == BOW || held.stack.item.name == TRIDENT) return draw(held)
        if (held.stack.item.name == CROSSBOW) return useCrossbow(held)
        if (held.stack.item.name != EMPTY_MAP) return
        val blank = game.items.of(FILLED_MAP) ?: return
        val map = world.maps.start(floor(position.x).toInt(), floor(position.z).toInt(), showsPlayers = held.stack.aux == LOCATOR)
        val drawn = blank.showing(map)
        world.drawMap(map, position.x, position.z, game.traits)
        swapHeld(drawn)
        log.debug("{} started map {}, around {}, {}", player?.displayName, map.id, map.centreX, map.centreZ)
    }

    /**
     * Throws one of what the player holds, as [kind], from their eyes the
     * way they look, unless projectiles are switched off, or they're still
     * waiting to throw another of its kind, as after an ender pearl, which
     * they then wait a moment to throw again. Everyone around hears it.
     */
    private fun throwHeld(kind: ProjectileKind) {
        if (!world.features.projectiles) return
        val cooldown = kind.cooldown
        if (cooldown != null && (cooldowns[cooldown.category] ?: 0) > aliveTicks) return
        val look = lookVector(yaw, pitch)
        val from = eyes()
        world.spawnProjectile(kind, entityId, Vec3(from.x, from.y - kind.drop, from.z), Vec3(look.x * THROW_SPEED, look.y * THROW_SPEED, look.z * THROW_SPEED))
        if (cooldown != null) {
            cooldowns[cooldown.category] = aliveTicks + cooldown.ticks
            send(PlayerStartItemCooldown(cooldown.category, cooldown.ticks))
        }
        if (!creative) inventory.remove(heldSlot)
        showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.THROW, position, -1, entityType = PLAYER_ENTITY))
        log.debug("{} threw {}", name, kind.name.lowercase())
    }

    /**
     * The player starting to draw back the bow or trident they hold, [held],
     * unless projectiles are switched off, they've no arrow to shoot from a
     * bow, outside creative, or the trident's worn to its last use. The
     * client goes on asking while they hold use down, which doesn't start it
     * over.
     */
    private fun draw(held: SlotStack) {
        if (!world.features.projectiles) return
        val bow = held.stack.item.name == BOW
        if (bow && !creative && arrowToShoot() == null) return
        if (!bow && held.stack.damage >= held.stack.item.maxDurability - 1) return
        if (drawing?.slot != heldSlot) drawing = Drawing(heldSlot, aliveTicks)
    }

    /** Whether the player's in water or out in the rain, where Impaling hurts them more and Riptide launches them. */
    private val inWaterOrRain get() = world.isWet(feet.toBlockPos()) || world.isWet(position.toBlockPos()) || world.rainingAt(position.toBlockPos())

    /**
     * Throws the trident the player [drawn] back, as they let go: unless
     * they let go too soon, it flies from a little below their eyes, the way
     * they look, worn a use, leaving their hand, but in creative, where it
     * stays. A trident with Riptide isn't thrown, but launches them, in
     * water or rain, which their client does itself. Everyone around hears
     * it.
     */
    private fun throwTrident(drawn: Drawing) {
        val held = inventory.items[drawn.slot]?.takeIf { drawn.slot == heldSlot && it.stack.item.name == TRIDENT } ?: return
        if (aliveTicks - drawn.since < TRIDENT_CHARGE_TICKS) return
        val riptide = held.stack.levelOf(RIPTIDE)
        if (riptide > 0) {
            if (!inWaterOrRain) return
            if (!creative) wearHeld(1, unforeseen = true)
            showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.TRIDENT_RIPTIDE[minOf(riptide, 3) - 1], position, -1))
            log.debug("{} spun off by Riptide {}", name, riptide)
            return
        }
        val trident = if (creative) held.stack else held.stack.worn(1) ?: return
        val look = lookVector(yaw, pitch)
        val from = eyes()
        world.spawnProjectile(
            ProjectileKind.TRIDENT, entityId, Vec3(from.x, from.y - ProjectileKind.TRIDENT.drop, from.z),
            Vec3(look.x * TRIDENT_SPEED, look.y * TRIDENT_SPEED, look.z * TRIDENT_SPEED), item = trident, fromCreative = creative,
        )
        if (!creative) {
            inventory.items[heldSlot] = null
            send(slotOf(heldSlot))
        }
        showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.TRIDENT_THROW, position, -1))
        log.debug("{} threw their trident", name)
    }

    /**
     * The player using the crossbow they hold, [held], unless projectiles
     * are switched off: one that's loaded fires, straight away, and one
     * that isn't starts loading, unless they've no arrow, outside
     * creative. While it loads, the client goes on asking once each time a
     * load would have finished, and the first to come once it has, loads
     * it. Held down after firing, the client starts it loading again a
     * moment later.
     */
    private fun useCrossbow(held: SlotStack) {
        if (!world.features.projectiles) return
        if (held.stack.nbt.containsKey(CHARGED_ITEM)) return fireCrossbow(held)
        val started = loading?.takeIf { it.slot == heldSlot }
        val quickCharge = held.stack.levelOf(QUICK_CHARGE)
        if (started == null) {
            if (!creative && arrowToShoot() == null) return
            loading = Drawing(heldSlot, aliveTicks)
            send(SetActorData(entityId, listOf(flagsItem(playerFlags()))))
            val sound = if (quickCharge > 0) LevelSoundEvent.CROSSBOW_QUICK_CHARGE_START else LevelSoundEvent.CROSSBOW_LOADING_START
            showAround(position.toBlockPos(), LevelSoundEvent(sound, position, -1))
        } else if (aliveTicks - started.since >= crossbowChargeTicks(quickCharge) - CROSSBOW_CHARGE_SLACK) {
            loadCrossbow(held)
        }
    }

    /** Sounds the crossbow the player's loading partway through, for everyone around to hear, as Quick Charge has it. Run once a tick. */
    private fun soundLoading() {
        val started = loading ?: return
        val quickCharge = inventory.items[started.slot]?.stack?.levelOf(QUICK_CHARGE) ?: return
        if (aliveTicks - started.since != crossbowMiddleTick(quickCharge)) return
        val sound = if (quickCharge > 0) LevelSoundEvent.CROSSBOW_QUICK_CHARGE_MIDDLE else LevelSoundEvent.CROSSBOW_LOADING_MIDDLE
        showAround(position.toBlockPos(), LevelSoundEvent(sound, position, -1))
    }

    /**
     * Loads the crossbow the player holds, [held], with the arrow they'd
     * shoot, which it uses up, outside creative, where it's a plain arrow if
     * they have none. The client hears all of it together, and nothing of
     * what it holds after: the arrow gone, the crossbow loaded, under its
     * stack's own ID, that it's done charging, and that the player's no
     * longer using it. Hearing of it otherwise, it takes the crossbow up
     * again while use is held down, and draws it back again. Everyone around
     * hears it load.
     */
    private fun loadCrossbow(held: SlotStack) {
        loading = null
        val loaded = arrowToShoot()
        val arrow = loaded?.let { (container, slot) -> container[slot] }
        val ammo = arrow?.stack?.copy(count = 1) ?: game.items.of(ARROW)?.takeIf { creative } ?: return
        val packets = mutableListOf<ServerPacket>()
        if (!creative && loaded != null && arrow != null) {
            val (container, slot, window) = loaded
            packets += replaceSlot(container, slot, window, arrow.stack.copy(count = arrow.stack.count - 1).takeIf { it.count > 0 })
        }
        val charged = NbtMap.builder().putString("Name", ammo.item.name).putByte("Count", 1).putShort("Damage", ammo.aux.toShort()).putByte("WasPickedUp", 0).build()
        inventory.items[heldSlot] = held.copy(stack = held.stack.copy(nbt = held.stack.nbt.toBuilder().putCompound(CHARGED_ITEM, charged).build()))
        packets += slotOf(heldSlot)
        packets += ActorEvent(entityId, ActorEvent.FINISHED_CHARGING_ITEM, 0)
        packets += SetActorData(entityId, listOf(flagsItem(playerFlags())))
        send(*packets.toTypedArray())
        heldShown = true
        val sound = if (held.stack.levelOf(QUICK_CHARGE) > 0) LevelSoundEvent.CROSSBOW_QUICK_CHARGE_END else LevelSoundEvent.CROSSBOW_LOADING_END
        showAround(position.toBlockPos(), LevelSoundEvent(sound, position, -1))
        log.debug("{} loaded their crossbow with {}", name, ammo.item.name)
    }

    /**
     * Fires the crossbow the player holds, [held]: its arrow, critical, from
     * a little below their eyes, the way they look, and with Multishot, two
     * more either side, which can't be picked up again, and with Piercing,
     * through as many more as its level. It wears for each arrow, under the
     * same stack ID, as the use that fired it is answered, and everyone
     * around hears it.
     */
    private fun fireCrossbow(held: SlotStack) {
        val crossbow = held.stack
        val ammo = crossbow.nbt.getCompound(CHARGED_ITEM)
        val potion = ammo.getShort("Damage").toInt().takeIf { it > 0 }?.minus(1)
        val turns = if (crossbow.levelOf(MULTISHOT) > 0) listOf(0f, -MULTISHOT_SPREAD, MULTISHOT_SPREAD) else listOf(0f)
        val from = eyes()
        for (turn in turns) {
            val look = lookVector(yaw + turn, pitch)
            val shot = ArrowShot(ARROW_DAMAGE, critical = true, returned = !creative && turn == 0f, potion = potion, piercing = crossbow.levelOf(PIERCING))
            world.spawnProjectile(
                ProjectileKind.ARROW, entityId, Vec3(from.x, from.y - ProjectileKind.ARROW.drop, from.z),
                Vec3(look.x * CROSSBOW_SPEED, look.y * CROSSBOW_SPEED, look.z * CROSSBOW_SPEED), shot,
            )
        }
        val unloaded = crossbow.copy(nbt = crossbow.nbt.toBuilder().apply { remove(CHARGED_ITEM) }.build())
        val worn = if (creative) unloaded else unloaded.worn(turns.size)
        inventory.items[heldSlot] = worn?.let { held.copy(stack = it) }
        if (worn == null) showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.BREAK, position, -1))
        showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.CROSSBOW_SHOOT, position, -1))
        log.debug("{} fired their crossbow", name)
    }

    /** Where the arrow the player would shoot is: in their offhand, or else the first slot of their items with one, as its container, slot and window. */
    private fun arrowToShoot(): Triple<Container, Int, Int>? {
        if (inventory.offhand[0]?.stack?.item?.name == ARROW) return Triple(inventory.offhand, 0, OFFHAND_WINDOW)
        val slot = (0 until inventory.items.size).firstOrNull { inventory.items[it]?.stack?.item?.name == ARROW } ?: return null
        return Triple(inventory.items, slot, INVENTORY_WINDOW)
    }

    /**
     * Looses an arrow from the bow the player [drawn], as they let go: from
     * a little below their eyes, the way they look, as fast as how far they
     * drew it, unless they let go too soon, or it's left their hand. A bow
     * drawn all the way shoots a critical arrow. Power makes it hurt more,
     * Punch knock back further and Flame burn, and Infinity keeps a plain
     * arrow from being used up, as creative does any, though neither's can
     * be picked up again. The bow wears, and everyone around hears it.
     */
    private fun loose(drawn: Drawing) {
        val bow = inventory.items[drawn.slot]?.stack?.takeIf { drawn.slot == heldSlot && it.item.name == BOW } ?: return
        val force = bowForce(aliveTicks - drawn.since) ?: return
        val loaded = arrowToShoot()
        val arrow = loaded?.let { (container, slot) -> container[slot] }
        if (arrow == null && !creative) return
        val potion = arrow?.stack?.aux?.takeIf { it > 0 }?.minus(1)
        val spared = creative || (bow.levelOf(INFINITY) > 0 && potion == null)
        val shot = ArrowShot(
            ARROW_DAMAGE + powerDamage(bow.levelOf(POWER)), critical = force >= 1f, punch = bow.levelOf(PUNCH), burns = bow.levelOf(FLAME) > 0,
            returned = !spared, potion = potion,
        )
        val look = lookVector(yaw, pitch)
        val speed = force * BOW_SPEED
        val from = eyes()
        world.spawnProjectile(ProjectileKind.ARROW, entityId, Vec3(from.x, from.y - ProjectileKind.ARROW.drop, from.z), Vec3(look.x * speed, look.y * speed, look.z * speed), shot)
        if (!spared && loaded != null && arrow != null) {
            val (container, slot, window) = loaded
            send(replaceSlot(container, slot, window, arrow.stack.copy(count = arrow.stack.count - 1).takeIf { it.count > 0 }))
        }
        if (!creative) wearHeld(1, unforeseen = true)
        showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.BOW, position, -1))
        log.debug("{} shot an arrow, at {} force{}", name, force, if (shot.critical) ", critically" else "")
    }

    /**
     * The player eating or drinking [food], [held], as the client says, by
     * its [tick]: the first use starts them eating, unless their food bar's
     * full and it isn't a food they can always eat, though in creative and
     * in peace any food can be eaten whenever, or they're still waiting to
     * eat another of its kind. Once it's taken as long as the food takes,
     * the next use finishes it, and starts on the next of it while they
     * hold the button down. Nothing's eaten with food switched off.
     */
    private fun eatFrom(held: SlotStack, food: Food, tick: Long) {
        if (!world.features.food) return
        val meal = eating
        if (meal == null || meal.slot != heldSlot || meal.item != held.stack.item.name) {
            if (eatsOnlyHungry() && hunger.full && !food.canAlwaysEat) return
            if (food.cooldown?.let { (cooldowns[it.category] ?: 0) > aliveTicks } == true) return
            eating = Eating(heldSlot, held.stack.item.name, tick)
            return
        }
        if (tick - meal.since < food.ticks - EATING_SLACK) return
        eating = null
        eat(held, food)
        // Holding the button down, the client goes straight on to the next of them, if there is one, and
        // the use that finishes that is the next it sends. Its wait between chorus fruit doesn't stop it.
        if (!dead && inventory.items[heldSlot]?.stack?.item?.name == meal.item) eating = Eating(heldSlot, meal.item, tick)
    }

    /**
     * The player finishing [food], [held]: it fills their food bar, gives
     * its effects, each by its chance, cures what it cures, and does what
     * more it does, like a potion's effects, then it's gone from their
     * hand, but in creative, leaving what it leaves, like a bowl or a glass
     * bottle. Their wait for another of its kind starts. Food makes them
     * burp, for everyone around to hear, and a drink doesn't.
     */
    private fun eat(held: SlotStack, food: Food) {
        if (food.feeds) hunger.eat(food.nutrition, food.saturationModifier)
        for (effect in food.effects) {
            if (Random.nextFloat() < effect.chance) giveEffect(EffectLevel(effect.effect, effect.amplifier), effect.seconds * TICKS_PER_SECOND, ambient = false)
        }
        food.cures.forEach { takeEffects(it) }
        val aux = held.stack.aux
        when (food.action) {
            FoodAction.CHORUS_TELEPORT -> chorusTeleport()
            FoodAction.SUSPICIOUS_STEW -> suspiciousStewEffect(aux)?.let { (effect, ticks) -> giveEffect(EffectLevel(effect), ticks, ambient = false) }
            FoodAction.POTION -> potionEffects(aux).forEach { giveEffect(it.level, it.ticks, ambient = false) }
            FoodAction.MILK -> takeEffects()
            FoodAction.OMINOUS -> giveEffect(EffectLevel(Effect.BAD_OMEN, aux.coerceIn(0, MAX_OMINOUS_AMPLIFIER)), OMINOUS_TICKS, ambient = false)
            null -> {}
        }
        food.cooldown?.let {
            cooldowns[it.category] = aliveTicks + it.ticks
            send(PlayerStartItemCooldown(it.category, it.ticks))
        }
        if (!creative) {
            val leaves = food.leaves?.let { game.items.of(it) }
            if (leaves != null) swapHeld(leaves) else inventory.remove(heldSlot)
        }
        if (food.feeds) showAround(position.toBlockPos(), LevelSoundEvent(BURP, position, -1))
        sendHunger()
        log.debug("{} ate {}, and has {} food and {} saturation", name, held.stack.item.name, hunger.food, hunger.saturation)
    }

    /** Whether the player only eats while they're hungry: in survival or adventure, but not in peace. */
    private fun eatsOnlyHungry() = (gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE) && world.difficulty != Difficulty.PEACEFUL

    /**
     * The player clicking the cake [cake]: a candle in hand goes on top of
     * it while it's whole, if they can build, a lit candle on it goes out,
     * and otherwise they eat a slice of it. Flint and steel or a fire charge
     * lights a candle on it instead, as [useItemOn] has it. Everyone with
     * the chunk sees and hears it. Returns whether anything happened.
     */
    private fun useCake(use: ItemUse, cake: BlockState): Boolean {
        if (use.hotbarSlot !in 0 until HOTBAR_SIZE || !inReach(use.position)) return false
        heldSlot = use.hotbarSlot
        val held = inventory.items[heldSlot]?.stack?.item?.name.takeIf { use.heldItem != 0 }
        if (cake.hasCandle && (held == FLINT_AND_STEEL || held == FIRE_CHARGE)) return false
        val candle = held?.takeIf { isCandle(it) && canBuild() }
        val used = candle?.let { world.putCandleOn(use.position, cake, it) } ?: world.putOutCandle(use.position, cake) ?: return eatCake(use, cake)
        showUsed(used, use.position)
        log.debug("{} used {} on {} at {}", player?.displayName, held ?: "their hand", cake.name, use.position)
        return true
    }

    /**
     * The player eating a slice of the cake they clicked, [cake], unless
     * food's switched off, or they're full and only eat while hungry. A
     * slice feeds them, a candle on it drops, and they burp, for everyone
     * around to hear. Returns whether they ate.
     */
    private fun eatCake(use: ItemUse, cake: BlockState): Boolean {
        if (!world.features.food || (eatsOnlyHungry() && hunger.full)) return false
        val used = world.biteCake(use.position, cake, game.items) ?: return false
        showUsed(used, use.position)
        hunger.eat(CAKE_NUTRITION, CAKE_SATURATION)
        sendHunger()
        showAround(position.toBlockPos(), LevelSoundEvent(BURP, position, -1))
        log.debug("{} ate cake at {}, and has {} food and {} saturation", name, use.position, hunger.food, hunger.saturation)
        return true
    }

    /**
     * Teleports the player somewhere near at random, as chorus fruit does:
     * up to eight blocks away each way, down onto whatever's under them
     * there, where they fit, trying sixteen times before giving up. Everyone
     * around both places hears it.
     */
    private fun chorusTeleport() {
        repeat(CHORUS_TRIES) {
            val x = feet.x + Random.nextFloat() * 2 * CHORUS_RANGE - CHORUS_RANGE
            val z = feet.z + Random.nextFloat() * 2 * CHORUS_RANGE - CHORUS_RANGE
            var below = BlockPos(floor(x).toInt(), (floor(feet.y).toInt() + Random.nextInt(-CHORUS_RANGE, CHORUS_RANGE + 1)).coerceIn(MIN_Y + 1, MAX_Y) - 1, floor(z).toInt())
            while (below.y > MIN_Y && world.stateAt(below)?.solidHeights() == null) below = below.side(Face.DOWN)
            if (world.stateAt(below)?.solidHeights() == null) return@repeat
            val to = Vec3(x, below.y + 1f, z)
            if (!world.isClear(to, PLAYER_WIDTH, PLAYER_HEIGHT) || world.isWet(to.toBlockPos())) return@repeat
            showAround(position.toBlockPos(), LevelSoundEvent(TELEPORT_SOUND, position, -1))
            teleport(to, yaw, pitch)
            showAround(position.toBlockPos(), LevelSoundEvent(TELEPORT_SOUND, position, -1))
            return
        }
    }

    /**
     * Puts on the armour the player holds, [held], in the armour slot
     * [slot], and puts what they wore there in their hand. Until it hears
     * otherwise, the client names the piece it put on by [legacyRequestId].
     */
    private fun putOn(slot: Int, held: SlotStack, legacyRequestId: Int) {
        val worn = inventory.armour[slot]
        inventory.armour[slot] = held
        inventory.items[heldSlot] = worn
        if (legacyRequestId != 0) stackRequests.rememberLegacyAt(legacyRequestId, listOf(Location(inventory.armour, slot)))
        send(InventorySlot(slot, held.stack, held.id, ARMOUR_WINDOW))
        log.debug("{} put on {}", player?.displayName, held.stack.item.name)
    }

    /**
     * The client picking the number of the locked copy of a map a
     * cartography table would make, as a glass pane goes in beside the map.
     * The copy is made then, for the client to show as what the table makes,
     * and to give the player if they take it.
     */
    private fun onLockedCopy(packet: MapCreateLockedCopy) {
        val map = world.maps[packet.mapId]?.takeIf { !it.locked } ?: return
        if (packet.copyId == 0L || packet.copyId == -1L || packet.copyId in world.maps) return
        val copy = map.lockedCopy(packet.copyId)
        world.maps.add(copy)
        lockedCopy = map.id to copy.id
        knownMaps += copy.id
        send(world.mapData(copy, copy.patch()))
    }

    /**
     * The client asking what a map shows, which it hears whole, and of every
     * change to it from then on. It learns of the maps it zooms out to with
     * it, and never asks about those, so it hears of their changes too.
     */
    private fun onMapInfoRequest(packet: MapInfoRequest) {
        val map = world.maps[packet.mapId] ?: return
        knownMaps += world.maps.lineage(map)
        send(world.mapData(map, map.patch()))
    }

    /** Sends [packets] about a map the client has asked about. */
    internal fun showMap(packets: List<ServerPacket>) = send(*packets.toTypedArray())

    /** Draws the maps the player holds, in either hand, as they go. Run once a tick. */
    fun drawHeldMaps() {
        if (!isInGame) return
        val held = listOfNotNull(inventory.items[heldSlot]?.stack?.mapId, inventory.offhand[0]?.stack?.mapId).distinct().mapNotNull(world.maps::get)
        held.forEach { world.drawMap(it, position.x, position.z, game.traits) }
    }

    /**
     * Gives the player [given] for [ticks], as a beacon or a potion does,
     * unless they have as much of it already. They see it start, or change.
     * Instant Health and Instant Damage heal or hurt them at once instead.
     * The client moves its player by its effects itself: Speed quickens
     * their walk, sprinting or not, which the walking speed Kore gives them
     * would undo.
     */
    internal fun giveEffect(given: EffectLevel, ticks: Int, ambient: Boolean) {
        if (tookInstantly(given)) return
        val had = effects[given.effect] != null
        val visible = effects.visible()
        if (!effects.give(given, ticks, ambient)) return
        send(MobEffect(entityId, if (had) MobEffect.MODIFY else MobEffect.ADD, given.effect, given.amplifier, ticks, ambient))
        if (effects.visible() != visible) send(visibleEffects())
        effectStarted(given)
    }

    /**
     * Gives the player [given] for [ticks], or for ever, for
     * [INFINITE_EFFECT], whatever they had of it, as `/effect` does, its
     * [particles] showing or not. They see it start, or change. Instant
     * Health and Instant Damage heal or hurt them at once, and are gone.
     */
    fun setEffect(given: EffectLevel, ticks: Int, particles: Boolean) {
        if (tookInstantly(given)) return
        val had = effects[given.effect] != null
        effects.set(given, ticks, particles)
        send(MobEffect(entityId, if (had) MobEffect.MODIFY else MobEffect.ADD, given.effect, given.amplifier, ticks, particles = particles), visibleEffects())
        effectStarted(given)
    }

    /** Heals or hurts the player at once, for Instant Health or Instant Damage, [given]. Returns whether it was one of them. */
    private fun tookInstantly(given: EffectLevel): Boolean {
        // Doubling a level at a time, as far as a number goes.
        val strength = 1 shl minOf(given.amplifier, MAX_DOUBLINGS)
        when (given.effect) {
            Effect.INSTANT_HEALTH -> heal(INSTANT_HEALTH * strength)
            Effect.INSTANT_DAMAGE -> hurt(INSTANT_DAMAGE * strength, DamageCause.MAGIC)
            else -> return false
        }
        return true
    }

    /** Takes [effect] from the player, or every effect, for null. Returns whether they had any to take. */
    fun takeEffects(effect: Int? = null): Boolean {
        val taken = if (effect == null) effects.clear() else listOfNotNull(effect.takeIf(effects::remove))
        if (taken.isEmpty()) return false
        send(*taken.map { MobEffect(entityId, MobEffect.REMOVE, it) }.toTypedArray(), visibleEffects())
        effectsEnded(taken)
        return true
    }

    /** Wears the player's effects down a tick. They see those that wear off end. Run once a tick. */
    fun tickEffects() {
        val ended = effects.tick()
        for (effect in ended) send(MobEffect(entityId, MobEffect.REMOVE, effect))
        if (ended.isNotEmpty()) send(visibleEffects())
        effectsEnded(ended)
    }

    /** What [given] does to the player's hearts as it starts: Health Boost adds to how many they can have, and Absorption puts more on top. */
    private fun effectStarted(given: EffectLevel) {
        when (given.effect) {
            Effect.HEALTH_BOOST -> vitals.maxHealth = MAX_HEALTH + BOOST_PER_LEVEL * (given.amplifier + 1)
            Effect.ABSORPTION -> vitals.absorption = maxOf(vitals.absorption, BOOST_PER_LEVEL * (given.amplifier + 1))
            else -> return
        }
        sendHealth()
    }

    /** What the [ended] effects leave of the player's hearts: no more than they could have without Health Boost, and no Absorption. */
    private fun effectsEnded(ended: List<Int>) {
        if (Effect.HEALTH_BOOST in ended) vitals.maxHealth = MAX_HEALTH
        if (Effect.ABSORPTION in ended) vitals.absorption = 0f
        if (Effect.HEALTH_BOOST in ended || Effect.ABSORPTION in ended) sendHealth()
    }

    /** The particles of the effects on the player, which everyone else sees as the player list syncs. */
    private fun visibleEffects() = SetActorData(entityId, listOf(LongItem(ActorDataId.VISIBLE_MOB_EFFECTS, effects.visible())))


    /**
     * A click on a block, with whatever the player holds, which the client
     * reports once it has predicted what happens. Clicking a block that
     * does something, like a lever, uses it, and clicking one that opens a
     * screen, like a chest, places nothing. Otherwise, or when the player
     * sneaks with something in hand, the click places what they hold.
     *
     * The client checks some things Kore doesn't, like whether a torch has
     * a face to stand on, or whether a block would go inside its player.
     * When it finds a click does nothing, it changes nothing, and Kore
     * leaves it at that. While the player holds the button down, the
     * client tries again every frame, so that's often. It leaves some uses
     * to the server, though, and finds they do nothing: a hoe tilling, or
     * an axe stripping a log. Kore does those, when the player clicks.
     */
    /** Whether [use] puts a mob or player head on top of [clicked], a note block, which places the head rather than tuning the note block. */
    private fun headOnNoteBlock(use: ItemUse, clicked: BlockState?): Boolean {
        if (clicked?.isNoteBlock != true || Face.of(use.face) != Face.UP) return false
        if (use.hotbarSlot !in 0 until HOTBAR_SIZE) return false
        val held = inventory.items[use.hotbarSlot]?.stack?.item?.takeIf { it.runtimeId == use.heldItem } ?: return false
        return held.name.endsWith("_head") || held.name.endsWith("_skull")
    }

    private fun onClick(use: ItemUse) {
        val clicked = world.stateAt(use.position)
        // With something in hand, sneaking passes over what a block does when it's clicked, but not what the item does to it.
        val blockFirst = !sneaking || use.heldItem == 0
        if (!use.predictedToWork) {
            // Not each time the client tries again, which would scrape copper bare as the button's held.
            if (!use.repeated && clicked != null) useItemOn(use, clicked, blockFirst)
            return
        }
        when {
            blockFirst && clicked?.usable == true -> useBlock(use.position)
            blockFirst && clicked?.isLectern == true && useLectern(use) -> {}
            // A chest, anvil or the like opens for the client, which Kore can't show yet. Either way, nothing's placed.
            blockFirst && clicked?.storage != null -> openStorage(use.position, clicked.storage!!)
            blockFirst && clicked?.name in WORKSTATIONS -> WORKSTATIONS.getValue(clicked!!.name).let { (workstation, type) -> openWorkstation(use.position, workstation, type) }
            blockFirst && (clicked?.furnaceKind != null || clicked?.name == BREWING_STAND || clicked?.name == HOPPER || clicked?.dispenserKind != null || clicked?.isCrafter == true) ->
                openMachine(use.position)
            blockFirst && clicked?.isSign == true -> useSign(use, clicked)
            blockFirst && clicked?.isCake == true && useCake(use, clicked) -> {}
            clicked != null && useItemOn(use, clicked, blockFirst) -> {}
            blockFirst && clicked?.opensOnClick == true && !headOnNoteBlock(use, clicked) ->
                log.debug("{} clicked {} at {}, which Kore can't open yet", player?.displayName, clicked.name, use.position)
            else -> onPlace(use)
        }
    }

    /**
     * What the player holds, used on the block they clicked, [clicked], if
     * [blockFirst]: picking ripe berries, into an item frame, flower pot,
     * jukebox, composter or cauldron, or on a note block, which a click
     * tunes, but for a head put on its top. Whether or not, bone meal grows
     * what it grows, as [fertilize] has it, an eye of ender goes into an end
     * portal frame, flint and steel or a fire charge starts a fire, as
     * [ignite] has it, and a tool or honeycomb works on the block, as
     * [useToolOn] has it. Everyone with the chunk sees and hears it, and a
     * disc going into a jukebox says what's playing. Outside creative, what's
     * used up goes from the player's hand, or swaps for what's left of it,
     * and a tool wears. Returns whether it did anything, if the player can
     * build and reach the block.
     */
    private fun useItemOn(use: ItemUse, clicked: BlockState, blockFirst: Boolean): Boolean {
        val face = Face.of(use.face) ?: return false
        if (use.hotbarSlot in 0 until HOTBAR_SIZE) heldSlot = use.hotbarSlot
        val held = inventory.items[heldSlot]?.stack?.takeIf { use.hotbarSlot == heldSlot && it.item.runtimeId == use.heldItem }
        if ((use.heldItem != 0 && held == null) || !canBuild() || !inReach(use.position)) return false
        val pos = use.position
        val headOnTop = headOnNoteBlock(use, clicked)
        val boneMeal = held?.item?.name == BONE_MEAL
        val used = when {
            // Bone meal on a bush with room to grow grows it, rather than picking what's on it.
            blockFirst && clicked.name == SWEET_BERRY_BUSH && !(boneMeal && clicked.number(GROWTH) < MAX_BERRY_GROWTH) -> world.pick(pos, clicked, game.items)
            blockFirst && clicked.hasGlowBerries -> world.pick(pos, clicked, game.items)
            blockFirst && clicked.isItemFrame -> world.useFrame(pos, clicked, held)
            blockFirst && clicked.isFlowerPot -> world.usePot(pos, clicked, held, held?.let { it.block ?: world.blocks.blockPlacedBy(it.item.name) }, game.items)
            blockFirst && clicked.isNoteBlock && !headOnTop -> ItemUsedOn(emptyList(), notes = listOfNotNull(world.tuneNote(pos)))
            blockFirst && clicked.isJukebox -> world.useJukebox(pos, held, game.items)
            blockFirst && clicked.isComposter -> world.useComposter(pos, clicked, held, game.items)
            blockFirst && clicked.isCauldron -> world.useCauldron(pos, clicked, held, game.items)
            boneMeal -> world.fertilize(pos, clicked, game.items) ?: if (face == Face.UP) world.fertilizeSeabed(pos) else null
            held?.item?.name == ENDER_EYE -> world.fillPortalFrame(pos, clicked)
            held?.item?.name == FLINT_AND_STEEL || held?.item?.name == FIRE_CHARGE -> world.ignite(pos, face, held)
            held != null -> world.useToolOn(pos, face, held, facing(yaw), game.items)
            else -> null
        } ?: return false
        showUsed(used, pos.side(face))
        if (clicked.isJukebox && used.held == HeldAfter.USED_UP) {
            world.nowPlaying(pos)?.let { showAround(listOf(it)) }
        }
        // Nor does the client swing the player's arm for a use it left to Kore, so Kore swings it, for them and everyone around.
        if (!use.predictedToWork) {
            send(Animate(Animate.SWING_ARM, entityId, swingSource = INTERACT_SWING))
            viewers().forEach { it.showSwing(entityId, INTERACT_SWING) }
        }
        log.debug("{} used {} on {} at {}", player?.displayName, held?.item?.name ?: "their hand", clicked.name, pos)
        return true
    }

    /**
     * Shows everyone with the chunk what using an item on a block did, [used],
     * and settles the player's hand: what changed, like leather dyed, is
     * swapped for its new self, and outside creative, what was used up goes,
     * or swaps for what it handed back, and a tool wears, which the client
     * hears of as the use is answered. What came out of the block drops at
     * [out], unless it says where, and anything else the player was handed
     * goes among their items.
     */
    private fun showUsed(used: ItemUsedOn, out: BlockPos) {
        showAround(world.packetsFor(Changes(emptyMap(), used.changed, listOfNotNull(used.sound), events = used.events, notes = used.notes, songs = used.songs)))
        when {
            used.held == HeldAfter.CHANGED -> used.given?.let { swapHeld(it, used.count) }
            creative -> {}
            used.held == HeldAfter.USED_UP -> inventory.remove(heldSlot, used.count)
            used.held == HeldAfter.WORN -> wearHeld(1)
            used.held == HeldAfter.SWAPPED -> used.given?.let { swapHeld(it, used.count) }
        }
        if (used.dropped.isNotEmpty()) world.dropFromBlock(used.dropAt ?: out, used.dropped)
        if (used.held == HeldAfter.KEPT) used.given?.let(::give)
    }

    /**
     * Swaps [count] of what the player holds for [swapped], as a bucket
     * emptied swaps for an empty one: into the held slot, if that was all
     * of it, and among their items otherwise, or thrown, if there's no
     * room. The client hears of the slots that changed.
     */
    private fun swapHeld(swapped: ItemStack, count: Int = 1) {
        val held = inventory.items[heldSlot] ?: return
        if (held.stack.count <= count) {
            inventory.items[heldSlot] = SlotStack(swapped, inventory.newStackId())
            send(slotOf(heldSlot))
            return
        }
        inventory.items[heldSlot] = held.less(count)
        val (filled, left) = inventory.add(swapped)
        send(*(filled + heldSlot).distinct().map(::slotOf).toTypedArray())
        if (left > 0) throwItem(swapped.copy(count = left))
    }

    /**
     * A click on the sign [sign]: a dye, glow ink sac, ink sac or honeycomb
     * marks the side the player sees, as [markSign] has it, and anything
     * else opens that side to write on. A waxed sign does neither, and says
     * so.
     */
    private fun useSign(use: ItemUse, sign: BlockState) {
        val pos = use.position
        if (gameMode == GameMode.SPECTATOR || !inReach(pos)) return
        val front = facesFront(pos, sign, position)
        if (use.hotbarSlot in 0 until HOTBAR_SIZE) heldSlot = use.hotbarSlot
        val held = inventory.items[heldSlot]?.stack?.takeIf { use.hotbarSlot == heldSlot && it.item.runtimeId == use.heldItem }
        val marked = if (canBuild()) world.markSign(pos, front, held) else null
        when {
            marked != null -> showUsed(marked, pos)
            !world.signWaxed(pos) && canBuild() -> send(OpenSign(pos, front))
        }
    }

    /**
     * What the player wrote on a sign, as their client sends it back: it
     * goes on the sign, if they can reach it and it isn't waxed, and
     * everyone with the chunk sees it. Otherwise, the client hears what the
     * sign says.
     */
    private fun onEditedBlockData(packet: EditedBlockData) {
        val pos = packet.position
        val written = world.stateAt(pos)?.isSign == true && canBuild() && inReach(pos) && world.writeSign(pos, packet.data)
        val now = world.blockEntityAt(pos) ?: return
        if (written) {
            showAround(pos, BlockActorData(pos, now))
            log.debug("{} wrote on the sign at {}", player?.displayName, pos)
        } else {
            log.debug("Refused {} writing on {} at {}", player?.displayName, world.stateAt(pos)?.name, pos)
            send(BlockActorData(pos, now))
        }
    }

    /** When the player last knocked an item out of a frame, and which, to tell the break the client reports with it from another. */
    private var knockedOut: Pair<BlockPos, Long>? = null

    /**
     * Knocks the item out of the frame at [pos], as hitting it does, if the
     * player can reach it: it drops, outside creative, and everyone with the
     * chunk sees the frame empty and hears it. Returns whether there was one.
     */
    private fun knockOutOfFrame(pos: BlockPos): Boolean {
        if (!canBuild() || !inReach(pos)) return false
        val item = world.takeFromFrame(pos, game.items) ?: return false
        knockedOut = pos to System.currentTimeMillis()
        showAround(world.packetsFor(Changes(emptyMap(), listOf(pos), listOf(BlockSound(pos, "block.itemframe.remove_item", -1)))))
        if (!creative) world.dropFromBlock(pos, listOf(item))
        log.debug("{} knocked {} out of the frame at {}", player?.displayName, item.item.name, pos)
        return true
    }

    /**
     * A click on a lectern. A lectern with a book opens it to read, which
     * the client then reads from the lectern's block entity, without a
     * window of its own. With a book and quill or a written book in hand,
     * and none on the lectern, the book goes on it, open at the first page,
     * out of the player's hand outside creative, and everyone with the
     * chunk sees it and hears it. Returns whether the lectern took the
     * click: with neither, the player places what they hold against it.
     */
    private fun useLectern(use: ItemUse): Boolean {
        val pos = use.position
        if (world.lecternBook(pos) != null) {
            if (gameMode != GameMode.SPECTATOR && inReach(pos)) {
                reading = true
                send(ContainerOpen(NO_WINDOW, ContainerType.LECTERN, pos, entityId = -1))
            }
            return true
        }
        if (use.hotbarSlot in 0 until HOTBAR_SIZE) heldSlot = use.hotbarSlot
        val held = inventory.items[heldSlot]?.takeIf { use.hotbarSlot == heldSlot && it.stack.item.runtimeId == use.heldItem }
        if (held?.stack?.goesOnLectern != true) return false
        if (gameMode == GameMode.SPECTATOR || !inReach(pos) || !world.putOnLectern(pos, held.stack)) return true
        if (!creative) inventory.items[heldSlot] = held.less(1)
        send(slotOf(heldSlot))
        showAround(pos, BlockActorData(pos, world.blockEntityAt(pos)!!), LevelSoundEvent(BOOK_PUT, pos.centre, -1))
        log.debug("{} put {} on the lectern at {}", player?.displayName, held.stack.item.name, pos)
        return true
    }

    /**
     * A player reading the book on a lectern turning a page, which
     * everyone else with the chunk sees, and which pulses redstone from the
     * lectern.
     */
    private fun onLecternUpdate(packet: LecternUpdate) {
        val pos = packet.position
        if (gameMode == GameMode.SPECTATOR || !inReach(pos) || !world.turnPage(pos, packet.page, packet.totalPages)) return
        showAround(pos, UpdateBlock(pos, world.blockAt(pos)), BlockActorData(pos, world.blockEntityAt(pos)!!))
    }

    /**
     * A player writing in the book and quill in one of their slots, or
     * signing it, which makes it a written book in their name. The client
     * shows what it wrote already, and hears of the book only once it's
     * signed. What Bedrock's limits don't allow changes nothing.
     */
    private fun onBookEdit(packet: BookEdit) {
        val there = inventory.items[packet.slot]?.takeIf { it.stack.item.name == WRITABLE_BOOK } ?: return
        val written = game.items.of(WRITTEN_BOOK) ?: return
        val edited = writeInBook(there.stack, packet.action, written, player?.xuid.orEmpty()) ?: return
        inventory.items[packet.slot] = there.copy(stack = edited)
        if (packet.action is BookAction.Sign) {
            send(slotOf(packet.slot))
            log.debug("{} signed a book, \"{}\"", player?.displayName, packet.action.title)
        }
    }

    /**
     * A player with a crafter open switching one of its empty slots off,
     * or back on, which everyone with the chunk sees in the crafter's data.
     */
    private fun onToggleCrafterSlot(packet: ToggleCrafterSlot) {
        val pos = packet.position
        if (window?.pos != pos || !world.toggleCrafterSlot(pos, packet.slot, packet.disabled)) return
        showAround(pos, BlockActorData(pos, world.blockEntityAt(pos)!!))
    }

    /**
     * Uses the block at [pos], if the player can reach it and isn't a
     * spectator. Everyone with the chunk sees what changes and hears it. A
     * pressed button pops back out on its own, later. A use Kore refuses
     * puts back what the client predicted.
     */
    private fun useBlock(pos: BlockPos) {
        val used = if (gameMode != GameMode.SPECTATOR && inReach(pos)) world.use(pos, facing(yaw)) else null
        if (used == null) {
            log.debug("Refused {} using {} at {}", player?.displayName, world.stateAt(pos)?.name, pos)
            send(UpdateBlock(pos, world.blockAt(pos)))
            return
        }
        if (used.changed.isNotEmpty()) {
            val updates = used.changed.map { UpdateBlock(it, world.blockAt(it)) }
            val sound = used.sound?.let { LevelSoundEvent(it, pos.centre, used.soundData) }
            showAround(pos, *(updates + listOfNotNull(sound)).toTypedArray())
        }
        log.debug("{} used {} at {}", player?.displayName, world.stateAt(pos)?.name, pos)
    }

    /**
     * A sound the player made, which the client leaves to the server to
     * play, like the block they're mining being hit: everyone with the
     * chunk hears it, the player too. One from beyond the player's reach
     * goes nowhere.
     */
    private fun onSound(sound: LevelSoundEvent) {
        if (state != State.IN_GAME || sound.sound.isBlank() || !sound.position.isPlausible() || !sound.position.within(SOUND_REACH, position)) {
            log.debug("Ignored {} from {}", sound, name)
            return
        }
        showAround(sound.position.toBlockPos(), sound)
    }

    /**
     * Places the block the player holds against the face they clicked, as
     * the client predicted, if they can: their game mode builds, the spot
     * is in reach, and the client's item is the block item Kore has in
     * that hotbar slot. Everyone with the chunk sees the block appear and
     * hears it go down, along with any blocks around it that join it, like
     * fences. Outside creative, placing uses up one of the stack.
     *
     * Kore's rules for where blocks go and which way they turn don't cover
     * all of the client's. So the client also hears what's really at both
     * spots it may have changed, the block clicked and the space beyond the
     * face, which puts right any guess that differed.
     */
    private fun onPlace(use: ItemUse) {
        val face = Face.of(use.face) ?: return
        if (use.hotbarSlot in 0 until HOTBAR_SIZE) heldSlot = use.hotbarSlot
        // The client's item must be the one Kore has in the slot.
        val stack = inventory.items[heldSlot]?.stack?.takeIf { use.hotbarSlot == heldSlot && it.item.runtimeId == use.heldItem }
        if (stack?.item?.name in BUCKETS) return useBucket(use, face, stack!!)
        val block = stack?.let { it.block ?: world.blocks.blockPlacedBy(it.item.name) }
        val placer = Placer(facing(yaw), yaw, position, sneaking)
        val placed = if (block != null && canBuild() && inReach(use.position.side(face))) {
            world.place(block, use.position, face, use.clickPosition.y, placer, stack)
        } else {
            emptyList()
        }

        if (placed.isEmpty()) {
            log.debug("Refused {} placing {} against {} {}", player?.displayName, block?.name ?: "item ${use.heldItem}", use.position, face)
        } else {
            val first = placed.first()
            val sound = first to LevelSoundEvent(LevelSoundEvent.PLACE, first.centre, world.blockAt(first))
            // A block picked with its data puts it back: a chest its items, a sign its writing.
            val restored = world.restoreBlockData(first, stack!!, game.items)
            val changes = world.settle(placed)
            showAround(placed.flatMap(world::withData) + sound + world.packetsFor(changes))
            if (world.stateAt(first)?.storage == Storage.SHULKER_BOX) fillShulkerBox(first, stack)
            // A sign just put up opens to write on, on its front, which faces the player, unless it came written.
            if (world.stateAt(first)?.isSign == true && !restored) send(OpenSign(first, front = true))
            if (!creative) inventory.remove(heldSlot)
            log.debug("{} placed {} at {}", player?.displayName, world.stateAt(first)?.let { it.name + it.properties }, first)
        }
        val corrections = (listOf(use.position, use.position.side(face)) - placed.toSet()).map { UpdateBlock(it, world.blockAt(it)) }
        if (corrections.isNotEmpty()) send(*corrections.toTypedArray())
        // Outside creative, the client used up the item it placed, which it gets back if nothing was placed.
        if (placed.isEmpty() && !creative) send(slotOf(heldSlot))
    }

    private val creative get() = gameMode == GameMode.CREATIVE

    /**
     * The player using a bucket on the block they clicked, if they can
     * build and reach it. An empty one takes in a water or lava source, or
     * the water inside a block, and a full one empties as a source, into
     * the block if it holds water, or the space beyond the face, as
     * [emptyBucket] has it. Everyone with the chunk sees and hears it. In
     * creative, the bucket stays as it was; otherwise it's filled or
     * emptied, one of a stack of empty ones going among the player's items.
     * What the client guessed and Kore refused is put right.
     */
    private fun useBucket(use: ItemUse, face: Face, held: ItemStack) {
        val broken = linkedMapOf<BlockPos, BrokenBlock>()
        val filling = held.item.name == BUCKET
        val liquid = BUCKET_LIQUIDS[held.item.name]
        val reach = if (filling) use.position else use.position.side(face)
        val at: BlockPos?
        var taken: Liquid? = null
        if (!canBuild() || !inReach(reach)) {
            at = null
        } else if (filling) {
            val filled = world.fillBucket(use.position, face)
            taken = filled?.first
            at = filled?.second
        } else {
            at = world.emptyBucket(liquid!!, use.position, face, broken)
        }
        if (at == null) {
            log.debug("Refused {} using {} on {} {}", player?.displayName, held.item.name, use.position, face)
            send(*listOf(use.position, use.position.side(face)).flatMap { listOf(UpdateBlock(it, world.blockAt(it)), UpdateBlock(it, world.liquidAt(it)?.networkId ?: AIR_NETWORK_ID, layer = 1)) }.toTypedArray())
            send(slotOf(heldSlot))
            return
        }
        val sound = if (filling) "bucket.fill.${taken!!.name.lowercase()}" else "bucket.empty.${liquid!!.name.lowercase()}"
        val changes = world.settle(listOf(at))
        showAround(world.packetsFor(Changes(broken + changes.broken, (listOf(at) + changes.reshaped).distinct())) + (at to LevelSoundEvent(sound, at.centre, -1)))
        broken.forEach { (pos, block) -> game.dropBroken(pos, block, harvested = !creative) }
        if (!creative) {
            val swapped = if (filling) game.items.of(LIQUID_BUCKETS.getValue(taken!!)) else game.items.of(BUCKET)
            swapped?.let(::swapHeld)
        }
        log.debug("{} used {} at {}", player?.displayName, held.item.name, at)
    }

    /** Puts the items the shulker box item [stack] held in the shulker box placed from it at [pos]. */
    private fun fillShulkerBox(pos: BlockPos, stack: ItemStack) {
        val container = world.containerAt(pos)
        for (held in stack.nbt.getList(ItemStacks.ITEMS, NbtType.COMPOUND, emptyList())) {
            val (slot, item) = game.items.fromNbt(held) ?: continue
            if (slot in 0 until container.size) container[slot] = SlotStack(item, inventory.newStackId())
        }
    }

    /** Where the player's feet are, if they're in the game and standing on something. */
    internal val standingAt: Vec3? get() = if (isInGame && onGround && !dead) Vec3(position.x, position.y - PLAYER_EYE_HEIGHT, position.z) else null

    /**
     * Breaks the block at [pos], as the client predicted, if the player
     * can: their game mode breaks blocks and it's in reach. A [creativeOnly]
     * break, like creative's instant one, needs the player to be in
     * creative, and not to hold a weapon that only hits, like a sword.
     * Outside creative, the block mustn't be one only creative players
     * break, and a break reported at the client's [tick] mustn't come too
     * soon after the player started on it.
     *
     * Everyone with the chunk sees the block go, with its particles and
     * sound: the player too, since the client leaves those to the server.
     * So do any torches the block held up, and blocks around it that joined
     * it, like fences, change. A break Kore refuses puts the block back.
     */
    private fun onBreak(pos: BlockPos, creativeOnly: Boolean, tick: Long? = null) {
        val state = world.stateAt(pos)
        // Air is what a break leaves, so it's a break the client has already reported another way. Nor can anyone break
        // water or lava, which a block holding water leaves when it breaks.
        if (state?.name == AIR || state?.liquid != null) return
        // Hitting a frame with an item in it knocks the item out, and leaves the frame, which the client may report as the frame breaking.
        if (state?.isItemFrame == true && (knockOutOfFrame(pos) || knockedOut?.let { (at, time) -> at == pos && System.currentTimeMillis() - time < KNOCK_OUT_MILLIS } == true)) {
            send(*world.withData(pos).map { it.second }.toTypedArray())
            return
        }

        val ticks = state?.let { ticksToBreak(it.name) }
        val unbreakable = state != null && game.mining[state.name]?.unbreakable == true
        // Checked before the cracks stop, which forgets when the player started.
        val early = !creative && tooSoon(pos, ticks, tick)
        if (!creative) stopBreaking()
        val weapon = creative && !breaksInCreative(heldStack?.item?.name)
        if (state == null || !canBuild() || !inReach(pos) || weapon || (!creative && (creativeOnly || unbreakable || early))) {
            log.debug("Refused {} breaking {} at {}", player?.displayName, state?.name, pos)
            send(UpdateBlock(pos, world.blockAt(pos)))
            return
        }
        val changes = world.breakBlock(pos)
        showAround(world.packetsFor(changes))
        val held = heldItem()
        changes.broken.forEach { (at, broken) ->
            // What the player broke drops only if what they hold harvests it, as the tool has it drop. What fell with it always drops.
            val byHand = at == pos || broken.state.otherHalf(at) == pos
            val harvested = !creative && (!byHand || game.mining[broken.state.name]?.let { harvests(it, held) } != false)
            game.dropBroken(at, broken, harvested, tool = heldStack.takeIf { byHand })
            if (byHand && harvested && world.rules.doTileDrops) game.drops.experienceOf(broken.state, heldStack).takeIf { it > 0 }?.let { world.dropExperience(at.centre, it) }
        }
        viewers().forEach { it.containerBroke(changes.broken.keys) }
        log.debug("{} broke {} at {}", player?.displayName, state.name, pos)
        if (creative) return
        wearHeld(game.mining[state.name]?.let { wearFromBreaking(it, held) } ?: 0)
        exhaust(BREAK_EXHAUSTION)
    }

    /**
     * Wears the item the player holds [uses] down, as breaking a block with
     * a tool does. One that wears out breaks, which everyone around hears.
     * The client hears what the slot holds once the use that wore it is
     * answered, or at once for wear it doesn't foresee, [unforeseen], like a
     * hit's.
     */
    private fun wearHeld(uses: Int, unforeseen: Boolean = false) {
        val held = inventory.items[heldSlot] ?: return
        val worn = held.stack.worn(uses)
        if (worn == held.stack) return
        if (unforeseen) send(replaceSlot(inventory.items, heldSlot, INVENTORY_WINDOW, worn)) else inventory.items[heldSlot] = worn?.let { held.copy(stack = it) }
        if (worn == null) {
            showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.BREAK, position, -1))
            log.debug("{}'s {} broke", player?.displayName, held.stack.item.name)
        }
    }


    /**
     * Throws [stack] the way the player looks, from just below their eyes,
     * as dropping an item from the inventory does.
     */
    private fun throwItem(stack: ItemStack) {
        val yaw = Math.toRadians(yaw.toDouble())
        val pitch = Math.toRadians(pitch.toDouble())
        val velocity = Vec3(
            (-sin(yaw) * cos(pitch) * DROP_SPEED).toFloat(),
            (-sin(pitch) * DROP_SPEED).toFloat() + DROP_LIFT,
            (cos(yaw) * cos(pitch) * DROP_SPEED).toFloat(),
        )
        world.spawnItem(stack, Vec3(position.x, position.y - 0.3f, position.z), velocity, THROWN_PICKUP_DELAY)
        log.debug("{} dropped {} {}", player?.displayName, stack.count, stack.item.name)
    }

    /**
     * Picks up every item in the player's reach that's ready to be, as much
     * of each as fits. The player sees their slots fill, and everyone with
     * the chunk sees the item fly to them. Run once a tick.
     */
    fun collectItems() {
        if (state != State.IN_GAME || gameMode == GameMode.SPECTATOR || dead) return
        for (item in world.items.toList()) {
            if (item.pickupDelay > 0 || !inPickupReach(item)) continue
            val (slots, left) = inventory.add(item.stack)
            if (slots.isEmpty()) continue
            send(*slots.map(::slotOf).toTypedArray())
            showAround(item.position.toBlockPos(), TakeItemActor(item.id, entityId))
            log.debug("{} picked up {} {}", player?.displayName, item.stack.count - left, item.stack.item.name)
            if (left == 0) world.removeItem(item.id) else item.stack = item.stack.copy(count = left)
        }
    }

    /**
     * Picks up an experience orb that touches the player, if they're ready
     * to: one at a time, a moment apart. They hear its chime, as does
     * everyone with the chunk. Run once a tick.
     */
    fun collectOrbs() {
        if (state != State.IN_GAME || gameMode == GameMode.SPECTATOR || dead) return
        if (orbPickupDelay > 0) {
            orbPickupDelay--
            return
        }
        val orb = world.orbs.firstOrNull(::touches) ?: return
        world.removeOrb(orb.id)
        orbPickupDelay = ORB_PICKUP_DELAY
        showAround(position.toBlockPos(), LevelEvent(LevelEvent.SOUND_EXPERIENCE_ORB, position, 0))
        mend(orb.value).takeIf { it > 0 }?.let(::addExperience)
    }

    /**
     * Mends one of the worn items with Mending that the player holds or
     * wears, picked at random, with experience [points]: two uses for each.
     * Returns the points left over, which go to the player.
     */
    private fun mend(points: Int): Int {
        val slots = listOf(Triple(inventory.items, heldSlot, INVENTORY_WINDOW), Triple(inventory.offhand, 0, OFFHAND_WINDOW)) +
            (0 until inventory.armour.size).map { Triple(inventory.armour, it, ARMOUR_WINDOW) }
        val (container, slot, window) = slots.filter { (container, slot) ->
            container[slot]?.stack?.let { it.damage > 0 && it.levelOf(MENDING) > 0 } == true
        }.randomOrNull() ?: return points
        val there = container[slot]!!
        val (mended, used) = there.stack.mendedBy(points)
        send(replaceSlot(container, slot, window, mended))
        log.debug("{}'s {} was mended to {} damage", player?.displayName, mended.item.name, mended.damage)
        return points - used
    }

    /** Whether [orb]'s box meets the player's. */
    private fun touches(orb: ExperienceOrb): Boolean {
        val feet = position.y - PLAYER_EYE_HEIGHT
        val across = PLAYER_WIDTH / 2 + ITEM_HALF_WIDTH
        return abs(orb.position.x - position.x) < across && abs(orb.position.z - position.z) < across &&
            orb.position.y < feet + playerHeight() && orb.position.y + ITEM_HEIGHT > feet
    }

    /** Where experience orbs fly to this player from, their feet, if they're in the game and pick orbs up. */
    internal val orbTarget: Vec3? get() = if (isInGame && gameMode != GameMode.SPECTATOR && !dead) feet else null

    /**
     * Shows the client the experience orbs in the chunks it has: those it
     * hasn't seen appear, those that moved move, and those that are gone
     * go. Run once a tick.
     */
    fun syncOrbs() {
        if (state != State.IN_GAME) return
        val inView = world.orbs.filter { it.position.toBlockPos().chunk in sentChunks }
        val ids = inView.mapTo(HashSet()) { it.id }
        val packets = shownOrbs.filter { it !in ids }.map(::RemoveActor).toMutableList<ServerPacket>()
        for (orb in inView) {
            val shown = Vec3(orb.position.x, orb.position.y + ITEM_NETWORK_OFFSET, orb.position.z)
            if (orb.id !in shownOrbs) packets += AddExperienceOrb(orb.id, shown, orb.velocity, orb.value)
            else if (orb.moved) packets += MoveActorAbsolute(orb.id, shown, orb.onGround, teleport = orb.snapped)
        }
        shownOrbs.retainAll(ids)
        shownOrbs += ids
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /**
     * Shows the client the blocks falling in the chunks it has: those it
     * hasn't seen appear, and those that moved move. Run once a tick, before
     * the blocks that started falling go from where they were, so the
     * client never has neither.
     */
    fun showFallingBlocks() {
        if (state != State.IN_GAME) return
        val packets = mutableListOf<ServerPacket>()
        for (falling in world.fallingBlocks.filter { it.position.toBlockPos().chunk in sentChunks }) {
            val shown = Vec3(falling.position.x, falling.position.y + FALLING_BLOCK_NETWORK_OFFSET, falling.position.z)
            if (shownFallingBlocks.add(falling.id)) packets += AddFallingBlock(falling.id, falling.state.networkId, shown, falling.velocity, FALLING_BLOCK_SIZE)
            else if (falling.moved) packets += MoveActorAbsolute(falling.id, shown, onGround = false)
        }
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /**
     * Takes away the falling blocks the client has that landed, or are out
     * of view. Run once a tick, after the blocks that landed show where they
     * landed, so the client never has neither.
     */
    fun hideFallingBlocks() {
        if (state != State.IN_GAME) return
        val inView = world.fallingBlocks.filter { it.position.toBlockPos().chunk in sentChunks }.mapTo(HashSet()) { it.id }
        val gone = shownFallingBlocks.filter { it !in inView }
        if (gone.isEmpty()) return
        shownFallingBlocks.removeAll(gone.toSet())
        send(*gone.map(::RemoveActor).toTypedArray())
    }

    /** The lightning bolts the client has been shown, by their entity IDs. */
    private val shownBolts = HashSet<Long>()

    /** What the client hears of the weather as it is: rain and thunder starting, at full strength, or stopping. */
    private fun weatherNow(): Array<ServerPacket> {
        val weather = world.weather
        val rain = if (weather.raining) LevelEvent(LevelEvent.START_RAIN, ORIGIN, FULL_WEATHER) else LevelEvent(LevelEvent.STOP_RAIN, ORIGIN, 0)
        val thunder = if (weather.thundering) LevelEvent(LevelEvent.START_THUNDER, ORIGIN, FULL_WEATHER) else LevelEvent(LevelEvent.STOP_THUNDER, ORIGIN, 0)
        return arrayOf(rain, thunder)
    }

    /** Shows the client the weather as it is now, once it's changed. */
    fun showWeather() {
        if (state == State.IN_GAME) send(*weatherNow())
    }

    /** Tells the client the time, which it keeps up itself in between, while the daylight cycle's on. */
    fun showTime() {
        if (state == State.IN_GAME) send(SetTime(world.time))
    }

    /** Tells the client of the game rules that changed, [rules]. */
    fun showRules(rules: Map<String, Any>) {
        if (state == State.IN_GAME) send(GameRulesChanged(rules))
    }

    /**
     * Shows the client the lightning that's struck in its chunks since, with
     * its thunder, and takes away the bolts that are gone. Run once a tick.
     */
    fun showLightning() {
        if (state != State.IN_GAME) return
        val packets = mutableListOf<ServerPacket>()
        val inView = world.lightning.filter { it.position.toBlockPos().chunk in sentChunks }
        for (bolt in inView) {
            if (!shownBolts.add(bolt.id)) continue
            packets += AddLightning(bolt.id, bolt.position)
            packets += LevelSoundEvent(THUNDER, bolt.position, -1, entityType = LIGHTNING_BOLT)
            packets += LevelSoundEvent(EXPLODE, bolt.position, -1)
        }
        val gone = shownBolts - inView.mapTo(HashSet()) { it.id }
        shownBolts -= gone
        packets += gone.map(::RemoveActor)
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /** The player's experience level, which anvils and enchanting cost. */
    val experienceLevel get() = experience.level

    /**
     * Gives the player [points] of experience, which fills their bar and
     * levels them up. Reaching a level that's a multiple of five sounds a
     * fanfare.
     */
    fun addExperience(points: Int) {
        val before = experience.level
        experience.add(points)
        sendExperience()
        val level = experience.level
        if (level > before && level / 5 > before / 5) {
            showAround(position.toBlockPos(), LevelSoundEvent(LEVEL_UP, position, 0x10000000 * (minOf(level, 30) / 5)))
        }
    }

    /** Gives the player [levels], or takes them away for fewer than none, though never below level 0. */
    fun addLevels(levels: Int) {
        experience.addLevels(levels)
        sendExperience()
    }

    private fun sendExperience() {
        if (isInGame) send(UpdateAttributes(entityId, experienceAttributes()))
    }

    private fun experienceAttributes() = listOf(
        Attribute("minecraft:player.level", 0f, Int.MAX_VALUE.toFloat(), experience.level.toFloat(), default = 0f),
        Attribute("minecraft:player.experience", 0f, 1f, experience.progress, default = 0f),
    )

    /** Whether [item] is in the player's reach: its box, grown by [PICKUP_REACH] across and [PICKUP_REACH_UP] up and down, meets theirs. */
    private fun inPickupReach(item: ItemEntity): Boolean {
        val feet = position.y - PLAYER_EYE_HEIGHT
        val across = PLAYER_WIDTH / 2 + ITEM_HALF_WIDTH + PICKUP_REACH
        return abs(item.position.x - position.x) < across && abs(item.position.z - position.z) < across &&
            item.position.y - PICKUP_REACH_UP < feet + playerHeight() && item.position.y + ITEM_HEIGHT + PICKUP_REACH_UP > feet
    }

    /**
     * Shows the client the items lying in the chunks it has: those it hasn't
     * seen appear, those that moved move, those whose stacks changed size
     * change, and those that are gone, picked up, merged or out of view, go.
     * Run once a tick.
     */
    fun syncItems() {
        if (state != State.IN_GAME) return
        val inView = world.items.filter { it.position.toBlockPos().chunk in sentChunks }
        val ids = inView.mapTo(HashSet()) { it.id }
        val packets = shownItems.filter { it !in ids }.map(::RemoveActor).toMutableList<ServerPacket>()
        for (item in inView) {
            if (item.id !in shownItems) {
                packets += AddItemActor(item.id, item.stack, item.position, item.velocity)
                continue
            }
            if (item.moved) packets += MoveActorAbsolute(item.id, Vec3(item.position.x, item.position.y + ITEM_NETWORK_OFFSET, item.position.z), item.onGround, teleport = item.snapped)
            if (item.restacked) packets += ActorEvent(item.id, ActorEvent.UPDATE_STACK_SIZE, item.stack.count)
        }
        shownItems.retainAll(ids)
        shownItems += ids
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /** The projectiles the client has been shown. */
    private val shownProjectiles = HashSet<Long>()

    /**
     * Shows the client what's been thrown and is flying in the chunks it
     * has: those it hasn't seen appear, the rest move, and those gone go.
     * Run once a tick.
     */
    fun syncProjectiles() {
        if (state != State.IN_GAME) return
        val inView = world.projectiles.filter { it.position.toBlockPos().chunk in sentChunks }
        val ids = inView.mapTo(HashSet()) { it.id }
        val packets = shownProjectiles.filter { it !in ids }.map(::RemoveActor).toMutableList<ServerPacket>()
        for (projectile in inView) {
            if (projectile.id !in shownProjectiles) {
                // An egg's climate variant is the egg entity's only property.
                val properties = listOfNotNull(projectile.kind.climate?.let { 0 to it })
                packets += AddProjectile(
                    projectile.id, projectile.kind.entity, projectile.thrower, projectile.position, projectile.velocity, projectile.pitch, projectile.yaw,
                    flagsOf(projectile), properties, projectile.arrow?.potion,
                )
                continue
            }
            if (!projectile.moved) continue
            val landed = projectile.stuckIn != null
            packets += MoveActorAbsolute(projectile.id, projectile.position, onGround = false, teleport = landed, pitch = projectile.pitch, yaw = projectile.yaw)
            // An arrow that's landed trails no sparks, and a trident setting off back shows it.
            if ((landed && projectile.arrow?.critical == true) || projectile.setOffBack) {
                packets += SetActorData(projectile.id, listOf(flagsItem(setOf(ActorFlag.HAS_GRAVITY, ActorFlag.CAN_CLIMB) + flagsOf(projectile))))
            }
        }
        shownProjectiles.retainAll(ids)
        shownProjectiles += ids
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /** The flags of [projectile]'s own: an arrow's sparks, while it flies, and its fire, and a trident's shine, and flying back. */
    private fun flagsOf(projectile: Projectile) = setOfNotNull(
        ActorFlag.CRITICAL.takeIf { projectile.arrow?.critical == true && projectile.stuckIn == null },
        ActorFlag.ON_FIRE.takeIf { projectile.arrow?.burns == true },
        ActorFlag.ENCHANTED.takeIf { projectile.item?.enchantments?.isNotEmpty() == true },
        ActorFlag.RETURN_TRIDENT.takeIf { projectile.returning },
    )

    /** Where the player can be hit, as tall as they are now. */
    internal val hitbox: Box get() = Box.of(feet, PLAYER_WIDTH, playerHeight())

    /**
     * The player hit by [projectile], thrown by [thrower]: unless their
     * shield's up and covers it, or it's another player's and the pvp rule
     * says no, it hurts them, though a snowball, egg or ender pearl by
     * nothing, and knocks them back the way it flew, if it's the first hurt
     * in a while.
     */
    internal fun hitBy(projectile: Projectile, thrower: Session?) {
        if (thrower != null && thrower !== this && !world.rules.pvp) return
        if (blocks(projectile.position, weapon = null, damage = 0f)) return
        val fresh = !vitals.inGrace
        val flew = projectile.velocity
        if (hurt(0f, DamageCause.PROJECTILE, by = thrower) && fresh) knockBack(Vec3(feet.x - flew.x, feet.y, feet.z - flew.z), extra = 0f)
    }

    /**
     * The player shot by [arrow], by [shooter]: unless it's another player's
     * and the pvp rule says no, or their shield covers it, it hurts them by
     * how fast it went, and if it's their first hurt in a while, knocks them
     * back the way it flew, the further for Punch. One that burns sets them
     * burning, and a tipped one gives them a share of its potion's effects.
     * The Thorns on what they wear may prick whoever shot it. Returns
     * whether it hurt them: one that didn't bounces off.
     */
    internal fun shotBy(arrow: Projectile, shooter: Session?): Boolean {
        val shot = arrow.arrow ?: return false
        if (shooter != null && shooter !== this && !world.rules.pvp) return false
        val flew = arrow.velocity
        val damage = arrowDamage(shot.damage, sqrt(flew.x * flew.x + flew.y * flew.y + flew.z * flew.z), shot.critical)
        if (shot.piercing == 0 && blocks(arrow.position, weapon = null, damage)) return false
        val fresh = !vitals.inGrace
        if (!hurt(damage, DamageCause.ARROW, by = shooter)) return false
        if (fresh) knockBack(Vec3(feet.x - flew.x, feet.y, feet.z - flew.z), extra = shot.punch * PUNCH_PER_LEVEL)
        if (shot.burns) setOnFire(FLAME_TICKS)
        shot.potion?.let { potion -> potionEffects(potion).forEach { giveEffect(it.level, maxOf(1, it.ticks / TIPPED_SHARE), ambient = false) } }
        if (shooter != null && shooter !== this && !dead) prick(shooter)
        return true
    }

    /**
     * The player hit by [trident], thrown by [thrower]: unless it's another
     * player's and the pvp rule says no, or their shield covers it, it hurts
     * them, the more for Impaling if they're in water or rain, and if it's
     * their first hurt in a while, knocks them back the way it flew. The
     * Thorns on what they wear may prick whoever threw it. Returns whether
     * it hurt them: one that did falls away, and one that didn't bounces off.
     */
    internal fun stabbedBy(trident: Projectile, thrower: Session?): Boolean {
        if (thrower != null && thrower !== this && !world.rules.pvp) return false
        val impaling = if (inWaterOrRain) impalingDamage(trident.item?.levelOf(IMPALING) ?: 0) else 0f
        val damage = TRIDENT_DAMAGE + impaling
        if (blocks(trident.position, weapon = null, damage)) return false
        val fresh = !vitals.inGrace
        if (!hurt(damage, DamageCause.TRIDENT, by = thrower)) return false
        val flew = trident.velocity
        if (fresh) knockBack(Vec3(feet.x - flew.x, feet.y, feet.z - flew.z), extra = 0f)
        if (thrower != null && thrower !== this && !dead) prick(thrower)
        return true
    }

    /** Whether lightning would strike the player for a trident's Channeling: out in the rain, in a thunderstorm. */
    internal val channelsLightning get() = world.weather.thundering && world.rainingAt(position.toBlockPos())

    /**
     * Picks up the arrows stuck in blocks within the player's reach: one
     * that was used up shooting it goes among their items, as their arrow,
     * tipped as it was, as there's room for it, and one spared being used
     * up, by creative or Infinity, goes, giving them nothing. Those around
     * see it fly to them. Tridents are picked up too, as they were thrown,
     * by anyone, but one with Loyalty, or thrown in creative, which gives
     * nothing back, only by whoever threw it, who picks up one flying back
     * to them as it reaches them. Run once a tick.
     */
    fun collectProjectiles() {
        if (state != State.IN_GAME || gameMode == GameMode.SPECTATOR || dead) return
        val reach = hitbox.grown(ARROW_PICKUP_ACROSS, ARROW_PICKUP_UP)
        for (projectile in world.projectiles.filter(::canPickUp)) {
            if (!reach.overlaps(Box.around(projectile.position))) continue
            val stack = when {
                projectile.arrow?.returned == true -> game.items.of(ARROW, aux = projectile.arrow.potion?.plus(1) ?: 0) ?: continue
                projectile.kind == ProjectileKind.TRIDENT && !projectile.fromCreative -> projectile.item ?: continue
                else -> null
            }
            if (stack != null) {
                val (slots, left) = inventory.add(stack)
                if (left > 0) continue
                send(*slots.map(::slotOf).toTypedArray())
            }
            showAround(projectile.position.toBlockPos(), TakeItemActor(projectile.id, entityId))
            world.removeProjectile(projectile.id)
        }
    }

    /** Whether the player could pick [projectile] up, coming within reach of it: an arrow or a trident that's landed, or flying back to them. */
    private fun canPickUp(projectile: Projectile) = when (projectile.kind) {
        ProjectileKind.ARROW -> projectile.stuckIn != null && projectile.stuckFor > ARROW_PICKUP_DELAY
        ProjectileKind.TRIDENT -> {
            val theirs = projectile.thrower == entityId
            (theirs || (projectile.loyalty == 0 && !projectile.fromCreative)) && (projectile.returning || (projectile.stuckIn != null && projectile.stuckFor > ARROW_PICKUP_DELAY))
        }
        else -> false
    }

    /** Takes the player to [at], where an ender pearl they threw landed, which hurts them as a fall does, unless they've died since. */
    internal fun arriveByPearl(at: Vec3) {
        if (dead || !isInGame) return
        showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.TELEPORT, position, -1))
        teleport(at, yaw, pitch)
        hurt(ENDER_PEARL_DAMAGE, DamageCause.FALL)
    }

    /**
     * What others see of a player at a moment: where they are and which way
     * they look, whether they sneak or sprint, and what they hold.
     */
    private data class Look(
        val position: Vec3,
        val pitch: Float,
        val yaw: Float,
        val headYaw: Float,
        val onGround: Boolean,
        val sneaking: Boolean,
        val sprinting: Boolean,
        val swimming: Boolean,
        val gliding: Boolean,
        val heldSlot: Int,
        val held: ItemStack?,
        val offhand: ItemStack?,
        /** What they wear, from the head down. */
        val armour: List<ItemStack?>,
        /** The effects whose particles show around them. */
        val effects: Long,
        /** Whether Invisibility hides them, whether they burn, whether they eat or draw a bow, and whether they hold up a shield. */
        val invisible: Boolean,
        val onFire: Boolean,
        val eating: Boolean,
        val shieldUp: Boolean,
    )

    private fun look() = Look(
        position, pitch, yaw, headYaw, onGround, sneaking, sprinting, swimming, gliding, heldSlot, inventory.items[heldSlot]?.stack,
        inventory.offhand[0]?.stack, inventory.armour.contents().map { it?.stack }, effects.visible(), effects[Effect.INVISIBILITY] != null,
        vitals.fireTicks > 0, eating != null || drawing != null || loading != null, shieldRaisedAt != null,
    )

    /**
     * Shows the client the other players. Everyone in the game is on its
     * player list, this player included, which is where the client finds
     * their skins. Those in the chunks it has appear, move, sneak, sprint,
     * change what they hold and wear, and go, as they do. Run once a tick.
     */
    fun syncPlayers() {
        if (state != State.IN_GAME) return
        val players = viewers().filter { it.isInGame }
        val packets = mutableListOf<ServerPacket>()

        val ids = players.mapTo(HashSet()) { it.entityId }
        val entries = mutableListOf<PlayerListEntry>()
        listedPlayers.entries.removeIf { (id, uuid) -> (id !in ids).also { gone -> if (gone) entries += PlayerListEntry.Remove(uuid) } }
        for (other in players) {
            if (other.entityId in listedPlayers) continue
            val entry = other.listEntry()
            listedPlayers[other.entityId] = entry.uuid
            entries += entry
        }
        if (entries.isNotEmpty()) packets += PlayerList(entries)

        val inView = players.filter { it !== this && !it.bodyGone && it.position.toBlockPos().chunk in sentChunks }
        val viewIds = inView.mapTo(HashSet()) { it.entityId }
        for (gone in shownPlayers.keys.filter { it !in viewIds }) {
            shownPlayers.remove(gone)
            packets += RemoveActor(gone)
        }
        for (other in inView) {
            val now = other.look()
            val before = shownPlayers.put(other.entityId, now)
            if (before == null) {
                packets += other.addPlayer(now)
                if (now.offhand != null) packets += ShowEquipment(other.entityId, now.offhand, 0, OFFHAND_WINDOW)
                if (now.armour.any { it != null }) packets += ShowArmour(other.entityId, now.armour)
                log.debug("{} sees {}", player?.displayName, other.player?.displayName)
                continue
            }
            if (now.position != before.position || now.pitch != before.pitch || now.yaw != before.yaw || now.headYaw != before.headYaw || now.onGround != before.onGround) {
                packets += MovePlayer(other.entityId, now.position, now.pitch, now.yaw, now.headYaw, now.onGround)
            }
            if (now.sneaking != before.sneaking || now.sprinting != before.sprinting || now.swimming != before.swimming || now.gliding != before.gliding ||
                now.invisible != before.invisible || now.onFire != before.onFire || now.eating != before.eating
            ) {
                packets += SetActorData(other.entityId, listOf(flagsItem(shownFlags(now)), FloatItem(ActorDataId.HEIGHT, heightOf(now))))
            }
            if (now.held != before.held || now.heldSlot != before.heldSlot) packets += ShowEquipment(other.entityId, now.held, now.heldSlot)
            if (now.offhand != before.offhand) packets += ShowEquipment(other.entityId, now.offhand, 0, OFFHAND_WINDOW)
            if (now.armour != before.armour) packets += ShowArmour(other.entityId, now.armour)
            if (now.effects != before.effects) packets += SetActorData(other.entityId, listOf(LongItem(ActorDataId.VISIBLE_MOB_EFFECTS, now.effects)))
            if (now.shieldUp != before.shieldUp) packets += SetActorData(other.entityId, listOf(secondFlagsItem(shownFlags(now))))
        }
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /** This player's entry on everyone's player list. They must have logged in. */
    private fun listEntry(): PlayerListEntry.Add {
        val identity = checkNotNull(player) { "a player in the game has logged in" }
        return PlayerListEntry.Add(
            identity.uuid, entityId, identity.displayName, identity.xuid.orEmpty(),
            platform = login?.clientInfo?.deviceOs ?: UNKNOWN_PLATFORM,
            skin = checkNotNull(skin) { "a player in the game has a skin from their login" },
        )
    }

    /** This player, appearing to someone else as they [look]. */
    private fun addPlayer(look: Look): AddPlayer {
        val identity = checkNotNull(player) { "a player in the game has logged in" }
        val data = listOf(
            flagsItem(shownFlags(look)),
            secondFlagsItem(shownFlags(look)),
            StringItem(ActorDataId.NAME, identity.displayName),
            ByteItem(ActorDataId.ALWAYS_SHOW_NAME, 1),
            FloatItem(ActorDataId.SCALE, 1f),
            FloatItem(ActorDataId.WIDTH, PLAYER_WIDTH),
            FloatItem(ActorDataId.HEIGHT, heightOf(look)),
            LongItem(ActorDataId.VISIBLE_MOB_EFFECTS, look.effects),
        )
        val feet = Vec3(look.position.x, look.position.y - PLAYER_EYE_HEIGHT, look.position.z)
        return AddPlayer(
            identity.uuid, identity.displayName, entityId, feet, look.pitch, look.yaw, look.headYaw, look.held,
            gameMode, data, abilitiesFor(gameMode), platform = login?.clientInfo?.deviceOs ?: UNKNOWN_PLATFORM, permissions = permissionLevel,
        )
    }

    /**
     * The flags others see a player with as they [look]: their own, that
     * their name shows above them, whether they can't be seen, whether they
     * burn, whether they eat, and whether they hold up a shield.
     */
    private fun shownFlags(look: Look) = PLAYER_FLAGS + ActorFlag.SHOW_NAME + ActorFlag.ALWAYS_SHOW_NAME +
        listOfNotNull(ActorFlag.SPRINTING.takeIf { look.sprinting }, ActorFlag.SNEAKING.takeIf { look.sneaking }, ActorFlag.INVISIBLE.takeIf { look.invisible }, ActorFlag.ON_FIRE.takeIf { look.onFire },
            ActorFlag.SWIMMING.takeIf { look.swimming }, ActorFlag.GLIDING.takeIf { look.gliding }, ActorFlag.USING_ITEM.takeIf { look.eating },
            ActorFlag.BLOCKING.takeIf { look.shieldUp },
        )

    private fun heightOf(look: Look) = when {
        look.swimming || look.gliding -> PLAYER_SWIMMING_HEIGHT
        look.sneaking -> PLAYER_SNEAKING_HEIGHT
        else -> PLAYER_HEIGHT
    }

    /** Shows the client the player [entityId] swinging their arm, for [source], with [hand], or the main one, if it can see them. */
    private fun showSwing(entityId: Long, source: String?, hand: Int? = null) {
        if (entityId in shownPlayers) send(Animate(Animate.SWING_ARM, entityId, swingSource = source, hand = hand))
    }

    /**
     * Puts [stack] in [slot] of [container], [window] to the client, changed
     * from what was there by something the client didn't do or foresee,
     * like wear, under a new stack ID, and returns what tells the client.
     * The client takes no notice of a stack it has changing under the ID it
     * already knows it by.
     */
    private fun replaceSlot(container: Container, slot: Int, window: Int, stack: ItemStack?): ServerPacket {
        val replaced = stack?.let { SlotStack(it, inventory.newStackId()) }
        container[slot] = replaced
        return slotPacket(slot, replaced, window)
    }

    /**
     * What tells the client [slot] of its [window] holds [there] now: that
     * slot, but for the offhand, all of it, as the game's own server sends
     * it, since the client can take no notice of the offhand's one slot.
     */
    private fun slotPacket(slot: Int, there: SlotStack?, window: Int): ServerPacket =
        if (window == OFFHAND_WINDOW) InventoryContent(OFFHAND_WINDOW, listOf(there?.let { it.stack to it.id }))
        else InventorySlot(slot, there?.stack, there?.id ?: 0, window)

    /** The slot of the player's items numbered [slot], as it is now, for the client. */
    private fun slotOf(slot: Int) = inventory.items[slot].let { InventorySlot(slot, it?.stack, it?.id ?: 0) }

    private fun heldItem() = inventory.items[heldSlot]?.stack?.item?.name

    /**
     * Whether a break the client reported at its [tick] came sooner after
     * the player started on the block at [pos] than the [ticks] it takes.
     * The client counts its own ticks, so the gap is exact, but a quarter's
     * slack covers anything Kore's reckoning leaves out. A block that
     * breaks at once, or a break that came without a tick, isn't checked.
     */
    private fun tooSoon(pos: BlockPos, ticks: Int?, tick: Long?): Boolean {
        if (ticks == null || ticks <= 1 || tick == null) return false
        val started = breaking?.takeIf { it.pos == pos }
        val took = started?.let { tick - it.startTick }
        log.debug("{} broke a block at {} {} ticks after starting, of the {} it takes", player?.displayName, pos, took ?: "unknown", ticks)
        return took == null || took < ticks * 3 / 4
    }

    /**
     * Follows a survival player breaking blocks, from the steps their input
     * carries at the client's [tick]. Everyone with the chunk sees cracks
     * spread over a block the player starts on, at the pace it breaks at,
     * and bits fly off it each tick. In creative, blocks break at once, so
     * there's nothing to see. Starting on a block with fire on the face
     * they hit puts the fire out instead, in creative too.
     */
    private fun onBlockActions(actions: List<BlockAction>, tick: Long) {
        for (action in actions) {
            if (action.action == PlayerAction.START_DESTROY_BLOCK && putOutFire(action.position, action.face)) continue
            if (gameMode != GameMode.SURVIVAL) continue
            when (action.action) {
                PlayerAction.START_DESTROY_BLOCK -> startBreaking(action.position, tick)
                PlayerAction.CRACK_BLOCK -> crack(action.position, action.face)
                PlayerAction.ABORT_DESTROY_BLOCK, PlayerAction.STOP_DESTROY_BLOCK -> stopBreaking()
            }
        }
    }

    /** Puts out the fire on the [face] of the block at [pos], for everyone around to see and hear, if the player can. Returns whether there was fire there. */
    private fun putOutFire(pos: BlockPos, face: Int): Boolean {
        val side = Face.of(face) ?: return false
        if (!canBuild() || !inReach(pos)) return false
        val changes = world.putOutFire(pos, side) ?: return false
        showAround(world.packetsFor(changes))
        log.debug("{} put out the fire at {}", player?.displayName, pos.side(side))
        return true
    }

    private fun startBreaking(pos: BlockPos, tick: Long) {
        stopBreaking()
        val state = world.stateAt(pos)?.takeIf { it.name != AIR } ?: return
        // In Bedrock, punching a lectern knocks its book off, and punching a frame with an item in it knocks the item out, leaving the frame.
        if (state.isLectern && world.knockBookOff(pos)) showAround(pos, BlockActorData(pos, world.blockEntityAt(pos)!!))
        if (state.isItemFrame && knockOutOfFrame(pos)) return
        // Punching a note block plays it, as it starts to break.
        if (state.isNoteBlock) world.playNote(pos)?.let { showAround(world.packetsFor(Changes(emptyMap(), emptyList(), notes = listOf(it)))) }
        val ticks = ticksToBreak(state.name)
        breaking = Breaking(pos, tick, ticks)
        if (ticks != null && ticks > 0) showAround(pos, LevelEvent(LevelEvent.START_BLOCK_CRACKING, pos.corner, CRACKED / ticks))
    }

    private fun crack(pos: BlockPos, face: Int) {
        if (face !in Face.entries.indices || world.stateAt(pos)?.name in listOf(null, AIR)) return
        showAround(pos, LevelEvent(LevelEvent.PARTICLES_CRACK_BLOCK_DOWN + face, pos.corner, world.blockAt(pos)))
    }

    /** Stops the cracks spreading over the block the player was breaking, if any. */
    private fun stopBreaking() {
        val stopped = breaking ?: return
        breaking = null
        showAround(stopped.pos, LevelEvent(LevelEvent.STOP_BLOCK_CRACKING, stopped.pos.corner, 0))
    }

    /** How many ticks the player takes to break the block called [name], with what they hold, if Kore knows. */
    private fun ticksToBreak(name: String): Int? =
        game.mining[name]?.let { breakTicks(it, heldItem(), heldStack?.levelOf(EFFICIENCY) ?: 0, effects.level(Effect.HASTE)) }

    private fun canBuild() = gameMode == GameMode.SURVIVAL || gameMode == GameMode.CREATIVE

    private fun inReach(pos: BlockPos): Boolean {
        val reach = if (gameMode == GameMode.CREATIVE) CREATIVE_REACH else SURVIVAL_REACH
        val centre = pos.centre
        val dx = centre.x - position.x
        val dy = centre.y - position.y
        val dz = centre.z - position.z
        return dx * dx + dy * dy + dz * dz <= reach * reach
    }

    /** Sends [packets] about the block at [pos] to every player who has its chunk, this one included. */
    private fun showAround(pos: BlockPos, vararg packets: ServerPacket) = showAround(packets.map { pos to it })

    /**
     * Sends each packet, about the block it's paired with, to every player
     * who has that block's chunk, this one included: one batch for each
     * chunk, in order. The blocks a change reaches, like a torch on a
     * neighbour's side, can be in the next chunk.
     */
    private fun showAround(packets: List<Pair<BlockPos, ServerPacket>>) = viewers().showAround(packets)

    /** The block at [pos] as it is now, for the clients that have it. */
    private fun updateOf(pos: BlockPos): Pair<BlockPos, ServerPacket> = pos to UpdateBlock(pos, world.blockAt(pos))

    /** Sends [packets] about the chunk at [chunk], if the client has it. Without it, there's nothing for them to change. */
    fun show(chunk: ChunkPos, vararg packets: ServerPacket) {
        if (chunk in sentChunks) send(*packets)
    }

    /**
     * What the player sees of [packets] about blocks changing: all of them,
     * but for blocks a piston's moving, and its head, that the player's
     * inside, until the piston's done, when they see them as they are. The
     * client takes those for whole blocks where they end up, and would push
     * the player up on top of them rather than let the piston carry them.
     * A moving block is drawn instead from where it started, which the
     * player isn't inside, sliding along from there as a block the piston
     * pulled would, and a head is left out, as the piston draws it anyway.
     * The block drawn from where it started stays a tick after the one it
     * stood for lands, which the client takes a moment to draw, so that
     * it doesn't flicker out in between.
     */
    internal fun pistonView(packets: List<Pair<BlockPos, ServerPacket>>): List<Pair<BlockPos, ServerPacket>> {
        if (packets.isEmpty() && hiddenByPistons.isEmpty()) return packets
        val now = world.blockTicks
        val done = hiddenByPistons.filterValues { it <= now }.keys.toList()
        hiddenByPistons.keys -= done.toSet()
        val shown = packets + done.filter { pos -> packets.none { it.first == pos } }.flatMap { world.withData(it) }
        if (!isInGame) return shown
        val instead = LinkedHashMap<BlockPos, List<ServerPacket>>()
        val drawnFrom = HashSet<BlockPos>()
        for ((pos, packet) in shown) {
            if (packet !is UpdateBlock || pos in instead || !world.snapsUp(pos, feet)) continue
            instead[pos] = listOf(UpdateBlock(pos, AIR_NETWORK_ID))
            world.movingFrom(pos, feet)?.takeIf { (from, _) -> from !in instead }?.let { (from, data) ->
                instead[from] = listOf(UpdateBlock(from, world.blockAt(pos)), BlockActorData(from, data))
                drawnFrom += from
            }
        }
        if (instead.isEmpty()) return shown
        for (pos in instead.keys) hiddenByPistons[pos] = now + PISTON_MOVE_TICKS + if (pos in drawnFrom) 1 else 0
        val replaced = HashSet<BlockPos>()
        val seen = shown.flatMap { (pos, packet) ->
            val view = instead[pos] ?: return@flatMap listOf(pos to packet)
            if (replaced.add(pos)) view.map { pos to it } else emptyList()
        }
        return seen + instead.filterKeys { it !in replaced }.flatMap { (pos, view) -> view.map { pos to it } }
    }

    /**
     * Moves the player by [motion], as pistons push them or carry them
     * along, or stops them once the pistons are done with them, so they
     * end up where the blocks do rather than sliding on. Along the axes no
     * piston pushes them, they go on as the client last said they were
     * going, so they can still walk about.
     */
    internal fun pushedByPistons(motion: Vec3?) {
        val pushed = motion ?: pistonMotion ?: return
        pistonMotion = motion
        val push = motion ?: Vec3(0f, 0f, 0f)
        fun along(by: Float, axis: Float, own: Float) = if (axis != 0f) by else own
        val sent = Vec3(along(push.x, pushed.x, delta.x), along(push.y, pushed.y, delta.y), along(push.z, pushed.z, delta.z))
        send(SetActorMotion(entityId, sent))
        pistonWatch = PISTON_WATCH
        log.debug("{} at {} {} by pistons, set moving {}", player?.displayName, feet, if (motion == null) "stopped" else "pushed", sent)
    }

    private fun onPlayerInitialised(packet: SetLocalPlayerAsInitialised) {
        if (packet.runtimeId != entityId) {
            log.debug("Network {} initialised runtime ID {}, but its player's is {}", networkId, packet.runtimeId, entityId)
            return
        }
        state = State.IN_GAME
        send(*playerState(), commands.map.availableTo(permission), SetTime(world.time), *weatherNow())
        spawnPoint?.let { send(SetSpawnPosition(SetSpawnPosition.PLAYER, it)) }
        showSavedItems()
        showSavedEffects()
        log.info("{} joined the game", player?.displayName)
        announceJoin()
    }

    /**
     * Tells everyone the player joined, once they're in the game and their
     * loading screen has closed, which the client reports together. The
     * message waits a moment more: a client that hears its own join message
     * as it comes out of loading sometimes shows it twice.
     */
    private fun announceJoin() {
        val identity = player ?: return
        if (state != State.IN_GAME || !loadingScreenClosed || joinAnnounced) return
        joinAnnounced = true
        schedule(JOIN_MESSAGE_DELAY) {
            if (state == State.IN_GAME) tellEveryone(Text(Text.TRANSLATION, JOINED, parameters = listOf(identity.displayName), needsTranslation = true))
        }
    }

    /**
     * A message the player typed in chat, which everyone in the game sees:
     * the player too, since the client shows its own messages only once the
     * server sends them back. It goes out under the name and XUID the
     * player logged in with, whatever the client put. A blank message goes
     * nowhere, as there's nothing in it to show.
     */
    private fun onChat(packet: Text) {
        val identity = player ?: return
        if (packet.type != Text.CHAT) return
        val message = packet.message.trim()
        if (message.isEmpty()) {
            log.debug("Refused a blank chat message from {}", identity.displayName)
            return
        }
        if (config.logs.chat) chatLog.info("{} said {}", identity.displayName, message)
        tellEveryone(Text(Text.CHAT, message, sourceName = identity.displayName, xuid = identity.xuid.orEmpty()))
    }

    /**
     * A command the player typed, which Kore runs as them. The client shows
     * what came of it, under the request it made.
     */
    private fun onCommand(packet: CommandRequest) {
        if (state != State.IN_GAME) return
        if (config.logs.commands) commandLog.info("{} ran {}", name, packet.command)
        val result = commands.run(this, packet.command)
        send(CommandOutput(packet.origin, result.messages, result.successes))
    }

    /** Shows the player [text], if they're in the game. */
    fun message(text: Text) {
        if (isInGame) send(text)
    }

    /**
     * Puts the player in [mode]. The client changes what its player can do,
     * and everyone hears of it, which changes how they see the player.
     */
    fun changeGameMode(mode: GameMode) {
        gameMode = mode
        if (mode != GameMode.SURVIVAL) stopBreaking()
        if (Ability.MAY_FLY !in abilitiesFor(mode)) flying = false
        send(SetPlayerGameType(mode), abilities())
        viewers().filter { it.isInGame }.forEach { it.send(UpdatePlayerGameType(mode, entityId)) }
        log.info("{} is now in {} mode", name, mode.name.lowercase())
    }

    /**
     * Tells the client what the player may do now that they've become an
     * operator, or stopped being one: their abilities, and the commands
     * they may run.
     */
    fun permissionsChanged() {
        if (!isInGame) return
        send(abilities(), commands.map.availableTo(permission))
        log.info("{} is {}", name, if (isOperator) "an operator" else "no longer an operator")
    }

    /**
     * Moves the player's [feet] where a command says, facing the way [yaw]
     * and [pitch] say. Their view follows, and the client gets the chunks
     * around them it doesn't have.
     */
    fun teleport(feet: Vec3, yaw: Float, pitch: Float) {
        position = Vec3(feet.x, feet.y + PLAYER_EYE_HEIGHT, feet.z)
        this.yaw = yaw
        this.pitch = pitch
        headYaw = yaw
        teleportedTo = position
        fallDistance = 0f
        send(MovePlayer(entityId, position, pitch, yaw, yaw, onGround = false, teleport = true))
        viewCentre = position.toBlockPos().chunk
        chunkRadius?.let { updateView(it) }
        log.debug("{} was teleported to {}", name, feet)
    }

    /** Whether anything can hurt the player: not in creative or spectator, nor with the damage system off. */
    private val hurtable get() = world.features.damage && gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR

    /**
     * Hurts the player by [amount] of [cause], as the game does, unless they
     * can't be hurt, or the grace after their last hurt holds it off: armour,
     * Resistance and protection lessen it, as [damageAfter] works out, and
     * armour wears from it. They and everyone who sees them see them flinch,
     * but from a harder hurt in the grace, and if it's the end of them, they
     * die of it, at the hands of [by], if another player hit them, with
     * [weapon], unless a Totem of Undying saves them, as anything but the
     * void can be saved from. Returns whether it hurt them.
     */
    fun hurt(amount: Float, cause: DamageCause, by: Session? = null, weapon: ItemStack? = null): Boolean {
        if (!isInGame || dead || !hurtable) return false
        val damage = damageAfter(amount, cause, inventory.armour.contents().map { it?.stack }, effects.level(Effect.RESISTANCE), breach = weapon?.levelOf(BREACH) ?: 0)
        val fresh = !vitals.inGrace
        vitals.hurt(damage) ?: return false
        if (cause.armour && amount > 0) {
            wearArmour(armourWear(amount))
            exhaust(HURT_EXHAUSTION)
        }
        sendHealth()
        if (fresh) viewers().filter { it.isInGame }.forEach { it.showHurt(entityId, cause) }
        log.debug("{} was hurt by {} of {}, down to {}", name, damage, cause.name.lowercase(), vitals.health)
        if (!vitals.isDead) return true
        // The void takes a player whatever they hold.
        if (!cause.void && useTotem()) return true
        die(cause.message, killer = by?.name, weapon = weapon?.customName)
        return true
    }

    /**
     * Saves the player from dying with a Totem of Undying, in their offhand
     * or else their hand, which it uses up: they're left with half a heart
     * and none of their effects, but Regeneration II, Fire Resistance and
     * Absorption II from the totem. They see it rise before their eyes, and
     * everyone around sees its particles and hears it. Returns whether it
     * saved them.
     */
    private fun useTotem(): Boolean {
        val (container, slot, window) = listOf(Triple(inventory.offhand, 0, OFFHAND_WINDOW), Triple(inventory.items, heldSlot, INVENTORY_WINDOW))
            .firstOrNull { (container, slot) -> container[slot]?.stack?.item?.name == TOTEM } ?: return false
        container[slot] = null
        send(slotPacket(slot, null, window))
        vitals.health = 1f
        takeEffects()
        for ((effect, ticks) in TOTEM_EFFECTS) giveEffect(effect, ticks, ambient = false)
        sendHealth()
        viewers().filter { it.isInGame }.forEach { it.showTotem(entityId) }
        showAround(position.toBlockPos(), LevelEvent(LevelEvent.SOUND_TOTEM_USED, position, 0))
        log.debug("{} was saved by a totem", name)
        return true
    }

    /** Shows the client the player [entityId] saved by a totem, if it's its own player or one it can see. */
    private fun showTotem(entityId: Long) {
        if (entityId == this.entityId || entityId in shownPlayers) send(ActorEvent(entityId, ActorEvent.TALISMAN_ACTIVATE, 0))
    }

    /**
     * The player hitting another player, [use]'s target, with what they
     * hold, unless combat's switched off, the pvp rule says no, either of
     * them is dead, or the other's out of sight or reach. The hit's as hard
     * as the weapon, by Strength and Weakness, more for Sharpness, more for
     * how far the player fell if it's a mace's smash, and half again more if
     * it's critical, struck falling. Everyone around hears it.
     * If it hurts, it knocks the other back, the further for Knockback or a
     * sprint, unless they're still in the grace after their last hurt, and
     * sets them burning for Fire Aspect; the weapon wears, the player tires,
     * and the Thorns on what the other wears may prick them.
     */
    private fun attack(use: ItemUseOnEntity) {
        if (!world.features.combat || !world.rules.pvp || dead || gameMode == GameMode.SPECTATOR) return
        if (use.hotbarSlot in 0 until HOTBAR_SIZE) heldSlot = use.hotbarSlot
        val target = viewers().find { it.entityId == use.target && it !== this && it.isInGame && !it.dead } ?: return
        val reach = if (gameMode == GameMode.CREATIVE) CREATIVE_ATTACK_REACH else ATTACK_REACH
        if (use.target !in shownPlayers || eyes().distanceTo(target.feet) > reach) return
        // Hitting lowers the player's own shield, which takes as long to block again as raising it did.
        if (shieldRaisedAt != null) shieldRaisedAt = aliveTicks
        val held = heldStack
        val crit = canCrit(held)
        val fallen = fallDistance.takeIf { canSmash(held) }
        val sharpness = sharpnessDamage(held)
        // A harder hit in the grace after the last hurts by as much harder as it is, but knocks nobody back.
        val fresh = !target.vitals.inGrace
        val impaling = if (target.inWaterOrRain) impalingDamage(held?.levelOf(IMPALING) ?: 0) else 0f
        val damage = withStrength(attackDamage(held), effects.level(Effect.STRENGTH), effects.level(Effect.WEAKNESS)) + sharpness + impaling +
            (fallen?.let { smashDamage(it, held?.levelOf(DENSITY) ?: 0) } ?: 0f)
        if (target.blocks(feet, held, if (crit) critical(damage) else damage)) {
            // Fire Aspect sets them burning all the same, and the shield knocks the player back.
            held?.levelOf(FIRE_ASPECT)?.takeIf { it > 0 }?.let { target.setOnFire(it * FIRE_ASPECT_TICKS) }
            knockBack(target.feet, extra = 0f)
            log.debug("{}'s shield blocked {}'s hit", target.name, name)
            return
        }
        val hurt = target.hurt(if (crit) critical(damage) else damage, DamageCause.ATTACK, by = this, weapon = held)
        val sound = if (hurt) ATTACK_STRONG else ATTACK_NO_DAMAGE
        showAround(target.feet.toBlockPos(), LevelSoundEvent(sound, target.eyes(), -1, entityType = PLAYER_ENTITY))
        log.debug("{} hit {} with {}, for {}{}{}", name, target.name, held?.item?.name ?: "their hand", damage, if (crit) ", critically" else "", fallen?.let { ", smashing from $it blocks" }.orEmpty())
        if (!hurt) return
        fallen?.let { smash(target, it, held) }
        if (crit) viewers().forEach { it.showHit(target.entityId, Animate.CRITICAL_HIT, CRITICAL_HIT_PARTICLES) }
        if (sharpness > 0) viewers().forEach { it.showHit(target.entityId, Animate.MAGIC_CRITICAL_HIT, ENCHANTED_HIT_PARTICLES) }
        val knockback = (held?.levelOf(KNOCKBACK) ?: 0) + if (sprinting) 1 else 0
        if (fresh) target.knockBack(feet, knockback * KNOCKBACK_PER_LEVEL)
        held?.levelOf(FIRE_ASPECT)?.takeIf { it > 0 }?.let { target.setOnFire(it * FIRE_ASPECT_TICKS) }
        if (!creative && held != null) wearHeld(wearFromHitting(held.item.name), unforeseen = true)
        exhaust(ATTACK_EXHAUSTION)
        target.prick(this)
    }

    /**
     * Pricks [attacker], who just hurt the player, with the Thorns on what
     * the player wears: each piece with it may, by chance, which hurts them
     * and knocks them back, and wears the piece for it. The grace after the
     * first prick keeps the rest from hurting more than the hardest of them.
     * Everyone around hears each.
     */
    private fun prick(attacker: Session) {
        for (slot in 0 until inventory.armour.size) {
            val piece = inventory.armour[slot] ?: continue
            val damage = thornsPrick(piece.stack.levelOf(THORNS)) ?: continue
            val worn = piece.stack.worn(THORNS_WEAR)
            if (worn != piece.stack) send(replaceSlot(inventory.armour, slot, ARMOUR_WINDOW, worn))
            if (worn == null) showAround(feet.toBlockPos(), LevelSoundEvent(LevelSoundEvent.BREAK, position, -1))
            showAround(attacker.feet.toBlockPos(), LevelSoundEvent(LevelSoundEvent.THORNS, attacker.position, -1))
            val fresh = !attacker.vitals.inGrace
            if (attacker.hurt(damage, DamageCause.THORNS, by = this) && fresh) attacker.knockBack(feet, extra = 0f)
            if (attacker.dead) return
        }
    }

    /**
     * Whether a hit the player makes now with [held] smashes: with a mace,
     * having fallen a block and a half or more, and not slowed by Slow
     * Falling. Gliding doesn't stop it, as it does in Java.
     */
    private fun canSmash(held: ItemStack?) = smashesWith(held?.item?.name) && fallDistance >= SMASH_FALL && !onGround && effects[Effect.SLOW_FALLING] == null

    /**
     * Lands the player's smash on [target], from a fall of [fallen] blocks,
     * with [mace]: the fall does them no harm once they land, everyone
     * around hears it, and the ground bursts into dust if the other stood on
     * it. Wind Burst on the mace launches the player back up.
     */
    private fun smash(target: Session, fallen: Float, mace: ItemStack?) {
        fallDistance = 0f
        val sound = when {
            !target.onGround -> LevelSoundEvent.MACE_SMASH_AIR
            fallen > HEAVY_SMASH_FALL -> LevelSoundEvent.MACE_HEAVY_SMASH_GROUND
            else -> LevelSoundEvent.MACE_SMASH_GROUND
        }
        showAround(target.feet.toBlockPos(), LevelSoundEvent(sound, target.position, -1))
        if (target.onGround) showAround(target.feet.toBlockPos(), LevelEvent(LevelEvent.PARTICLES_SMASH_GROUND_DUST, target.feet, 0))
        val windBurst = mace?.levelOf(WIND_BURST) ?: 0
        if (windBurst <= 0) return
        send(SetActorMotion(entityId, Vec3(0f, windBurstLift(windBurst), 0f)))
        showAround(feet.toBlockPos(), LevelEvent(LevelEvent.PARTICLES_WIND_EXPLOSION, feet, 0), LevelSoundEvent(LevelSoundEvent.WIND_CHARGE_BURST, position, -1))
    }

    /**
     * Raises the shield the player holds while they sneak, in their hand or
     * else their offhand, and lowers it once they stop, or it leaves their
     * hands, or an axe disables it, which they and everyone who sees them
     * see.
     */
    private fun raiseShield() {
        val raised = sneaking && gameMode != GameMode.SPECTATOR && heldShield() != null && (cooldowns[SHIELD_COOLDOWN] ?: 0) <= aliveTicks
        if (raised == (shieldRaisedAt != null)) return
        shieldRaisedAt = if (raised) aliveTicks else null
        send(SetActorData(entityId, listOf(secondFlagsItem(playerFlags()))))
    }

    /** The shield the player would block with: one in their hand, or else their offhand, as its container, slot and window. */
    private fun heldShield(): Triple<Container, Int, Int>? =
        listOf(Triple(inventory.items, heldSlot, INVENTORY_WINDOW), Triple(inventory.offhand, 0, OFFHAND_WINDOW))
            .firstOrNull { (container, slot) -> container[slot]?.stack?.item?.name == SHIELD }

    /**
     * Blocks a hit of [damage] from [from], with [weapon], with the player's
     * shield, if it's been up long enough and the hit comes from in front of
     * them: it does nothing to them, but wears the shield, and an axe
     * disables it for a while. Everyone around hears it. Returns whether it
     * blocked the hit.
     */
    private fun blocks(from: Vec3, weapon: ItemStack?, damage: Float): Boolean {
        val raisedAt = shieldRaisedAt ?: return false
        if (aliveTicks - raisedAt < SHIELD_RAISE_TICKS || !shieldCovers(feet, yaw, from)) return false
        val (container, slot, window) = heldShield() ?: return false
        val shield = container[slot] ?: return false
        val worn = shield.stack.worn(shieldWear(damage))
        if (worn != shield.stack) send(replaceSlot(container, slot, window, worn))
        val disabled = disablesShields(weapon?.item?.name)
        showAround(feet.toBlockPos(), LevelSoundEvent(LevelSoundEvent.SHIELD_BLOCK, position, -1))
        if (worn == null || disabled) showAround(feet.toBlockPos(), LevelSoundEvent(LevelSoundEvent.BREAK, position, -1))
        if (disabled) {
            cooldowns[SHIELD_COOLDOWN] = aliveTicks + SHIELD_DISABLED_TICKS
            send(PlayerStartItemCooldown(SHIELD_COOLDOWN, SHIELD_DISABLED_TICKS))
            raiseShield()
        }
        return true
    }

    /**
     * Whether a hit the player makes now is critical: struck falling, with
     * [held], not a spear, and not flying, gliding, in water, on something
     * they climb, blind, or slowed or lifted by Slow Falling or Levitation.
     * Sprinting doesn't stop it, as it does in Java.
     */
    private fun canCrit(held: ItemStack?): Boolean {
        val at = feet.toBlockPos()
        return critsWith(held?.item?.name) && fallDistance > 0 && !onGround && !flying && !gliding && !world.isWet(at) &&
            world.stateAt(at)?.name !in CLIMBABLE && effects[Effect.SLOW_FALLING] == null && effects[Effect.BLINDNESS] == null &&
            effects[Effect.LEVITATION] == null
    }

    /**
     * Shows the client the player [entityId] hit, by [action], like the
     * sparks of a critical hit, as many [particles] of them, if it's its own
     * player or one it can see.
     */
    private fun showHit(entityId: Long, action: Int, particles: Int) {
        if (entityId == this.entityId || entityId in shownPlayers) send(Animate(action, entityId, particles.toFloat()))
    }

    /**
     * Knocks the player back from [from], where whoever hit them stood:
     * away from there and up, the further for each [extra] level, as of
     * Knockback, and less for the netherite they wear. The client moves its
     * player by it.
     */
    private fun knockBack(from: Vec3, extra: Float) {
        var dx = feet.x - from.x
        var dz = feet.z - from.z
        val across = sqrt(dx * dx + dz * dz)
        if (across > 0f) {
            dx /= across
            dz /= across
        }
        val kept = 1f - knockbackResistance(inventory.armour.contents().map { it?.stack })
        val force = (KNOCKBACK_FORCE + extra) * kept
        send(SetActorMotion(entityId, Vec3(dx * force, KNOCKBACK_HEIGHT * kept, dz * force)))
    }

    /** Sets the player burning for [ticks], as Fire Aspect does, which they and everyone who sees them see at once. */
    private fun setOnFire(ticks: Int) {
        val burning = vitals.fireTicks > 0
        ignite(ticks)
        if (!burning && vitals.fireTicks > 0) send(SetActorData(entityId, listOf(flagsItem(playerFlags()))))
    }

    /** Heals the player by [amount], up to as many hearts as they can have. */
    fun heal(amount: Float) {
        if (!dead && vitals.heal(amount)) sendHealth()
    }

    /**
     * Shows the client the player [entityId] flinching from [cause], if
     * it's its own player or one it can see. The client sounds the hurt by
     * its cause: the sound of burning, say, rather than the usual one.
     */
    private fun showHurt(entityId: Long, cause: DamageCause) {
        if (entityId == this.entityId || entityId in shownPlayers) send(ActorEvent(entityId, ActorEvent.HURT, cause.id))
    }

    /** Wears each piece of armour the player has on by [uses], as a blow does. One that wears out breaks, which everyone around hears. */
    private fun wearArmour(uses: Int) {
        val changed = mutableListOf<ServerPacket>()
        for (slot in 0 until inventory.armour.size) {
            val there = inventory.armour[slot]?.takeIf { armourOf(it.stack.item.name).first > 0 } ?: continue
            val worn = there.stack.worn(uses)
            if (worn == there.stack) continue
            changed += replaceSlot(inventory.armour, slot, ARMOUR_WINDOW, worn)
            if (worn == null) showAround(position.toBlockPos(), LevelSoundEvent(LevelSoundEvent.BREAK, position, -1))
        }
        if (changed.isNotEmpty()) send(*changed.toTypedArray())
    }

    /** Sets the player burning for [ticks], unless they already burn for longer, less a share for each level of Fire Protection they wear. */
    private fun ignite(ticks: Int) {
        val protection = inventory.armour.contents().sumOf { it?.stack?.levelOf(FIRE_PROTECTION) ?: 0 }
        vitals.fireTicks = maxOf(vitals.fireTicks, ticks - ticks * protection * FIRE_PROTECTION_SHARE / 100)
    }

    /**
     * Hurts the player by lightning that struck at [at], if it struck near
     * them, which sets them burning too.
     */
    fun struckNear(at: Vec3) {
        if (abs(feet.x - at.x) > LIGHTNING_REACH || abs(feet.z - at.z) > LIGHTNING_REACH || feet.y + playerHeight() < at.y || feet.y > at.y + LIGHTNING_REACH * 2) return
        if (!hurtable) return
        ignite(FIRE_BURNS)
        hurt(LIGHTNING_DAMAGE, DamageCause.LIGHTNING)
    }

    /**
     * What hurts and heals the player this tick, unless nothing can hurt
     * them: the void below the world; lava and fire, which set them burning,
     * and burning itself, once a second, unless Fire Resistance keeps them
     * from harm; a lit campfire or magma they don't sneak on; a cactus, a
     * berry bush they move through, and a block they're inside; water they
     * hold their breath under, once it runs out; poison, wither and
     * Regeneration; and their own healing, while they're well fed. Water and
     * rain put out their fire. They see what changes of their breath and fire.
     * Run once a tick.
     */
    fun tickHealth() {
        if (state != State.IN_GAME || dead) return
        aliveTicks++
        vitals.tick()
        val flags = playerFlags()
        val air = vitals.air
        if (hurtable) {
            val around = world.surroundings(feet, playerHeight(), eyes())
            hurtByWorld(around)
            breathe(around.underwater)
            if (!dead) effectsOnHealth()
            if (!dead) regenerate()
        } else {
            vitals.fireTicks = 0
            breathe(underwater = false)
        }
        if (dead) return
        tickHunger()
        stopUsingWhatsGone()
        raiseShield()
        soundLoading()
        if (playerFlags() != flags || vitals.air != air) {
            send(SetActorData(entityId, listOf(flagsItem(playerFlags()), ShortItem(ActorDataId.AIR_SUPPLY, maxOf(vitals.air, 0).toShort()))))
        }
        if (hunger.food to hunger.saturation != shownHunger) sendHunger()
    }

    /**
     * What the player's hunger does this tick: in peace, their food bar
     * fills again a point every half second, the Hunger effect tires them,
     * and Saturation feeds them. With hunger switched off, the bar stays
     * full.
     */
    private fun tickHunger() {
        if (!world.features.hunger) {
            hunger.reset()
        } else {
            if (world.difficulty == Difficulty.PEACEFUL && aliveTicks % PEACEFUL_FEEDING_INTERVAL == 0 && !hunger.full) hunger.food++
            effects[Effect.HUNGER]?.let { exhaust(HUNGER_EFFECT_EXHAUSTION * (it.amplifier + 1)) }
        }
        effects[Effect.SATURATION]?.let { if (world.features.food) hunger.eat(it.amplifier + 1, 1f) }
    }

    /**
     * The client saying its player is eating or drinking, as it does every
     * few ticks while it does: the sound of it, and the bits of food from
     * their mouth, for them and everyone who sees them, by what they hold.
     * The client shows its own player eating only as the server says, so
     * it's heard in step with what the client shows, whatever Kore makes of
     * the eating itself.
     */
    private fun onEating() {
        if (!world.features.food || dead) return
        val held = inventory.items[heldSlot]?.stack?.takeIf { it.item.food != null } ?: return
        val event = ActorEvent(entityId, ActorEvent.EATING_ITEM, (held.item.runtimeId shl EATING_ITEM_SHIFT) or held.aux)
        viewers().filter { it.isInGame }.forEach { it.showEating(entityId, event) }
    }

    /** Shows the client the player [entityId] eating, [event], if it's its own player or one it can see. */
    private fun showEating(entityId: Long, event: ActorEvent) {
        if (entityId == this.entityId || entityId in shownPlayers) send(event)
    }

    /** Tires the player by [amount], unless their hunger is switched off, or they're in creative or spectator. */
    private fun exhaust(amount: Float) {
        if (!world.features.hunger || (gameMode != GameMode.SURVIVAL && gameMode != GameMode.ADVENTURE)) return
        hunger.exhaust(amount, peace = world.difficulty == Difficulty.PEACEFUL)
    }

    private fun hurtByWorld(around: Surroundings) {
        val rules = world.rules
        val burns = rules.fireDamage && effects[Effect.FIRE_RESISTANCE] == null
        if (feet.y < MIN_Y - VOID_DEPTH) hurt(VOID_DAMAGE, DamageCause.VOID)
        if (around.lava) {
            ignite(LAVA_BURNS)
            if (burns) hurt(LAVA_DAMAGE, DamageCause.LAVA)
        }
        if (around.fire > 0) {
            ignite(FIRE_BURNS)
            if (burns) hurt(around.fire.toFloat(), DamageCause.FIRE)
        }
        if (around.campfire > 0 && !sneaking && burns) hurt(around.campfire.toFloat(), DamageCause.FIRE)
        val frostWalker = inventory.armour[FEET_SLOT]?.stack?.levelOf(FROST_WALKER) ?: 0
        if (around.magma && onGround && !sneaking && frostWalker == 0 && burns) hurt(1f, DamageCause.MAGMA)
        if (around.cactus) hurt(1f, DamageCause.CACTUS)
        if (around.berryBush && (abs(delta.x) >= BERRY_PRICK_MOVE || abs(delta.z) >= BERRY_PRICK_MOVE)) hurt(1f, DamageCause.BERRY_BUSH)
        if (around.suffocating) hurt(1f, DamageCause.SUFFOCATION)
        if (vitals.fireTicks > 0 && (around.water || world.rainingAt(position.toBlockPos()))) {
            vitals.fireTicks = 0
            showAround(position.toBlockPos(), LevelSoundEvent(FIZZ, position, -1))
        }
        if (vitals.fireTicks > 0) {
            if (vitals.fireTicks % BURN_INTERVAL == 0 && !around.lava && burns) hurt(1f, DamageCause.BURNING)
            vitals.fireTicks--
        }
    }

    /**
     * The player breathing, or holding their breath [underwater], unless
     * Water Breathing or Conduit Power lets them breathe there, which runs out
     * a tick at a time, less often with Respiration, and then drowns them a
     * little every second. Above water, they get their breath back quickly.
     */
    private fun breathe(underwater: Boolean) {
        breathing = !underwater || effects[Effect.WATER_BREATHING] != null || effects[Effect.CONDUIT_POWER] != null
        if (breathing) {
            vitals.air = minOf(PLAYER_MAX_AIR.toInt(), vitals.air + BREATH_REGAINED)
            return
        }
        val respiration = inventory.armour[HEAD_SLOT]?.stack?.levelOf(RESPIRATION) ?: 0
        if (respiration > 0 && Random.nextInt(respiration + 1) > 0) return
        if (--vitals.air > -DROWNING_INTERVAL) return
        vitals.air = 0
        if (world.rules.drowningDamage) hurt(DROWNING_DAMAGE, DamageCause.DROWNING)
    }

    /**
     * What the player's effects do to their health: Regeneration heals them
     * and poison and wither hurt them, each so many ticks apart, twice as
     * often a level up. Poison leaves them half a heart, but fatal poison doesn't.
     */
    private fun effectsOnHealth() {
        fun due(effect: Int, every: Int): Boolean {
            val on = effects[effect] ?: return false
            val interval = every shr on.amplifier
            val ticks = if (on.ticksLeft == INFINITE_EFFECT) aliveTicks else on.ticksLeft
            return interval <= 0 || ticks % interval == 0
        }
        if (due(Effect.REGENERATION, REGENERATION_EVERY)) heal(1f)
        if (due(Effect.POISON, POISON_EVERY) && vitals.health > 1) hurt(1f, DamageCause.MAGIC)
        if (due(Effect.FATAL_POISON, POISON_EVERY)) hurt(1f, DamageCause.MAGIC)
        if (due(Effect.WITHER, WITHER_EVERY)) hurt(1f, DamageCause.WITHER)
    }

    /**
     * The player healing by themselves, as the rules let them: a point every
     * four seconds while they have 18 food or more, which tires them, and
     * every second in peace. Or, with no food at all, starving a point
     * every four seconds, down to five hearts on easy and half a heart on
     * normal, and to death on hard.
     */
    private fun regenerate() {
        val rules = world.rules
        val difficulty = world.difficulty
        if (difficulty == Difficulty.PEACEFUL && rules.naturalRegeneration && aliveTicks % PEACEFUL_REGENERATION_INTERVAL == 0) heal(1f)
        val regenerating = rules.naturalRegeneration && hunger.food >= WELL_FED && vitals.health < vitals.maxHealth
        val starving = hunger.food <= 0
        if (!regenerating && !starving) {
            sinceRegeneration = 0
            return
        }
        if (++sinceRegeneration < REGENERATION_INTERVAL) return
        sinceRegeneration = 0
        if (regenerating) {
            heal(1f)
            exhaust(REGENERATION_EXHAUSTION)
        } else if (difficulty == Difficulty.HARD || vitals.health > STARVES_DOWN_TO.getValue(difficulty)) {
            hurt(1f, DamageCause.STARVATION)
        }
    }

    /** The player's health and absorption, as their client shows them: in whole half hearts, rounded up, as the game shows them. */
    private fun healthAttributes() = listOf(
        Attribute(HEALTH, 0f, vitals.maxHealth, ceil(vitals.health), default = MAX_HEALTH),
        Attribute(ABSORPTION, 0f, Float.MAX_VALUE, ceil(vitals.absorption), default = 0f),
    )

    private fun sendHealth() {
        if (isInGame) send(UpdateAttributes(entityId, healthAttributes()))
    }

    /** The player's food bar, saturation and exhaustion, as their client shows them. */
    private fun hungerAttributes() = listOf(
        Attribute(FOOD, 0f, MAX_FOOD.toFloat(), hunger.food.toFloat(), default = MAX_FOOD.toFloat()),
        Attribute(SATURATION, 0f, MAX_FOOD.toFloat(), hunger.saturation, default = MAX_FOOD.toFloat()),
        Attribute(EXHAUSTION, 0f, SHOWN_MAX_EXHAUSTION, hunger.exhaustion.coerceAtMost(SHOWN_MAX_EXHAUSTION), default = 0f),
    )

    private fun sendHunger() {
        if (!isInGame) return
        send(UpdateAttributes(entityId, hungerAttributes()))
        shownHunger = hunger.food to hunger.saturation
    }

    /** Kills the player, as `/kill` does, unless they're in creative, where nothing can. Returns whether they died. */
    fun kill(): Boolean {
        if (gameMode == GameMode.CREATIVE) return false
        if (!dead) die(DIED)
        return true
    }

    /**
     * The player dying, of [cause], the key of the message that says so, at
     * the hands of [killer], if another player killed them, with [weapon],
     * if it has a name of its own, which the message says too. Everyone
     * around sees them fall over, and a moment later they're gone, and
     * everyone hears how they died, if the rules say to. They drop what they
     * had, but what's cursed to vanish, which goes, and some of their
     * experience, unless the rules keep it, and their effects end. Their
     * screen says how they died, and waits for them to respawn.
     */
    private fun die(cause: String, killer: String? = null, weapon: String? = null) {
        val message = if (killer != null && weapon != null) "$cause.item" else cause
        val parameters = listOfNotNull(name, killer, weapon.takeIf { killer != null })
        dead = true
        eating = null
        drawing = null
        loading = null
        cooldowns.clear()
        if (shieldRaisedAt != null) {
            shieldRaisedAt = null
            send(SetActorData(entityId, listOf(secondFlagsItem(playerFlags()))))
        }
        stopBreaking()
        closeWindow(reply = true, byServer = true)
        vitals.health = 0f
        vitals.fireTicks = 0
        sendHealth()
        viewers().filter { it.isInGame }.forEach { it.showDeath(entityId) }
        if (world.rules.showDeathMessages) tellEveryone(Text(Text.TRANSLATION, "%$message", parameters = parameters, needsTranslation = true))
        if (!world.rules.keepInventory) dropEverything()
        val ended = effects.clear()
        if (ended.isNotEmpty()) send(*ended.map { MobEffect(entityId, MobEffect.REMOVE, it) }.toTypedArray(), visibleEffects())
        effectsEnded(ended)
        send(DeathInfo(message, parameters), Respawn(spawnEyes(), Respawn.SEARCHING_FOR_SPAWN, entityId))
        schedule(DEAD_BODY_TICKS) { if (dead) bodyGone = true }
        log.debug("{} died: {}", name, message)
    }

    /** Shows the client the player [entityId] dying, if it's its own player or one it can see. */
    private fun showDeath(entityId: Long) {
        if (entityId == this.entityId || entityId in shownPlayers) send(ActorEvent(entityId, ActorEvent.DEATH, 0))
    }

    /**
     * Drops everything the player had, as dying does, around where they
     * were, but what's cursed to vanish, which goes; and some of their
     * experience, the rest of which they lose.
     */
    private fun dropEverything() {
        val from = Vec3(position.x, position.y - DEATH_DROP_BELOW_EYES, position.z)
        val windows = listOf(INVENTORY_WINDOW to inventory.items, OFFHAND_WINDOW to inventory.offhand, ARMOUR_WINDOW to inventory.armour)
        for ((_, container) in windows) {
            for (slot in 0 until container.size) {
                val there = container[slot] ?: continue
                container[slot] = null
                if (there.stack.levelOf(VANISHING) > 0) continue
                val spread = Random.nextFloat() * DEATH_DROP_SPREAD
                val angle = Random.nextDouble(2 * PI)
                world.spawnItem(there.stack, from, Vec3((-sin(angle) * spread).toFloat(), 0.2f, (cos(angle) * spread).toFloat()), DEATH_PICKUP_DELAY)
            }
        }
        send(*windows.map { (window, container) -> InventoryContent(window, List(container.size) { null }) }.toTypedArray())
        val dropped = minOf(experience.level * DEATH_EXPERIENCE_PER_LEVEL, MAX_DEATH_EXPERIENCE)
        experience = PlayerExperience()
        sendExperience()
        if (dropped > 0) world.dropExperience(feet, dropped)
    }

    /** Where the player respawns, at their eyes: the middle of their spawn point, or of the world's spawn. */
    private fun spawnEyes(): Vec3 {
        val spawn = spawnPoint ?: world.spawn
        return Vec3(spawn.x + 0.5f, spawn.y + PLAYER_EYE_HEIGHT, spawn.z + 0.5f)
    }

    /** Sets where the player respawns, as `/spawnpoint` does, or, for null, back to the world's spawn. */
    fun setSpawnPoint(pos: BlockPos?) {
        spawnPoint = pos
        tell(SetSpawnPosition(SetSpawnPosition.PLAYER, pos ?: world.spawn))
    }

    /** Sends the client [packets], if it's in the game, as commands show players titles and sounds, and where spawns are. */
    fun tell(vararg packets: ServerPacket) {
        if (isInGame) send(*packets)
    }

    /**
     * Brings the player back to life at the world's spawn, once they've
     * chosen to respawn and the client is ready: whole again, facing south
     * and looking straight ahead, where everyone sees them get up again.
     */
    private fun onRespawn(packet: Respawn) {
        if (!dead || packet.state != Respawn.CLIENT_READY_TO_SPAWN) return
        dead = false
        bodyGone = false
        vitals.reset()
        hunger.reset()
        breathing = true
        val eyes = spawnEyes()
        send(Respawn(eyes, Respawn.READY_TO_SPAWN, entityId), *playerState())
        teleport(Vec3(eyes.x, eyes.y - PLAYER_EYE_HEIGHT, eyes.z), SPAWN_YAW, SPAWN_PITCH)
        // Anyone still seeing them lie dead sees them get up, and go to where they respawned as players sync. Taking them
        // away and showing them afresh instead, so soon after, loses their skin on some clients, which show them as Steve.
        val look = look()
        viewers().forEach { it.showAlive(entityId, look) }
        log.debug("{} respawned", name)
    }

    /**
     * Shows the client the player [entityId], whom it saw die, alive again,
     * if it still shows them, as they [look] now. Their size goes with it:
     * the client keeps the one it shrank them to as they fell, which leaves
     * their hitbox and name down by their feet.
     */
    private fun showAlive(entityId: Long, look: Look) {
        if (entityId !in shownPlayers) return
        val size = listOf(flagsItem(shownFlags(look)), FloatItem(ActorDataId.WIDTH, PLAYER_WIDTH), FloatItem(ActorDataId.HEIGHT, heightOf(look)))
        send(ActorEvent(entityId, ActorEvent.SPAWN_ALIVE, 0), SetActorData(entityId, size))
    }

    /** What the player holds, if anything. */
    val heldStack: ItemStack? get() = inventory.items[heldSlot]?.stack

    /** Puts [stack] in the player's hand in place of what they held, as the same stack, changed. */
    fun replaceHeld(stack: ItemStack) {
        val held = inventory.items[heldSlot] ?: return
        inventory.items[heldSlot] = SlotStack(stack, inventory.newStackId())
        send(slotOf(heldSlot))
        log.debug("{}'s {} is now {}", name, held.stack.item.name, stack)
    }

    /** Gives the player [stack]: as much as fits among their items, with the rest dropped at their feet. */
    fun give(stack: ItemStack) {
        val (slots, left) = inventory.add(stack)
        if (slots.isNotEmpty()) send(*slots.map(::slotOf).toTypedArray())
        var dropping = left
        while (dropping > 0) {
            val count = minOf(dropping, stack.item.maxStackSize)
            world.spawnItem(stack.copy(count = count), feet, Vec3(0f, 0f, 0f), pickupDelay = 0)
            dropping -= count
        }
        log.debug("{} was given {} {}", name, stack.count, stack.item.name)
    }

    /**
     * Takes the player's items that [matching] picks out, from their
     * inventory, offhand and armour, up to [maxCount] of them, or all with a
     * negative count. A count of none takes nothing. Returns how many it
     * took, or with none, how many there are.
     */
    fun clearItems(maxCount: Int, matching: (ItemStack) -> Boolean): Int {
        var found = 0
        val changed = mutableListOf<ServerPacket>()
        for ((container, window) in listOf(inventory.items to INVENTORY_WINDOW, inventory.offhand to OFFHAND_WINDOW, inventory.armour to ARMOUR_WINDOW)) {
            for (slot in 0 until container.size) {
                val there = container[slot]?.takeIf { matching(it.stack) } ?: continue
                if (maxCount == 0) {
                    found += there.stack.count
                    continue
                }
                val taking = if (maxCount < 0) there.stack.count else minOf(there.stack.count, maxCount - found)
                if (taking == 0) continue
                container[slot] = if (taking < there.stack.count) there.copy(stack = there.stack.copy(count = there.stack.count - taking)) else null
                found += taking
                changed += slotPacket(slot, container[slot], window)
            }
        }
        if (changed.isNotEmpty()) send(*changed.toTypedArray())
        return found
    }

    /** Disconnects the player, for [reason] if there is one, as a command kicking them does. Everyone hears they left. */
    fun kick(reason: String?) {
        log.info("{} was kicked{}", name, reason?.let { ": $it" }.orEmpty())
        disconnect(Disconnect.Reason.KICKED, reason)
    }

    /**
     * Disconnects the player, for [reason], with [message] to show them if
     * there is one. Everyone hears they left, unless the server's stopping,
     * when everyone's leaving, and the log has no need to say so of each.
     */
    fun disconnect(reason: Disconnect.Reason, message: String? = null) {
        if (state == State.CLOSED) return
        send(Disconnect(reason, message))
        leave(announce = reason != Disconnect.Reason.SHUTDOWN)
        state = State.CLOSED
    }

    /** Saves where the player got to, if they're in the game, as an autosave does. */
    fun savePlayer() {
        val identity = player ?: return
        if (state == State.IN_GAME) game.players.save(identity.uuid, savedData())
    }

    /**
     * What's kept of the player between sessions, as the game saves a
     * player: where their eyes are and which way they look, their game
     * mode, their items, by slot, in their inventory, armour, offhand and
     * ender chest, the hotbar slot they hold, their experience, where they
     * respawn, if `/spawnpoint` set it, and their health, breath and fire.
     */
    private fun savedData(): NbtMap {
        fun listed(container: Container) = (0 until container.size).map { slot -> container[slot]?.let { ItemStacks.nbtOf(it.stack, slot) } ?: EMPTY_SLOT }
        return NbtMap.builder()
            .putString("identifier", "minecraft:player")
            .putList("Pos", NbtType.FLOAT, listOf(position.x, position.y, position.z))
            .putList("Rotation", NbtType.FLOAT, listOf(yaw, pitch))
            .putInt("PlayerGameMode", gameMode.id)
            .putInt("SelectedInventorySlot", heldSlot)
            .putList("Inventory", NbtType.COMPOUND, listed(inventory.items))
            .putList("Armor", NbtType.COMPOUND, listed(inventory.armour))
            .putList("Offhand", NbtType.COMPOUND, listed(inventory.offhand))
            .putList("EnderChestInventory", NbtType.COMPOUND, listed(enderChest))
            .putInt("PlayerLevel", experience.level)
            .putFloat("PlayerLevelProgress", experience.progress)
            .apply { spawnPoint?.let { putInt("SpawnX", it.x).putInt("SpawnY", it.y).putInt("SpawnZ", it.z) } }
            .putList("Attributes", NbtType.COMPOUND, savedAttributes())
            .putShort("Air", vitals.air.coerceAtLeast(0).toShort())
            .putShort("Fire", vitals.fireTicks.toShort())
            .putList("ActiveEffects", NbtType.COMPOUND, savedEffects())
            .build()
    }

    /** The player's effects, as the game saves them, each lasting as long on every difficulty. */
    private fun savedEffects(): List<NbtMap> = effects.all.entries.sortedBy { it.key }.map { (effect, on) ->
        NbtMap.builder()
            .putByte("Id", effect.toByte())
            .putByte("Amplifier", on.amplifier.toByte())
            .putInt("Duration", on.ticksLeft)
            .putInt("DurationEasy", on.ticksLeft).putInt("DurationNormal", on.ticksLeft).putInt("DurationHard", on.ticksLeft)
            .putBoolean("Ambient", on.ambient)
            .putBoolean("ShowParticles", on.particles)
            .putBoolean("DisplayOnScreenTextureAnimation", false)
            .build()
    }

    /** The player's health, absorption and hunger, as the game saves them among their attributes. */
    private fun savedAttributes(): List<NbtMap> = listOf(
        savedAttribute(HEALTH, vitals.health, MAX_HEALTH, vitals.maxHealth),
        savedAttribute(ABSORPTION, vitals.absorption, 0f, Float.MAX_VALUE),
        savedAttribute(FOOD, hunger.food.toFloat(), MAX_FOOD.toFloat(), MAX_FOOD.toFloat()),
        savedAttribute(SATURATION, hunger.saturation, MAX_FOOD.toFloat(), MAX_FOOD.toFloat()),
        savedAttribute(EXHAUSTION, hunger.exhaustion, 0f, SHOWN_MAX_EXHAUSTION),
    )

    private fun savedAttribute(name: String, current: Float, base: Float, max: Float): NbtMap = NbtMap.builder()
        .putString("Name", name)
        .putFloat("Base", base).putFloat("Current", current)
        .putFloat("Min", 0f).putFloat("Max", max)
        .putFloat("DefaultMin", 0f).putFloat("DefaultMax", max)
        .build()

    /**
     * Picks up where the player left off last time, as [savedData] kept it,
     * for whatever it kept that Kore can use, but which way they looked:
     * they face south again, looking straight ahead, as they do joining for
     * the first time.
     */
    private fun restoreFrom(data: NbtMap) {
        data.getList("Pos", NbtType.FLOAT, emptyList()).takeIf { it.size == 3 }?.let { Vec3(it[0], it[1], it[2]) }?.takeIf { it.isPlausible() }?.let {
            position = it
            viewCentre = it.toBlockPos().chunk
        }
        GameMode.entries.find { it.id == data.getInt("PlayerGameMode", -1) && it != GameMode.DEFAULT }?.let { gameMode = it }
        heldSlot = data.getInt("SelectedInventorySlot", 0).coerceIn(0, HOTBAR_SIZE - 1)
        for ((name, container) in listOf("Inventory" to inventory.items, "Armor" to inventory.armour, "Offhand" to inventory.offhand, "EnderChestInventory" to enderChest)) {
            data.getList(name, NbtType.COMPOUND, emptyList()).forEachIndexed { index, saved ->
                val (slot, stack) = game.items.fromNbt(saved) ?: return@forEachIndexed
                // Armour and the offhand go by their order, as the game lists them.
                val at = if (name == "Armor" || name == "Offhand") index else slot
                if (at in 0 until container.size) container[at] = SlotStack(stack, inventory.newStackId())
            }
        }
        experience = PlayerExperience(data.getInt("PlayerLevel", 0).coerceAtLeast(0), data.getFloat("PlayerLevelProgress", 0f).coerceIn(0f, 1f))
        if (listOf("SpawnX", "SpawnY", "SpawnZ").all(data::containsKey)) spawnPoint = BlockPos(data.getInt("SpawnX"), data.getInt("SpawnY"), data.getInt("SpawnZ"))
        restoreEffects(data.getList("ActiveEffects", NbtType.COMPOUND, emptyList()))
        val attributes = data.getList("Attributes", NbtType.COMPOUND, emptyList()).associateBy { it.getString("Name") }
        attributes[HEALTH]?.let { vitals.health = it.getFloat("Current", MAX_HEALTH).coerceIn(0f, vitals.maxHealth) }
        attributes[ABSORPTION]?.let { vitals.absorption = it.getFloat("Current", 0f).coerceAtLeast(0f) }
        attributes[FOOD]?.let { hunger.food = it.getFloat("Current", MAX_FOOD.toFloat()).toInt().coerceIn(0, MAX_FOOD) }
        attributes[SATURATION]?.let { hunger.saturation = it.getFloat("Current", STARTING_SATURATION).coerceIn(0f, hunger.food.toFloat()) }
        attributes[EXHAUSTION]?.let { hunger.exhaustion = it.getFloat("Current", 0f).coerceIn(0f, SHOWN_MAX_EXHAUSTION) }
        // Health saved as none is a player who left dead, back as if they'd respawned.
        if (vitals.isDead) {
            effects.clear()
            vitals.maxHealth = MAX_HEALTH
            vitals.reset()
            hunger.reset()
        }
        vitals.air = data.getShort("Air", PLAYER_MAX_AIR).toInt().coerceIn(0, PLAYER_MAX_AIR.toInt())
        vitals.fireTicks = data.getShort("Fire", 0).toInt().coerceAtLeast(0)
        log.debug("{} picks up at {}, in {}", player?.displayName, position, gameMode)
    }

    /**
     * Puts back the effects the player had, [saved], for as long as they
     * had left of each, but any Kore doesn't know or that had run out. Health
     * Boost gives them back the hearts it adds, for their health to fill.
     */
    private fun restoreEffects(saved: List<NbtMap>) {
        val known = EFFECT_KINDS.map { it.id }.toSet()
        for (effect in saved) {
            val id = effect.getByte("Id").toInt()
            val ticks = effect.getInt("Duration", 0)
            if (id !in known || (ticks <= 0 && ticks != INFINITE_EFFECT)) continue
            val amplifier = effect.getByte("Amplifier").toInt() and 0xFF
            effects.set(EffectLevel(id, amplifier), ticks, effect.getBoolean("ShowParticles", true), effect.getBoolean("Ambient"))
        }
        effects[Effect.HEALTH_BOOST]?.let { vitals.maxHealth = MAX_HEALTH + BOOST_PER_LEVEL * (it.amplifier + 1) }
    }

    /** Shows the client the effects the player had when they were last saved, which it starts out without. */
    private fun showSavedEffects() {
        if (effects.all.isEmpty()) return
        val shown = effects.all.entries.sortedBy { it.key }.map { (effect, on) ->
            MobEffect(entityId, MobEffect.ADD, effect, on.amplifier, on.ticksLeft, on.ambient, particles = on.particles)
        }
        send(*shown.toTypedArray(), visibleEffects())
    }

    /** Shows the client the items the player had when they were last saved, which it starts out without. */
    private fun showSavedItems() {
        val windows = listOf(INVENTORY_WINDOW to inventory.items, OFFHAND_WINDOW to inventory.offhand, ARMOUR_WINDOW to inventory.armour)
        val packets = windows.filter { (_, container) -> container.contents().any { it != null } }
            .map { (window, container) -> InventoryContent(window, container.contents().map { slot -> slot?.let { it.stack to it.id } }) }
        if (packets.isNotEmpty()) send(*packets.toTypedArray())
    }

    /**
     * Saves where the player got to, once their session has ended, and if
     * [announce], tells the log and everyone still in the game that they left.
     */
    fun leave(announce: Boolean = true) {
        val identity = player ?: return
        if (state != State.IN_GAME) return
        closeWindow(reply = false)
        // A player who leaves dead comes back as if they'd respawned.
        if (dead) {
            position = spawnEyes()
            vitals.reset()
            hunger.reset()
        }
        game.players.save(identity.uuid, savedData())
        state = State.CLOSED
        if (!announce) return log.debug("{} left as the server stopped", identity.displayName)
        log.info("{} left the game", identity.displayName)
        tellEveryone(Text(Text.TRANSLATION, LEFT, parameters = listOf(identity.displayName), needsTranslation = true))
    }

    /** Sends [text] to every player in the game. */
    private fun tellEveryone(text: Text) = viewers().filter { it.isInGame }.forEach { it.send(text) }

    /**
     * What the client needs to know about its own player to move it like
     * the game does. Without the flags, the client treats its player as
     * something that doesn't breathe or fall. The attributes give it its
     * health, full hunger (sprinting needs food) and its walking speed.
     */
    private fun playerState(): Array<ServerPacket> = arrayOf(
        SetActorData(
            entityId,
            listOf(
                flagsItem(playerFlags()),
                ShortItem(ActorDataId.AIR_SUPPLY, maxOf(vitals.air, 0).toShort()),
                ShortItem(ActorDataId.AIR_SUPPLY_MAX, PLAYER_MAX_AIR),
                FloatItem(ActorDataId.SCALE, 1f),
                FloatItem(ActorDataId.WIDTH, PLAYER_WIDTH),
                FloatItem(ActorDataId.HEIGHT, playerHeight()),
            ),
        ),
        UpdateAttributes(
            entityId,
            healthAttributes() + listOf(
                Attribute("minecraft:movement", 0f, Float.MAX_VALUE, PLAYER_WALK_SPEED),
            ) + hungerAttributes() + experienceAttributes(),
        ),
        abilities(),
    )

    /** What the player may do: in their game mode, as an operator or not, and with commands. */
    private fun abilities() = UpdateAbilities(entityId, permissionLevel, abilitiesFor(gameMode), walkSpeed = PLAYER_WALK_SPEED, commandPermission = permission)

    private fun reject() {
        send(Disconnect(Disconnect.Reason.NOT_AUTHENTICATED))
        state = State.CLOSED
    }

    /**
     * Takes the client to be on the version of the game that speaks
     * [clientProtocol], if Kore speaks it, and turns away clients on any
     * other, telling them whose version is behind.
     */
    private fun acceptsProtocol(clientProtocol: Int): Boolean {
        GameVersion.of(clientProtocol)?.let {
            gameVersion = it
            return true
        }

        log.info("Network {} speaks protocol {}, but Kore speaks {}", networkId, clientProtocol, GameVersion.entries.joinToString { "${it.protocol} (${it.title})" })
        val status = if (clientProtocol < GameVersion.NEWEST.protocol) PlayStatus.Status.LOGIN_FAILED_CLIENT_OLD else PlayStatus.Status.LOGIN_FAILED_SERVER_OLD
        send(PlayStatus(status))
        state = State.CLOSED
        return false
    }

    private fun unexpected(packet: ClientPacket) {
        log.debug("Ignored {} from network {} while {}", packet, networkId, state)
    }

    /** Sends [packets] together in one batch. */
    private fun send(vararg packets: ServerPacket) {
        val batch = batches.encode(packets.map { it.encode(gameVersion) })
        packetLog.debug("-> {}: {} ({} bytes)", who, names(packets), batch.size)
        send(batch)
    }

    /** Who the logs call this session: the player once they've logged in, their network until then. */
    private val who get() = player?.displayName ?: "network $networkId"
}

/**
 * Sends each packet, about the block it's paired with, to every one of
 * these players who has that block's chunk: one batch for each chunk, in
 * order.
 */
internal fun Collection<Session>.showAround(packets: List<Pair<BlockPos, ServerPacket>>) {
    for ((chunk, inChunk) in packets.groupBy { (pos, _) -> pos.chunk }) {
        val batch = inChunk.map { (_, packet) -> packet }.toTypedArray()
        forEach { it.show(chunk, *batch) }
    }
}

/**
 * Ticks the blocks in [game]'s world, for these players to see if they
 * have the chunks: blocks that lost what held them up a tick ago break,
 * scaffolding moves along, sand and the like fall, coral out of water
 * dies, big dripleaves tip under the players and items standing on them,
 * pressure plates press under the players, items and orbs on them,
 * pressed buttons pop back out, and pistons move, and push or carry the
 * players in the way of what they move, or on top of it. Spectators press
 * nothing. What breaks drops what it drops, if [dropItems], and what it
 * held either way, and falling blocks that can't land drop as items. Run
 * once a tick.
 */
/**
 * Flies what's been thrown a tick, at the players in the game, and lands
 * what hits something: it bursts where it hit for everyone around to see,
 * a player it hit is hit by it, and an ender pearl takes whoever threw it
 * there.
 */
internal fun Collection<Session>.flyProjectiles(game: GameData) {
    val players = filter { it.isInGame && !it.dead && it.gameMode != GameMode.SPECTATOR }
    val hits = game.world.tickProjectiles(players.associate { it.entityId to it.hitbox }, players.associate { it.entityId to it.position })
    // A trident setting off back to whoever threw it calls out.
    for (trident in game.world.projectiles.filter { it.setOffBack }) showAround(listOf(trident.position.toBlockPos() to LevelSoundEvent(LevelSoundEvent.TRIDENT_RETURN, trident.position, -1)))
    for (hit in hits) {
        val projectile = hit.projectile
        val thrower = players.find { it.entityId == projectile.thrower }
        val target = (hit as? ProjectileHit.Entity)?.let { struck -> players.find { it.entityId == struck.entityId } }
        if (projectile.kind == ProjectileKind.TRIDENT) {
            landTrident(hit, target, thrower, game)
            continue
        }
        if (projectile.kind == ProjectileKind.ARROW) {
            // An arrow that hurts its target goes, and one that doesn't bounces off. One that hits a block quivers in it.
            if (hit is ProjectileHit.Entity) {
                val hurt = target?.shotBy(projectile, thrower) == true
                val piercing = projectile.arrow?.piercing ?: 0
                when {
                    !hurt -> projectile.bounce()
                    // A piercing arrow goes on through, as many as its Piercing lets it.
                    projectile.pierced.size < piercing -> projectile.pierced += hit.entityId
                    else -> game.world.removeProjectile(projectile.id)
                }
            }
            val sounds = listOf(LevelSoundEvent(LevelSoundEvent.BOW_HIT, hit.at, -1)) + listOfNotNull(ActorEvent(projectile.id, ActorEvent.ARROW_SHAKE, ARROW_SHAKE_TICKS).takeIf { hit is ProjectileHit.Block })
            showAround(sounds.map { hit.at.toBlockPos() to it })
            continue
        }
        target?.hitBy(projectile, thrower)
        showAround(burst(projectile.kind, hit.at, game).map { hit.at.toBlockPos() to it })
        if (projectile.kind == ProjectileKind.ENDER_PEARL) thrower?.arriveByPearl(hit.at)
    }
}

/**
 * Lands the trident that made [hit] on [target], thrown by [thrower]: one
 * that hurts them falls away, calling lightning down on them with
 * Channeling, if they're out in the rain in a thunderstorm, and one that
 * doesn't bounces off. Everyone around hears it hit, or hit the ground.
 */
private fun Collection<Session>.landTrident(hit: ProjectileHit, target: Session?, thrower: Session?, game: GameData) {
    val trident = hit.projectile
    val sound = if (hit is ProjectileHit.Entity) LevelSoundEvent.TRIDENT_HIT else LevelSoundEvent.TRIDENT_HIT_GROUND
    showAround(listOf(hit.at.toBlockPos() to LevelSoundEvent(sound, hit.at, -1)))
    if (hit !is ProjectileHit.Entity) return
    if (target?.stabbedBy(trident, thrower) != true) return trident.bounce()
    trident.fallAway()
    if ((trident.item?.levelOf(CHANNELING) ?: 0) == 0 || !target.channelsLightning) return
    val changed = mutableListOf<BlockPos>()
    val events = mutableListOf<WorldEvent>()
    val bolt = game.world.strike(target.feet.toBlockPos(), changed, events)
    showAround(game.world.packetsFor(Changes(emptyMap(), changed.distinct(), events = events)))
    showAround(listOf(hit.at.toBlockPos() to LevelSoundEvent(LevelSoundEvent.TRIDENT_THUNDER, hit.at, -1)))
    forEach { it.struckNear(bolt.position) }
}

/** How long an arrow quivers as it sticks in a block, in ticks. */
private const val ARROW_SHAKE_TICKS = 7

/** How long a stuck arrow lies before it can be picked up, in ticks, and how far beyond a player's box they reach it, across and up and down. */
private const val ARROW_PICKUP_DELAY = 5
private const val ARROW_PICKUP_ACROSS = 1.0
private const val ARROW_PICKUP_UP = 0.5

/** How many bits a snowball or an egg bursts into where it hits. */
private const val BURST_PARTICLES = 6

/** What everyone around sees and hears of [kind] landing [at]: a snowball's puff, an egg smashing, in bits of its own colour, or an ender pearl's flash. */
private fun burst(kind: ProjectileKind, at: Vec3, game: GameData): List<ServerPacket> = when {
    kind == ProjectileKind.SNOWBALL -> List(BURST_PARTICLES) { LevelEvent(LevelEvent.PARTICLE_SNOWBALL_POOF, at, 0) }
    kind.egg -> {
        val egg = (kind.item?.let { game.items.of(it) }?.item?.runtimeId ?: 0) shl Short.SIZE_BITS
        List(BURST_PARTICLES) { LevelEvent(LevelEvent.PARTICLE_ITEM_BREAK, at, egg) }
    }
    else -> listOf(LevelEvent(LevelEvent.PARTICLES_TELEPORT, at, 0), LevelSoundEvent(LevelSoundEvent.TELEPORT, at, -1))
}

internal fun Collection<Session>.tickBlocks(game: GameData, dropItems: Boolean) {
    val world = game.world
    val stood = world.stepOn(mapNotNull { it.standingAt } + world.items.filter { it.onGround }.map { it.position })
    val pressed = world.pressPlates(filter { it.isInGame && it.gameMode != GameMode.SPECTATOR && !it.dead }.map { it.feet })
    val ticked = world.tickBlocks()
    val changes = Changes(ticked.broken, (stood + pressed.reshaped + ticked.reshaped).distinct(), pressed.sounds + ticked.sounds, ticked.data, events = ticked.events)
    val packets = if (changes.isEmpty) emptyList() else world.packetsFor(changes)
    // A block that starts falling shows falling before it goes from where it was, and one that lands shows where it landed before it stops falling.
    forEach { it.showFallingBlocks() }
    forEach { listOf(it).showAround(it.pistonView(packets)) }
    forEach { it.hideFallingBlocks() }
    if (ticked.broken.isNotEmpty() || ticked.moved.isNotEmpty()) forEach { it.containerBroke(ticked.broken.keys + ticked.moved) }
    val players = filter { it.isInGame }
    val motions = world.pistonMotions(players.associate { it.entityId to it.feet })
    players.forEach { it.pushedByPistons(motions[it.entityId]) }
    changes.broken.forEach { (pos, broken) -> game.dropBroken(pos, broken, harvested = dropItems) }
    ticked.dropped.forEach { (at, state) -> game.dropFallen(at, state) }
}

/**
 * Drops the block [state] that fell to [at] and couldn't land there, as
 * its item, as the game does whatever the game mode: snow as a single
 * snowball, however deep it was.
 */
private fun GameData.dropFallen(at: Vec3, state: BlockState) {
    val stacks = if (state.name == SNOW_LAYER) listOfNotNull(items.of(SNOWBALL)) else drops.of(state)
    world.dropFromBlock(at.toBlockPos(), stacks)
}

/**
 * Drops what the [broken] block at [pos] leaves in the world: the block,
 * if it was [harvested] and the `doTileDrops` rule lets blocks drop, with
 * the data it kept, as broken with [tool], and what it held, even if not,
 * a shulker box keeping that inside itself as it drops, and any other
 * container dropping it loose. The experience a furnace's work earned that
 * nobody took drops too.
 */
internal fun GameData.dropBroken(pos: BlockPos, broken: BrokenBlock, harvested: Boolean, tool: ItemStack? = null) {
    val keepsItems = broken.state.storage == Storage.SHULKER_BOX
    val dropping = harvested && world.rules.doTileDrops
    if (dropping || (keepsItems && broken.contents.any { it != null })) world.dropFromBlock(pos, drops.of(broken.state, broken.blockEntity, broken.contents, tool))
    if (!keepsItems) world.dropFromBlock(pos, broken.contents.filterNotNull())
    wholeExperience(broken.experience).takeIf { it > 0 }?.let { world.dropExperience(pos.centre, it) }
}

/**
 * Works the furnaces, brewing stands, hoppers, dispensers and droppers in
 * [game]'s world for a tick, for these players to see if they have the
 * chunks: furnaces lighting up and going out, the bottles on brewing
 * stands, the sound of a brew finishing, dispensers firing, and what
 * their bone meal grew, composters filling and emptying, and jukeboxes
 * playing what hoppers put in them, and what's playing. Run once a tick, after items move, so that the items
 * hoppers take in go from the world as players see it.
 */
internal fun Collection<Session>.tickMachines(game: GameData) {
    val world = game.world
    val brewed = mutableListOf<BlockPos>()
    val fired = mutableListOf<Fired>()
    val sounds = mutableListOf<BlockSound>()
    val songs = mutableListOf<SongChange>()
    val changed = world.tickMachines(game.recipes, game.fuel, brewed, fired, sounds, game.items, songs)
    showAround(
        changed.map { it to UpdateBlock(it, world.blockAt(it)) } + songs.mapNotNull { (at) -> world.blockEntityAt(at)?.let { at to BlockActorData(at, it) } } +
            brewed.map { it to LevelSoundEvent(BREWED, it.centre, -1) } + fired.flatMap(::firing) +
            fired.mapNotNull { it.grew }.flatMap { world.packetsFor(Changes(emptyMap(), it.changed, events = it.events)) } +
            sounds.map { it.pos to LevelSoundEvent(it.sound, it.pos.centre, it.data) } + songs.flatMap(::songPackets) + songs.mapNotNull { world.nowPlaying(it.pos) },
    )
}

/**
 * Every [BEACON_INTERVAL] ticks, looks at the beacons in [game]'s world:
 * everyone with the chunk hears those whose beams come on or go out, and
 * the players those shining reach, spectators aside, get their powers.
 */
internal fun Collection<Session>.tickBeacons(game: GameData) {
    val world = game.world
    if (world.blockTicks % BEACON_INTERVAL != 0L) return
    val looked = world.lookAtBeacons(game.traits)
    showAround(looked.sounds.map { it.pos to LevelSoundEvent(it.sound, it.pos.centre, it.data) })
    val players = filter { it.isInGame && it.gameMode != GameMode.SPECTATOR && !it.dead }
    for (beacon in looked.shining) {
        for (player in players.filter { beacon.reaches(it.feet) }) beacon.effects.forEach { player.giveEffect(it, beacon.ticks, ambient = true) }
    }
}

/**
 * Shows the players who've asked about each map in [world] what changed on
 * it since they last saw, and on a locator map, where the players on it,
 * spectators aside, are now. Run once a tick, after the players holding
 * maps have drawn on them.
 */
internal fun Collection<Session>.syncMaps(world: FlatWorld) {
    val players = filter { it.isInGame && it.gameMode != GameMode.SPECTATOR }
    for (map in world.maps.all) {
        val patches = map.takeChanges()
        val markers = if (map.showsPlayers) players.mapNotNull { map.markerFor(it.feet.x, it.feet.z, it.yaw) } else emptyList()
        if (patches.isEmpty() && markers == map.shownMarkers) continue
        map.shownMarkers = markers
        val viewers = filter { map.id in it.knownMaps }
        if (viewers.isEmpty()) continue
        val packets = patches.map { world.mapData(map, it) }.ifEmpty { listOf(world.mapData(map, patch = null)) }
        viewers.forEach { it.showMap(packets) }
    }
}

/**
 * What [map] shows: the pixels in [patch], if any, where the players on it
 * are, if it shows them, and the maps it zooms out to.
 */
private fun FlatWorld.mapData(map: MapData, patch: MapPatch?) = ClientboundMapItemData(
    map.id, BlockPos(map.centreX, 0, map.centreZ), map.scale, map.locked, map.shownMarkers.takeIf { map.showsPlayers }, patch, maps.lineage(map),
)

/**
 * Ticks the sky over [game]'s world, around these players, for them to see
 * what it does: the weather changing, lightning striking, with what it did
 * to the blocks it struck and the players near it, snow settling, water freezing, cauldrons
 * filling with rain or snow, and daylight detectors changing. They hear the time once a second. Run once a tick,
 * before redstone, which answers what the sky powered.
 */
internal fun Collection<Session>.tickSky(game: GameData) {
    val world = game.world
    val sky = world.tickSky(filter { it.isInGame }.map { it.feet.toBlockPos().chunk })
    if (sky.weatherChanged) forEach { it.showWeather() }
    val changes = Changes(emptyMap(), sky.changed.distinct(), events = sky.events)
    if (!changes.isEmpty) showAround(world.packetsFor(changes))
    forEach { it.showLightning() }
    for (bolt in sky.strikes) forEach { it.struckNear(bolt.position) }
    if (world.gameTime % TIME_SYNC_TICKS == 0L) forEach { it.showTime() }
}

/**
 * Gives random ticks to the blocks around these players in [game]'s world,
 * for them to see what grows, spreads, dries, decays, melts or weathers.
 * What breaks drops what it drops, if [dropItems], like leaves decaying.
 * Run once a tick.
 */
internal fun Collection<Session>.tickGrowth(game: GameData, dropItems: Boolean) {
    val world = game.world
    val grown = world.tickRandomly(filter { it.isInGame }.map { it.feet.toBlockPos().chunk })
    if (grown.isEmpty) return
    showAround(world.packetsFor(grown))
    grown.broken.forEach { (pos, broken) -> game.dropBroken(pos, broken, harvested = dropItems) }
}

/**
 * Brings what redstone powers in [game]'s world in line with what changed
 * since it last looked, for these players to see if they have the chunks:
 * doors, trapdoors and fence gates swinging, with their sounds, and the
 * states of dispensers, droppers and hoppers. Run once a tick, after the
 * machines, so a dispenser that power reaches waits its four ticks from
 * the next.
 */
internal fun Collection<Session>.tickPower(game: GameData) {
    val changes = game.world.updatePower()
    if (!changes.isEmpty) showAround(game.world.packetsFor(changes))
}

/** What players see and hear of a dispenser or dropper firing: a click, and smoke out of its front, or a higher click with nothing to fire. */
private fun firing(fired: Fired): List<Pair<BlockPos, ServerPacket>> {
    val at = fired.pos.centre
    if (!fired.dispensed) return listOf(fired.pos to LevelEvent(LevelEvent.SOUND_CLICK_FAIL, at, CLICK_FAIL_PITCH))
    return listOf(fired.pos to LevelEvent(LevelEvent.SOUND_CLICK, at, CLICK_PITCH), fired.pos to LevelEvent(LevelEvent.PARTICLES_SHOOT, at, smokeDirection(fired.facing)))
}

/** What everyone around the jukebox at [pos] is told as a song starts: what's playing. Null if it has no disc. */
private fun FlatWorld.nowPlaying(pos: BlockPos): Pair<BlockPos, ServerPacket>? =
    songTitleIn(pos)?.let { pos to Text(Text.JUKEBOX_POPUP, NOW_PLAYING, parameters = listOf(it), needsTranslation = true) }

/**
 * What players see of [changes]: blocks going, with the particles and
 * sound of their breaking, and what took their place, like water, blocks
 * changing shape, with any data they keep, the sounds blocks made, and
 * the data blocks keep that changed by itself, like a piston's arm moving
 * out, the notes note blocks played, and the songs jukeboxes started or
 * stopped.
 */
internal fun FlatWorld.packetsFor(changes: Changes): List<Pair<BlockPos, ServerPacket>> =
    changes.broken.flatMap { (pos, broken) ->
        withData(pos) + (pos to LevelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos.centre, broken.state.networkId))
    } + changes.reshaped.flatMap(::withData) + changes.sounds.map { it.pos to LevelSoundEvent(it.sound, it.pos.centre, it.data) } +
        changes.events.map { it.pos to LevelEvent(it.event, if (it.centred) it.pos.centre else it.pos.corner, it.data) } +
        changes.data.mapNotNull { pos -> blockEntityAt(pos)?.let { pos to BlockActorData(pos, it) } } +
        changes.notes.flatMap { played ->
            val (pos, instrument, note) = played
            // The note sounds, and rises out of the block in its colour, but a mob's sound shows no note.
            listOfNotNull(pos to LevelSoundEvent(NOTE_SOUND, pos.centre, instrument shl 8 or note), (pos to BlockEvent(pos, instrument, note)).takeIf { played.tuned })
        } +
        changes.songs.flatMap(::songPackets)

/**
 * What players hear of a jukebox's song starting, [change], played by its
 * handle and tied to the jukebox, as a jukebox's songs play since 26.50, so
 * they can be told to stop it by the handle. Stopping it is the handle alone.
 */
private fun songPackets(change: SongChange): List<Pair<BlockPos, ServerPacket>> {
    val (pos, handle, song) = change
    if (song == null) return listOf(pos to UpdateSoundData(handle))
    return listOf(pos to PlaySound(song, pos.centre, JUKEBOX_VOLUME, 1f, handle), pos to RecordStarted(pos, handle))
}

/**
 * The block at [pos] as it is now, with the water inside it, if there is
 * any or was lately, followed by the data it keeps, if any.
 */
private fun FlatWorld.withData(pos: BlockPos): List<Pair<BlockPos, ServerPacket>> {
    val inside = liquidAt(pos)?.networkId ?: AIR_NETWORK_ID
    return listOfNotNull(
        // The client draws a cauldron's water afresh only as its block changes, so a cauldron changes twice, to show its colour.
        cauldronNudge(pos)?.let { pos to UpdateBlock(pos, it) },
        pos to UpdateBlock(pos, blockAt(pos)),
        (pos to UpdateBlock(pos, inside, layer = 1)).takeIf { inside != AIR_NETWORK_ID || pos in liquidChanges },
        blockEntityAt(pos)?.let { pos to BlockActorData(pos, it) },
    )
}

/** Packet names, with a run of the same packet counted rather than repeated: `LevelChunk x16`. */
private fun names(packets: Array<out ServerPacket>): String {
    val runs = mutableListOf<Pair<String, Int>>()
    for (packet in packets) {
        val name = packet::class.simpleName.orEmpty()
        val last = runs.lastOrNull()
        if (last != null && last.first == name) runs[runs.lastIndex] = name to last.second + 1 else runs += name to 1
    }
    return runs.joinToString { (name, count) -> if (count == 1) name else "$name x$count" }
}

/**
 * Every connected player's session, driven by the transport's connection
 * callbacks on the UDP event loop. The game ticks on the same thread, 20
 * times a second: items move, players pick up those in reach, then each
 * player sees what changed. The commands players run are shared, and the
 * console runs them too. Every minute, and as the server stops, the world
 * and its players are saved.
 */
class Sessions(
    private val config: ServerConfig,
    private val game: GameData,
    private val authenticator: LoginAuthenticator,
    /** The thread the sessions run on, which runs what the world does later, too. */
    private val loop: ScheduledExecutorService,
    /** Shuts the server down, once `/stop` has disconnected everyone. */
    private val stop: () -> Unit = {},
    /** Saves the world, besides its players, which the sessions save. */
    private val saveWorld: () -> Unit = {},
) {
    /** How many times the game has ticked, which it saves every [AUTOSAVE_TICKS] of. */
    private var ticks = 0L
    private val sessions = HashMap<NetherNetConnection, Session>()

    /** How many players are in the game, as of the last tick, which the server list and LAN discovery read from their own threads. */
    @Volatile
    var playerCount = 0
        private set
    private val commands = Commands(config, game, { sessions.values }, Operators(config.permissionsFile), shutDown = ::stopAll)

    /** The console runs commands from the middle of the world's spawn. */
    private val console = ConsoleSource(game.world.spawn.let { Vec3(it.x + 0.5f, it.y.toFloat(), it.z + 0.5f) })

    init {
        loop.scheduleAtFixedRate({ runCatching(::tick).onFailure { log.error("A tick failed", it) } }, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS)
    }

    private fun tick() {
        if (++ticks % AUTOSAVE_TICKS == 0L) save()
        playerCount = sessions.values.count { it.isInGame }
        sessions.values.tickBlocks(game, dropItems = config.gameMode != GameMode.CREATIVE)
        // Items burning in lava fizz, for everyone around to hear.
        sessions.values.showAround(game.world.tickItems().map { it.toBlockPos() to LevelSoundEvent(FIZZ, it, -1) })
        sessions.values.tickMachines(game)
        sessions.values.tickSky(game)
        sessions.values.tickGrowth(game, dropItems = config.gameMode != GameMode.CREATIVE)
        sessions.values.tickPower(game)
        sessions.values.forEach { it.tickHealth() }
        sessions.values.forEach { it.tickEffects() }
        sessions.values.tickBeacons(game)
        game.world.tickOrbs(sessions.values.mapNotNull { it.orbTarget })
        sessions.values.flyProjectiles(game)
        sessions.values.forEach { it.collectItems() }
        sessions.values.forEach { it.collectProjectiles() }
        sessions.values.forEach { it.collectOrbs() }
        sessions.values.forEach { it.syncItems() }
        sessions.values.forEach { it.syncOrbs() }
        sessions.values.forEach { it.syncProjectiles() }
        sessions.values.forEach { it.syncWindow() }
        sessions.values.forEach { it.syncPlayers() }
        sessions.values.forEach { it.drawHeldMaps() }
        sessions.values.syncMaps(game.world)
        // Everyone's been shown where the water inside blocks changed.
        game.world.liquidChanges.clear()
    }

    fun connect(connection: NetherNetConnection) {
        sessions[connection] = Session(
            config, game, connection.networkId, game.world.newEntityId(), connection.clientKey, authenticator,
            viewers = { sessions.values },
            // A task that throws would otherwise fail quietly, in a future nobody looks at.
            schedule = { ticks, task -> loop.schedule({ runCatching(task).onFailure { log.error("A scheduled task failed", it) } }, ticks * TICK_MILLIS, TimeUnit.MILLISECONDS) },
            commands = commands,
        ) { message ->
            connection.send(message)
        }
    }

    fun receive(connection: NetherNetConnection, message: ByteArray) {
        sessions[connection]?.receive(message)
    }

    fun disconnect(connection: NetherNetConnection, reason: DisconnectReason) {
        sessions.remove(connection)?.leave()
        log.debug("Session for network {} ended: {}", connection.networkId, reason)
    }

    /** Runs [line], typed at the console, on the sessions' thread, and shows what it came to. */
    fun runConsole(line: String) {
        loop.execute {
            runCatching { logOutput(commands.run(console, line)) }.onFailure { log.error("The console's command failed: {}", line, it) }
        }
    }

    /** Disconnects everyone, as the server is shutting down, then shuts it down, once the command that asked has finished. */
    private fun stopAll() {
        shutDown()
        loop.execute(stop)
    }

    /**
     * Saves the world and every player in it, then disconnects everyone,
     * with the shutdown message. Run on the sessions' thread as the server
     * stops, however it does.
     */
    fun shutDown() {
        save()
        sessions.values.forEach { it.disconnect(Disconnect.Reason.SHUTDOWN, config.shutdownMessage) }
    }

    /** Saves the world, and every player in it. Run on the sessions' thread. */
    fun save() {
        runCatching {
            sessions.values.forEach(Session::savePlayer)
            saveWorld()
        }.onFailure { log.error("Couldn't save the world", it) }
    }
}
