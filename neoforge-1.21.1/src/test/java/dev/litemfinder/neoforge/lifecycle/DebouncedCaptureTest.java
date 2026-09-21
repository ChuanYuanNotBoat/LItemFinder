package dev.litemfinder.neoforge.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebouncedCaptureTest {

    @Test
    void capturesInitialValueAfterOpenDelay() {
        DebouncedCapture<String> capture = new DebouncedCapture<>(1, 5);
        capture.opened("initial");

        assertEquals("initial", capture.tick("initial").orElseThrow());
        assertTrue(capture.tick("initial").isEmpty());
    }

    @Test
    void waitsForChangedValueToRemainStable() {
        DebouncedCapture<String> capture = new DebouncedCapture<>(1, 5);
        capture.opened("a");
        capture.tick("a");

        for (int tick = 1; tick < 5; tick++) {
            assertTrue(capture.tick("b").isEmpty());
        }
        assertEquals("b", capture.tick("b").orElseThrow());
    }

    @Test
    void finalCloseBypassesDebounceButSkipsUnchangedValue() {
        DebouncedCapture<String> changed = new DebouncedCapture<>(1, 5);
        changed.opened("a");
        changed.tick("a");
        assertEquals("b", changed.closed("b").orElseThrow());
        assertTrue(!changed.isOpen());

        DebouncedCapture<String> unchanged = new DebouncedCapture<>(1, 5);
        unchanged.opened("a");
        unchanged.tick("a");
        assertTrue(unchanged.closed("a").isEmpty());
    }

    @Test
    void rejectsTicksOutsideAnOpenSession() {
        DebouncedCapture<String> capture = new DebouncedCapture<>(1, 5);
        assertThrows(IllegalStateException.class, () -> capture.tick("a"));
    }
}
