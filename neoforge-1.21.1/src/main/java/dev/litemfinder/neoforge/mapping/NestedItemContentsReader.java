package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.neoforge.compat.SophisticatedBackpackContents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Shares the same nested contents between change detection and snapshot mapping. */
public final class NestedItemContentsReader {

    private final SophisticatedBackpackContents backpacks;
    private final java.util.function.BiFunction<ItemStack, HolderLookup.Provider,
            Optional<dev.litemfinder.neoforge.api.inventory.FluidInventory>> itemFluids;

    public NestedItemContentsReader() {
        this(new SophisticatedBackpackContents());
    }

    public NestedItemContentsReader(SophisticatedBackpackContents backpacks) {
        this(backpacks, ItemFluidContentsReader::read);
    }

    public NestedItemContentsReader(SophisticatedBackpackContents backpacks,
            java.util.function.BiFunction<ItemStack, HolderLookup.Provider,
                    Optional<dev.litemfinder.neoforge.api.inventory.FluidInventory>> itemFluids) {
        this.backpacks = Objects.requireNonNull(backpacks, "backpacks must not be null");
        this.itemFluids = Objects.requireNonNull(itemFluids);
    }

    public boolean hasCachedFluids(ItemStack stack) { return backpacks.accepts(stack) || SophisticatedBackpackContents.isBackpackItem(stack); }

    public Optional<dev.litemfinder.neoforge.api.inventory.FluidInventory> readFluids(ItemStack stack, HolderLookup.Provider registries) {
        try {
            var fluids = hasCachedFluids(stack) ? backpacks.readFluids(stack, registries) : itemFluids.apply(stack, registries);
            return fluids.map(value -> ItemFluidContentsReader.scale(value, stack.getCount()));
        } catch (RuntimeException | LinkageError failure) { return Optional.empty(); }
    }

    public Optional<NestedItemContents> read(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) {
            var contents = stack.get(DataComponents.CONTAINER);
            if (contents == null || contents.getSlots() == 0) {
                return Optional.empty();
            }
            Map<Integer, ItemStack> occupied = new TreeMap<>();
            for (int slot = 0; slot < contents.getSlots(); slot++) {
                ItemStack nested = contents.getStackInSlot(slot);
                if (!nested.isEmpty()) {
                    occupied.put(slot, nested);
                }
            }
            return Optional.of(new NestedItemContents(Math.max(27, contents.getSlots()), occupied));
        }
        return backpacks.read(stack, registries);
    }

    public void clear() {
        backpacks.clear();
    }
}
