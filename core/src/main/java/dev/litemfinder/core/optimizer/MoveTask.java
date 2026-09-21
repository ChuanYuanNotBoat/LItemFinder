package dev.litemfinder.core.optimizer;

import dev.litemfinder.core.classification.StorageGroupId;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ItemKey;

import java.util.Objects;

/** Declarative move request; platform code decides how to execute the inventory clicks. */
public record MoveTask(
        StorageSlotRef source,
        ContainerId targetContainer,
        StorageGroupId targetGroup,
        ItemKey item,
        long amount
) {

    public MoveTask {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(targetContainer, "targetContainer must not be null");
        Objects.requireNonNull(targetGroup, "targetGroup must not be null");
        Objects.requireNonNull(item, "item must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (source.path().root().equals(targetContainer)) {
            throw new IllegalArgumentException("source root and target container must differ");
        }
    }
}
