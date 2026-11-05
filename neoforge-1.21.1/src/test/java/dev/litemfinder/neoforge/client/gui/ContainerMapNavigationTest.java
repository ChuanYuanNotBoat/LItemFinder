package dev.litemfinder.neoforge.client.gui;

import dev.litemfinder.core.index.StorageEntry;
import dev.litemfinder.core.model.*;
import dev.litemfinder.core.search.SearchResponse;
import dev.litemfinder.core.search.SearchResult;
import dev.litemfinder.core.view.AcquisitionDraft;
import dev.litemfinder.core.view.AcquisitionPlan;
import dev.litemfinder.core.view.ContainerOverview;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContainerMapNavigationTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }

    @Test void keepsThePreviousPathWhileRecalculatingTheSamePlanAndAcceptsTheFreshResult() throws Exception {
        try (var fixture = new Fixture()) {
            var first = overlay("world", 0, 20);
            fixture.replies.getFirst().complete(first);
            assertSame(first, fixture.overlay());
            when(fixture.game.player.position()).thenReturn(new Vec3(9, 64, 0));
            fixture.refresh();
            assertEquals(2, fixture.replies.size());
            assertSame(first, fixture.overlay(), "recalculation must not flash back to schematic stops");
            var fresh = overlay("world", 9, 20);
            fixture.replies.getLast().complete(fresh);
            assertSame(fresh, fixture.overlay());
        }
    }

    @Test void aLateResultFromAReplacedWorldCannotReviveAnOldPathOrLeaveTheRequestPending() throws Exception {
        try (var fixture = new Fixture()) {
            var replacement = mock(ClientLevel.class);
            when(replacement.dimension()).thenReturn(Level.OVERWORLD);
            fixture.game.level = replacement;
            fixture.replies.getFirst().complete(overlay("world", 0, 20));
            assertNull(fixture.overlay());
            assertFalse((boolean) field(fixture.map, "navigationPending"));
            fixture.refresh();
            assertEquals(2, fixture.replies.size());
        }
    }

    @Test void removingTheScreenInvalidatesLateResultsEvenWhenTheParentStillHoldsTheMapInstance() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.map.removed();
            fixture.replies.getFirst().complete(overlay("world", 0, 20));
            assertNull(fixture.overlay());
            assertFalse((boolean) field(fixture.map, "navigationPending"));
        }
    }

    @Test void rejectsCrossWorldProviderDataWithoutCrashingTheClient() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.replies.getFirst().complete(overlay("other-world", 0, 20));
            assertNull(fixture.overlay());
            assertFalse((boolean) field(fixture.map, "navigationPending"));
        }
    }

    @Test void synchronousOptionalApiLinkageErrorsFallBackToRecordedStops() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.replies.getFirst().complete(overlay("world", 0, 20));
            when(fixture.game.player.position()).thenReturn(new Vec3(9, 64, 0));
            doThrow(new NoClassDefFoundError("optional navigation API")).when(fixture.gateway).route(any(), anyList());
            assertDoesNotThrow(fixture::refresh);
            assertNull(fixture.overlay());
            assertFalse((boolean) field(fixture.map, "navigationPending"));
        }
    }

    private static Object field(Object instance, String name) throws Exception {
        var field = ContainerMapScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    @Test void providerRevisionChangeImmediatelyDropsTheOldPathAndRequestsFreshData() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.replies.getFirst().complete(overlay("world", 0, 20));
            fixture.map.tick();
            when(fixture.gateway.revision()).thenReturn(1L);
            fixture.map.tick();
            assertNull(fixture.overlay());
            assertEquals(2, fixture.replies.size());
        }
    }

    @Test void unknownAndUnreachableProviderOutcomesAreKeptForTheirExplanation() throws Exception {
        for (var status : List.of(NavigationGateway.Status.UNKNOWN, NavigationGateway.Status.UNREACHABLE)) {
            try (var fixture = new Fixture()) {
                var report = new dev.litemfinder.core.view.NavigationReport(
                        dev.litemfinder.core.view.NavigationReport.Reason.BUDGET,
                        dev.litemfinder.core.view.NavigationReport.Estimate.BASE_SPEED, 0, Optional.empty(),
                        dev.litemfinder.core.view.NavigationReport.Confidence.UNSPECIFIED,
                        dev.litemfinder.core.view.NavigationReport.Source.UNSPECIFIED);
                var result = new NavigationGateway.Overlay(status, List.of(), 0, report);
                fixture.replies.getFirst().complete(result);
                assertSame(result, fixture.overlay());
                assertFalse((boolean) field(fixture.map, "navigationFailed"));
                assertFalse((boolean) field(fixture.map, "navigationPending"));
            }
        }
    }

    @Test void providerFailureCanBeShownSeparatelyFromAnUnknownRoute() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.replies.getFirst().completeExceptionally(new IllegalStateException("provider unavailable"));
            assertNull(fixture.overlay());
            assertTrue((boolean) field(fixture.map, "navigationFailed"));
        }
    }

    @Test void precisePlayerCoordinatesReachTheProviderIncludingFractionalStandingHeight() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.replies.getFirst().complete(overlay("world", 0, 20));
            when(fixture.game.player.position()).thenReturn(new Vec3(9.125, 64.5, .75));
            fixture.refresh();
            var positions = org.mockito.ArgumentCaptor.forClass(WorldPosition.class);
            verify(fixture.gateway, times(2)).route(positions.capture(), anyList());
            assertEquals(new WorldPosition("world", "minecraft:overworld", 9.125, 64.5, .75), positions.getValue());
        }
    }

    @Test void clickingThePlayerMarkerOpensOnlyTheCurrentPlayersOriginalSlots() throws Exception {
        try (var fixture = new Fixture()) {
            var player = java.util.UUID.fromString("76488ca8-25d1-4620-ad2c-7960ac41e066");
            when(fixture.game.player.getUUID()).thenReturn(player);
            var root = dev.litemfinder.neoforge.identity.ContainerIdentityFactory.playerInventory("world", player).container();
            when(fixture.api.containerOverview()).thenReturn(ContainerOverview.from(List.of(new InventorySnapshot(root, 45,
                    Instant.EPOCH, List.of(new SlotSnapshot(41, new ItemStackInfo(ItemKey.parse("minecraft:shulker_box"), 1)))))));
            fixture.refresh();
            for (String name : List.of("playerMarkerX", "playerMarkerY")) {
                var marker = ContainerMapScreen.class.getDeclaredField(name); marker.setAccessible(true); marker.set(fixture.map, 100d);
            }
            assertTrue(fixture.map.mouseClicked(100, 100, 0));
            var screen = org.mockito.ArgumentCaptor.forClass(Screen.class);
            verify(fixture.game).setScreen(screen.capture());
            assertInstanceOf(ContainerContentsScreen.class, screen.getValue());
            var selectedRoot = ContainerContentsScreen.class.getDeclaredField("rootId"); selectedRoot.setAccessible(true);
            assertEquals(root.id(), selectedRoot.get(screen.getValue()));
        }
    }

    private static NavigationGateway.Overlay overlay(String scope, double from, double to) {
        return new NavigationGateway.Overlay(NavigationGateway.Status.FOUND, List.of(
                new NavigationGateway.Segment("WALK", List.of(
                        new WorldPosition(scope, "minecraft:overworld", from, 64, 0),
                        new WorldPosition(scope, "minecraft:overworld", to, 64, 0)))), 4);
    }

    private static final class Fixture implements AutoCloseable {
        final Minecraft game = mock(Minecraft.class);
        final NavigationGateway gateway = mock(NavigationGateway.class);
        final MockedStatic<Minecraft> staticMinecraft = mockStatic(Minecraft.class);
        final MockedStatic<NavigationGateway> staticGateway = mockStatic(NavigationGateway.class);
        final List<CompletableFuture<NavigationGateway.Overlay>> replies = new ArrayList<>();
        final ContainerMapScreen map;
        final ItemFinderClientApi api = mock(ItemFinderClientApi.class);

        Fixture() throws Exception {
            try {
            staticMinecraft.when(Minecraft::getInstance).thenReturn(game);
            staticGateway.when(NavigationGateway::discover).thenReturn(Optional.of(gateway));
            game.level = mock(ClientLevel.class);
            game.player = mock(LocalPlayer.class);
            when(game.level.dimension()).thenReturn(Level.OVERWORLD);
            when(game.player.position()).thenReturn(new Vec3(0, 64, 0));
            doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(game).execute(any(Runnable.class));
            when(gateway.route(any(), anyList())).thenAnswer(call -> {
                var reply = new CompletableFuture<NavigationGateway.Overlay>();
                replies.add(reply);
                return reply;
            });
            ItemKey diamond = ItemKey.parse("minecraft:diamond");
            var root = new ContainerRecord(new ContainerId("chest"), ContainerType.CHEST,
                    new WorldLocation("world", "minecraft:overworld", 20, 64, 0));
            var item = new ItemStackInfo(diamond, 5);
            var snapshot = new InventorySnapshot(root, 27, Instant.EPOCH, List.of(new SlotSnapshot(0, item)));
            var response = new SearchResponse(List.of(new SearchResult(diamond, List.of(
                    new StorageEntry(root, root, ContainerPath.root(root.id()), 0, item, Instant.EPOCH)))));
            when(api.containerOverview()).thenReturn(ContainerOverview.from(List.of(snapshot)));
            when(api.plan(anyMap())).thenAnswer(call -> AcquisitionPlan.allocate(response, call.getArgument(0)));
            AcquisitionDraft draft = new AcquisitionDraft();
            draft.set(diamond, 5, 5);
            map = new ContainerMapScreen(mock(Screen.class), api, draft, null);
            game.screen = map;
            var minecraft = Screen.class.getDeclaredField("minecraft");
            minecraft.setAccessible(true); minecraft.set(map, game);
            var mockFont = mock(Font.class);
            when(mockFont.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
            when(mockFont.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
            var font = Screen.class.getDeclaredField("font");
            font.setAccessible(true); font.set(map, mockFont);
            map.width = 640; map.height = 360;
            map.init();
            assertEquals(1, replies.size());
            } catch (Exception | Error failure) {
                close();
                throw failure;
            }
        }

        Object overlay() throws Exception { return field(map, "navigationOverlay"); }
        void refresh() { for (int tick = 0; tick < 40; tick++) map.tick(); }
        @Override public void close() { staticGateway.close(); staticMinecraft.close(); }
    }
}
