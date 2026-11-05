package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.*;
import dev.litemfinder.core.view.ContainerPreview;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Read-only recorded-content tooltip. Upgrade decorations never contribute to preview or inventory totals. */
public final class ContainerItemTooltip {
    private InventorySnapshot cached;
    private List<ContainerPreview.Entry> entries = List.of();
    private final Map<ItemKey, ItemStack> icons = new HashMap<>();

    void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY,
                Component name, String slot, long count, boolean container, InventorySnapshot contents) {
        render(graphics, font, screenWidth, screenHeight, mouseX, mouseY, name, slot, count, container, contents, Screen.hasShiftDown());
    }

    /** Explicit expansion also permits keyboard/accessibility hosts to reuse the same recorded preview. */
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY,
                       Component name, String slot, long count, boolean container, InventorySnapshot contents, boolean expand) {
        if (!Objects.equals(cached, contents)) {
            cached = contents;
            entries = contents == null ? List.of() : ContainerPreview.aggregate(contents);
        }
        boolean expanded = expand && contents != null;
        int cellWidth = Math.max(22, entries.stream().mapToInt(entry -> font.width(amount(entry.count())) + 6).max().orElse(22));
        int columns = Math.max(1, Math.min(Math.min(9, Math.max(1, entries.size())), (screenWidth - 32) / cellWidth));
        int width = Math.min(screenWidth - 16, Math.max(230, expanded ? columns * cellWidth + 16 : 230));
        List<Component> paragraphs = new ArrayList<>();
        paragraphs.add(count > 1 ? Component.translatable("gui.litemfinder.preview.name_count", name, amount(count)) : name);
        if (!slot.isBlank()) paragraphs.add(Component.translatable("gui.litemfinder.preview.slot", equipmentName(slot)));
        if (container) paragraphs.add(Component.translatable(contents == null ? "gui.litemfinder.preview.unknown"
                : expanded ? "gui.litemfinder.preview.contents" : "gui.litemfinder.preview.shift"));
        if (contents != null) {
            paragraphs.add(Component.translatable("gui.litemfinder.preview.open"));
            if (contents.container().metadata().containsKey(dev.litemfinder.core.snapshot.NestedInventoryObservation.RETAINED)) {
                paragraphs.add(Component.translatable("gui.litemfinder.preview.retained"));
            }
            if (contents.capturedAt().isBefore(Instant.now().minus(Duration.ofMinutes(30)))) {
                paragraphs.add(Component.translatable("gui.litemfinder.preview.stale"));
            }
        }
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        paragraphs.forEach(line -> lines.addAll(font.split(line, Math.max(1, width - 16))));
        List<String> upgrades = new ArrayList<>();
        if (expanded) for (int index = 0; index < 16; index++) {
            String id = contents.container().metadata().get("previewUpgrade." + index);
            if (id != null) upgrades.add(id);
        }
        int header = lines.size() * (font.lineHeight + 2) + 12;
        int upgradeHeight = upgrades.isEmpty() ? 0 : 32;
        int rowLimit = Math.max(1, Math.min(8, (screenHeight - header - upgradeHeight - 38) / 23));
        int shown = expanded ? Math.min(entries.size(), columns * rowLimit) : 0;
        int rows = (shown + columns - 1) / columns;
        int height = header + upgradeHeight + rows * 23 + (shown < entries.size() && expanded ? 13 : 0);
        int x = Math.max(8, Math.min(mouseX + 12, screenWidth - width - 8));
        int y = Math.max(8, Math.min(mouseY + 8, screenHeight - height - 8));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFFB3BDCA);
        graphics.fill(x, y, x + width, y + height, 0xF0121720);
        int lineY = y + 6;
        for (var line : lines) { graphics.drawString(font, line, x + 8, lineY, 0xFFE3EAF2); lineY += font.lineHeight + 2; }
        int top = y + header;
        if (!upgrades.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.litemfinder.preview.upgrades"), x + 8, top, 0xFFEDD57C);
            for (int index = 0; index < Math.min(upgrades.size(), (width - 16) / 18); index++) {
                graphics.renderItem(icon(ItemKey.parse(upgrades.get(index))), x + 8 + index * 18, top + 12);
            }
            top += upgradeHeight;
        }
        for (int index = 0; index < shown; index++) {
            var entry = entries.get(index);
            int itemX = x + 8 + index % columns * cellWidth;
            int itemY = top + index / columns * 23;
            graphics.renderItem(icon(entry.item()), itemX, itemY);
            String amount = amount(entry.count());
            graphics.drawString(font, amount, itemX + cellWidth - font.width(amount) - 2, itemY + 13, 0xFFFFFFFF);
        }
        if (shown < entries.size() && expanded) graphics.drawString(font,
                Component.translatable("gui.litemfinder.preview.more", entries.size() - shown), x + 8, top + rows * 23, 0xFFB6CDD9);
        graphics.pose().popPose();
    }

    private ItemStack icon(ItemKey item) {
        return icons.computeIfAbsent(item, key -> {
            ResourceLocation id = ResourceLocation.tryParse(key.itemId().toString());
            return id != null && BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
        });
    }
    private static String amount(long count) { return NumberFormat.getIntegerInstance(Locale.ROOT).format(count); }
    static Component equipmentName(String value) {
        String key = value.substring(value.lastIndexOf('/') + 1);
        int colon = key.lastIndexOf(':');
        String type = colon < 0 ? key : key.substring(0, colon);
        String translation = "curios.identifier." + type;
        if (net.minecraft.locale.Language.getInstance().has(translation)) return Component.translatable(translation);
        String own = "gui.litemfinder.player.slot." + type;
        return net.minecraft.locale.Language.getInstance().has(own) ? Component.translatable(own) : Component.literal(value);
    }
}
