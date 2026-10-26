package dev.litemfinder.neoforge.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** One row geometry and palette for both inventory modes. */
final class InventoryListStyle {
    static final int ROW_HEIGHT = 24, DETAILS_HEIGHT = 70;
    static void row(GuiGraphics graphics, int width, int y, boolean selected, boolean hovered) {
        graphics.fill(10, y, width - 12, y + ROW_HEIGHT - 1, selected ? 0xFF2D4254 : hovered ? 0xFF263646 : 0xFF202C38);
    }
    static void details(GuiGraphics graphics, int width, int y) {
        graphics.fill(10, y, width - 12, y + DETAILS_HEIGHT - 1, 0xFF243444);
    }
    private InventoryListStyle() { }
}
