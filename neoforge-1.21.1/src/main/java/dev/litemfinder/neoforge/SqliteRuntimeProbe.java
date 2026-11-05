package dev.litemfinder.neoforge;

import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.SlotSnapshot;

import java.nio.file.Path;
import java.util.Objects;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exercises the packaged driver and persistence lifecycle in a separate development-only probe database. */
final class SqliteRuntimeProbe {

    private SqliteRuntimeProbe() {
    }

    static void verify(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile must not be null");
        ContainerId id = new ContainerId("runtime-probe:" + UUID.randomUUID());
        var container = new ContainerRecord(id, ContainerType.CHEST, new LogicalLocation("runtime-probe", id.value()));
        var snapshot = new InventorySnapshot(container, 1, Instant.now(), List.of(
                new SlotSnapshot(0, new ItemStackInfo(ItemKey.parse("minecraft:diamond"), 7))));
        try (SqliteSnapshotRepository repository = new SqliteSnapshotRepository(databaseFile)) {
            repository.save(snapshot);
        }
        try (SqliteSnapshotRepository repository = new SqliteSnapshotRepository(databaseFile)) {
            if (!repository.find(id).filter(snapshot::equals).isPresent()) {
                throw new IllegalStateException("SQLite runtime probe could not restore its snapshot after reopening");
            }
            if (!repository.delete(id)) {
                throw new IllegalStateException("SQLite runtime probe could not remove its own snapshot");
            }
        }
    }
}
