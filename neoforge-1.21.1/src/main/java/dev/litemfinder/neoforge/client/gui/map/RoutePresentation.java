package dev.litemfinder.neoforge.client.gui.map;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.view.MapRoute;
import dev.litemfinder.core.view.RouteTransition;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;

import java.util.List;
import java.util.Objects;

/** Converts the optional provider contract to shared geometry without copying unchanged paths each frame. */
public final class RoutePresentation {
    private final RouteTransition transition = new RouteTransition();
    private NavigationGateway.Overlay previousOverlay;
    private List<WorldLocation> previousStops;
    private WorldPosition previousOrigin;

    public void update(WorldPosition origin, List<WorldLocation> stops, NavigationGateway.Overlay overlay, long nowMillis) {
        boolean actual = overlay != null && !overlay.segments().isEmpty()
                && (overlay.status() == NavigationGateway.Status.FOUND || overlay.status() == NavigationGateway.Status.PARTIAL);
        boolean alreadyNear = overlay != null && overlay.status() == NavigationGateway.Status.FOUND
                && overlay.segments().isEmpty() && overlay.report().verifiedStops() == stops.size();
        boolean providerRoute = actual || alreadyNear;
        if (providerRoute && previousOverlay == overlay && origin != null && previousOrigin != null
                && MapRoute.sameSpace(origin, previousOrigin)) return;
        if (!providerRoute && previousOverlay == null && Objects.equals(origin, previousOrigin)
                && Objects.equals(stops, previousStops)) return;
        MapRoute route = actual ? navigation(origin, overlay) : alreadyNear ? MapRoute.EMPTY : MapRoute.schematic(origin, stops);
        transition.update(route, nowMillis);
        previousOverlay = providerRoute ? overlay : null;
        previousOrigin = origin;
        previousStops = stops;
    }

    /** Reject invalid cross-world or wrong-starting-dimension results before publishing them to the screen. */
    public static MapRoute navigation(WorldPosition origin, NavigationGateway.Overlay overlay) {
        Objects.requireNonNull(origin, "navigation needs a confirmed origin");
        if (overlay.status() != NavigationGateway.Status.FOUND && overlay.status() != NavigationGateway.Status.PARTIAL) {
            throw new IllegalArgumentException("an unknown or unreachable result is not a navigation path");
        }
        MapRoute route = new MapRoute(MapRoute.Kind.NAVIGATION, overlay.segments().stream()
                .map(segment -> new MapRoute.Segment(segment.mode(), segment.points())).toList(), false);
        if (route.isEmpty() || !MapRoute.sameSpace(origin, route.start())) {
            throw new IllegalArgumentException("provider route must start in the requested world and dimension");
        }
        return route;
    }

    public RouteTransition.Frame frame(long nowMillis) { return transition.sample(nowMillis); }

    public void clear() {
        transition.clear();
        previousOverlay = null;
        previousStops = null;
        previousOrigin = null;
    }
}
