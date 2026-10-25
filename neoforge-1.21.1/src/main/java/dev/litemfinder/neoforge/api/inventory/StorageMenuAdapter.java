package dev.litemfinder.neoforge.api.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Optional;

/** Resolves only the real backing inventories and confirmed location of an open storage menu. */
@FunctionalInterface
public interface StorageMenuAdapter {
    Optional<MenuStorage> resolve(AbstractContainerMenu menu);
}
