package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.classification.ItemTagResolver;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe tag view populated on the client thread and read without touching game registries. */
public final class CachedItemTagResolver implements ItemTagResolver {

    private final ConcurrentMap<NamespacedId, Set<NamespacedId>> tagsByItem = new ConcurrentHashMap<>();

    public void remember(MappedItemStack mapped) {
        Objects.requireNonNull(mapped, "mapped must not be null");
        tagsByItem.put(mapped.stack().item().itemId(), mapped.tags());
    }

    @Override
    public Set<NamespacedId> tags(ItemKey item) {
        Objects.requireNonNull(item, "item must not be null");
        return tagsByItem.getOrDefault(item.itemId(), Set.of());
    }

    public void clear() {
        tagsByItem.clear();
    }

    public int cachedItemCount() {
        return tagsByItem.size();
    }
}
