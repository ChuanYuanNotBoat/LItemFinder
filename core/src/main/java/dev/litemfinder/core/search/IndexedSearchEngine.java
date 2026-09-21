package dev.litemfinder.core.search;

import dev.litemfinder.core.classification.ItemTagResolver;
import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.index.StorageIndex;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.WorldPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact-match search backed by a {@link StorageIndex}. */
public final class IndexedSearchEngine implements SearchEngine {

    private static final Comparator<StorageEntry> DEFAULT_ENTRY_ORDER =
            Comparator.comparing(StorageEntry::observedAt).reversed()
                    .thenComparing(entry -> entry.rootContainer().id())
                    .thenComparingInt(entry -> entry.path().hops().size())
                    .thenComparingInt(StorageEntry::slot);

    private final StorageIndex index;
    private final ItemTagResolver tags;

    public IndexedSearchEngine(StorageIndex index) {
        this(index, ItemTagResolver.empty());
    }

    public IndexedSearchEngine(StorageIndex index, ItemTagResolver tags) {
        this.index = Objects.requireNonNull(index, "index must not be null");
        this.tags = Objects.requireNonNull(tags, "tags must not be null");
    }

    @Override
    public SearchResult findExact(ItemKey item) {
        Objects.requireNonNull(item, "item must not be null");
        List<StorageEntry> orderedEntries = index.findExact(item).stream()
                .sorted(DEFAULT_ENTRY_ORDER)
                .toList();
        return new SearchResult(item, orderedEntries);
    }

    @Override
    public SearchResponse search(SearchQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Map<ItemKey, List<StorageEntry>> entriesByItem = new LinkedHashMap<>();
        for (StorageEntry entry : index.allEntries()) {
            ItemKey item = entry.stack().item();
            if (matchesIdentity(item, query)) {
                entriesByItem.computeIfAbsent(item, ignored -> new ArrayList<>()).add(entry);
            }
        }

        List<SearchResult> results = entriesByItem.entrySet().stream()
                .map(entry -> new SearchResult(
                        entry.getKey(),
                        entry.getValue().stream().sorted(entryOrder(query.origin())).toList()
                ))
                .filter(result -> result.totalCount() >= query.minimumTotalCount())
                .sorted(Comparator.comparingLong(SearchResult::totalCount).reversed()
                        .thenComparing(SearchResult::item))
                .toList();
        return new SearchResponse(results);
    }

    private boolean matchesIdentity(ItemKey item, SearchQuery query) {
        if (!query.namespace().isEmpty() && !query.namespace().equals(item.namespace())) {
            return false;
        }
        if (!query.text().isEmpty()
                && !item.itemId().toString().contains(query.text())) {
            return false;
        }
        return tags.tags(item).containsAll(query.requiredTags());
    }

    private Comparator<StorageEntry> entryOrder(java.util.Optional<WorldPosition> origin) {
        if (origin.isEmpty()) {
            return DEFAULT_ENTRY_ORDER;
        }
        WorldPosition position = origin.orElseThrow();
        return Comparator.comparingDouble((StorageEntry entry) -> position
                        .distanceTo(entry.rootContainer().location())
                        .orElse(Double.POSITIVE_INFINITY))
                .thenComparing(DEFAULT_ENTRY_ORDER);
    }
}
