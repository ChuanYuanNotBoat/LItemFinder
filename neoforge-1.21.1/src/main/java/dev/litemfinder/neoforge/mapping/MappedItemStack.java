package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.NamespacedId;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Complete immutable result of reading one non-empty Minecraft stack on the client thread. */
public record MappedItemStack(ItemStackInfo stack, Set<NamespacedId> tags) {

    public MappedItemStack {
        Objects.requireNonNull(stack, "stack must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        if (tags.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("tags must not contain null");
        }
        tags = Collections.unmodifiableSet(new TreeSet<>(tags));
    }
}
