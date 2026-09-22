package dev.litemfinder.navigation;

import java.util.Objects;

/** A position inside one privacy-scoped world and dimension. */
public record Waypoint(String scope, String dimension, int x, int y, int z) {

    public Waypoint {
        scope = nonBlank(scope, "scope");
        dimension = nonBlank(dimension, "dimension");
    }

    public double distanceTo(Waypoint other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!scope.equals(other.scope) || !dimension.equals(other.dimension)) {
            throw new IllegalArgumentException("distance requires matching scope and dimension");
        }
        double dx = (double) x - other.x;
        double dy = (double) y - other.y;
        double dz = (double) z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
