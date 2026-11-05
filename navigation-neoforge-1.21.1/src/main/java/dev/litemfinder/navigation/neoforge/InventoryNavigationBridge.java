package dev.litemfinder.navigation.neoforge;

import dev.litemfinder.navigation.PathGeometry;
import dev.litemfinder.navigation.Waypoint;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.view.NavigationReport;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;

import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CompletionStage;

/** Loaded only by the inventory Mod's optional service lookup; standalone navigation never touches this class. */
public final class InventoryNavigationBridge implements NavigationGateway {
    @Override public long revision() {return LItemNavigationClient.service().revision();}
    @Override public CompletionStage<Overlay> route(WorldPosition origin,List<WorldLocation> stops) {
        var start=new Waypoint(origin.scope(),origin.dimension().toString(),origin.x(),origin.y(),origin.z());
        var targets=stops.stream().map(stop->new Waypoint(stop.scope(),stop.dimension().toString(),stop.x(),stop.y(),stop.z())).toList();
        return LItemNavigationClient.service().routeToBlocks(start,targets).thenApply(result->new Overlay(
                Status.valueOf(result.status().name()),result.legs().stream().filter(leg->leg.points().size()>=2).map(leg->new Segment("WALK",
                PathGeometry.compress(leg.points()).stream().map(point->new WorldPosition(point.scope(),point.dimension(),
                        point.x(),point.y(),point.z())).toList(),OptionalDouble.of(leg.estimatedSeconds()))).toList(),result.estimatedSeconds(),
                new NavigationReport(NavigationReport.Reason.valueOf(result.reason().name()),NavigationReport.Estimate.BASE_SPEED,
                        result.verifiedStops(),result.checkedAt(),result.checkedAt().isPresent()
                        ?NavigationReport.Confidence.LOCALLY_CHECKED:NavigationReport.Confidence.UNSPECIFIED,
                        result.checkedAt().isPresent()?NavigationReport.Source.CLIENT_COLLISION:NavigationReport.Source.UNSPECIFIED)));
    }
}
