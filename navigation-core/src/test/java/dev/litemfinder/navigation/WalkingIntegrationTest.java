package dev.litemfinder.navigation;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WalkingIntegrationTest {
    static final Instant NOW=Instant.parse("2026-10-28T00:00:00Z");
    static final TravelerProfile WALKER=new TravelerProfile(Set.of(TravelMode.WALK),Map.of(TravelMode.WALK,1.0),Set.of());
    @Test void directionCostAndPrivateSnapshotArePreservedAfterSourceInvalidation() {
        var source=new PassabilityCache(20);Waypoint a=point(0,1),b=point(1,2);
        source.observeDirectedConnection(a,b,4,NOW);
        var copy=source.copy();source.invalidate(b);
        assertEquals(NavigationResult.Status.FOUND,new WalkAStar(copy).find(a,b,WALKER,NOW,Duration.ofSeconds(1),20).status());
        assertEquals(4,new WalkAStar(copy).find(a,b,WALKER,NOW,Duration.ofSeconds(1),20).estimatedSeconds());
        assertEquals(NavigationResult.Status.UNKNOWN,new WalkAStar(copy).find(b,a,WALKER,NOW,Duration.ofSeconds(1),20).status());
        assertEquals(NavigationResult.Status.UNKNOWN,new WalkAStar(source).find(a,b,WALKER,NOW,Duration.ofSeconds(1),20).status());
    }
    @Test void unknownGoalKeepsOnlyVerifiedProgressAndMultipleInteractionPositionsAreSupported() {
        var source=new PassabilityCache(20);Waypoint a=point(0,1),b=point(1,1),goal=point(3,1);
        source.observeConnection(a,b,NOW);
        var partial=new WalkAStar(source).findAny(a,Set.of(),goal,WALKER,NOW,Duration.ofSeconds(1),20);
        assertEquals(NavigationResult.Status.PARTIAL,partial.status());
        assertEquals(List.of(a,b),partial.points());
        var found=new WalkAStar(source).findAny(a,Set.of(b),goal,WALKER,NOW,Duration.ofSeconds(1),20);
        assertEquals(NavigationResult.Status.FOUND,found.status());
        assertEquals(b,found.points().getLast());
    }
    @Test void compressionKeepsCornersReversalsAndHeightChanges() {
        Waypoint a=point(0,1),b=point(1,1),c=point(2,1),up=point(3,1.5);
        assertEquals(List.of(a,c,up,c),PathGeometry.compress(List.of(a,b,c,up,c)));
        assertThrows(IllegalArgumentException.class,()->new Waypoint("scope","world",Double.NaN,1,0));
        var cache=new PassabilityCache(20);
        assertThrows(IllegalArgumentException.class,()->cache.observeDirectedConnection(a,b,.5,NOW));
        assertEquals(0,cache.size());
    }
    private static Waypoint point(double x,double y) {return new Waypoint("scope","world",x,y,0);}
}
