package dev.litemfinder.neoforge.client.gui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.view.ContainerOverview;
import dev.litemfinder.neoforge.client.view.FluidOverview;
import dev.litemfinder.neoforge.client.view.FluidQuantityFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only fluid tab. Item quantities and retrieval drafts are unaffected by fluid volumes. */
public final class FluidOverviewScreen extends Screen {
    private static final int TOP = 54;
    private static final int ROW_HEIGHT = 26;
    private final Screen parent;
    private final ItemFinderClientApi api;
    private final ContainerId root;
    private final Map<String, Visual> visuals = new HashMap<>();
    private List<FluidOverview.Row> rows = List.of();
    private EditBox search;
    private int scroll;
    private int ticks;
    private String searchText = "";

    public FluidOverviewScreen(Screen parent, ItemFinderClientApi api, ContainerId root) {
        super(Component.translatable("gui.litemfinder.fluids.title"));
        this.parent = parent;
        this.api = api;
        this.root = root;
    }

    @Override
    protected void init() {
        search = addRenderableWidget(new EditBox(font, 116, 27, Math.max(70, width - 128), 19,
                Component.translatable("gui.litemfinder.fluids.search")));
        search.setHint(Component.translatable("gui.litemfinder.fluids.search"));
        search.setMaxLength(120);
        search.setValue(searchText);
        search.setResponder(value -> { searchText = value; scroll = 0; refresh(); });
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.back"), ignored -> onClose())
                .bounds(width - 64, 3, 52, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.tabs.items"), ignored -> onClose())
                .bounds(12, 26, 46, 20).build());
        var fluids = addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.tabs.fluids"), ignored -> { })
                .bounds(62, 26, 46, 20).build());
        fluids.active = false;
        refresh();
    }

    @Override
    public void tick() { if (++ticks % 20 == 0) refresh(); }

    private void refresh() {
        var overview = api.containerOverview();
        if (root != null) overview = new ContainerOverview(overview.roots().stream()
                .filter(row -> row.container().id().equals(root)).toList());
        String needle = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        rows = FluidOverview.from(overview).rows().stream().filter(row -> needle.isEmpty()
                || row.fluidId().contains(needle) || visual(row).name().toLowerCase(Locale.ROOT).contains(needle)).toList();
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xF0121921);
        graphics.fill(0, 0, width, TOP - 4, 0xFF1C2834);
        graphics.drawString(font, title, 12, 8, 0xFFF2F4F6);
        FluidOverview.Row hovered = null;
        graphics.enableScissor(8, TOP, width - 8, height - 27);
        int y = TOP - scroll;
        for (var row : rows) {
            if (y + ROW_HEIGHT > TOP && y < height - 27) {
                Visual visual = visual(row);
                graphics.fill(10, y, width - 12, y + ROW_HEIGHT - 2, 0xFF202C38);
                int barLeft = 16, barRight = width - 18;
                graphics.fill(barLeft, y + 2, barRight, y + 23, 0xFF38434B);
                double ratio = row.capacity() == 0 ? 1 : Math.min(1, row.amount() / (double) row.capacity());
                int filled = (int) Math.round((barRight - barLeft) * ratio);
                graphics.fill(barLeft, y + 2, barLeft + filled, y + 23, visual.color());
                if (visual.sprite() != null && filled > 0) {
                    graphics.enableScissor(barLeft, Math.max(TOP, y + 2), barLeft + filled, Math.min(height - 27, y + 23));
                    for (int tileX = barLeft; tileX < barLeft + filled; tileX += 16) {
                        for (int tileY = y + 2; tileY < y + 23; tileY += 16) {
                            int color = visual.color();
                            graphics.blit(tileX, tileY, 0, 16, 16, visual.sprite(),
                                    ((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1f);
                        }
                    }
                    graphics.disableScissor();
                    graphics.enableScissor(8, TOP, width - 8, height - 27);
                }
                String amount = FluidQuantityFormat.display(row.amount()) + (row.capacity() > 0
                        ? " / " + FluidQuantityFormat.display(row.capacity()) : "");
                graphics.drawString(font, font.plainSubstrByWidth(visual.name(), Math.max(20,
                        barRight - barLeft - font.width(amount) - 14)), barLeft + 4, y + 8, 0xFFFFFFFF);
                graphics.drawString(font, amount, Math.max(barLeft + 4, barRight - font.width(amount) - 4), y + 8, 0xFFFFFFFF);
                if (mouseX >= 10 && mouseX < width - 12 && mouseY >= Math.max(TOP, y)
                        && mouseY < Math.min(height - 27, y + ROW_HEIGHT - 2)) hovered = row;
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        graphics.fill(0, height - 27, width, height, 0xFF1C2834);
        graphics.drawString(font, Component.translatable("gui.litemfinder.fluids.summary", rows.size()), 12, height - 18, 0xFFBDCBD7);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            var observed = hovered.sources().stream().map(FluidOverview.Source::observedAt).min(java.time.Instant::compareTo).orElseThrow();
            graphics.renderComponentTooltip(font, List.of(Component.literal(visual(hovered).name()),
                    Component.literal(hovered.amount() + " mB" + (hovered.capacity() > 0 ? " / " + hovered.capacity() + " mB" : "")),
                    Component.translatable("gui.litemfinder.fluids.sources", hovered.sources().size()),
                    Component.literal(SnapshotTimeFormatter.format(observed)),
                    Component.translatable("gui.litemfinder.fluids.open_sources")), mouseX, mouseY);
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= 10 && x < width - 12 && y >= TOP && y < height - 27) {
            int index = (int) ((y - TOP + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size() && minecraft != null) {
                var row = rows.get(index);
                minecraft.setScreen(new FluidSourcesScreen(this, api, root, row.fluidId(), row.variant(), visual(row).name()));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (y >= TOP && y < height - 27 && dy != 0) {
            scroll -= (int) Math.signum(dy) * ROW_HEIGHT;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }
    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() * ROW_HEIGHT - (height - 27 - TOP)))); }

    private Visual visual(FluidOverview.Row row) {
        String data = row.sources().getFirst().tank().encoded();
        return visuals.computeIfAbsent(data, ignored -> {
            try {
                if (minecraft != null && minecraft.level != null) {
                    var fluid = FluidStack.CODEC.parse(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                            JsonParser.parseString(data)).getOrThrow();
                    var extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
                    var texture = extensions.getStillTexture(fluid);
                    var sprite = texture == null ? null : minecraft.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(texture);
                    return new Visual(fluid.getFluid().getFluidType().getDescription(fluid).getString(),
                            extensions.getTintColor(fluid) | 0xFF000000, sprite);
                }
            } catch (RuntimeException | LinkageError ignoredFailure) { }
            return new Visual(row.fluidId(), 0xFF477A96, null);
        });
    }
    private record Visual(String name, int color, TextureAtlasSprite sprite) { }
}
