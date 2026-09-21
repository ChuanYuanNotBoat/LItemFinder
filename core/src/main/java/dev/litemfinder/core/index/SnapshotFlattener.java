package dev.litemfinder.core.index;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerPath;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.SlotSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Converts a recursive inventory snapshot into independent index entries. */
public final class SnapshotFlattener {

    public List<StorageEntry> flatten(InventorySnapshot rootSnapshot) {
        Objects.requireNonNull(rootSnapshot, "rootSnapshot must not be null");

        List<StorageEntry> entries = new ArrayList<>();
        Set<ContainerId> encounteredContainers = new HashSet<>();
        flatten(
                rootSnapshot,
                rootSnapshot.container(),
                ContainerPath.root(rootSnapshot.container().id()),
                entries,
                encounteredContainers
        );
        return List.copyOf(entries);
    }

    private void flatten(
            InventorySnapshot snapshot,
            ContainerRecord rootContainer,
            ContainerPath path,
            List<StorageEntry> entries,
            Set<ContainerId> encounteredContainers
    ) {
        if (!encounteredContainers.add(snapshot.container().id())) {
            throw new IllegalArgumentException(
                    "container appears more than once in snapshot tree: " + snapshot.container().id()
            );
        }

        for (SlotSnapshot slot : snapshot.slots()) {
            entries.add(new StorageEntry(
                    rootContainer,
                    snapshot.container(),
                    path,
                    slot.slot(),
                    slot.stack(),
                    snapshot.capturedAt()
            ));

            slot.nestedContainer().ifPresent(nested -> flatten(
                    nested,
                    rootContainer,
                    path.append(slot.slot(), nested.container().id()),
                    entries,
                    encounteredContainers
            ));
        }
    }
}
