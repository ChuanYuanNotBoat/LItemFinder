package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.view.AcquisitionDraft;
import dev.litemfinder.neoforge.client.view.AcquisitionPlan;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only source allocation for the current session's exact-variant requests. */
public final class AcquisitionPlanScreen extends Screen {

    private static final Duration STALE_HINT_AFTER = Duration.ofMinutes(30);
    private static final int STALE_INDICATOR_SIZE = 11;

    private final Screen parent;
    private final ItemFinderClientApi api;
    private final AcquisitionDraft draft;
    private AcquisitionPlan plan = new AcquisitionPlan(List.of(), Map.of());
    private List<Line> lines = List.of();
    private boolean planChanged;
    private int scroll;
    private int ticks;

    public AcquisitionPlanScreen(Screen parent, ItemFinderClientApi api, AcquisitionDraft draft) {
        super(Component.translatable("gui.litemfinder.plan.title"));
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.draft = Objects.requireNonNull(draft, "draft must not be null");
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.back"), ignored -> onClose())
                .bounds(width - 86, 12, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.refresh"), ignored -> refresh())
                .bounds(width - 172, 12, 78, 20).build());
        refresh();
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            refresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xF0121921);
        graphics.fill(0, 0, width, 42, 0xFF1C2834);
        graphics.drawString(font, title, 12, 17, 0xFFF2F4F6);
        if (planChanged) {
            graphics.drawString(font, Component.translatable("gui.litemfinder.plan.changed"),
                    12, height - 19, 0xFFFFC875);
        }
        graphics.enableScissor(8, 45, width - 8, height - 26);
        int y = 48 - scroll;
        Component hoveredTooltip = null;
        for (Line line : lines) {
            if (y + 15 >= 45 && y <= height - 26) {
                int indicatorX = width - 23;
                int textWidth = line.tooltip() == null ? width - 27 : Math.max(30, indicatorX - 19);
                graphics.drawString(font, font.plainSubstrByWidth(line.text(), textWidth),
                        14, y, line.color());
                if (line.tooltip() != null) {
                    renderStaleIndicator(graphics, indicatorX, y - 1);
                    if (contains(mouseX, mouseY, indicatorX, y - 1,
                            indicatorX + STALE_INDICATOR_SIZE, y - 1 + STALE_INDICATOR_SIZE)) {
                        hoveredTooltip = line.tooltip();
                    }
                }
            }
            y += 17;
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredTooltip != null) {
            graphics.renderTooltip(font, hoveredTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Prevent Screen.render from blurring this screen's custom text a second time.
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseY >= 45 && mouseY < height - 26 && deltaY != 0) {
            scroll -= (int) Math.signum(deltaY) * 34;
            int max = Math.max(0, lines.size() * 17 - Math.max(1, height - 74));
            scroll = Math.max(0, Math.min(scroll, max));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void refresh() {
        AcquisitionPlan latest = api.plan(draft.selections());
        if (ticks > 0 && !latest.equals(plan)) {
            planChanged = true;
        }
        plan = latest;
        List<Line> result = new ArrayList<>();
        if (draft.selections().isEmpty()) {
            result.add(new Line(Component.translatable("gui.litemfinder.plan.empty").getString(),
                    0xFFB8C6D2, null));
        } else {
            draft.selections().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(request -> {
                ItemKey item = request.getKey();
                result.add(new Line(itemLabel(item) + " ×" + request.getValue(), 0xFFF2F4F6, null));
                plan.picks().stream().filter(pick -> pick.item().equals(item)).forEach(pick -> {
                    boolean oldSnapshot = pick.source().observedAt()
                            .isBefore(Instant.now().minus(STALE_HINT_AFTER));
                    Component staleTooltip = oldSnapshot
                            ? Component.translatable("gui.litemfinder.plan.stale",
                            SnapshotTimeFormatter.format(pick.source().observedAt())) : null;
                    result.add(new Line("  ← " + sourceLabel(pick.source()) + " ×" + pick.count(),
                            0xFFB9E3B7, staleTooltip));
                });
                long missing = plan.missing().getOrDefault(item, 0L);
                if (missing > 0) {
                    result.add(new Line("  " + Component.translatable("gui.litemfinder.plan.missing", missing)
                            .getString(), 0xFFFFC875, null));
                }
                result.add(new Line("", 0xFFFFFFFF, null));
            });
        }
        lines = List.copyOf(result);
    }

    private void renderStaleIndicator(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + STALE_INDICATOR_SIZE, y + STALE_INDICATOR_SIZE, 0xFFFFC875);
        graphics.fill(x + 1, y + 1, x + STALE_INDICATOR_SIZE - 1,
                y + STALE_INDICATOR_SIZE - 1, 0xFF4A3518);
        graphics.drawCenteredString(font, "!", x + STALE_INDICATOR_SIZE / 2, y + 1, 0xFFFFD36B);
    }

    private static boolean contains(double mouseX, double mouseY,
                                    int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static String itemLabel(ItemKey item) {
        if (!item.hasVariant()) {
            return item.itemId().toString();
        }
        String variant = item.variant();
        return item.itemId() + " [" + variant.substring(Math.max(0, variant.length() - 8)) + "]";
    }

    private static String sourceLabel(StorageEntry entry) {
        String position;
        if (entry.rootContainer().location() instanceof WorldLocation world) {
            position = world.dimension() + " " + world.x() + "," + world.y() + "," + world.z();
        } else {
            position = Component.translatable("gui.litemfinder.plan.logical").getString();
        }
        StringBuilder label = new StringBuilder(position)
                .append(" · ").append(entry.rootContainer().type().id().path());
        entry.path().hops().forEach(hop -> label.append(" → ").append(hop.parentSlot()));
        return label.append(" #").append(entry.slot()).toString();
    }

    private record Line(String text, int color, Component tooltip) {
    }
}
