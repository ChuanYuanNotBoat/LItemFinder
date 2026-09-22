package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.model.NamespacedId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Session-only requested counts; source and variant allocation are a later phase. */
public final class AcquisitionDraft {

    private final Map<NamespacedId, Long> requested = new LinkedHashMap<>();

    public long get(NamespacedId itemId) {
        return requested.getOrDefault(Objects.requireNonNull(itemId), 0L);
    }

    public void set(NamespacedId itemId, long count, long upperBound) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        if (count < 0 || upperBound < 0) {
            throw new IllegalArgumentException("count and upperBound must be non-negative");
        }
        long bounded = Math.min(count, upperBound);
        if (bounded == 0) {
            requested.remove(itemId);
        } else {
            requested.put(itemId, bounded);
        }
    }

    public void reconcile(InventoryOverview overview) {
        Objects.requireNonNull(overview, "overview must not be null");
        Map<NamespacedId, Long> available = new LinkedHashMap<>();
        overview.rows().forEach(row -> available.put(row.itemId(), row.totalCount()));
        requested.replaceAll((item, count) -> Math.min(count, available.getOrDefault(item, 0L)));
        requested.values().removeIf(count -> count == 0);
    }

    public Map<NamespacedId, Long> selections() {
        return Map.copyOf(requested);
    }

    public void clear() {
        requested.clear();
    }
}
