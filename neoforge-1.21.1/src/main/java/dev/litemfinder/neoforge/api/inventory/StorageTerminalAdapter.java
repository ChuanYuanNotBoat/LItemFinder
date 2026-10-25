package dev.litemfinder.neoforge.api.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.Optional;

/** Opt-in terminal projection. Its member windows must identify actual backing containers, not ghost slots. */
public interface StorageTerminalAdapter {
    boolean supports(AbstractContainerMenu menu);
    /** Empty means loading/unavailable, so the previous observation is retained. */
    Optional<StorageTerminalView> read(AbstractContainerMenu menu);
}
