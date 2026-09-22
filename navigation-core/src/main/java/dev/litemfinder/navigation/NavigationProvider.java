package dev.litemfinder.navigation;

import java.time.Duration;
import java.time.Instant;

/** Standalone waypoint service; callers do not need LItemFinder classes. */
public interface NavigationProvider {

    NavigationResult route(Waypoint start, Waypoint goal, TravelerProfile profile,
                           Instant now, Duration maxDataAge, int maxExpanded);
}
