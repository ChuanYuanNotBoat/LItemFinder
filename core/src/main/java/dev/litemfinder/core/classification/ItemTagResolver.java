package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;

import java.util.Set;

/** Supplies loader-provided item tags without exposing a game registry to Core. */
@FunctionalInterface
public interface ItemTagResolver {

    Set<NamespacedId> tags(ItemKey item);

    static ItemTagResolver empty() {
        return ignored -> Set.of();
    }
}
