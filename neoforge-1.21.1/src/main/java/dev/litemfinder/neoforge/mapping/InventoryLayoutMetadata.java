package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.neoforge.api.inventory.InventoryLayout;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/** Versioned adapter metadata, persisted by the existing snapshot schema without changing Core. */
public final class InventoryLayoutMetadata {
    private static final String VERSION_KEY = "slotLayoutVersion";
    private static final String COUNT_KEY = "slotLayoutCount";
    private static final String PREFIX = "slotPosition.";

    private InventoryLayoutMetadata() { }

    public static void write(Map<String, String> metadata, InventoryLayout layout) {
        metadata.keySet().removeIf(key -> key.startsWith(PREFIX));
        metadata.put(VERSION_KEY, "1");
        metadata.put(COUNT_KEY, Integer.toString(layout.positions().size()));
        for (var position : layout.positions()) {
            metadata.put(PREFIX + position.slot(), position.x() + "," + position.y());
        }
    }

    public static Optional<InventoryLayout> read(Map<String, String> metadata, int slotCount) {
        if (!"1".equals(metadata.get(VERSION_KEY))) return Optional.empty();
        try {
            int expected = Integer.parseInt(metadata.getOrDefault(COUNT_KEY, "-1"));
            if (expected < 0 || expected > dev.litemfinder.neoforge.api.inventory.BlockInventory.MAX_SLOTS) return Optional.empty();
            var positions = new ArrayList<InventoryLayout.Position>();
            for (var entry : metadata.entrySet()) {
                if (!entry.getKey().startsWith(PREFIX)) continue;
                int slot = Integer.parseInt(entry.getKey().substring(PREFIX.length()));
                String[] coordinates = entry.getValue().split(",", -1);
                if (coordinates.length != 2) return Optional.empty();
                positions.add(new InventoryLayout.Position(slot, Integer.parseInt(coordinates[0]),
                        Integer.parseInt(coordinates[1])));
            }
            positions.sort(java.util.Comparator.comparingInt(InventoryLayout.Position::slot));
            if (positions.size() != expected) return Optional.empty();
            InventoryLayout layout = new InventoryLayout(positions);
            return layout.validFor(slotCount) && (slotCount == 0 || !positions.isEmpty())
                    ? Optional.of(layout) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
