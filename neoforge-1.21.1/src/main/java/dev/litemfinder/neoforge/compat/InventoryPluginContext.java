package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.api.inventory.*;
import dev.litemfinder.neoforge.capture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Owns registrations so an entrypoint that fails halfway cannot leave a partially active integration. */
final class InventoryPluginContext implements InventoryPluginRegistration, AutoCloseable {
    private final List<AutoCloseable> registrations = new ArrayList<>();
    private boolean sealed;

    @Override public <T extends BlockEntity> void blockItems(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<BlockInventory>> reader) {
        checkOpen();
        registrations.add(BlockInventoryAdapters.register(id, type, priority, reader));
    }

    @Override public <T extends BlockEntity> void blockFluids(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<BlockFluidInventory>> reader) {
        checkOpen();
        registrations.add(FluidInventoryAdapters.registerBlock(id, type, priority, reader));
    }

    @Override public <T extends AbstractContainerMenu> void menu(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<MenuStorage>> reader) {
        checkOpen();
        registrations.add(StorageMenuAdapters.register(id, type, priority, reader));
    }

    @Override public <T extends BlockEntity> void fluids(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<FluidInventory>> reader) {
        checkOpen();
        registrations.add(FluidInventoryAdapters.register(id, type, priority, reader));
    }

    @Override public void equipment(EquipmentInventorySource source) {
        checkOpen();
        registrations.add(EquipmentInventorySources.register(source));
    }

    @Override public void terminal(StorageTerminalAdapter adapter) {
        checkOpen();
        registrations.add(TerminalMenuAdapters.register(adapter));
    }

    void seal() { sealed = true; }

    private void checkOpen() {
        if (sealed) throw new IllegalStateException("inventory plugin registration has ended");
    }

    @Override public void close() {
        sealed = true;
        for (int index = registrations.size() - 1; index >= 0; index--) {
            try { registrations.get(index).close(); }
            catch (Exception failure) { throw new IllegalStateException("cannot remove inventory adapter", failure); }
        }
        registrations.clear();
    }
}
