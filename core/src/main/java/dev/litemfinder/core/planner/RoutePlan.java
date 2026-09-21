package dev.litemfinder.core.planner;

import dev.litemfinder.core.model.ItemKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ordered retrieval route plus any demand that reachable indexed storage cannot satisfy. */
public record RoutePlan(List<RouteStop> stops, Map<ItemKey, Long> missing) {

    public RoutePlan {
        stops = List.copyOf(Objects.requireNonNull(stops, "stops must not be null"));
        missing = Map.copyOf(Objects.requireNonNull(missing, "missing must not be null"));
        if (missing.values().stream().anyMatch(amount -> amount == null || amount <= 0)) {
            throw new IllegalArgumentException("missing must contain only positive quantities");
        }
    }

    public double totalDistance() {
        return stops.stream().mapToDouble(RouteStop::distanceFromPrevious).sum();
    }

    public boolean fulfilled() {
        return missing.isEmpty();
    }
}
