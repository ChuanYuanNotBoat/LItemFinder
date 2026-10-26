package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.model.*;
import dev.litemfinder.core.snapshot.FluidSnapshotMetadata;
import dev.litemfinder.core.view.*;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.view.AcquisitionDraft;
import dev.litemfinder.neoforge.client.view.InventoryOverview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;
import java.util.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InventoryModeSwitchTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }

    @Test void switchingBodyKeepsTheSameHeaderSearchBoxAndItemDraftWithoutOpeningAnotherScreen() throws Exception {
        Minecraft game = mock(Minecraft.class);
        try (var staticMinecraft = mockStatic(Minecraft.class)) {
            staticMinecraft.when(Minecraft::getInstance).thenReturn(game);
            var api = mock(ItemFinderClientApi.class);
            ItemKey diamond = ItemKey.parse("minecraft:diamond");
            when(api.overview()).thenReturn(new InventoryOverview(List.of(new InventoryOverview.ItemRow(diamond.itemId(),
                    List.of(new InventoryOverview.VariantRow(diamond, 23))))));
            when(api.fluidOverview()).thenReturn(new FluidOverview(List.of()));
            var draft = new AcquisitionDraft(); draft.set(diamond, 5, 23);
            var screen = new InventoryOverviewScreen(api, draft);
            prepare(screen, game); screen.init();
            var header = List.copyOf(screen.children());
            var search = (EditBox) screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
            search.setValue("diamond");
            screen.mouseClicked(100, 60, 0);
            button(screen, "gui.litemfinder.tabs.fluids").onPress();
            assertEquals(header, screen.children(), "the host and header widgets must remain in place");
            assertSame(search, screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow());
            search.setValue("water");
            button(screen, "gui.litemfinder.tabs.items").onPress();
            assertEquals("diamond", search.getValue());
            assertEquals(5, draft.get(diamond));
            button(screen, "gui.litemfinder.tabs.fluids").onPress();
            assertEquals("water", search.getValue());
            verify(game, never()).setScreen(any());
        }
    }

    @Test void fluidMapHighlightsActualContainersAndDropsDrainedTargetsWithoutCreatingCarriedStops() throws Exception {
        Minecraft game = mock(Minecraft.class);
        try (var staticMinecraft = mockStatic(Minecraft.class)) {
            staticMinecraft.when(Minecraft::getInstance).thenReturn(game);
            var api = mock(ItemFinderClientApi.class);
            var snapshots = new AtomicReference<>(List.of(container("tank", true, 2000), container("carried", false, 500)));
            when(api.containerOverview()).thenAnswer(ignored -> ContainerOverview.from(snapshots.get()));
            var key = new FluidKey(NamespacedId.parse("example:water"), "");
            var map = ContainerMapScreen.forFluids(mock(Screen.class), api, key, null);
            prepare(map, game); map.init();
            var stopsMethod = ContainerMapScreen.class.getDeclaredMethod("sourceStops"); stopsMethod.setAccessible(true);
            assertEquals(List.of(snapshots.get().getFirst().container().location()), stopsMethod.invoke(map));
            snapshots.set(List.of(container("tank", true, 0), container("carried", false, 500)));
            for (int tick = 0; tick < 40; tick++) map.tick();
            assertEquals(List.of(), stopsMethod.invoke(map));
            var header = List.copyOf(map.children());
            button(map, "gui.litemfinder.tabs.items").onPress();
            button(map, "gui.litemfinder.tabs.fluids").onPress();
            assertEquals(header, map.children());
            verify(game, never()).setScreen(any());
        }
    }

    private static void prepare(Screen screen, Minecraft game) throws Exception {
        var font = mock(Font.class);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        var minecraftField = Screen.class.getDeclaredField("minecraft"); minecraftField.setAccessible(true); minecraftField.set(screen, game);
        var fontField = Screen.class.getDeclaredField("font"); fontField.setAccessible(true); fontField.set(screen, font);
        screen.width = 640; screen.height = 360;
    }
    private static Button button(Screen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getContents() instanceof TranslatableContents translated && translated.getKey().equals(key)).findFirst().orElseThrow();
    }
    private static InventorySnapshot container(String id, boolean world, long amount) {
        var metadata = new TreeMap<String, String>();
        FluidSnapshotMetadata.writeStored(metadata, new FluidSnapshotMetadata.StoredInventory(List.of(
                new FluidSnapshotMetadata.StoredTank(0, amount == 0 ? "" : "example:water", "", amount == 0 ? "" : "opaque", amount, 10000)), Instant.EPOCH));
        return new InventorySnapshot(new ContainerRecord(new ContainerId(id), ContainerType.CHEST, world
                ? new WorldLocation("scope", NamespacedId.parse("example:world"), 1, 64, 2) : new LogicalLocation("scope", "carried"), metadata),
                0, Instant.EPOCH, List.of());
    }
}
