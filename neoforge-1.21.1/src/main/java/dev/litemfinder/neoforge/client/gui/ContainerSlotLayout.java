package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
import dev.litemfinder.neoforge.mapping.InventoryLayoutMetadata;

import java.util.List;
import java.util.Map;

/** Pure layout/viewport math shared by rendering, scrolling and slot hit boxes. */
public record ContainerSlotLayout(List<InventoryLayout.Position> positions, int width, int height, int minX, int minY) {
    public static final int SLOT_SIZE = InventoryLayout.SLOT_PITCH;

    public static ContainerSlotLayout resolve(Map<String, String> metadata, int slots) {
        var layout = InventoryLayoutMetadata.read(metadata, slots)
                .orElseGet(() -> InventoryLayout.grid(slots, Math.max(1, Math.min(9, slots))));
        int minX = layout.positions().stream().mapToInt(InventoryLayout.Position::x).min().orElse(0);
        int minY = layout.positions().stream().mapToInt(InventoryLayout.Position::y).min().orElse(0);
        int width = layout.positions().stream().mapToInt(InventoryLayout.Position::x).max().orElse(0) - minX + SLOT_SIZE;
        int height = layout.positions().stream().mapToInt(InventoryLayout.Position::y).max().orElse(-SLOT_SIZE) - minY + SLOT_SIZE;
        return new ContainerSlotLayout(layout.positions(), width, height, minX, minY);
    }

    public float scale(int screenWidth) {
        return Math.min(1f, Math.max(1, screenWidth - 16) / (float) width);
    }

    public int scrollLimit(int screenWidth, int viewportHeight) {
        int overflow = Math.max(0, (int) Math.ceil(height * scale(screenWidth)) - Math.max(1, viewportHeight));
        return (overflow + SLOT_SIZE - 1) / SLOT_SIZE;
    }

    public List<Cell> cells(int screenWidth, int top, int bottom, int scrollRows) {
        float scale = scale(screenWidth);
        int left = Math.max(8, Math.round((screenWidth - width * scale) / 2));
        int size = Math.max(1, (int) Math.ceil(SLOT_SIZE * scale));
        return positions.stream().map(position -> new Cell(position.slot(),
                        left + Math.round((position.x() - minX) * scale),
                        top + Math.round((position.y() - minY) * scale) - scrollRows * SLOT_SIZE, size, scale))
                .filter(cell -> cell.y() + cell.size() > top && cell.y() < bottom).toList();
    }

    public record Cell(int slot, int x, int y, int size, float scale) { }
}
