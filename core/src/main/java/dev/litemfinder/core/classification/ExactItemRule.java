package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ItemKey;

import java.util.Objects;

/** Matches one exact item identity, including its variant. */
public record ExactItemRule(ItemKey expected) implements ItemRule {

    public ExactItemRule {
        Objects.requireNonNull(expected, "expected must not be null");
    }

    @Override
    public boolean matches(ItemKey item, ItemTagResolver tags) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        return expected.equals(item);
    }
}
