package dev.litemfinder.core.view;

import dev.litemfinder.core.model.*;
import dev.litemfinder.core.snapshot.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidOverviewTest {
    @Test void blockItemAndNestedFluidsAggregateWithRealRootPathsAndVariantSeparation() {
        var nestedMetadata = new TreeMap<String, String>();
        ItemFluidSnapshotMetadata.write(nestedMetadata, Map.of(2, fluid("a", 500, 500)), Set.of());
        var nested = new InventorySnapshot(container("nested", new LogicalLocation("world", "nested"), nestedMetadata),
                3, Instant.EPOCH, List.of(item(2, "example:bottle")));
        var metadata = new TreeMap<String, String>();
        FluidSnapshotMetadata.writeStored(metadata, fluid("a", 1000, 10000));
        ItemFluidSnapshotMetadata.write(metadata, Map.of(0, fluid("a", 2000, 0), 1, fluid("b", 100, 1000)), Set.of());
        var location = new WorldLocation("world", NamespacedId.parse("example:dimension"), 2, 64, 3);
        var root = new InventorySnapshot(container("root", location, metadata), 5, Instant.EPOCH,
                List.of(item(0, "example:upgrade"), item(1, "example:jar"), SlotSnapshot.nested(4, new ItemStackInfo(ItemKey.parse("example:bag"), 1), nested)));
        var rows = FluidOverview.from(ContainerOverview.from(List.of(root))).rows();
        assertEquals(2, rows.size());
        assertEquals(3500, rows.getFirst().amount());
        assertEquals(0, rows.getFirst().capacity(), "one unknown capacity makes aggregate capacity unknown");
        assertEquals(Set.of(List.of(), List.of(0), List.of(4, 2)), rows.getFirst().sources().stream()
                .map(FluidOverview.Source::itemPath).collect(java.util.stream.Collectors.toSet()));
        assertTrue(rows.getFirst().sources().stream().allMatch(source -> source.root().equals(root.container().id()) && source.location().equals(location)));
        assertEquals("b", rows.get(1).variant());
        var overview = FluidOverview.from(ContainerOverview.from(List.of(root)));
        assertEquals(3500, overview.byRoot().get(root.container().id()).rows().getFirst().amount());
        assertEquals(500, overview.filterSources(source -> source.itemPath().equals(List.of(4, 2))).rows().getFirst().amount());
        assertThrows(UnsupportedOperationException.class, () -> rows.getFirst().sources().clear());
    }
    @Test void locationResolutionDeduplicatesRootsAndNeverInventsCoordinatesForCarriedStorage() {
        var world = container("block", new WorldLocation("scope", NamespacedId.parse("example:dimension"), 1, 2, 3), Map.of());
        var logical = container("carried", new LogicalLocation("scope", "inventory"), Map.of());
        var overview = ContainerOverview.from(List.of(new InventorySnapshot(world, 0, Instant.EPOCH, List.of()),
                new InventorySnapshot(logical, 0, Instant.EPOCH, List.of())));
        assertEquals(List.of(world.location()), RecordedSourceLocations.resolve(overview,
                List.of(world.id(), world.id(), logical.id(), new ContainerId("missing"))));
    }
    @Test void providerConfirmedNetworksGroupAcrossFluidOnlyAndItemContainers() {
        var a = new InventorySnapshot(container("a", new LogicalLocation("scope", "a"), Map.of("storageNetworkId", "one", "storageNetworkName", "Warehouse")), 0, Instant.EPOCH, List.of());
        var b = new InventorySnapshot(container("b", new LogicalLocation("scope", "b"), Map.of("storageNetworkId", "one")), 0, Instant.EPOCH, List.of());
        var c = new InventorySnapshot(container("c", new LogicalLocation("scope", "c"), Map.of("storageNetworkId", "two")), 0, Instant.EPOCH, List.of());
        var groups = ContainerOverview.from(List.of(a, b, c)).groups();
        assertEquals(2, groups.size()); assertEquals(List.of(a.container().id(), b.container().id()), groups.getFirst().members());
    }
    @Test void bucketUnitsRetainPrecisionAndDistinguishMilliKiloAndMegaBuckets() {
        assertEquals("0 mB", FluidQuantityFormat.display(0));
        assertEquals("999 mB", FluidQuantityFormat.display(999));
        assertEquals("1.5 B", FluidQuantityFormat.display(1500));
        assertEquals("1 kB", FluidQuantityFormat.display(1_000_000));
        assertEquals("1 MB", FluidQuantityFormat.display(1_000_000_000));
        assertEquals("9.22 PB", FluidQuantityFormat.display(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> FluidQuantityFormat.display(-1));
    }
    private static FluidSnapshotMetadata.StoredInventory fluid(String variant, long amount, long capacity) {
        return new FluidSnapshotMetadata.StoredInventory(List.of(new FluidSnapshotMetadata.StoredTank(0, "example:water", variant, "opaque", amount, capacity)), Instant.EPOCH);
    }
    private static ContainerRecord container(String id, ContainerLocation location, Map<String, String> metadata) {
        return new ContainerRecord(new ContainerId(id), ContainerType.CHEST, location, metadata);
    }
    private static SlotSnapshot item(int slot, String id) { return new SlotSnapshot(slot, new ItemStackInfo(ItemKey.parse(id), 1)); }
}
