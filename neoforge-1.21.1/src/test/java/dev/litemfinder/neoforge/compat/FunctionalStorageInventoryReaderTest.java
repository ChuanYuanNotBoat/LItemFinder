package dev.litemfinder.neoforge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FunctionalStorageInventoryReaderTest {
    @BeforeAll
    static void bootstrapMinecraft() { Bootstrap.bootStrap(); }

    @Test
    void readsOneTwoAndFourRealSlotsWithLargeCountsAndExcludesOverflow() throws Exception {
        var reader = new FunctionalStorageInventoryReader(Drawer.class);
        for (int slots : new int[]{1, 2, 4}) {
            Drawer drawer = new Drawer(slots);
            drawer.handler.setStackInSlot(0, new ItemStack(Items.DIRT, 1024));
            drawer.handler.setStackInSlot(slots - 1, new ItemStack(Items.DIRT, Integer.MAX_VALUE));
            drawer.handler.setStackInSlot(slots, new ItemStack(Items.DIAMOND, 64)); // virtual overflow
            var contents = reader.read(drawer).orElseThrow();
            assertEquals(slots, contents.stacks().size());
            assertEquals(Integer.MAX_VALUE, contents.stacks().getLast().getCount());
            assertTrue(contents.stacks().stream().noneMatch(stack -> stack.is(Items.DIAMOND)));
            drawer.handler.getStackInSlot(slots - 1).setCount(3);
            assertEquals(Integer.MAX_VALUE, contents.stacks().getLast().getCount(), "copy must detach live data");
        }
    }

    @Test
    void subclassesWorkButUnrelatedControllersAndConversionViewsAreNotGuessed() throws Exception {
        var reader = new FunctionalStorageInventoryReader(Drawer.class);
        assertTrue(reader.read(new FramedDrawer()).isPresent());
        assertTrue(reader.read(new UnrelatedStorage()).isEmpty());
    }

    @Test
    void lockedEmptyDrawerDoesNotBecomeAnItemAndLaterUpdatesRemainReadable() throws Exception {
        var reader = new FunctionalStorageInventoryReader(Drawer.class);
        Drawer drawer = new Drawer(1);
        drawer.handler.setStackInSlot(0, new ItemStack(Items.DIRT, 0));
        var empty = reader.read(drawer).orElseThrow();
        assertTrue(empty.stacks().getFirst().isEmpty());
        drawer.handler.setStackInSlot(0, new ItemStack(Items.DIRT, 1024));
        var full = reader.read(drawer).orElseThrow();
        assertFalse(full.sameContents(empty));
        drawer.handler.setStackInSlot(0, ItemStack.EMPTY);
        assertTrue(reader.read(drawer).orElseThrow().sameContents(empty));
    }

    public static class Drawer extends BlockEntity {
        final ItemStackHandler handler;
        private final int slots;
        Drawer(int slots) {
            super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
            this.slots = slots;
            handler = new ItemStackHandler(slots + 1);
        }
        public IItemHandler getStorage() { return handler; }
        public DrawerType getDrawerType() {
            return switch (slots) { case 1 -> DrawerType.X_1; case 2 -> DrawerType.X_2; default -> DrawerType.X_4; };
        }
        public int getStorageSlotAmount() { return 4; } // upgrade slots, NOT the item layout
    }

    public static class FramedDrawer extends Drawer {
        FramedDrawer() { super(4); }
    }

    public enum DrawerType {
        X_1(1), X_2(2), X_4(4);
        private final int slots;
        DrawerType(int slots) { this.slots = slots; }
        public int getSlots() { return slots; }
    }

    public static class UnrelatedStorage extends BlockEntity {
        UnrelatedStorage() { super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState()); }
        public IItemHandler getStorage() { return new ItemStackHandler(3); }
        public int getStorageSlotAmount() { return 3; }
    }
}
