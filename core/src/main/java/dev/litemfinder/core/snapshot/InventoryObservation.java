package dev.litemfinder.core.snapshot;

import dev.litemfinder.core.model.InventorySnapshot;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** An observation includes explicit empty slots, while the snapshot contains only occupied slots.
 * An absent observation means unavailable data and must not be submitted as an empty inventory. */
public record InventoryObservation(InventorySnapshot snapshot, Set<Integer> coveredSlots, boolean complete) {
    public InventoryObservation {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Set<Integer> coverage = Set.copyOf(coveredSlots);
        if (coverage.stream().anyMatch(slot -> slot < 0 || slot >= snapshot.slotCount())
                || snapshot.slots().stream().anyMatch(slot -> !coverage.contains(slot.slot()))
                || (complete && coverage.size() != snapshot.slotCount())) {
            throw new IllegalArgumentException("invalid observation coverage");
        }
        coveredSlots = coverage;
    }
    public static InventoryObservation complete(InventorySnapshot snapshot) {
        return new InventoryObservation(snapshot, IntStream.range(0, snapshot.slotCount()).boxed().collect(Collectors.toSet()), true);
    }
}
