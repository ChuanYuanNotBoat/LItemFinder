package dev.litemfinder.neoforge.client.navigation;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.CompletionStage;

/** Optional bridge implemented by a separate navigation Mod; stock features never require it. */
public interface NavigationGateway {

    /** Implementations perform expensive search off the render thread. */
    CompletionStage<Overlay> route(WorldPosition origin, List<WorldLocation> stops);

    static Optional<NavigationGateway> discover() {
        try {
            return ServiceLoader.load(NavigationGateway.class).findFirst();
        } catch (ServiceConfigurationError unavailable) {
            return Optional.empty();
        }
    }

    record Overlay(Status status, List<Segment> segments, double estimatedSeconds) {

        public Overlay {
            Objects.requireNonNull(status, "status must not be null");
            segments = List.copyOf(Objects.requireNonNull(segments, "segments must not be null"));
            if (!Double.isFinite(estimatedSeconds) || estimatedSeconds < 0) {
                throw new IllegalArgumentException("estimatedSeconds must be finite and non-negative");
            }
        }
    }

    enum Status {
        FOUND,
        PARTIAL,
        UNKNOWN,
        UNREACHABLE
    }

    /** Consecutive positions form a path for one travel mode; cross-dimension transitions may have two points. */
    record Segment(String mode, List<WorldPosition> points) {

        public Segment {
            Objects.requireNonNull(mode, "mode must not be null");
            points = List.copyOf(Objects.requireNonNull(points, "points must not be null"));
            if (mode.isBlank() || points.size() < 2) {
                throw new IllegalArgumentException("a path segment needs a mode and at least two points");
            }
        }
    }
}
