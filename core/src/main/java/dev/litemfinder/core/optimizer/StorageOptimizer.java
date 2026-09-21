package dev.litemfinder.core.optimizer;

import dev.litemfinder.core.classification.StorageClassifier;
import dev.litemfinder.core.classification.StorageGroup;
import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.index.StorageIndex;
import dev.litemfinder.core.model.ContainerPath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Generates deterministic classification moves without executing platform operations. */
public final class StorageOptimizer {

    private final StorageIndex index;
    private final StorageClassifier classifier;

    public StorageOptimizer(StorageIndex index, StorageClassifier classifier) {
        this.index = Objects.requireNonNull(index, "index must not be null");
        this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
    }

    public OptimizationPlan optimize() {
        List<StorageEntry> entries = index.allEntries();
        Set<StorageSlotRef> nestedContainerSlots = findNestedContainerSlots(entries);
        List<MoveTask> tasks = new ArrayList<>();
        List<StorageEntry> alreadyPlaced = new ArrayList<>();
        List<StorageEntry> unclassified = new ArrayList<>();
        List<StorageEntry> nestedContainerItems = new ArrayList<>();

        for (StorageEntry entry : entries) {
            StorageSlotRef source = new StorageSlotRef(entry.path(), entry.slot());
            if (nestedContainerSlots.contains(source)) {
                nestedContainerItems.add(entry);
                continue;
            }

            var classified = classifier.classify(entry.stack().item());
            if (classified.isEmpty()) {
                unclassified.add(entry);
                continue;
            }

            StorageGroup group = classified.orElseThrow();
            if (group.containers().contains(entry.rootContainer().id())) {
                alreadyPlaced.add(entry);
                continue;
            }

            tasks.add(new MoveTask(
                    source,
                    group.containers().getFirst(),
                    group.id(),
                    entry.stack().item(),
                    entry.stack().count()
            ));
        }

        return new OptimizationPlan(tasks, alreadyPlaced, unclassified, nestedContainerItems);
    }

    private static Set<StorageSlotRef> findNestedContainerSlots(List<StorageEntry> entries) {
        Set<StorageSlotRef> slots = new HashSet<>();
        for (StorageEntry entry : entries) {
            ContainerPath currentPath = ContainerPath.root(entry.path().root());
            for (ContainerPath.Hop hop : entry.path().hops()) {
                slots.add(new StorageSlotRef(currentPath, hop.parentSlot()));
                currentPath = currentPath.append(hop.parentSlot(), hop.container());
            }
        }
        return slots;
    }
}
