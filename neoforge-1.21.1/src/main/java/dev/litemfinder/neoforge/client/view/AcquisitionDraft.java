package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.model.ItemKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Session-only requests keyed by exact variant. No slots are clicked or items moved. */
public final class AcquisitionDraft {

    private final Map<ItemKey, Long> requested = new LinkedHashMap<>();

    public long get(ItemKey item) {
        return requested.getOrDefault(Objects.requireNonNull(item), 0L);
    }

    public void set(ItemKey item, long count, long upperBound) {
        Objects.requireNonNull(item, "item must not be null");
        if (count < 0 || upperBound < 0) {
            throw new IllegalArgumentException("count and upperBound must be non-negative");
        }
        long bounded = Math.min(count, upperBound);
        if (bounded == 0) {
            requested.remove(item);
        } else {
            requested.put(item, bounded);
        }
    }

    /** Shrinks requests to the latest recorded totals; callers should surface any changed values. */
    public void reconcile(InventoryOverview overview) {
        Objects.requireNonNull(overview, "overview must not be null");
        Map<ItemKey, Long> available = new LinkedHashMap<>();
        overview.rows().forEach(row -> row.variants().forEach(variant ->
                available.put(variant.item(), variant.count())));
        requested.replaceAll((item, count) -> Math.min(count, available.getOrDefault(item, 0L)));
        requested.values().removeIf(count -> count == 0);
    }

    public Map<ItemKey, Long> selections() {
        return Map.copyOf(requested);
    }

    public long selectedFor(InventoryOverview.ItemRow row) {
        Objects.requireNonNull(row, "row must not be null");
        long total = 0;
        for (InventoryOverview.VariantRow variant : row.variants()) {
            total = Math.addExact(total, get(variant.item()));
        }
        return total;
    }

    public void clear() {
        requested.clear();
    }
}
