package dev.litemfinder.neoforge.mapping;

import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.neoforge.capture.CaptureReason;
import dev.litemfinder.neoforge.capture.MenuCaptureRequest;
import dev.litemfinder.neoforge.capture.MenuFingerprintCalculator;
import dev.litemfinder.neoforge.capture.MenuPartition;
import dev.litemfinder.neoforge.capture.VanillaMenuSlotPartitioner;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.neoforge.identity.ScopeIdFactory;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSnapshotMapperTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-09-21T00:00:00Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @Test
    void mapsTrustedRootSlotsAndRecursiveShulkerContents() {
        Inventory player = new Inventory(null);
        player.setItem(0, new ItemStack(Items.EMERALD, 64));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, shulkerContaining(shulkerContaining(new ItemStack(Items.DIAMOND, 3))));
        ChestMenu menu = ChestMenu.threeRows(1, player, chest);
        MenuPartition partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
        String scope = ScopeIdFactory.singleplayer("snapshot-test-world");
        var identity = ContainerIdentityFactory.block(
                scope,
                NamespacedId.parse("minecraft:overworld"),
                10,
                64,
                20,
                NamespacedId.parse("minecraft:generic_9x3")
        );
        MenuCaptureRequest request = new MenuCaptureRequest(
                identity,
                menu,
                partition,
                new MenuFingerprintCalculator().calculate(menu, partition),
                CaptureReason.OPEN_STABLE
        );
        CachedItemTagResolver tags = new CachedItemTagResolver();

        InventorySnapshot root = new MenuSnapshotMapper(tags).map(request, registries(), CAPTURED_AT);

        assertEquals(27, root.slotCount());
        assertEquals(1, root.slots().size());
        InventorySnapshot firstShulker = root.slots().getFirst().nestedContainer().orElseThrow();
        InventorySnapshot secondShulker = firstShulker.slots().getFirst().nestedContainer().orElseThrow();
        assertEquals(27, firstShulker.slotCount());
        assertEquals(27, secondShulker.slotCount());
        assertEquals(Items.DIAMOND, item(secondShulker.slots().getFirst().stack().item().itemId()));
        assertEquals(3, secondShulker.slots().getFirst().stack().count());
        assertTrue(firstShulker.container().id().value().endsWith(":0"));
        assertTrue(secondShulker.container().id().value().endsWith(":0.0"));
        assertEquals(2, tags.cachedItemCount());
        assertTrue(root.slots().stream().noneMatch(slot -> slot.stack().item().itemId().path().equals("emerald")));
    }

    private static ItemStack shulkerContaining(ItemStack content) {
        NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.set(0, content);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return shulker;
    }

    private static net.minecraft.world.item.Item item(NamespacedId id) {
        return BuiltInRegistries.ITEM.get(MinecraftIds.fromCore(id));
    }

    private static RegistryAccess.Frozen registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}
