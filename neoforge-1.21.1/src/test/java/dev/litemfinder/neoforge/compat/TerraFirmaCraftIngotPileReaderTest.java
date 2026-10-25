package dev.litemfinder.neoforge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerraFirmaCraftIngotPileReaderTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    @Test void readsActualMixedEntriesAndClearingWithoutEstimatingBlockHeight() throws Exception {
        var reader = new TerraFirmaCraftIngotPileReader(Pile.class, Entry.class);
        var pile = new Pile();
        for (int i = 0; i < 63; i++) pile.entries.add(new Entry(new ItemStack(i == 0 ? Items.COPPER_INGOT : Items.IRON_INGOT)));
        var observed = reader.read(pile).orElseThrow();
        assertEquals(63, observed.stacks().size());
        assertEquals(63, observed.stacks().stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(observed.stacks().getFirst().is(Items.COPPER_INGOT));
        pile.entries.clear();
        assertEquals(63, observed.stacks().size());
        assertTrue(reader.read(pile).orElseThrow().stacks().isEmpty());
        assertTrue(reader.read(new BlockEntity(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState()) { }).isEmpty(),
                "only the explicit synced pile type is indexed, never natural ground cover");
    }
    public static class Pile extends BlockEntity {
        private final List<Entry> entries = new ArrayList<>();
        Pile() { super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState()); }
    }
    static class Entry {
        final ItemStack stack;
        Entry(ItemStack stack) { this.stack = stack; }
    }
}
