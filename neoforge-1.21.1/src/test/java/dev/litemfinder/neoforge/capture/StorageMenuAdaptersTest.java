package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.MenuStorage;
import dev.litemfinder.neoforge.identity.IdentityConfidence;
import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageMenuAdaptersTest {
    @BeforeAll
    static void bootstrapMinecraft() { Bootstrap.bootStrap(); }

    @Test
    void publicBackingContractSupportsBarrelVesselAndMachineLayoutsWithoutAClassWhitelist() {
        for (int size : new int[]{3, 9, 18}) {
            Inventory player = new Inventory(null);
            StorageBlock owner = new StorageBlock(size);
            BlockMenu menu = new BlockMenu(owner, player);
            MenuPartition partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
            assertTrue(partition.supported());
            assertEquals(IntStream.range(0, size).boxed().toList(),
                    partition.slots().stream().map(MenuSlotRef::containerSlot).toList());
            assertTrue(partition.slots().stream().allMatch(slot -> slot.menuSlot() < size));
        }
    }

    @Test
    void registeredAdapterSupportsSeveralHandlersAndDeduplicatesAliasedSlots() throws Exception {
        Inventory player = new Inventory(null);
        PlainMenu menu = new PlainMenu();
        ItemStackHandler first = new ItemStackHandler(2);
        ItemStackHandler second = new ItemStackHandler(2);
        menu.slot(new SlotItemHandler(first, 0, 0, 0));
        menu.slot(new SlotItemHandler(first, 1, 0, 0));
        menu.slot(new SlotItemHandler(second, 0, 0, 0));
        menu.slot(new SlotItemHandler(second, 1, 0, 0));
        menu.slot(new SlotItemHandler(first, 0, 0, 0));
        menu.slot(new Slot(new SimpleContainer(new ItemStack(Items.DIAMOND)), 0, 0, 0));
        menu.slot(new Slot(player, 0, 0, 0));
        try (var registration = StorageMenuAdapters.register(candidate -> candidate == menu
                ? Optional.of(new MenuStorage(List.of(first, second), Optional.empty(), BlockPos.ZERO))
                : Optional.empty())) {
            var partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
            assertEquals(List.of(0, 1, 2, 3), partition.slots().stream().map(MenuSlotRef::containerSlot).toList());
        }
        assertFalse(new VanillaMenuSlotPartitioner(true).partition(menu, player).supported());
    }

    @Test
    void backingOwnerSuppliesPersistentIdentityEvenWhenTheInteractionWindowHasExpired() {
        Level level = mock(Level.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.BARREL.defaultBlockState());
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        StorageBlock owner = new StorageBlock(3);
        owner.setLevel(level);
        BlockMenu menu = new BlockMenu(owner, new Inventory(null));
        var tracker = new RecentInteractionTracker(new MinecraftContainerIdentityResolver());
        tracker.rememberBlock(level, BlockPos.ZERO);
        for (int i = 0; i < 10; i++) tracker.advanceTick();
        var identity = tracker.resolve("test-world", player, menu);
        assertEquals(IdentityConfidence.EXACT, identity.confidence());
        assertTrue(identity.persistable());
        assertEquals("minecraft:barrel", identity.container().metadata().get("minecraftBlock"));
    }

    @Test
    void transientMenusAndUnrelatedSlotHandlersAreNotAutomaticallyIndexed() {
        PlainMenu menu = new PlainMenu();
        menu.slot(new Slot(new SimpleContainer(new ItemStack(Items.DIAMOND)), 0, 0, 0));
        assertFalse(new VanillaMenuSlotPartitioner(true).partition(menu, new Inventory(null)).supported());
    }

    public static class StorageBlock extends BlockEntity {
        private final ItemStackHandler inventory;
        StorageBlock(int size) {
            super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            inventory = new ItemStackHandler(size);
        }
        public ItemStackHandler getInventory() { return inventory; }
    }

    public static class PlainMenu extends AbstractContainerMenu {
        PlainMenu() { super(MenuType.GENERIC_9x3, 1); }
        void slot(Slot slot) { addSlot(slot); }
        public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        public boolean stillValid(Player player) { return true; }
    }

    public static final class BlockMenu extends PlainMenu {
        private final StorageBlock owner;
        BlockMenu(StorageBlock owner, Inventory player) {
            this.owner = owner;
            for (int i = 0; i < owner.getInventory().getSlots(); i++) slot(new SlotItemHandler(owner.getInventory(), i, 0, 0));
            slot(new Slot(new SimpleContainer(new ItemStack(Items.DIAMOND, 64)), 0, 0, 0));
            slot(new Slot(player, 0, 0, 0));
        }
        public StorageBlock getBlockEntity() { return owner; }
    }
}
