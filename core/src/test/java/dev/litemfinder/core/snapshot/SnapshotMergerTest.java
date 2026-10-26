package dev.litemfinder.core.snapshot;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotMergerTest {
    private static final ContainerRecord ROOT = new ContainerRecord(new ContainerId("warehouse-1"), ContainerType.CHEST,
            new LogicalLocation("test-world", "warehouse-1"));

    @Test void terminalPagesReplaceVisibleSlotsAndRetainUnseenSlotsUnderOneIdentity() {
        var first = SnapshotMerger.merge(null, observation(0, 9, Map.of(), Set.of(0, 1), slot(0, "minecraft:coal", 10))).orElseThrow();
        var second = SnapshotMerger.merge(first, observation(1, 3, Map.of(), Set.of(2), slot(2, "minecraft:diamond", 4))).orElseThrow();
        var index = new InMemoryStorageIndex(); index.update(first); index.update(second);
        assertEquals(1, index.rootContainerCount());
        assertEquals(9, second.slotCount(), "a partial capacity hint cannot truncate unseen slots");
        assertEquals(Set.of(0, 2), second.slots().stream().map(SlotSnapshot::slot).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Instant.EPOCH, second.capturedAt(), "unseen data keeps its older observation time");
        assertEquals("0,1,2", second.container().metadata().get("observedSlots"));
        assertEquals(Instant.EPOCH.plusSeconds(1).toString(), second.container().metadata().get("lastPartialObservedAt"));
        var cleared = SnapshotMerger.merge(second, observation(2, 9, Map.of(), Set.of(0))).orElseThrow();
        assertEquals(List.of(slot(2, "minecraft:diamond", 4)), cleared.slots());
    }
    @Test void completeObservationCanShrinkAndClearAllSlotsWithoutLeavingPartialMarkers() {
        var old = SnapshotMerger.merge(null, observation(0, 9, Map.of(), Set.of(8), slot(8, "minecraft:coal", 20))).orElseThrow();
        var full = snapshot(1, 1, Map.of());
        var cleared = SnapshotMerger.merge(old, InventoryObservation.complete(full)).orElseThrow();
        assertEquals(1, cleared.slotCount()); assertTrue(cleared.slots().isEmpty());
        assertFalse(cleared.container().metadata().containsKey("captureCoverage"));
        assertFalse(cleared.container().metadata().containsKey("lastPartialObservedAt"));
    }
    @Test void rejectsOlderPagesAndFullSnapshotsAfterANewerPartialObservation() {
        var previous = SnapshotMerger.merge(null, observation(5, 9, Map.of(), Set.of(0), slot(0, "minecraft:coal", 10))).orElseThrow();
        var later = SnapshotMerger.merge(previous, observation(10, 9, Map.of(), Set.of(1))).orElseThrow();
        assertTrue(SnapshotMerger.merge(later, observation(7, 9, Map.of(), Set.of(0))).isEmpty());
        assertTrue(SnapshotMerger.merge(later, InventoryObservation.complete(snapshot(8, 9, Map.of()))).isEmpty());
    }
    @Test void invalidCoverageAndWrongPhysicalIdentityAreRejectedAtTheCoreBoundary() {
        assertThrows(IllegalArgumentException.class, () -> observation(0, 2, Map.of(), Set.of(2)));
        assertThrows(IllegalArgumentException.class, () -> observation(0, 2, Map.of(), Set.of(0), slot(1, "minecraft:coal", 1)));
        assertThrows(IllegalArgumentException.class, () -> new InventoryObservation(snapshot(0, 2, Map.of()), Set.of(0), true));
        var another = new InventorySnapshot(new ContainerRecord(new ContainerId("other"), ROOT.type(), ROOT.location()), 2, Instant.EPOCH, List.of());
        assertThrows(IllegalArgumentException.class, () -> SnapshotMerger.merge(another, observation(1, 2, Map.of(), Set.of(0))));
    }
    @Test void fluidCoverageIsIndependentOfVisibleItemSlots() {
        var initial = fluidMetadata(0, "COMPLETE", tank(0, 2000), tank(1, 3000));
        var previous = snapshot(0, 2, initial, slot(0, "minecraft:coal", 16));
        var partial = SnapshotMerger.merge(previous, observation(1, 2, fluidMetadata(1, "PARTIAL", tank(0, 0)), Set.of())).orElseThrow();
        var read = FluidSnapshotMetadata.read(partial.container().metadata()).orElseThrow();
        assertEquals(List.of(tank(0, 0), tank(1, 3000)), read.tanks());
        assertEquals(Instant.EPOCH, read.observedAt());
        assertEquals(previous.slots(), partial.slots());
        var complete = SnapshotMerger.merge(partial, observation(2, 2, fluidMetadata(2, "COMPLETE"), Set.of())).orElseThrow();
        assertTrue(FluidSnapshotMetadata.read(complete.container().metadata()).orElseThrow().tanks().isEmpty());
        assertEquals(previous.slots(), complete.slots());
    }
    @Test void unreadableFluidAndNetworkDataRetainTheirOriginalObservation() {
        var metadata = fluidMetadata(0, "COMPLETE", tank(0, 2000));
        metadata.put("storageNetworkId", "network-a"); metadata.put("storageNetworkName", "Warehouse");
        var previous = snapshot(0, 2, metadata);
        var incoming = SnapshotMerger.retainUnavailable(snapshot(10, 2, Map.of()), previous);
        assertEquals(Instant.EPOCH, FluidSnapshotMetadata.read(incoming.container().metadata()).orElseThrow().observedAt());
        assertEquals("network-a", incoming.container().metadata().get("storageNetworkId"));
    }
    @Test void unreadableMovedItemIsRetainedOnlyForAnUnambiguousExactVariant() {
        var metadata = new TreeMap<String, String>();
        var inventory = new FluidSnapshotMetadata.StoredInventory(List.of(tank(0, 500)), Instant.EPOCH);
        ItemFluidSnapshotMetadata.write(metadata, Map.of(0, inventory), Set.of());
        var previous = snapshot(0, 4, metadata, slot(0, "example:bottle", 1));
        var unavailable = new TreeMap<String, String>(); ItemFluidSnapshotMetadata.write(unavailable, Map.of(), Set.of(3));
        var incoming = snapshot(1, 4, unavailable, slot(3, "example:bottle", 1));
        assertEquals(Map.of(3, inventory), ItemFluidSnapshotMetadata.read(SnapshotMerger.retainUnavailable(incoming, previous).container().metadata()));
        var otherVariant = new SlotSnapshot(3, new ItemStackInfo(ItemKey.parse("example:bottle").withVariant("different"), 1));
        assertTrue(ItemFluidSnapshotMetadata.read(SnapshotMerger.retainUnavailable(snapshot(1, 4, unavailable, otherVariant), previous).container().metadata()).isEmpty());
        ItemFluidSnapshotMetadata.write(metadata, Map.of(0, inventory, 1, inventory), Set.of());
        var ambiguous = snapshot(0, 4, metadata, slot(0, "example:bottle", 1), slot(1, "example:bottle", 1));
        assertTrue(ItemFluidSnapshotMetadata.read(SnapshotMerger.retainUnavailable(incoming, ambiguous).container().metadata()).isEmpty());
    }
    @Test void itemFluidPagesKeepUnseenTanksAndClearAnObservedEmptySlot() {
        var inventory = new FluidSnapshotMetadata.StoredInventory(List.of(tank(0, 500)), Instant.EPOCH);
        var metadata = new TreeMap<String, String>(); ItemFluidSnapshotMetadata.write(metadata, Map.of(0, inventory, 3, inventory), Set.of());
        var previous = snapshot(0, 4, metadata, slot(0, "example:bottle", 1), slot(3, "example:bottle", 1));
        var empty = new TreeMap<String, String>(); ItemFluidSnapshotMetadata.write(empty, Map.of(), Set.of());
        var merged = SnapshotMerger.merge(previous, observation(1, 4, empty, Set.of(0))).orElseThrow();
        assertEquals(Map.of(3, inventory), ItemFluidSnapshotMetadata.read(merged.container().metadata()));
    }
    private InventoryObservation observation(int time, int slots, Map<String, String> metadata, Set<Integer> covered, SlotSnapshot... items) {
        return new InventoryObservation(snapshot(time, slots, metadata, items), covered, false);
    }
    private InventorySnapshot snapshot(int time, int slots, Map<String, String> metadata, SlotSnapshot... items) {
        return new InventorySnapshot(new ContainerRecord(ROOT.id(), ROOT.type(), ROOT.location(), metadata), slots, Instant.EPOCH.plusSeconds(time), List.of(items));
    }
    private static SlotSnapshot slot(int index, String id, long count) { return new SlotSnapshot(index, new ItemStackInfo(ItemKey.parse(id), count)); }
    private static FluidSnapshotMetadata.StoredTank tank(int index, long amount) {
        return new FluidSnapshotMetadata.StoredTank(index, amount == 0 ? "" : "example:water", "", amount == 0 ? "" : "opaque-payload", amount, 10000);
    }
    private Map<String, String> fluidMetadata(int time, String coverage, FluidSnapshotMetadata.StoredTank... tanks) {
        var metadata = new TreeMap<String, String>();
        FluidSnapshotMetadata.writeStored(metadata, new FluidSnapshotMetadata.StoredInventory(List.of(tanks), Instant.EPOCH.plusSeconds(time)));
        metadata.put("fluidCoverage", coverage); return metadata;
    }
}
