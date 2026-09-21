package dev.litemfinder.core.search;

import dev.litemfinder.core.classification.ItemTagResolver;
import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerLocation;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvancedSearchTest {

    @Test
    void combinesTextNamespaceTagAndMinimumQuantityFilters() {
        ItemKey iron = ItemKey.parse("minecraft:iron_ingot");
        ItemKey createIron = ItemKey.parse("create:iron_sheet");
        NamespacedId metals = NamespacedId.parse("c:metals");
        ItemTagResolver tags = item -> Map.of(
                iron, Set.of(metals),
                createIron, Set.of(metals)
        ).getOrDefault(item, Set.of());
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        index.update(snapshot("chest-a", new LogicalLocation("world", "chest-a"), iron, 20, 1));
        index.update(snapshot("chest-b", new LogicalLocation("world", "chest-b"), createIron, 64, 2));

        SearchQuery query = SearchQuery.all()
                .withText("iron")
                .withNamespace("minecraft")
                .withRequiredTags(Set.of(metals))
                .withMinimumTotalCount(10);
        SearchResponse response = new IndexedSearchEngine(index, tags).search(query);

        assertEquals(1, response.results().size());
        assertEquals(iron, response.results().getFirst().item());
        assertEquals(20, response.totalCount());
    }

    @Test
    void ordersLocationsByDistanceAndLeavesLogicalLocationsLast() {
        ItemKey diamond = ItemKey.parse("minecraft:diamond");
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        index.update(snapshot(
                "far",
                new WorldLocation("world", "minecraft:overworld", 100, 64, 0),
                diamond,
                1,
                3
        ));
        index.update(snapshot(
                "logical",
                new LogicalLocation("world", "ender-chest"),
                diamond,
                1,
                4
        ));
        index.update(snapshot(
                "near",
                new WorldLocation("world", "minecraft:overworld", 3, 64, 4),
                diamond,
                1,
                1
        ));

        SearchResponse response = new IndexedSearchEngine(index).search(
                SearchQuery.all()
                        .withText("diamond")
                        .withOrigin(new WorldPosition("world", "minecraft:overworld", 0, 64, 0))
        );

        List<String> orderedIds = response.results().getFirst().entries().stream()
                .map(entry -> entry.rootContainer().id().value())
                .toList();
        assertEquals(List.of("near", "far", "logical"), orderedIds);
    }

    private static InventorySnapshot snapshot(
            String containerId,
            ContainerLocation location,
            ItemKey item,
            long count,
            long timestamp
    ) {
        return new InventorySnapshot(
                new ContainerRecord(new ContainerId(containerId), ContainerType.CHEST, location),
                27,
                Instant.EPOCH.plusSeconds(timestamp),
                List.of(new SlotSnapshot(0, new ItemStackInfo(item, count)))
        );
    }
}
