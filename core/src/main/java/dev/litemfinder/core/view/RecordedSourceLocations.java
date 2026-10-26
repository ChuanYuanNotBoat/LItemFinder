package dev.litemfinder.core.view;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.WorldLocation;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Shared route targets for recorded item and fluid sources. Logical inventories have no world stop. */
public final class RecordedSourceLocations {
    private RecordedSourceLocations() { }
    public static List<WorldLocation> resolve(ContainerOverview overview, Collection<ContainerId> targets) {
        Set<ContainerId> ids = Set.copyOf(targets);
        return overview.roots().stream().filter(row -> ids.contains(row.container().id()))
                .sorted(java.util.Comparator.comparing(row -> row.container().id()))
                .map(row -> row.container().location()).filter(WorldLocation.class::isInstance)
                .map(WorldLocation.class::cast).distinct().toList();
    }
}
