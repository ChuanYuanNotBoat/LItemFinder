package dev.litemfinder.navigation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopologyRouteFinderTest {

    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");
    private static final Duration AGE = Duration.ofHours(1);

    @Test
    void choosesUsableCrossDimensionRouteByTime() {
        Waypoint start = new Waypoint("scope", "minecraft:overworld", 0, 64, 0);
        Waypoint portalIn = new Waypoint("scope", "minecraft:overworld", 100, 64, 0);
        Waypoint portalOut = new Waypoint("scope", "minecraft:the_nether", 12, 64, 0);
        Waypoint goal = new Waypoint("scope", "minecraft:the_nether", 30, 64, 0);
        TopologyGraph graph = new TopologyGraph();
        graph.add(edge(start, portalIn, TravelMode.RAIL, 100, 2, "minecraft:minecart"));
        graph.add(edge(start, portalIn, TravelMode.WALK, 100, 0, ""));
        graph.add(edge(portalIn, portalOut, TravelMode.PORTAL, 0, 4, ""));
        graph.add(edge(portalOut, goal, TravelMode.WALK, 18, 0, ""));
        TravelerProfile traveler = new TravelerProfile(Set.of(TravelMode.WALK, TravelMode.RAIL,
                TravelMode.PORTAL), Map.of(TravelMode.WALK, 4.0, TravelMode.RAIL, 10.0),
                Set.of("minecraft:minecart"));

        NavigationResult route = new TopologyRouteFinder(graph, new TrajectoryEvidence())
                .find(start, goal, traveler, NOW, AGE);

        assertEquals(NavigationResult.Status.FOUND, route.status());
        assertEquals(TravelMode.RAIL, route.edges().getFirst().mode());
        assertEquals(TravelMode.PORTAL, route.edges().get(1).mode());
        assertEquals(20.5, route.estimatedSeconds());
    }

    @Test
    void skipsClosedFacilitiesAndMissingVehicle() {
        Waypoint start = new Waypoint("scope", "minecraft:overworld", 0, 64, 0);
        Waypoint goal = new Waypoint("scope", "minecraft:overworld", 100, 64, 0);
        TopologyGraph graph = new TopologyGraph();
        graph.add(edge(start, goal, TravelMode.ICE_BOAT, 100, 1, "minecraft:boat"));
        graph.add(new TravelEdge(start, goal, TravelMode.RAIL, 100, 1, FacilityState.CLOSED,
                "minecraft:minecart", NOW));
        graph.add(edge(start, goal, TravelMode.WALK, 100, 0, ""));
        TravelerProfile traveler = new TravelerProfile(Set.of(TravelMode.WALK, TravelMode.ICE_BOAT,
                TravelMode.RAIL), Map.of(TravelMode.WALK, 4.0, TravelMode.ICE_BOAT, 20.0,
                TravelMode.RAIL, 8.0), Set.of());

        NavigationResult route = new TopologyRouteFinder(graph, new TrajectoryEvidence())
                .find(start, goal, traveler, NOW, AGE);

        assertEquals(TravelMode.WALK, route.edges().getFirst().mode());
        assertEquals(25, route.estimatedSeconds());
    }

    @Test
    void newerClosedFacilityReplacesPreviouslyOpenEdge() {
        Waypoint start = new Waypoint("scope", "minecraft:overworld", 0, 64, 0);
        Waypoint goal = new Waypoint("scope", "minecraft:overworld", 100, 64, 0);
        TopologyGraph graph = new TopologyGraph();
        graph.add(edge(start, goal, TravelMode.RAIL, 100, 1, "minecraft:minecart"));
        graph.add(new TravelEdge(start, goal, TravelMode.RAIL, 100, 1, FacilityState.CLOSED,
                "minecraft:minecart", NOW.plusSeconds(1)));
        TravelerProfile traveler = new TravelerProfile(Set.of(TravelMode.RAIL),
                Map.of(TravelMode.RAIL, 10.0), Set.of("minecraft:minecart"));

        NavigationResult route = new TopologyRouteFinder(graph, new TrajectoryEvidence())
                .find(start, goal, traveler, NOW.plusSeconds(1), AGE);

        assertEquals(NavigationResult.Status.UNKNOWN, route.status());
    }

    @Test
    void learnedOldRouteDoesNotHideAnUntraveledShortcut() {
        Waypoint start = new Waypoint("scope", "minecraft:overworld", 0, 64, 0);
        Waypoint old = new Waypoint("scope", "minecraft:overworld", 5, 64, 0);
        Waypoint goal = new Waypoint("scope", "minecraft:overworld", 10, 64, 0);
        TopologyGraph graph = new TopologyGraph();
        graph.add(edge(start, old, TravelMode.WALK, 10, 0, ""));
        graph.add(edge(old, goal, TravelMode.WALK, 10, 0, ""));
        graph.add(edge(start, goal, TravelMode.WALK, 8, 0, ""));
        TrajectoryEvidence history = new TrajectoryEvidence();
        history.record(start, old, TravelMode.WALK, 4);
        history.record(old, goal, TravelMode.WALK, 4);
        TravelerProfile traveler = new TravelerProfile(Set.of(TravelMode.WALK),
                Map.of(TravelMode.WALK, 4.0), Set.of());

        NavigationResult route = new TopologyRouteFinder(graph, history)
                .find(start, goal, traveler, NOW, AGE);

        assertEquals(1, route.edges().size());
        assertEquals(goal, route.edges().getFirst().to());
    }

    private static TravelEdge edge(Waypoint from, Waypoint to, TravelMode mode,
                                   double meters, double fixed, String item) {
        return new TravelEdge(from, to, mode, meters, fixed, FacilityState.OPEN, item, NOW);
    }
}
