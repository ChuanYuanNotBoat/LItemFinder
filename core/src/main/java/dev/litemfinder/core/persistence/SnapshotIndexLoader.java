package dev.litemfinder.core.persistence;

import dev.litemfinder.core.index.IndexUpdateResult;
import dev.litemfinder.core.index.StorageIndex;

import java.util.Objects;

/** Restores persisted snapshots into an index during application startup. */
public final class SnapshotIndexLoader {

    public int load(SnapshotRepository repository, StorageIndex index) {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(index, "index must not be null");

        int loaded = 0;
        for (var snapshot : repository.findAll()) {
            if (index.update(snapshot) != IndexUpdateResult.IGNORED_STALE) {
                loaded++;
            }
        }
        return loaded;
    }
}
