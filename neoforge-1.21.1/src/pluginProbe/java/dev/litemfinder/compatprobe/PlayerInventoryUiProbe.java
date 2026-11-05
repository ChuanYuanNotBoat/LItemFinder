package dev.litemfinder.compatprobe;

import com.mojang.logging.LogUtils;
import dev.litemfinder.core.model.*;
import dev.litemfinder.core.view.ContainerOverview;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.gui.*;
import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
import dev.litemfinder.neoforge.mapping.InventoryLayoutMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Synthetic player/accessory fixtures rendered through production widgets; no real world is opened. */
final class PlayerInventoryUiProbe {
    private int stage, frames;
    private boolean prepared, finished;
    private static final InventorySnapshot PLAYER = fixture();

    boolean frame(Minecraft game) {
        if (finished) return true;
        try {
            if (!prepared) {
                String language = stage < 3 ? "en_us" : "zh_cn";
                game.getLanguageManager().setSelected(language);
                game.getLanguageManager().onResourceManagerReload(game.getResourceManager());
                game.options.guiScale().set(5); game.resizeDisplay();
                var api = (ItemFinderClientApi) Proxy.newProxyInstance(ItemFinderClientApi.class.getClassLoader(), new Class<?>[]{ItemFinderClientApi.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("containerOverview")) return ContainerOverview.from(List.of(PLAYER));
                            if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                            throw new IllegalStateException("Unexpected player UI fixture call: " + method.getName());
                        });
                var contents = new ContainerContentsScreen(new TitleScreen(), api, PLAYER.container().id());
                game.setScreen(contents);
                var jump = ContainerContentsScreen.class.getDeclaredMethod("jumpToEquipment"); jump.setAccessible(true); jump.invoke(contents);
                if (stage % 3 == 1) game.setScreen(new TooltipScreen(contents));
                prepared = true; frames = 0; return false;
            }
            if (++frames < 8) return false;
            if (stage % 3 == 2 && frames == 8) {
                var field = ContainerContentsScreen.class.getDeclaredField("scrollRows"); field.setAccessible(true);
                var layout = ContainerSlotLayout.resolve(PLAYER.container().metadata(), PLAYER.slotCount());
                var cell = layout.cells(game.screen.width, 74, game.screen.height - 32, (int) field.get(game.screen)).stream()
                        .filter(value -> value.slot() == 41).findFirst().orElseThrow();
                if (!game.screen.mouseClicked(cell.x() + 9, cell.y() + 9, 0)) throw new IllegalStateException("nested slot click failed");
                var path = ContainerContentsScreen.class.getDeclaredField("nestedPath"); path.setAccessible(true);
                if (!path.get(game.screen).equals(List.of(41))) throw new IllegalStateException("wrong nested path after slot click");
                return false;
            }
            if (stage % 3 == 2 && frames < 10) return false;
            Path file = Path.of("../build/player-ui-probe", (stage < 3 ? "en_us" : "zh_cn")
                    + (stage % 3 == 0 ? "-slots-scale5.png" : stage % 3 == 1 ? "-preview-scale5.png" : "-nested-slots-scale5.png"));
            Files.createDirectories(file.getParent());
            try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(file); }
            stage++; prepared = false;
            if (stage == 6) {
                finished = true;
                LogUtils.getLogger().info("PLAYER INVENTORY UI PROBE PASSED: six synthetic grids/previews, clicked accessory slot opens original nested slots, en_us/zh_cn, GUI scale 5");
                game.setScreen(new TitleScreen());
            }
            return false;
        } catch (Exception error) { throw new IllegalStateException("player inventory UI probe failed", error); }
    }

    private static final class TooltipScreen extends Screen {
        final ContainerContentsScreen contents;
        final ContainerItemTooltip tooltip = new ContainerItemTooltip();
        TooltipScreen(ContainerContentsScreen contents) { super(Component.literal("Preview fixture")); this.contents = contents; }
        @Override protected void init() { contents.init(minecraft, width, height); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            contents.render(graphics, -1, -1, partialTick);
            tooltip.render(graphics, font, width, height, width / 2, 80,
                    Component.translatable("block.minecraft.shulker_box"), "curios/back:0", 1, true,
                    PLAYER.slots().getLast().nestedContainer().orElseThrow(), true);
        }
    }

    private static InventorySnapshot fixture() {
        var bagRecord = new ContainerRecord(new ContainerId("player-ui-bag"), new ContainerType(NamespacedId.parse("minecraft:shulker_box")),
                new LogicalLocation("ui", "bag"), Map.of("previewUpgrade.0", "minecraft:hopper", "previewUpgrade.1", "minecraft:chest"));
        var bag = new InventorySnapshot(bagRecord, 120, Instant.now(), List.of(
                new SlotSnapshot(2, new ItemStackInfo(ItemKey.parse("minecraft:diamond"), 4462)),
                new SlotSnapshot(23, new ItemStackInfo(ItemKey.parse("minecraft:iron_ingot"), 1897)),
                new SlotSnapshot(119, new ItemStackInfo(ItemKey.parse("minecraft:gold_ingot"), 64))));
        Map<String, String> metadata = new TreeMap<>();
        List<InventoryLayout.Position> positions = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) positions.add(new InventoryLayout.Position(slot, 8 + slot * 18, 142));
        for (int slot = 9; slot < 36; slot++) positions.add(new InventoryLayout.Position(slot, 8 + (slot - 9) % 9 * 18, 84 + (slot - 9) / 9 * 18));
        for (int slot = 36; slot < 40; slot++) positions.add(new InventoryLayout.Position(slot, 8, 62 - (slot - 36) * 18));
        positions.add(new InventoryLayout.Position(40, 152, 62));
        for (int slot = 41; slot < 45; slot++) {
            positions.add(new InventoryLayout.Position(slot, 8 + (slot - 41) * 18, 180));
            metadata.put("accessorySlot." + slot, "curios/" + (slot == 41 ? "back" : "charm") + ":0");
        }
        InventoryLayoutMetadata.write(metadata, new InventoryLayout(positions));
        var record = new ContainerRecord(new ContainerId("player:ui:76488ca8-25d1-4620-ad2c-7960ac41e066:inventory"),
                new ContainerType(NamespacedId.parse("minecraft:player_inventory")), new LogicalLocation("ui", "player"), metadata);
        return new InventorySnapshot(record, 45, Instant.now(), List.of(
                new SlotSnapshot(0, new ItemStackInfo(ItemKey.parse("minecraft:iron_pickaxe"), 1)),
                new SlotSnapshot(9, new ItemStackInfo(ItemKey.parse("minecraft:stone"), 64)),
                SlotSnapshot.nested(41, new ItemStackInfo(ItemKey.parse("minecraft:shulker_box"), 1), bag)));
    }
}
