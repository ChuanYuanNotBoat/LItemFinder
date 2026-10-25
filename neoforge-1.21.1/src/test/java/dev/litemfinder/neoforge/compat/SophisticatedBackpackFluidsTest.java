package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import dev.litemfinder.neoforge.capture.*;
import dev.litemfinder.neoforge.client.view.*;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.neoforge.mapping.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SophisticatedBackpackFluidsTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    @Test void cacheOnlyChangesRefreshEquippedTankAndMovingUpgradeDoesNotDoubleCount() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        UUID uuid = UUID.fromString("fb8bd0da-e1dd-4359-9ef6-6588c17b82c1");
        CompoundTag cache = new CompoundTag(), inventory = new CompoundTag(), upgrades = new CompoundTag();
        inventory.putInt("Size", 27);
        cache.put("inventory", inventory);
        upgrades.putInt("amount", 2000);
        cache.put("upgradeInventory", upgrades);
        var backpacks = new SophisticatedBackpackContents(stack -> stack.is(Items.BUNDLE) ? Optional.of(uuid) : Optional.empty(),
                id -> Optional.of(cache), (stack, data, lookup) -> Optional.of(tanks(data.getCompound("upgradeInventory").getInt("amount"), 8000)));
        var reader = new NestedItemContentsReader(backpacks, (stack, lookup) -> Optional.of(stack.is(Items.CHEST)
                ? tanks(3000, 0) : new FluidInventory(List.of())));
        var fingerprints = new MenuFingerprintCalculator(reader);
        var mapper = new MenuSnapshotMapper(new CachedItemTagResolver(), reader);
        var player = new Inventory(null);
        var menu = new InventoryMenu(player, true, null);
        var partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
        ItemStack bag = new ItemStack(Items.BUNDLE), unchangedBag = bag.copy();
        var equipment = List.of(new AccessorySlot("curios:back", 0, bag));
        var before = fingerprints.calculate(menu, partition, registries, equipment);
        upgrades.putInt("amount", 3000);
        var after = fingerprints.calculate(menu, partition, registries, equipment);
        assertNotEquals(before, after, "the outer backpack stack need not change when the synced tank changes");
        assertTrue(ItemStack.matches(bag, unchangedBag));
        var identity = ContainerIdentityFactory.playerInventory("world", uuid);
        var first = mapper.map(new MenuCaptureRequest(identity, menu, partition, after, CaptureReason.CONTENT_STABLE, equipment), registries, Instant.EPOCH);
        var row = FluidOverview.from(ContainerOverview.from(List.of(first))).rows().getFirst();
        assertEquals(3000, row.amount());
        assertEquals(8000, row.capacity());
        assertEquals(List.of(41), row.sources().getFirst().itemPath());
        upgrades.putInt("amount", 0);
        player.setItem(0, new ItemStack(Items.CHEST)); // detached upgrade with its own stored contents
        var next = mapper.map(new MenuCaptureRequest(identity, menu, partition,
                fingerprints.calculate(menu, partition, registries, equipment), CaptureReason.CONTENT_STABLE, equipment), registries, Instant.EPOCH.plusSeconds(1));
        var detached = FluidOverview.from(ContainerOverview.from(List.of(next))).rows().getFirst();
        assertEquals(3000, detached.amount());
        assertEquals(1, detached.sources().size());
        assertEquals(0, detached.capacity(), "loose upgrade has no backpack row/stack capacity context");
        assertEquals(List.of(0), detached.sources().getFirst().itemPath());
        player.setItem(0, ItemStack.EMPTY);
        upgrades.putInt("amount", 3000);
        var installed = mapper.map(new MenuCaptureRequest(identity, menu, partition,
                fingerprints.calculate(menu, partition, registries, equipment), CaptureReason.CONTENT_STABLE, equipment), registries, Instant.EPOCH.plusSeconds(2));
        var restored = FluidOverview.from(ContainerOverview.from(List.of(installed))).rows().getFirst();
        assertEquals(3000, restored.amount());
        assertEquals(1, restored.sources().size());
        assertEquals(8000, restored.capacity());
        backpacks.clear();
    }
    private static FluidInventory tanks(int amount, int capacity) {
        return new FluidInventory(amount == 0 ? List.of() : List.of(new FluidInventory.Tank(0, new FluidStack(Fluids.WATER, 1), amount, capacity)));
    }
}
