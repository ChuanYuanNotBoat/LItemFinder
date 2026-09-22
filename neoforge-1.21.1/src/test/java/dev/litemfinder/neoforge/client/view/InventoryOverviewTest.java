package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerPath;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.search.SearchResponse;
import dev.litemfinder.core.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryOverviewTest {

    @Test
    void groupsVariantsAndSortsByDescendingTotalThenId() {
        ItemKey potion = ItemKey.parse("minecraft:potion");
        ItemKey variant = potion.withVariant("components:v1:healing");
        ItemKey diamond = ItemKey.parse("minecraft:diamond");
        var overview = InventoryOverview.from(new SearchResponse(List.of(
                new SearchResult(potion, List.of(entry(potion, 40))),
                new SearchResult(diamond, List.of(entry(diamond, 64))),
                new SearchResult(variant, List.of(entry(variant, 24)))
        )));

        assertEquals(2, overview.rows().size());
        assertEquals(diamond.itemId(), overview.rows().get(0).itemId());
        assertEquals(potion.itemId(), overview.rows().get(1).itemId());
        assertEquals(64, overview.rows().get(1).totalCount());
        assertEquals(2, overview.rows().get(1).variants().size());
    }

    private static StorageEntry entry(ItemKey item, long count) {
        var root = new ContainerRecord(
                new ContainerId("test-root"),
                ContainerType.CHEST,
                new LogicalLocation("test-scope", "test-root")
        );
        return new StorageEntry(
                root,
                root,
                ContainerPath.root(root.id()),
                0,
                new ItemStackInfo(item, count),
                Instant.parse("2026-09-23T00:00:00Z")
        );
    }
}
