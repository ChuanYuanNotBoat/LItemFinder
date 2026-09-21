package dev.litemfinder.core.index;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Thread-safe in-memory implementation intended for tests and non-persistent sessions. */
public final class InMemoryStorageIndex implements StorageIndex {

    private final SnapshotFlattener flattener;
    private final Map<ContainerId, IndexedRoot> roots = new LinkedHashMap<>();
    private final Map<ItemKey, List<StorageEntry>> entriesByItem = new HashMap<>();

    public InMemoryStorageIndex() {
        this(new SnapshotFlattener());
    }

    public InMemoryStorageIndex(SnapshotFlattener flattener) {
        this.flattener = Objects.requireNonNull(flattener, "flattener must not be null");
    }

    @Override
    public IndexUpdateResult update(InventorySnapshot rootSnapshot) {
        Objects.requireNonNull(rootSnapshot, "rootSnapshot must not be null");
        List<StorageEntry> newEntries = flattener.flatten(rootSnapshot);

        synchronized (this) {
            ContainerId rootId = rootSnapshot.container().id();
            IndexedRoot existing = roots.get(rootId);
            if (existing != null && rootSnapshot.capturedAt().isBefore(existing.snapshot().capturedAt())) {
                return IndexUpdateResult.IGNORED_STALE;
            }

            if (existing != null) {
                removeEntries(existing.entries());
            }

            IndexedRoot replacement = new IndexedRoot(rootSnapshot, newEntries);
            roots.put(rootId, replacement);
            addEntries(newEntries);
            return existing == null ? IndexUpdateResult.ADDED : IndexUpdateResult.REPLACED;
        }
    }

    @Override
    public synchronized boolean remove(ContainerId rootContainerId) {
        Objects.requireNonNull(rootContainerId, "rootContainerId must not be null");
        IndexedRoot removed = roots.remove(rootContainerId);
        if (removed == null) {
            return false;
        }
        removeEntries(removed.entries());
        return true;
    }

    @Override
    public synchronized List<StorageEntry> findExact(ItemKey item) {
        Objects.requireNonNull(item, "item must not be null");
        return List.copyOf(entriesByItem.getOrDefault(item, List.of()));
    }

    @Override
    public synchronized List<StorageEntry> allEntries() {
        return roots.values().stream()
                .flatMap(root -> root.entries().stream())
                .toList();
    }

    @Override
    public synchronized List<InventorySnapshot> rootSnapshots() {
        return roots.values().stream().map(IndexedRoot::snapshot).toList();
    }

    @Override
    public synchronized Optional<Instant> latestCaptureTime(ContainerId rootContainerId) {
        Objects.requireNonNull(rootContainerId, "rootContainerId must not be null");
        IndexedRoot root = roots.get(rootContainerId);
        return root == null ? Optional.empty() : Optional.of(root.snapshot().capturedAt());
    }

    @Override
    public synchronized int rootContainerCount() {
        return roots.size();
    }

    private void addEntries(List<StorageEntry> entries) {
        for (StorageEntry entry : entries) {
            entriesByItem.computeIfAbsent(entry.stack().item(), ignored -> new ArrayList<>()).add(entry);
        }
    }

    private void removeEntries(List<StorageEntry> entries) {
        for (StorageEntry entry : entries) {
            ItemKey item = entry.stack().item();
            List<StorageEntry> indexedEntries = entriesByItem.get(item);
            if (indexedEntries == null) {
                continue;
            }
            indexedEntries.remove(entry);
            if (indexedEntries.isEmpty()) {
                entriesByItem.remove(item);
            }
        }
    }

    private record IndexedRoot(InventorySnapshot snapshot, List<StorageEntry> entries) {

        private IndexedRoot {
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
        }
    }
}
