package dev.litemfinder.storage.sqlite;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.persistence.SnapshotIndexLoader;
import dev.litemfinder.core.persistence.SnapshotRepositoryException;
import dev.litemfinder.core.persistence.SnapshotWriteResult;
import dev.litemfinder.core.search.IndexedSearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSnapshotRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsRecursiveSnapshotAndRestoresIndexAfterRestart() {
        Path database = temporaryDirectory.resolve("snapshots.db");
        InventorySnapshot original = nestedSnapshot(Instant.parse("2026-09-21T12:00:00Z"));

        try (SqliteSnapshotRepository repository = new SqliteSnapshotRepository(database)) {
            assertEquals(SnapshotWriteResult.ADDED, repository.save(original));
            assertEquals(original, repository.find(new ContainerId("chest-a")).orElseThrow());
        }

        try (SqliteSnapshotRepository reopened = new SqliteSnapshotRepository(database)) {
            InMemoryStorageIndex index = new InMemoryStorageIndex();
            assertEquals(1, new SnapshotIndexLoader().load(reopened, index));

            var result = new IndexedSearchEngine(index).findExact(
                    ItemKey.parse("minecraft:potion").withVariant("potion=minecraft:healing")
            );
            assertEquals(3, result.totalCount());
            assertEquals("shulker-a", result.entries().getFirst().path().leaf().value());
        }
    }

    @Test
    void replacesNewerSnapshotRejectsOlderAndDeletesTree() {
        Path database = temporaryDirectory.resolve("updates.db");
        Instant initialTime = Instant.parse("2026-09-21T12:00:00Z");
        InventorySnapshot initial = singleItemSnapshot(initialTime, "minecraft:diamond", 4);
        InventorySnapshot older = singleItemSnapshot(initialTime.minusSeconds(1), "minecraft:dirt", 64);
        InventorySnapshot newer = singleItemSnapshot(initialTime.plusSeconds(1), "minecraft:emerald", 9);

        try (SqliteSnapshotRepository repository = new SqliteSnapshotRepository(database)) {
            assertEquals(SnapshotWriteResult.ADDED, repository.save(initial));
            assertEquals(SnapshotWriteResult.IGNORED_STALE, repository.save(older));
            assertEquals(initial, repository.find(new ContainerId("chest-a")).orElseThrow());

            assertEquals(SnapshotWriteResult.REPLACED, repository.save(newer));
            assertEquals(newer, repository.find(new ContainerId("chest-a")).orElseThrow());
            assertEquals(1, repository.findAll().size());

            assertTrue(repository.delete(new ContainerId("chest-a")));
            assertFalse(repository.delete(new ContainerId("chest-a")));
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Test
    void rejectsNewerSchemaVersionAndReleasesDatabaseFile() throws Exception {
        Path database = temporaryDirectory.resolve("future-schema.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 999");
        }

        assertThrows(
                SnapshotRepositoryException.class,
                () -> new SqliteSnapshotRepository(database)
        );
        Files.delete(database);
        assertFalse(Files.exists(database));
    }

    private static InventorySnapshot nestedSnapshot(Instant capturedAt) {
        InventorySnapshot shulker = new InventorySnapshot(
                new ContainerRecord(
                        new ContainerId("shulker-a"),
                        ContainerType.SHULKER_BOX,
                        new LogicalLocation("server-a", "nested/shulker-a"),
                        Map.of("color", "blue")
                ),
                27,
                capturedAt.minusSeconds(5),
                List.of(new SlotSnapshot(
                        12,
                        new ItemStackInfo(
                                ItemKey.parse("minecraft:potion")
                                        .withVariant("potion=minecraft:healing"),
                                3
                        )
                ))
        );
        return new InventorySnapshot(
                new ContainerRecord(
                        new ContainerId("chest-a"),
                        ContainerType.CHEST,
                        new WorldLocation("server-a", "minecraft:overworld", 10, 64, -3),
                        Map.of("customName", "Brewing supplies")
                ),
                27,
                capturedAt,
                List.of(SlotSnapshot.nested(
                        5,
                        new ItemStackInfo(ItemKey.parse("minecraft:blue_shulker_box"), 1),
                        shulker
                ))
        );
    }

    private static InventorySnapshot singleItemSnapshot(Instant capturedAt, String itemId, long count) {
        return new InventorySnapshot(
                new ContainerRecord(
                        new ContainerId("chest-a"),
                        ContainerType.CHEST,
                        new LogicalLocation("server-a", "warehouse/chest-a")
                ),
                27,
                capturedAt,
                List.of(new SlotSnapshot(0, new ItemStackInfo(ItemKey.parse(itemId), count)))
        );
    }
}
