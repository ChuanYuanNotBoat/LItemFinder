package dev.litemfinder.core.view;

import dev.litemfinder.core.model.WorldPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded presentation state: nearby prefix changes interpolate, unrelated polylines fade out then in. */
public final class RouteTransition {
    private final long durationMillis;
    private final double maxDistance;
    private MapRoute target = MapRoute.EMPTY;
    private MapRoute previous = MapRoute.EMPTY;
    private Frame stable = new Frame(MapRoute.EMPTY, 0, null, 1, 0);
    private long startedAt;
    private int prefix = -1;
    private double previousOpacity = 1;
    private boolean changing;

    public RouteTransition() { this(240, 8); }

    public RouteTransition(long durationMillis, double maxDistance) {
        if (durationMillis <= 0 || !Double.isFinite(maxDistance) || maxDistance <= 0) {
            throw new IllegalArgumentException("transition duration and distance must be positive");
        }
        this.durationMillis = durationMillis;
        this.maxDistance = maxDistance;
    }

    public void update(MapRoute next, long nowMillis) {
        Objects.requireNonNull(next, "next must not be null");
        if (next.equals(target)) return;
        if (next.isEmpty() || target.isEmpty() || !target.sameContext(next)) {
            reset(next);
            return;
        }
        // Keep an in-flight fade's clock when only the incoming origin drifts a little.
        if (changing && prefix < 0 && next.interpolatedPrefix(target, maxDistance) >= 0) {
            target = next;
            stable = new Frame(next, 1, null, 1, 0);
            return;
        }
        Frame current = sample(nowMillis);
        previous = current.snapshot();
        previousOpacity = current.opacity();
        target = next;
        stable = new Frame(next, 1, null, 1, 0);
        prefix = previous.interpolatedPrefix(next, maxDistance);
        startedAt = nowMillis;
        changing = true;
    }

    public Frame sample(long nowMillis) {
        if (!changing) return stable;
        double progress = Math.max(0, Math.min(1, (nowMillis - startedAt) / (double) durationMillis));
        if (progress >= 1) {
            changing = false;
            previous = MapRoute.EMPTY;
            return stable;
        }
        if (prefix >= 0) {
            double eased = smooth(progress);
            return new Frame(target, previousOpacity + (1 - previousOpacity) * eased, previous, eased, prefix);
        }
        if (progress < 0.5) {
            return new Frame(previous, previousOpacity * (1 - smooth(progress * 2)), null, 1, 0);
        }
        return new Frame(target, smooth(progress * 2 - 1), null, 1, 0);
    }

    public void clear() { reset(MapRoute.EMPTY); }

    private void reset(MapRoute route) {
        target = route;
        previous = MapRoute.EMPTY;
        stable = new Frame(route, route.isEmpty() ? 0 : 1, null, 1, 0);
        changing = false;
    }

    private static double smooth(double value) { return value * value * (3 - 2 * value); }

    /** Frames share unchanged geometry; sampling a long path does not copy all of its points. */
    public record Frame(MapRoute route, double opacity, MapRoute previous, double progress, int prefix) {
        public Frame {
            Objects.requireNonNull(route, "route must not be null");
            if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1
                    || !Double.isFinite(progress) || progress < 0 || progress > 1 || prefix < 0
                    || (previous != null && (!route.sameContext(previous)
                    || prefix > route.segments().getFirst().points().size()
                    || prefix > previous.segments().getFirst().points().size()))) {
                throw new IllegalArgumentException("invalid route presentation frame");
            }
        }

        public WorldPosition point(int segment, int index) {
            WorldPosition point = route.segments().get(segment).points().get(index);
            return previous != null && segment == 0 && index < prefix
                    ? MapRoute.interpolate(previous.segments().getFirst().points().get(index), point, progress) : point;
        }

        MapRoute snapshot() {
            if (previous == null || prefix == 0) return route;
            List<MapRoute.Segment> segments = new ArrayList<>(route.segments());
            var first = segments.getFirst();
            List<WorldPosition> points = new ArrayList<>(first.points());
            for (int index = 0; index < prefix; index++) points.set(index, point(0, index));
            segments.set(0, new MapRoute.Segment(first.mode(), points));
            return new MapRoute(route.kind(), segments, route.followsPlayer());
        }
    }
}
