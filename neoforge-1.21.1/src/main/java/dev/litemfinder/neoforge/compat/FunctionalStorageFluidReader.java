package dev.litemfinder.neoforge.compat;

import com.mojang.logging.LogUtils;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.neoforge.api.inventory.BlockFluidInventory;
import dev.litemfinder.neoforge.api.inventory.BlockFluidInventoryAdapter;
import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import java.lang.reflect.Method;
import java.util.Optional;

/** Reads only physical normal/framed fluid drawers whose saved handler is synchronized by Titanium.
 * Controllers, lock filters and creative/infinite views are never counted as finite stored fluid. */
public final class FunctionalStorageFluidReader implements BlockFluidInventoryAdapter {
    private final Optional<Access> access;
    private boolean warned;
    public FunctionalStorageFluidReader() { access = findAccess(); }
    FunctionalStorageFluidReader(Class<?> drawerType) throws ReflectiveOperationException { access = Optional.of(accessFor(drawerType)); }

    @Override public Optional<BlockFluidInventory> read(BlockEntity owner) {
        if (access.isEmpty() || !access.orElseThrow().type().isInstance(owner)) return Optional.empty();
        try {
            var api = access.orElseThrow();
            Object raw = api.handler().invoke(owner);
            if (!(raw instanceof IFluidHandler handler)) return Optional.empty();
            // CustomFluidTank.getFluidInTank mutates the live stack to MAX_VALUE for creative drawers.
            // Check this flag first, without querying their contents or calling fill/drain.
            if (!Boolean.FALSE.equals(api.creative().invoke(raw))) return Optional.empty();
            int slots = ((Number) api.slots().invoke(api.drawerType().invoke(owner))).intValue();
            if ((slots != 1 && slots != 2 && slots != 4) || handler.getTanks() != slots) return Optional.empty();
            return Optional.of(new BlockFluidInventory(owner.getBlockPos(), NamespacedId.parse("litemfinder:block_inventory"),
                    0, FluidInventory.fromHandler(handler)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warned) LogUtils.getLogger().warn("Functional Storage fluid drawer capture is unavailable", failure);
            warned = true;
            return Optional.empty();
        }
    }
    private static Optional<Access> findAccess() {
        try {
            return Optional.of(accessFor(Class.forName("com.buuz135.functionalstorage.block.tile.FluidDrawerTile", false,
                    FunctionalStorageFluidReader.class.getClassLoader())));
        } catch (ClassNotFoundException absent) { return Optional.empty(); }
        catch (ReflectiveOperationException | LinkageError incompatible) {
            LogUtils.getLogger().warn("Functional Storage fluid drawer API is incompatible", incompatible);
            return Optional.empty();
        }
    }
    private static Access accessFor(Class<?> type) throws ReflectiveOperationException {
        Method handler = type.getMethod("getFluidHandler"), drawerType = type.getMethod("getDrawerType");
        return new Access(type, handler, drawerType, drawerType.getReturnType().getMethod("getSlots"),
                handler.getReturnType().getMethod("isDrawerCreative"));
    }
    private record Access(Class<?> type, Method handler, Method drawerType, Method slots, Method creative) { }
}
