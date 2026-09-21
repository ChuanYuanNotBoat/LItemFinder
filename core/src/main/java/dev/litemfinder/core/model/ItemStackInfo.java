package dev.litemfinder.core.model;

import java.util.Objects;

/** A positive quantity of one item identity. */
public record ItemStackInfo(ItemKey item, long count) {

    public ItemStackInfo {
        Objects.requireNonNull(item, "item must not be null");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
