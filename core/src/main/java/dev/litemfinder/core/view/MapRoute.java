package dev.litemfinder.core.view;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable geometry for presenting recorded stops or provider paths; it performs no pathfinding. */
public record MapRoute(Kind kind, List<Segment> segments, boolean followsPlayer) {
    public enum Kind { SCHEMATIC, NAVIGATION }
    public static final MapRoute EMPTY = new MapRoute(Kind.SCHEMATIC, List.of(), false);

    public MapRoute {
        Objects.requireNonNull(kind, "kind must not be null");
        segments = List.copyOf(segments);
        if (kind == Kind.NAVIGATION && followsPlayer) {
            throw new IllegalArgumentException("a provider path cannot be attached to an unverified live player position");
        }
        if (!segments.isEmpty()) {
            String scope = segments.getFirst().points().getFirst().scope();
            if (segments.stream().flatMap(segment -> segment.points().stream())
                    .anyMatch(point -> !point.scope().equals(scope))) {
                throw new IllegalArgumentException("a route must belong to one world scope");
            }
        }
    }

    public record Segment(String mode, List<WorldPosition> points) {
        public Segment {
            Objects.requireNonNull(mode, "mode must not be null");
            points = List.copyOf(points);
            if (mode.isBlank() || points.size() < 2) {
                throw new IllegalArgumentException("a route segment needs a mode and at least two points");
            }
        }
    }

    public static MapRoute schematic(WorldPosition origin, List<WorldLocation> stops) {
        Objects.requireNonNull(stops, "stops must not be null");
        if (stops.isEmpty()) return EMPTY;
        String scope = origin == null ? stops.getFirst().scope() : origin.scope();
        List<WorldPosition> points = new ArrayList<>();
        if (origin != null) points.add(origin);
        for (WorldLocation stop : stops) {
            if (stop.scope().equals(scope)) points.add(WorldPosition.at(stop));
        }
        return points.size() < 2 ? EMPTY
                : new MapRoute(Kind.SCHEMATIC, List.of(new Segment("SCHEMATIC", points)), origin != null);
    }

    public boolean isEmpty() { return segments.isEmpty(); }
    public WorldPosition start() { return segments.getFirst().points().getFirst(); }

    /** A changed world or starting dimension must never retain a previous world's visual state. */
    public boolean sameContext(MapRoute other) {
        return !isEmpty() && !other.isEmpty() && sameSpace(start(), other.start());
    }

    /** Only a short changed prefix before an unchanged anchor may be interpolated. */
    int interpolatedPrefix(MapRoute other, double maxDistance) {
        if (kind != other.kind || followsPlayer != other.followsPlayer || !sameContext(other)
                || segments.size() != other.segments.size()) return -1;
        for (int index = 1; index < segments.size(); index++) {
            if (!segments.get(index).equals(other.segments.get(index))) return -1;
        }
        Segment before = segments.getFirst();
        Segment after = other.segments.getFirst();
        if (!before.mode().equals(after.mode()) || before.points().size() != after.points().size()) return -1;
        int prefix = before.points().size();
        while (prefix > 0 && before.points().get(prefix - 1).equals(after.points().get(prefix - 1))) prefix--;
        if (prefix == before.points().size() || prefix > 8) return -1;
        for (int index = 0; index < prefix; index++) {
            WorldPosition a = before.points().get(index);
            WorldPosition b = after.points().get(index);
            if (!near(a, b, maxDistance) || !near(a, start(), maxDistance)
                    || !near(b, other.start(), maxDistance)) return -1;
        }
        return prefix;
    }

    public static boolean sameSpace(WorldPosition a, WorldPosition b) {
        return a.scope().equals(b.scope()) && a.dimension().equals(b.dimension());
    }

    public static boolean near(WorldPosition a, WorldPosition b, double distance) {
        return sameSpace(a, b) && Math.hypot(Math.hypot(a.x() - b.x(), a.z() - b.z()), a.y() - b.y()) <= distance;
    }

    static WorldPosition interpolate(WorldPosition from, WorldPosition to, double progress) {
        return new WorldPosition(to.scope(), to.dimension(),
                from.x() + (to.x() - from.x()) * progress,
                from.y() + (to.y() - from.y()) * progress,
                from.z() + (to.z() - from.z()) * progress);
    }
}
