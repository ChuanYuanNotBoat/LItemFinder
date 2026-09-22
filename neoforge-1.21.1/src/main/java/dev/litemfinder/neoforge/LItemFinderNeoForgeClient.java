package dev.litemfinder.neoforge;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.InputConstants;
import dev.litemfinder.neoforge.capture.ClientMenuCaptureCoordinator;
import dev.litemfinder.neoforge.capture.ClientPlayerInventoryCaptureCoordinator;
import dev.litemfinder.neoforge.capture.MenuCaptureRequest;
import dev.litemfinder.neoforge.capture.MenuFingerprintCalculator;
import dev.litemfinder.neoforge.capture.RecentInteractionTracker;
import dev.litemfinder.neoforge.capture.VanillaMenuSlotPartitioner;
import dev.litemfinder.neoforge.client.DefaultItemFinderClientApi;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.gui.ClientDisplayConfig;
import dev.litemfinder.neoforge.client.gui.InventoryOverviewScreen;
import dev.litemfinder.neoforge.client.view.AcquisitionDraft;
import dev.litemfinder.neoforge.command.ClientDebugCommands;
import dev.litemfinder.neoforge.diagnostics.CaptureDiagnostics;
import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.lifecycle.BlockContainerRemovalMonitor;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.mapping.MenuSnapshotMapper;
import dev.litemfinder.neoforge.persistence.SnapshotStorageCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.Instant;

/** NeoForge client entry point. Platform integration is added behind this boundary. */
@Mod(value = LItemFinderNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class LItemFinderNeoForgeClient {

    public static final String MOD_ID = "litemfinder";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final KeyMapping OPEN_OVERVIEW = new KeyMapping(
            "key.litemfinder.open_overview",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.litemfinder"
    );
    private final CachedItemTagResolver itemTags = new CachedItemTagResolver();
    private final MenuSnapshotMapper snapshots = new MenuSnapshotMapper(itemTags);
    private final SnapshotStorageCoordinator storage;
    private final CaptureDiagnostics diagnostics = new CaptureDiagnostics();
    private final ItemFinderClientApi clientApi;
    private final AcquisitionDraft acquisitionDraft = new AcquisitionDraft();
    private final ClientDebugCommands commands;
    private final RecentInteractionTracker interactions;
    private final ClientMenuCaptureCoordinator captures;
    private final ClientPlayerInventoryCaptureCoordinator playerInventoryCaptures;
    private final BlockContainerRemovalMonitor removals;
    private boolean openOverviewRequested;

    public LItemFinderNeoForgeClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientDisplayConfig.SPEC);
        storage = new SnapshotStorageCoordinator(FMLPaths.CONFIGDIR.get().resolve("litemfinder/index"));
        clientApi = new DefaultItemFinderClientApi(storage, itemTags, diagnostics);
        commands = new ClientDebugCommands(clientApi, this::requestOverview);
        interactions = new RecentInteractionTracker(new MinecraftContainerIdentityResolver());
        captures = new ClientMenuCaptureCoordinator(
                new MinecraftScopeResolver(),
                interactions,
                new VanillaMenuSlotPartitioner(true),
                new MenuFingerprintCalculator(),
                diagnostics,
                this::storeCapture
        );
        playerInventoryCaptures = new ClientPlayerInventoryCaptureCoordinator(
                new MinecraftScopeResolver(),
                new MinecraftContainerIdentityResolver(),
                new VanillaMenuSlotPartitioner(true),
                new MenuFingerprintCalculator(),
                diagnostics,
                this::storeCapture
        );
        removals = new BlockContainerRemovalMonitor(storage, new MinecraftScopeResolver(), diagnostics);
        modEventBus.addListener(this::registerReloadListeners);
        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientLogout);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(this::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(this::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(this::onGameShuttingDown);
        LOGGER.info("LItem Finder NeoForge adapter initialized");
        if (Boolean.getBoolean("litemfinder.m0.sqliteProbe")) {
            Path database = Path.of("litemfinder-m0-sqlite-probe.db");
            SqliteRuntimeProbe.verify(database);
            LOGGER.info("LItem Finder SQLite runtime probe passed");
        }
    }

    private void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ignored -> itemTags.clear());
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_OVERVIEW);
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        playerInventoryCaptures.close();
        captures.close();
        itemTags.clear();
        captures.reset();
        playerInventoryCaptures.reset();
        removals.reset();
        acquisitionDraft.clear();
        openOverviewRequested = false;
        storage.leaveScope();
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_OVERVIEW.consumeClick()) {
            requestOverview();
        }
        captures.clientTick(minecraft);
        playerInventoryCaptures.clientTick(minecraft);
        removals.clientTick(minecraft);
        if (openOverviewRequested && minecraft.player != null && minecraft.screen == null) {
            openOverviewRequested = false;
            minecraft.setScreen(new InventoryOverviewScreen(clientApi, acquisitionDraft));
        }
    }

    private void requestOverview() {
        openOverviewRequested = true;
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        commands.register(event);
    }

    private void onScreenOpening(ScreenEvent.Opening event) {
        captures.screenOpened(event.getScreen(), Minecraft.getInstance());
    }

    private void onScreenClosing(ScreenEvent.Closing event) {
        captures.screenClosed(event.getScreen());
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            interactions.rememberBlock(event.getLevel(), event.getPos());
        }
    }

    private void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) {
            interactions.rememberEntity(event.getTarget());
        }
    }

    private void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide) {
            interactions.rememberEntity(event.getTarget());
        }
    }

    private void storeCapture(MenuCaptureRequest request) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        try {
            var snapshot = snapshots.map(request, minecraft.level.registryAccess(), Instant.now());
            var queued = storage.submit(snapshot, request.identity().persistable());
            LOGGER.debug(
                    "LItem Finder captured: reason={}, id={}, confidence={}, kind={}, slots={}, occupied={}, queued={}",
                    request.reason(),
                    request.identity().container().id(),
                    request.identity().confidence(),
                    request.partition().kind(),
                    request.fingerprint().slotCount(),
                    request.fingerprint().occupiedSlots(),
                    queued
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to map captured menu {}", request.identity().container().id(), exception);
        }
    }

    private void onGameShuttingDown(GameShuttingDownEvent event) {
        captures.close();
        playerInventoryCaptures.close();
        storage.close();
    }
}
