package dev.litemfinder.navigation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Combines active local A* with topology Dijkstra; neither search depends on saved trajectories. */
public final class StandardNavigationEngine implements NavigationProvider {

    private final WalkAStar walking;
    private final TopologyRouteFinder topology;

    public StandardNavigationEngine(PassabilityCache passability, TopologyGraph graph,
                                    TrajectoryEvidence evidence) {
        walking = new WalkAStar(Objects.requireNonNull(passability, "passability must not be null"));
        topology = new TopologyRouteFinder(Objects.requireNonNull(graph, "graph must not be null"),
                Objects.requireNonNull(evidence, "evidence must not be null"));
    }

    @Override
    public NavigationResult route(Waypoint start, Waypoint goal, TravelerProfile profile,
                                  Instant now, Duration maxDataAge, int maxExpanded) {
        WalkPath local = walking.find(start, goal, profile, now, maxDataAge, maxExpanded);
        NavigationResult network = topology.find(start, goal, profile, now, maxDataAge);
        if (local.status() == NavigationResult.Status.FOUND
                && (network.status() != NavigationResult.Status.FOUND
                || local.estimatedSeconds() <= network.estimatedSeconds())) {
            return new NavigationResult(NavigationResult.Status.FOUND,
                    asWalkingEdges(local.points(), now), local.estimatedSeconds());
        }
        if (network.status() == NavigationResult.Status.FOUND) {
            return network;
        }
        if (local.status() == NavigationResult.Status.PARTIAL) {
            return new NavigationResult(NavigationResult.Status.PARTIAL,
                    asWalkingEdges(local.points(), now), local.estimatedSeconds());
        }
        return NavigationResult.unknown();
    }

    private static List<TravelEdge> asWalkingEdges(List<Waypoint> points, Instant observedAt) {
        List<TravelEdge> edges = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            Waypoint from = points.get(index - 1);
            Waypoint to = points.get(index);
            edges.add(new TravelEdge(from, to, TravelMode.WALK, from.distanceTo(to), 0,
                    FacilityState.OPEN, "", observedAt));
        }
        return List.copyOf(edges);
    }
}
