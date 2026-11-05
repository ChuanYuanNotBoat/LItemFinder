package dev.litemfinder.neoforge.client.navigation;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.view.NavigationReport;
import dev.litemfinder.core.view.NavigationSummary;

import java.util.List;

/** Maps optional provider outcomes to Core presentation facts without discarding unsuccessful outcomes. */
public final class NavigationSummaries {
    private NavigationSummaries() { }

    public static NavigationSummary from(List<WorldLocation> stops, NavigationGateway.Overlay overlay,
                                         boolean provider, boolean pending, boolean failed) {
        var state = stops.isEmpty() ? NavigationSummary.State.IDLE
                : failed ? NavigationSummary.State.FAILED
                : overlay != null ? NavigationSummary.State.valueOf(overlay.status().name())
                : pending ? NavigationSummary.State.CALCULATING
                : provider ? NavigationSummary.State.UNKNOWN : NavigationSummary.State.SCHEMATIC;
        return new NavigationSummary(state, pending && overlay != null, stops,
                overlay == null ? List.of() : overlay.segments().stream().map(segment ->
                        new NavigationSummary.Leg(segment.mode(), segment.points(), segment.estimatedSeconds())).toList(),
                overlay == null ? 0 : overlay.estimatedSeconds(),
                overlay == null ? NavigationReport.unspecified() : overlay.report());
    }
}
