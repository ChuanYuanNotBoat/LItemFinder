package dev.litemfinder.neoforge;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.capture.ClientMenuCaptureCoordinator;
import dev.litemfinder.neoforge.capture.MenuCaptureRequest;
import dev.litemfinder.neoforge.capture.MenuFingerprintCalculator;
import dev.litemfinder.neoforge.capture.RecentInteractionTracker;
import dev.litemfinder.neoforge.capture.VanillaMenuSlotPartitioner;
import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.mapping.MenuSnapshotMapper;
import dev.litemfinder.neoforge.persistence.SnapshotStorageCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Instant;

/** NeoForge client entry point. Platform integration is added behind this boundary. */
@Mod(value = LItemFinderNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class LItemFinderNeoForgeClient {

    public static final String MOD_ID = "litemfinder";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final CachedItemTagResolver itemTags = new CachedItemTagResolver();
    private final MenuSnapshotMapper snapshots = new MenuSnapshotMapper(itemTags);
    private final SnapshotStorageCoordinator storage;
    private final RecentInteractionTracker interactions;
    private final ClientMenuCaptureCoordinator captures;

    public LItemFinderNeoForgeClient(IEventBus modEventBus) {
        storage = new SnapshotStorageCoordinator(FMLPaths.CONFIGDIR.get().resolve("litemfinder/index"));
        interactions = new RecentInteractionTracker(new MinecraftContainerIdentityResolver());
        captures = new ClientMenuCaptureCoordinator(
                new MinecraftScopeResolver(),
                interactions,
                new VanillaMenuSlotPartitioner(true),
                new MenuFingerprintCalculator(),
                this::storeCapture
        );
        modEventBus.addListener(this::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onClientLogout);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
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

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        itemTags.clear();
        captures.reset();
        storage.leaveScope();
    }

    private void onClientTick(ClientTickEvent.Post event) {
        captures.clientTick(Minecraft.getInstance());
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
            LOGGER.info(
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
        storage.close();
    }
}
