package dev.litemfinder.core.search;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.ItemKey;

import java.util.List;
import java.util.Objects;

/** Immutable exact-match search result. */
public record SearchResult(ItemKey item, List<StorageEntry> entries) {

    public SearchResult {
        Objects.requireNonNull(item, "item must not be null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
        if (entries.stream().anyMatch(entry -> !entry.stack().item().equals(item))) {
            throw new IllegalArgumentException("all entries must match the result item");
        }
    }

    public long totalCount() {
        long total = 0;
        for (StorageEntry entry : entries) {
            total = Math.addExact(total, entry.stack().count());
        }
        return total;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
