package dev.litemfinder.core.model;

import java.util.Objects;
import java.util.Optional;

/** Content observed in one occupied slot. */
public record SlotSnapshot(
        int slot,
        ItemStackInfo stack,
        Optional<InventorySnapshot> nestedContainer
) {

    public SlotSnapshot {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        Objects.requireNonNull(stack, "stack must not be null");
        Objects.requireNonNull(nestedContainer, "nestedContainer must not be null");
    }

    public SlotSnapshot(int slot, ItemStackInfo stack) {
        this(slot, stack, Optional.empty());
    }

    public static SlotSnapshot nested(int slot, ItemStackInfo stack, InventorySnapshot nestedContainer) {
        return new SlotSnapshot(slot, stack, Optional.of(
                Objects.requireNonNull(nestedContainer, "nestedContainer must not be null")
        ));
    }
}
