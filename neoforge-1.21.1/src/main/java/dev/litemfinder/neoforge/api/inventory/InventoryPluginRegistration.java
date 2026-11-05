package dev.litemfinder.neoforge.api.inventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Function;

/** Registration is valid only during InventoryPlugin.register; a failed plugin is rolled back. */
public interface InventoryPluginRegistration {
    <T extends BlockEntity> void blockItems(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<BlockInventory>> reader);
    <T extends BlockEntity> void blockFluids(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<BlockFluidInventory>> reader);
    <T extends AbstractContainerMenu> void menu(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<MenuStorage>> reader);
    <T extends BlockEntity> void fluids(ResourceLocation id, Class<T> type, int priority,
            Function<? super T, InventoryReadResult<FluidInventory>> reader);
    void equipment(EquipmentInventorySource source);
    void terminal(StorageTerminalAdapter adapter);
}
