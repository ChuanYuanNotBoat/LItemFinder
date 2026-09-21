package dev.litemfinder.core.planner;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.WorldLocation;

import java.util.Map;
import java.util.Objects;

/** One world container visit and the quantities to retrieve there. */
public record RouteStop(
        ContainerRecord container,
        Map<ItemKey, Long> pickup,
        double distanceFromPrevious
) {

    public RouteStop {
        Objects.requireNonNull(container, "container must not be null");
        if (!(container.location() instanceof WorldLocation)) {
            throw new IllegalArgumentException("route stop must have a world location");
        }
        pickup = Map.copyOf(Objects.requireNonNull(pickup, "pickup must not be null"));
        if (pickup.isEmpty() || pickup.values().stream().anyMatch(amount -> amount == null || amount <= 0)) {
            throw new IllegalArgumentException("pickup must contain positive quantities");
        }
        if (!Double.isFinite(distanceFromPrevious) || distanceFromPrevious < 0) {
            throw new IllegalArgumentException("distanceFromPrevious must be finite and non-negative");
        }
    }
}
