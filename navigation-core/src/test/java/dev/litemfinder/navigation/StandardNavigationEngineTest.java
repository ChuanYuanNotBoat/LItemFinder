package dev.litemfinder.navigation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardNavigationEngineTest {

    @Test
    void standaloneWaypointsUseNewKnownShortcutWithoutInventoryMod() {
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        Waypoint start = new Waypoint("scope", "minecraft:overworld", 0, 64, 0);
        Waypoint middle = new Waypoint("scope", "minecraft:overworld", 1, 64, 0);
        Waypoint goal = new Waypoint("scope", "minecraft:overworld", 2, 64, 0);
        PassabilityCache cache = new PassabilityCache(16);
        cache.observeConnection(start, middle, now);
        cache.observeConnection(middle, goal, now);
        TravelerProfile profile = new TravelerProfile(Set.of(TravelMode.WALK),
                Map.of(TravelMode.WALK, 2.0), Set.of());
        NavigationProvider provider = new StandardNavigationEngine(cache,
                new TopologyGraph(), new TrajectoryEvidence());

        NavigationResult result = provider.route(start, goal, profile, now, Duration.ofMinutes(10), 100);

        assertEquals(NavigationResult.Status.FOUND, result.status());
        assertEquals(2, result.edges().size());
        assertEquals(1, result.estimatedSeconds());
    }
}
