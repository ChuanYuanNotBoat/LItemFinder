package dev.litemfinder.navigation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** A confirmed, directed transport connection, including observed cross-dimension transitions. */
public record TravelEdge(Waypoint from, Waypoint to, TravelMode mode, double distanceMeters,
                         double fixedSeconds, FacilityState facility, String requiredItem,
                         Instant observedAt) {

    public TravelEdge {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(facility, "facility must not be null");
        Objects.requireNonNull(requiredItem, "requiredItem must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (!from.scope().equals(to.scope())) {
            throw new IllegalArgumentException("transport edges must stay in one scope");
        }
        if (!from.dimension().equals(to.dimension())
                && mode != TravelMode.PORTAL && mode != TravelMode.MODDED) {
            throw new IllegalArgumentException("cross-dimension edges require a portal or modded provider");
        }
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0
                || !Double.isFinite(fixedSeconds) || fixedSeconds < 0) {
            throw new IllegalArgumentException("edge costs must be finite and non-negative");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("edge endpoints must differ");
        }
    }

    public boolean available(TravelerProfile profile, Instant now, Duration maxAge) {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(maxAge, "maxAge must not be null");
        return facility == FacilityState.OPEN
                && !observedAt.isAfter(now)
                && !observedAt.plus(maxAge).isBefore(now)
                && profile.canUse(mode, requiredItem)
                && (distanceMeters == 0 || profile.speed(mode) > 0);
    }

    public double seconds(TravelerProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        if (distanceMeters == 0) {
            return fixedSeconds;
        }
        double speed = profile.speed(mode);
        if (speed <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return fixedSeconds + distanceMeters / speed;
    }
}
