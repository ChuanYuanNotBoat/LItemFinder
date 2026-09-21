package dev.litemfinder.core.search;

import java.util.List;
import java.util.Objects;

/** Results for a query that may match multiple item identities. */
public record SearchResponse(List<SearchResult> results) {

    public SearchResponse {
        results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
    }

    public long totalCount() {
        long total = 0;
        for (SearchResult result : results) {
            total = Math.addExact(total, result.totalCount());
        }
        return total;
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }
}
