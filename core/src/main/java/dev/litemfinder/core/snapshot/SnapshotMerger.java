package dev.litemfinder.core.snapshot;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.SlotSnapshot;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Loader-neutral replacement of observed slots/tanks. Unseen pages are historical, never extra stock.
 * Adapters resolve physical identity and source replacement before passing the previous snapshot. */
public final class SnapshotMerger {
    private SnapshotMerger() { }

    public static Optional<InventorySnapshot> merge(InventorySnapshot previous, InventoryObservation observation) {
        var visible = observation.snapshot();
        boolean complete = observation.complete();
        var covered = observation.coveredSlots();
        if (previous != null && !previous.container().id().equals(visible.container().id())) throw new IllegalArgumentException("root mismatch");
        if (isOutdated(previous, visible)) return Optional.empty();
        int capacity = !complete && previous != null ? Math.max(previous.slotCount(), visible.slotCount()) : visible.slotCount();
        Map<Integer, SlotSnapshot> slots = new TreeMap<>();
        Map<String, String> metadata = new TreeMap<>();
        if (previous != null) {
            previous.slots().forEach(slot -> slots.put(slot.slot(), slot));
            metadata.putAll(previous.container().metadata());
        }
        metadata.putAll(visible.container().metadata());
        mergeFluids(metadata, previous, visible, complete);
        ItemFluidSnapshotMetadata.mergeVisible(metadata, previous, observation);
        if (complete) slots.clear(); else covered.forEach(slots::remove);
        visible.slots().forEach(slot -> slots.put(slot.slot(), slot));
        slots.keySet().removeIf(slot -> slot >= capacity);
        if (complete) {
            metadata.remove("captureCoverage");
            metadata.remove("observedSlots");
            metadata.remove("lastPartialObservedAt");
        } else {
            Set<Integer> observed = new TreeSet<>(covered);
            if (previous != null) {
                if (!previous.container().metadata().containsKey("captureCoverage")) {
                    IntStream.range(0, Math.min(previous.slotCount(), capacity)).forEach(observed::add);
                } else {
                    for (String value : previous.container().metadata().getOrDefault("observedSlots", "").split(",")) {
                        try { int index = Integer.parseInt(value); if (index >= 0 && index < capacity) observed.add(index); }
                        catch (NumberFormatException ignored) { }
                    }
                }
            }
            metadata.put("captureCoverage", "PARTIAL");
            metadata.put("observedSlots", observed.stream().map(String::valueOf).collect(Collectors.joining(",")));
            metadata.put("lastPartialObservedAt", visible.capturedAt().toString());
        }
        Instant time = !complete && previous != null ? previous.capturedAt() : visible.capturedAt();
        var root = visible.container();
        return Optional.of(new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                capacity, time, List.copyOf(slots.values())));
    }

    private static void mergeFluids(Map<String, String> metadata, InventorySnapshot previous,
                                    InventorySnapshot visible, boolean complete) {
        var newFluids = FluidSnapshotMetadata.read(visible.container().metadata());
        var oldFluids = previous == null ? Optional.<FluidSnapshotMetadata.StoredInventory>empty()
                : FluidSnapshotMetadata.read(previous.container().metadata());
        // Liquid visibility is independent of item-page visibility.
        if (!complete && !"COMPLETE".equals(visible.container().metadata().get("fluidCoverage"))
                && newFluids.isPresent() && oldFluids.isPresent()) {
            var tanks = new TreeMap<Integer, FluidSnapshotMetadata.StoredTank>();
            oldFluids.orElseThrow().tanks().forEach(tank -> tanks.put(tank.index(), tank));
            newFluids.orElseThrow().tanks().forEach(tank -> tanks.put(tank.index(), tank));
            FluidSnapshotMetadata.writeStored(metadata, new FluidSnapshotMetadata.StoredInventory(
                    List.copyOf(tanks.values()), oldFluids.orElseThrow().observedAt()));
        } else if (newFluids.isPresent()) FluidSnapshotMetadata.writeStored(metadata, newFluids.orElseThrow());
    }

    public static boolean isOutdated(InventorySnapshot previous, InventorySnapshot incoming) {
        if (previous == null) return false;
        Instant last = previous.capturedAt();
        try { last = Instant.parse(previous.container().metadata().getOrDefault("lastPartialObservedAt", last.toString())); }
        catch (RuntimeException invalid) { }
        return incoming.capturedAt().isBefore(last);
    }

    public static InventorySnapshot retainUnavailable(InventorySnapshot incoming, InventorySnapshot previous) {
        var retained = FluidSnapshotMetadata.preserveUnavailable(incoming, previous);
        retained = retainNetwork(retained, previous);
        return ItemFluidSnapshotMetadata.preserveUnavailable(retained, previous);
    }

    public static InventorySnapshot retainNetwork(InventorySnapshot incoming, InventorySnapshot previous) {
        if (previous == null || !previous.container().id().equals(incoming.container().id())) return incoming;
        var metadata = new TreeMap<>(incoming.container().metadata());
        previous.container().metadata().forEach((key, value) -> {
            if (key.equals("storageNetworkId") || key.equals("storageNetworkName") || key.equals("storageNetworkObservedAt")) metadata.putIfAbsent(key, value);
        });
        var root = incoming.container();
        return new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                incoming.slotCount(), incoming.capturedAt(), incoming.slots());
    }
}
