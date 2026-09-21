package dev.litemfinder.core.persistence;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.InventorySnapshot;

import java.util.List;
import java.util.Optional;

/** Persistent storage boundary for complete root container snapshots. */
public interface SnapshotRepository extends AutoCloseable {

    SnapshotWriteResult save(InventorySnapshot rootSnapshot);

    Optional<InventorySnapshot> find(ContainerId rootContainerId);

    List<InventorySnapshot> findAll();

    boolean delete(ContainerId rootContainerId);

    @Override
    void close();
}
