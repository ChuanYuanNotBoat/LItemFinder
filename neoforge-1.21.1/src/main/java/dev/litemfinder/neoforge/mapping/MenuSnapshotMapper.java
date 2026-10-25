package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.neoforge.capture.MenuCaptureRequest;
import dev.litemfinder.neoforge.capture.MenuSlotRef;
import dev.litemfinder.neoforge.capture.BlockCaptureRequest;
import dev.litemfinder.neoforge.capture.CaptureReason;
import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
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
    private final NestedItemContentsReader contentsReader;

    public MenuSnapshotMapper(CachedItemTagResolver tags) {
        this(tags, new NestedItemContentsReader());
    }

    public MenuSnapshotMapper(CachedItemTagResolver tags, NestedItemContentsReader contentsReader) {
        this.tags = Objects.requireNonNull(tags, "tags must not be null");
        this.contentsReader = contentsReader;
        items = new MinecraftItemStackMapper();
        nestedContainers = new NestedContainerMapper(items, tags, NestedContainerMapper.DEFAULT_MAX_DEPTH, contentsReader);
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
        List<InventoryLayout.Position> positions = new ArrayList<>();
        Map<Integer, ItemStack> fluidItems = new TreeMap<>();
        boolean validPositions = true;
        for (MenuSlotRef reference : request.partition().slots()) {
            Slot menuSlot = request.menu().getSlot(reference.menuSlot());
            fluidItems.put(reference.containerSlot(), menuSlot.getItem());
            try {
                positions.add(new InventoryLayout.Position(reference.containerSlot(), menuSlot.x, menuSlot.y));
            } catch (IllegalArgumentException invalidPosition) {
                validPositions = false;
            }
            mapSlot(menuSlot.getItem(), reference.containerSlot(), root, registries, capturedAt).ifPresent(occupied::add);
        }
        Map<String, String> metadata = new TreeMap<>(root.metadata());
        int accessoryTop = positions.stream().mapToInt(InventoryLayout.Position::y).max().orElse(0) + 27;
        int accessoryLeft = positions.stream().mapToInt(InventoryLayout.Position::x).min().orElse(0);
        int accessoryIndex = 0;
        for (var accessory : request.accessories()) {
            int slot = slotCount++;
            try {
                positions.add(new InventoryLayout.Position(slot, accessoryLeft + (accessoryIndex % 9) * 18,
                        accessoryTop + (accessoryIndex / 9) * 18));
            } catch (IllegalArgumentException invalidPosition) {
                validPositions = false;
            }
            accessoryIndex++;
            metadata.put("accessorySlot." + slot, accessory.key());
            fluidItems.put(slot, accessory.stack());
            mapSlot(accessory.stack(), slot, root, registries, capturedAt).ifPresent(occupied::add);
        }
        try {
            if (validPositions) InventoryLayoutMetadata.write(metadata, new InventoryLayout(positions));
        } catch (IllegalArgumentException overlappingLayout) {
            // An unusual menu can overlay actual slots; preserve its contents with the grid fallback.
        }
        dev.litemfinder.neoforge.capture.FluidInventoryAdapters.readMenu(request.menu())
                .ifPresent(fluids -> FluidInventoryMetadata.write(metadata, fluids, registries, capturedAt));
        ItemFluidInventoryMetadata.capture(metadata, fluidItems, contentsReader, registries, capturedAt);
        root = new ContainerRecord(root.id(), root.type(), root.location(), metadata);
        return new InventorySnapshot(root, slotCount, capturedAt, occupied);
    }

    private Optional<SlotSnapshot> mapSlot(ItemStack source, int slot, ContainerRecord root,
                                            HolderLookup.Provider registries, Instant capturedAt) {
        ItemStack copied = source.copy();
        return items.map(copied, registries).map(mapped -> {
            tags.remember(mapped);
            return nestedContainers.map(copied, mapped, root, List.of(slot), registries, capturedAt)
                    .map(nested -> SlotSnapshot.nested(slot, mapped.stack(), nested))
                    .orElseGet(() -> new SlotSnapshot(slot, mapped.stack()));
        });
    }

    /** Uses the same item/variant/nesting mapping for synchronized storage without a GUI. */
    public InventorySnapshot map(BlockCaptureRequest request, HolderLookup.Provider registries, Instant capturedAt) {
        ContainerRecord source = request.identity().container();
        Map<String, String> metadata = new TreeMap<>(source.metadata());
        metadata.put("captureReason", CaptureReason.BLOCK_OBSERVED.name());
        metadata.put("inventorySource", request.inventory().source());
        request.inventory().layout().ifPresent(layout -> InventoryLayoutMetadata.write(metadata, layout));
        ContainerRecord root = new ContainerRecord(source.id(), source.type(), source.location(), metadata);
        List<SlotSnapshot> occupied = new ArrayList<>();
        var stacks = request.inventory().stacks();
        Map<Integer, ItemStack> fluidItems = new TreeMap<>();
        for (int slot = 0; slot < stacks.size(); slot++) {
            fluidItems.put(slot, stacks.get(slot));
            mapSlot(stacks.get(slot), slot, root, registries, capturedAt).ifPresent(occupied::add);
        }
        ItemFluidInventoryMetadata.capture(metadata, fluidItems, contentsReader, registries, capturedAt);
        root = new ContainerRecord(root.id(), root.type(), root.location(), metadata);
        return new InventorySnapshot(root, stacks.size(), capturedAt, occupied);
    }

    private static ContainerRecord withCaptureMetadata(MenuCaptureRequest request) {
        ContainerRecord source = request.identity().container();
        Map<String, String> metadata = new TreeMap<>(source.metadata());
        metadata.put("captureReason", request.reason().name());
        metadata.put("menuKind", request.partition().kind());
        return new ContainerRecord(source.id(), source.type(), source.location(), metadata);
    }

    public InventorySnapshot map(dev.litemfinder.neoforge.api.inventory.StorageTerminalView view,
            dev.litemfinder.neoforge.api.inventory.StorageTerminalView.Window window,
            HolderLookup.Provider registries, Instant capturedAt) {
        var source = window.identity().container();
        Map<String, String> metadata = new TreeMap<>(source.metadata());
        String network = java.util.UUID.nameUUIDFromBytes((source.location().scope() + "|" + view.networkId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        metadata.put("storageNetworkId", network);
        metadata.put("storageNetworkName", view.name());
        metadata.put("storageNetworkObservedAt", capturedAt.toString());
        metadata.put("captureReason", "TERMINAL_VISIBLE");
        window.fluids().ifPresent(fluids -> FluidInventoryMetadata.write(metadata, fluids, registries, capturedAt));
        if (window.fluids().isPresent()) metadata.put("fluidCoverage", window.complete() ? "COMPLETE" : "PARTIAL");
        ItemFluidInventoryMetadata.capture(metadata, window.slots(), contentsReader, registries, capturedAt);
        var root = new ContainerRecord(source.id(), source.type(), source.location(), metadata);
        List<SlotSnapshot> occupied = new ArrayList<>();
        window.slots().forEach((slot, stack) -> mapSlot(stack, slot, root, registries, capturedAt).ifPresent(occupied::add));
        return new InventorySnapshot(root, window.slotCount(), capturedAt, occupied);
    }
}
