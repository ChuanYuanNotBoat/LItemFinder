package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ItemKey;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves overlapping storage groups by priority and then stable group identity. */
public final class StorageClassifier {

    private static final Comparator<StorageGroup> GROUP_ORDER =
            Comparator.comparingInt(StorageGroup::priority).reversed()
                    .thenComparing(StorageGroup::id);

    private final List<StorageGroup> groups;
    private final ItemTagResolver tags;

    public StorageClassifier(List<StorageGroup> groups, ItemTagResolver tags) {
        Objects.requireNonNull(groups, "groups must not be null");
        this.groups = groups.stream().sorted(GROUP_ORDER).toList();
        this.tags = Objects.requireNonNull(tags, "tags must not be null");
    }

    public Optional<StorageGroup> classify(ItemKey item) {
        Objects.requireNonNull(item, "item must not be null");
        return groups.stream().filter(group -> group.rule().matches(item, tags)).findFirst();
    }

    public List<StorageGroup> groups() {
        return groups;
    }
}
