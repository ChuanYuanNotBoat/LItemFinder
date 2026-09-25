package dev.litemfinder.neoforge.client.gui.map;

import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerRouteOptimizerTest {

    private static final String SCOPE = "world";
    private static final String DIMENSION = "minecraft:overworld";
    private static final WorldPosition ORIGIN = new WorldPosition(SCOPE, DIMENSION, 0, 64, 0);

    @Test
    void findsShortestOpenRouteWhenNearestNeighbourWouldTakeTheLongWayAround() {
        WorldLocation farBottom = stop(-3, -3);
        WorldLocation lower = stop(-3, -2);
        WorldLocation upper = stop(-3, -1);
        WorldLocation farTop = stop(-3, 1);

        assertEquals(List.of(farTop, upper, lower, farBottom), ContainerRouteOptimizer.order(ORIGIN,
                List.of(farBottom, lower, upper, farTop)));
    }

    @Test
    void exactOrderDoesNotDependOnAllocationOrder() {
        List<WorldLocation> expected = ContainerRouteOptimizer.order(ORIGIN,
                List.of(stop(9, 2), stop(-4, 6), stop(2, -8), stop(1, 1)));

        assertEquals(expected, ContainerRouteOptimizer.order(ORIGIN,
                List.of(stop(1, 1), stop(2, -8), stop(9, 2), stop(-4, 6))));
    }

    @Test
    void defersStopsWhoseTravelCostRequiresCrossDimensionTopology() {
        WorldLocation nether = new WorldLocation(SCOPE, "minecraft:the_nether", 0, 64, 0);
        WorldLocation near = stop(2, 0);

        assertEquals(List.of(near, nether), ContainerRouteOptimizer.order(ORIGIN, List.of(nether, near)));
    }

    private static WorldLocation stop(int x, int z) {
        return new WorldLocation(SCOPE, DIMENSION, x, 64, z);
    }
}
