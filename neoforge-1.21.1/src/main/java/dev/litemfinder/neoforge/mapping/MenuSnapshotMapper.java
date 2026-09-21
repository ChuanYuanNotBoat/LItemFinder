package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.neoforge.capture.MenuCaptureRequest;
import dev.litemfinder.neoforge.capture.MenuSlotRef;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Copies one trusted live menu into a complete immutable Core snapshot on the client thread. */
public final class MenuSnapshotMapper {

    private final MinecraftItemStackMapper items;
    private final CachedItemTagResolver tags;
    private final NestedContainerMapper nestedContainers;

    public MenuSnapshotMapper(CachedItemTagResolver tags) {
        this.tags = Objects.requireNonNull(tags, "tags must not be null");
        items = new MinecraftItemStackMapper();
        nestedContainers = new NestedContainerMapper(items, tags);
    }

    public InventorySnapshot map(
            MenuCaptureRequest request,
            HolderLookup.Provider registries,
            Instant capturedAt
    ) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(registries, "registries must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");

        ContainerRecord root = withCaptureMetadata(request);
        int slotCount = request.partition().slots().stream()
                .mapToInt(MenuSlotRef::containerSlot)
                .max()
                .orElseThrow() + 1;
        List<SlotSnapshot> occupied = new ArrayList<>();
        for (MenuSlotRef reference : request.partition().slots()) {
            Slot menuSlot = request.menu().getSlot(reference.menuSlot());
            ItemStack copied = menuSlot.getItem().copy();
            Optional<MappedItemStack> mapped = items.map(copied, registries);
            if (mapped.isEmpty()) {
                continue;
            }
            MappedItemStack mappedStack = mapped.orElseThrow();
            tags.remember(mappedStack);
            Optional<InventorySnapshot> nested = nestedContainers.map(
                    copied,
                    mappedStack,
                    root,
                    List.of(reference.containerSlot()),
                    registries,
                    capturedAt
            );
            occupied.add(nested
                    .map(snapshot -> SlotSnapshot.nested(reference.containerSlot(), mappedStack.stack(), snapshot))
                    .orElseGet(() -> new SlotSnapshot(reference.containerSlot(), mappedStack.stack())));
        }
        return new InventorySnapshot(root, slotCount, capturedAt, occupied);
    }

    private static ContainerRecord withCaptureMetadata(MenuCaptureRequest request) {
        ContainerRecord source = request.identity().container();
        Map<String, String> metadata = new TreeMap<>(source.metadata());
        metadata.put("captureReason", request.reason().name());
        metadata.put("menuKind", request.partition().kind());
        return new ContainerRecord(source.id(), source.type(), source.location(), metadata);
    }
}
