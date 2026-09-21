package dev.litemfinder.neoforge.capture;

/** Lightweight same-session menu state used only for change detection. */
public record MenuFingerprint(long value, int slotCount, int occupiedSlots) {

    public MenuFingerprint {
        if (slotCount < 0) {
            throw new IllegalArgumentException("slotCount must not be negative");
        }
        if (occupiedSlots < 0 || occupiedSlots > slotCount) {
            throw new IllegalArgumentException("occupiedSlots must be within slotCount");
        }
    }
}
