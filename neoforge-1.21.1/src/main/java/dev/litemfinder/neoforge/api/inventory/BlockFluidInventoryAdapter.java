package dev.litemfinder.neoforge.api.inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Optional;
@FunctionalInterface
public interface BlockFluidInventoryAdapter {
    Optional<BlockFluidInventory> read(BlockEntity target);
}
