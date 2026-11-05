package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.view.NavigationSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Scrollable expanded route evidence. The snapshot is intentionally immutable while this view is open. */
public final class NavigationReportScreen extends Screen {
    private final Screen parent;
    private final List<Component> paragraphs;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;

    public NavigationReportScreen(Screen parent, NavigationSummary summary, Component destination) {
        super(Component.translatable("gui.litemfinder.navigation.details"));
        this.parent = parent;
        this.paragraphs = NavigationSummaryText.details(summary, destination);
    }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.back"), ignored -> onClose())
                .bounds(Math.max(8, width - 86), 8, 74, 20).build());
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component paragraph : paragraphs) wrapped.addAll(font.split(paragraph, Math.max(1, width - 32)));
        lines = List.copyOf(wrapped);
        scroll = Math.min(scroll, maxScroll());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xF0121921);
        graphics.drawString(font, title, 12, 14, 0xFFF2F4F6);
        graphics.enableScissor(8, 38, width - 8, height - 12);
        int lineHeight = font.lineHeight + 5;
        for (int index = 0; index < lines.size(); index++) {
            int y = 42 + index * lineHeight - scroll;
            if (y + lineHeight >= 38 && y < height - 12) graphics.drawString(font, lines.get(index), 16, y, 0xFFD7E3EC);
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int track = Math.max(1, height - 58);
            int thumb = Math.max(12, track * track / Math.max(track, lines.size() * lineHeight));
            int top = 42 + (track - thumb) * scroll / maxScroll();
            graphics.fill(width - 7, top, width - 4, top + thumb, 0xFF8CAFC4);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dy != 0) { scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy) * 32)); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        int delta = key == GLFW.GLFW_KEY_PAGE_DOWN ? Math.max(16, height - 70)
                : key == GLFW.GLFW_KEY_PAGE_UP ? -Math.max(16, height - 70)
                : key == GLFW.GLFW_KEY_DOWN ? 16 : key == GLFW.GLFW_KEY_UP ? -16 : 0;
        if (delta != 0) { scroll = Math.max(0, Math.min(maxScroll(), scroll + delta)); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    private int maxScroll() { return Math.max(0, lines.size() * (font.lineHeight + 5) - Math.max(1, height - 58)); }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
}
