package dev.litemfinder.core.planner;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.index.StorageIndex;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Deterministic nearest-neighbor route planner over currently indexed world containers. */
public final class RoutePlanner {

    private final StorageIndex index;

    public RoutePlanner(StorageIndex index) {
        this.index = Objects.requireNonNull(index, "index must not be null");
    }

    public RoutePlan plan(WorldPosition origin, List<ItemRequirement> requirements) {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(requirements, "requirements must not be null");

        Map<ItemKey, Long> remaining = aggregateRequirements(requirements);
        if (remaining.isEmpty()) {
            return new RoutePlan(List.of(), Map.of());
        }

        List<Candidate> candidates = buildCandidates(origin, remaining.keySet());
        Set<ContainerId> visited = new LinkedHashSet<>();
        List<RouteStop> stops = new ArrayList<>();
        WorldPosition current = origin;

        while (remaining.values().stream().anyMatch(amount -> amount > 0)) {
            Candidate selected = selectNearest(current, candidates, visited, remaining);
            if (selected == null) {
                break;
            }

            double distance = current.distanceTo(selected.container().location()).orElseThrow();
            Map<ItemKey, Long> pickup = new LinkedHashMap<>();
            for (Map.Entry<ItemKey, Long> need : remaining.entrySet()) {
                long available = selected.available().getOrDefault(need.getKey(), 0L);
                long amount = Math.min(need.getValue(), available);
                if (amount > 0) {
                    pickup.put(need.getKey(), amount);
                    need.setValue(need.getValue() - amount);
                }
            }

            visited.add(selected.container().id());
            stops.add(new RouteStop(selected.container(), pickup, distance));
            current = WorldPosition.at((WorldLocation) selected.container().location());
        }

        Map<ItemKey, Long> missing = new TreeMap<>();
        remaining.forEach((item, amount) -> {
            if (amount > 0) {
                missing.put(item, amount);
            }
        });
        return new RoutePlan(stops, missing);
    }

    private List<Candidate> buildCandidates(WorldPosition origin, Set<ItemKey> requiredItems) {
        Map<ContainerId, CandidateBuilder> builders = new LinkedHashMap<>();
        for (StorageEntry entry : index.allEntries()) {
            if (!requiredItems.contains(entry.stack().item())
                    || origin.distanceTo(entry.rootContainer().location()).isEmpty()) {
                continue;
            }
            CandidateBuilder builder = builders.computeIfAbsent(
                    entry.rootContainer().id(),
                    ignored -> new CandidateBuilder(entry.rootContainer())
            );
            builder.add(entry.stack().item(), entry.stack().count());
        }
        return builders.values().stream().map(CandidateBuilder::build).toList();
    }

    private Candidate selectNearest(
            WorldPosition current,
            List<Candidate> candidates,
            Set<ContainerId> visited,
            Map<ItemKey, Long> remaining
    ) {
        return candidates.stream()
                .filter(candidate -> !visited.contains(candidate.container().id()))
                .filter(candidate -> candidate.canContribute(remaining))
                .min(Comparator
                        .comparingDouble((Candidate candidate) -> current
                                .distanceTo(candidate.container().location())
                                .orElse(Double.POSITIVE_INFINITY))
                        .thenComparing(candidate -> candidate.container().id()))
                .orElse(null);
    }

    private static Map<ItemKey, Long> aggregateRequirements(List<ItemRequirement> requirements) {
        Map<ItemKey, Long> totals = new TreeMap<>();
        for (ItemRequirement requirement : requirements) {
            Objects.requireNonNull(requirement, "requirements must not contain null");
            totals.merge(requirement.item(), requirement.amount(), Math::addExact);
        }
        return totals;
    }

    private record Candidate(ContainerRecord container, Map<ItemKey, Long> available) {

        private boolean canContribute(Map<ItemKey, Long> remaining) {
            return remaining.entrySet().stream().anyMatch(
                    need -> need.getValue() > 0 && available.getOrDefault(need.getKey(), 0L) > 0
            );
        }
    }

    private static final class CandidateBuilder {

        private final ContainerRecord container;
        private final Map<ItemKey, Long> available = new LinkedHashMap<>();

        private CandidateBuilder(ContainerRecord container) {
            this.container = container;
        }

        private void add(ItemKey item, long amount) {
            available.merge(item, amount, Math::addExact);
        }

        private Candidate build() {
            return new Candidate(container, Map.copyOf(available));
        }
    }
}
