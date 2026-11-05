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

    private boolean usesBackpackCache(ItemStack stack) {
        return backpacks.accepts(stack) || SophisticatedBackpackContents.isBackpackItem(stack);
    }

    public boolean hasCachedFluids(ItemStack stack) { return usesBackpackCache(stack); }

    public boolean isContainer(ItemStack stack) {
        return usesBackpackCache(stack) || stack.has(DataComponents.CONTAINER)
                || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }

    public Map<String, String> previewMetadata(ItemStack stack) { return backpacks.previewMetadata(stack); }

    public void markUnavailable(Map<String, String> metadata, Map<Integer, ItemStack> stacks, HolderLookup.Provider registries) {
        stacks.forEach((slot, stack) -> backpacks.identity(stack).ifPresent(identity -> metadata.put(
                dev.litemfinder.core.snapshot.NestedInventoryObservation.IDENTITY_PREFIX + slot, identity)));
        String missing = stacks.entrySet().stream().filter(entry -> !entry.getValue().isEmpty() && isContainer(entry.getValue())
                && read(entry.getValue(), registries).isEmpty()).map(entry -> Integer.toString(entry.getKey()))
                .collect(java.util.stream.Collectors.joining(","));
        if (!missing.isEmpty()) metadata.put(dev.litemfinder.core.snapshot.NestedInventoryObservation.UNAVAILABLE, missing);
    }

    public Optional<dev.litemfinder.neoforge.api.inventory.FluidInventory> readFluids(ItemStack stack, HolderLookup.Provider registries) {
        try {
            var fluids = hasCachedFluids(stack) ? backpacks.readFluids(stack, registries) : itemFluids.apply(stack, registries);
            return fluids.map(value -> ItemFluidContentsReader.scale(value, stack.getCount()));
        } catch (RuntimeException | LinkageError failure) { return Optional.empty(); }
    }

    public Optional<NestedItemContents> read(ItemStack stack, HolderLookup.Provider registries) {
        // The vanilla component is an explicit, already-synchronized storage contract used by modded items too.
        // Sophisticated backpacks use their own synchronized cache and must retain that authoritative reader.
        boolean shulker = stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
        var contents = stack.get(DataComponents.CONTAINER);
        if (shulker || (contents != null && !usesBackpackCache(stack))) {
            if (contents == null || contents.getSlots() == 0) return Optional.of(new NestedItemContents(shulker ? 27 : 0, Map.of()));
            Map<Integer, ItemStack> occupied = new TreeMap<>();
            for (int slot = 0; slot < contents.getSlots(); slot++) {
                ItemStack nested = contents.getStackInSlot(slot);
                if (!nested.isEmpty()) {
                    occupied.put(slot, nested);
                }
            }
            return Optional.of(new NestedItemContents(shulker ? Math.max(27, contents.getSlots()) : contents.getSlots(), occupied));
        }
        return backpacks.read(stack, registries);
    }

    public void clear() {
        backpacks.clear();
    }
}
