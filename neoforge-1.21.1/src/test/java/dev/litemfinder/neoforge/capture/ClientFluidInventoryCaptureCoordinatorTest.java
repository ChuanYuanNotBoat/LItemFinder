package dev.litemfinder.neoforge.capture;

import dev.litemfinder.core.snapshot.FluidSnapshotMetadata;

import dev.litemfinder.core.model.*;
import dev.litemfinder.neoforge.api.inventory.*;
import dev.litemfinder.neoforge.mapping.FluidInventoryMetadata;
import dev.litemfinder.neoforge.persistence.PartialSnapshotMerger;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ClientFluidInventoryCaptureCoordinatorTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    @Test void canonicalMasterDeduplicatesDummyTargetsAndFailuresNeverEmitAnEmptyReading() {
        Level level = mock(Level.class);
        BlockPos master = new BlockPos(16, 64, 16), dummy = master.east();
        BlockEntity a = mock(BlockEntity.class), b = mock(BlockEntity.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(true);
        when(level.getBlockEntity(master)).thenReturn(a);
        when(level.getBlockEntity(dummy)).thenReturn(b);
        when(level.getBlockState(master)).thenReturn(Blocks.BARREL.defaultBlockState());
        AtomicReference<Optional<BlockFluidInventory>> read = new AtomicReference<>(Optional.of(inventory(master, 2000)));
        List<InventorySnapshot> captured = new ArrayList<>();
        var capture = new ClientFluidInventoryCaptureCoordinator(owner -> read.get(), captured::add);
        capture.observe("world", level, master);
        capture.observe("world", level, dummy);
        assertEquals(1, captured.size());
        assertEquals(new WorldLocation("world", NamespacedId.parse("minecraft:overworld"), 16, 64, 16), captured.getFirst().container().location());
        read.set(Optional.empty());
        capture.observe("world", level, master);
        assertEquals(1, captured.size());
        read.set(Optional.of(inventory(master, 0)));
        capture.observe("world", level, dummy);
        assertEquals(2, captured.size());
        assertEquals(0, FluidSnapshotMetadata.read(captured.getLast().container().metadata()).orElseThrow().tanks().getFirst().amount());
        var previous = new InventorySnapshot(captured.getFirst().container(), 4, Instant.EPOCH,
                List.of(new SlotSnapshot(0, new ItemStackInfo(ItemKey.parse("minecraft:coal"), 16))));
        var merged = PartialSnapshotMerger.merge(previous, captured.getLast(), Set.of(), false).orElseThrow();
        assertEquals(previous.slots(), merged.slots(), "aiming at a tank cannot clear unobserved item slots");
        assertEquals(0, FluidSnapshotMetadata.read(merged.container().metadata()).orElseThrow().tanks().getFirst().amount());
        clearInvocations(level);
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(false);
        capture.observe("world", level, master);
        verify(level, never()).getBlockEntity(any());
    }
    @Test void throwingAdapterDoesNotEmitOrPreventLaterRetry() {
        Level level = mock(Level.class);
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(true);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(mock(BlockEntity.class));
        List<InventorySnapshot> captured = new ArrayList<>();
        var capture = new ClientFluidInventoryCaptureCoordinator(owner -> { throw new IllegalStateException(); }, captured::add);
        assertDoesNotThrow(() -> capture.observe("world", level, BlockPos.ZERO));
        assertTrue(captured.isEmpty());
    }
    private static BlockFluidInventory inventory(BlockPos master, int amount) {
        return new BlockFluidInventory(master, NamespacedId.parse("immersiveengineering:cokeoven"), 4,
                new FluidInventory(List.of(new FluidInventory.Tank(0, new FluidStack(Fluids.WATER, 1), amount, 12000))));
    }
}
