package dev.litemfinder.navigation;

import java.util.List;
import java.util.Objects;

/** Known path, partial known path, or a deliberately incomplete outcome. */
public record NavigationResult(Status status, List<TravelEdge> edges, double estimatedSeconds) {

    public NavigationResult {
        Objects.requireNonNull(status, "status must not be null");
        edges = List.copyOf(Objects.requireNonNull(edges, "edges must not be null"));
        if (!Double.isFinite(estimatedSeconds) || estimatedSeconds < 0) {
            throw new IllegalArgumentException("estimatedSeconds must be finite and non-negative");
        }
    }

    public enum Status {
        FOUND,
        PARTIAL,
        UNKNOWN,
        UNREACHABLE
    }

    public static NavigationResult unknown() {
        return new NavigationResult(Status.UNKNOWN, List.of(), 0);
    }
}
