package dev.litemfinder.neoforge.persistence;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.snapshot.InventoryObservation;
import dev.litemfinder.core.snapshot.SnapshotMerger;
import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
import dev.litemfinder.neoforge.mapping.InventoryLayoutMetadata;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Minecraft identity/layout checks around the shared Core observation merger. */
public final class PartialSnapshotMerger {
    private PartialSnapshotMerger() { }
    public static Optional<InventorySnapshot> merge(InventorySnapshot previous, InventorySnapshot visible,
                                                   Set<Integer> covered, boolean complete) {
        return SnapshotMerger.merge(compatiblePrevious(previous, visible), new InventoryObservation(visible, covered, complete))
                .map(PartialSnapshotMerger::sanitizeLayout);
    }
    private static InventorySnapshot sanitizeLayout(InventorySnapshot snapshot) {
        var positions = InventoryLayoutMetadata.read(snapshot.container().metadata(), snapshot.slotCount());
        if (positions.isEmpty() || positions.orElseThrow().positions().stream().map(InventoryLayout.Position::slot)
                .collect(Collectors.toSet()).containsAll(snapshot.slots().stream().map(slot -> slot.slot()).toList())) return snapshot;
        var metadata = new TreeMap<>(snapshot.container().metadata());
        metadata.keySet().removeIf(key -> key.startsWith("slotPosition.") || key.startsWith("slotLayout"));
        var root = snapshot.container();
        return new InventorySnapshot(new ContainerRecord(root.id(), root.type(), root.location(), metadata),
                snapshot.slotCount(), snapshot.capturedAt(), snapshot.slots());
    }
    /** Only the loader can decide whether a physical block at a recorded position was replaced. */
    static InventorySnapshot compatiblePrevious(InventorySnapshot previous, InventorySnapshot incoming) {
        if (previous == null) return null;
        if (!previous.container().id().equals(incoming.container().id())) return previous;
        String before = previous.container().metadata().get("minecraftBlock");
        String after = incoming.container().metadata().get("minecraftBlock");
        return before != null && after != null && !before.equals(after) ? null : previous;
    }
    public static InventorySnapshot retainNetwork(InventorySnapshot incoming, InventorySnapshot previous) {
        return SnapshotMerger.retainNetwork(incoming, compatiblePrevious(previous, incoming));
    }
}
