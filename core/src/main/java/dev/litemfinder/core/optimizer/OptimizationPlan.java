package dev.litemfinder.core.optimizer;

import dev.litemfinder.core.index.StorageEntry;

import java.util.List;
import java.util.Objects;

/** Immutable warehouse classification plan and entries that require no generated move. */
public record OptimizationPlan(
        List<MoveTask> tasks,
        List<StorageEntry> alreadyPlaced,
        List<StorageEntry> unclassified,
        List<StorageEntry> nestedContainerItems
) {

    public OptimizationPlan {
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks must not be null"));
        alreadyPlaced = List.copyOf(Objects.requireNonNull(alreadyPlaced, "alreadyPlaced must not be null"));
        unclassified = List.copyOf(Objects.requireNonNull(unclassified, "unclassified must not be null"));
        nestedContainerItems = List.copyOf(Objects.requireNonNull(
                nestedContainerItems,
                "nestedContainerItems must not be null"
        ));
    }

    public long totalMoveAmount() {
        long total = 0;
        for (MoveTask task : tasks) {
            total = Math.addExact(total, task.amount());
        }
        return total;
    }
}
