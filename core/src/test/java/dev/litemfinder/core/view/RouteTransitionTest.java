package dev.litemfinder.core.view;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteTransitionTest {
    @Test void interpolatesMovingOriginButKeepsAnUnchangedAnchorAndRestOfTheRoute() {
        var transition = new RouteTransition();
        var old = path(point(0, 0), point(30, 0), point(60, 0));
        var next = path(point(4, 0), point(30, 0), point(60, 0));
        transition.update(old, 0);
        transition.update(next, 100);
        var frame = transition.sample(220);
        assertEquals(point(2, 0), frame.point(0, 0));
        assertEquals(point(30, 0), frame.point(0, 1));
        assertEquals(1, frame.opacity());
        assertSame(next, frame.route());
        assertSame(next, transition.sample(340).route());
    }

    @Test void interpolatesOnlyNearbyChangedPrefixBeforeACommonAnchor() {
        var transition = new RouteTransition();
        transition.update(path(point(0, 0), point(2, 0), point(30, 0)), 0);
        transition.update(path(point(2, 0), point(4, 0), point(30, 0)), 100);
        var frame = transition.sample(220);
        assertEquals(point(1, 0), frame.point(0, 0));
        assertEquals(point(3, 0), frame.point(0, 1));
        assertEquals(point(30, 0), frame.point(0, 2));
    }

    @Test void fadesUnrelatedShapesWithoutEverStretchingTheirIntermediateVertices() {
        var transition = new RouteTransition();
        var before = path(point(0, 0), point(20, 0), point(40, 0));
        var after = path(point(0, 0), point(20, 30), point(40, 0));
        transition.update(before, 0);
        transition.update(after, 100);
        var outgoing = transition.sample(160);
        assertSame(before, outgoing.route());
        assertEquals(0.5, outgoing.opacity(), 1E-9);
        assertEquals(point(20, 0), outgoing.point(0, 1));
        assertEquals(0, transition.sample(220).opacity());
        var incoming = transition.sample(280);
        assertSame(after, incoming.route());
        assertEquals(point(20, 30), incoming.point(0, 1));
        assertEquals(0.5, incoming.opacity(), 1E-9);
    }

    @Test void reorderedStopsAndChangedPointCountsUseFade() {
        var transition = new RouteTransition();
        var first = path(point(0, 0), point(10, 0), point(20, 0));
        var reordered = path(point(0, 0), point(20, 0), point(10, 0));
        transition.update(first, 0);
        transition.update(reordered, 100);
        assertSame(first, transition.sample(160).route());
        transition.update(path(point(0, 0), point(10, 0)), 160);
        assertEquals(0.5, transition.sample(160).opacity(), 1E-9);
        assertSame(first, transition.sample(160).route());
    }

    @Test void frequentRetargetingContinuesFromTheVisibleInterpolatedPosition() {
        var transition = new RouteTransition();
        transition.update(path(point(0, 0), point(30, 0)), 0);
        transition.update(path(point(4, 0), point(30, 0)), 100);
        assertEquals(point(2, 0), transition.sample(220).point(0, 0));
        transition.update(path(point(6, 0), point(30, 0)), 220);
        assertEquals(point(2, 0), transition.sample(220).point(0, 0));
        assertEquals(point(4, 0), transition.sample(340).point(0, 0));
    }

    @Test void incomingOriginDriftDoesNotRestartAnOngoingFadeOrAccumulateLayers() {
        var transition = new RouteTransition();
        var first = path(point(0, 0), point(20, 0), point(40, 0));
        transition.update(first, 0);
        transition.update(path(point(0, 0), point(20, 30), point(40, 0)), 100);
        transition.update(path(point(1, 0), point(20, 30), point(40, 0)), 160);
        assertEquals(0, transition.sample(220).opacity());
        assertEquals(point(1, 0), transition.sample(340).point(0, 0));
        assertEquals(1, transition.sample(340).opacity());
    }

    @Test void interruptedFadeKeepsItsVisibleOpacityInsteadOfFlashingToFullOpacity() {
        var transition = new RouteTransition();
        transition.update(path(point(0, 0), point(20, 0)), 0);
        transition.update(path(point(0, 0), point(0, 20)), 100);
        assertEquals(0.5, transition.sample(280).opacity(), 1E-9);
        transition.update(path(point(0, 0), point(-20, 0)), 280);
        assertEquals(0.5, transition.sample(280).opacity(), 1E-9);
        assertEquals(point(0, 20), transition.sample(280).point(0, 1));
    }

    @Test void worldAndStartingDimensionChangesResetWithoutShowingOldWorldGeometry() {
        var transition = new RouteTransition();
        transition.update(path(point(0, 0), point(20, 0)), 0);
        var otherWorld = path(new WorldPosition("other", "minecraft:overworld", 0, 64, 0),
                new WorldPosition("other", "minecraft:overworld", 20, 64, 0));
        transition.update(otherWorld, 100);
        assertSame(otherWorld, transition.sample(100).route());
        assertEquals(1, transition.sample(100).opacity());
        var nether = path(new WorldPosition("other", "minecraft:the_nether", 0, 64, 0),
                new WorldPosition("other", "minecraft:the_nether", 20, 64, 0));
        transition.update(nether, 101);
        assertSame(nether, transition.sample(101).route());
    }

    @Test void clearingAPlanImmediatelyRemovesItsOldSources() {
        var transition = new RouteTransition();
        transition.update(path(point(0, 0), point(20, 0)), 0);
        transition.update(MapRoute.EMPTY, 100);
        assertTrue(transition.sample(100).route().isEmpty());
        transition.update(path(point(0, 0), point(20, 0)), 200);
        transition.clear();
        assertEquals(0, transition.sample(201).opacity());
    }

    @Test void changingFromSchematicToProviderFadesRatherThanClaimingInterpolatedNavigation() {
        var transition = new RouteTransition();
        var schematic = path(point(0, 0), point(20, 0));
        var actual = new MapRoute(MapRoute.Kind.NAVIGATION, schematic.segments(), false);
        transition.update(schematic, 0);
        transition.update(actual, 100);
        assertEquals(MapRoute.Kind.SCHEMATIC, transition.sample(160).route().kind());
        assertEquals(MapRoute.Kind.NAVIGATION, transition.sample(280).route().kind());
    }

    @Test void immutableGeometryRejectsCrossWorldPathsAndUnverifiedLiveProviderAnchors() {
        var points = new ArrayList<>(List.of(point(0, 0), point(20, 0)));
        var route = new MapRoute(MapRoute.Kind.NAVIGATION, List.of(new MapRoute.Segment("WALK", points)), false);
        points.clear();
        assertEquals(2, route.segments().getFirst().points().size());
        assertThrows(IllegalArgumentException.class, () -> path(point(0, 0),
                new WorldPosition("other", "minecraft:overworld", 1, 64, 1)));
        assertThrows(IllegalArgumentException.class, () -> new MapRoute(MapRoute.Kind.NAVIGATION, route.segments(), true));
    }

    @Test void schematicGeometryNeverLinksSourcesFromDifferentWorldScopes() {
        var route = MapRoute.schematic(point(0, 0), List.of(
                new WorldLocation("world", "minecraft:overworld", 10, 64, 0),
                new WorldLocation("other", "minecraft:overworld", 20, 64, 0)));
        assertEquals(2, route.segments().getFirst().points().size());
        assertTrue(route.followsPlayer());
    }

    @Test void unchangedFramesReuseGeometryAndBackwardClockDoesNotProduceInvalidCoordinates() {
        var transition = new RouteTransition();
        var before = path(point(0, 0), point(20, 0));
        transition.update(before, 0);
        assertSame(transition.sample(1), transition.sample(2));
        transition.update(path(point(4, 0), point(20, 0)), 100);
        assertEquals(point(0, 0), transition.sample(90).point(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RouteTransition(0, 8));
    }

    private static MapRoute path(WorldPosition... points) {
        return new MapRoute(MapRoute.Kind.SCHEMATIC, List.of(new MapRoute.Segment("SCHEMATIC", List.of(points))), false);
    }
    private static WorldPosition point(double x, double z) {
        return new WorldPosition("world", "minecraft:overworld", x, 64, z);
    }
}
