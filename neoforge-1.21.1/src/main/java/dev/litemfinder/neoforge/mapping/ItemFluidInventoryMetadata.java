package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.*;

/** Item-held tanks stay separate from the block's own tanks and retain their real item-slot paths. */
public final class ItemFluidInventoryMetadata {
    private static final String PREFIX = "itemFluids.";
    private ItemFluidInventoryMetadata() { }
    public static void capture(Map<String, String> metadata, Map<Integer, ItemStack> items,
            NestedItemContentsReader reader, HolderLookup.Provider registries, Instant time) {
        Map<Integer, FluidInventoryMetadata.StoredInventory> known = new TreeMap<>();
        Set<Integer> unavailable = new TreeSet<>();
        items.forEach((slot, stack) -> {
            var fluids = reader.readFluids(stack, registries);
            if (fluids.isEmpty()) { unavailable.add(slot); return; }
            if (fluids.orElseThrow().tanks().isEmpty()) return;
            Map<String, String> encoded = new TreeMap<>();
            FluidInventoryMetadata.write(encoded, fluids.orElseThrow(), registries, time);
            known.put(slot, FluidInventoryMetadata.read(encoded).orElseThrow());
        });
        write(metadata, known, unavailable);
    }
    public static Map<Integer, FluidInventoryMetadata.StoredInventory> read(Map<String, String> metadata) {
        Map<Integer, FluidInventoryMetadata.StoredInventory> result = new TreeMap<>();
        if (!"1".equals(metadata.get(PREFIX + "version"))) return result;
        for (String value : metadata.getOrDefault(PREFIX + "slots", "").split(",")) {
            try {
                int slot = Integer.parseInt(value);
                if (slot < 0 || slot >= 4096) continue;
                String prefix = PREFIX + slot + ".";
                Map<String, String> fluid = new TreeMap<>();
                metadata.forEach((key, entry) -> { if (key.startsWith(prefix)) fluid.put(key.substring(prefix.length()), entry); });
                FluidInventoryMetadata.read(fluid).ifPresent(inventory -> result.put(slot, inventory));
            } catch (RuntimeException ignored) { }
        }
        return result;
    }
    private static Set<Integer> unavailable(Map<String, String> metadata) {
        Set<Integer> result = new TreeSet<>();
        for (String value : metadata.getOrDefault(PREFIX + "unavailable", "").split(",")) {
            try { result.add(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
        }
        return result;
    }
    private static void write(Map<String, String> metadata, Map<Integer, FluidInventoryMetadata.StoredInventory> known,
                              Set<Integer> unavailable) {
        metadata.keySet().removeIf(key -> key.startsWith(PREFIX));
        metadata.put(PREFIX + "version", "1");
        metadata.put(PREFIX + "slots", join(known.keySet()));
        metadata.put(PREFIX + "unavailable", join(unavailable));
        known.forEach((slot, inventory) -> {
            Map<String, String> encoded = new TreeMap<>();
            FluidInventoryMetadata.writeStored(encoded, inventory);
            encoded.forEach((key, value) -> metadata.put(PREFIX + slot + "." + key, value));
        });
    }
    private static String join(Set<Integer> indices) {
        return indices.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
    public static InventorySnapshot preserveUnavailable(InventorySnapshot incoming, InventorySnapshot previous) {
        if (previous == null || !incoming.container().id().equals(previous.container().id())
                || !incoming.container().metadata().containsKey(PREFIX + "version")) return incoming;
        Map<Integer, FluidInventoryMetadata.StoredInventory> known = read(incoming.container().metadata());
        var old = read(previous.container().metadata());
        var unavailable = unavailable(incoming.container().metadata());
        for (int slot : unavailable) {
            var current = incoming.slots().stream().filter(item -> item.slot() == slot).findFirst();
            if (current.isEmpty()) continue;
            // Follow a moved UUID/variant item within the same root without guessing a different item's fluid.
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
    public static void mergeVisible(Map<String, String> target, InventorySnapshot previous,
                                    InventorySnapshot visible, Set<Integer> covered, boolean complete) {
        if (!visible.container().metadata().containsKey(PREFIX + "version")) return;
        var fixed = preserveUnavailable(visible, previous);
        Map<Integer, FluidInventoryMetadata.StoredInventory> merged = !complete && previous != null
                ? new TreeMap<>(read(previous.container().metadata())) : new TreeMap<>();
        covered.forEach(merged::remove);
        merged.putAll(read(fixed.container().metadata()));
        write(target, merged, unavailable(fixed.container().metadata()));
    }
}
