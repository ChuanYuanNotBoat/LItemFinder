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

/** Actively discovers the shortest known walking path; trajectory history is not an input. */
public final class WalkAStar {

    private final PassabilityCache cache;

    public WalkAStar(PassabilityCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    public WalkPath find(Waypoint start, Waypoint goal, TravelerProfile profile,
                         Instant now, Duration maxDataAge, int maxExpanded) {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(maxDataAge, "maxDataAge must not be null");
        if (maxExpanded <= 0 || maxDataAge.isNegative()) {
            throw new IllegalArgumentException("search budget and max age must be positive/non-negative");
        }
        double speed = profile.speed(TravelMode.WALK);
        if (!profile.enabledModes().contains(TravelMode.WALK) || speed <= 0
                || !start.scope().equals(goal.scope()) || !start.dimension().equals(goal.dimension())
                || !cache.known(start, now, maxDataAge) || !cache.known(goal, now, maxDataAge)) {
            return new WalkPath(NavigationResult.Status.UNKNOWN, List.of(), 0);
        }
        if (start.equals(goal)) {
            return new WalkPath(NavigationResult.Status.FOUND, List.of(start), 0);
        }

        Map<Waypoint, Double> cost = new HashMap<>();
        Map<Waypoint, Waypoint> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::score));
        cost.put(start, 0.0);
        queue.add(new QueueEntry(start, heuristic(start, goal, speed)));
        Waypoint closest = start;
        double closestHeuristic = heuristic(start, goal, speed);
        int expanded = 0;

        while (!queue.isEmpty()) {
            QueueEntry current = queue.remove();
            double currentCost = cost.getOrDefault(current.point(), Double.POSITIVE_INFINITY);
            if (current.score() > currentCost + heuristic(current.point(), goal, speed) + 1e-9) {
                continue;
            }
            if (current.point().equals(goal)) {
                return new WalkPath(NavigationResult.Status.FOUND,
                        reconstruct(start, goal, previous), currentCost);
            }
            double remaining = heuristic(current.point(), goal, speed);
            if (remaining < closestHeuristic) {
                closest = current.point();
                closestHeuristic = remaining;
            }
            if (++expanded > maxExpanded) {
                return new WalkPath(NavigationResult.Status.PARTIAL,
                        reconstruct(start, closest, previous), cost.get(closest));
            }
            cache.connections(current.point(), now, maxDataAge).forEach((neighbor, meters) -> {
                double candidate = currentCost + meters / speed;
                if (candidate < cost.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    cost.put(neighbor, candidate);
                    previous.put(neighbor, current.point());
                    queue.add(new QueueEntry(neighbor, candidate + heuristic(neighbor, goal, speed)));
                }
            });
        }
        return new WalkPath(NavigationResult.Status.UNKNOWN, List.of(), 0);
    }

    private static double heuristic(Waypoint from, Waypoint to, double speed) {
        return from.distanceTo(to) / speed;
    }

    private static List<Waypoint> reconstruct(Waypoint start, Waypoint end,
                                              Map<Waypoint, Waypoint> previous) {
        List<Waypoint> reversed = new ArrayList<>();
        Waypoint point = end;
        reversed.add(point);
        while (!point.equals(start)) {
            point = previous.get(point);
            if (point == null) {
                throw new IllegalStateException("search predecessor chain is incomplete");
            }
            reversed.add(point);
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private record QueueEntry(Waypoint point, double score) {
    }
}
