package dev.litemfinder.core.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable state of the occupied slots in a container at one point in time. */
public record InventorySnapshot(
        ContainerRecord container,
        int slotCount,
        Instant capturedAt,
        List<SlotSnapshot> slots
) {

    public InventorySnapshot {
        Objects.requireNonNull(container, "container must not be null");
        if (slotCount < 0) {
            throw new IllegalArgumentException("slotCount must not be negative");
        }
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        Objects.requireNonNull(slots, "slots must not be null");

        Set<Integer> occupied = new HashSet<>();
        for (SlotSnapshot slot : slots) {
            Objects.requireNonNull(slot, "slots must not contain null");
            if (slot.slot() >= slotCount) {
                throw new IllegalArgumentException(
                        "slot " + slot.slot() + " is outside container capacity " + slotCount
                );
            }
            if (!occupied.add(slot.slot())) {
                throw new IllegalArgumentException("duplicate occupied slot: " + slot.slot());
            }
        }

        slots = slots.stream()
                .sorted(Comparator.comparingInt(SlotSnapshot::slot))
                .toList();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }
}
