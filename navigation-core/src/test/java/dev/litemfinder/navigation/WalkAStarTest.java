package dev.litemfinder.navigation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalkAStarTest {

    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");
    private static final Duration AGE = Duration.ofHours(1);
    private static final TravelerProfile WALKER = new TravelerProfile(
            Set.of(TravelMode.WALK), Map.of(TravelMode.WALK, 1.0), Set.of());

    @Test
    void discoversNewShortcutInsteadOfFollowingTheOldRoute() {
        PassabilityCache cache = new PassabilityCache(100);
        Waypoint start = point(0, 0);
        Waypoint detourA = point(0, 1);
        Waypoint detourB = point(1, 1);
        Waypoint end = point(2, 0);
        Waypoint shortcut = point(1, 0);
        cache.observeConnection(start, detourA, NOW);
        cache.observeConnection(detourA, detourB, NOW);
        cache.observeConnection(detourB, end, NOW);
        cache.observeConnection(start, shortcut, NOW);
        cache.observeConnection(shortcut, end, NOW);

        WalkPath path = new WalkAStar(cache).find(start, end, WALKER, NOW, AGE, 100);

        assertEquals(NavigationResult.Status.FOUND, path.status());
        assertEquals(java.util.List.of(start, shortcut, end), path.points());
        assertEquals(2, path.estimatedSeconds());
    }

    @Test
    void neverCrossesUnknownOrInvalidatedWorldData() {
        PassabilityCache cache = new PassabilityCache(100);
        Waypoint start = point(0, 0);
        Waypoint middle = point(1, 0);
        Waypoint end = point(2, 0);
        cache.observeConnection(start, middle, NOW);
        cache.observeConnection(middle, end, NOW);
        cache.invalidate(middle);

        assertEquals(NavigationResult.Status.UNKNOWN,
                new WalkAStar(cache).find(start, end, WALKER, NOW, AGE, 100).status());
        cache.observeConnection(start, middle, NOW.minus(Duration.ofHours(2)));
        assertEquals(NavigationResult.Status.UNKNOWN,
                new WalkAStar(cache).find(start, end, WALKER, NOW, AGE, 100).status());
    }

    @Test
    void returnsOnlyKnownPrefixWhenSearchBudgetEnds() {
        PassabilityCache cache = new PassabilityCache(100);
        Waypoint start = point(0, 0);
        Waypoint one = point(1, 0);
        Waypoint two = point(2, 0);
        Waypoint end = point(3, 0);
        cache.observeConnection(start, one, NOW);
        cache.observeConnection(one, two, NOW);
        cache.observeConnection(two, end, NOW);

        WalkPath path = new WalkAStar(cache).find(start, end, WALKER, NOW, AGE, 1);

        assertEquals(NavigationResult.Status.PARTIAL, path.status());
        assertEquals(java.util.List.of(start, one), path.points());
    }

    @Test
    void refreshingPointsDoesNotReviveAnExpiredConnection() {
        PassabilityCache cache = new PassabilityCache(100);
        Waypoint start = point(0, 0);
        Waypoint end = point(1, 0);
        cache.observeConnection(start, end, NOW.minus(Duration.ofHours(2)));
        cache.observe(start, NOW);
        cache.observe(end, NOW);

        assertEquals(NavigationResult.Status.UNKNOWN,
                new WalkAStar(cache).find(start, end, WALKER, NOW, AGE, 100).status());
    }

    private static Waypoint point(int x, int z) {
        return new Waypoint("scope", "minecraft:overworld", x, 64, z);
    }
}
