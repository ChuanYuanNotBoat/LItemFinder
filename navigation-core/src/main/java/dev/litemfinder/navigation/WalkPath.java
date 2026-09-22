package dev.litemfinder.navigation;

import java.util.List;
import java.util.Objects;

/** A path through verified walking connections only. */
public record WalkPath(NavigationResult.Status status, List<Waypoint> points, double estimatedSeconds) {

    public WalkPath {
        Objects.requireNonNull(status, "status must not be null");
        points = List.copyOf(Objects.requireNonNull(points, "points must not be null"));
        if (!Double.isFinite(estimatedSeconds) || estimatedSeconds < 0) {
            throw new IllegalArgumentException("estimatedSeconds must be finite and non-negative");
        }
    }
}
