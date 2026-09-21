package dev.litemfinder.core.optimizer;

import dev.litemfinder.core.model.ContainerPath;

import java.util.Objects;

/** Exact source slot, including the path through nested containers. */
public record StorageSlotRef(ContainerPath path, int slot) {

    public StorageSlotRef {
        Objects.requireNonNull(path, "path must not be null");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
    }
}
