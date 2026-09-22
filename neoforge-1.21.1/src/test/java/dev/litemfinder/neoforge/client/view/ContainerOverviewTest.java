package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.LogicalLocation;
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
    }
}
