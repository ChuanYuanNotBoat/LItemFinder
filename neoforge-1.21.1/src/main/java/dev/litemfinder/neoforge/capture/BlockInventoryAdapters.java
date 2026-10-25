package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.BlockInventory;
import dev.litemfinder.neoforge.api.inventory.BlockInventoryAdapter;
import dev.litemfinder.neoforge.compat.FunctionalStorageInventoryReader;
import dev.litemfinder.neoforge.compat.TerraFirmaCraftDisplayInventoryReader;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Explicit readers only: an arbitrary client capability can contain unsynchronized defaults. */
public final class BlockInventoryAdapters {
    private static final CopyOnWriteArrayList<BlockInventoryAdapter> ADAPTERS =
            new CopyOnWriteArrayList<>(java.util.List.of(new FunctionalStorageInventoryReader(),
                    new TerraFirmaCraftDisplayInventoryReader()));
    static { ADAPTERS.add(new dev.litemfinder.neoforge.compat.TerraFirmaCraftIngotPileReader()); }

    private BlockInventoryAdapters() { }

    public static AutoCloseable register(BlockInventoryAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter must not be null");
        ADAPTERS.add(0, adapter);
        return () -> ADAPTERS.remove(adapter);
    }

    public static Optional<BlockInventory> read(BlockEntity owner) {
        for (var adapter : ADAPTERS) {
            try {
                var inventory = adapter.read(owner);
                if (inventory.isPresent()) return inventory;
            } catch (RuntimeException | LinkageError ignored) {
                // Keep the last observation if an optional integration temporarily cannot read.
            }
        }
        return Optional.empty();
    }
}
