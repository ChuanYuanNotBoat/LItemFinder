package dev.litemfinder.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySnapshotTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-09-21T10:15:30Z");

    @Test
    void representsNestedContainersWithoutGameTypes() {
        InventorySnapshot shulker = new InventorySnapshot(
                container("shulker-a", ContainerType.SHULKER_BOX, new LogicalLocation("test-world", "nested/shulker-a")),
                27,
                CAPTURED_AT,
                List.of(new SlotSnapshot(12, new ItemStackInfo(ItemKey.parse("minecraft:diamond"), 32)))
        );

        InventorySnapshot chest = new InventorySnapshot(
                container("chest-a", ContainerType.CHEST, new WorldLocation("test-world", "minecraft:overworld", 10, 64, -3)),
                27,
                CAPTURED_AT,
                List.of(SlotSnapshot.nested(
                        5,
                        new ItemStackInfo(ItemKey.parse("minecraft:blue_shulker_box"), 1),
                        shulker
                ))
        );

        InventorySnapshot nested = chest.slots().getFirst().nestedContainer().orElseThrow();
        assertEquals(new ContainerId("shulker-a"), nested.container().id());
        assertEquals(ItemKey.parse("minecraft:diamond"), nested.slots().getFirst().stack().item());
    }

    @Test
    void copiesAndOrdersOccupiedSlots() {
        List<SlotSnapshot> mutableSlots = new ArrayList<>();
        mutableSlots.add(new SlotSnapshot(4, new ItemStackInfo(ItemKey.parse("minecraft:stone"), 64)));
        mutableSlots.add(new SlotSnapshot(1, new ItemStackInfo(ItemKey.parse("minecraft:iron_ingot"), 8)));

        InventorySnapshot snapshot = new InventorySnapshot(
                container("chest-a", ContainerType.CHEST, new WorldLocation("world", "minecraft:overworld", 0, 64, 0)),
                9,
                CAPTURED_AT,
                mutableSlots
        );
        mutableSlots.clear();

        assertEquals(List.of(1, 4), snapshot.slots().stream().map(SlotSnapshot::slot).toList());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.slots().add(
                new SlotSnapshot(8, new ItemStackInfo(ItemKey.parse("minecraft:dirt"), 1))
        ));
    }

    @Test
    void rejectsInvalidSnapshotState() {
        ContainerRecord container = container(
                "chest-a",
                ContainerType.CHEST,
                new WorldLocation("world", "minecraft:overworld", 0, 64, 0)
        );
        SlotSnapshot occupied = new SlotSnapshot(2, new ItemStackInfo(ItemKey.parse("minecraft:stone"), 1));

        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(
                container, 2, CAPTURED_AT, List.of(occupied)
        ));
        assertThrows(IllegalArgumentException.class, () -> new InventorySnapshot(
                container, 9, CAPTURED_AT, List.of(occupied, occupied)
        ));
        assertThrows(IllegalArgumentException.class, () -> new ItemStackInfo(ItemKey.parse("minecraft:air"), 0));
    }

    @Test
    void copiesContainerMetadata() {
        Map<String, String> mutableMetadata = new java.util.HashMap<>();
        mutableMetadata.put("customName", "Minerals");

        ContainerRecord record = new ContainerRecord(
                new ContainerId("chest-a"),
                ContainerType.CHEST,
                new LogicalLocation("world", "warehouse/chest-a"),
                mutableMetadata
        );
        mutableMetadata.clear();

        assertEquals("Minerals", record.metadata().get("customName"));
        assertThrows(UnsupportedOperationException.class, () -> record.metadata().put("color", "blue"));
    }

    @Test
    void appendsNestedPathWithoutChangingParent() {
        ContainerPath root = ContainerPath.root(new ContainerId("chest-a"));
        ContainerPath nested = root.append(5, new ContainerId("shulker-a"));

        assertTrue(root.hops().isEmpty());
        assertEquals(new ContainerId("shulker-a"), nested.leaf());
        assertEquals(5, nested.hops().getFirst().parentSlot());
    }

    private static ContainerRecord container(
            String id,
            ContainerType type,
            ContainerLocation location
    ) {
        return new ContainerRecord(new ContainerId(id), type, location);
    }
}
