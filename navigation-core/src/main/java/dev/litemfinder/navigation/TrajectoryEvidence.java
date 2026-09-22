package dev.litemfinder.navigation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Measured travel times refine known edges; observations never define the search frontier. */
public final class TrajectoryEvidence {

    private final Map<Key, Measurement> times = new HashMap<>();

    public synchronized void record(Waypoint from, Waypoint to, TravelMode mode, double seconds) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("travel time must be finite and non-negative");
        }
        times.compute(new Key(from, to, mode), (ignored, old) -> old == null
                ? new Measurement(seconds, 1) : old.add(seconds));
    }

    public synchronized OptionalDouble estimate(TravelEdge edge) {
        Objects.requireNonNull(edge, "edge must not be null");
        Measurement value = times.get(new Key(edge.from(), edge.to(), edge.mode()));
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value.average());
    }

    public synchronized void clear() {
        times.clear();
    }

    private record Key(Waypoint from, Waypoint to, TravelMode mode) {
    }

    private record Measurement(double average, long samples) {

        private Measurement add(double seconds) {
            long nextCount = Math.addExact(samples, 1);
            return new Measurement(average + (seconds - average) / nextCount, nextCount);
        }
    }
}
