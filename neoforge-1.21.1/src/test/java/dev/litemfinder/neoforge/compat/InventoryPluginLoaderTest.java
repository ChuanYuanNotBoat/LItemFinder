package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.api.inventory.*;
import dev.litemfinder.neoforge.capture.StorageMenuAdapters;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InventoryPluginLoaderTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }

    @Test void checksModPresenceAndVersionBeforeLoadingAnyOptionalEntrypoint() {
        var manifest = manifest("""
                {
                  "test:missing": {"entrypoint":"missing.Class", "required":{"absent":"[1,)"}},
                  "test:old": {"entrypoint":"wrong.Version", "required":{"target":"[3,)"}},
                  "test:good": {"entrypoint":"good.Plugin", "required":{"target":"[2,3)"}}
                }
                """);
        AtomicInteger calls = new AtomicInteger();
        try (var loader = new InventoryPluginLoader()) {
            var report = loader.load(List.of(manifest), Map.of("target", new DefaultArtifactVersion("2.5")), name -> {
                assertEquals("good.Plugin", name);
                calls.incrementAndGet();
                return registration -> { };
            });
            assertEquals(1, calls.get());
            assertEquals(1, report.count(InventoryPluginLoader.Status.LOADED));
            assertEquals(2, report.count(InventoryPluginLoader.Status.SKIPPED));
        }
    }

    @Test void malformedManifestEntryAndBrokenEntrypointDoNotStopOtherPlugins() {
        try (var loader = new InventoryPluginLoader()) {
            var report = loader.load(List.of(manifest("{invalid"), manifest("""
                    {
                      "bad id": {"entrypoint":"invalid.Id"},
                      "test:bad": {"entrypoint":"bad.Plugin"},
                      "test:good": {"entrypoint":"good.Plugin"},
                      "test:range": {"entrypoint":"bad.Range", "required":{"target":"1.0"}}
                    }
                    """)), Map.of(), name -> {
                if (name.equals("bad.Plugin")) throw new NoClassDefFoundError("optional missing API");
                assertEquals("good.Plugin", name);
                return registration -> { };
            });
            assertEquals(1, report.count(InventoryPluginLoader.Status.LOADED));
            assertEquals(4, report.count(InventoryPluginLoader.Status.FAILED));
        }
    }

    @Test void duplicatePluginIdsFailBothDefinitionsWithoutLoadingEither() {
        try (var loader = new InventoryPluginLoader()) {
            var report = loader.load(List.of(manifest("{\"test:same\":{\"entrypoint\":\"a.Plugin\"}}"),
                    manifest("{\"test:same\":{\"entrypoint\":\"b.Plugin\"}}")), Map.of(), name -> {
                fail("duplicate plugin must not be instantiated");
                return null;
            });
            assertEquals(2, report.count(InventoryPluginLoader.Status.FAILED));
        }
    }

    @Test void failedPluginRollsBackAllRegistrationsAndDoesNotBlockHealthyPlugin() {
        var menu = new PluginMenu();
        var inventory = new SimpleContainer(1);
        try (var loader = new InventoryPluginLoader()) {
            var report = loader.load(List.of(manifest("""
                    {"test:a_bad":{"entrypoint":"bad.Plugin"},"test:z_good":{"entrypoint":"good.Plugin"}}
                    """)), Map.of(), name -> registration -> {
                registration.menu(id(), PluginMenu.class, 10,
                        value -> InventoryReadResult.available(new MenuStorage(inventory, Optional.empty(), BlockPos.ZERO)));
                if (name.equals("bad.Plugin")) throw new IllegalStateException("failure after registration");
            });
            assertEquals(1, report.count(InventoryPluginLoader.Status.FAILED));
            assertEquals(1, report.count(InventoryPluginLoader.Status.LOADED));
            assertTrue(StorageMenuAdapters.resolve(menu).isPresent());
        }
        assertTrue(StorageMenuAdapters.resolve(menu).isEmpty());
    }

    @Test void registrationEndsAfterInitializationAndReloadRemovesOldReaders() {
        AtomicReference<InventoryPluginRegistration> captured = new AtomicReference<>();
        var menu = new PluginMenu();
        try (var loader = new InventoryPluginLoader()) {
            loader.load(List.of(manifest("{\"test:good\":{\"entrypoint\":\"good.Plugin\"}}")), Map.of(),
                    name -> registration -> {
                        captured.set(registration);
                        registration.menu(id(), PluginMenu.class, 10, value -> InventoryReadResult.unavailable());
                    });
            assertThrows(IllegalStateException.class, () -> captured.get().menu(id(), PluginMenu.class, 0,
                    value -> InventoryReadResult.unsupported()));
            loader.load(List.of(), Map.of(), name -> { throw new AssertionError("no plugins"); });
            assertTrue(StorageMenuAdapters.resolve(menu).isEmpty());
            assertEquals(0, loader.report().outcomes().size());
        }
    }

    private static InventoryPluginLoader.Manifest manifest(String json) {
        return new InventoryPluginLoader.Manifest("test-addon", json);
    }
    private static ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("test", "menu"); }
    static final class PluginMenu extends AbstractContainerMenu {
        PluginMenu() { super(MenuType.GENERIC_9x3, 1); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }
}
