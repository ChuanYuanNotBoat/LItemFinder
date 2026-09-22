package dev.litemfinder.navigation;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded, scope-aware cache of verified standable points and verified walking connections. */
public final class PassabilityCache {

    private final int capacity;
    private final LinkedHashMap<Waypoint, Node> nodes = new LinkedHashMap<>(16, 0.75f, true);

    public PassabilityCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void observe(Waypoint point, Instant observedAt) {
        Objects.requireNonNull(point, "point must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        nodes.compute(point, (ignored, existing) -> existing == null
                ? new Node(observedAt) : existing.refresh(observedAt));
        trim();
    }

    /** The platform adapter must call this only after checking collision and movement rules. */
    public synchronized void observeConnection(Waypoint from, Waypoint to, Instant observedAt) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (from.equals(to) || !from.scope().equals(to.scope())
                || !from.dimension().equals(to.dimension())) {
            throw new IllegalArgumentException("walking connections require distinct points in one dimension");
        }
        if (capacity < 2) {
            throw new IllegalStateException("a walking connection requires capacity for two points");
        }
        observe(from, observedAt);
        observe(to, observedAt);
        double distance = from.distanceTo(to);
        Connection connection = new Connection(distance, observedAt);
        nodes.get(from).connections.put(to, connection);
        nodes.get(to).connections.put(from, connection);
    }

    public synchronized void invalidate(Waypoint point) {
        Objects.requireNonNull(point, "point must not be null");
        nodes.remove(point);
        nodes.values().forEach(node -> node.connections.remove(point));
    }

    public synchronized void clear() {
        nodes.clear();
    }

    public synchronized int size() {
        return nodes.size();
    }

    public synchronized boolean known(Waypoint point, Instant now, Duration maxAge) {
        Node node = nodes.get(Objects.requireNonNull(point));
        return valid(node, Objects.requireNonNull(now), Objects.requireNonNull(maxAge));
    }

    public synchronized Map<Waypoint, Double> connections(Waypoint point, Instant now, Duration maxAge) {
        Node node = nodes.get(Objects.requireNonNull(point));
        if (!valid(node, now, maxAge)) {
            return Map.of();
        }
        Map<Waypoint, Double> validConnections = new HashMap<>();
        node.connections.forEach((neighbor, connection) -> {
            if (valid(nodes.get(neighbor), now, maxAge)
                    && valid(connection.observedAt(), now, maxAge)) {
                validConnections.put(neighbor, connection.meters());
            }
        });
        return Map.copyOf(validConnections);
    }

    private static boolean valid(Node node, Instant now, Duration maxAge) {
        return node != null && valid(node.observedAt, now, maxAge);
    }

    private static boolean valid(Instant observedAt, Instant now, Duration maxAge) {
        if (maxAge.isNegative()) {
            return false;
        }
        return !observedAt.isAfter(now) && !observedAt.plus(maxAge).isBefore(now);
    }

    private void trim() {
        while (nodes.size() > capacity) {
            Waypoint oldest = nodes.keySet().iterator().next();
            invalidate(oldest);
        }
    }

    private static final class Node {

        private Instant observedAt;
        private final Map<Waypoint, Connection> connections = new HashMap<>();

        private Node(Instant observedAt) {
            this.observedAt = observedAt;
        }

        private Node refresh(Instant newer) {
            if (newer.isAfter(observedAt)) {
                observedAt = newer;
            }
            return this;
        }
    }

    private record Connection(double meters, Instant observedAt) {
    }
}
