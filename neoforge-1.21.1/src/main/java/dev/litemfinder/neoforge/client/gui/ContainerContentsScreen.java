package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.core.model.WorldLocation;
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
    private static final int CONTENT_TOP = HEADER_HEIGHT + 20;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SLOT_SIZE = ContainerSlotLayout.SLOT_SIZE;
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
    private ContainerRecord layoutContainer;
    private ContainerSlotLayout slotLayout;
    private java.util.Set<Integer> observedSlots;

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
        var itemsTab = addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.tabs.items"), ignored -> { })
                .bounds(100, 3, 46, 20).build());
        itemsTab.active = false;
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.tabs.fluids"), ignored -> {
            if (minecraft != null) minecraft.setScreen(new FluidOverviewScreen(this, api, rootId));
        }).bounds(150, 3, 46, 20).build());
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
        if (node.container().metadata().containsKey("captureCoverage")) {
            summary += " · " + Component.translatable("gui.litemfinder.contents.partial").getString();
        }
        boolean stale = node.observedAt().isBefore(Instant.now().minus(STALE_HINT_AFTER));
        int staleX = Math.max(12, width - 23);
        int staleY = 54;
        int summaryWidth = stale ? Math.max(30, staleX - 18) : width - 24;
        graphics.drawString(font, font.plainSubstrByWidth(
                        summary, summaryWidth),
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
        ContainerSlotLayout layout = layout(node);
        clampScroll(node);
        int gridTop = CONTENT_TOP;
        int gridBottom = Math.max(gridTop + 1, height - FOOTER_HEIGHT);
        Map<Integer, SlotSnapshot> occupied = new HashMap<>();
        node.contents().forEach(slot -> occupied.put(slot.slot(), slot));
        List<SlotHit> hits = new ArrayList<>();

        graphics.enableScissor(0, gridTop, width, gridBottom);
        for (var cell : layout.cells(width, gridTop, gridBottom, scrollRows)) {
            int x = cell.x();
            int y = cell.y();
            SlotSnapshot slot = occupied.get(cell.slot());
            boolean hovered = mouseX >= x && mouseX < x + cell.size()
                    && mouseY >= Math.max(y, gridTop) && mouseY < Math.min(y + cell.size(), gridBottom);
            int border = slot != null && slot.nestedContainer().isPresent()
                    ? 0xFF72E7B2 : hovered ? 0xFFFFD36B : 0xFF536675;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(cell.scale(), cell.scale(), 1);
            graphics.fill(0, 0, SLOT_SIZE, SLOT_SIZE, border);
            graphics.fill(1, 1, SLOT_SIZE - 1, SLOT_SIZE - 1,
                    slot == null ? 0xFF1B2731 : 0xFF263746);
            if (slot == null) {
                if (observedSlots != null && !observedSlots.contains(cell.slot())) {
                    graphics.drawCenteredString(font, "?", SLOT_SIZE / 2, 5, 0xFF8C9AA6);
                }
                graphics.pose().popPose();
                continue;
            }
            ItemVisual visual = visualFor(slot.stack().item());
            if (!visual.stack().isEmpty()) {
                graphics.renderItem(visual.stack(), 1, 1);
                graphics.renderItemDecorations(font, visual.stack(), 1, 1,
                        shortCount(slot.stack().count()));
            }
            graphics.pose().popPose();
            hits.add(new SlotHit(x, Math.max(y, gridTop), x + cell.size(),
                    Math.min(y + cell.size(), gridBottom), slot, visual));
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
        if (deltaY != 0 && mouseY >= CONTENT_TOP && mouseY < height - FOOTER_HEIGHT) {
            scrollRows -= (int) Math.signum(deltaY) * 2;
            PreviewNode node = currentNode();
            if (node != null) {
                clampScroll(node);
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
            clampScroll(node);
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
        StringBuilder result = new StringBuilder(containerName(root.container()));
        if (root.container().location() instanceof WorldLocation location) {
            result.append(" · ").append(location.x()).append(", ").append(location.y()).append(", ").append(location.z());
        }
        for (int parentSlot : nestedPath) {
            result.append(" / #").append(parentSlot + 1);
        }
        if (!nestedPath.isEmpty()) {
            result.append(" · ").append(containerName(node.container()));
        }
        return result.toString();
    }

    private String containerName(ContainerRecord container) {
        String block = container.metadata().get("minecraftBlock");
        if (block != null) {
            ResourceLocation id = ResourceLocation.tryParse(block);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                return BuiltInRegistries.BLOCK.get(id).getName().getString();
            }
        }
        var id = ResourceLocation.tryParse(container.type().id().toString());
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
        }
        if (container.type().id().toString().equals("minecraft:player_inventory")) {
            return Component.translatable("container.inventory").getString();
        }
        return Component.translatable("gui.litemfinder.contents.title").getString();
    }

    private String slotDescription(SlotHit hit) {
        StringBuilder label = new StringBuilder("#").append(hit.slot().slot() + 1).append(" · ")
                .append(hit.visual().name())
                .append(" · ").append(hit.slot().stack().item().itemId())
                .append(" ×").append(hit.slot().stack().count());
        PreviewNode node = currentNode();
        String equipment = node == null ? null : node.container().metadata().get("accessorySlot." + hit.slot().slot());
        if (equipment != null) {
            label.append(" · ").append(equipment);
        }
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

    private void clampScroll(PreviewNode node) {
        int viewportHeight = Math.max(1, height - FOOTER_HEIGHT - CONTENT_TOP);
        scrollRows = Math.max(0, Math.min(scrollRows, layout(node).scrollLimit(width, viewportHeight)));
    }

    private ContainerSlotLayout layout(PreviewNode node) {
        if (layoutContainer != node.container()) {
            layoutContainer = node.container();
            slotLayout = ContainerSlotLayout.resolve(node.container().metadata(), node.slotCount());
            observedSlots = null;
            if (node.container().metadata().containsKey("captureCoverage")) {
                observedSlots = new java.util.HashSet<>();
                for (String value : node.container().metadata().getOrDefault("observedSlots", "").split(",")) {
                    try { observedSlots.add(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
                }
            }
        }
        return slotLayout;
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
