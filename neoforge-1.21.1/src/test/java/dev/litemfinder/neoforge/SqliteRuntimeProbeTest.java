package dev.litemfinder.neoforge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRuntimeProbeTest {

    @Test
    void createsAndOpensRepositoryWithRuntimeDriver(@TempDir Path directory) {
        Path database = directory.resolve("probe.db");

        assertDoesNotThrow(() -> SqliteRuntimeProbe.verify(database));

        assertTrue(database.toFile().isFile());
    }
}
