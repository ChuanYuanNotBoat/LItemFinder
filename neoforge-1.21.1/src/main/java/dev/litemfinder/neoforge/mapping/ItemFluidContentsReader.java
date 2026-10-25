package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;
import java.util.Optional;

/** Fluid item capabilities are inspected on a copy and are never filled/drained. */
public final class ItemFluidContentsReader {
    private ItemFluidContentsReader() { }
    public static Optional<FluidInventory> read(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return Optional.of(new FluidInventory(List.of()));
        try {
            if (dev.litemfinder.neoforge.compat.SophisticatedBackpackTankContents.isTankUpgrade(stack)) {
                return dev.litemfinder.neoforge.compat.SophisticatedBackpackTankContents.readLoose(stack);
            }
            var handler = stack.copy().getCapability(Capabilities.FluidHandler.ITEM);
            return Optional.of(handler == null ? new FluidInventory(List.of()) : FluidInventory.fromHandler(handler));
        } catch (RuntimeException | LinkageError failure) { return Optional.empty(); }
    }
    public static FluidInventory scale(FluidInventory inventory, int count) {
        return new FluidInventory(inventory.tanks().stream().map(tank -> new FluidInventory.Tank(tank.index(),
                tank.fluid(), Math.multiplyExact(tank.amount(), count), Math.multiplyExact(tank.capacity(), count))).toList());
    }
}
