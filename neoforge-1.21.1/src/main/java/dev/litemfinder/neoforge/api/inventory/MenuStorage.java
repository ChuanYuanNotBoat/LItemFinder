package dev.litemfinder.neoforge.api.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Ordered real storages; several handlers may expose overlapping local slot indices. */
public record MenuStorage(List<Object> inventories, Optional<BlockEntity> owner, Optional<BlockPos> position) {
    public MenuStorage {
        inventories = List.copyOf(inventories);
        java.util.Objects.requireNonNull(owner);
        position = position.map(BlockPos::immutable);
        if (inventories.isEmpty() || inventories.stream().anyMatch(value ->
                !(value instanceof Container) && !(value instanceof IItemHandler))) {
            throw new IllegalArgumentException("only real Container or IItemHandler storages are supported");
        }
        var unique = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
        if (inventories.stream().anyMatch(value -> !unique.add(value))) {
            throw new IllegalArgumentException("backing storage must not appear twice");
        }
    }

    public MenuStorage(Object inventory, Optional<BlockEntity> owner, BlockPos position) {
        this(List.of(inventory), owner, Optional.of(position));
    }

    public MenuStorage(List<Object> inventories, Optional<BlockEntity> owner, BlockPos position) {
        this(inventories, owner, Optional.of(position));
    }

    public static MenuStorage withoutPosition(Object inventory) {
        return new MenuStorage(List.of(inventory), Optional.empty(), Optional.empty());
    }

    public OptionalInt slotIndex(Slot slot) {
        Object actual = slot instanceof SlotItemHandler handler ? handler.getItemHandler() : slot.container;
        int offset = 0;
        for (Object inventory : inventories) {
            int size = size(inventory);
            if (actual == inventory) {
                int local = slot.getContainerSlot();
                if (local < 0 || local >= size) throw new IllegalArgumentException("invalid backing slot");
                return OptionalInt.of(offset + local);
            }
            offset = Math.addExact(offset, size);
        }
        return OptionalInt.empty();
    }

    private static int size(Object inventory) {
        return inventory instanceof IItemHandler handler ? handler.getSlots() : ((Container) inventory).getContainerSize();
    }
}
