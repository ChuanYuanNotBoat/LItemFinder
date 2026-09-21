package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ContainerId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Named target containers and the item rule assigned to them. */
public record StorageGroup(
        StorageGroupId id,
        String name,
        List<ContainerId> containers,
        ItemRule rule,
        int priority
) {

    public StorageGroup {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank() || !name.equals(name.trim())) {
            throw new IllegalArgumentException("name must be non-blank without surrounding whitespace");
        }
        Objects.requireNonNull(containers, "containers must not be null");
        containers = List.copyOf(new LinkedHashSet<>(containers));
        if (containers.isEmpty()) {
            throw new IllegalArgumentException("containers must not be empty");
        }
        Objects.requireNonNull(rule, "rule must not be null");
    }
}
