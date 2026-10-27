package dev.litemfinder.neoforge.compat;

import dev.litemfinder.core.model.*;
import dev.litemfinder.core.view.*;
import dev.litemfinder.neoforge.capture.FluidInventoryAdapters;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.neoforge.mapping.FluidInventoryMetadata;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FunctionalStorageFluidReaderTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    @Test void normalAndFramedDrawersReadAllPhysicalTanksWithDetachedAmountsAndCapacities() throws Exception {
        var reader = new FunctionalStorageFluidReader(Drawer.class);
        for (int count : new int[]{1, 2, 4}) {
            Drawer drawer = count == 4 ? new FramedDrawer() : new Drawer(count);
            drawer.handler.tanks[0].setFluid(new FluidStack(Fluids.WATER, 1200));
            if (count > 1) drawer.handler.tanks[count - 1].setFluid(new FluidStack(Fluids.LAVA, 900));
            var read = reader.read(drawer).orElseThrow();
            assertEquals(count, read.fluids().tanks().size());
            assertEquals(1200, read.fluids().tanks().getFirst().amount());
            assertEquals(32000, read.fluids().tanks().getFirst().capacity());
            assertEquals(0, read.itemSlots(), "upgrade slots are not stored item inventory");
            drawer.handler.tanks[0].setFluid(FluidStack.EMPTY);
            assertEquals(1200, read.fluids().tanks().getFirst().amount(), "reading must be detached");
        }
    }
    @Test void lockedEmptyFiltersAreNotStockAndFillCapacityAndDrainChangesRemainObservable() throws Exception {
        var reader = new FunctionalStorageFluidReader(Drawer.class);
        Drawer drawer = new Drawer(1);
        var empty = reader.read(drawer).orElseThrow();
        assertEquals(0, empty.fluids().tanks().getFirst().amount());
        drawer.handler.tanks[0].setFluid(new FluidStack(Fluids.WATER, 1200));
        var filled = reader.read(drawer).orElseThrow();
        assertNotEquals(empty.fluids().fingerprint(), filled.fluids().fingerprint());
        drawer.handler.tanks[0].setCapacity(64000);
        assertNotEquals(filled.fluids().fingerprint(), reader.read(drawer).orElseThrow().fluids().fingerprint());
        drawer.handler.tanks[0].setFluid(FluidStack.EMPTY);
        assertEquals(0, reader.read(drawer).orElseThrow().fluids().tanks().getFirst().amount());
    }
    @Test void creativeViewsControllersAndMismatchedVirtualTanksAreNeverReadAsFiniteStock() throws Exception {
        var reader = new FunctionalStorageFluidReader(Drawer.class);
        var creative = new Drawer(1); creative.handler.creative = true;
        creative.handler.tanks[0].setFluid(new FluidStack(Fluids.WATER, 1200));
        assertTrue(reader.read(creative).isEmpty());
        assertEquals(0, creative.handler.reads, "creative getter mutates live amounts and must never be called");
        assertEquals(1200, creative.handler.tanks[0].getFluidAmount());
        assertTrue(reader.read(new Controller()).isEmpty());
        var inconsistent = new Drawer(1); inconsistent.handler = new Handler(2);
        assertTrue(reader.read(inconsistent).isEmpty());
        assertEquals(0, inconsistent.handler.reads);
    }
    @Test void unavailableOrThrowingHandlerDoesNotProduceAnEmptyObservation() throws Exception {
        var reader = new FunctionalStorageFluidReader(Drawer.class);
        var drawer = new Drawer(1); drawer.handler = null;
        assertTrue(reader.read(drawer).isEmpty());
        drawer.handler = new Handler(1); drawer.fail = true;
        assertTrue(reader.read(drawer).isEmpty());
    }
    @Test void sharedAdapterPipelineReachesCoreOverviewAndMapAtThePhysicalBlockWithoutItemDuplicates() throws Exception {
        var reader = new FunctionalStorageFluidReader(Drawer.class);
        Drawer drawer = new Drawer(1); drawer.handler.tanks[0].setFluid(new FluidStack(Fluids.WATER, 1200));
        try (var registration = FluidInventoryAdapters.registerBlock(reader)) {
            var read = FluidInventoryAdapters.readBlock(drawer).orElseThrow();
            assertEquals(1200, FluidInventoryAdapters.read(drawer).orElseThrow().tanks().getFirst().amount());
            var identity = ContainerIdentityFactory.block("world", NamespacedId.parse("minecraft:overworld"),
                    read.position().getX(), read.position().getY(), read.position().getZ(), read.containerType());
            var metadata = new TreeMap<String, String>();
            FluidInventoryMetadata.write(metadata, read.fluids(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), Instant.EPOCH);
            var record = identity.container();
            var snapshot = new InventorySnapshot(new ContainerRecord(record.id(), record.type(), record.location(), metadata), 0, Instant.EPOCH, List.of());
            var containers = ContainerOverview.from(List.of(snapshot));
            var fluids = FluidOverview.from(containers);
            var row = fluids.byRoot().get(record.id()).rows().getFirst();
            assertEquals("1.2 B", FluidQuantityFormat.display(row.amount()));
            assertEquals("32 B", FluidQuantityFormat.display(row.capacity()));
            assertEquals(List.of(record.location()), RecordedSourceLocations.resolve(containers, List.of(record.id())));
            assertTrue(snapshot.slots().isEmpty());
        }
    }
    public static class Drawer extends BlockEntity {
        Handler handler; final DrawerType type; boolean fail;
        Drawer(int slots) { super(BlockEntityType.BARREL, new BlockPos(7, 64, 9), Blocks.BARREL.defaultBlockState()); handler = new Handler(slots); type = new DrawerType(slots); }
        public Handler getFluidHandler() { if (fail) throw new IllegalStateException("not synchronized"); return handler; }
        public DrawerType getDrawerType() { return type; }
        public int getStorageSlotAmount() { return 4; }
    }
    public static class FramedDrawer extends Drawer { FramedDrawer() { super(4); } }
    public record DrawerType(int slots) { public int getSlots() { return slots; } }
    public static class Controller extends BlockEntity {
        Controller() { super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState()); }
        public Handler getFluidHandler() { throw new AssertionError("do not query network proxies"); }
    }
    public static class Handler implements IFluidHandler {
        final FluidTank[] tanks; boolean creative; int reads;
        Handler(int count) { tanks = new FluidTank[count]; Arrays.setAll(tanks, ignored -> new FluidTank(32000)); }
        public boolean isDrawerCreative() { return creative; }
        public int getTanks() { return tanks.length; }
        public FluidStack getFluidInTank(int tank) { reads++; if (creative) throw new AssertionError("creative view mutation"); return tanks[tank].getFluid(); }
        public int getTankCapacity(int tank) { return tanks[tank].getCapacity(); }
        public FluidStack[] getFilterStack() { throw new AssertionError("locked filter is not actual fluid"); }
        public boolean isFluidValid(int tank, FluidStack fluid) { throw new AssertionError("not needed for reading"); }
        public int fill(FluidStack fluid, FluidAction action) { throw new AssertionError("read-only capture"); }
        public FluidStack drain(FluidStack fluid, FluidAction action) { throw new AssertionError("read-only capture"); }
        public FluidStack drain(int amount, FluidAction action) { throw new AssertionError("read-only capture"); }
    }
}
