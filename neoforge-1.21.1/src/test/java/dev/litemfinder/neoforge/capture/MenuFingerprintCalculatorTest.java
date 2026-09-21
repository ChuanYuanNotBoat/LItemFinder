package dev.litemfinder.neoforge.capture;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MenuFingerprintCalculatorTest {

    private final VanillaMenuSlotPartitioner partitions = new VanillaMenuSlotPartitioner(true);
    private final MenuFingerprintCalculator fingerprints = new MenuFingerprintCalculator();

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void playerInventoryChangesDoNotAffectChestFingerprint() {
        Inventory player = new Inventory(null);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(1, player, chest);
        MenuPartition partition = partitions.partition(menu, player);
        MenuFingerprint before = fingerprints.calculate(menu, partition);

        player.setItem(0, new ItemStack(Items.DIAMOND, 5));

        assertEquals(before, fingerprints.calculate(menu, partition));
    }

    @Test
    void rootItemAndCountChangesAffectFingerprint() {
        Inventory player = new Inventory(null);
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(1, player, chest);
        MenuPartition partition = partitions.partition(menu, player);
        MenuFingerprint empty = fingerprints.calculate(menu, partition);

        chest.setItem(0, new ItemStack(Items.DIAMOND, 1));
        MenuFingerprint one = fingerprints.calculate(menu, partition);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 2));
        MenuFingerprint two = fingerprints.calculate(menu, partition);

        assertNotEquals(empty, one);
        assertNotEquals(one, two);
        assertEquals(1, one.occupiedSlots());
        assertEquals(27, one.slotCount());
    }
}
