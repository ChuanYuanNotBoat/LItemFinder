package dev.litemfinder.core.model;

import java.util.Objects;
import java.util.OptionalDouble;

/** Precise point used for distance calculations without depending on a game vector type. */
public record WorldPosition(
        String scope,
        NamespacedId dimension,
        double x,
        double y,
        double z
) {

    public WorldPosition {
        scope = ModelValidation.nonBlank(scope, "scope");
        Objects.requireNonNull(dimension, "dimension must not be null");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
    }

    public WorldPosition(String scope, String dimension, double x, double y, double z) {
        this(scope, NamespacedId.parse(dimension), x, y, z);
    }

    public static WorldPosition at(WorldLocation location) {
        Objects.requireNonNull(location, "location must not be null");
        return new WorldPosition(
                location.scope(),
                location.dimension(),
                location.x(),
                location.y(),
                location.z()
        );
    }

    public OptionalDouble distanceTo(ContainerLocation location) {
        Objects.requireNonNull(location, "location must not be null");
        if (!(location instanceof WorldLocation world)
                || !scope.equals(world.scope())
                || !dimension.equals(world.dimension())) {
            return OptionalDouble.empty();
        }
        double deltaX = x - world.x();
        double deltaY = y - world.y();
        double deltaZ = z - world.z();
        return OptionalDouble.of(Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ));
    }
}
