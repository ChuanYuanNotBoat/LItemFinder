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
    private static final TypedInventoryAdapterRegistry<BlockEntity, BlockInventory> TYPED = new TypedInventoryAdapterRegistry<>();
    private static final CopyOnWriteArrayList<BlockInventoryAdapter> ADAPTERS =
            new CopyOnWriteArrayList<>(java.util.List.of(new FunctionalStorageInventoryReader(),
                    new TerraFirmaCraftDisplayInventoryReader()));
    static { ADAPTERS.add(new dev.litemfinder.neoforge.compat.TerraFirmaCraftIngotPileReader()); }

    private BlockInventoryAdapters() { }

    public static <T extends BlockEntity> AutoCloseable register(net.minecraft.resources.ResourceLocation id,
            Class<T> type, int priority, java.util.function.Function<? super T,
            dev.litemfinder.neoforge.api.inventory.InventoryReadResult<BlockInventory>> reader) {
        return TYPED.register(id, type, priority, reader);
    }

    public static AutoCloseable register(BlockInventoryAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter must not be null");
        ADAPTERS.add(0, adapter);
        return () -> ADAPTERS.remove(adapter);
    }

    public static Optional<BlockInventory> read(BlockEntity owner) {
        var selected = TYPED.read(owner);
        if (selected.state() != dev.litemfinder.neoforge.api.inventory.InventoryReadResult.State.UNSUPPORTED) {
            return selected.value();
        }
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
