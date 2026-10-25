package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.capture.BlockCaptureRequest;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.mapping.MenuSnapshotMapper;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerraFirmaCraftDisplayInventoryReaderTest {
    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() { Bootstrap.bootStrap(); }

    private TerraFirmaCraftDisplayInventoryReader reader() throws Exception {
        return new TerraFirmaCraftDisplayInventoryReader(DisplayStorage.class, PlacedItems.class,
                owner -> ResourceLocation.parse(((DisplayStorage) owner).typeId));
    }

    @Test
    void toolRackReadsFourRealSlotsWithVariantsAndTracksRemoval() throws Exception {
        var reader = reader();
        var rack = new DisplayStorage("tfc:tool_rack");
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        tool.set(DataComponents.DAMAGE, 34);
        rack.inventory.setStackInSlot(2, tool);
        var before = reader.read(rack).orElseThrow();
        assertEquals(4, before.stacks().size());
        assertEquals(34, before.stacks().get(2).get(DataComponents.DAMAGE));
        assertEquals(4, before.layout().orElseThrow().positions().size());
        assertTrue(before.stacks().get(0).isEmpty());
        rack.inventory.setStackInSlot(2, ItemStack.EMPTY);
        assertFalse(before.sameContents(reader.read(rack).orElseThrow()));
        assertFalse(before.stacks().get(2).isEmpty(), "capture is detached from live storage");
    }

    @Test
    void placedItemsUseFourSmallPositionsOrTheSingleLargeItemInSlotZero() throws Exception {
        var reader = reader();
        var placed = new PlacedItems("tfc:placed_item");
        placed.inventory.setStackInSlot(3, new ItemStack(Items.BRICK));
        assertEquals(4, reader.read(placed).orElseThrow().stacks().size());
        placed.inventory.setStackInSlot(3, ItemStack.EMPTY);
        placed.large = true;
        placed.inventory.setStackInSlot(0, new ItemStack(Items.IRON_HOE));
        var large = reader.read(placed).orElseThrow();
        assertEquals(1, large.stacks().size());
        assertTrue(large.stacks().getFirst().is(Items.IRON_HOE));
        assertEquals(1, large.layout().orElseThrow().positions().size());
    }

    @Test
    void naturalGroundcoverUnknownInventoriesAndPitKilnSubclassesNeverReadOrPolluteDatabase() throws Exception {
        var reader = reader();
        List<DisplayStorage> excluded = List.of(new DisplayStorage("tfc:groundcover/twig"),
                new DisplayStorage("tfc:rock/loose/granite"), new DisplayStorage("other_mod:tool_rack"),
                new DisplayStorage("tfc:unknown_machine"), new PlacedItems("tfc:pit_kiln"));
        try (var repository = new SqliteSnapshotRepository(temporaryDirectory.resolve("natural.db"))) {
            var mapper = new MenuSnapshotMapper(new CachedItemTagResolver());
            var identity = ContainerIdentityFactory.block("test", NamespacedId.parse("minecraft:overworld"),
                    0, 64, 0, NamespacedId.parse("litemfinder:block_inventory"));
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            for (int iteration = 0; iteration < 100; iteration++) {
                for (var natural : excluded) {
                    natural.inventory.setStackInSlot(0, new ItemStack(Items.STICK, 64));
                    reader.read(natural).ifPresent(contents -> repository.save(mapper.map(
                            new BlockCaptureRequest(identity, contents), registries, Instant.EPOCH)));
                }
            }
            assertTrue(repository.findAll().isEmpty());
            excluded.forEach(owner -> assertEquals(0, owner.reads, "type policy is checked before getters"));
        }
    }

    public static class DisplayStorage extends BlockEntity {
        final ItemStackHandler inventory = new ItemStackHandler(4);
        final String typeId;
        int reads;
        DisplayStorage(String typeId) {
            super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
            this.typeId = typeId;
        }
        public IItemHandler getInventory() { reads++; return inventory; }
    }

    public static class PlacedItems extends DisplayStorage {
        boolean large;
        PlacedItems(String typeId) { super(typeId); }
        public boolean holdingLargeItem() { return large; }
    }
}
