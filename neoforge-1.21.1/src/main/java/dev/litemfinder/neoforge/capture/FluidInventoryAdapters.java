package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import dev.litemfinder.neoforge.api.inventory.FluidInventoryAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Positive opt-in contracts; arbitrary unsynchronized block capabilities are never interpreted as empty. */
public final class FluidInventoryAdapters {
    private static final CopyOnWriteArrayList<FluidInventoryAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<dev.litemfinder.neoforge.api.inventory.BlockFluidInventoryAdapter> BLOCK_ADAPTERS = new CopyOnWriteArrayList<>();
    private FluidInventoryAdapters() { }

    public static AutoCloseable register(FluidInventoryAdapter adapter) {
        ADAPTERS.add(0, Objects.requireNonNull(adapter));
        return () -> ADAPTERS.remove(adapter);
    }

    public static Optional<FluidInventory> readMenu(AbstractContainerMenu menu) {
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) return Optional.empty();
        return StorageMenuAdapters.resolve(menu).flatMap(storage -> storage.owner().flatMap(FluidInventoryAdapters::read));
    }
    public static AutoCloseable registerBlock(dev.litemfinder.neoforge.api.inventory.BlockFluidInventoryAdapter adapter) {
        BLOCK_ADAPTERS.add(0, Objects.requireNonNull(adapter));
        return () -> BLOCK_ADAPTERS.remove(adapter);
    }
    public static Optional<dev.litemfinder.neoforge.api.inventory.BlockFluidInventory> readBlock(BlockEntity owner) {
        for (var adapter : BLOCK_ADAPTERS) {
            try { var result = adapter.read(owner); if (result.isPresent()) return result; }
            catch (RuntimeException | LinkageError ignored) { }
        }
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType());
        if (type != null && type.toString().equals("tfc:barrel")) {
            return read(owner).map(fluids -> new dev.litemfinder.neoforge.api.inventory.BlockFluidInventory(owner.getBlockPos(),
                    dev.litemfinder.core.model.NamespacedId.parse("tfc:barrel"), 3, fluids));
        }
        return dev.litemfinder.neoforge.compat.ImmersiveEngineeringCokeFluidReader.read(owner);
    }

    public static Optional<FluidInventory> read(BlockEntity owner) {
        for (var adapter : ADAPTERS) {
            try {
                var value = adapter.read(owner);
                if (value.isPresent()) return value;
            } catch (RuntimeException | LinkageError ignored) { }
        }
        // TFC BarrelInventory implements DelegateFluidHandler. Its serialized inventory includes the tank,
        // and the server synchronizes it to the open menu/client block entity, including sealed barrels.
        var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType());
        return readSyncedBarrel(owner, type == null ? "" : type.toString());
    }

    static Optional<FluidInventory> readSyncedBarrel(BlockEntity owner, String registeredType) {
        if (!registeredType.equals("tfc:barrel")) return Optional.empty();
        try {
            Object inventory = owner.getClass().getMethod("getInventory").invoke(owner);
            return inventory instanceof IFluidHandler handler
                    ? Optional.of(FluidInventory.fromHandler(handler)) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}
