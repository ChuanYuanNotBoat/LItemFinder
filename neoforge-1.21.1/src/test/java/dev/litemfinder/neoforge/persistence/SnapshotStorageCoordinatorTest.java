package dev.litemfinder.neoforge.persistence;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.neoforge.identity.ScopeIdFactory;
import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotStorageCoordinatorTest {

    @Test
    void persistsAndRestoresScopeIndex(@TempDir Path directory) {
        String scope = ScopeIdFactory.singleplayer("world-a");
        ItemKey diamond = ItemKey.parse("minecraft:diamond");
        InventorySnapshot snapshot = snapshot(scope, "chest-a", diamond, 12);
        Path database;

        try (SnapshotStorageCoordinator first = new SnapshotStorageCoordinator(directory)) {
            database = first.databaseFile(scope);
            first.submit(snapshot, true);
            first.flush();
            assertEquals(12, first.currentIndex().findExact(diamond).getFirst().stack().count());
        }

        try (SnapshotStorageCoordinator restarted = new SnapshotStorageCoordinator(directory)) {
            restarted.submit(snapshot(scope, "session", ItemKey.parse("minecraft:stone"), 1), false);
            restarted.flush();
            assertEquals(12, restarted.currentIndex().findExact(diamond).getFirst().stack().count());
        }

        assertTrue(database.toFile().isFile());
    }

    @Test
    void sessionOnlySnapshotsNeverEnterDatabase(@TempDir Path directory) {
        String scope = ScopeIdFactory.multiplayer("example.com", 25565);
        InventorySnapshot session = snapshot(scope, "session-only", ItemKey.parse("minecraft:stone"), 4);
        Path database;

        try (SnapshotStorageCoordinator storage = new SnapshotStorageCoordinator(directory)) {
            database = storage.databaseFile(scope);
            storage.submit(session, false);
            storage.flush();
            assertFalse(storage.currentIndex().findExact(ItemKey.parse("minecraft:stone")).isEmpty());
        }

        try (SqliteSnapshotRepository repository = new SqliteSnapshotRepository(database)) {
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Test
    void differentScopesUseDifferentDatabaseFiles(@TempDir Path directory) {
        String firstScope = ScopeIdFactory.singleplayer("first");
        String secondScope = ScopeIdFactory.singleplayer("second");

        try (SnapshotStorageCoordinator storage = new SnapshotStorageCoordinator(directory)) {
            storage.submit(snapshot(firstScope, "first", ItemKey.parse("minecraft:iron_ingot"), 1), true);
            storage.flush();
            storage.submit(snapshot(secondScope, "second", ItemKey.parse("minecraft:gold_ingot"), 2), true);
            storage.flush();

            assertFalse(storage.databaseFile(firstScope).equals(storage.databaseFile(secondScope)));
            assertTrue(storage.databaseFile(firstScope).toFile().isFile());
            assertTrue(storage.databaseFile(secondScope).toFile().isFile());
        }
    }

    @Test
    void clearsPersistedAndInMemorySnapshotsForCurrentScope(@TempDir Path directory) {
        String scope = ScopeIdFactory.singleplayer("world-to-clear");
        ItemKey diamond = ItemKey.parse("minecraft:diamond");
        Path database;

        try (SnapshotStorageCoordinator storage = new SnapshotStorageCoordinator(directory)) {
            database = storage.databaseFile(scope);
            storage.submit(snapshot(scope, "persisted", diamond, 12), true);
            storage.submit(snapshot(scope, "session", ItemKey.parse("minecraft:stone"), 3), false);
            storage.flush();

            assertEquals(2, storage.currentIndex().rootContainerCount());
            assertEquals(1, storage.clearCurrentScopeData());
            assertEquals(0, storage.currentIndex().rootContainerCount());
        }

        try (SqliteSnapshotRepository repository = new SqliteSnapshotRepository(database)) {
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Test
    void logoutAfterShutdownIsAnIdempotentNoOp(@TempDir Path directory) {
        SnapshotStorageCoordinator storage = new SnapshotStorageCoordinator(directory);
        storage.close();

        storage.leaveScope();
        storage.flush();

        assertEquals(-1, storage.clearCurrentScopeData());
    }

    @Test
    void asynchronouslyRemovesAContainerAfterEarlierWrites(@TempDir Path directory) throws Exception {
        String scope = ScopeIdFactory.singleplayer("world-remove");
        InventorySnapshot snapshot = snapshot(scope, "remove-me", ItemKey.parse("minecraft:coal"), 7);
        Path database;

        try (SnapshotStorageCoordinator storage = new SnapshotStorageCoordinator(directory)) {
            database = storage.databaseFile(scope);
            storage.submit(snapshot, true);

            assertTrue(storage.removeCurrentScopeContainer(snapshot).get());
            assertEquals(0, storage.currentIndex().rootContainerCount());
        }

        try (SqliteSnapshotRepository repository = new SqliteSnapshotRepository(database)) {
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Test
    void doesNotRemoveAReplacementWrittenAfterTheObservedSnapshot(@TempDir Path directory) throws Exception {
        String scope = ScopeIdFactory.singleplayer("world-replacement");
        Instant observedAt = Instant.parse("2026-09-21T00:00:00Z");
        InventorySnapshot observed = snapshot(
                scope, "same-container", ItemKey.parse("minecraft:coal"), 7, observedAt
        );
        InventorySnapshot replacement = snapshot(
                scope, "same-container", ItemKey.parse("minecraft:diamond"), 3, observedAt.plusSeconds(1)
        );

        try (SnapshotStorageCoordinator storage = new SnapshotStorageCoordinator(directory)) {
            storage.submit(observed, true);
            storage.flush();
            storage.submit(replacement, true);

            assertFalse(storage.removeCurrentScopeContainer(observed).get());
            assertEquals(replacement, storage.currentIndex().rootSnapshots().getFirst());
        }
    }

    @Test
    void doesNotRemoveAnEqualIdFromAnotherScope(@TempDir Path directory) throws Exception {
        String firstScope = ScopeIdFactory.singleplayer("first-removal-scope");
        String secondScope = ScopeIdFactory.singleplayer("second-removal-scope");
        InventorySnapshot first = snapshot(firstScope, "shared-id", ItemKey.parse("minecraft:coal"), 7);
        InventorySnapshot second = snapshot(secondScope, "shared-id", ItemKey.parse("minecraft:gold_ingot"), 4);

        try (SnapshotStorageCoordinator storage = new SnapshotStorageCoordinator(directory)) {
            storage.submit(first, true);
            storage.flush();
            storage.submit(second, true);
            storage.flush();

            assertFalse(storage.removeCurrentScopeContainer(first).get());
            assertEquals(second, storage.currentIndex().rootSnapshots().getFirst());
        }
    }

    private static InventorySnapshot snapshot(String scope, String id, ItemKey item, long count) {
        return snapshot(scope, id, item, count, Instant.parse("2026-09-21T00:00:00Z"));
    }

    private static InventorySnapshot snapshot(
            String scope,
            String id,
            ItemKey item,
            long count,
            Instant capturedAt
    ) {
        ContainerRecord container = new ContainerRecord(
                new ContainerId(id),
                new ContainerType(new NamespacedId("minecraft", "chest")),
                new LogicalLocation(scope, id)
        );
        return new InventorySnapshot(
                container,
                27,
                capturedAt,
                List.of(new SlotSnapshot(0, new ItemStackInfo(item, count)))
        );
    }
}
