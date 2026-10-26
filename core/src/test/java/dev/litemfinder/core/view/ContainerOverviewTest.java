package dev.litemfinder.core.view;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerOverviewTest {

    @Test
    void includesAnEmptyRoot() {
        ContainerRecord empty = new ContainerRecord(new ContainerId("empty"), ContainerType.CHEST,
                new LogicalLocation("scope", "empty"));
        var view = ContainerOverview.from(List.of(new InventorySnapshot(empty, 27,
                Instant.parse("2026-09-23T00:00:00Z"), List.of())));

        assertEquals(1, view.roots().size());
        assertEquals(27, view.roots().get(0).slots());
        assertEquals(0, view.roots().get(0).occupiedSlots());
        assertEquals(List.of(), view.roots().get(0).contents());
    }

    @Test
    void exposesRootSlotsAndNestedSnapshotsForReadOnlyPreview() {
        Instant observedAt = Instant.parse("2026-09-23T00:00:00Z");
        ContainerRecord shulker = new ContainerRecord(new ContainerId("nested"), ContainerType.SHULKER_BOX,
                new LogicalLocation("scope", "nested"));
        InventorySnapshot nested = new InventorySnapshot(shulker, 27, observedAt,
                List.of(new SlotSnapshot(2, new ItemStackInfo(ItemKey.parse("minecraft:diamond"), 4))));
        ContainerRecord chest = new ContainerRecord(new ContainerId("root"), ContainerType.CHEST,
                new LogicalLocation("scope", "root"));
        InventorySnapshot root = new InventorySnapshot(chest, 27, observedAt,
                List.of(SlotSnapshot.nested(5,
                        new ItemStackInfo(ItemKey.parse("minecraft:shulker_box"), 1), nested)));

        ContainerOverview.RootRow row = ContainerOverview.from(List.of(root)).roots().getFirst();

        assertEquals(5, row.contents().getFirst().slot());
        assertEquals(ItemKey.parse("minecraft:diamond"), row.contents().getFirst().nestedContainer()
                .orElseThrow().slots().getFirst().stack().item());
    }
}
