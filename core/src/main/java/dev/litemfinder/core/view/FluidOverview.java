package dev.litemfinder.core.view;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerLocation;
import dev.litemfinder.core.model.FluidKey;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.snapshot.FluidSnapshotMetadata;
import dev.litemfinder.core.snapshot.ItemFluidSnapshotMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;

/** Fluid totals and capacity retain separate per-tank observation times. Never contributes item counts. */
public record FluidOverview(List<Row> rows) {
    public FluidOverview { rows = List.copyOf(rows); }
    public static FluidOverview from(ContainerOverview containers) {
        Map<String, List<Source>> grouped = new TreeMap<>();
        for (var root : containers.roots()) {
            collect(root, root.container(), root.contents(), List.of(), grouped);
        }
        return aggregate(grouped);
    }

    public FluidOverview filterSources(java.util.function.Predicate<Source> predicate) {
        Map<String, List<Source>> grouped = new TreeMap<>();
        rows.stream().flatMap(row -> row.sources().stream()).filter(predicate).forEach(source -> group(grouped, source));
        return aggregate(grouped);
    }

    public Map<ContainerId, FluidOverview> byRoot() {
        Map<ContainerId, Map<String, List<Source>>> grouped = new TreeMap<>();
        rows.stream().flatMap(row -> row.sources().stream()).forEach(source ->
                group(grouped.computeIfAbsent(source.root(), ignored -> new TreeMap<>()), source));
        Map<ContainerId, FluidOverview> result = new TreeMap<>();
        grouped.forEach((root, sources) -> result.put(root, aggregate(sources)));
        return Map.copyOf(result);
    }

    private static void group(Map<String, List<Source>> grouped, Source source) {
        grouped.computeIfAbsent(source.tank().fluidId() + "|" + source.tank().variant(), ignored -> new ArrayList<>()).add(source);
    }

    private static FluidOverview aggregate(Map<String, List<Source>> grouped) {
        return new FluidOverview(grouped.values().stream().map(sources -> {
            long amount = 0;
            long capacity = 0;
            boolean unknownCapacity = false;
            for (var source : sources) {
                amount = Math.addExact(amount, source.tank().amount());
                capacity = Math.addExact(capacity, source.tank().capacity());
                unknownCapacity |= source.tank().capacity() == 0;
            }
            return new Row(sources.getFirst().tank().fluidId(), sources.getFirst().tank().variant(),
                    amount, unknownCapacity ? 0 : capacity, List.copyOf(sources));
        }).sorted(Comparator.comparingLong(Row::amount).reversed().thenComparing(Row::fluidId).thenComparing(Row::variant)).toList());
    }
    private static void collect(ContainerOverview.RootRow root, dev.litemfinder.core.model.ContainerRecord container,
            List<dev.litemfinder.core.model.SlotSnapshot> slots, List<Integer> path, Map<String, List<Source>> grouped) {
        FluidSnapshotMetadata.read(container.metadata()).ifPresent(inventory -> add(root, inventory, path, grouped));
        ItemFluidSnapshotMetadata.read(container.metadata()).forEach((slot, inventory) -> {
            var itemPath = new ArrayList<>(path);
            itemPath.add(slot);
            add(root, inventory, itemPath, grouped);
        });
        for (var slot : slots) slot.nestedContainer().ifPresent(nested -> {
            var childPath = new ArrayList<>(path);
            childPath.add(slot.slot());
            collect(root, nested.container(), nested.slots(), childPath, grouped);
        });
    }
    private static void add(ContainerOverview.RootRow root, FluidSnapshotMetadata.StoredInventory inventory,
            List<Integer> itemPath, Map<String, List<Source>> grouped) {
        for (var tank : inventory.tanks()) {
            if (tank.amount() == 0) continue;
            group(grouped, new Source(root.container().id(), root.container().location(), tank, inventory.observedAt(), List.copyOf(itemPath)));
        }
    }
    public record Row(String fluidId, String variant, long amount, long capacity, List<Source> sources) {
        public Row {
            Objects.requireNonNull(fluidId); Objects.requireNonNull(variant);
            sources = List.copyOf(sources);
            if (amount < 0 || capacity < 0 || sources.isEmpty()) throw new IllegalArgumentException("invalid fluid row");
        }
        public FluidKey key() { return new FluidKey(NamespacedId.parse(fluidId), variant); }
    }
    public record Source(ContainerId root, ContainerLocation location, FluidSnapshotMetadata.StoredTank tank,
                         Instant observedAt, List<Integer> itemPath) {
        public Source {
            Objects.requireNonNull(root); Objects.requireNonNull(location); Objects.requireNonNull(tank); Objects.requireNonNull(observedAt);
            itemPath = List.copyOf(itemPath);
            if (itemPath.stream().anyMatch(slot -> slot < 0)) throw new IllegalArgumentException("invalid item path");
        }
    }
}
