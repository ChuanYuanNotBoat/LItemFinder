package dev.litemfinder.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.file.Path;

/** NeoForge client entry point. Platform integration is added behind this boundary. */
@Mod(value = LItemFinderNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class LItemFinderNeoForgeClient {

    public static final String MOD_ID = "litemfinder";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LItemFinderNeoForgeClient() {
        LOGGER.info("LItem Finder NeoForge adapter initialized");
        if (Boolean.getBoolean("litemfinder.m0.sqliteProbe")) {
            Path database = Path.of("litemfinder-m0-sqlite-probe.db");
            SqliteRuntimeProbe.verify(database);
            LOGGER.info("LItem Finder SQLite runtime probe passed");
        }
    }
}
