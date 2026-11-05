package dev.litemfinder.core.snapshot;

import dev.litemfinder.core.model.*;
import java.util.*;
import java.util.stream.Collectors;

/** An explicitly unavailable nested read retains the exact same carrier's last observed children. */
public final class NestedInventoryObservation {
    public static final String UNAVAILABLE = "nestedUnavailableSlots";
    public static final String IDENTITY_PREFIX = "nestedIdentity.";
    public static final String RETAINED = "nestedReadUnavailable";
    private NestedInventoryObservation() { }
    public static InventorySnapshot retainUnavailable(InventorySnapshot incoming, InventorySnapshot previous) {
        if (previous == null || !previous.container().id().equals(incoming.container().id())) return incoming;
        Set<Integer> unavailable = new HashSet<>();
        for (String value : incoming.container().metadata().getOrDefault(UNAVAILABLE, "").split(",")) {
            try { unavailable.add(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
        }
        var old = previous.slots().stream().collect(Collectors.toMap(SlotSnapshot::slot, slot -> slot));
        var slots = incoming.slots().stream().map(slot -> {
            var before = old.get(slot.slot());
            String identity = incoming.container().metadata().get(IDENTITY_PREFIX + slot.slot());
            boolean sameIdentity = identity != null && !identity.isBlank()
                    && identity.equals(previous.container().metadata().get(IDENTITY_PREFIX + slot.slot()))
                    && before != null && before.stack().item().itemId().equals(slot.stack().item().itemId())
                    && before.stack().count() == slot.stack().count();
            if (before == null || !before.stack().equals(slot.stack()) && !sameIdentity) return slot;
            if (slot.nestedContainer().isPresent() && before.nestedContainer().isPresent()) {
                return SlotSnapshot.nested(slot.slot(), slot.stack(), retainUnavailable(slot.nestedContainer().orElseThrow(), before.nestedContainer().orElseThrow()));
            }
            return unavailable.contains(slot.slot()) && slot.nestedContainer().isEmpty() && before.nestedContainer().isPresent()
                    ? SlotSnapshot.nested(slot.slot(), slot.stack(), markRetained(before.nestedContainer().orElseThrow())) : slot;
        }).toList();
        return new InventorySnapshot(incoming.container(), incoming.slotCount(), incoming.capturedAt(), slots);
    }
    private static InventorySnapshot markRetained(InventorySnapshot snapshot) {
        var metadata = new TreeMap<>(snapshot.container().metadata()); metadata.put(RETAINED, "true");
        var root = snapshot.container();
        return new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                snapshot.slotCount(), snapshot.capturedAt(), snapshot.slots());
    }
}
