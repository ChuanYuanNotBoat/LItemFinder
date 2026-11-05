package dev.litemfinder.compatprobe;

import com.mojang.logging.LogUtils;
import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.*;
import dev.litemfinder.core.search.SearchResponse;
import dev.litemfinder.core.search.SearchResult;
import dev.litemfinder.core.view.*;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.gui.ContainerMapScreen;
import dev.litemfinder.neoforge.client.gui.NavigationReportScreen;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/** Real client rendering of synthetic data; never opens a world or claims that fixture routes are traversable. */
final class NavigationUiProbe {
    private int stage, frames;
    private boolean prepared, finished;
    private static final List<WorldLocation> STOPS = List.of(
            new WorldLocation("ui-probe", "minecraft:overworld", 20, 64, 0),
            new WorldLocation("ui-probe", "minecraft:the_nether", 100, 80, -300));

    boolean frame(Minecraft game) {
        if (finished) return true;
        try {
            if (!prepared) {
                prepare(game);
                prepared = true;
                frames = 0;
                return false;
            }
            if (++frames < 8 || game.getOverlay() != null) return false;
            int scale = stage < 4 && stage % 2 == 0 ? 3 : 5;
            require(game.getWindow().getGuiScale() == scale, "requested GUI scale was not applied");
            if (stage >= 4 && stage % 2 == 1) {
                for (int count = 0; count < 20; count++) game.screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
                if (frames < 10) return false;
            }
            @SuppressWarnings("unchecked")
            List<FormattedCharSequence> lines = (List<FormattedCharSequence>) read(game.screen,
                    game.screen instanceof ContainerMapScreen ? "summaryLines" : "lines");
            int maxWidth = game.screen.width - (game.screen instanceof ContainerMapScreen ? 28 : 32);
            require(!lines.isEmpty() && lines.stream().allMatch(line -> game.font.width(line) <= maxWidth),
                    "wrapped route text overflows its available width");
            if (stage >= 4 && stage % 2 == 1) require((int) read(game.screen, "scroll") > 0, "details did not scroll");
            Path output = Path.of("../build/route-ui-probe", name());
            Files.createDirectories(output.getParent());
            try (var image = Screenshot.takeScreenshot(game.getMainRenderTarget())) { image.writeToFile(output); }
            LogUtils.getLogger().info("NAVIGATION UI CAPTURE: {} at {}x{}, GUI scale {}, wrapped {} lines", name(),
                    game.screen.width, game.screen.height, scale, lines.size());
            stage++;
            prepared = false;
            if (stage == 8) {
                finished = true;
                LogUtils.getLogger().info("NAVIGATION UI PROBE PASSED: eight synthetic layouts, en_us/zh_cn, actual GUI scales 3/5, wrapped text and scrolling");
                game.setScreen(new TitleScreen());
            }
            return false;
        } catch (Exception failure) { throw new IllegalStateException("route UI probe failed at stage " + stage, failure); }
    }

    private void prepare(Minecraft game) throws Exception {
        String language = stage < 4 ? stage < 2 ? "en_us" : "zh_cn" : stage < 6 ? "en_us" : "zh_cn";
        game.getLanguageManager().setSelected(language);
        game.getLanguageManager().onResourceManagerReload(game.getResourceManager());
        game.getWindow().setWindowed(1920, 1200);
        game.options.guiScale().set(stage < 4 && stage % 2 == 0 ? 3 : 5);
        game.resizeDisplay();
        if (stage < 4) {
            var map = mapFixture();
            game.setScreen(map);
            write(map, "navigationOverlay", new NavigationGateway.Overlay(NavigationGateway.Status.UNKNOWN, List.of(), 0,
                    report(NavigationReport.Reason.BUDGET, 0)));
        } else {
            var walk = new NavigationSummary.Leg("WALK", List.of(point("minecraft:overworld", 0, 64, 0),
                    point("minecraft:overworld", 20, 64, 0)), OptionalDouble.of(12));
            var boat = new NavigationSummary.Leg("WATER_BOAT", List.of(walk.end(),
                    point("minecraft:overworld", 1200, 64, 40)), OptionalDouble.of(100));
            var portal = new NavigationSummary.Leg("PORTAL", List.of(boat.end(),
                    point("minecraft:the_nether", 150, 80, -300)), OptionalDouble.empty());
            var summary = new NavigationSummary(NavigationSummary.State.PARTIAL, false, STOPS,
                    List.of(walk, boat, portal), 112, report(NavigationReport.Reason.UNOBSERVED, 1));
            game.setScreen(new NavigationReportScreen(new TitleScreen(), summary, Component.translatable("block.minecraft.barrel")));
        }
    }

    private static NavigationReport report(NavigationReport.Reason reason, int stops) {
        return new NavigationReport(reason, NavigationReport.Estimate.BASE_SPEED, stops, Optional.of(Instant.parse("2026-10-28T15:00:00Z")),
                NavigationReport.Confidence.PROVIDER_REPORTED, NavigationReport.Source.PROVIDER);
    }

    private static ContainerMapScreen mapFixture() {
        ItemKey item = ItemKey.parse("minecraft:diamond");
        var first = new ContainerRecord(new ContainerId("ui-one"), ContainerType.CHEST, STOPS.getFirst(), Map.of("minecraftBlock", "minecraft:barrel"));
        var second = new ContainerRecord(new ContainerId("ui-two"), ContainerType.CHEST, STOPS.getLast(), Map.of("minecraftBlock", "minecraft:chest"));
        var stack = new ItemStackInfo(item, 5);
        var snapshots = List.of(new InventorySnapshot(first, 27, Instant.EPOCH, List.of(new SlotSnapshot(0, stack))),
                new InventorySnapshot(second, 27, Instant.EPOCH, List.of(new SlotSnapshot(0, stack))));
        var response = new SearchResponse(List.of(new SearchResult(item, List.of(
                new StorageEntry(first, first, ContainerPath.root(first.id()), 0, stack, Instant.EPOCH),
                new StorageEntry(second, second, ContainerPath.root(second.id()), 0, stack, Instant.EPOCH)))));
        var api = (ItemFinderClientApi) Proxy.newProxyInstance(ItemFinderClientApi.class.getClassLoader(), new Class<?>[]{ItemFinderClientApi.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "containerOverview" -> ContainerOverview.from(snapshots);
                    case "plan" -> AcquisitionPlan.allocate(response, Map.of(item, 10L));
                    default -> throw new IllegalStateException("unexpected fixture API: " + method.getName());
                });
        var draft = new AcquisitionDraft();
        draft.set(item, 10, 10);
        return new ContainerMapScreen(new TitleScreen(), api, draft, null);
    }

    private String name() {
        return stage < 4 ? (stage < 2 ? "en_us" : "zh_cn") + "-map-scale" + (stage % 2 == 0 ? 3 : 5) + ".png"
                : (stage < 6 ? "en_us" : "zh_cn") + "-details-scale5-" + (stage % 2 == 0 ? "top" : "bottom") + ".png";
    }
    private static WorldPosition point(String dimension, double x, double y, double z) { return new WorldPosition("ui-probe", dimension, x, y, z); }
    private static Object read(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void write(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
