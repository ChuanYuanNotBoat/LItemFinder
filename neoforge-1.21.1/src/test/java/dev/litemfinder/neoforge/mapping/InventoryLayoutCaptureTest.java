package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.neoforge.api.inventory.InventoryLayout;
import dev.litemfinder.neoforge.capture.CaptureReason;
import dev.litemfinder.neoforge.capture.MenuCaptureRequest;
import dev.litemfinder.neoforge.capture.MenuFingerprintCalculator;
import dev.litemfinder.neoforge.capture.MenuPartition;
import dev.litemfinder.neoforge.capture.MenuSlotRef;
import dev.litemfinder.neoforge.client.gui.ContainerSlotLayout;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.storage.sqlite.SqliteSnapshotRepository;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class InventoryLayoutCaptureTest {
    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() { Bootstrap.bootStrap(); }

    @Test
    void capturesActualForgeCoordinatesIncludingEmptySlotsAndRestoresThemAfterRestart() {
        // Actual TFC 4.2.9 CharcoalForgeContainer slot positions, in backing slot order.
        int[][] coordinates = {{80,70}, {98,52}, {62,52}, {116,34}, {44,34},
                {80,52}, {98,34}, {62,34}, {116,16}, {44,16},
                {152,16}, {152,34}, {152,52}, {152,70}};
        ForgeMenu menu = new ForgeMenu(coordinates);
        var identity = ContainerIdentityFactory.block("layout-test", NamespacedId.parse("minecraft:overworld"),
                10, 64, 20, NamespacedId.parse("tfc:charcoal_forge"));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var request = new MenuCaptureRequest(identity, menu, menu.partition,
                new MenuFingerprintCalculator().calculate(menu, menu.partition, registries), CaptureReason.OPEN_STABLE);
        var snapshot = new MenuSnapshotMapper(new CachedItemTagResolver()).map(request, registries, Instant.EPOCH);
        assertEquals(14, snapshot.slotCount());
        assertEquals(List.of(11, 12, 13), snapshot.slots().stream().map(slot -> slot.slot()).toList());

        Path database = temporaryDirectory.resolve("layout.db");
        try (var repository = new SqliteSnapshotRepository(database)) { repository.save(snapshot); }
        try (var reopened = new SqliteSnapshotRepository(database)) {
            var restored = reopened.find(snapshot.container().id()).orElseThrow();
            var positions = InventoryLayoutMetadata.read(restored.container().metadata(), restored.slotCount())
                    .orElseThrow().positions();
            assertEquals(14, positions.size(), "empty slots retain their positions");
            for (int slot = 0; slot < coordinates.length; slot++) {
                assertEquals(new InventoryLayout.Position(slot, coordinates[slot][0], coordinates[slot][1]), positions.get(slot));
            }
            var layout = ContainerSlotLayout.resolve(restored.container().metadata(), 14);
            var cells = layout.cells(320, 70, 240, 0);
            var upperRight = cells.stream().filter(cell -> cell.slot() == 10).findFirst().orElseThrow();
            var bottomRight = cells.stream().filter(cell -> cell.slot() == 13).findFirst().orElseThrow();
            assertEquals(upperRight.x(), bottomRight.x());
            assertEquals(54, bottomRight.y() - upperRight.y());
            assertEquals(14, cells.size());
        }
    }

    @Test
    void positionsStayBoundToBackingSlotsWhenMenuOrderDiffersAndLayoutChangesAffectFingerprint() {
        ForgeMenu first = new ForgeMenu(new int[][]{{80, 70}, {152, 16}});
        ForgeMenu changed = new ForgeMenu(new int[][]{{80, 70}, {152, 34}});
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        assertNotEquals(new MenuFingerprintCalculator().calculate(first, first.partition, registries),
                new MenuFingerprintCalculator().calculate(changed, changed.partition, registries));
    }

    @Test
    void historicalUnknownAndDamagedLayoutsFallBackToGridAndWideLayoutsFitWithoutChangingHitOrder() {
        var historical = ContainerSlotLayout.resolve(Map.of(), 14);
        assertEquals(9 * 18, historical.width());
        assertEquals(2 * 18, historical.height());
        Map<String, String> metadata = new HashMap<>();
        InventoryLayoutMetadata.write(metadata, new InventoryLayout(List.of(
                new InventoryLayout.Position(0, -80, -20), new InventoryLayout.Position(1, 600, -20))));
        var wide = ContainerSlotLayout.resolve(metadata, 2);
        assertTrue(wide.scale(200) < 1);
        var cells = wide.cells(200, 70, 240, 0);
        assertEquals(List.of(0, 1), cells.stream().map(ContainerSlotLayout.Cell::slot).toList());
        assertTrue(cells.getFirst().x() >= 8);
        assertTrue(cells.getLast().x() + cells.getLast().size() <= 193);
        metadata.remove("slotPosition.1");
        assertTrue(InventoryLayoutMetadata.read(metadata, 2).isEmpty(), "truncated metadata must not hide items");
        metadata.put("slotPosition.1", "999999999,0");
        assertTrue(InventoryLayoutMetadata.read(metadata, 2).isEmpty());
        metadata.put("slotLayoutVersion", "future");
        assertTrue(InventoryLayoutMetadata.read(metadata, 2).isEmpty());
    }

    @Test
    void scrollingUsesActualPixelHeightAndKeepsLastSlotReachable() {
        Map<String, String> metadata = new HashMap<>();
        InventoryLayoutMetadata.write(metadata, new InventoryLayout(List.of(
                new InventoryLayout.Position(0, 0, 0), new InventoryLayout.Position(1, 0, 400))));
        var layout = ContainerSlotLayout.resolve(metadata, 2);
        int maxScroll = layout.scrollLimit(320, 100);
        assertTrue(maxScroll > 0);
        assertEquals(List.of(0), layout.cells(320, 70, 170, 0).stream().map(ContainerSlotLayout.Cell::slot).toList());
        var bottom = layout.cells(320, 70, 170, maxScroll);
        assertEquals(List.of(1), bottom.stream().map(ContainerSlotLayout.Cell::slot).toList());
        assertTrue(bottom.getFirst().y() + bottom.getFirst().size() <= 170);
    }

    private static class ForgeMenu extends AbstractContainerMenu {
        final MenuPartition partition;
        ForgeMenu(int[][] positions) {
            super(MenuType.GENERIC_9x3, 1);
            var inventory = new SimpleContainer(positions.length);
            if (positions.length == 14) {
                for (int slot : new int[]{11, 12, 13}) inventory.setItem(slot, new ItemStack(Items.BRICK));
            }
            // Reverse menu order: layout must associate coordinates with backing slot identity.
            for (int slot = positions.length - 1; slot >= 0; slot--) {
                addSlot(new Slot(inventory, slot, positions[slot][0], positions[slot][1]));
            }
            partition = MenuPartition.supported("fixture_forge", IntStream.range(0, positions.length)
                    .mapToObj(slot -> new MenuSlotRef(positions.length - 1 - slot, slot)).toList());
        }
        public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        public boolean stillValid(Player player) { return true; }
    }
}
