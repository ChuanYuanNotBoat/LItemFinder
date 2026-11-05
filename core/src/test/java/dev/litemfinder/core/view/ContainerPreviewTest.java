package dev.litemfinder.core.view;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.*;
import dev.litemfinder.core.search.*;
import dev.litemfinder.core.snapshot.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerPreviewTest {
    private static final UUID PLAYER = UUID.fromString("76488ca8-25d1-4620-ad2c-7960ac41e066");
    private static final ItemStackInfo BAG = new ItemStackInfo(new ItemKey(NamespacedId.parse("example:bag"), "unique-bag"), 1);
    private static final ItemStackInfo DIAMONDS = new ItemStackInfo(ItemKey.parse("minecraft:diamond"), 4462);
    private static final ContainerRecord ROOT = new ContainerRecord(new ContainerId("player:scope:" + PLAYER + ":inventory"),
            new ContainerType(NamespacedId.parse("minecraft:player_inventory")), new LogicalLocation("scope", "player"));
    private static InventorySnapshot contents(long amount) {
        var record = new ContainerRecord(new ContainerId("nested-bag"), new ContainerType(BAG.item().itemId()), new LogicalLocation("scope", "bag"));
        return new InventorySnapshot(record, 120, Instant.EPOCH, amount == 0 ? List.of() : List.of(new SlotSnapshot(7, new ItemStackInfo(DIAMONDS.item(), amount))));
    }
    private static InventorySnapshot root(InventorySnapshot nested, Map<String,String> metadata, ItemStackInfo carrier) {
        var record = new ContainerRecord(ROOT.id(), ROOT.type(), ROOT.location(), metadata);
        return new InventorySnapshot(record, 45, Instant.EPOCH, List.of(new SlotSnapshot(41, carrier, Optional.ofNullable(nested))));
    }
    @Test void playerAccessoryBackpackContentsReachBothTotalsAndTheOriginalGrid() {
        var snapshot = root(contents(4462), Map.of("accessorySlot.41", "curios/back:0"), BAG);
        var overview = ContainerOverview.from(List.of(snapshot));
        var player = ContainerPreview.player(overview, PLAYER).orElseThrow();
        assertTrue(ContainerPreview.player(overview, UUID.randomUUID()).isEmpty());
        assertEquals(41, player.contents().getFirst().slot());
        assertEquals(7, player.contents().getFirst().nestedContainer().orElseThrow().slots().getFirst().slot());
        assertEquals(4462, ContainerPreview.aggregate(snapshot).getFirst().count());
        var index = new InMemoryStorageIndex(); index.update(snapshot);
        assertEquals(4462, new IndexedSearchEngine(index).findExact(DIAMONDS.item()).totalCount());
        assertEquals(4462, InventoryOverview.from(new IndexedSearchEngine(index).search(SearchQuery.all())).rows().getFirst().totalCount());
    }
    @Test void unavailableNestedReadKeepsTheLastCountButConfirmedEmptyClearsIt() {
        var previous = root(contents(4462), Map.of(), BAG);
        var unavailable = root(null, Map.of(NestedInventoryObservation.UNAVAILABLE, "41"), BAG);
        var retained = SnapshotMerger.retainUnavailable(unavailable, previous);
        assertEquals(4462, ContainerPreview.aggregate(retained).getFirst().count());
        assertEquals(Instant.EPOCH, retained.slots().getFirst().nestedContainer().orElseThrow().capturedAt());
        var empty = SnapshotMerger.retainUnavailable(root(contents(0), Map.of(), BAG), retained);
        assertEquals(List.of(new ContainerPreview.Entry(BAG.item(), 1)), ContainerPreview.aggregate(empty));
    }
    @Test void removingOrReplacingTheCarrierDoesNotInheritAnOldBackpack() {
        var previous = root(contents(4462), Map.of(), BAG);
        var replaced = root(null, Map.of(NestedInventoryObservation.UNAVAILABLE, "41"),
                new ItemStackInfo(new ItemKey(BAG.item().itemId(), "other-uuid"), 1));
        assertTrue(SnapshotMerger.retainUnavailable(replaced, previous).slots().getFirst().nestedContainer().isEmpty());
        var removed = new InventorySnapshot(ROOT, 45, Instant.EPOCH, List.of());
        assertTrue(SnapshotMerger.retainUnavailable(removed, previous).slots().isEmpty());
    }
    @Test void aStableBackpackIdentityCanRetainContentsAcrossNonStorageComponentChanges() {
        var previous = root(contents(4462), Map.of(NestedInventoryObservation.IDENTITY_PREFIX + 41, "bag-uuid"), BAG);
        var changed = new ItemStackInfo(new ItemKey(BAG.item().itemId(), "changed-tooltip-components"), 1);
        var incoming = root(null, Map.of(NestedInventoryObservation.UNAVAILABLE, "41",
                NestedInventoryObservation.IDENTITY_PREFIX + 41, "bag-uuid"), changed);
        var retained = SnapshotMerger.retainUnavailable(incoming, previous);
        assertEquals(4462, ContainerPreview.aggregate(retained).getFirst().count());
        assertEquals("true", retained.slots().getFirst().nestedContainer().orElseThrow().container().metadata().get(NestedInventoryObservation.RETAINED));
        var other = root(null, Map.of(NestedInventoryObservation.UNAVAILABLE, "41",
                NestedInventoryObservation.IDENTITY_PREFIX + 41, "other-uuid"), changed);
        assertTrue(SnapshotMerger.retainUnavailable(other, previous).slots().getFirst().nestedContainer().isEmpty());
    }
}
