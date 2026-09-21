package dev.litemfinder.core.index;

import dev.litemfinder.core.model.ContainerPath;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ItemStackInfo;

import java.time.Instant;
import java.util.Objects;

/** One indexed item stack and the complete route to its containing slot. */
public record StorageEntry(
        ContainerRecord rootContainer,
        ContainerRecord containingContainer,
        ContainerPath path,
        int slot,
        ItemStackInfo stack,
        Instant observedAt
) {

    public StorageEntry {
        Objects.requireNonNull(rootContainer, "rootContainer must not be null");
        Objects.requireNonNull(containingContainer, "containingContainer must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        Objects.requireNonNull(stack, "stack must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");

        if (!rootContainer.id().equals(path.root())) {
            throw new IllegalArgumentException("path root must match rootContainer id");
        }
        if (!containingContainer.id().equals(path.leaf())) {
            throw new IllegalArgumentException("path leaf must match containingContainer id");
        }
    }
}
