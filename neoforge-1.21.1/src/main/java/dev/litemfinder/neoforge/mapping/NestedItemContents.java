package dev.litemfinder.neoforge.mapping;

import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Client-thread view of actual storage slots, excluding upgrades and settings filters. */
public record NestedItemContents(int slotCount, Map<Integer, ItemStack> stacks) {

    public NestedItemContents {
        if (slotCount < 1) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        stacks = Collections.unmodifiableMap(new TreeMap<>(stacks));
        for (var entry : stacks.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= slotCount || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("invalid occupied storage slot");
            }
        }
    }
}
