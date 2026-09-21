package dev.litemfinder.core.planner;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerLocation;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {

    @Test
    void visitsNearestReachableContainersAndCollectsAllNeededItemsAtEachStop() {
        ItemKey iron = ItemKey.parse("minecraft:iron_ingot");
        ItemKey diamond = ItemKey.parse("minecraft:diamond");
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        index.update(snapshot(
                "near",
                new WorldLocation("world", "minecraft:overworld", 3, 64, 4),
                List.of(stack(iron, 30), stack(diamond, 5))
        ));
        index.update(snapshot(
                "far",
                new WorldLocation("world", "minecraft:overworld", 10, 64, 0),
                List.of(stack(iron, 70))
        ));
        index.update(snapshot(
                "other-dimension",
                new WorldLocation("world", "minecraft:the_nether", 0, 64, 0),
                List.of(stack(iron, 999))
        ));
        index.update(snapshot(
                "logical",
                new LogicalLocation("world", "ender-chest"),
                List.of(stack(iron, 999))
        ));

        RoutePlan plan = new RoutePlanner(index).plan(
                new WorldPosition("world", "minecraft:overworld", 0, 64, 0),
                List.of(new ItemRequirement(iron, 80), new ItemRequirement(diamond, 5))
        );

        assertTrue(plan.fulfilled());
        assertEquals(List.of("near", "far"), plan.stops().stream()
                .map(stop -> stop.container().id().value())
                .toList());
        assertEquals(30, plan.stops().getFirst().pickup().get(iron));
        assertEquals(5, plan.stops().getFirst().pickup().get(diamond));
        assertEquals(50, plan.stops().getLast().pickup().get(iron));
        assertEquals(5.0 + Math.sqrt(65.0), plan.totalDistance(), 0.000_001);
    }

    @Test
    void aggregatesDuplicateRequirementsAndReportsMissingQuantity() {
        ItemKey iron = ItemKey.parse("minecraft:iron_ingot");
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        index.update(snapshot(
                "only-chest",
                new WorldLocation("world", "minecraft:overworld", 1, 64, 0),
                List.of(stack(iron, 10))
        ));

        RoutePlan plan = new RoutePlanner(index).plan(
                new WorldPosition("world", "minecraft:overworld", 0, 64, 0),
                List.of(new ItemRequirement(iron, 8), new ItemRequirement(iron, 7))
        );

        assertFalse(plan.fulfilled());
        assertEquals(5, plan.missing().get(iron));
        assertEquals(10, plan.stops().getFirst().pickup().get(iron));
    }

    private static InventorySnapshot snapshot(
            String id,
            ContainerLocation location,
            List<ItemStackInfo> stacks
    ) {
        List<SlotSnapshot> slots = java.util.stream.IntStream.range(0, stacks.size())
                .mapToObj(slot -> new SlotSnapshot(slot, stacks.get(slot)))
                .toList();
        return new InventorySnapshot(
                new ContainerRecord(new ContainerId(id), ContainerType.CHEST, location),
                27,
                Instant.EPOCH,
                slots
        );
    }

    private static ItemStackInfo stack(ItemKey item, long count) {
        return new ItemStackInfo(item, count);
    }
}
