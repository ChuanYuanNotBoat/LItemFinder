package dev.litemfinder.core.index;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.core.model.WorldLocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotFlattenerTest {

    @Test
    void flattensRootAndNestedItemsWithCompletePaths() {
        Instant rootTime = Instant.parse("2026-09-21T10:00:00Z");
        Instant nestedTime = Instant.parse("2026-09-21T09:55:00Z");
        InventorySnapshot shulker = snapshot(
                container("shulker-a", ContainerType.SHULKER_BOX),
                nestedTime,
                List.of(new SlotSnapshot(12, stack("minecraft:diamond", 32)))
        );
        InventorySnapshot chest = snapshot(
                new ContainerRecord(
                        new ContainerId("chest-a"),
                        ContainerType.CHEST,
                        new WorldLocation("server-a", "minecraft:overworld", 10, 64, -3)
                ),
                rootTime,
                List.of(SlotSnapshot.nested(5, stack("minecraft:blue_shulker_box", 1), shulker))
        );

        List<StorageEntry> entries = new SnapshotFlattener().flatten(chest);

        assertEquals(2, entries.size());
        StorageEntry outer = entries.get(0);
        assertEquals("chest-a", outer.path().leaf().value());
        assertEquals(5, outer.slot());
        assertEquals(rootTime, outer.observedAt());

        StorageEntry nested = entries.get(1);
        assertEquals("chest-a", nested.path().root().value());
        assertEquals("shulker-a", nested.path().leaf().value());
        assertEquals(5, nested.path().hops().getFirst().parentSlot());
        assertEquals(12, nested.slot());
        assertEquals(nestedTime, nested.observedAt());
    }

    @Test
    void rejectsRepeatedContainerIdentityInOneTree() {
        ContainerRecord repeated = container("shulker-a", ContainerType.SHULKER_BOX);
        InventorySnapshot left = snapshot(repeated, Instant.EPOCH, List.of());
        InventorySnapshot right = snapshot(repeated, Instant.EPOCH, List.of());
        InventorySnapshot root = snapshot(
                container("chest-a", ContainerType.CHEST),
                Instant.EPOCH,
                List.of(
                        SlotSnapshot.nested(0, stack("minecraft:shulker_box", 1), left),
                        SlotSnapshot.nested(1, stack("minecraft:shulker_box", 1), right)
                )
        );

        assertThrows(IllegalArgumentException.class, () -> new SnapshotFlattener().flatten(root));
    }

    private static InventorySnapshot snapshot(
            ContainerRecord container,
            Instant capturedAt,
            List<SlotSnapshot> slots
    ) {
        return new InventorySnapshot(container, 27, capturedAt, slots);
    }

    private static ContainerRecord container(String id, ContainerType type) {
        return new ContainerRecord(
                new ContainerId(id),
                type,
                new LogicalLocation("server-a", "container/" + id)
        );
    }

    private static ItemStackInfo stack(String id, long count) {
        return new ItemStackInfo(ItemKey.parse(id), count);
    }
}
