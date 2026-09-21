package dev.litemfinder.core.planner;

import dev.litemfinder.core.model.ItemKey;

import java.util.Objects;

/** Positive requested quantity for one exact item identity. */
public record ItemRequirement(ItemKey item, long amount) {

    public ItemRequirement {
        Objects.requireNonNull(item, "item must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
