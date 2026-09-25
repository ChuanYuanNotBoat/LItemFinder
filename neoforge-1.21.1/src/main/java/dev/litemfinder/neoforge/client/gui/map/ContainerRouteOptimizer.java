package dev.litemfinder.neoforge.client.gui.map;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Orders recorded container stops into the shortest open route from the player. */
public final class ContainerRouteOptimizer {

    static final int EXACT_STOP_LIMIT = 15;
    private static final double EPSILON = 1.0E-9;
    private static final Comparator<WorldLocation> LOCATION_ORDER = Comparator
            .comparing(WorldLocation::scope)
            .thenComparing(location -> location.dimension().toString())
            .thenComparingInt(WorldLocation::x)
            .thenComparingInt(WorldLocation::y)
            .thenComparingInt(WorldLocation::z);

    private ContainerRouteOptimizer() {
    }

    /**
     * Finds an exact shortest order for normal sized same-dimension plans. Very large plans use
     * deterministic nearest-neighbour plus 2-opt so the render thread cannot allocate exponential memory.
     * Stops outside the origin's scope or dimension remain after the comparable group because their travel
     * cost depends on a navigation provider's portal topology.
     */
    public static List<WorldLocation> order(WorldPosition origin, List<WorldLocation> stops) {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(stops, "stops must not be null");
        if (stops.isEmpty()) {
            return List.of();
        }

        List<WorldLocation> comparable = new ArrayList<>();
        List<WorldLocation> deferred = new ArrayList<>();
        for (WorldLocation stop : stops) {
            Objects.requireNonNull(stop, "stops must not contain null");
            if (origin.scope().equals(stop.scope()) && origin.dimension().equals(stop.dimension())) {
                comparable.add(stop);
            } else {
                deferred.add(stop);
            }
        }
        comparable.sort(LOCATION_ORDER);

        List<WorldLocation> result = new ArrayList<>(stops.size());
        result.addAll(comparable.size() <= EXACT_STOP_LIMIT
                ? exactOrder(origin, comparable) : improvedNearestOrder(origin, comparable));
        result.addAll(deferred);
        return List.copyOf(result);
    }

    private static List<WorldLocation> exactOrder(WorldPosition origin, List<WorldLocation> stops) {
        int size = stops.size();
        if (size < 2) {
            return List.copyOf(stops);
        }
        int stateCount = 1 << size;
        double[][] costs = new double[stateCount][size];
        int[][] previous = new int[stateCount][size];
        for (int state = 0; state < stateCount; state++) {
            Arrays.fill(costs[state], Double.POSITIVE_INFINITY);
            Arrays.fill(previous[state], -1);
        }
        for (int stop = 0; stop < size; stop++) {
            costs[1 << stop][stop] = distance(origin, stops.get(stop));
        }

        for (int state = 1; state < stateCount; state++) {
            for (int last = 0; last < size; last++) {
                double cost = costs[state][last];
                if ((state & (1 << last)) == 0 || !Double.isFinite(cost)) {
                    continue;
                }
                for (int next = 0; next < size; next++) {
                    if ((state & (1 << next)) != 0) {
                        continue;
                    }
                    int nextState = state | (1 << next);
                    double candidate = cost + distance(stops.get(last), stops.get(next));
                    if (candidate + EPSILON < costs[nextState][next]) {
                        costs[nextState][next] = candidate;
                        previous[nextState][next] = last;
                    }
                }
            }
        }

        int fullState = stateCount - 1;
        int last = 0;
        for (int candidate = 1; candidate < size; candidate++) {
            if (costs[fullState][candidate] + EPSILON < costs[fullState][last]) {
                last = candidate;
            }
        }
        List<WorldLocation> reversed = new ArrayList<>(size);
        int state = fullState;
        while (last >= 0) {
            reversed.add(stops.get(last));
            int parent = previous[state][last];
            state ^= 1 << last;
            last = parent;
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static List<WorldLocation> improvedNearestOrder(WorldPosition origin,
                                                             List<WorldLocation> stops) {
        List<WorldLocation> remaining = new ArrayList<>(stops);
        List<WorldLocation> route = new ArrayList<>(stops.size());
        double currentX = origin.x();
        double currentY = origin.y();
        double currentZ = origin.z();
        while (!remaining.isEmpty()) {
            int bestIndex = 0;
            double bestDistance = distance(currentX, currentY, currentZ, remaining.getFirst());
            for (int index = 1; index < remaining.size(); index++) {
                double candidate = distance(currentX, currentY, currentZ, remaining.get(index));
                if (candidate + EPSILON < bestDistance) {
                    bestDistance = candidate;
                    bestIndex = index;
                }
            }
            WorldLocation next = remaining.remove(bestIndex);
            route.add(next);
            currentX = next.x();
            currentY = next.y();
            currentZ = next.z();
        }

        boolean changed;
        int remainingPasses = route.size();
        do {
            changed = false;
            for (int first = 0; first < route.size() - 1; first++) {
                for (int last = first + 1; last < route.size(); last++) {
                    double before = boundaryCost(origin, route, first, last, false);
                    double after = boundaryCost(origin, route, first, last, true);
                    if (after + EPSILON < before) {
                        Collections.reverse(route.subList(first, last + 1));
                        changed = true;
                    }
                }
            }
        } while (changed && --remainingPasses > 0);
        return route;
    }

    private static double boundaryCost(WorldPosition origin, List<WorldLocation> route,
                                       int first, int last, boolean reversed) {
        double cost;
        if (first == 0) {
            cost = distance(origin, route.get(reversed ? last : first));
        } else {
            cost = distance(route.get(first - 1), route.get(reversed ? last : first));
        }
        if (last + 1 < route.size()) {
            cost += distance(route.get(reversed ? first : last), route.get(last + 1));
        }
        return cost;
    }

    private static double distance(WorldPosition from, WorldLocation to) {
        return distance(from.x(), from.y(), from.z(), to);
    }

    private static double distance(WorldLocation from, WorldLocation to) {
        return distance(from.x(), from.y(), from.z(), to);
    }

    private static double distance(double x, double y, double z, WorldLocation to) {
        double deltaX = x - to.x();
        double deltaY = y - to.y();
        double deltaZ = z - to.z();
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }
}
