package dev.litemfinder.core.snapshot;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import java.util.*;

/** Portable slot-to-fluid observations. Reading native item capabilities is an adapter responsibility. */
public final class ItemFluidSnapshotMetadata {
    public static final int MAX_ITEM_SLOTS = 4096;
    private static final String PREFIX = "itemFluids.";
    private ItemFluidSnapshotMetadata() { }

    public static Map<Integer, FluidSnapshotMetadata.StoredInventory> read(Map<String, String> metadata) {
        Map<Integer, FluidSnapshotMetadata.StoredInventory> result = new TreeMap<>();
        if (!"1".equals(metadata.get(PREFIX + "version"))) return result;
        for (String value : metadata.getOrDefault(PREFIX + "slots", "").split(",")) {
            try {
                int slot = Integer.parseInt(value);
                if (slot < 0 || slot >= MAX_ITEM_SLOTS) continue;
                String prefix = PREFIX + slot + ".";
                Map<String, String> fluid = new TreeMap<>();
                metadata.forEach((key, entry) -> { if (key.startsWith(prefix)) fluid.put(key.substring(prefix.length()), entry); });
                FluidSnapshotMetadata.read(fluid).ifPresent(inventory -> result.put(slot, inventory));
            } catch (RuntimeException ignored) { }
        }
        return result;
    }
    private static Set<Integer> unavailable(Map<String, String> metadata) {
        Set<Integer> result = new TreeSet<>();
        for (String value : metadata.getOrDefault(PREFIX + "unavailable", "").split(",")) {
            try {
                int slot = Integer.parseInt(value);
                if (slot >= 0 && slot < MAX_ITEM_SLOTS) result.add(slot);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }
    public static void write(Map<String, String> metadata, Map<Integer, FluidSnapshotMetadata.StoredInventory> known,
                             Set<Integer> unavailable) {
        if (java.util.stream.Stream.concat(known.keySet().stream(), unavailable.stream())
                .anyMatch(slot -> slot < 0 || slot >= MAX_ITEM_SLOTS)) throw new IllegalArgumentException("invalid item fluid slot");
        metadata.keySet().removeIf(key -> key.startsWith(PREFIX));
        metadata.put(PREFIX + "version", "1");
        metadata.put(PREFIX + "slots", join(known.keySet()));
        metadata.put(PREFIX + "unavailable", join(unavailable));
        known.forEach((slot, inventory) -> {
            Map<String, String> encoded = new TreeMap<>();
            FluidSnapshotMetadata.writeStored(encoded, inventory);
            encoded.forEach((key, value) -> metadata.put(PREFIX + slot + "." + key, value));
        });
    }
    private static String join(Set<Integer> indices) {
        return indices.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
    public static InventorySnapshot preserveUnavailable(InventorySnapshot incoming, InventorySnapshot previous) {
        if (previous == null || !incoming.container().id().equals(previous.container().id())
                || !incoming.container().metadata().containsKey(PREFIX + "version")) return incoming;
        var known = read(incoming.container().metadata());
        var old = read(previous.container().metadata());
        var unavailable = unavailable(incoming.container().metadata());
        for (int slot : unavailable) {
            var current = incoming.slots().stream().filter(item -> item.slot() == slot).findFirst();
            if (current.isEmpty()) continue;
            // Only an unambiguous exact item identity can inherit an unavailable reading after a move.
            var candidates = previous.slots().stream().filter(item -> item.stack().item().equals(current.orElseThrow().stack().item())
                    && old.containsKey(item.slot())).toList();
            if (candidates.size() == 1) known.put(slot, old.get(candidates.getFirst().slot()));
        }
        Map<String, String> metadata = new TreeMap<>(incoming.container().metadata());
        write(metadata, known, unavailable);
        var root = incoming.container();
        return new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                incoming.slotCount(), incoming.capturedAt(), incoming.slots());
    }
    static void mergeVisible(Map<String, String> target, InventorySnapshot previous,
                             InventoryObservation observation) {
        var visible = observation.snapshot();
        if (!visible.container().metadata().containsKey(PREFIX + "version")) return;
        var fixed = preserveUnavailable(visible, previous);
        Map<Integer, FluidSnapshotMetadata.StoredInventory> merged = !observation.complete() && previous != null
                ? new TreeMap<>(read(previous.container().metadata())) : new TreeMap<>();
        observation.coveredSlots().forEach(merged::remove);
        merged.putAll(read(fixed.container().metadata()));
        write(target, merged, unavailable(fixed.container().metadata()));
    }
}
