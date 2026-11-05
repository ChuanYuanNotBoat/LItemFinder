package dev.litemfinder.neoforge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteRuntimeProbeTest {

    @Test
    void createsAndOpensRepositoryWithRuntimeDriver(@TempDir Path directory) {
        Path database = directory.resolve("probe.db");

        assertDoesNotThrow(() -> SqliteRuntimeProbe.verify(database));

        assertTrue(database.toFile().isFile());
        try (var repository = new dev.litemfinder.storage.sqlite.SqliteSnapshotRepository(database)) {
            assertTrue(repository.findAll().isEmpty());
        }
    }

    @Test
    void repeatedProbeDoesNotDeleteOtherSnapshots(@TempDir Path directory) {
        Path database = directory.resolve("probe.db");
        var id = new dev.litemfinder.core.model.ContainerId("existing");
        var container = new dev.litemfinder.core.model.ContainerRecord(id,
                dev.litemfinder.core.model.ContainerType.CHEST,
                new dev.litemfinder.core.model.LogicalLocation("test", "existing"));
        var snapshot = new dev.litemfinder.core.model.InventorySnapshot(container, 1,
                java.time.Instant.EPOCH, java.util.List.of());
        try (var repository = new dev.litemfinder.storage.sqlite.SqliteSnapshotRepository(database)) {
            repository.save(snapshot);
        }
        SqliteRuntimeProbe.verify(database);
        SqliteRuntimeProbe.verify(database);
        try (var repository = new dev.litemfinder.storage.sqlite.SqliteSnapshotRepository(database)) {
            assertEquals(java.util.List.of(snapshot), repository.findAll());
        }
    }
}
