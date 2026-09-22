package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;

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
                        snapshot.slotCount(), snapshot.slots().size()))
                .sorted(Comparator.comparing((RootRow row) -> row.container().id()))
                .toList());
    }

    public record RootRow(ContainerRecord container, Instant observedAt, int slots, int occupiedSlots) {

        public RootRow {
            Objects.requireNonNull(container, "container must not be null");
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            if (slots < 0 || occupiedSlots < 0 || occupiedSlots > slots) {
                throw new IllegalArgumentException("occupied slots must lie within capacity");
            }
        }
    }
}
