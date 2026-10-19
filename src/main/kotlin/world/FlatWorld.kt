package org.koremc.world

import org.cloudburstmc.nbt.NbtMap
import org.koremc.inventory.Container
import org.koremc.protocol.Difficulty
import org.koremc.registry.AIR
import org.koremc.registry.AIR_NETWORK_ID
import org.koremc.registry.BlockPalette
import org.koremc.registry.BlockState
import org.koremc.registry.BlockTraits
import org.koremc.registry.Fuel
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemStacks
import org.koremc.registry.Recipes
import java.util.TreeMap

/**
 * Chunks name biomes by number, and Cloudburst's data has no numbers for
 * vanilla biomes. So plains is hard-coded, as 1: the ID other servers send
 * it with.
 */
const val PLAINS_BIOME_ID = 1

/**
 * A world that starts out the same in every column: [layers] of blocks
 * from the bottom of the world up, air above them, and one biome
 * throughout. Until a player changes a block in a chunk, the chunk is that
 * same column, so the world holds just one of those, plus each chunk that
 * players have changed, or that [loader] had saved, which it asks for the
 * first time anything looks at the chunk. The items lying in it stay until
 * they're picked up or go.
 *
 * Like the sessions that change it, it runs on the UDP event loop.
 */
class FlatWorld(
    /** Every block state the world can hold, by network ID. */
    val blocks: BlockPalette,
    layers: List<BlockState>,
    /** The one biome the world is in, by number. */
    val biome: Int = PLAINS_BIOME_ID,
    private val loader: ChunkLoader = ChunkLoader.NONE,
    /** Where players appear: by default standing on the top layer, in the corner of chunk 0, 0. `/setworldspawn` moves it. */
    var spawn: BlockPos = BlockPos(0, MIN_Y + layers.size, 0),
    /** What the game says of each block state, like what liquid does to it. */
    val traits: BlockTraits = BlockTraits.NONE,
    /** The climate of [biome]: how warm it is, what falls from the sky when it rains, and how deep snow lies. */
    climate: Climate = Climate.PLAINS,
    /** The world's seed, which lays out what isn't the same in every column. */
    private val seed: Long = 0,
) {

    private val generated: Chunk

    /** The top of the layers, if it's snow lying unevenly, as the game makes it in a snowy biome, rather than the same in every column. */
    private val drifting: BlockState? = layers.lastOrNull()?.takeIf { it.name == SNOW_LAYER && climate.snowCover > 0 }
    private val snowCover = climate.snowCover
    private val driftY = MIN_Y + layers.size - 1

    /** The chunks made so far that aren't the same as every other, by where they are: those with snow drifting on them. */
    private val made = HashMap<ChunkPos, Chunk>()

    /** The chunks that don't match [generated]: those players changed, and those [loader] had saved. */
    private val changed = HashMap<ChunkPos, Chunk>()

    /** The chunks [loader] has been asked for, each only ever once. */
    private val asked = HashSet<ChunkPos>()

    /** The chunks whose blocks changed since the world was last saved. */
    internal val unsaved = HashSet<ChunkPos>()

    /** Every chunk that doesn't match [generated], by where it is. */
    internal val chunks: Map<ChunkPos, Chunk> get() = changed

    /** How many times the world's blocks have ticked, which updates are scheduled by. */
    internal var blockTicks = 0L

    /** Blocks to look at again, by the tick to, and what for. */
    internal val scheduled = TreeMap<Long, LinkedHashSet<Pair<BlockPos, BlockUpdate>>>()

    /** Whether any block is waiting to be looked at again. */
    val hasScheduledUpdates get() = scheduled.isNotEmpty()

    /** The block entities in each chunk that has any, by where they are. */
    private val blockEntities = HashMap<ChunkPos, HashMap<BlockPos, NbtMap>>()

    private val itemEntities = LinkedHashMap<Long, ItemEntity>()
    private val orbEntities = LinkedHashMap<Long, ExperienceOrb>()
    private val fallingEntities = LinkedHashMap<Long, FallingBlock>()
    private val projectileEntities = LinkedHashMap<Long, Projectile>()
    private var nextEntityId = 1L

    /** The items lying in the world, oldest first. */
    val items: Collection<ItemEntity> get() = itemEntities.values

    /** The experience orbs in the world, oldest first. */
    val orbs: Collection<ExperienceOrb> get() = orbEntities.values

    /** The blocks falling, oldest first. */
    val fallingBlocks: Collection<FallingBlock> get() = fallingEntities.values

    /** What's been thrown and is still flying, oldest first. */
    val projectiles: Collection<Projectile> get() = projectileEntities.values

    /**
     * Where blocks, or the water inside them, changed since the blocks
     * around them were last looked at: one above may have nothing under it
     * now and fall, and liquid beside one may flow.
     */
    internal val blockChanges = LinkedHashSet<BlockPos>()

    /** The liquid due to move, so that none is due twice at once. */
    internal val flowDue = HashSet<BlockPos>()

    /** Where the water inside blocks changed lately, which players are shown as well as the blocks. */
    internal val liquidChanges = LinkedHashSet<BlockPos>()

    /** The blocks giving off light in each chunk light was asked about since a block in it last changed. */
    internal val lightSources = HashMap<ChunkPos, List<LightSource>>()

    /** For each chunk light was asked about since a block in it last changed, how far down the sky's light falls whole in each column. */
    internal val skyTops = HashMap<ChunkPos, IntArray>()

    /** The network IDs of the blocks a random tick does something to, once they've been asked for. */
    internal var randomTicking: Set<Int>? = null

    init {
        require(layers.size < SUB_CHUNK_COUNT * 16) { "${layers.size} layers leave no room to stand in the overworld" }

        // Every sub-chunk the layers reach. A block's y within its sub-chunk is the low four bits of its index.
        val subChunks = (0 until (layers.size + 15) / 16).map { subChunk ->
            PalettedStorage.of(IntArray(SUB_CHUNK_VOLUME) { block -> layers.getOrNull(subChunk * 16 + (block and 0xF))?.networkId ?: AIR_NETWORK_ID })
        }
        // The same storage all the way up, which goes out as one storage and then a byte for each repeat.
        val biomes = PalettedStorage.filled(biome)
        generated = Chunk(subChunks, List(SUB_CHUNK_COUNT) { biomes })
    }

    fun chunkAt(pos: ChunkPos): Chunk = changed[pos] ?: load(pos)

    /**
     * The chunk at [pos] as [loader] had it, the first time it's asked for,
     * with what it keeps besides its blocks put back once it's in, or the
     * generated chunk if the loader has none.
     */
    private fun load(pos: ChunkPos): Chunk {
        if (!asked.add(pos)) return generate(pos)
        val loaded = loader.load(pos) ?: return generate(pos)
        loaded.chunk?.let { changed[pos] = it }
        loaded.restore(this)
        return changed[pos] ?: generate(pos)
    }

    /**
     * The chunk at [pos] as the world makes it: the same as every other, but
     * for snow on top drifting, from bare ground to as deep as the biome
     * makes it, as the world's seed lays it out.
     */
    private fun generate(pos: ChunkPos): Chunk {
        val snow = drifting ?: return generated
        return made.getOrPut(pos) {
            val subChunk = (driftY - MIN_Y) shr 4
            val top = generated.subChunks[subChunk]
            val ids = IntArray(SUB_CHUNK_VOLUME) { top[it] }
            val depths = (1..snowCover).map { blocks.find(SNOW_LAYER, snow.properties + ("height" to it - 1))?.networkId ?: snow.networkId }
            for (x in 0 until 16) for (z in 0 until 16) {
                val depth = snowDepth(seed, pos.x * 16 + x, pos.z * 16 + z, snowCover)
                ids[blockIndex(x, driftY and 0xF, z)] = if (depth == 0) AIR_NETWORK_ID else depths[depth - 1]
            }
            Chunk(generated.subChunks.toMutableList().apply { set(subChunk, PalettedStorage.of(ids)) }, generated.biomes)
        }
    }

    /** The network ID of the block at [pos]. */
    fun blockAt(pos: BlockPos): Int = pending?.get(pos.chunk)?.get(pos) ?: chunkAt(pos.chunk).blockAt(pos)

    /** The blocks set while [inBulk] runs, by chunk, waiting to go into their chunks together. */
    private var pending: HashMap<ChunkPos, HashMap<BlockPos, Int>>? = null

    /**
     * Runs [edit], which sets many blocks, putting them into their chunks
     * together once it's done, each sub-chunk built afresh once rather
     * than for every block, as is far quicker for a command's thousands.
     * Within it, blocks read as they've been set.
     */
    internal fun <T> inBulk(edit: () -> T): T {
        if (pending != null) return edit()
        val bulk = HashMap<ChunkPos, HashMap<BlockPos, Int>>()
        pending = bulk
        try {
            return edit()
        } finally {
            pending = null
            for ((chunk, blocks) in bulk) changed[chunk] = chunkAt(chunk).with(blocks)
        }
    }

    /** The block at [pos], or null if it's one [blocks] doesn't have. */
    fun stateAt(pos: BlockPos): BlockState? = blocks[blockAt(pos)]

    /** An ID for a new entity, unique among players and items alike. */
    fun newEntityId() = nextEntityId++

    private var nextStackId = 1

    /** An ID for a new stack of items, unique among every player's and every container's, which clients name stacks by. */
    fun newStackId() = nextStackId++

    /** Puts [stack] in the world at [position], moving at [velocity], for anyone to pick up after [pickupDelay] ticks. */
    fun spawnItem(stack: ItemStack, position: Vec3, velocity: Vec3, pickupDelay: Int): ItemEntity =
        ItemEntity(newEntityId(), stack, position, velocity, pickupDelay).also { itemEntities[it.id] = it }

    /** The item [id], or null if it's gone. */
    fun itemWith(id: Long): ItemEntity? = itemEntities[id]

    fun removeItem(id: Long) {
        itemEntities.remove(id)
    }

    /** Puts an orb worth [value] experience in the world at [position], moving at [velocity]. */
    fun spawnOrb(value: Int, position: Vec3, velocity: Vec3): ExperienceOrb =
        ExperienceOrb(newEntityId(), value, position, velocity).also { orbEntities[it.id] = it }

    /** The orb [id], or null if it's gone. */
    fun orbWith(id: Long): ExperienceOrb? = orbEntities[id]

    fun removeOrb(id: Long) {
        orbEntities.remove(id)
    }

    /** Sets [state] falling from [position], having started at the height [fromY], moving at [velocity]. */
    fun spawnFallingBlock(state: BlockState, position: Vec3, fromY: Int, velocity: Vec3 = Vec3(0f, 0f, 0f)): FallingBlock =
        FallingBlock(newEntityId(), state, position, fromY, velocity).also { fallingEntities[it.id] = it }

    fun removeFallingBlock(id: Long) {
        fallingEntities.remove(id)
    }

    /** Sets [kind] flying from [position], thrown or shot by the entity [thrower], moving at [velocity], and for an arrow, shot with [arrow]. */
    fun spawnProjectile(kind: ProjectileKind, thrower: Long, position: Vec3, velocity: Vec3, arrow: ArrowShot? = null): Projectile =
        Projectile(newEntityId(), kind, thrower, position, velocity, arrow).also { projectileEntities[it.id] = it }

    fun removeProjectile(id: Long) {
        projectileEntities.remove(id)
    }

    /** What's inside the block at [pos], in the second layer: the water in a fence, say, or null for nothing. */
    fun liquidAt(pos: BlockPos): BlockState? = chunkAt(pos.chunk).liquidAt(pos).takeIf { it != AIR_NETWORK_ID }?.let { blocks[it] }

    /** Puts [liquid] inside the block at [pos], or takes what's there out, for null. */
    fun setLiquid(pos: BlockPos, liquid: BlockState?) {
        require(pos.y in MIN_Y..MAX_Y) { "$pos is outside the overworld" }
        changed[pos.chunk] = chunkAt(pos.chunk).withLiquid(pos, liquid?.networkId ?: AIR_NETWORK_ID)
        unsaved += pos.chunk
        blockChanges += pos
        liquidChanges += pos
        lightChanged(pos.chunk)
    }

    /** Forgets what was worked out about the light in the chunk at [chunk], a block in it having changed. */
    private fun lightChanged(chunk: ChunkPos) {
        lightSources -= chunk
        skyTops -= chunk
    }

    /**
     * Puts [state] at [pos], which must be within the overworld's height.
     * Putting a block of another kind there forgets the block entity the
     * old one had. Water inside the old block stays where it is: in the
     * new one, if it can hold water, and as water by itself if the new one
     * is air. A block that can hold water put in a water source takes the
     * water in.
     */
    operator fun set(pos: BlockPos, state: BlockState) {
        require(pos.y in MIN_Y..MAX_Y) { "$pos is outside the overworld" }
        val inside = liquidAt(pos)
        if (inside != null && state.name == AIR) {
            setLiquid(pos, null)
            return set(pos, liquidState(inside.liquid ?: Liquid.WATER, inside.liquidDepth))
        }
        val replaced = stateAt(pos)
        if (inside != null && !holdsWater(state)) setLiquid(pos, null)
        if (inside == null && replaced?.liquid == Liquid.WATER && replaced.liquidDepth == 0 && holdsWater(state)) {
            setLiquid(pos, liquidState(Liquid.WATER, 0, inside = true))
        }
        // A furnace lighting up, or going out, is the same furnace.
        if (replaced?.let(::kindOf) != kindOf(state)) forget(pos)
        val bulk = pending
        if (bulk != null) bulk.getOrPut(pos.chunk) { HashMap() }[pos] = state.networkId else changed[pos.chunk] = chunkAt(pos.chunk).with(pos, state.networkId)
        unsaved += pos.chunk
        lightChanged(pos.chunk)
        // A hopper works from the moment it's there, whether anyone opens it or not.
        if (state.name == HOPPER) machines.getOrPut(pos) { Hopper() }
        if (state.isComparator) comparators += pos else comparators -= pos
        if (state.name == BEACON) beacons.putIfAbsent(pos, 0)
        if (state.isDaylightDetector) daylightDetectors += pos else daylightDetectors -= pos
        if (features.redstone) powerChanges += pos
        blockChanges += pos
    }

    /**
     * Forgets what the block at [pos] kept: its block entity, what it held,
     * a machine's work, and what redstone knew of it, as a block of another
     * kind put there does.
     */
    internal fun forget(pos: BlockPos) {
        blockEntities[pos.chunk]?.remove(pos)
        containers.remove(pos)
        machines.remove(pos)
        poweredOpen.remove(pos)
        torchToggles.remove(pos)
        redstoneDue.remove(pos)
        comparatorPower.remove(pos)
        pistonArms.remove(pos)
        beacons.remove(pos)
        playing.remove(pos)
    }

    /** Where blocks changed since redstone last looked, which may have powered something, or stopped powering it. */
    internal val powerChanges = LinkedHashSet<BlockPos>()

    /**
     * The doors, trapdoors and fence gates that were powered when redstone
     * last looked, a door by its lower half, so that only power reaching
     * them or going opens or shuts them.
     */
    internal val poweredOpen = HashSet<BlockPos>()

    /** When each redstone torch went out lately, by the block ticks, which burn it out if it's too often. */
    internal val torchToggles = HashMap<BlockPos, MutableList<Long>>()

    /** The redstone torches, repeaters and comparators due to look again at the power they take, so that none is due twice at once. */
    internal val redstoneDue = HashSet<BlockPos>()

    /** Every comparator, which redstone looks at every tick, since what it reads changes by itself. */
    internal val comparators = HashSet<BlockPos>()

    /** What each comparator that gives any power gives out of its front, by where it is. */
    internal val comparatorPower = HashMap<BlockPos, Int>()

    /** What was on each pressure plate anything was on, when the plates last looked, by where they are. */
    internal var plateLoads: Map<BlockPos, PlateLoad> = emptyMap()

    /** Every beacon, by where it is, with how many layers of pyramid its beam last shone on, or 0 if it didn't. */
    internal val beacons = HashMap<BlockPos, Int>()

    /** How long the world has run, in ticks, as the game counts it, whatever the time of day. */
    var gameTime = 0L

    /** The time of day, in ticks since the world began, a day being [DAY_LENGTH]; it stands still while the daylight cycle's off. */
    var time = 0L

    /** The world's game rules. */
    var rules = GameRules()

    /** The game's systems that run in this world, as kore.jsonc switches them. */
    var features = Features()

    /** The climate of [biome]: how warm it is, and what falls from the sky when it rains, and how deep snow piles up. */
    var climate = climate

    /** How hard the game is, which fire spreads faster the harder it is, and lightning sets fires at all only at normal or hard. */
    var difficulty = Difficulty.NORMAL

    /** The weather. */
    var weather = Weather()

    /** The lightning bolts striking, by their entity IDs. */
    internal val bolts = LinkedHashMap<Long, LightningBolt>()

    /** The lightning bolts striking, oldest first. */
    val lightning: Collection<LightningBolt> get() = bolts.values

    /** Every daylight detector, which the sky powers. */
    internal val daylightDetectors = HashSet<BlockPos>()

    /** The jukeboxes playing a song, by where they are, with when each song ends and the handle clients know it by. */
    internal val playing = HashMap<BlockPos, PlayingSong>()

    private var nextSoundHandle = 1L

    /** A handle for a new sound, like a jukebox's song, unique in the world, by which clients can be told to stop it. */
    internal fun newSoundHandle() = nextSoundHandle++

    /** The maps players have drawn. */
    val maps = Maps()

    /** The arms of the pistons that have moved, or are moving, by where the pistons are. */
    internal val pistonArms = HashMap<BlockPos, PistonArm>()

    /** The arm of the piston [state] at [pos], in, if it's never moved. */
    internal fun pistonArm(pos: BlockPos, state: BlockState): PistonArm = pistonArms.getOrPut(pos) { PistonArm(state.name == STICKY_PISTON) }

    /**
     * What kind of block [state] is, which a furnace, redstone torch or
     * redstone lamp is whether lit or not, a repeater or comparator
     * whether powered or not, and a daylight detector whichever it senses.
     */
    private fun kindOf(state: BlockState) = state.furnaceKind?.block ?: copperKind(state.name) ?: when (state.name) {
        UNLIT_REDSTONE_TORCH -> REDSTONE_TORCH
        LIT_REDSTONE_LAMP -> REDSTONE_LAMP
        POWERED_REPEATER -> UNPOWERED_REPEATER
        POWERED_COMPARATOR -> UNPOWERED_COMPARATOR
        INVERTED_DAYLIGHT_DETECTOR -> DAYLIGHT_DETECTOR
        else -> state.name
    }

    /** The items each chest, barrel and shulker box keeps, by where it is, once a player has opened it or put something in it. */
    private val containers = HashMap<BlockPos, Container>()

    /** The furnaces, brewing stands, dispensers and droppers, by where they are, once something has used them, and every hopper. */
    private val machines = HashMap<BlockPos, Machine>()

    /** The items each chest, barrel, shulker box and lectern keeps, and every machine, by where they are, for saving. */
    internal val keptContainers: Map<BlockPos, Container> get() = containers
    internal val keptMachines: Map<BlockPos, Machine> get() = machines

    /** Where every block that keeps data besides its state is. */
    internal val blockEntityPositions: List<BlockPos> get() = blockEntities.values.flatMap { it.keys }

    /** How many times the machines have worked, which hoppers go by to keep in step. */
    private var machineTicks = 0L

    /** The items the chest, barrel or shulker box at [pos] keeps. */
    fun containerAt(pos: BlockPos): Container {
        chunkAt(pos.chunk) // Which puts back what a saved chunk's containers held, first
        return containers.getOrPut(pos) { Container(STORAGE_SLOTS) }
    }

    /** The one slot the lectern at [pos] keeps its book in. */
    internal fun lecternAt(pos: BlockPos): Container {
        chunkAt(pos.chunk)
        return containers.getOrPut(pos) { Container(1) }
    }

    /** The furnace at [pos], or null if there isn't one. */
    fun furnaceAt(pos: BlockPos): Furnace? {
        (machines[pos] as? Furnace)?.let { return it }
        val kind = stateAt(pos)?.furnaceKind ?: return null
        return Furnace(kind).also { machines[pos] = it }
    }

    /** The brewing stand at [pos], or null if there isn't one. */
    fun brewingStandAt(pos: BlockPos): BrewingStand? {
        (machines[pos] as? BrewingStand)?.let { return it }
        if (stateAt(pos)?.name != BREWING_STAND) return null
        return BrewingStand().also { machines[pos] = it }
    }

    /** The hopper at [pos], or null if there isn't one. */
    fun hopperAt(pos: BlockPos): Hopper? {
        (machines[pos] as? Hopper)?.let { return it }
        if (stateAt(pos)?.name != HOPPER) return null
        return Hopper().also { machines[pos] = it }
    }

    /** The dispenser or dropper at [pos], or null if there isn't one. */
    fun dispenserAt(pos: BlockPos): Dispenser? {
        (machines[pos] as? Dispenser)?.let { return it }
        val kind = stateAt(pos)?.dispenserKind ?: return null
        return Dispenser(kind).also { machines[pos] = it }
    }

    /**
     * What a block keeps besides its state, which goes with it as a piston
     * moves it: the data of its block entity, its items, and its machine.
     */
    internal class Kept(val blockEntity: NbtMap?, val container: Container?, val machine: Machine?) {
        /** Every item it keeps, slot by slot, taken out. */
        fun empty(): List<ItemStack?> = container?.empty() ?: machine?.slots?.empty().orEmpty()
    }

    /** Takes what the block at [pos] keeps off it, to go with it elsewhere. */
    internal fun lift(pos: BlockPos) = Kept(blockEntities[pos.chunk]?.remove(pos), containers.remove(pos), machines.remove(pos))

    /** Puts [kept] with the block at [pos], once it's there. */
    internal fun lay(pos: BlockPos, kept: Kept) {
        kept.blockEntity?.let { setBlockEntity(pos, it) }
        kept.container?.let { containers[pos] = it }
        kept.machine?.let { machines[pos] = it }
    }

    /** The crafter at [pos], or null if there isn't one. */
    fun crafterAt(pos: BlockPos): Crafter? {
        (machines[pos] as? Crafter)?.let { return it }
        if (stateAt(pos)?.isCrafter != true) return null
        return Crafter().also { machines[pos] = it }
    }

    /** Takes out every item the block at [pos] kept, as it breaks, slot by slot. */
    fun emptyContainer(pos: BlockPos): List<ItemStack?> = (containers.remove(pos)?.empty() ?: (machines[pos])?.slots?.empty()).orEmpty()

    /** Takes out the experience the furnace at [pos] has stored, as it breaks, and forgets the furnace. */
    fun takeExperience(pos: BlockPos): Float = (machines.remove(pos) as? Furnace)?.takeExperience() ?: 0f

    /**
     * Works every furnace, brewing stand, hopper, dispenser, dropper and
     * crafter a tick, by [recipes], burning [fuel]. Returns where blocks
     * changed how they look: a furnace lit or went out, a brewing stand's
     * bottles changed, a crafter crafted, or a hopper filled or emptied a
     * composter. Brewing stands that finished a brew are in [brewed], the
     * dispensers and droppers that fired in [fired], the sounds crafters and
     * composters made in [sounds], and the songs of the jukeboxes hoppers put
     * a disc in, in [songs]. Hoppers take bone meal out of composters made
     * with [items]. With machines switched off, none work.
     */
    fun tickMachines(
        recipes: Recipes,
        fuel: Fuel,
        brewed: MutableList<BlockPos> = mutableListOf(),
        fired: MutableList<Fired> = mutableListOf(),
        sounds: MutableList<BlockSound> = mutableListOf(),
        items: ItemStacks? = null,
        songs: MutableList<SongChange> = mutableListOf(),
    ): List<BlockPos> {
        if (!features.machines) return emptyList()
        val changed = mutableListOf<BlockPos>()
        machineTicks++
        for ((pos, machine) in machines.toList()) {
            val state = stateAt(pos) ?: continue
            when (machine) {
                is Furnace -> if (machine.tick(recipes, fuel, ::newStackId)) {
                    val name = if (machine.lit) machine.kind.litBlock else machine.kind.block
                    blocks.find(name, state.properties)?.let { set(pos, it); changed += pos }
                }
                is BrewingStand -> {
                    if (machine.tick(recipes, ::newStackId)) brewed += pos
                    val bits = BOTTLE_BITS.map { (slot, bit) -> bit to bit(slot in machine.bottlesShown) }.toMap()
                    if (bits.any { (bit, value) -> state.properties[bit] != value }) {
                        blocks.find(state.name, state.properties + bits)?.let { set(pos, it); changed += pos }
                    }
                }
                is Hopper -> tickHopper(pos, machine, machineTicks, recipes, fuel, items, changed, sounds, songs)
                is Dispenser -> if (machine.fireIn > 0 && --machine.fireIn == 0) fire(pos, machine, recipes, fuel, items)?.let(fired::add)
                is Crafter -> if (tickCrafter(pos, machine, state, recipes, fuel, sounds)) changed += pos
            }
        }
        return changed
    }

    /** The data the block at [pos] keeps beyond its state, like a bed's colour, or null for none. */
    fun blockEntityAt(pos: BlockPos): NbtMap? {
        chunkAt(pos.chunk)
        return blockEntities[pos.chunk]?.get(pos)
    }

    /** Gives the block at [pos] [data] to keep, or forgets what it kept, for null. The data names where it is. */
    fun setBlockEntity(pos: BlockPos, data: NbtMap?) {
        if (data == null) {
            blockEntities[pos.chunk]?.remove(pos)
            return
        }
        val located = data.toBuilder().putInt("x", pos.x).putInt("y", pos.y).putInt("z", pos.z).build()
        blockEntities.getOrPut(pos.chunk) { HashMap() }[pos] = located
    }

    /** The block entities in the chunk at [pos], for sending with it. */
    fun blockEntitiesIn(pos: ChunkPos): Collection<NbtMap> {
        chunkAt(pos)
        return blockEntities[pos]?.values.orEmpty()
    }

    companion object {
        /** The vanilla flat world's layers, from the bottom up: bedrock, two layers of dirt and grass on top. */
        fun vanillaLayers(blocks: BlockPalette) = listOf(
            blocks.stateOf("minecraft:bedrock", mapOf("infiniburn_bit" to 0.toByte())),
            blocks.stateOf("minecraft:dirt"),
            blocks.stateOf("minecraft:dirt"),
            blocks.stateOf("minecraft:grass_block"),
        )

        /** The vanilla flat world, all plains, saved nowhere. */
        fun vanilla(blocks: BlockPalette) = FlatWorld(blocks, vanillaLayers(blocks))
    }
}
