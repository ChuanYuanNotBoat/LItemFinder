package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.search.SearchResponse;
import dev.litemfinder.core.search.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, display-ready grouping of the current exact-variant search results. */
public record InventoryOverview(List<ItemRow> rows) {

    public InventoryOverview {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
    }

    public static InventoryOverview from(SearchResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        Map<NamespacedId, List<VariantRow>> variantsById = new LinkedHashMap<>();
        for (SearchResult result : response.results()) {
            variantsById.computeIfAbsent(result.item().itemId(), ignored -> new ArrayList<>())
                    .add(new VariantRow(result.item(), result.totalCount()));
        }
        List<ItemRow> rows = variantsById.entrySet().stream()
                .map(entry -> new ItemRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(ItemRow::totalCount).reversed()
                        .thenComparing(ItemRow::itemId))
                .toList();
        return new InventoryOverview(rows);
    }

    public record ItemRow(NamespacedId itemId, List<VariantRow> variants) {

        public ItemRow {
            Objects.requireNonNull(itemId, "itemId must not be null");
            variants = List.copyOf(Objects.requireNonNull(variants, "variants must not be null"));
            if (variants.isEmpty() || variants.stream().anyMatch(row -> !row.item().itemId().equals(itemId))) {
                throw new IllegalArgumentException("variants must be nonempty and match itemId");
            }
        }

        public long totalCount() {
            long total = 0;
            for (VariantRow variant : variants) {
                total = Math.addExact(total, variant.count());
            }
            return total;
        }
    }

    public record VariantRow(ItemKey item, long count) {

        public VariantRow {
            Objects.requireNonNull(item, "item must not be null");
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
        }
    }
}
