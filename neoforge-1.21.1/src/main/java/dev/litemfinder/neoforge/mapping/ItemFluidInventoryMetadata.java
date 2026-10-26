package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.snapshot.FluidSnapshotMetadata;
import dev.litemfinder.core.snapshot.ItemFluidSnapshotMetadata;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import java.time.Instant;
import java.util.*;

/** Reads native item fluids and emits portable Core metadata. */
public final class ItemFluidInventoryMetadata {
    private ItemFluidInventoryMetadata() { }
    public static void capture(Map<String, String> metadata, Map<Integer, ItemStack> items,
            NestedItemContentsReader reader, HolderLookup.Provider registries, Instant time) {
        Map<Integer, FluidSnapshotMetadata.StoredInventory> known = new TreeMap<>();
        Set<Integer> unavailable = new TreeSet<>();
        items.forEach((slot, stack) -> {
            var fluids = reader.readFluids(stack, registries);
            if (fluids.isEmpty()) { unavailable.add(slot); return; }
            if (fluids.orElseThrow().tanks().isEmpty()) return;
            known.put(slot, FluidInventoryMetadata.map(fluids.orElseThrow(), registries, time));
        });
        ItemFluidSnapshotMetadata.write(metadata, known, unavailable);
    }
}
