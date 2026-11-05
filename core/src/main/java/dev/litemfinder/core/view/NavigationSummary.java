package dev.litemfinder.core.view;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Presentation facts for route summaries; stop coverage describes a calculated route, never player progress. */
public record NavigationSummary(State state, boolean updating, List<WorldLocation> stops,
                                List<Leg> legs, double estimatedSeconds, NavigationReport report) {
    public enum State { IDLE, SCHEMATIC, CALCULATING, FOUND, PARTIAL, UNKNOWN, UNREACHABLE, FAILED }

    public NavigationSummary {
        Objects.requireNonNull(state);
        stops = List.copyOf(stops);
        legs = List.copyOf(legs);
        Objects.requireNonNull(report);
        if (!Double.isFinite(estimatedSeconds) || estimatedSeconds < 0 || report.verifiedStops() > stops.size()) {
            throw new IllegalArgumentException("invalid estimate or stop coverage");
        }
        if (state == State.FOUND && report.verifiedStops() >= 0 && report.verifiedStops() != stops.size()) {
            throw new IllegalArgumentException("a complete route must cover all requested stops");
        }
        if (!stops.isEmpty()) {
            String scope = stops.getFirst().scope();
            if (stops.stream().anyMatch(stop -> !scope.equals(stop.scope()))
                    || legs.stream().flatMap(leg -> leg.points().stream()).anyMatch(point -> !scope.equals(point.scope()))) {
                throw new IllegalArgumentException("summary data must belong to one world scope");
            }
        }
    }

    public record Leg(String mode, List<WorldPosition> points, OptionalDouble estimatedSeconds) {
        public Leg {
            Objects.requireNonNull(mode);
            points = List.copyOf(points);
            Objects.requireNonNull(estimatedSeconds);
            if (mode.isBlank() || points.size() < 2 || estimatedSeconds.isPresent()
                    && (!Double.isFinite(estimatedSeconds.getAsDouble()) || estimatedSeconds.getAsDouble() < 0)) {
                throw new IllegalArgumentException("invalid route leg");
            }
        }
        public WorldPosition start() { return points.getFirst(); }
        public WorldPosition end() { return points.getLast(); }
        public boolean changesDimension() { return !start().dimension().equals(end().dimension()); }
    }

    public OptionalDouble knownSeconds() {
        return state == State.FOUND || state == State.PARTIAL ? OptionalDouble.of(estimatedSeconds) : OptionalDouble.empty();
    }

    public boolean partialEstimate() { return state == State.PARTIAL; }

    public OptionalInt verifiedStops() {
        if (report.verifiedStops() >= 0) return OptionalInt.of(report.verifiedStops());
        return state == State.FOUND ? OptionalInt.of(stops.size()) : OptionalInt.empty();
    }

    public Optional<WorldPosition> gapStart() {
        return state == State.PARTIAL && !legs.isEmpty() ? Optional.of(legs.getLast().end()) : Optional.empty();
    }

    public List<String> modes() { return legs.stream().map(Leg::mode).distinct().toList(); }
}
