package dev.litemfinder.core.search;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.index.StorageIndex;
import dev.litemfinder.core.model.ItemKey;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Exact-match search backed by a {@link StorageIndex}. */
public final class IndexedSearchEngine implements SearchEngine {

    private static final Comparator<StorageEntry> RESULT_ORDER =
            Comparator.comparing(StorageEntry::observedAt).reversed()
                    .thenComparing(entry -> entry.rootContainer().id())
                    .thenComparingInt(entry -> entry.path().hops().size())
                    .thenComparingInt(StorageEntry::slot);

    private final StorageIndex index;

    public IndexedSearchEngine(StorageIndex index) {
        this.index = Objects.requireNonNull(index, "index must not be null");
    }

    @Override
    public SearchResult findExact(ItemKey item) {
        Objects.requireNonNull(item, "item must not be null");
        List<StorageEntry> orderedEntries = index.findExact(item).stream()
                .sorted(RESULT_ORDER)
                .toList();
        return new SearchResult(item, orderedEntries);
    }
}
