package dev.litemfinder.core.model;

import java.util.Objects;

/** Exact fluid identity; adapters normalize native components into an opaque variant. */
public record FluidKey(NamespacedId fluidId, String variant) {
    public FluidKey { Objects.requireNonNull(fluidId); Objects.requireNonNull(variant); }
}
