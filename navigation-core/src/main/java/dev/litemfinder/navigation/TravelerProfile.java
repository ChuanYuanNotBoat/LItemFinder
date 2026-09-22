package dev.litemfinder.navigation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Current player abilities, available vehicles/items and measured or configured speeds. */
public record TravelerProfile(Set<TravelMode> enabledModes, Map<TravelMode, Double> metersPerSecond,
                              Set<String> availableItems) {

    public TravelerProfile {
        enabledModes = Set.copyOf(Objects.requireNonNull(enabledModes, "enabledModes must not be null"));
        metersPerSecond = Map.copyOf(Objects.requireNonNull(metersPerSecond,
                "metersPerSecond must not be null"));
        availableItems = Set.copyOf(Objects.requireNonNull(availableItems,
                "availableItems must not be null"));
        for (Map.Entry<TravelMode, Double> speed : metersPerSecond.entrySet()) {
            if (speed.getKey() == null || speed.getValue() == null
                    || !Double.isFinite(speed.getValue()) || speed.getValue() <= 0) {
                throw new IllegalArgumentException("speeds must be finite and positive");
            }
        }
    }

    public boolean canUse(TravelMode mode, String requiredItem) {
        return enabledModes.contains(mode)
                && (requiredItem == null || requiredItem.isBlank() || availableItems.contains(requiredItem));
    }

    public double speed(TravelMode mode) {
        return metersPerSecond.getOrDefault(mode, 0.0);
    }
}
