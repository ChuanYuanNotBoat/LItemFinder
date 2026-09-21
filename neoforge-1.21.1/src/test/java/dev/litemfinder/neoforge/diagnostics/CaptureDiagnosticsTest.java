package dev.litemfinder.neoforge.diagnostics;

import dev.litemfinder.neoforge.capture.CaptureReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptureDiagnosticsTest {

    @Test
    void snapshotsCaptureSkipAndDegradedCounters() {
        CaptureDiagnostics diagnostics = new CaptureDiagnostics();
        diagnostics.captured(CaptureReason.OPEN_STABLE);
        diagnostics.captured(CaptureReason.CONTENT_STABLE);
        diagnostics.skipped("unsupported_menu:HopperMenu");
        diagnostics.skipped("unsupported_menu:HopperMenu");
        diagnostics.degraded("session_only_identity:chest_like");
        diagnostics.removed("block_missing_or_replaced");

        CaptureDiagnostics.Snapshot snapshot = diagnostics.snapshot();

        assertEquals(2, snapshot.totalCaptures());
        assertEquals(2, snapshot.totalSkipped());
        assertEquals(1, snapshot.totalDegraded());
        assertEquals(1, snapshot.totalRemoved());
        assertEquals(2, snapshot.skipped().get("unsupported_menu:HopperMenu"));

        diagnostics.clear();
        assertEquals(0, diagnostics.snapshot().totalCaptures());
        assertEquals(2, snapshot.totalCaptures());
    }

    @Test
    void rejectsBlankReasons() {
        CaptureDiagnostics diagnostics = new CaptureDiagnostics();

        assertThrows(IllegalArgumentException.class, () -> diagnostics.skipped(" "));
        assertThrows(IllegalArgumentException.class, () -> diagnostics.degraded(""));
    }
}
