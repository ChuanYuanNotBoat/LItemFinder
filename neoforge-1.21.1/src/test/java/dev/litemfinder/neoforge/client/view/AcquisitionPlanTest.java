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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcquisitionPlanTest {

    @Test
    void allocatesExactVariantsAndNeverReservesOneSlotTwice() {
        ItemKey normal = ItemKey.parse("minecraft:potion");
        ItemKey healing = normal.withVariant("components:v1:healing");
        StorageEntry first = entry(normal, 8, 0);
        StorageEntry second = entry(healing, 4, 1);
        SearchResponse response = new SearchResponse(List.of(
                new SearchResult(normal, List.of(first)),
                new SearchResult(healing, List.of(second))
        ));

        AcquisitionPlan plan = AcquisitionPlan.allocate(response, Map.of(normal, 10L, healing, 3L));

        assertEquals(2, plan.picks().size());
        assertEquals(2, plan.missing().get(normal));
        assertEquals(3, plan.picks().stream()
                .filter(pick -> pick.item().equals(healing)).findFirst().orElseThrow().count());

        AcquisitionPlan.ReservationLedger ledger = new AcquisitionPlan.ReservationLedger();
        assertEquals(5, ledger.reserve(first, 5));
        assertEquals(3, ledger.reserve(first, 5));
        assertEquals(0, ledger.reserve(first, 1));
    }

    private static StorageEntry entry(ItemKey item, long count, int slot) {
        ContainerRecord root = new ContainerRecord(new ContainerId("root"), ContainerType.CHEST,
                new LogicalLocation("scope", "root"));
        return new StorageEntry(root, root, ContainerPath.root(root.id()), slot,
                new ItemStackInfo(item, count), Instant.parse("2026-09-23T00:00:00Z"));
    }
}
