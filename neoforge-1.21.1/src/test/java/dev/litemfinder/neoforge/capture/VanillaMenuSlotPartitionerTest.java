package dev.litemfinder.neoforge.capture;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void persistentMachineMenusUseOnlyTheirStorageSlots() {
        Inventory player = testPlayerInventory();

        assertPartition(new FurnaceMenu(1, player), player, 3, "furnace_like");
        assertPartition(new BlastFurnaceMenu(2, player), player, 3, "furnace_like");
        assertPartition(new SmokerMenu(3, player), player, 3, "furnace_like");
        assertPartition(new HopperMenu(4, player), player, 5, "hopper");
        assertPartition(new BrewingStandMenu(5, player), player, 5, "brewing_stand");
        assertPartition(new DispenserMenu(6, player), player, 9, "dispenser_like");
        assertPartition(new CrafterMenu(7, player), player, 9, "crafter");
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

    private void assertPartition(
            net.minecraft.world.inventory.AbstractContainerMenu menu,
            Inventory player,
            int expectedSlots,
            String expectedKind
    ) {
        MenuPartition partition = partitions.partition(menu, player);

        assertTrue(partition.supported());
        assertEquals(expectedKind, partition.kind());
        assertEquals(IntStream.range(0, expectedSlots).boxed().toList(), containerSlots(partition));
        assertTrue(partition.slots().stream().allMatch(slot -> slot.menuSlot() < expectedSlots));
    }

    private static Inventory testPlayerInventory() {
        Player player = mock(Player.class);
        Level level = mock(Level.class);
        when(level.potionBrewing()).thenReturn(mock(PotionBrewing.class));
        when(player.level()).thenReturn(level);
        return new Inventory(player);
    }
}
