package dev.litemfinder.neoforge.capture;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaMenuSlotPartitionerTest {

    private final VanillaMenuSlotPartitioner partitions = new VanillaMenuSlotPartitioner(true);

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void chestAndShulkerPartitionsExcludeAllPlayerSlots() {
        Inventory player = new Inventory(null);

        MenuPartition chest = partitions.partition(ChestMenu.threeRows(1, player), player);
        MenuPartition shulker = partitions.partition(new ShulkerBoxMenu(2, player), player);

        assertEquals(IntStream.range(0, 27).boxed().toList(), containerSlots(chest));
        assertEquals(IntStream.range(0, 27).boxed().toList(), containerSlots(shulker));
        assertTrue(chest.slots().stream().allMatch(slot -> slot.menuSlot() < 27));
        assertTrue(shulker.slots().stream().allMatch(slot -> slot.menuSlot() < 27));
    }

    @Test
    void playerInventoryExcludesCraftingAndResultSlots() {
        Inventory player = new Inventory(null);

        MenuPartition inventory = partitions.partition(new InventoryMenu(player, true, null), player);

        assertEquals(IntStream.range(0, 41).boxed().toList(), containerSlots(inventory));
        assertEquals(41, inventory.slots().size());
    }

    @Test
    void semanticMenusStayUnsupportedUntilTheyHaveDedicatedPolicy() {
        Inventory player = new Inventory(null);

        MenuPartition hopper = partitions.partition(new HopperMenu(1, player), player);

        assertFalse(hopper.supported());
        assertTrue(hopper.kind().startsWith("unsupported_menu:"));
    }

    @Test
    void playerInventoryCanBeDisabledWithoutAffectingOtherMenus() {
        Inventory player = new Inventory(null);
        VanillaMenuSlotPartitioner disabled = new VanillaMenuSlotPartitioner(false);

        assertFalse(disabled.partition(new InventoryMenu(player, true, null), player).supported());
        assertTrue(disabled.partition(ChestMenu.threeRows(1, player), player).supported());
    }

    private static java.util.List<Integer> containerSlots(MenuPartition partition) {
        return partition.slots().stream().map(MenuSlotRef::containerSlot).toList();
    }
}
