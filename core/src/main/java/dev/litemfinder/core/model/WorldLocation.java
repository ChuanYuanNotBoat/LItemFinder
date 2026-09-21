package dev.litemfinder.core.model;

import java.util.Objects;

/** Block position within a scoped world and dimension. */
public record WorldLocation(
        String scope,
        NamespacedId dimension,
        int x,
        int y,
        int z
) implements ContainerLocation {

    public WorldLocation {
        scope = ModelValidation.nonBlank(scope, "scope");
        Objects.requireNonNull(dimension, "dimension must not be null");
    }

    public WorldLocation(String scope, String dimension, int x, int y, int z) {
        this(scope, NamespacedId.parse(dimension), x, y, z);
    }
}
