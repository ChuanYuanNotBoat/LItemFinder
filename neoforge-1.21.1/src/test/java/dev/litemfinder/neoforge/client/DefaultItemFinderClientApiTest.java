package dev.litemfinder.neoforge.client;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.core.search.SearchQuery;
import dev.litemfinder.neoforge.capture.CaptureReason;
import dev.litemfinder.neoforge.diagnostics.CaptureDiagnostics;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.persistence.SnapshotStorageCoordinator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultItemFinderClientApiTest {

    @Test
    void exposesTypedSearchLookupStatusAndClearOperations() {
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        ItemKey potion = ItemKey.parse("minecraft:potion");
        ItemKey healingPotion = potion.withVariant("components:v1:healing");
        index.update(new InventorySnapshot(
                new ContainerRecord(
                        new ContainerId("test-chest"),
                        ContainerType.CHEST,
                        new LogicalLocation("test-scope", "test-chest")
                ),
                3,
                Instant.parse("2026-09-22T00:00:00Z"),
                List.of(
                        new SlotSnapshot(0, new ItemStackInfo(potion, 1)),
                        new SlotSnapshot(1, new ItemStackInfo(healingPotion, 2)),
                        new SlotSnapshot(2, new ItemStackInfo(ItemKey.parse("minecraft:diamond"), 4))
                )
        ));

        SnapshotStorageCoordinator storage = mock(SnapshotStorageCoordinator.class);
        when(storage.currentIndex()).thenReturn(index);
        when(storage.clearCurrentScopeData()).thenReturn(1);
        CaptureDiagnostics diagnostics = new CaptureDiagnostics();
        diagnostics.captured(CaptureReason.OPEN_STABLE);
        diagnostics.skipped("unsupported_menu:test");
        DefaultItemFinderClientApi api = new DefaultItemFinderClientApi(
                storage,
                new CachedItemTagResolver(),
                diagnostics
        );

        var search = api.search(SearchQuery.all().withText("potion"));
        var lookup = api.findByItemId(NamespacedId.parse("minecraft:potion"));
        var status = api.status();

        assertEquals(2, search.results().size());
        assertEquals(3, search.totalCount());
        assertEquals(3, lookup.totalCount());
        assertEquals(2, lookup.variantCount());
        assertEquals(1, lookup.rootContainerCount());
        assertEquals(1, status.rootContainers());
        assertEquals(3, status.entries());
        assertEquals(3, status.variants());
        assertEquals(1, status.capture().captured());
        assertEquals(1, status.capture().captureReasons().get("OPEN_STABLE"));
        assertEquals(1, status.capture().skipped());

        var cleared = api.clearCurrentScopeData();

        assertTrue(cleared.successful());
        assertEquals(1, cleared.deletedRootSnapshots());
        assertEquals(0, diagnostics.snapshot().totalCaptures());
        verify(storage).clearCurrentScopeData();
    }

    @Test
    void preservesFailureAsTypedResultAndKeepsDiagnostics() {
        SnapshotStorageCoordinator storage = mock(SnapshotStorageCoordinator.class);
        when(storage.clearCurrentScopeData()).thenReturn(-1);
        CaptureDiagnostics diagnostics = new CaptureDiagnostics();
        diagnostics.removed("test-removal");
        DefaultItemFinderClientApi api = new DefaultItemFinderClientApi(
                storage,
                new CachedItemTagResolver(),
                diagnostics
        );

        var result = api.clearCurrentScopeData();

        assertFalse(result.successful());
        assertEquals(0, result.deletedRootSnapshots());
        assertEquals(1, diagnostics.snapshot().totalRemoved());
    }
}
