package dev.litemfinder.core.snapshot;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.NamespacedId;
import java.time.Instant;
import java.util.*;

/** Portable fluid observations in mB. Encoded display templates and variant identifiers are opaque to Core.
 * The version-1 metadata wire format remains readable by existing SQLite snapshots. */
public final class FluidSnapshotMetadata {
    public static final int MAX_TANKS = 256;
    private static final String PREFIX = "fluids.";
    private FluidSnapshotMetadata() { }

    public static Optional<StoredInventory> read(Map<String, String> metadata) {
        if (!"1".equals(metadata.get(PREFIX + "version"))) return Optional.empty();
        try {
            int size = Integer.parseInt(metadata.get(PREFIX + "count"));
            if (size < 0 || size > MAX_TANKS) return Optional.empty();
            Instant observedAt = Instant.parse(metadata.get(PREFIX + "observedAt"));
            List<StoredTank> tanks = new ArrayList<>();
            for (int row = 0; row < size; row++) {
                String key = PREFIX + row + ".";
                tanks.add(new StoredTank(Integer.parseInt(metadata.get(key + "index")),
                        metadata.getOrDefault(key + "id", ""), metadata.getOrDefault(key + "variant", ""),
                        metadata.getOrDefault(key + "data", ""), Long.parseLong(metadata.get(key + "amount")),
                        Long.parseLong(metadata.get(key + "capacity"))));
            }
            return Optional.of(new StoredInventory(tanks, observedAt));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    public static void writeStored(Map<String, String> metadata, StoredInventory inventory) {
        metadata.keySet().removeIf(key -> key.startsWith(PREFIX));
        metadata.put(PREFIX + "version", "1");
        metadata.put(PREFIX + "count", Integer.toString(inventory.tanks().size()));
        metadata.put(PREFIX + "observedAt", inventory.observedAt().toString());
        for (int row = 0; row < inventory.tanks().size(); row++) {
            var tank = inventory.tanks().get(row);
            String key = PREFIX + row + ".";
            metadata.put(key + "index", Integer.toString(tank.index()));
            metadata.put(key + "id", tank.fluidId());
            metadata.put(key + "variant", tank.variant());
            metadata.put(key + "data", tank.encoded());
            metadata.put(key + "amount", Long.toString(tank.amount()));
            metadata.put(key + "capacity", Long.toString(tank.capacity()));
        }
    }

    /** Unavailable fluid data retains its original observation time. The adapter checks source replacement first. */
    public static InventorySnapshot preserveUnavailable(InventorySnapshot incoming, InventorySnapshot previous) {
        if (incoming.container().metadata().containsKey(PREFIX + "version") || previous == null
                || !incoming.container().id().equals(previous.container().id())) return incoming;
        Map<String, String> metadata = new TreeMap<>(incoming.container().metadata());
        previous.container().metadata().forEach((key, value) -> { if (key.startsWith(PREFIX)) metadata.put(key, value); });
        var root = incoming.container();
        return new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                incoming.slotCount(), incoming.capturedAt(), incoming.slots());
    }

    public record StoredInventory(List<StoredTank> tanks, Instant observedAt) {
        public StoredInventory {
            tanks = List.copyOf(tanks);
            Objects.requireNonNull(observedAt);
            var indices = new HashSet<Integer>();
            if (tanks.size() > MAX_TANKS || tanks.stream().anyMatch(tank -> !indices.add(tank.index()))) {
                throw new IllegalArgumentException("invalid tank indices");
            }
        }
    }
    /** Capacity zero denotes unknown capacity, and does not imply that a filled tank is invalid. */
    public record StoredTank(int index, String fluidId, String variant, String encoded, long amount, long capacity) {
        public StoredTank {
            Objects.requireNonNull(fluidId); Objects.requireNonNull(variant); Objects.requireNonNull(encoded);
            if (index < 0 || index >= MAX_TANKS || amount < 0 || capacity < 0
                    || (amount > 0 && (fluidId.isBlank() || encoded.isBlank()))) {
                throw new IllegalArgumentException("invalid stored fluid");
            }
            if (amount > 0) NamespacedId.parse(fluidId);
        }
    }
}
