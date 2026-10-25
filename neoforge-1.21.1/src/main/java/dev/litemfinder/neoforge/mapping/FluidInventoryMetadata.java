package dev.litemfinder.neoforge.mapping;

import com.mojang.serialization.JsonOps;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Independent typed fluid records carried through the existing metadata persistence boundary. */
public final class FluidInventoryMetadata {
    private static final String PREFIX = "fluids.";
    private FluidInventoryMetadata() { }

    public static void write(Map<String, String> metadata, FluidInventory fluids,
            HolderLookup.Provider registries, Instant observedAt) {
        metadata.keySet().removeIf(key -> key.startsWith(PREFIX));
        metadata.put(PREFIX + "version", "1");
        metadata.put(PREFIX + "count", Integer.toString(fluids.tanks().size()));
        metadata.put(PREFIX + "observedAt", observedAt.toString());
        metadata.put("fluidCoverage", "COMPLETE");
        var variants = new ComponentVariantFingerprint();
        for (int row = 0; row < fluids.tanks().size(); row++) {
            var tank = fluids.tanks().get(row);
            String key = PREFIX + row + ".";
            metadata.put(key + "index", Integer.toString(tank.index()));
            metadata.put(key + "amount", Long.toString(tank.amount()));
            metadata.put(key + "capacity", Long.toString(tank.capacity()));
            if (tank.amount() == 0) continue;
            var encoded = FluidStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), tank.fluid())
                    .getOrThrow();
            metadata.put(key + "id", BuiltInRegistries.FLUID.getKey(tank.fluid().getFluid()).toString());
            metadata.put(key + "variant", variants.fingerprintEncodedPatch(encoded));
            metadata.put(key + "data", encoded.toString());
        }
    }

    public static Optional<StoredInventory> read(Map<String, String> metadata) {
        if (!"1".equals(metadata.get(PREFIX + "version"))) return Optional.empty();
        try {
            int size = Integer.parseInt(metadata.get(PREFIX + "count"));
            if (size < 0 || size > FluidInventory.MAX_TANKS) return Optional.empty();
            Instant observedAt = Instant.parse(metadata.get(PREFIX + "observedAt"));
            List<StoredTank> tanks = new ArrayList<>();
            var indices = new java.util.HashSet<Integer>();
            for (int row = 0; row < size; row++) {
                String key = PREFIX + row + ".";
                var tank = new StoredTank(Integer.parseInt(metadata.get(key + "index")),
                        metadata.getOrDefault(key + "id", ""), metadata.getOrDefault(key + "variant", ""),
                        metadata.getOrDefault(key + "data", ""), Long.parseLong(metadata.get(key + "amount")),
                        Long.parseLong(metadata.get(key + "capacity")));
                if (!indices.add(tank.index())) return Optional.empty();
                tanks.add(tank);
            }
            return Optional.of(new StoredInventory(List.copyOf(tanks), observedAt));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
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

    /** Item-only observations cannot erase a last known fluid reading or promote its timestamp. */
    public static InventorySnapshot preserveUnavailable(InventorySnapshot incoming, InventorySnapshot previous) {
        if (incoming.container().metadata().containsKey(PREFIX + "version") || previous == null
                || !incoming.container().id().equals(previous.container().id())) return incoming;
        String before = previous.container().metadata().get("minecraftBlock");
        String after = incoming.container().metadata().get("minecraftBlock");
        if (before != null && after != null && !before.equals(after)) return incoming;
        Map<String, String> metadata = new TreeMap<>(incoming.container().metadata());
        previous.container().metadata().forEach((key, value) -> { if (key.startsWith(PREFIX)) metadata.put(key, value); });
        var root = incoming.container();
        return new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                incoming.slotCount(), incoming.capturedAt(), incoming.slots());
    }

    public record StoredInventory(List<StoredTank> tanks, Instant observedAt) { }
    public record StoredTank(int index, String fluidId, String variant, String encoded, long amount, long capacity) {
        public StoredTank {
            if (index < 0 || index >= FluidInventory.MAX_TANKS || amount < 0 || capacity < 0
                    || (amount > 0 && (fluidId.isBlank() || encoded.isBlank()))) {
                throw new IllegalArgumentException("invalid stored fluid");
            }
        }
    }
}
