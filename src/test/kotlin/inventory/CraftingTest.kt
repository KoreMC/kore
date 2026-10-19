package org.koremc.inventory

import org.cloudburstmc.nbt.NbtMap
import org.koremc.protocol.ContainerName
import org.koremc.protocol.SlotRef
import org.koremc.protocol.StackAction
import org.koremc.protocol.StackRequest
import org.koremc.registry.CRAFTING_TABLE
import org.koremc.registry.CreativeInventory
import org.koremc.registry.Ingredient
import org.koremc.registry.ItemDefinition
import org.koremc.registry.ItemStack
import org.koremc.registry.ItemVersion
import org.koremc.registry.Recipes
import org.koremc.registry.ShapedRecipe
import org.koremc.registry.ShapelessRecipe
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CraftingTest {
    private fun item(name: String, id: Int) = ItemDefinition("minecraft:$name", id, componentBased = false, ItemVersion.NONE)

    private val oakLog = item("oak_log", 17)
    private val birchLog = item("birch_log", 18)
    private val planks = item("oak_planks", 5)
    private val stick = item("stick", 323)
    private val table = item("crafting_table", 58)
    private val chest = item("chest", 54)
    private val milk = item("milk_bucket", 400)
    private val bucket = item("bucket", 401)
    private val cake = item("cake", 402)
    private val whiteBox = item("white_shulker_box", 218)
    private val blackBox = item("black_shulker_box", 219)
    private val inkSac = item("ink_sac", 416)
    private val ironIngot = item("iron_ingot", 265)

    private val logs = Ingredient.Tag("minecraft:logs", 1)
    private val plank = Ingredient.Tag("minecraft:planks", 1)

    private fun shaped(id: Int, width: Int, inputs: List<Ingredient?>, output: ItemStack, block: String = CRAFTING_TABLE, symmetric: Boolean = true) =
        ShapedRecipe("recipe_$id", width, inputs.size / width, inputs, listOf(output), block, 0, symmetric, UUID(0, id.toLong()), id)

    private val recipes = Recipes(
        listOf(
            shaped(1, 1, listOf(logs), ItemStack(planks, 4)),
            shaped(2, 1, listOf(plank, plank), ItemStack(stick, 4)),
            shaped(3, 2, listOf(plank, plank, plank, plank), ItemStack(table)),
            shaped(4, 3, listOf(plank, plank, plank, plank, null, plank, plank, plank, plank), ItemStack(chest)),
            // A log beside a plank, which can only go one way round.
            shaped(5, 2, listOf(logs, plank), ItemStack(stick), symmetric = false),
            ShapelessRecipe("cake", listOf(Ingredient.Item("minecraft:milk_bucket", null, 1), Ingredient.Item("minecraft:oak_planks", null, 1)), listOf(ItemStack(cake), ItemStack(bucket)), CRAFTING_TABLE, 0, UUID(0, 6), keepsData = false, 6),
            ShapelessRecipe("dye_box", listOf(Ingredient.Item("minecraft:white_shulker_box", null, 1), Ingredient.Item("minecraft:ink_sac", null, 1)), listOf(ItemStack(blackBox)), CRAFTING_TABLE, 0, UUID(0, 7), keepsData = true, 7),
            ShapelessRecipe("smelt", listOf(Ingredient.Item("minecraft:oak_log", null, 1)), listOf(ItemStack(ironIngot)), "furnace", 0, UUID(0, 8), keepsData = false, 8),
        ),
        potionMixes = emptyList(),
        containerMixes = emptyList(),
        tags = mapOf("minecraft:logs" to setOf(oakLog.name, birchLog.name), "minecraft:planks" to setOf(planks.name)),
    )

    private val inventory = PlayerInventory()
    private val handler = StackRequestHandler(inventory, CreativeInventory(emptyList(), emptyList()), creativeMode = { false }, onDrop = {}, recipes = recipes)

    /** Lays [stack] in the crafting grid's slot [slot]. */
    private fun grid(slot: Int, stack: ItemStack) {
        inventory.ui[slot] = SlotStack(stack, inventory.newStackId())
    }

    /** Crafts recipe [recipe] from the grid [times] over, taking what it makes to the hotbar's slot [to]. */
    private fun craft(recipe: Int, count: Int, times: Int = 1, to: Int = 0, requestId: Int = -1) = handler.handle(
        StackRequest(
            requestId,
            listOf(
                StackAction.Craft(recipe, times),
                StackAction.Consume(1, SlotRef(ContainerName.CRAFTING_INPUT, 28, 0)), // Kore works out what the grid uses itself
                StackAction.Informational(18),
                StackAction.Transfer(count, createdOutput(requestId), SlotRef(ContainerName.HOTBAR, to, 0)),
            ),
        ),
    )

    @Test
    fun `a recipe laid out in the crafting grid makes what it makes, using up what it takes`() {
        grid(30, ItemStack(birchLog, 3))

        val response = craft(1, count = 4)

        assertTrue(response.accepted)
        assertEquals(ItemStack(planks, 4), inventory.items[0]?.stack)
        assertEquals(ItemStack(birchLog, 2), inventory.ui[30]?.stack)
        // The client hears what's left in the grid slot, where it went, and the created output emptied.
        val containers = response.containers.associate { it.container to it.slots.map { slot -> slot.slot to slot.count } }
        assertEquals(listOf(30 to 2), containers[ContainerName.CRAFTING_INPUT])
        assertEquals(listOf(0 to 4), containers[ContainerName.HOTBAR])
    }

    @Test
    fun `a shaped recipe can go anywhere in the grid, with nothing beside it`() {
        grid(29, ItemStack(planks))
        grid(31, ItemStack(planks))
        assertTrue(craft(2, count = 4).accepted)
        assertEquals(ItemStack(stick, 4), inventory.items[0]?.stack)

        // Side by side isn't one over the other.
        grid(28, ItemStack(planks))
        grid(29, ItemStack(planks))
        assertFalse(craft(2, count = 4, requestId = -3).accepted)
        // And something else in the grid spoils it.
        grid(30, ItemStack(planks))
        grid(31, ItemStack(stick))
        inventory.ui[29] = null
        assertFalse(craft(2, count = 4, requestId = -5).accepted)
    }

    @Test
    fun `a recipe three wide needs a crafting table's grid`() {
        for (slot in LARGE_GRID) if (slot != LARGE_GRID.first + 4) grid(slot, ItemStack(planks))
        assertFalse(craft(4, count = 1).accepted)

        inventory.workstation = Workstation.CRAFTING_TABLE
        assertTrue(craft(4, count = 1, requestId = -3).accepted)
        assertEquals(ItemStack(chest), inventory.items[0]?.stack)
        assertTrue(LARGE_GRID.all { inventory.ui[it] == null })
    }

    @Test
    fun `a pattern mirrors only if its recipe allows`() {
        grid(28, ItemStack(oakLog))
        grid(29, ItemStack(planks))
        assertTrue(craft(5, count = 1).accepted)

        grid(28, ItemStack(planks))
        grid(29, ItemStack(oakLog))
        assertFalse(craft(5, count = 1, requestId = -3).accepted)
    }

    @Test
    fun `crafting several times over uses up as much more`() {
        grid(28, ItemStack(planks, 3))
        grid(30, ItemStack(planks, 5))

        assertTrue(craft(2, count = 12, times = 3).accepted)
        assertEquals(ItemStack(stick, 12), inventory.items[0]?.stack)
        assertNull(inventory.ui[28])
        assertEquals(2, inventory.ui[30]?.stack?.count)
    }

    @Test
    fun `the recipe book crafts from the inventory, using up what the client names, which must be what the recipe takes`() {
        inventory.items[5] = SlotStack(ItemStack(oakLog, 2), inventory.newStackId())
        val log = inventory.items[5]!!.id
        val request = { id: Int, from: Int, stackId: Int ->
            StackRequest(
                id,
                listOf(
                    StackAction.Craft(1, 1, auto = true),
                    StackAction.Consume(1, SlotRef(ContainerName.HOTBAR_AND_INVENTORY, from, stackId)),
                    StackAction.Transfer(4, createdOutput(id), SlotRef(ContainerName.HOTBAR, 0, 0)),
                ),
            )
        }

        assertTrue(handler.handle(request(-1, 5, log)).accepted)
        assertEquals(ItemStack(planks, 4), inventory.items[0]?.stack)
        assertEquals(1, inventory.items[5]?.stack?.count)

        // Sticks aren't logs.
        inventory.items[6] = SlotStack(ItemStack(stick), inventory.newStackId())
        assertFalse(handler.handle(request(-3, 6, inventory.items[6]!!.id)).accepted)
        assertEquals(1, inventory.items[6]?.stack?.count)
    }

    @Test
    fun `a recipe that makes two things creates each when the client asks`() {
        grid(28, ItemStack(milk))
        grid(29, ItemStack(planks))

        val response = handler.handle(
            StackRequest(
                -1,
                listOf(
                    StackAction.Craft(6, 1),
                    StackAction.Create(0),
                    StackAction.Transfer(1, createdOutput(-1), SlotRef(ContainerName.HOTBAR, 0, 0)),
                    StackAction.Create(1),
                    StackAction.Transfer(1, createdOutput(-1), SlotRef(ContainerName.HOTBAR, 1, 0)),
                ),
            ),
        )

        assertTrue(response.accepted)
        assertEquals(ItemStack(cake), inventory.items[0]?.stack)
        assertEquals(ItemStack(bucket), inventory.items[1]?.stack)
    }

    @Test
    fun `dyeing a shulker box keeps what it holds`() {
        val held = NbtMap.builder().putString("Items", "some").build()
        grid(28, ItemStack(whiteBox, nbt = held))
        grid(31, ItemStack(inkSac))

        assertTrue(craft(7, count = 1).accepted)
        assertEquals(ItemStack(blackBox, nbt = held), inventory.items[0]?.stack)
    }

    @Test
    fun `recipes made elsewhere, like in a furnace, aren't crafted`() {
        grid(28, ItemStack(oakLog))

        assertFalse(craft(8, count = 1).accepted)
        assertEquals(ItemStack(oakLog), inventory.ui[28]?.stack)
    }

    private fun createdOutput(stackId: Int) = SlotRef(ContainerName.CREATED_OUTPUT, CREATED_OUTPUT_SLOT, stackId)
}
