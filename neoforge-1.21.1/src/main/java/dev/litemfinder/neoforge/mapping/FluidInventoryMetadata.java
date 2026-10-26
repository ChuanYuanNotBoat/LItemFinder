package dev.litemfinder.neoforge.mapping;

import com.mojang.serialization.JsonOps;
import dev.litemfinder.core.snapshot.FluidSnapshotMetadata;
import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

/** Converts native fluids to Core observations. Persistence, merging and aggregation live in Core. */
public final class FluidInventoryMetadata {
    private FluidInventoryMetadata() { }
    public static void write(Map<String, String> metadata, FluidInventory fluids,
            HolderLookup.Provider registries, Instant observedAt) {
        FluidSnapshotMetadata.writeStored(metadata, map(fluids, registries, observedAt));
        metadata.put("fluidCoverage", "COMPLETE");
    }
    static FluidSnapshotMetadata.StoredInventory map(FluidInventory fluids,
            HolderLookup.Provider registries, Instant observedAt) {
        var tanks = new ArrayList<FluidSnapshotMetadata.StoredTank>();
        var variants = new ComponentVariantFingerprint();
        for (var tank : fluids.tanks()) {
            if (tank.amount() == 0) {
                tanks.add(new FluidSnapshotMetadata.StoredTank(tank.index(), "", "", "", 0, tank.capacity()));
                continue;
            }
            var encoded = FluidStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), tank.fluid()).getOrThrow();
            tanks.add(new FluidSnapshotMetadata.StoredTank(tank.index(),
                    BuiltInRegistries.FLUID.getKey(tank.fluid().getFluid()).toString(),
                    variants.fingerprintEncodedPatch(encoded), encoded.toString(), tank.amount(), tank.capacity()));
        }
        return new FluidSnapshotMetadata.StoredInventory(tanks, observedAt);
    }
}
