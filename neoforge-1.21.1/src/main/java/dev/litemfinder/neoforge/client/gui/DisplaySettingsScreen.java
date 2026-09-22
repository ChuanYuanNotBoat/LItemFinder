package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.neoforge.client.view.QuantityFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings-only quantity style controls; rows never contain display toggles. */
public final class DisplaySettingsScreen extends Screen {

    private final Screen parent;
    private Button overviewButton;
    private Button editorButton;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.translatable("gui.litemfinder.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = Math.max(16, (width - 260) / 2);
        int top = Math.max(40, height / 2 - 50);
        overviewButton = addRenderableWidget(Button.builder(modeLabel("overview", ClientDisplayConfig.overviewMode()),
                ignored -> {
                    ClientDisplayConfig.setOverviewMode(next(ClientDisplayConfig.overviewMode()));
                    overviewButton.setMessage(modeLabel("overview", ClientDisplayConfig.overviewMode()));
                }).bounds(left, top, 260, 22).build());
        editorButton = addRenderableWidget(Button.builder(modeLabel("editor", ClientDisplayConfig.editorMode()),
                ignored -> {
                    ClientDisplayConfig.setEditorMode(next(ClientDisplayConfig.editorMode()));
                    editorButton.setMessage(modeLabel("editor", ClientDisplayConfig.editorMode()));
                }).bounds(left, top + 30, 260, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.back"), ignored -> onClose())
                .bounds(left, top + 72, 260, 22).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0111720);
        graphics.drawCenteredString(font, title, width / 2, Math.max(16, height / 2 - 78), 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.litemfinder.settings.hint"),
                width / 2, Math.min(height - 22, height / 2 + 62), 0xA8B8C8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static QuantityFormat.Mode next(QuantityFormat.Mode current) {
        QuantityFormat.Mode[] values = QuantityFormat.Mode.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static Component modeLabel(String area, QuantityFormat.Mode mode) {
        return Component.translatable("gui.litemfinder.settings." + area)
                .append(": ")
                .append(Component.translatable("gui.litemfinder.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)));
    }
}
