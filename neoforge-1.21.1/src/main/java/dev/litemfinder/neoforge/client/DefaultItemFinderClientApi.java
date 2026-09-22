package dev.litemfinder.neoforge.client;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.search.IndexedSearchEngine;
import dev.litemfinder.core.search.SearchQuery;
import dev.litemfinder.core.search.SearchResponse;
import dev.litemfinder.neoforge.client.view.InventoryOverview;
import dev.litemfinder.neoforge.client.view.ContainerOverview;
import dev.litemfinder.neoforge.diagnostics.CaptureDiagnostics;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.persistence.SnapshotStorageCoordinator;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Default live implementation shared by client presentation surfaces. */
public final class DefaultItemFinderClientApi implements ItemFinderClientApi {

    private static final Comparator<StorageEntry> ENTRY_ORDER =
            Comparator.comparing(StorageEntry::observedAt).reversed()
                    .thenComparing(entry -> entry.rootContainer().id())
                    .thenComparingInt(entry -> entry.path().hops().size())
                    .thenComparingInt(StorageEntry::slot);

    private final SnapshotStorageCoordinator storage;
    private final CachedItemTagResolver tags;
    private final CaptureDiagnostics diagnostics;

    public DefaultItemFinderClientApi(
            SnapshotStorageCoordinator storage,
            CachedItemTagResolver tags,
            CaptureDiagnostics diagnostics
    ) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.tags = Objects.requireNonNull(tags, "tags must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    @Override
    public SearchResponse search(SearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return new IndexedSearchEngine(storage.currentIndex(), tags).search(query);
    }

    @Override
    public InventoryOverview overview() {
        return InventoryOverview.from(search(SearchQuery.all()));
    }

    @Override
    public ContainerOverview containerOverview() {
        return ContainerOverview.from(storage.currentIndex().rootSnapshots());
    }

    @Override
    public ItemLookup findByItemId(NamespacedId itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        var entries = storage.currentIndex().allEntries().stream()
                .filter(entry -> entry.stack().item().itemId().equals(itemId))
                .sorted(ENTRY_ORDER)
                .toList();
        return new ItemLookup(itemId, entries);
    }

    @Override
    public IndexStatus status() {
        var index = storage.currentIndex();
        var entries = index.allEntries();
        var snapshot = diagnostics.snapshot();
        return new IndexStatus(
                index.rootContainerCount(),
                entries.size(),
                entries.stream().map(entry -> entry.stack().item()).distinct().count(),
                tags.cachedItemCount(),
                new CaptureStatus(
                        snapshot.totalCaptures(),
                        snapshot.totalSkipped(),
                        snapshot.totalDegraded(),
                        snapshot.totalRemoved(),
                        snapshot.captures().entrySet().stream().collect(Collectors.toMap(
                                entry -> entry.getKey().name(),
                                Map.Entry::getValue,
                                (left, right) -> left,
                                TreeMap::new
                        )),
                        snapshot.skipped(),
                        snapshot.degraded(),
                        snapshot.removed()
                )
        );
    }

    @Override
    public ClearResult clearCurrentScopeData() {
        int deleted = storage.clearCurrentScopeData();
        if (deleted < 0) {
            return ClearResult.failure();
        }
        diagnostics.clear();
        return ClearResult.success(deleted);
    }
}
