package dev.litemfinder.neoforge.capture;

/** One trusted root-container slot and its index in the live menu. */
public record MenuSlotRef(int menuSlot, int containerSlot) {

    public MenuSlotRef {
        if (menuSlot < 0) {
            throw new IllegalArgumentException("menuSlot must not be negative");
        }
        if (containerSlot < 0) {
            throw new IllegalArgumentException("containerSlot must not be negative");
        }
    }
}
