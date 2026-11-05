package dev.litemfinder.neoforge.client.gui.map;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.view.MapRoute;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutePresentationTest {
    private static final WorldPosition ORIGIN = point(0);
    private static final List<WorldLocation> STOPS = List.of(new WorldLocation("world", "minecraft:overworld", 20, 64, 0));

    @Test void aVerifiedAlreadyNearbyResultRemovesSchematicGeometryRatherThanImplyingMoreWalking() {
        var presentation = new RoutePresentation();
        presentation.update(ORIGIN, STOPS, null, 0);
        var report = new dev.litemfinder.core.view.NavigationReport(dev.litemfinder.core.view.NavigationReport.Reason.NONE,
                dev.litemfinder.core.view.NavigationReport.Estimate.BASE_SPEED, 1, java.util.Optional.empty(),
                dev.litemfinder.core.view.NavigationReport.Confidence.UNSPECIFIED, dev.litemfinder.core.view.NavigationReport.Source.UNSPECIFIED);
        var result = new NavigationGateway.Overlay(NavigationGateway.Status.FOUND, List.of(), 0, report);
        presentation.update(ORIGIN, STOPS, result, 10);
        assertTrue(presentation.frame(300).route().isEmpty());
        presentation.update(ORIGIN, STOPS, result, 400);
        assertTrue(presentation.frame(400).route().isEmpty());
    }

    @Test void unchangedProviderPathIsReusedWhileThePlayerMovesInsteadOfBeingAttachedToUnverifiedCoordinates() {
        var presentation = new RoutePresentation();
        var overlay = overlay(NavigationGateway.Status.FOUND, ORIGIN, point(20));
        presentation.update(ORIGIN, STOPS, overlay, 0);
        var first = presentation.frame(0);
        presentation.update(point(4), STOPS, overlay, 100);
        assertSame(first, presentation.frame(100));
        assertEquals(ORIGIN, first.point(0, 0));
        assertFalse(first.route().followsPlayer());
    }

    @Test void absentOrUnknownProviderKeepsSchematicStopsAndNeverClaimsAnActualPath() {
        var presentation = new RoutePresentation();
        presentation.update(ORIGIN, STOPS, null, 0);
        assertEquals(MapRoute.Kind.SCHEMATIC, presentation.frame(0).route().kind());
        assertTrue(presentation.frame(0).route().followsPlayer());
        presentation.update(ORIGIN, STOPS, overlay(NavigationGateway.Status.UNKNOWN, ORIGIN, point(20)), 10);
        assertEquals(MapRoute.Kind.SCHEMATIC, presentation.frame(10).route().kind());
        assertThrows(IllegalArgumentException.class, () -> RoutePresentation.navigation(ORIGIN,
                overlay(NavigationGateway.Status.UNKNOWN, ORIGIN, point(20))));
    }

    @Test void providerArrivalAndLossFadeBetweenTheirOwnStylesAndClearDropsAllGeometry() {
        var presentation = new RoutePresentation();
        presentation.update(ORIGIN, STOPS, null, 0);
        presentation.update(ORIGIN, STOPS, overlay(NavigationGateway.Status.PARTIAL, ORIGIN, point(20)), 100);
        assertEquals(MapRoute.Kind.SCHEMATIC, presentation.frame(160).route().kind());
        assertEquals(MapRoute.Kind.NAVIGATION, presentation.frame(280).route().kind());
        presentation.update(ORIGIN, STOPS, null, 400);
        assertEquals(MapRoute.Kind.NAVIGATION, presentation.frame(460).route().kind());
        assertEquals(MapRoute.Kind.SCHEMATIC, presentation.frame(580).route().kind());
        presentation.clear();
        assertTrue(presentation.frame(581).route().isEmpty());
    }

    @Test void rejectsOtherWorldsAndIncorrectStartingDimensionsButKeepsPortalSegments() {
        var other = new WorldPosition("other", "minecraft:overworld", 20, 64, 0);
        assertThrows(IllegalArgumentException.class, () ->
                RoutePresentation.navigation(ORIGIN, overlay(NavigationGateway.Status.FOUND, ORIGIN, other)));
        var nether = new WorldPosition("world", "minecraft:the_nether", 2, 64, 0);
        assertThrows(IllegalArgumentException.class, () ->
                RoutePresentation.navigation(ORIGIN, overlay(NavigationGateway.Status.FOUND, nether, nether)));
        var portal = new NavigationGateway.Overlay(NavigationGateway.Status.FOUND,
                List.of(new NavigationGateway.Segment("PORTAL", List.of(ORIGIN, nether))), 4);
        assertEquals(nether, RoutePresentation.navigation(ORIGIN, portal).segments().getFirst().points().getLast());
    }

    static NavigationGateway.Overlay overlay(NavigationGateway.Status status, WorldPosition from, WorldPosition to) {
        return new NavigationGateway.Overlay(status, List.of(new NavigationGateway.Segment("WALK", List.of(from, to))), 4);
    }
    private static WorldPosition point(double x) { return new WorldPosition("world", "minecraft:overworld", x, 64, 0); }
}
