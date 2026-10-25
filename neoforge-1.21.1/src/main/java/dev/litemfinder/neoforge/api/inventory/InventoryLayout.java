package dev.litemfinder.neoforge.api.inventory;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

/** Original item-slot positions in GUI pixels; contains no player/ghost slots or background artwork. */
public record InventoryLayout(List<Position> positions) {
    public static final int SLOT_PITCH = 18;

    public InventoryLayout {
        positions = List.copyOf(positions);
        if (positions.size() > BlockInventory.MAX_SLOTS) throw new IllegalArgumentException("too many slot positions");
        var slots = new HashSet<Integer>();
        var coordinates = new HashSet<String>();
        for (var position : positions) {
            if (!slots.add(position.slot()) || !coordinates.add(position.x() + "," + position.y())) {
                throw new IllegalArgumentException("duplicate layout slot or position");
            }
        }
    }

    public boolean validFor(int slotCount) {
        return positions.stream().allMatch(position -> position.slot() < slotCount);
    }

    public static InventoryLayout grid(int slots, int columns) {
        if (slots < 0 || slots > BlockInventory.MAX_SLOTS || columns < 1) {
            throw new IllegalArgumentException("invalid inventory grid");
        }
        return new InventoryLayout(IntStream.range(0, slots)
                .mapToObj(slot -> new Position(slot, (slot % columns) * SLOT_PITCH,
                        (slot / columns) * SLOT_PITCH)).toList());
    }

    public record Position(int slot, int x, int y) {
        public Position {
            if (slot < 0 || slot >= BlockInventory.MAX_SLOTS || Math.abs((long) x) > 8192 || Math.abs((long) y) > 8192) {
                throw new IllegalArgumentException("invalid slot position");
            }
        }
    }
}
