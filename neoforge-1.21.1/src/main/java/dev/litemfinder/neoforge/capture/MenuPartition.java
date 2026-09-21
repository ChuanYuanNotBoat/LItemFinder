package dev.litemfinder.neoforge.capture;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Result of deciding which live menu slots belong to the root container. */
public record MenuPartition(boolean supported, String kind, List<MenuSlotRef> slots) {

    public MenuPartition {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(slots, "slots must not be null");
        if (kind.isBlank()) {
            throw new IllegalArgumentException("kind must not be blank");
        }
        slots = List.copyOf(slots);
        if (!supported && !slots.isEmpty()) {
            throw new IllegalArgumentException("unsupported partitions must not contain slots");
        }

        Set<Integer> menuSlots = new HashSet<>();
        Set<Integer> containerSlots = new HashSet<>();
        for (MenuSlotRef slot : slots) {
            Objects.requireNonNull(slot, "slots must not contain null");
            if (!menuSlots.add(slot.menuSlot())) {
                throw new IllegalArgumentException("duplicate menu slot: " + slot.menuSlot());
            }
            if (!containerSlots.add(slot.containerSlot())) {
                throw new IllegalArgumentException("duplicate container slot: " + slot.containerSlot());
            }
        }
    }

    public static MenuPartition supported(String kind, List<MenuSlotRef> slots) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("supported partitions must contain at least one slot");
        }
        return new MenuPartition(true, kind, slots);
    }

    public static MenuPartition unsupported(String reason) {
        return new MenuPartition(false, reason, List.of());
    }
}
