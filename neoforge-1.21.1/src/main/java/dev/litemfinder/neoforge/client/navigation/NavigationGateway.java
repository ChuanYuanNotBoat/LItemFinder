package dev.litemfinder.neoforge.client.navigation;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.view.NavigationReport;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.CompletionStage;

/** Optional bridge implemented by a separate navigation Mod; stock features never require it. */
public interface NavigationGateway {

    /** Implementations perform expensive search off the render thread. */
    CompletionStage<Overlay> route(WorldPosition origin, List<WorldLocation> stops);

    /** Changes when observations supporting a published route become invalid. */
    default long revision() { return 0; }

    static Optional<NavigationGateway> discover() {
        try {
            return ServiceLoader.load(NavigationGateway.class).findFirst();
        } catch (ServiceConfigurationError unavailable) {
            return Optional.empty();
        }
    }

    record Overlay(Status status, List<Segment> segments, double estimatedSeconds, NavigationReport report) {

        /** Keeps the existing provider constructor usable; evidence is explicitly unknown. */
        public Overlay(Status status, List<Segment> segments, double estimatedSeconds) {
            this(status, segments, estimatedSeconds, NavigationReport.unspecified());
        }

        public Overlay {
            Objects.requireNonNull(status, "status must not be null");
            segments = List.copyOf(Objects.requireNonNull(segments, "segments must not be null"));
            Objects.requireNonNull(report, "report must not be null");
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
    record Segment(String mode, List<WorldPosition> points, OptionalDouble estimatedSeconds) {

        public Segment(String mode, List<WorldPosition> points) { this(mode, points, OptionalDouble.empty()); }

        public Segment {
            Objects.requireNonNull(mode, "mode must not be null");
            points = List.copyOf(Objects.requireNonNull(points, "points must not be null"));
            Objects.requireNonNull(estimatedSeconds, "estimatedSeconds must not be null");
            if (mode.isBlank() || points.size() < 2) {
                throw new IllegalArgumentException("a path segment needs a mode and at least two points");
            }
            if (estimatedSeconds.isPresent() && (!Double.isFinite(estimatedSeconds.getAsDouble()) || estimatedSeconds.getAsDouble() < 0)) {
                throw new IllegalArgumentException("leg estimate must be finite and non-negative");
            }
        }
    }
}
