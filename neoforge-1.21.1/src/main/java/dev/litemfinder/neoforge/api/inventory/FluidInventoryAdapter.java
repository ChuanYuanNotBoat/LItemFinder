package dev.litemfinder.neoforge.api.inventory;

import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Optional;

/** Reads confirmed, already synchronized fluid tanks. Empty means unavailable, not drained. */
@FunctionalInterface
public interface FluidInventoryAdapter {
    Optional<FluidInventory> read(BlockEntity owner);
}
