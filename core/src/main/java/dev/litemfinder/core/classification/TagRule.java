package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;

import java.util.Objects;

/** Matches items that a platform adapter associates with one tag. */
public record TagRule(NamespacedId tag) implements ItemRule {

    public TagRule {
        Objects.requireNonNull(tag, "tag must not be null");
    }

    @Override
    public boolean matches(ItemKey item, ItemTagResolver tags) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        return tags.tags(item).contains(tag);
    }
}
