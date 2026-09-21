package dev.litemfinder.core.search;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.SlotSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexedSearchEngineTest {

    @Test
    void searchesOneHundredItemKindsAcrossTenContainers() {
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        ItemKey target = ItemKey.parse("test:item_42");

        for (int containerNumber = 0; containerNumber < 10; containerNumber++) {
            List<SlotSnapshot> slots = new ArrayList<>();
            for (int itemNumber = 0; itemNumber < 100; itemNumber++) {
                slots.add(new SlotSnapshot(
                        itemNumber,
                        new ItemStackInfo(ItemKey.parse("test:item_" + itemNumber), containerNumber + 1L)
                ));
            }
            index.update(new InventorySnapshot(
                    new ContainerRecord(
                            new ContainerId("chest-" + containerNumber),
                            ContainerType.CHEST,
                            new LogicalLocation("test-world", "warehouse/chest-" + containerNumber)
                    ),
                    100,
                    Instant.EPOCH.plusSeconds(containerNumber),
                    slots
            ));
        }

        SearchResult result = new IndexedSearchEngine(index).findExact(target);

        assertEquals(10, result.entries().size());
        assertEquals(55, result.totalCount());
        assertEquals("chest-9", result.entries().getFirst().rootContainer().id().value());
    }

    @Test
    void exactSearchDoesNotMixVariants() {
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        ItemKey base = ItemKey.parse("minecraft:potion");
        ItemKey healing = base.withVariant("potion=minecraft:healing");
        InventorySnapshot snapshot = new InventorySnapshot(
                new ContainerRecord(
                        new ContainerId("chest-a"),
                        ContainerType.CHEST,
                        new LogicalLocation("test-world", "warehouse/chest-a")
                ),
                2,
                Instant.EPOCH,
                List.of(
                        new SlotSnapshot(0, new ItemStackInfo(base, 1)),
                        new SlotSnapshot(1, new ItemStackInfo(healing, 2))
                )
        );
        index.update(snapshot);

        SearchResult result = new IndexedSearchEngine(index).findExact(healing);

        assertEquals(2, result.totalCount());
        assertEquals(1, result.entries().size());
        assertTrue(new IndexedSearchEngine(index).findExact(ItemKey.parse("minecraft:water")).isEmpty());
    }
}
