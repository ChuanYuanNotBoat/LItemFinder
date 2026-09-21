package dev.litemfinder.neoforge;

import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;

import java.nio.file.Path;
import java.util.Objects;

/** Opens the production repository to verify that the SQLite driver can load in the game runtime. */
final class SqliteRuntimeProbe {

    private SqliteRuntimeProbe() {
    }

    static void verify(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile must not be null");
        try (SqliteSnapshotRepository ignored = new SqliteSnapshotRepository(databaseFile)) {
            // Opening the repository exercises driver discovery, native loading, and schema creation.
        }
    }
}
