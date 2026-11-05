package dev.litemfinder.compatprobe;

import com.mojang.logging.LogUtils;
import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.search.IndexedSearchEngine;
import dev.litemfinder.neoforge.api.inventory.*;
import dev.litemfinder.neoforge.capture.BlockCaptureRequest;
import dev.litemfinder.neoforge.capture.BlockInventoryAdapters;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.mapping.MenuSnapshotMapper;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;
import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/** Separate installed addon, used only by runPluginProbeClient. No existing world or server is opened. */
@Mod(value = "litemfinder_compat_probe", dist = Dist.CLIENT)
public final class CompatibilityProbe {
    private static final ResourceLocation READER = ResourceLocation.parse("litemfinder_compat_probe:chest");
    private static boolean brokenRan;
    private static boolean workingPassed;
    private static boolean missingLoaded;
    private boolean setupPassed;
    private boolean exiting;
    private NavigationUiProbe uiProbe;
    private PlayerInventoryUiProbe playerUiProbe;
    private static int readState;

    public CompatibilityProbe(IEventBus modBus) {
        modBus.addListener(this::complete);
        NeoForge.EVENT_BUS.addListener(this::shutdown);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }

    private void complete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            require(brokenRan && workingPassed && !missingLoaded, "plugin discovery/gating/rollback did not complete");
            verifyNavigationBridge();
            LogUtils.getLogger().info("COMPAT PROBE PASSED: addon discovery, version gating, rollback, reader, mapping, SQLite, Core search");
            setupPassed = true;
        });
    }

    private void verifyNavigationBridge() {
        var provider = NavigationGateway.discover();
        if (!Boolean.getBoolean("litemfinder.compatProbe.navigationExpected")) {
            require(!ModList.get().isLoaded("litemnavigation") && provider.isEmpty(),
                    "inventory-only probe unexpectedly contains navigation");
            LogUtils.getLogger().info("NAVIGATION ABSENT PROBE PASSED: inventory starts without navigation");
            return;
        }
        require(ModList.get().isLoaded("litemnavigation"), "combined probe is missing the navigation Mod");
        var gateway = provider.orElseThrow(() -> new IllegalStateException("navigation service was not discovered across JARs"));
        require(gateway.getClass().getName().equals("dev.litemfinder.navigation.neoforge.InventoryNavigationBridge"),
                "unexpected navigation provider");
        var origin = new WorldPosition("bridge-probe", "minecraft:overworld", .5, 64, .5);
        var target = new WorldLocation("bridge-probe", NamespacedId.parse("minecraft:overworld"), 4, 64, 8);
        var result = gateway.route(origin, List.of(target)).toCompletableFuture().getNow(null);
        require(result != null && result.status() == NavigationGateway.Status.UNKNOWN && result.segments().isEmpty(),
                "navigation bridge did not safely return an unknown route without a world");
        require(gateway.revision() >= 0 && result.estimatedSeconds() == 0,
                "navigation bridge returned an invalid revision or estimate");
        require(result.report().reason() == dev.litemfinder.core.view.NavigationReport.Reason.NO_WORLD
                        && result.report().source() == dev.litemfinder.core.view.NavigationReport.Source.UNSPECIFIED
                        && result.report().checkedAt().isEmpty(),
                "navigation bridge lost the no-world reason or fabricated collision evidence");
        LogUtils.getLogger().info("NAVIGATION BRIDGE PROBE PASSED: service discovery, shared Core input, request/result and revision across installed JARs");
    }

    private void frame(RenderFrameEvent.Post event) {
        Minecraft game = Minecraft.getInstance();
        if (setupPassed && !exiting && Boolean.getBoolean("litemfinder.compatProbe.routeUi")) {
            if (uiProbe == null) {
                if (!(game.screen instanceof TitleScreen) || game.getOverlay() != null) return;
                uiProbe = new NavigationUiProbe();
            }
            if (!uiProbe.frame(game)) return;
            if (playerUiProbe == null) playerUiProbe = new PlayerInventoryUiProbe();
            if (!playerUiProbe.frame(game)) return;
        }
        if (setupPassed && !exiting && game.screen instanceof TitleScreen && game.getOverlay() == null
                && Boolean.getBoolean("litemfinder.compatProbe.exitAfterSetup")) {
            exiting = true;
            LogUtils.getLogger().info("COMPAT PROBE MAIN MENU READY: title screen rendered after resource loading");
            game.stop();
        }
    }

    private void shutdown(GameShuttingDownEvent event) {
        LogUtils.getLogger().info("COMPAT PROBE SHUTDOWN: normal client lifecycle");
    }

    public static final class BrokenPlugin implements InventoryPlugin {
        @Override public void register(InventoryPluginRegistration registration) {
            brokenRan = true;
            registration.blockItems(READER, ProbeChest.class, 100, chest -> InventoryReadResult.unavailable());
            throw new IllegalStateException("intentional registration failure; the next plugin needs the same reader ID");
        }
    }

    public static final class MustNotLoad implements InventoryPlugin {
        static { missingLoaded = true; }
        @Override public void register(InventoryPluginRegistration registration) {
            throw new IllegalStateException("dependency-gated entrypoint was loaded");
        }
    }

    public static final class WorkingPlugin implements InventoryPlugin {
        @Override public void register(InventoryPluginRegistration registration) {
            registration.blockItems(READER, ProbeChest.class, 100, chest -> {
                if (readState == 1) return InventoryReadResult.unavailable();
                if (readState == 2) throw new IllegalStateException("intentional transient reader failure");
                return InventoryReadResult.available(new BlockInventory("compat-probe",
                        IntStream.range(0, chest.getContainerSize()).mapToObj(chest::getItem).toList()));
            });
            ProbeChest chest = new ProbeChest();
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            var inventory = BlockInventoryAdapters.read(chest).orElseThrow();
            require(inventory.stacks().getFirst().getCount() == 7, "typed reader did not capture exact quantity");
            readState = 1;
            require(BlockInventoryAdapters.read(chest).isEmpty(), "unavailable reader fell through");
            readState = 2;
            require(BlockInventoryAdapters.read(chest).isEmpty(), "reader failure escaped isolation");
            readState = 0;
            require(BlockInventoryAdapters.read(chest).orElseThrow().stacks().getFirst().getCount() == 7,
                    "reader did not recover");
            var identity = ContainerIdentityFactory.block("compat-probe", NamespacedId.parse("minecraft:overworld"),
                    4, 64, 8, NamespacedId.parse("minecraft:chest"));
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var snapshot = new MenuSnapshotMapper(new CachedItemTagResolver()).map(
                    new BlockCaptureRequest(identity, inventory), registries, Instant.now());
            Path database = Path.of("compat-plugin-probe.db");
            try (var repository = new SqliteSnapshotRepository(database)) { repository.save(snapshot); }
            try (var repository = new SqliteSnapshotRepository(database)) {
                var restored = repository.find(snapshot.container().id()).orElseThrow();
                require(restored.equals(snapshot), "snapshot changed after closing and reopening SQLite");
                var index = new InMemoryStorageIndex();
                index.update(restored);
                require(new IndexedSearchEngine(index).findExact(ItemKey.parse("minecraft:diamond")).totalCount() == 7,
                        "plugin quantity did not reach Core search");
                require(repository.delete(snapshot.container().id()), "probe record cleanup failed");
            }
            chest.clearContent();
            require(BlockInventoryAdapters.read(chest).orElseThrow().stacks().stream().allMatch(ItemStack::isEmpty),
                    "confirmed empty inventory was not available");
            workingPassed = true;
        }
    }

    public static final class ProbeChest extends ChestBlockEntity {
        public ProbeChest() { super(new BlockPos(4, 64, 8), Blocks.CHEST.defaultBlockState()); }
    }

    private static void require(boolean success, String message) {
        if (!success) throw new IllegalStateException(message);
    }
}
