package dev.litemfinder.storage.sqlite;

import dev.litemfinder.core.model.*;
import dev.litemfinder.core.view.*;
import dev.litemfinder.core.snapshot.FluidSnapshotMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidSnapshotCompatibilityTest {
    @Test void versionOneMetadataIsReadableAfterCoreExtractionWithoutMigration(@TempDir Path directory) {
        // Literal pre-extraction fields, including an empty/unavailable list and opaque native templates.
        var metadata = new TreeMap<String, String>(Map.ofEntries(
                Map.entry("fluids.version", "1"), Map.entry("fluids.count", "1"), Map.entry("fluids.observedAt", Instant.EPOCH.toString()),
                Map.entry("fluids.0.index", "0"), Map.entry("fluids.0.id", "example:water"), Map.entry("fluids.0.variant", ""),
                Map.entry("fluids.0.data", "opaque-block-template"), Map.entry("fluids.0.amount", "2000"), Map.entry("fluids.0.capacity", "10000"),
                Map.entry("itemFluids.version", "1"), Map.entry("itemFluids.slots", "0"), Map.entry("itemFluids.unavailable", ""),
                Map.entry("itemFluids.0.fluids.version", "1"), Map.entry("itemFluids.0.fluids.count", "1"),
                Map.entry("itemFluids.0.fluids.observedAt", Instant.EPOCH.toString()),
                Map.entry("itemFluids.0.fluids.0.index", "0"), Map.entry("itemFluids.0.fluids.0.id", "example:water"),
                Map.entry("itemFluids.0.fluids.0.variant", ""), Map.entry("itemFluids.0.fluids.0.data", "opaque-item-template"),
                Map.entry("itemFluids.0.fluids.0.amount", "1000"), Map.entry("itemFluids.0.fluids.0.capacity", "0")));
        var container = new ContainerRecord(new ContainerId("existing-root"), ContainerType.CHEST,
                new WorldLocation("scope", NamespacedId.parse("example:dimension"), 1, 64, 2), metadata);
        var original = new InventorySnapshot(container, 3, Instant.EPOCH, List.of(new SlotSnapshot(0,
                new ItemStackInfo(ItemKey.parse("example:upgrade"), 1))));
        Path database = directory.resolve("existing.db");
        try (var repository = new SqliteSnapshotRepository(database)) { repository.save(original); }
        try (var repository = new SqliteSnapshotRepository(database)) {
            var restored = repository.find(container.id()).orElseThrow();
            assertEquals(original, restored);
            assertEquals(2000, FluidSnapshotMetadata.read(restored.container().metadata()).orElseThrow().tanks().getFirst().amount());
            var row = FluidOverview.from(ContainerOverview.from(repository.findAll())).rows().getFirst();
            assertEquals(3000, row.amount()); assertEquals(0, row.capacity());
            assertEquals(List.of(container.location()), RecordedSourceLocations.resolve(ContainerOverview.from(repository.findAll()), List.of(container.id())));
        }
    }
}
