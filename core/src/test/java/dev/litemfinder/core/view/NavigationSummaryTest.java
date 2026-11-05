package dev.litemfinder.core.view;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class NavigationSummaryTest {
    private static final List<WorldLocation> STOPS = List.of(new WorldLocation("world", "minecraft:overworld", 30, 64, 0));
    private static NavigationReport report(int stops) {
        return new NavigationReport(NavigationReport.Reason.BUDGET, NavigationReport.Estimate.BASE_SPEED, stops,
                Optional.of(Instant.EPOCH), NavigationReport.Confidence.LOCALLY_CHECKED, NavigationReport.Source.CLIENT_COLLISION);
    }
    private static NavigationSummary.Leg leg() {
        return new NavigationSummary.Leg("WALK", List.of(new WorldPosition("world", "minecraft:overworld", .5, 64, .5),
                new WorldPosition("world", "minecraft:overworld", 10.5, 64, .5)), OptionalDouble.of(3));
    }

    @Test void partialEstimateNeverExtrapolatesTheUnverifiedRemainderOrClaimsAStopWasReached() {
        var summary = new NavigationSummary(NavigationSummary.State.PARTIAL, false, STOPS, List.of(leg()), 3, report(0));
        assertTrue(summary.partialEstimate());
        assertEquals(3, summary.knownSeconds().orElseThrow());
        assertEquals(0, summary.verifiedStops().orElseThrow());
        assertEquals(leg().end(), summary.gapStart().orElseThrow());
    }
    @Test void schematicCalculatingAndUnknownOutcomesHaveNoFabricatedTimeEstimate() {
        for (var state : List.of(NavigationSummary.State.SCHEMATIC, NavigationSummary.State.CALCULATING,
                NavigationSummary.State.UNKNOWN, NavigationSummary.State.UNREACHABLE, NavigationSummary.State.FAILED)) {
            var summary = new NavigationSummary(state, false, STOPS, List.of(), 0, NavigationReport.unspecified());
            assertTrue(summary.knownSeconds().isEmpty());
            assertTrue(summary.gapStart().isEmpty());
        }
    }
    @Test void olderProviderEvidenceStaysUnspecifiedAndPartialCoverageIsNotInferredFromGeometry() {
        var summary = new NavigationSummary(NavigationSummary.State.PARTIAL, true, STOPS, List.of(leg()), 3, NavigationReport.unspecified());
        assertTrue(summary.updating());
        assertTrue(summary.verifiedStops().isEmpty());
        assertTrue(summary.report().checkedAt().isEmpty());
        assertEquals(NavigationReport.Confidence.UNSPECIFIED, summary.report().confidence());
        assertEquals(NavigationReport.Source.UNSPECIFIED, summary.report().source());
    }
    @Test void completeStopCoverageMustAgreeWithTheRequest() {
        assertThrows(IllegalArgumentException.class, () -> new NavigationSummary(NavigationSummary.State.FOUND,
                false, STOPS, List.of(leg()), 3, report(0)));
        assertThrows(IllegalArgumentException.class, () -> new NavigationSummary(NavigationSummary.State.PARTIAL,
                false, STOPS, List.of(leg()), 3, report(2)));
        var legacy = new NavigationSummary(NavigationSummary.State.FOUND, false, STOPS, List.of(leg()), 3, NavigationReport.unspecified());
        assertEquals(1, legacy.verifiedStops().orElseThrow());
    }
    @Test void routeSegmentsPreserveModesAndDimensionTransitions() {
        var portal = new NavigationSummary.Leg("PORTAL", List.of(leg().end(),
                new WorldPosition("world", "minecraft:the_nether", 1, 64, 0)), OptionalDouble.empty());
        var summary = new NavigationSummary(NavigationSummary.State.PARTIAL, false, STOPS,
                List.of(leg(), portal), 3, NavigationReport.unspecified());
        assertEquals(List.of("WALK", "PORTAL"), summary.modes());
        assertTrue(portal.changesDimension());
        assertTrue(portal.estimatedSeconds().isEmpty());
    }
    @Test void summariesRejectMixedWorldsInvalidNumbersAndKeepAnImmutableSnapshot() {
        var stops = new ArrayList<>(STOPS);
        var summary = new NavigationSummary(NavigationSummary.State.PARTIAL, false, stops, List.of(leg()), 3, report(0));
        stops.clear();
        assertEquals(1, summary.stops().size());
        assertThrows(IllegalArgumentException.class, () -> new NavigationSummary(NavigationSummary.State.PARTIAL, false,
                List.of(new WorldLocation("other", "minecraft:overworld", 30, 64, 0)), List.of(leg()), 3, report(0)));
        assertThrows(IllegalArgumentException.class, () -> new NavigationSummary(NavigationSummary.State.PARTIAL,
                false, STOPS, List.of(leg()), Double.NaN, report(0)));
        assertThrows(IllegalArgumentException.class, () -> new NavigationSummary.Leg("WALK", leg().points(), OptionalDouble.of(-1)));
    }
}
