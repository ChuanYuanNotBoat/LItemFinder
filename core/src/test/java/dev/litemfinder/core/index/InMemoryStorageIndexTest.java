package dev.litemfinder.core.index;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStorageIndexTest {

    private static final ContainerId ROOT_ID = new ContainerId("chest-a");
    private static final ItemKey DIAMOND = ItemKey.parse("minecraft:diamond");
    private static final ItemKey IRON = ItemKey.parse("minecraft:iron_ingot");

    @Test
    void replacesRootAtomicallyAndIgnoresOlderSnapshots() {
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        Instant firstTime = Instant.parse("2026-09-21T10:00:00Z");
        Instant secondTime = firstTime.plusSeconds(60);

        assertEquals(IndexUpdateResult.ADDED, index.update(snapshot(firstTime, DIAMOND, 12)));
        assertEquals(12, index.findExact(DIAMOND).getFirst().stack().count());
        assertEquals(1, index.rootContainerCount());
        assertEquals(firstTime, index.rootSnapshots().getFirst().capturedAt());

        assertEquals(IndexUpdateResult.IGNORED_STALE, index.update(snapshot(firstTime.minusSeconds(1), IRON, 64)));
        assertTrue(index.findExact(IRON).isEmpty());
        assertEquals(12, index.findExact(DIAMOND).getFirst().stack().count());

        assertEquals(IndexUpdateResult.REPLACED, index.update(snapshot(secondTime, IRON, 48)));
        assertTrue(index.findExact(DIAMOND).isEmpty());
        assertEquals(48, index.findExact(IRON).getFirst().stack().count());
        assertEquals(secondTime, index.latestCaptureTime(ROOT_ID).orElseThrow());
        assertEquals(IRON, index.rootSnapshots().getFirst().slots().getFirst().stack().item());

        assertTrue(index.remove(ROOT_ID));
        assertFalse(index.remove(ROOT_ID));
        assertTrue(index.findExact(IRON).isEmpty());
        assertEquals(0, index.rootContainerCount());
    }

    @Test
    void queryResultsAreImmutableSnapshots() {
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        index.update(snapshot(Instant.EPOCH, DIAMOND, 1));

        List<StorageEntry> result = index.findExact(DIAMOND);

        assertThrows(UnsupportedOperationException.class, () -> result.add(result.getFirst()));
    }

    private static InventorySnapshot snapshot(Instant capturedAt, ItemKey item, long count) {
        ContainerRecord container = new ContainerRecord(
                ROOT_ID,
                ContainerType.CHEST,
                new LogicalLocation("server-a", "container/chest-a")
        );
        return new InventorySnapshot(
                container,
                27,
                capturedAt,
                List.of(new SlotSnapshot(0, new ItemStackInfo(item, count)))
        );
    }
}
