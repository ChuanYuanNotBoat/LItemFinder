package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.snapshot.FluidSnapshotMetadata;

import dev.litemfinder.core.model.*;
import dev.litemfinder.neoforge.api.inventory.*;
import dev.litemfinder.neoforge.capture.*;
import dev.litemfinder.core.view.ContainerOverview;
import dev.litemfinder.core.view.FluidOverview;
import dev.litemfinder.core.view.FluidQuantityFormat;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidInventoryTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    private final RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test void quantityUnitsUseBucketsAndKeepMillibucketsDistinctThroughLongMaximum() {
        assertEquals("0 mB", FluidQuantityFormat.display(0));
        assertEquals("999 mB", FluidQuantityFormat.display(999));
        assertEquals("1 B", FluidQuantityFormat.display(1000));
        assertEquals("2 B", FluidQuantityFormat.display(2000));
        assertEquals("1.5 B", FluidQuantityFormat.display(1500));
        assertEquals("1 kB", FluidQuantityFormat.display(1_000_000));
        assertEquals("1 MB", FluidQuantityFormat.display(1_000_000_000));
        assertEquals("1 GB", FluidQuantityFormat.display(1_000_000_000_000L));
        assertEquals("9.22 PB", FluidQuantityFormat.display(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> FluidQuantityFormat.display(-1));
    }

    @Test void fluidOnlyChangesTriggerCaptureAndDrainedTankReplacesContentsWithoutChangingItemTotals() throws Exception {
        Barrel owner = new Barrel();
        BarrelMenu menu = new BarrelMenu(owner);
        try (var registration = FluidInventoryAdapters.register(block -> block == owner
                ? Optional.of(FluidInventory.fromHandler(owner.tank)) : Optional.empty())) {
            var partition = new VanillaMenuSlotPartitioner(true).partition(menu, new Inventory(null));
            var fingerprint = new MenuFingerprintCalculator();
            var empty = fingerprint.calculate(menu, partition, registries);
            owner.tank.setFluid(new FluidStack(Fluids.WATER, 2000));
            var filled = fingerprint.calculate(menu, partition, registries);
            assertNotEquals(empty, filled);
            var identity = ContainerIdentityFactory.block("fluid-test", NamespacedId.parse("minecraft:overworld"),
                    1, 64, 2, NamespacedId.parse("tfc:barrel"));
            var mapper = new MenuSnapshotMapper(new CachedItemTagResolver());
            var first = mapper.map(new MenuCaptureRequest(identity, menu, partition, filled, CaptureReason.OPEN_STABLE), registries, Instant.EPOCH);
            var fluid = FluidSnapshotMetadata.read(first.container().metadata()).orElseThrow();
            assertEquals(2000, fluid.tanks().getFirst().amount());
            assertEquals(10000, fluid.tanks().getFirst().capacity());
            assertTrue(first.slots().isEmpty(), "fluid is not represented as an item stack");
            assertEquals(2000, FluidOverview.from(ContainerOverview.from(List.of(first))).rows().getFirst().amount());
            owner.tank.setFluid(FluidStack.EMPTY);
            var next = mapper.map(new MenuCaptureRequest(identity, menu, partition,
                    fingerprint.calculate(menu, partition, registries), CaptureReason.CONTENT_STABLE), registries, Instant.EPOCH.plusSeconds(1));
            assertTrue(FluidOverview.from(ContainerOverview.from(List.of(next))).rows().isEmpty());
            assertEquals(0, FluidSnapshotMetadata.read(next.container().metadata()).orElseThrow().tanks().getFirst().amount());
        }
    }

    @Test void fluidDataRoundTripsAlongsideItemsAndUnavailableReadKeepsOriginalFluidTime() {
        Map<String, String> metadata = new TreeMap<>(Map.of("minecraftBlock", "tfc:wood/barrel/maple"));
        FluidInventoryMetadata.write(metadata, new FluidInventory(List.of(new FluidInventory.Tank(0,
                new FluidStack(Fluids.WATER, 1), 3_000_000_000L, 4_000_000_000L))), registries, Instant.EPOCH);
        var record = new ContainerRecord(new ContainerId("barrel"), new ContainerType(NamespacedId.parse("tfc:barrel")),
                new LogicalLocation("test", "barrel"), metadata);
        var snapshot = new InventorySnapshot(record, 1, Instant.EPOCH, List.of());
        try (var repository = new SqliteSnapshotRepository(directory.resolve("fluids.db"))) { repository.save(snapshot); }
        try (var repository = new SqliteSnapshotRepository(directory.resolve("fluids.db"))) {
            var restored = repository.find(record.id()).orElseThrow();
            assertEquals(snapshot, restored);
            var read = FluidSnapshotMetadata.read(restored.container().metadata()).orElseThrow();
            assertEquals(3_000_000_000L, read.tanks().getFirst().amount());
            var itemOnly = new InventorySnapshot(new ContainerRecord(record.id(), record.type(), record.location(),
                    Map.of("minecraftBlock", "tfc:wood/barrel/maple")), 1, Instant.EPOCH.plusSeconds(20), List.of());
            assertEquals(Instant.EPOCH, FluidSnapshotMetadata.read(FluidSnapshotMetadata.preserveUnavailable(itemOnly, restored)
                    .container().metadata()).orElseThrow().observedAt());
        }
    }

    public static class Barrel extends BlockEntity {
        final ItemStackHandler inventory = new ItemStackHandler(3);
        final FluidTank tank = new FluidTank(10000);
        Barrel() { super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState()); }
        public ItemStackHandler getInventory() { return inventory; }
    }
    public static class BarrelMenu extends AbstractContainerMenu {
        final Barrel owner;
        BarrelMenu(Barrel owner) {
            super(MenuType.GENERIC_9x3, 1); this.owner = owner;
            for (int slot = 0; slot < 3; slot++) addSlot(new SlotItemHandler(owner.inventory, slot, 8 + 18 * slot, 18));
        }
        public Barrel getBlockEntity() { return owner; }
        public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        public boolean stillValid(Player player) { return true; }
    }
}
