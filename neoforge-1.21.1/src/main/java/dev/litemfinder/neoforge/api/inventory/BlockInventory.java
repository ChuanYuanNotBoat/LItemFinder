package dev.litemfinder.neoforge.api.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/** Detached physical slots, including empties and counts larger than a vanilla stack. */
public record BlockInventory(String source, List<ItemStack> stacks, Optional<InventoryLayout> layout) {
    public static final int MAX_SLOTS = 4096;

    public BlockInventory {
        Objects.requireNonNull(source, "source must not be null");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        Objects.requireNonNull(stacks, "stacks must not be null");
        if (stacks.size() > MAX_SLOTS) throw new IllegalArgumentException("too many block inventory slots");
        stacks = stacks.stream().map(ItemStack::copy).toList();
        Objects.requireNonNull(layout, "layout must not be null");
        if (layout.isPresent() && !layout.orElseThrow().validFor(stacks.size())) {
            throw new IllegalArgumentException("layout refers to a missing slot");
        }
    }

    public BlockInventory(String source, List<ItemStack> stacks) {
        this(source, stacks, Optional.empty());
    }

    public BlockInventory withLayout(InventoryLayout positions) {
        return new BlockInventory(source, stacks, Optional.of(positions));
    }

    /** Read only real slots, excluding overflow/void/filter/upgrade views supplied by a handler. */
    public static BlockInventory fromHandler(String source, IItemHandler handler, int physicalSlots) {
        Objects.requireNonNull(handler, "handler must not be null");
        if (physicalSlots < 0 || physicalSlots > MAX_SLOTS || physicalSlots > handler.getSlots()) {
            throw new IllegalArgumentException("invalid physical slot count");
        }
        return new BlockInventory(source, IntStream.range(0, physicalSlots)
                .mapToObj(handler::getStackInSlot).toList());
    }

    public boolean sameContents(BlockInventory other) {
        if (!source.equals(other.source) || stacks.size() != other.stacks.size() || !layout.equals(other.layout)) return false;
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!ItemStack.matches(stacks.get(slot), other.stacks.get(slot))) return false;
        }
        return true;
    }
}
