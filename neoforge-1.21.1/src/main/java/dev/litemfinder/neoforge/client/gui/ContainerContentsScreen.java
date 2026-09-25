package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.view.ContainerOverview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only slot and nested-container preview opened from the container map. */
public final class ContainerContentsScreen extends Screen {

    private static final Duration STALE_HINT_AFTER = Duration.ofMinutes(30);
    private static final int HEADER_HEIGHT = 54;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SLOT_SIZE = 20;
    private static final int MAX_COLUMNS = 9;
    private static final int STALE_INDICATOR_SIZE = 11;

    private final Screen parent;
    private final ItemFinderClientApi api;
    private final ContainerId rootId;
    private final List<Integer> nestedPath = new ArrayList<>();
    private final Map<ItemKey, ItemVisual> visuals = new HashMap<>();
    private ContainerOverview.RootRow root;
    private List<SlotHit> slotHits = List.of();
    private Button upButton;
    private int scrollRows;
    private int ticks;

    public ContainerContentsScreen(Screen parent, ItemFinderClientApi api, ContainerId rootId) {
        super(Component.translatable("gui.litemfinder.contents.title"));
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.rootId = Objects.requireNonNull(rootId, "rootId must not be null");
    }

    @Override
    protected void init() {
        upButton = addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.contents.up"),
                        ignored -> moveUp())
                .bounds(Math.max(12, width - 132), 24, 62, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.back"), ignored -> onClose())
                .bounds(width - 64, 24, 52, 20).build());
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) {
            refresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xF0121921);
        graphics.fill(0, 0, width, 52, 0xFF1C2834);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, 0xFF1C2834);
        graphics.drawString(font, title, 12, 8, 0xFFF2F4F6);

        PreviewNode node = currentNode();
        if (node == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.litemfinder.contents.missing"),
                    width / 2, height / 2 - 5, 0xFFFFC875);
            slotHits = List.of();
            updateButtons();
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        String breadcrumb = breadcrumb(node);
        graphics.drawString(font, font.plainSubstrByWidth(breadcrumb, Math.max(30, width - 156)),
                12, 31, 0xFFB8C6D2);
        String summary = Component.translatable("gui.litemfinder.contents.summary",
                node.contents().size(), node.slotCount()).getString();
        boolean stale = node.observedAt().isBefore(Instant.now().minus(STALE_HINT_AFTER));
        int staleX = Math.max(12, width - 23);
        int staleY = 54;
        int summaryWidth = stale ? Math.max(30, staleX - 18) : width - 24;
        graphics.drawString(font, font.plainSubstrByWidth(
                        node.container().type().id() + " · " + summary, summaryWidth),
                12, 56, 0xFFD8E7F2);
        if (stale) {
            renderStaleIndicator(graphics, staleX, staleY);
        }

        renderSlots(graphics, node, mouseX, mouseY);
        updateButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (stale && contains(mouseX, mouseY, staleX, staleY,
                staleX + STALE_INDICATOR_SIZE, staleY + STALE_INDICATOR_SIZE)) {
            graphics.renderTooltip(font, Component.translatable("gui.litemfinder.contents.stale",
                    SnapshotTimeFormatter.format(node.observedAt())), mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The screen background is rendered before widgets in render().
    }

    private void renderSlots(GuiGraphics graphics, PreviewNode node, int mouseX, int mouseY) {
        int columns = columns(node.slotCount());
        int totalRows = rows(node.slotCount(), columns);
        clampScroll(totalRows);
        int gridWidth = columns * SLOT_SIZE;
        int gridX = Math.max(8, (width - gridWidth) / 2);
        int gridTop = HEADER_HEIGHT + 15;
        int gridBottom = Math.max(gridTop + 1, height - FOOTER_HEIGHT);
        Map<Integer, SlotSnapshot> occupied = new HashMap<>();
        node.contents().forEach(slot -> occupied.put(slot.slot(), slot));
        List<SlotHit> hits = new ArrayList<>();

        graphics.enableScissor(0, gridTop, width, gridBottom);
        for (int slotIndex = 0; slotIndex < node.slotCount(); slotIndex++) {
            int row = slotIndex / columns;
            int column = slotIndex % columns;
            int x = gridX + column * SLOT_SIZE;
            int y = gridTop + (row - scrollRows) * SLOT_SIZE;
            if (y + SLOT_SIZE <= gridTop || y >= gridBottom) {
                continue;
            }
            SlotSnapshot slot = occupied.get(slotIndex);
            boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE
                    && mouseY >= y && mouseY < y + SLOT_SIZE;
            int border = slot != null && slot.nestedContainer().isPresent()
                    ? 0xFF72E7B2 : hovered ? 0xFFFFD36B : 0xFF536675;
            graphics.fill(x, y, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, border);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2,
                    slot == null ? 0xFF1B2731 : 0xFF263746);
            if (slot == null) {
                continue;
            }
            ItemVisual visual = visualFor(slot.stack().item());
            if (!visual.stack().isEmpty()) {
                graphics.renderItem(visual.stack(), x + 2, y + 2);
                graphics.renderItemDecorations(font, visual.stack(), x + 2, y + 2,
                        shortCount(slot.stack().count()));
            }
            hits.add(new SlotHit(x, y, x + SLOT_SIZE, y + SLOT_SIZE, slot, visual));
        }
        graphics.disableScissor();
        slotHits = List.copyOf(hits);

        SlotHit hovered = hits.stream().filter(hit -> hit.contains(mouseX, mouseY)).findFirst().orElse(null);
        String footer = hovered == null
                ? Component.translatable("gui.litemfinder.contents.hint").getString()
                : slotDescription(hovered);
        graphics.drawString(font, font.plainSubstrByWidth(footer, width - 24),
                12, height - 20, hovered != null && hovered.slot().nestedContainer().isPresent()
                        ? 0xFF72E7B2 : 0xFFB8C6D2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (SlotHit hit : slotHits) {
            if (hit.contains(mouseX, mouseY) && hit.slot().nestedContainer().isPresent()) {
                nestedPath.add(hit.slot().slot());
                scrollRows = 0;
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && mouseY >= HEADER_HEIGHT + 15 && mouseY < height - FOOTER_HEIGHT) {
            scrollRows -= (int) Math.signum(deltaY) * 2;
            PreviewNode node = currentNode();
            if (node != null) {
                clampScroll(rows(node.slotCount(), columns(node.slotCount())));
            }
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
        root = api.containerOverview().roots().stream()
                .filter(row -> row.container().id().equals(rootId))
                .findFirst().orElse(null);
        while (!nestedPath.isEmpty() && currentNode() == null) {
            nestedPath.removeLast();
        }
        PreviewNode node = currentNode();
        if (node != null) {
            clampScroll(rows(node.slotCount(), columns(node.slotCount())));
        } else {
            scrollRows = 0;
        }
        updateButtons();
    }

    private PreviewNode currentNode() {
        if (root == null) {
            return null;
        }
        PreviewNode node = new PreviewNode(root.container(), root.observedAt(), root.slots(), root.contents());
        for (int parentSlot : nestedPath) {
            SlotSnapshot slot = node.contents().stream()
                    .filter(candidate -> candidate.slot() == parentSlot)
                    .findFirst().orElse(null);
            InventorySnapshot nested = slot == null ? null : slot.nestedContainer().orElse(null);
            if (nested == null) {
                return null;
            }
            node = new PreviewNode(nested.container(), nested.capturedAt(), nested.slotCount(), nested.slots());
        }
        return node;
    }

    private void moveUp() {
        if (!nestedPath.isEmpty()) {
            nestedPath.removeLast();
            scrollRows = 0;
            updateButtons();
        }
    }

    private void updateButtons() {
        if (upButton != null) {
            upButton.active = !nestedPath.isEmpty();
        }
    }

    private String breadcrumb(PreviewNode node) {
        StringBuilder result = new StringBuilder(rootId.value());
        for (int parentSlot : nestedPath) {
            result.append(" / #").append(parentSlot + 1);
        }
        if (!nestedPath.isEmpty()) {
            result.append(" · ").append(node.container().type().id().path());
        }
        return result.toString();
    }

    private String slotDescription(SlotHit hit) {
        StringBuilder label = new StringBuilder("#").append(hit.slot().slot() + 1).append(" · ")
                .append(hit.visual().name())
                .append(" · ").append(hit.slot().stack().item().itemId())
                .append(" ×").append(hit.slot().stack().count());
        String variant = hit.slot().stack().item().variant();
        if (!variant.isEmpty()) {
            label.append(" [").append(variant.substring(Math.max(0, variant.length() - 8))).append(']');
        }
        if (hit.slot().nestedContainer().isPresent()) {
            label.append(" · ").append(Component.translatable("gui.litemfinder.contents.open_nested").getString());
        }
        return label.toString();
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

    private ItemVisual visualFor(ItemKey key) {
        return visuals.computeIfAbsent(key, ignored -> {
            ResourceLocation itemId = ResourceLocation.parse(key.itemId().toString());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == Items.AIR && !itemId.toString().equals("minecraft:air")) {
                return new ItemVisual(ItemStack.EMPTY, key.itemId().toString());
            }
            ItemStack stack = new ItemStack(item);
            return new ItemVisual(stack, stack.getHoverName().getString());
        });
    }

    private void clampScroll(int totalRows) {
        int viewportRows = Math.max(1,
                (height - FOOTER_HEIGHT - (HEADER_HEIGHT + 15)) / SLOT_SIZE);
        scrollRows = Math.max(0, Math.min(scrollRows, Math.max(0, totalRows - viewportRows)));
    }

    private static int columns(int slotCount) {
        return Math.max(1, Math.min(MAX_COLUMNS, slotCount));
    }

    private static int rows(int slotCount, int columns) {
        return (slotCount + columns - 1) / columns;
    }

    private static String shortCount(long count) {
        if (count < 1_000) {
            return Long.toString(count);
        }
        if (count < 1_000_000) {
            return count / 1_000 + "k";
        }
        if (count < 1_000_000_000) {
            return count / 1_000_000 + "m";
        }
        return count / 1_000_000_000 + "b";
    }

    private record PreviewNode(ContainerRecord container, Instant observedAt, int slotCount,
                               List<SlotSnapshot> contents) {
    }

    private record ItemVisual(ItemStack stack, String name) {
    }

    private record SlotHit(int left, int top, int right, int bottom,
                           SlotSnapshot slot, ItemVisual visual) {

        private boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
