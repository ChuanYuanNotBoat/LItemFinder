package dev.litemfinder.core.view;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.SlotSnapshot;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only root container summaries, including roots with no indexed items. */
public record ContainerOverview(List<RootRow> roots) {

    public ContainerOverview {
        roots = List.copyOf(Objects.requireNonNull(roots, "roots must not be null"));
    }

    public static ContainerOverview from(List<InventorySnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots must not be null");
        return new ContainerOverview(snapshots.stream()
                .map(snapshot -> new RootRow(snapshot.container(), snapshot.capturedAt(),
                        snapshot.slotCount(), snapshot.slots().size(), snapshot.slots()))
                .sorted(Comparator.comparing((RootRow row) -> row.container().id()))
                .toList());
    }

    /** Groups require a provider-confirmed network identity; proximity and matching block types imply nothing. */
    public List<GroupRow> groups() {
        var byNetwork = new java.util.TreeMap<String, java.util.List<RootRow>>();
        for (var root : roots) {
            String network = root.container().metadata().get("storageNetworkId");
            if (network != null) byNetwork.computeIfAbsent(network, ignored -> new java.util.ArrayList<>()).add(root);
        }
        return byNetwork.entrySet().stream().map(entry -> {
            var latest = entry.getValue().stream().max(Comparator.comparing(row -> {
                try { return Instant.parse(row.container().metadata().getOrDefault("storageNetworkObservedAt", row.observedAt().toString())); }
                catch (RuntimeException invalid) { return row.observedAt(); }
            })).orElseThrow();
            return new GroupRow(entry.getKey(), latest.container().metadata().getOrDefault("storageNetworkName", "Storage network"),
                    entry.getValue().stream().map(row -> row.container().id()).sorted().toList());
        }).toList();
    }

    public record GroupRow(String id, String name, List<dev.litemfinder.core.model.ContainerId> members) { }

    public record RootRow(ContainerRecord container, Instant observedAt, int slots, int occupiedSlots,
                          List<SlotSnapshot> contents) {

        public RootRow {
            Objects.requireNonNull(container, "container must not be null");
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            contents = List.copyOf(Objects.requireNonNull(contents, "contents must not be null"));
            if (slots < 0 || occupiedSlots < 0 || occupiedSlots > slots) {
                throw new IllegalArgumentException("occupied slots must lie within capacity");
            }
            if (contents.size() != occupiedSlots) {
                throw new IllegalArgumentException("contents must match occupied slot count");
            }
        }
    }
}
