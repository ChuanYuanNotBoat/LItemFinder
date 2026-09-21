package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;

import java.util.Objects;

/** Matches every item registered in one namespace. */
public record NamespaceRule(String namespace) implements ItemRule {

    public NamespaceRule {
        namespace = new NamespacedId(namespace, "validation").namespace();
    }

    @Override
    public boolean matches(ItemKey item, ItemTagResolver tags) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        return namespace.equals(item.namespace());
    }
}
