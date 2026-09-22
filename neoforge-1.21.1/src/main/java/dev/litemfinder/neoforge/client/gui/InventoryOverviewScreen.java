package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.view.AcquisitionDraft;
import dev.litemfinder.neoforge.client.view.InventoryOverview;
import dev.litemfinder.neoforge.client.view.QuantityFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Compact read-only inventory overview with a session-only retrieval quantity draft. */
public final class InventoryOverviewScreen extends Screen {

    private static final int LIST_TOP = 55;
    private static final int FOOTER_HEIGHT = 27;
    private static final int ROW_HEIGHT = 24;
    private static final int EDITOR_EXTRA_HEIGHT = 70;
    private static final int MAX_SEARCH_CHARS = 120;

    private final ItemFinderClientApi api;
    private final AcquisitionDraft draft;
    private final Map<NamespacedId, ItemVisual> visualCache = new HashMap<>();
    private final Map<NamespacedId, ItemKey> selectedVariants = new HashMap<>();
    private InventoryOverview overview = new InventoryOverview(List.of());
    private List<InventoryOverview.ItemRow> filteredRows = List.of();
    private List<UnitHit> unitHits = List.of();
    private NamespacedId expanded;
    private EditBox searchBox;
    private EditBox quantityBox;
    private Button minusButton;
    private Button plusButton;
    private Button variantButton;
    private String searchText = "";
    private String notice = "";
    private int scroll;
    private int ticks;

    public InventoryOverviewScreen(ItemFinderClientApi api, AcquisitionDraft draft) {
        super(Component.translatable("gui.litemfinder.overview.title"));
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.draft = Objects.requireNonNull(draft, "draft must not be null");
    }

    @Override
    protected void init() {
        int searchWidth = Math.max(80, width - 144);
        searchBox = addRenderableWidget(new EditBox(font, 12, 27, searchWidth, 19,
                Component.translatable("gui.litemfinder.search")));
        searchBox.setMaxLength(MAX_SEARCH_CHARS);
        searchBox.setHint(Component.translatable("gui.litemfinder.search"));
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            scroll = 0;
            rebuildFilter();
        });

        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.settings"), ignored -> {
            commitQuantity();
            if (minecraft != null) {
                minecraft.setScreen(new DisplaySettingsScreen(this));
            }
        }).bounds(width - 120, 26, 108, 21).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.plan"), ignored -> {
            commitQuantity();
            if (minecraft != null) {
                minecraft.setScreen(new AcquisitionPlanScreen(this, api, draft));
            }
        }).bounds(Math.max(12, width - 216), 3, 96, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.map"), ignored -> {
            commitQuantity();
            if (minecraft != null) {
                minecraft.setScreen(new ContainerMapScreen(this, api, draft, expanded));
            }
        }).bounds(Math.max(108, width - 320), 3, 96, 20).build());

        quantityBox = addRenderableWidget(new EditBox(font, 0, 0, 78, 19,
                Component.translatable("gui.litemfinder.request")));
        quantityBox.setMaxLength(32);
        quantityBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(
                character -> character >= '0' && character <= '9'));
        minusButton = addRenderableWidget(Button.builder(Component.literal("−"), ignored -> adjust(-64))
                .bounds(0, 0, 20, 19).build());
        plusButton = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> adjust(64))
                .bounds(0, 0, 20, 19).build());
        variantButton = addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.variant"),
                ignored -> cycleVariant()).bounds(0, 0, 150, 19).build());

        refreshOverview();
        layoutEditor();
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 20 == 0) {
            refreshOverview();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0121921);
        graphics.fill(0, 0, width, 51, 0xFF1C2834);
        graphics.drawString(font, title, 12, 9, 0xFFF2F4F6);

        List<UnitHit> hits = new ArrayList<>();
        graphics.enableScissor(8, LIST_TOP, width - 8, listBottom());
        int rowY = LIST_TOP - scroll;
        for (InventoryOverview.ItemRow row : filteredRows) {
            int rowHeight = rowHeight(row);
            if (rowY + rowHeight >= LIST_TOP && rowY <= listBottom()) {
                renderRow(graphics, row, rowY, mouseX, mouseY, hits);
            }
            rowY += rowHeight;
        }
        graphics.disableScissor();
        unitHits = List.copyOf(hits);

        graphics.fill(0, listBottom(), width, height, 0xFF1C2834);
        String summary = Component.translatable("gui.litemfinder.summary", filteredRows.size(),
                draft.selections().size()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(summary, width - 24), 12,
                height - 18, 0xFFBDCBD7);
        if (!notice.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(notice, Math.max(50, width / 2)),
                    Math.max(12, width / 2), height - 18, 0xFFFFC875);
        }

        layoutEditor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics graphics, InventoryOverview.ItemRow row, int y,
                           int mouseX, int mouseY, List<UnitHit> hits) {
        int right = width - 12;
        boolean selected = row.itemId().equals(expanded);
        boolean hovered = mouseX >= 10 && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
        graphics.fill(10, y, right, y + ROW_HEIGHT - 1,
                selected ? 0xFF2D4254 : hovered ? 0xFF263646 : 0xFF202C38);

        ItemVisual visual = visualFor(row.itemId());
        if (!visual.stack().isEmpty()) {
            graphics.renderItem(visual.stack(), 15, y + 3);
        }
        String name = visual.name();
        if (row.variants().size() > 1) {
            name += "  · " + row.variants().size() + "v";
        }
        int stackSize = visual.stackSize();
        String count = QuantityFormat.display(row.totalCount(), Math.max(1, stackSize),
                stackSize > 0 ? ClientDisplayConfig.overviewMode() : QuantityFormat.Mode.NUMBER,
                unitLabel("box"), unitLabel("stack"), unitLabel("item"));
        int countWidth = font.width(count);
        graphics.drawString(font, font.plainSubstrByWidth(name,
                        Math.max(24, width - 72 - countWidth)),
                37, y + 8, 0xFFE7EEF4);
        graphics.drawString(font, count, right - 7 - countWidth, y + 8, 0xFFB9E3B7);

        if (!selected) {
            return;
        }
        int editorY = y + ROW_HEIGHT;
        graphics.fill(10, editorY, right, editorY + EDITOR_EXTRA_HEIGHT - 1, 0xFF243444);
        graphics.drawString(font, Component.translatable("gui.litemfinder.request"), 17,
                editorY + 6, 0xFFE4EDF5);
        InventoryOverview.VariantRow variant = selectedVariant(row);
        graphics.drawString(font, Component.translatable("gui.litemfinder.cap", variant.count()),
                17, editorY + 19, 0xFFB8C6D2);

        long requested = draft.get(variant.item());
        int displayStackSize = Math.max(1, stackSize);
        QuantityFormat.Mode mode = stackSize > 0
                ? ClientDisplayConfig.editorMode() : QuantityFormat.Mode.NUMBER;
        int labelY = editorY + (row.variants().size() > 1 ? 56 : 38);
        int labelX = 17;
        if (mode == QuantityFormat.Mode.BOX_STACK_ITEM) {
            long boxSize = QuantityFormat.unitStep(displayStackSize, QuantityFormat.Unit.BOX);
            long remainder = requested % boxSize;
            labelX = drawUnit(graphics, hits, labelX, labelY, requested / boxSize + " " + unitLabel("box"),
                    QuantityFormat.Unit.BOX);
            labelX = drawUnit(graphics, hits, labelX + 8, labelY,
                    remainder / displayStackSize + " " + unitLabel("stack"), QuantityFormat.Unit.STACK);
            drawUnit(graphics, hits, labelX + 8, labelY,
                    remainder % displayStackSize + " " + unitLabel("item"), QuantityFormat.Unit.ITEM);
        } else if (mode == QuantityFormat.Mode.STACK_AND_REMAINDER) {
            labelX = drawUnit(graphics, hits, labelX, labelY,
                    requested / displayStackSize + " " + unitLabel("stack"), QuantityFormat.Unit.STACK);
            drawUnit(graphics, hits, labelX + 8, labelY,
                    requested % displayStackSize + " " + unitLabel("item"), QuantityFormat.Unit.ITEM);
        } else {
            drawUnit(graphics, hits, labelX, labelY,
                    Component.translatable("gui.litemfinder.wheel_64").getString(), null);
        }
    }

    private int drawUnit(GuiGraphics graphics, List<UnitHit> hits, int x, int y,
                         String value, QuantityFormat.Unit unit) {
        int end = x + font.width(value);
        graphics.drawString(font, value, x, y, 0xFFD7ECFB);
        if (unit != null) {
            hits.add(new UnitHit(x, end, y - 3, y + 12, unit));
        }
        return end;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX < 10 || mouseX >= width - 12 || mouseY < LIST_TOP || mouseY >= listBottom()) {
            commitQuantity();
            return false;
        }
        int y = LIST_TOP - scroll;
        for (InventoryOverview.ItemRow row : filteredRows) {
            int height = rowHeight(row);
            if (mouseY >= y && mouseY < y + height) {
                if (mouseY < y + ROW_HEIGHT) {
                    commitQuantity();
                    expanded = row.itemId().equals(expanded) ? null : row.itemId();
                    if (expanded != null) {
                        quantityBox.setValue(Long.toString(draft.get(selectedVariant(row).item())));
                    }
                    clampScroll();
                    layoutEditor();
                    return true;
                }
                return true;
            }
            y += height;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY == 0) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }
        if (mouseY >= LIST_TOP && mouseY < listBottom() && expanded != null) {
            for (UnitHit hit : unitHits) {
                if (hit.contains(mouseX, mouseY)) {
                    int stackSize = visualFor(expanded).stackSize();
                    if (stackSize > 0) {
                        adjust((deltaY > 0 ? 1 : -1) * QuantityFormat.unitStep(stackSize, hit.unit()));
                    }
                    return true;
                }
            }
            if (quantityBox.visible && mouseX >= quantityBox.getX()
                    && mouseX < quantityBox.getX() + quantityBox.getWidth()
                    && mouseY >= quantityBox.getY()
                    && mouseY < quantityBox.getY() + quantityBox.getHeight()) {
                adjust(deltaY > 0 ? 64 : -64);
                return true;
            }
        }
        if (mouseY >= LIST_TOP && mouseY < listBottom()) {
            scroll -= (int) Math.signum(deltaY) * ROW_HEIGHT * 2;
            clampScroll();
            layoutEditor();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (quantityBox != null && quantityBox.isFocused()
                && (keyCode == 257 || keyCode == 335)) {
            commitQuantity();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        commitQuantity();
        super.onClose();
    }

    private void refreshOverview() {
        Map<ItemKey, Long> before = draft.selections();
        overview = api.overview();
        draft.reconcile(overview);
        if (!before.equals(draft.selections())) {
            notice = Component.translatable("gui.litemfinder.notice.clamped").getString();
        }
        rebuildFilter();
        if (expanded != null && overview.rows().stream().noneMatch(row -> row.itemId().equals(expanded))) {
            expanded = null;
        }
        if (expanded != null && quantityBox != null && !quantityBox.isFocused()) {
            InventoryOverview.ItemRow row = expandedRow();
            if (row != null) {
                quantityBox.setValue(Long.toString(draft.get(selectedVariant(row).item())));
            }
        }
        clampScroll();
    }

    private void rebuildFilter() {
        String needle = searchText.trim().toLowerCase(Locale.ROOT);
        filteredRows = overview.rows().stream()
                .filter(row -> needle.isEmpty() || row.itemId().toString().contains(needle)
                        || visualFor(row.itemId()).name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        clampScroll();
    }

    private void adjust(long delta) {
        InventoryOverview.ItemRow row = expandedRow();
        if (row == null) {
            return;
        }
        commitQuantity();
        InventoryOverview.VariantRow variant = selectedVariant(row);
        long next = QuantityFormat.adjust(draft.get(variant.item()), delta, variant.count());
        draft.set(variant.item(), next, variant.count());
        quantityBox.setValue(Long.toString(next));
        notice = "";
    }

    private void commitQuantity() {
        InventoryOverview.ItemRow row = expandedRow();
        if (row == null || quantityBox == null) {
            return;
        }
        String raw = quantityBox.getValue();
        try {
            InventoryOverview.VariantRow variant = selectedVariant(row);
            long bounded = QuantityFormat.parseClamped(raw, variant.count());
            draft.set(variant.item(), bounded, variant.count());
            if (!raw.equals(Long.toString(bounded))) {
                notice = Component.translatable("gui.litemfinder.notice.clamped").getString();
            }
        } catch (IllegalArgumentException exception) {
            notice = Component.translatable("gui.litemfinder.notice.integer").getString();
        }
        quantityBox.setValue(Long.toString(draft.get(selectedVariant(row).item())));
    }

    private InventoryOverview.ItemRow expandedRow() {
        if (expanded == null) {
            return null;
        }
        return overview.rows().stream().filter(row -> row.itemId().equals(expanded)).findFirst().orElse(null);
    }

    private void layoutEditor() {
        if (quantityBox == null || minusButton == null || plusButton == null || variantButton == null) {
            return;
        }
        quantityBox.visible = false;
        minusButton.visible = false;
        plusButton.visible = false;
        variantButton.visible = false;
        int y = LIST_TOP - scroll;
        for (InventoryOverview.ItemRow row : filteredRows) {
            if (row.itemId().equals(expanded)) {
                int editY = y + ROW_HEIGHT + 3;
                boolean inView = editY >= LIST_TOP
                        && editY + (row.variants().size() > 1 ? 50 : 19) <= listBottom();
                quantityBox.visible = inView;
                minusButton.visible = inView;
                plusButton.visible = inView;
                variantButton.visible = inView && row.variants().size() > 1;
                if (inView) {
                    int x = Math.max(100, width - 134);
                    minusButton.setX(x - 26);
                    minusButton.setY(editY);
                    quantityBox.setX(x);
                    quantityBox.setY(editY);
                    plusButton.setX(x + 83);
                    plusButton.setY(editY);
                    variantButton.setX(17);
                    variantButton.setY(editY + 31);
                    if (row.variants().size() > 1) {
                        InventoryOverview.VariantRow selected = selectedVariant(row);
                        int index = row.variants().indexOf(selected) + 1;
                        String variant = selected.item().variant();
                        String suffix = variant.isEmpty() ? "default"
                                : variant.substring(Math.max(0, variant.length() - 8));
                        variantButton.setMessage(Component.translatable("gui.litemfinder.variant_index",
                                index, row.variants().size(), suffix));
                    }
                }
                break;
            }
            y += rowHeight(row);
        }
    }

    private int rowHeight(InventoryOverview.ItemRow row) {
        return ROW_HEIGHT + (row.itemId().equals(expanded) ? EDITOR_EXTRA_HEIGHT : 0);
    }

    private int listBottom() {
        return Math.max(LIST_TOP + 1, height - FOOTER_HEIGHT);
    }

    private void clampScroll() {
        int total = 0;
        for (InventoryOverview.ItemRow row : filteredRows) {
            total += rowHeight(row);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - (listBottom() - LIST_TOP))));
    }

    private ItemVisual visualFor(NamespacedId itemId) {
        return visualCache.computeIfAbsent(itemId, id -> {
            ResourceLocation key = ResourceLocation.parse(id.toString());
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item == Items.AIR && !id.toString().equals("minecraft:air")) {
                return new ItemVisual(ItemStack.EMPTY, id.toString(), -1);
            }
            ItemStack stack = new ItemStack(item);
            return new ItemVisual(stack, stack.getHoverName().getString(), stack.getMaxStackSize());
        });
    }

    private static String unitLabel(String unit) {
        return Component.translatable("gui.litemfinder.unit." + unit).getString();
    }

    private InventoryOverview.VariantRow selectedVariant(InventoryOverview.ItemRow row) {
        ItemKey key = selectedVariants.get(row.itemId());
        for (InventoryOverview.VariantRow variant : row.variants()) {
            if (variant.item().equals(key)) {
                return variant;
            }
        }
        InventoryOverview.VariantRow first = row.variants().getFirst();
        selectedVariants.put(row.itemId(), first.item());
        return first;
    }

    private void cycleVariant() {
        InventoryOverview.ItemRow row = expandedRow();
        if (row == null || row.variants().size() < 2) {
            return;
        }
        commitQuantity();
        int index = row.variants().indexOf(selectedVariant(row));
        ItemKey next = row.variants().get((index + 1) % row.variants().size()).item();
        selectedVariants.put(row.itemId(), next);
        quantityBox.setValue(Long.toString(draft.get(next)));
        layoutEditor();
    }

    private record ItemVisual(ItemStack stack, String name, int stackSize) {
    }

    private record UnitHit(int left, int right, int top, int bottom, QuantityFormat.Unit unit) {

        private boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }
}
