package dev.litemfinder.neoforge.capture;

import dev.litemfinder.core.model.*;
import dev.litemfinder.neoforge.api.inventory.*;
import dev.litemfinder.neoforge.client.view.ContainerOverview;
import dev.litemfinder.neoforge.identity.*;
import dev.litemfinder.neoforge.mapping.*;
import dev.litemfinder.neoforge.persistence.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageTerminalCaptureTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    private final String scope = ScopeIdFactory.singleplayer("terminal-fixture-world");
    private final RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private final MenuSnapshotMapper mapper = new MenuSnapshotMapper(new CachedItemTagResolver());

    private ResolvedContainerIdentity identity(int x) {
        return ContainerIdentityFactory.block(scope, NamespacedId.parse("minecraft:overworld"), x, 64, 0,
                NamespacedId.parse("minecraft:generic_9x3"), NamespacedId.parse("minecraft:chest"));
    }
    private StorageTerminalView view(String network, String page, StorageTerminalView.Window... windows) {
        return new StorageTerminalView(network, "Warehouse", page, List.of(windows));
    }
    private StorageTerminalView.Window window(int x, Map<Integer, ItemStack> slots) {
        return new StorageTerminalView.Window(identity(x), 27, slots, false, Optional.empty());
    }
    private InventorySnapshot map(StorageTerminalView view, int seconds) {
        return mapper.map(view, view.windows().getFirst(), registries, Instant.EPOCH.plusSeconds(seconds));
    }

    @Test void queuedPagesMergePhysicalRootWithoutDoubleCountingAndSurviveRestart() {
        var first = view("fixture:network-a", "filter=all;page=0", window(1, Map.of(0, new ItemStack(Items.DIAMOND, 4))));
        var second = view("fixture:network-a", "filter=all;page=1", window(1, Map.of(20, new ItemStack(Items.EMERALD, 7))));
        try (var storage = new SnapshotStorageCoordinator(directory)) {
            // Queue both before waiting: a latest-value buffer would incorrectly drop the first page.
            var pendingFirst = storage.submitPartial(map(first, 0), Set.of(0), false);
            var pendingSecond = storage.submitPartial(map(second, 1), Set.of(20), false);
            assertTrue(pendingFirst.join()); assertTrue(pendingSecond.join());
            assertEquals(1, storage.currentIndex().rootContainerCount());
            assertEquals(2, storage.currentIndex().allEntries().size());
            assertEquals(11, storage.currentIndex().allEntries().stream().mapToLong(entry -> entry.stack().count()).sum());
            var overview = ContainerOverview.from(storage.currentIndex().rootSnapshots());
            assertEquals(1, overview.groups().size());
            assertEquals(List.of(identity(1).container().id()), overview.groups().getFirst().members());
            var root = storage.currentIndex().rootSnapshots().getFirst();
            assertEquals(Instant.EPOCH, root.capturedAt(), "invisible page cannot receive a fresh timestamp");
            assertEquals("PARTIAL", root.container().metadata().get("captureCoverage"));
        }
        try (var storage = new SnapshotStorageCoordinator(directory)) {
            assertTrue(storage.submitPartial(map(second, 2), Set.of(20), false).join());
            assertEquals(2, storage.currentIndex().allEntries().size());
            assertEquals(1, ContainerOverview.from(storage.currentIndex().rootSnapshots()).groups().size());
        }
    }

    @Test void visibleEmptySlotClearsOnlyItsRangeAndFilteringDoesNotClearUnseenItems() {
        var first = view("net", "page0", window(1, Map.of(0, new ItemStack(Items.DIAMOND, 4), 20, new ItemStack(Items.EMERALD, 7))));
        var previous = map(first, 0);
        var emptyPage = view("net", "filter=redstone", window(1, Map.of()));
        var unchanged = PartialSnapshotMerger.merge(previous, map(emptyPage, 1), Set.of(), false).orElseThrow();
        assertEquals(2, unchanged.slots().size());
        var clear = view("net", "page0", window(1, Map.of(0, ItemStack.EMPTY)));
        var changed = PartialSnapshotMerger.merge(unchanged, map(clear, 2), Set.of(0), false).orElseThrow();
        assertEquals(List.of(20), changed.slots().stream().map(SlotSnapshot::slot).toList());
        assertNotEquals(ClientTerminalCaptureCoordinator.signature(first), ClientTerminalCaptureCoordinator.signature(clear));
        assertNotEquals(ClientTerminalCaptureCoordinator.signature(first), ClientTerminalCaptureCoordinator.signature(emptyPage));
    }

    @Test void fullUpdatesReplaceOldContentsButPartialCapacityHintsCannotDeleteUnseenSlots() {
        var previous = map(view("net", "page1", window(1, Map.of(26, new ItemStack(Items.DIAMOND)))), 0);
        var small = new StorageTerminalView.Window(identity(1), 9, Map.of(0, new ItemStack(Items.EMERALD)), false, Optional.empty());
        var partial = mapper.map(view("net", "page0", small), small, registries, Instant.EPOCH.plusSeconds(1));
        var merged = PartialSnapshotMerger.merge(previous, partial, Set.of(0), false).orElseThrow();
        assertEquals(27, merged.slotCount()); assertEquals(2, merged.slots().size());
        Map<Integer, ItemStack> empty = new TreeMap<>();
        for (int slot = 0; slot < 27; slot++) empty.put(slot, ItemStack.EMPTY);
        var whole = new StorageTerminalView.Window(identity(1), 27, empty, true, Optional.empty());
        var complete = map(view("net", "all", whole), 2);
        var cleared = PartialSnapshotMerger.merge(merged, complete, empty.keySet(), true).orElseThrow();
        assertTrue(cleared.slots().isEmpty()); assertFalse(cleared.container().metadata().containsKey("captureCoverage"));
        assertEquals(Instant.EPOCH.plusSeconds(2), cleared.capturedAt());
        assertTrue(PartialSnapshotMerger.merge(cleared, partial, Set.of(0), false).isEmpty());
    }

    @Test void confirmedNetworkGroupsAreIndependentAndDirectRecaptureRetainsMembership() {
        var a = map(view("net-a", "page0", window(1, Map.of())), 0);
        var b = map(view("net-a", "page1", window(2, Map.of())), 0);
        var c = map(view("net-b", "page0", window(3, Map.of())), 0);
        var groups = ContainerOverview.from(List.of(a, b, c)).groups();
        assertEquals(2, groups.size());
        assertEquals(List.of(1, 2), groups.stream().map(group -> group.members().size()).sorted().toList());
        var direct = new InventorySnapshot(identity(1).container(), 27, Instant.EPOCH.plusSeconds(2), List.of());
        assertEquals(a.container().metadata().get("storageNetworkId"),
                PartialSnapshotMerger.retainNetwork(direct, a).container().metadata().get("storageNetworkId"));
    }

    @Test void visibleFluidTanksMergeIndependentlyAndDoNotPromoteInvisibleTankTime() {
        var firstFluids = new FluidInventory(List.of(new FluidInventory.Tank(0, new FluidStack(Fluids.WATER, 1), 2000, 10000)));
        var secondFluids = new FluidInventory(List.of(new FluidInventory.Tank(1, new FluidStack(Fluids.LAVA, 1), 5000, 10000)));
        var first = new StorageTerminalView.Window(identity(1), 27, Map.of(), false, Optional.of(firstFluids));
        var second = new StorageTerminalView.Window(identity(1), 27, Map.of(), false, Optional.of(secondFluids));
        var previous = map(view("net", "tank0", first), 0);
        var next = PartialSnapshotMerger.merge(previous, map(view("net", "tank1", second), 1), Set.of(), false).orElseThrow();
        var fluids = FluidInventoryMetadata.read(next.container().metadata()).orElseThrow();
        assertEquals(2, fluids.tanks().size()); assertEquals(Instant.EPOCH, fluids.observedAt());
        assertTrue(next.slots().isEmpty());
    }

    @Test void loadingTerminalBypassesOrdinarySlotCaptureAndUnresolvedAggregatesAreRejected() throws Exception {
        Inventory player = new Inventory(null);
        var menu = ChestMenu.threeRows(1, player, new SimpleContainer(27));
        var adapter = new StorageTerminalAdapter() {
            public boolean supports(net.minecraft.world.inventory.AbstractContainerMenu candidate) { return candidate == menu; }
            public Optional<StorageTerminalView> read(net.minecraft.world.inventory.AbstractContainerMenu candidate) { return Optional.empty(); }
        };
        try (var registration = TerminalMenuAdapters.register(adapter)) {
            assertTrue(TerminalMenuAdapters.find(menu).isPresent());
            assertFalse(new VanillaMenuSlotPartitioner(true).partition(menu, player).supported());
            assertTrue(adapter.read(menu).isEmpty());
        }
        assertTrue(new VanillaMenuSlotPartitioner(true).partition(menu, player).supported());
        var aggregate = ContainerIdentityFactory.session(scope, UUID.randomUUID(), NamespacedId.parse("fixture:terminal"));
        assertThrows(IllegalArgumentException.class, () -> new StorageTerminalView.Window(aggregate, 27, Map.of(), false, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StorageTerminalView.Window(identity(1), 27, Map.of(27, ItemStack.EMPTY), false, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StorageTerminalView.Window(identity(1), 27, Map.of(0, ItemStack.EMPTY), true, Optional.empty()));
    }
}
