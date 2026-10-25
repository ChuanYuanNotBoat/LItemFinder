package dev.litemfinder.neoforge.capture;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Real equipment slot; cosmetic copies are deliberately excluded by the source adapter. */
public record AccessorySlot(String identifier, int index, ItemStack stack) {
    public AccessorySlot {
        Objects.requireNonNull(identifier);
        Objects.requireNonNull(stack);
        if (identifier.isBlank() || index < 0) {
            throw new IllegalArgumentException("invalid accessory slot");
        }
        stack = stack.copy();
    }

    public String key() {
        return identifier + ":" + index;
    }
}
