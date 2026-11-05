package dev.litemfinder.core.view;

import dev.litemfinder.core.index.SnapshotFlattener;
import dev.litemfinder.core.model.*;
import java.util.*;

/** Shared carried-container lookup and exact-variant preview totals, independent of game widgets. */
public final class ContainerPreview {
    private ContainerPreview() { }
    public record Entry(ItemKey item, long count) { }
    public static List<Entry> aggregate(InventorySnapshot snapshot) {
        Map<ItemKey, Long> amounts = new TreeMap<>();
        new SnapshotFlattener().flatten(snapshot).forEach(entry -> amounts.merge(entry.stack().item(), entry.stack().count(), Math::addExact));
        return amounts.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(Entry::count).reversed().thenComparing(Entry::item)).toList();
    }
    public static Optional<ContainerOverview.RootRow> player(ContainerOverview overview, UUID player) {
        String suffix = ":" + player + ":inventory";
        return overview.roots().stream().filter(row -> row.container().type().id().toString().equals("minecraft:player_inventory")
                && row.container().id().value().endsWith(suffix)).findFirst();
    }
    public static InventorySnapshot snapshot(ContainerOverview.RootRow row) {
        return new InventorySnapshot(row.container(), row.slots(), row.observedAt(), row.contents());
    }
}
