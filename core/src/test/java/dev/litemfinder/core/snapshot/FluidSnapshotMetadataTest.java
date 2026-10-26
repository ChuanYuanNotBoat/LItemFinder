package dev.litemfinder.core.snapshot;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidSnapshotMetadataTest {
    @Test void versionOneRoundTripPreservesOpaqueTemplatesLargeAmountsAndUnknownCapacity() {
        var original = new FluidSnapshotMetadata.StoredInventory(List.of(new FluidSnapshotMetadata.StoredTank(
                0, "example:solution", "components-digest", "adapter-specific, not necessarily JSON", 3_000_000_000L, 0)), Instant.EPOCH);
        var metadata = new TreeMap<String, String>(Map.of("other", "retained"));
        FluidSnapshotMetadata.writeStored(metadata, original);
        assertEquals("1", metadata.get("fluids.version"));
        assertEquals(original, FluidSnapshotMetadata.read(metadata).orElseThrow());
        assertEquals("retained", metadata.get("other"));
        assertThrows(UnsupportedOperationException.class, () -> original.tanks().clear());
    }
    @Test void corruptOrUnsupportedRecordsAreUnavailableRatherThanAnEmptyTank() {
        var metadata = new TreeMap<String, String>();
        assertTrue(FluidSnapshotMetadata.read(metadata).isEmpty());
        FluidSnapshotMetadata.writeStored(metadata, new FluidSnapshotMetadata.StoredInventory(List.of(), Instant.EPOCH));
        assertTrue(FluidSnapshotMetadata.read(metadata).orElseThrow().tanks().isEmpty());
        metadata.put("fluids.version", "2"); assertTrue(FluidSnapshotMetadata.read(metadata).isEmpty());
        metadata.put("fluids.version", "1"); metadata.put("fluids.count", "257"); assertTrue(FluidSnapshotMetadata.read(metadata).isEmpty());
        metadata.put("fluids.count", "0"); metadata.put("fluids.observedAt", "invalid"); assertTrue(FluidSnapshotMetadata.read(metadata).isEmpty());
    }
    @Test void duplicateTanksAndInvalidFluidIdentityAreRejectedBeforePersistence() {
        var tank = new FluidSnapshotMetadata.StoredTank(0, "example:water", "", "opaque", 1, 1000);
        assertThrows(IllegalArgumentException.class, () -> new FluidSnapshotMetadata.StoredInventory(List.of(tank, tank), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new FluidSnapshotMetadata.StoredTank(0, "invalid fluid id", "", "opaque", 1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new FluidSnapshotMetadata.StoredTank(-1, "", "", "", 0, 1000));
    }
}
