package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.SlotSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Decodes nested shulker-box contents from the vanilla container data component. */
public final class NestedContainerMapper {

    public static final int DEFAULT_MAX_DEPTH = 8;
    private static final int SHULKER_SLOTS = 27;

    private final MinecraftItemStackMapper items;
    private final CachedItemTagResolver tags;
    private final int maxDepth;

    public NestedContainerMapper(MinecraftItemStackMapper items, CachedItemTagResolver tags) {
        this(items, tags, DEFAULT_MAX_DEPTH);
    }

    public NestedContainerMapper(MinecraftItemStackMapper items, CachedItemTagResolver tags, int maxDepth) {
        this.items = Objects.requireNonNull(items, "items must not be null");
        this.tags = Objects.requireNonNull(tags, "tags must not be null");
        if (maxDepth < 1 || maxDepth > 16) {
            throw new IllegalArgumentException("maxDepth must be between 1 and 16");
        }
        this.maxDepth = maxDepth;
    }

    public Optional<InventorySnapshot> map(
            ItemStack outerStack,
            MappedItemStack mappedOuter,
            ContainerRecord root,
            List<Integer> slotPath,
            HolderLookup.Provider registries,
            Instant capturedAt
    ) {
        Objects.requireNonNull(outerStack, "outerStack must not be null");
        Objects.requireNonNull(mappedOuter, "mappedOuter must not be null");
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(slotPath, "slotPath must not be null");
        Objects.requireNonNull(registries, "registries must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        if (!isShulkerBox(outerStack) || slotPath.isEmpty() || slotPath.size() > maxDepth) {
            return Optional.empty();
        }

        ItemContainerContents contents = outerStack.get(DataComponents.CONTAINER);
        if (contents == null || contents.getSlots() == 0) {
            return Optional.empty();
        }

        int slotCount = Math.max(SHULKER_SLOTS, contents.getSlots());
        List<SlotSnapshot> occupied = new ArrayList<>();
        boolean truncated = false;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            int nestedSlot = slot;
            ItemStack nestedStack = contents.getStackInSlot(slot);
            Optional<MappedItemStack> mapped = items.map(nestedStack, registries);
            if (mapped.isEmpty()) {
                continue;
            }
            MappedItemStack mappedStack = mapped.orElseThrow();
            tags.remember(mappedStack);

            List<Integer> childPath = append(slotPath, slot);
            Optional<InventorySnapshot> child = Optional.empty();
            if (slotPath.size() < maxDepth) {
                child = map(nestedStack, mappedStack, root, childPath, registries, capturedAt);
            } else if (isPopulatedShulkerBox(nestedStack)) {
                truncated = true;
            }

            occupied.add(child
                    .map(snapshot -> SlotSnapshot.nested(nestedSlot, mappedStack.stack(), snapshot))
                    .orElseGet(() -> new SlotSnapshot(nestedSlot, mappedStack.stack())));
        }

        ContainerId nestedId = nestedId(root.id(), slotPath);
        Map<String, String> metadata = new TreeMap<>();
        metadata.put("nestedDepth", Integer.toString(slotPath.size()));
        metadata.put("nestedPath", pathText(slotPath));
        if (truncated) {
            metadata.put("nestedTruncated", "max_depth");
        }
        ContainerRecord nestedContainer = new ContainerRecord(
                nestedId,
                new ContainerType(mappedOuter.stack().item().itemId()),
                new LogicalLocation(root.location().scope(), nestedId.value()),
                metadata
        );
        return Optional.of(new InventorySnapshot(nestedContainer, slotCount, capturedAt, occupied));
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isPopulatedShulkerBox(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return isShulkerBox(stack) && contents != null && contents.getSlots() > 0;
    }

    private static List<Integer> append(List<Integer> path, int slot) {
        List<Integer> child = new ArrayList<>(path);
        child.add(slot);
        return List.copyOf(child);
    }

    private static ContainerId nestedId(ContainerId root, List<Integer> path) {
        return new ContainerId("nested:" + root.value() + ":" + pathText(path));
    }

    private static String pathText(List<Integer> path) {
        return path.stream().map(String::valueOf).collect(Collectors.joining("."));
    }
}
