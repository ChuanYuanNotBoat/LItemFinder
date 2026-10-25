package dev.litemfinder.neoforge.api.inventory;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/** Opt-in reader for actual item storage already synchronized to the client without a menu. */
@FunctionalInterface
public interface BlockInventoryAdapter {
    /** Empty means unsupported or unavailable, never proof that previously observed storage is empty. */
    Optional<BlockInventory> read(BlockEntity owner);
}
