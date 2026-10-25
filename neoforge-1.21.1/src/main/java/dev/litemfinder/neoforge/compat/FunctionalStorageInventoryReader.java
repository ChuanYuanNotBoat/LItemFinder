package dev.litemfinder.neoforge.compat;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.api.inventory.BlockInventory;
import dev.litemfinder.neoforge.api.inventory.BlockInventoryAdapter;
import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.Optional;

/** Reads normal/framed 1/2/4-slot drawers; controllers and shared/converted views are not inventories. */
public final class FunctionalStorageInventoryReader implements BlockInventoryAdapter {
    private final Optional<Access> access;
    private boolean warned;

    public FunctionalStorageInventoryReader() {
        access = findAccess();
    }

    // Also used to test the optional API contract without loading the mod or its dependencies.
    FunctionalStorageInventoryReader(Class<?> drawerType) throws ReflectiveOperationException {
        access = Optional.of(accessFor(drawerType));
    }

    @Override
    public Optional<BlockInventory> read(BlockEntity owner) {
        if (access.isEmpty() || !access.orElseThrow().type().isInstance(owner)) return Optional.empty();
        try {
            Access api = access.orElseThrow();
            Object raw = api.storage().invoke(owner);
            if (!(raw instanceof IItemHandler handler)) return Optional.empty();
            // getStorageSlotAmount() is the number of upgrade slots (always four in 1.5.4).
            // DrawerType.getSlots() is the real item layout and excludes the optional void slot.
            int physicalSlots = (Integer) api.slots().invoke(api.drawerType().invoke(owner));
            return Optional.of(BlockInventory.fromHandler("functionalstorage:drawer", handler, physicalSlots)
                    .withLayout(InventoryLayout.grid(physicalSlots, physicalSlots == 4 ? 2 : 1)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!warned) LogUtils.getLogger().warn("Functional Storage drawer capture is unavailable", exception);
            warned = true;
            return Optional.empty();
        }
    }

    private static Optional<Access> findAccess() {
        try {
            Class<?> type = Class.forName("com.buuz135.functionalstorage.block.tile.DrawerTile", false,
                    FunctionalStorageInventoryReader.class.getClassLoader());
            return Optional.of(accessFor(type));
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        } catch (ReflectiveOperationException | LinkageError exception) {
            LogUtils.getLogger().warn("Functional Storage drawer API is incompatible", exception);
            return Optional.empty();
        }
    }

    private static Access accessFor(Class<?> type) throws ReflectiveOperationException {
        Method drawerType = type.getMethod("getDrawerType");
        return new Access(type, type.getMethod("getStorage"), drawerType,
                drawerType.getReturnType().getMethod("getSlots"));
    }

    private record Access(Class<?> type, Method storage, Method drawerType, Method slots) { }
}
