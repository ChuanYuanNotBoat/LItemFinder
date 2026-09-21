package dev.litemfinder.neoforge;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.nio.file.Path;

/** NeoForge client entry point. Platform integration is added behind this boundary. */
@Mod(value = LItemFinderNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class LItemFinderNeoForgeClient {

    public static final String MOD_ID = "litemfinder";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final CachedItemTagResolver itemTags = new CachedItemTagResolver();

    public LItemFinderNeoForgeClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onClientLogout);
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
    }
}
