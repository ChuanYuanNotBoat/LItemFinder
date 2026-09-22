package dev.litemfinder.navigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Directed, explicitly observed transport topology. Never manufactures portal exits. */
public final class TopologyGraph {

    private final Map<Waypoint, List<TravelEdge>> outgoing = new HashMap<>();

    public synchronized void add(TravelEdge edge) {
        Objects.requireNonNull(edge, "edge must not be null");
        List<TravelEdge> edges = outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>());
        boolean newerExists = edges.stream().anyMatch(existing -> existing.to().equals(edge.to())
                && existing.mode() == edge.mode() && existing.observedAt().isAfter(edge.observedAt()));
        if (newerExists) {
            return;
        }
        edges.removeIf(existing -> existing.to().equals(edge.to()) && existing.mode() == edge.mode());
        edges.add(edge);
        outgoing.computeIfAbsent(edge.to(), ignored -> new ArrayList<>());
    }

    public synchronized List<TravelEdge> outgoing(Waypoint point) {
        return List.copyOf(outgoing.getOrDefault(Objects.requireNonNull(point), List.of()));
    }

    public synchronized Set<Waypoint> points() {
        return Set.copyOf(outgoing.keySet());
    }

    public synchronized void clear() {
        outgoing.clear();
    }
}
