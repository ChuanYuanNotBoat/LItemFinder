package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.view.FluidOverview;
import dev.litemfinder.neoforge.client.view.FluidQuantityFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Fluid sources keep their actual root and item path; carried tanks never become artificial map stops. */
final class FluidSourcesScreen extends Screen {
    private static final int TOP = 32, ROW = 32, FOOTER = 24;
    private final Screen parent;
    private final ItemFinderClientApi api;
    private final ContainerId rootFilter;
    private final String fluidId, variant;
    private List<FluidOverview.Source> sources = List.of();
    private Button mapButton;
    private int scroll, ticks;

    FluidSourcesScreen(Screen parent, ItemFinderClientApi api, ContainerId rootFilter, String fluidId, String variant, String name) {
        super(Component.translatable("gui.litemfinder.fluids.source_title", name));
        this.parent = parent; this.api = api; this.rootFilter = rootFilter; this.fluidId = fluidId; this.variant = variant;
    }
    @Override protected void init() {
        mapButton = addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.map"), ignored -> {
            if (minecraft != null) minecraft.setScreen(ContainerMapScreen.forSources(this, api, worldRoots(), null));
        }).bounds(width - 164, 4, 92, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.back"), ignored -> onClose())
                .bounds(width - 64, 4, 52, 20).build());
        refresh();
    }
    private Set<ContainerId> worldRoots() {
        return sources.stream().filter(source -> source.location() instanceof WorldLocation)
                .map(FluidOverview.Source::root).collect(Collectors.toUnmodifiableSet());
    }
    private void refresh() {
        sources = FluidOverview.from(api.containerOverview()).rows().stream()
                .filter(row -> row.fluidId().equals(fluidId) && row.variant().equals(variant))
                .flatMap(row -> row.sources().stream()).filter(source -> rootFilter == null || rootFilter.equals(source.root()))
                .sorted(java.util.Comparator.comparing(FluidOverview.Source::root)
                        .thenComparing(source -> source.itemPath().toString()).thenComparingInt(source -> source.tank().index())).toList();
        mapButton.active = !worldRoots().isEmpty();
        clamp();
    }
    @Override public void tick() { if (++ticks % 20 == 0) refresh(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0121921);
        graphics.fill(0, 0, width, TOP - 3, 0xFF1C2834);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(20, width - 184)), 12, 10, 0xFFF2F4F6);
        FluidOverview.Source hovered = null;
        graphics.enableScissor(8, TOP, width - 8, height - FOOTER);
        for (int i = 0; i < sources.size(); i++) {
            int y = TOP + i * ROW - scroll;
            if (y + ROW <= TOP || y >= height - FOOTER) continue;
            var source = sources.get(i);
            graphics.fill(10, y, width - 12, y + ROW - 2, 0xFF202C38);
            String where = source.location() instanceof WorldLocation world
                    ? world.dimension() + " · " + world.x() + ", " + world.y() + ", " + world.z()
                    : Component.translatable("gui.litemfinder.plan.logical").getString();
            String amount = FluidQuantityFormat.display(source.tank().amount()) + (source.tank().capacity() > 0
                    ? " / " + FluidQuantityFormat.display(source.tank().capacity()) : "");
            graphics.drawString(font, font.plainSubstrByWidth(where, Math.max(20, width - font.width(amount) - 42)), 16, y + 5, 0xFFD8E7F2);
            graphics.drawString(font, amount, width - 20 - font.width(amount), y + 5, 0xFFB8E9B2);
            String path = source.itemPath().isEmpty() ? Component.translatable("gui.litemfinder.fluids.block_tank").getString()
                    : Component.translatable("gui.litemfinder.fluids.item_path", source.itemPath().stream()
                    .map(slot -> Integer.toString(slot + 1)).collect(Collectors.joining(" → "))).getString();
            graphics.drawString(font, font.plainSubstrByWidth(path, width - 40), 16, y + 18, 0xFFB8C6D2);
            if (mouseX >= 10 && mouseX < width - 12 && mouseY >= Math.max(TOP, y)
                    && mouseY < Math.min(height - FOOTER, y + ROW - 2)) hovered = source;
        }
        graphics.disableScissor();
        graphics.fill(0, height - FOOTER, width, height, 0xFF1C2834);
        graphics.drawString(font, Component.translatable("gui.litemfinder.fluids.source_hint"), 12, height - 16, 0xFFB8C6D2);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) graphics.renderComponentTooltip(font, List.of(
                Component.literal(hovered.tank().amount() + " mB" + (hovered.tank().capacity() > 0
                        ? " / " + hovered.tank().capacity() + " mB" : "")),
                Component.literal(SnapshotTimeFormatter.format(hovered.observedAt())),
                Component.translatable(hovered.location() instanceof WorldLocation
                        ? "gui.litemfinder.fluids.navigate" : "gui.litemfinder.fluids.carried")), mouseX, mouseY);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= 10 && x < width - 12 && y >= TOP && y < height - FOOTER) {
            int index = (int) ((y - TOP + scroll) / ROW);
            if (index >= 0 && index < sources.size() && minecraft != null) {
                var source = sources.get(index);
                minecraft.setScreen(source.location() instanceof WorldLocation
                        ? ContainerMapScreen.forSources(this, api, Set.of(source.root()), source.root())
                        : new ContainerContentsScreen(this, api, source.root()));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (y >= TOP && y < height - FOOTER && dy != 0) { scroll -= (int) Math.signum(dy) * ROW; clamp(); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }
    private void clamp() { scroll = Math.max(0, Math.min(scroll, Math.max(0, sources.size() * ROW - (height - FOOTER - TOP)))); }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float tick) { }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
