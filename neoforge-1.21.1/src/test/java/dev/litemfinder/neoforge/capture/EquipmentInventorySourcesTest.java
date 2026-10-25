package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.EquipmentInventorySource;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EquipmentInventorySourcesTest {
    @BeforeAll
    static void bootstrapMinecraft() { Bootstrap.bootStrap(); }

    @Test
    void sourcesRetainUnavailableDataClearExplicitlyAndNeverLeakAcrossPlayers() throws Exception {
        AtomicReference<Optional<List<AccessorySlot>>> state = new AtomicReference<>(Optional.of(
                List.of(new AccessorySlot("back", 0, new ItemStack(Items.DIAMOND, 2)))));
        EquipmentInventorySource source = new EquipmentInventorySource() {
            public String id() { return "test-equipment"; }
            public Optional<List<AccessorySlot>> read(LivingEntity wearer) { return state.get(); }
        };
        LivingEntity first = mock(LivingEntity.class);
        LivingEntity second = mock(LivingEntity.class);
        EquipmentInventorySources sources = new EquipmentInventorySources();
        try (var registration = EquipmentInventorySources.register(source)) {
            var initial = sources.read(first).orElseThrow();
            assertEquals("test-equipment/back:0", initial.getFirst().key());
            state.set(Optional.empty());
            var retained = sources.read(first).orElseThrow();
            assertEquals(initial.getFirst().key(), retained.getFirst().key());
            assertTrue(ItemStack.matches(initial.getFirst().stack(), retained.getFirst().stack()));
            assertTrue(sources.read(second).orElseThrow().isEmpty());
            state.set(Optional.of(initial));
            assertFalse(sources.read(second).orElseThrow().isEmpty());
            state.set(Optional.of(List.of()));
            assertTrue(sources.read(second).orElseThrow().isEmpty());
        }
        assertTrue(sources.read(first).orElseThrow().isEmpty());
    }

    @Test
    void slotLayoutChangesAreDetectedEvenWhenEverySlotIsEmpty() {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var player = new net.minecraft.world.entity.player.Inventory(null);
        var menu = new net.minecraft.world.inventory.InventoryMenu(player, true, null);
        var partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
        var fingerprints = new MenuFingerprintCalculator();
        var back = fingerprints.calculate(menu, partition, registries,
                List.of(new AccessorySlot("back", 0, ItemStack.EMPTY)));
        var belt = fingerprints.calculate(menu, partition, registries,
                List.of(new AccessorySlot("belt", 0, ItemStack.EMPTY)));
        assertNotEquals(back, belt);
    }
}
