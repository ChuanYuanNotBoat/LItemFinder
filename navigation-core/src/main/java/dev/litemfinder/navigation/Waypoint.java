package dev.litemfinder.navigation;

import java.util.Objects;

/** A position inside one privacy-scoped world and dimension. */
public record Waypoint(String scope, String dimension, double x, double y, double z) {

    public Waypoint {
        scope = nonBlank(scope, "scope");
        dimension = nonBlank(dimension, "dimension");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("waypoint coordinates must be finite");
        }
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
