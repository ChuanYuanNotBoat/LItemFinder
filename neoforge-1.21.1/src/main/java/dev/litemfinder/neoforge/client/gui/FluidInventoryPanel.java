package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.*;
import dev.litemfinder.core.view.FluidOverview;
import dev.litemfinder.core.view.FluidQuantityFormat;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Embedded lower inventory panel: the host owns the header, search box, navigation and active mode. */
final class FluidInventoryPanel {
    private final Screen owner;
    private final ItemFinderClientApi api;
    private final FluidVisualResolver visuals = new FluidVisualResolver();
    private ContainerId root;
    private List<Integer> path = List.of();
    private List<FluidOverview.Row> rows = List.of();
    private FluidKey expanded;
    private FluidOverview.Row hovered;
    private String search = "";
    private int scroll, top, bottom, width;
    private Button mapButton;

    FluidInventoryPanel(Screen owner, ItemFinderClientApi api, ContainerId root) { this.owner = owner; this.api = api; this.root = root; }
    void initialize(Consumer<Button> addWidget) {
        mapButton = Button.builder(Component.translatable("gui.litemfinder.map"), ignored -> openMap())
                .bounds(0, 0, 64, 20).build();
        mapButton.visible = false;
        addWidget.accept(mapButton);
    }
    void scope(ContainerId root, List<Integer> path) {
        if (Objects.equals(this.root, root) && this.path.equals(path)) return;
        this.root = root; this.path = List.copyOf(path); scroll = 0; expanded = null; refresh();
    }
    void search(String value) { search = value.strip().toLowerCase(Locale.ROOT); scroll = 0; refresh(); }
    void refresh() {
        rows = api.fluidOverview().filterSources(source -> (root == null || root.equals(source.root()))
                && source.itemPath().size() >= path.size() && source.itemPath().subList(0, path.size()).equals(path)).rows().stream()
                .filter(row -> search.isEmpty() || row.fluidId().contains(search) || visuals.resolve(row).name().toLowerCase(Locale.ROOT).contains(search)).toList();
        if (expanded != null && rows.stream().noneMatch(row -> row.key().equals(expanded))) expanded = null;
        clamp();
    }
    void hideControls() { if (mapButton != null) mapButton.visible = false; hovered = null; }
    Component summary() { return Component.translatable("gui.litemfinder.fluids.summary", rows.size()); }
    void openMap() {
        var minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.setScreen(ContainerMapScreen.forFluids(owner, api, expanded, root));
    }
    void render(GuiGraphics graphics, Font font, int width, int top, int bottom, int mouseX, int mouseY) {
        this.width = width; this.top = top; this.bottom = bottom; clamp(); hideControls();
        graphics.enableScissor(8, top, width - 8, bottom);
        int y = top - scroll;
        for (var row : rows) {
            boolean selected = row.key().equals(expanded);
            int rowHeight = InventoryListStyle.ROW_HEIGHT + (selected ? InventoryListStyle.DETAILS_HEIGHT : 0);
            if (y + rowHeight > top && y < bottom) {
                boolean over = mouseX >= 10 && mouseX < width - 12 && mouseY >= Math.max(y, top)
                        && mouseY < Math.min(y + rowHeight, bottom);
                InventoryListStyle.row(graphics, width, y, selected, over && mouseY < y + InventoryListStyle.ROW_HEIGHT);
                var visual = visuals.resolve(row);
                visual.icon(graphics, 15, y + 3);
                String count = FluidQuantityFormat.display(row.amount());
                graphics.drawString(font, font.plainSubstrByWidth(visual.name(), Math.max(24, width - 72 - font.width(count))), 37, y + 8, 0xFFE7EEF4);
                graphics.drawString(font, count, width - 19 - font.width(count), y + 8, 0xFFB9E3B7);
                if (selected) {
                    int detailY = y + InventoryListStyle.ROW_HEIGHT;
                    InventoryListStyle.details(graphics, width, detailY);
                    String capacity = row.capacity() > 0 ? FluidQuantityFormat.display(row.capacity())
                            : Component.translatable("gui.litemfinder.fluids.unknown_capacity").getString();
                    graphics.drawString(font, Component.translatable("gui.litemfinder.fluids.capacity", capacity), 17, detailY + 6, 0xFFE4EDF5);
                    String exact = row.amount() + " mB" + (row.capacity() > 0 ? " / " + row.capacity() + " mB" : "");
                    graphics.drawString(font, font.plainSubstrByWidth(exact, width - 42), 17, detailY + 21, 0xFFB8C6D2);
                    int barRight = width - 24, filled = row.capacity() == 0 ? 0
                            : (int) Math.round((barRight - 17) * Math.min(1, row.amount() / (double) row.capacity()));
                    graphics.fill(17, detailY + 36, barRight, detailY + 44, 0xFF38434B);
                    graphics.fill(17, detailY + 36, 17 + filled, detailY + 44, visual.color());
                    graphics.drawString(font, font.plainSubstrByWidth(SnapshotTimeFormatter.format(oldest(row)), width - 42), 17, detailY + 52, 0xFFB8C6D2);
                    mapButton.visible = detailY + 3 >= top && detailY + 23 <= bottom;
                    mapButton.active = row.sources().stream().anyMatch(source -> source.location() instanceof WorldLocation);
                    mapButton.setX(width - 84); mapButton.setY(detailY + 3);
                }
                if (over) hovered = row;
            }
            y += rowHeight;
        }
        graphics.disableScissor();
    }
    void tooltip(GuiGraphics graphics, Font font, int x, int y) {
        if (hovered != null) graphics.renderComponentTooltip(font, List.of(Component.literal(visuals.resolve(hovered).name()),
                Component.literal(hovered.amount() + " mB" + (hovered.capacity() > 0 ? " / " + hovered.capacity() + " mB" : "")),
                Component.literal(SnapshotTimeFormatter.format(oldest(hovered)))), x, y);
    }
    boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || x < 10 || x >= width - 12 || y < top || y >= bottom) return false;
        int rowY = top - scroll;
        for (var row : rows) {
            int height = InventoryListStyle.ROW_HEIGHT + (row.key().equals(expanded) ? InventoryListStyle.DETAILS_HEIGHT : 0);
            if (y >= rowY && y < rowY + height) {
                if (y < rowY + InventoryListStyle.ROW_HEIGHT) expanded = row.key().equals(expanded) ? null : row.key();
                clamp(); return true;
            }
            rowY += height;
        }
        return false;
    }
    boolean mouseScrolled(double y, double delta) {
        if (y < top || y >= bottom || delta == 0) return false;
        scroll -= (int) Math.signum(delta) * InventoryListStyle.ROW_HEIGHT * 2; clamp(); return true;
    }
    private void clamp() {
        int total = rows.size() * InventoryListStyle.ROW_HEIGHT + (expanded == null ? 0 : InventoryListStyle.DETAILS_HEIGHT);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - Math.max(1, bottom - top))));
    }
    private static java.time.Instant oldest(FluidOverview.Row row) { return row.sources().stream().map(FluidOverview.Source::observedAt).min(java.time.Instant::compareTo).orElseThrow(); }
}
