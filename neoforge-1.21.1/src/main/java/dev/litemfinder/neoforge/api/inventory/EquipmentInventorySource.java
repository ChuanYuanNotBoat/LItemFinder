package dev.litemfinder.neoforge.api.inventory;

import dev.litemfinder.neoforge.capture.AccessorySlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/** Client-thread equipment extension. Empty Optional means unavailable; an empty list clears old slots. */
public interface EquipmentInventorySource {
    String id();

    Optional<List<AccessorySlot>> read(LivingEntity wearer);
}
