package dev.litemfinder.navigation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Dijkstra over active non-negative travel-time edges, including confirmed dimension changes. */
public final class TopologyRouteFinder {

    private final TopologyGraph graph;
    private final TrajectoryEvidence evidence;

    public TopologyRouteFinder(TopologyGraph graph, TrajectoryEvidence evidence) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.evidence = Objects.requireNonNull(evidence, "evidence must not be null");
    }

    public NavigationResult find(Waypoint start, Waypoint goal, TravelerProfile profile,
                                 Instant now, Duration maxDataAge) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(maxDataAge, "maxDataAge must not be null");
        if (maxDataAge.isNegative() || !start.scope().equals(goal.scope())) {
            return NavigationResult.unknown();
        }
        if (start.equals(goal)) {
            return new NavigationResult(NavigationResult.Status.FOUND, List.of(), 0);
        }

        Map<Waypoint, Double> best = new HashMap<>();
        Map<Waypoint, TravelEdge> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::seconds));
        best.put(start, 0.0);
        queue.add(new QueueEntry(start, 0));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.remove();
            if (current.seconds() > best.getOrDefault(current.point(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            if (current.point().equals(goal)) {
                List<TravelEdge> path = new ArrayList<>();
                Waypoint point = goal;
                while (!point.equals(start)) {
                    TravelEdge edge = previous.get(point);
                    path.add(edge);
                    point = edge.from();
                }
                java.util.Collections.reverse(path);
                return new NavigationResult(NavigationResult.Status.FOUND, path, current.seconds());
            }
            for (TravelEdge edge : graph.outgoing(current.point())) {
                if (!edge.available(profile, now, maxDataAge)) {
                    continue;
                }
                double nominal = edge.seconds(profile);
                double time = evidence.estimate(edge).orElse(nominal);
                if (!Double.isFinite(time) || time < 0) {
                    continue;
                }
                double candidate = current.seconds() + time;
                if (candidate < best.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    best.put(edge.to(), candidate);
                    previous.put(edge.to(), edge);
                    queue.add(new QueueEntry(edge.to(), candidate));
                }
            }
        }
        return NavigationResult.unknown();
    }

    private record QueueEntry(Waypoint point, double seconds) {
    }
}
