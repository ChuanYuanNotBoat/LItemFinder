package dev.litemfinder.neoforge.client.view;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerLocation;
import dev.litemfinder.neoforge.mapping.FluidInventoryMetadata;
import dev.litemfinder.neoforge.mapping.ItemFluidInventoryMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fluid totals and capacity retain separate per-tank observation times. Never contributes item counts. */
public record FluidOverview(List<Row> rows) {
    public static FluidOverview from(ContainerOverview containers) {
        Map<String, List<Source>> grouped = new TreeMap<>();
        for (var root : containers.roots()) {
            collect(root, root.container(), root.contents(), List.of(), grouped);
        }
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
        FluidInventoryMetadata.read(container.metadata()).ifPresent(inventory -> add(root, inventory, path, grouped));
        ItemFluidInventoryMetadata.read(container.metadata()).forEach((slot, inventory) -> {
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
    private static void add(ContainerOverview.RootRow root, FluidInventoryMetadata.StoredInventory inventory,
            List<Integer> itemPath, Map<String, List<Source>> grouped) {
        for (var tank : inventory.tanks()) {
            if (tank.amount() == 0) continue;
            grouped.computeIfAbsent(tank.fluidId() + "|" + tank.variant(), ignored -> new ArrayList<>())
                    .add(new Source(root.container().id(), root.container().location(), tank, inventory.observedAt(), List.copyOf(itemPath)));
        }
    }
    public record Row(String fluidId, String variant, long amount, long capacity, List<Source> sources) { }
    public record Source(ContainerId root, ContainerLocation location, FluidInventoryMetadata.StoredTank tank,
                         Instant observedAt, List<Integer> itemPath) { }
}
