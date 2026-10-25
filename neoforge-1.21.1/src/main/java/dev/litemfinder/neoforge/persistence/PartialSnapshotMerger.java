package dev.litemfinder.neoforge.persistence;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.neoforge.mapping.FluidInventoryMetadata;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Visible-range replacement, never addition of another terminal copy of the same physical inventory. */
public final class PartialSnapshotMerger {
    private PartialSnapshotMerger() { }
    public static Optional<InventorySnapshot> merge(InventorySnapshot previous, InventorySnapshot visible,
                                                   Set<Integer> covered, boolean complete) {
        if (covered.stream().anyMatch(slot -> slot < 0 || slot >= visible.slotCount())
                || visible.slots().stream().anyMatch(slot -> !covered.contains(slot.slot()))
                || (complete && covered.size() != visible.slotCount())) throw new IllegalArgumentException("invalid coverage");
        if (previous != null && !previous.container().id().equals(visible.container().id())) throw new IllegalArgumentException("root mismatch");
        if (previous != null && replacedBlock(previous, visible)) previous = null;
        int capacity = !complete && previous != null ? Math.max(previous.slotCount(), visible.slotCount()) : visible.slotCount();
        if (previous != null) {
            if (!previous.container().id().equals(visible.container().id())) throw new IllegalArgumentException("root mismatch");
            Instant last = previous.capturedAt();
            try { last = Instant.parse(previous.container().metadata().getOrDefault("lastPartialObservedAt", last.toString())); }
            catch (RuntimeException ignored) { }
            if (visible.capturedAt().isBefore(last)) return Optional.empty();
        }
        MapBuilder builder = new MapBuilder(previous, visible, complete);
        dev.litemfinder.neoforge.mapping.ItemFluidInventoryMetadata.mergeVisible(builder.metadata, previous, visible, covered, complete);
        if (complete) {
            builder.slots.clear();
        } else {
            covered.forEach(builder.slots::remove);
        }
        visible.slots().forEach(slot -> builder.slots.put(slot.slot(), slot));
        builder.slots.keySet().removeIf(slot -> slot >= capacity);
        if (complete) {
            builder.metadata.remove("captureCoverage");
            builder.metadata.remove("observedSlots");
            builder.metadata.remove("lastPartialObservedAt");
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
            builder.metadata.put("captureCoverage", "PARTIAL");
            builder.metadata.put("observedSlots", observed.stream().map(String::valueOf).collect(Collectors.joining(",")));
            builder.metadata.put("lastPartialObservedAt", visible.capturedAt().toString());
        }
        var root = visible.container();
        var positions = dev.litemfinder.neoforge.mapping.InventoryLayoutMetadata.read(builder.metadata, capacity);
        if (positions.isPresent() && !positions.orElseThrow().positions().stream()
                .map(dev.litemfinder.neoforge.api.inventory.InventoryLayout.Position::slot).collect(Collectors.toSet())
                .containsAll(builder.slots.keySet())) {
            builder.metadata.keySet().removeIf(key -> key.startsWith("slotPosition.") || key.startsWith("slotLayout"));
        }
        // Retained invisible items keep the older time; no claim that an unseen page has been refreshed.
        Instant time = !complete && previous != null ? previous.capturedAt() : visible.capturedAt();
        return Optional.of(new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), builder.metadata),
                capacity, time, java.util.List.copyOf(builder.slots.values())));
    }

    private static class MapBuilder {
        final TreeMap<Integer, SlotSnapshot> slots = new TreeMap<>();
        final TreeMap<String, String> metadata = new TreeMap<>();
        MapBuilder(InventorySnapshot previous, InventorySnapshot visible, boolean complete) {
            if (previous != null) {
                previous.slots().forEach(slot -> slots.put(slot.slot(), slot));
                metadata.putAll(previous.container().metadata());
            }
            metadata.putAll(visible.container().metadata());
            // Fluid visibility is a separate dimension. Preserve tanks outside an explicitly read window.
            var newFluids = FluidInventoryMetadata.read(visible.container().metadata());
            var oldFluids = previous == null ? Optional.<FluidInventoryMetadata.StoredInventory>empty()
                    : FluidInventoryMetadata.read(previous.container().metadata());
            if (!complete && !"COMPLETE".equals(visible.container().metadata().get("fluidCoverage"))
                    && newFluids.isPresent() && oldFluids.isPresent()) {
                var tanks = new TreeMap<Integer, FluidInventoryMetadata.StoredTank>();
                oldFluids.orElseThrow().tanks().forEach(tank -> tanks.put(tank.index(), tank));
                newFluids.orElseThrow().tanks().forEach(tank -> tanks.put(tank.index(), tank));
                FluidInventoryMetadata.writeStored(metadata, new FluidInventoryMetadata.StoredInventory(
                        java.util.List.copyOf(tanks.values()), oldFluids.orElseThrow().observedAt()));
            } else if (newFluids.isPresent()) {
                FluidInventoryMetadata.writeStored(metadata, newFluids.orElseThrow());
            }
        }
    }

    public static InventorySnapshot retainNetwork(InventorySnapshot incoming, InventorySnapshot previous) {
        if (previous == null || !previous.container().id().equals(incoming.container().id()) || replacedBlock(previous, incoming)) return incoming;
        var metadata = new TreeMap<>(incoming.container().metadata());
        previous.container().metadata().forEach((key, value) -> {
            if (key.equals("storageNetworkId") || key.equals("storageNetworkName") || key.equals("storageNetworkObservedAt")) metadata.putIfAbsent(key, value);
        });
        var root = incoming.container();
        return new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                incoming.slotCount(), incoming.capturedAt(), incoming.slots());
    }

    private static boolean replacedBlock(InventorySnapshot previous, InventorySnapshot incoming) {
        String before = previous.container().metadata().get("minecraftBlock");
        String after = incoming.container().metadata().get("minecraftBlock");
        return before != null && after != null && !before.equals(after);
    }
}
