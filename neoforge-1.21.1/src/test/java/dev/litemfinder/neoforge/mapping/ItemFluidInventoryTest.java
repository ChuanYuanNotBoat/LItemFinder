package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.snapshot.ItemFluidSnapshotMetadata;

import dev.litemfinder.core.model.*;
import dev.litemfinder.neoforge.api.inventory.FluidInventory;
import dev.litemfinder.neoforge.capture.*;
import dev.litemfinder.core.view.*;
import dev.litemfinder.neoforge.compat.SophisticatedBackpackContents;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemFluidInventoryTest {
    @BeforeAll static void bootstrap() { Bootstrap.bootStrap(); }
    final RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    boolean available = true;
    final NestedItemContentsReader reader = new NestedItemContentsReader(new SophisticatedBackpackContents(), (stack, lookup) -> {
        if (!available) return Optional.empty();
        long amount = stack.is(Items.GLASS_BOTTLE) ? 500 : stack.is(Items.BUCKET) ? 1000 : 0;
        return Optional.of(new FluidInventory(amount == 0 ? List.of() : List.of(
                new FluidInventory.Tank(0, new FluidStack(Fluids.WATER, 1), amount, amount))));
    });
    @Test void stackedBucketsBottleNestedAndEquippedSourcesHaveRealPathsAndScaleWithCounts() {
        Inventory player = new Inventory(null);
        player.setItem(0, new net.minecraft.world.item.ItemStack(Items.BUCKET, 3));
        var shulker = new net.minecraft.world.item.ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new net.minecraft.world.item.ItemStack(Items.GLASS_BOTTLE))));
        player.setItem(2, shulker);
        var first = snapshot(player, List.of(new AccessorySlot("curios:back", 0, new net.minecraft.world.item.ItemStack(Items.GLASS_BOTTLE))), 0);
        var row = FluidOverview.from(ContainerOverview.from(List.of(first))).rows().getFirst();
        assertEquals(4000, row.amount());
        assertEquals(4000, row.capacity());
        assertEquals(Set.of(List.of(0), List.of(2, 0), List.of(41)), row.sources().stream().map(FluidOverview.Source::itemPath).collect(java.util.stream.Collectors.toSet()));
        assertTrue(RecordedSourceLocations.resolve(ContainerOverview.from(List.of(first)), Set.of(first.container().id())).isEmpty());
        player.setItem(0, new net.minecraft.world.item.ItemStack(Items.BUCKET));
        var next = snapshot(player, List.of(), 1);
        assertEquals(1500, FluidOverview.from(ContainerOverview.from(List.of(next))).rows().getFirst().amount());
        player.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
        assertEquals(500, FluidOverview.from(ContainerOverview.from(List.of(snapshot(player, List.of(), 2)))).rows().getFirst().amount());
    }
    @Test void failedReadPreservesMovedItemButRemovalClearsItsFluid() {
        Inventory player = new Inventory(null);
        player.setItem(0, new net.minecraft.world.item.ItemStack(Items.BUCKET, 3));
        var previous = snapshot(player, List.of(), 0);
        player.setItem(3, player.removeItemNoUpdate(0));
        available = false;
        var incoming = ItemFluidSnapshotMetadata.preserveUnavailable(snapshot(player, List.of(), 1), previous);
        var row = FluidOverview.from(ContainerOverview.from(List.of(incoming))).rows().getFirst();
        assertEquals(3000, row.amount());
        assertEquals(List.of(3), row.sources().getFirst().itemPath());
        assertEquals(Instant.EPOCH, row.sources().getFirst().observedAt());
        player.setItem(3, net.minecraft.world.item.ItemStack.EMPTY);
        var removed = ItemFluidSnapshotMetadata.preserveUnavailable(snapshot(player, List.of(), 2), incoming);
        assertTrue(FluidOverview.from(ContainerOverview.from(List.of(removed))).rows().isEmpty());
    }
    @Test void navigationKeepsWorldContainerAndIgnoresCarriedRootsAndMissingIds() {
        var id = new ContainerId("world-barrel");
        var barrel = new InventorySnapshot(new ContainerRecord(id, new ContainerType(NamespacedId.parse("tfc:barrel")),
                new WorldLocation("world", NamespacedId.parse("minecraft:overworld"), 7, 64, 9)), 0, Instant.EPOCH, List.of());
        var carried = snapshot(new Inventory(null), List.of(), 0);
        var locations = RecordedSourceLocations.resolve(ContainerOverview.from(List.of(barrel, carried)),
                List.of(id, id, carried.container().id(), new ContainerId("missing")));
        assertEquals(List.of(barrel.container().location()), locations);
    }
    private InventorySnapshot snapshot(Inventory player, List<AccessorySlot> accessories, int seconds) {
        var menu = new InventoryMenu(player, true, null);
        var partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
        return new MenuSnapshotMapper(new CachedItemTagResolver(), reader).map(new MenuCaptureRequest(
                ContainerIdentityFactory.playerInventory("world", UUID.fromString("00000000-0000-0000-0000-000000000001")),
                menu, partition, new MenuFingerprintCalculator(reader).calculate(menu, partition, registries, accessories),
                CaptureReason.CONTENT_STABLE, accessories), registries, Instant.EPOCH.plusSeconds(seconds));
    }
}
