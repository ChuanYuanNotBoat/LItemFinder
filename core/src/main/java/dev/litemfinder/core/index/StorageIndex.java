package dev.litemfinder.core.index;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Mutable index contract implemented independently from its future persistence mechanism. */
public interface StorageIndex {

    IndexUpdateResult update(InventorySnapshot rootSnapshot);

    boolean remove(ContainerId rootContainerId);

    List<StorageEntry> findExact(ItemKey item);

    List<StorageEntry> allEntries();

    Optional<Instant> latestCaptureTime(ContainerId rootContainerId);

    int rootContainerCount();
}
