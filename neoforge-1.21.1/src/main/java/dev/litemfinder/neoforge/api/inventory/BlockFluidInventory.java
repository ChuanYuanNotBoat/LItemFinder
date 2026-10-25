package dev.litemfinder.neoforge.api.inventory;

import dev.litemfinder.core.model.NamespacedId;
import net.minecraft.core.BlockPos;

/** Confirmed native container identity and a successful fluid read, including known-empty tanks. */
public record BlockFluidInventory(BlockPos position, NamespacedId containerType, int itemSlots, FluidInventory fluids) {
    public BlockFluidInventory {
        position = position.immutable();
        java.util.Objects.requireNonNull(containerType);
        java.util.Objects.requireNonNull(fluids);
        if (itemSlots < 0 || itemSlots > 4096) throw new IllegalArgumentException("invalid item capacity");
    }
}
