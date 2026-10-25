package dev.litemfinder.neoforge.compat;

import dev.litemfinder.core.index.InMemoryStorageIndex;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.neoforge.capture.CaptureReason;
import dev.litemfinder.neoforge.capture.AccessorySlot;
import dev.litemfinder.neoforge.capture.MenuCaptureRequest;
import dev.litemfinder.neoforge.capture.MenuFingerprintCalculator;
import dev.litemfinder.neoforge.capture.MenuPartition;
import dev.litemfinder.neoforge.capture.VanillaMenuSlotPartitioner;
import dev.litemfinder.neoforge.identity.ContainerIdentityFactory;
import dev.litemfinder.neoforge.mapping.CachedItemTagResolver;
import dev.litemfinder.neoforge.mapping.MenuSnapshotMapper;
import dev.litemfinder.neoforge.mapping.NestedItemContentsReader;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import net.neoforged.neoforge.items.ItemStackHandler;

import static org.junit.jupiter.api.Assertions.*;

class SophisticatedBackpackContentsTest {

    private static final UUID BACKPACK = UUID.fromString("b4539393-6f12-43ed-8628-8597fe0ff491");
    private static final Instant CAPTURED_AT = Instant.parse("2026-10-20T00:00:00Z");
    private final Map<UUID, CompoundTag> synced = new HashMap<>();
    // BundleItem stands in for the optional BackpackItem API: its registered namespace is different.
    // All nested/equipment tests exercise the real type-based identifier path, not a UUID-only bypass.
    private final SophisticatedBackpackContents backpacks = new SophisticatedBackpackContents(
            SophisticatedBackpackContents.identifiersForType(BundleItem.class), uuid -> Optional.ofNullable(synced.get(uuid))
    );
    private final NestedItemContentsReader reader = new NestedItemContentsReader(backpacks);
    private final MenuFingerprintCalculator fingerprints = new MenuFingerprintCalculator(reader);
    private final MenuSnapshotMapper mapper = new MenuSnapshotMapper(new CachedItemTagResolver(), reader);
    private RegistryAccess.Frozen registries;
    private Inventory player;
    private InventoryMenu menu;
    private MenuPartition partition;

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void preparePlayerInventory() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        player = new Inventory(null);
        player.setItem(3, backpack(BACKPACK));
        menu = new InventoryMenu(player, true, null);
        partition = new VanillaMenuSlotPartitioner(true).partition(menu, player);
    }

    @Test
    void mapsSyncedStorageWithOversizedStacksAndStablePlayerSlotPath() {
        // Fixture follows SophisticatedCore InventoryHandler: inventory/Size/Items/Slot.
        synced.put(BACKPACK, contents(54, item(53, "minecraft:diamond", 16384)));

        InventorySnapshot root = snapshot(0);
        InventorySnapshot nested = root.slots().getFirst().nestedContainer().orElseThrow();

        assertEquals(41, root.slotCount());
        assertEquals(3, root.slots().getFirst().slot());
        assertEquals(54, nested.slotCount());
        assertEquals(53, nested.slots().getFirst().slot());
        assertEquals(16384, nested.slots().getFirst().stack().count());
        assertTrue(nested.container().id().value().endsWith(":3"));
        assertEquals("3", nested.container().metadata().get("nestedPath"));
        assertEquals(16384, synced.get(BACKPACK).getCompound("inventory")
                .getList("Items", 10).getCompound(0).getInt("count"));
    }

    @Test
    void lateSyncAndContentOnlyChangesRefreshFingerprintAndReplaceIndexEntries() {
        ItemStack unchangedOuter = player.getItem(3).copy();
        var unavailable = fingerprints.calculate(menu, partition, registries);
        assertTrue(snapshot(0).slots().getFirst().nestedContainer().isEmpty());

        synced.put(BACKPACK, contents(27, item(0, "minecraft:diamond", 7)));
        var populated = fingerprints.calculate(menu, partition, registries);
        assertNotEquals(unavailable, populated);
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        InventorySnapshot initial = snapshot(1);
        ItemKey diamond = initial.slots().getFirst().nestedContainer().orElseThrow()
                .slots().getFirst().stack().item();
        index.update(initial);
        assertEquals(7, index.findExact(diamond).getFirst().stack().count());

        // Mutate the same live tag, as client caches may update data in place.
        synced.get(BACKPACK).getCompound("inventory").getList("Items", 10)
                .getCompound(0).putInt("count", 9);
        var changed = fingerprints.calculate(menu, partition, registries);
        assertNotEquals(populated, changed);
        index.update(snapshot(2));
        assertEquals(1, index.findExact(diamond).size());
        assertEquals(9, index.findExact(diamond).getFirst().stack().count());

        synced.put(BACKPACK, contents(27));
        assertNotEquals(changed, fingerprints.calculate(menu, partition, registries));
        index.update(snapshot(3));
        assertTrue(index.findExact(diamond).isEmpty());
        assertTrue(snapshot(3).slots().getFirst().nestedContainer().orElseThrow().isEmpty());
        assertTrue(ItemStack.matches(unchangedOuter, player.getItem(3)));
    }

    @Test
    void nestedBackpackChangesAreObservedInsideAnotherBackpackAndShulker() {
        UUID child = UUID.fromString("63c5dbe7-7b51-4bb0-88e8-a129ced802e1");
        CompoundTag childItem = (CompoundTag) backpack(child).save(registries);
        childItem.putInt("Slot", 5);
        synced.put(BACKPACK, contents(27, childItem));
        synced.put(child, contents(54, item(40, "minecraft:emerald", 2)));
        NonNullList<ItemStack> shulkerItems = NonNullList.withSize(27, ItemStack.EMPTY);
        shulkerItems.set(4, player.getItem(3));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(shulkerItems));
        player.setItem(3, shulker);

        var before = fingerprints.calculate(menu, partition, registries);
        InventorySnapshot first = snapshot(0).slots().getFirst().nestedContainer().orElseThrow();
        InventorySnapshot second = first.slots().getFirst().nestedContainer().orElseThrow();
        InventorySnapshot third = second.slots().getFirst().nestedContainer().orElseThrow();
        assertEquals("3.4.5", third.container().metadata().get("nestedPath"));
        assertEquals(2, third.slots().getFirst().stack().count());

        synced.put(child, contents(54, item(40, "minecraft:emerald", 3)));
        assertNotEquals(before, fingerprints.calculate(menu, partition, registries));
    }

    @Test
    void unavailableOrSettingsOnlyDataIsNotTreatedAsAnEmptyBackpack() {
        assertTrue(backpacks.read(player.getItem(3), registries).isEmpty());
        synced.put(BACKPACK, new CompoundTag());
        assertTrue(backpacks.read(player.getItem(3), registries).isEmpty());
        synced.put(BACKPACK, contents(0));
        assertTrue(backpacks.read(player.getItem(3), registries).isEmpty());
        synced.put(BACKPACK, contents(5000));
        assertTrue(backpacks.read(player.getItem(3), registries).isEmpty());
    }

    @Test
    void excludesUpgradeAndFilterItemsAndIgnoresInvalidStorageSlots() {
        CompoundTag data = contents(27, item(-1, "minecraft:diamond", 1),
                item(27, "minecraft:diamond", 1), item(1, "minecraft:emerald", 2));
        data.put("upgradeInventory", contents(9, item(0, "minecraft:diamond", 8)));
        data.put("settings", contents(9, item(0, "minecraft:diamond", 8)));
        synced.put(BACKPACK, data);

        var nested = backpacks.read(player.getItem(3), registries).orElseThrow();

        assertEquals(1, nested.stacks().size());
        assertEquals(Items.EMERALD, nested.stacks().get(1).getItem());
    }

    @Test
    void cacheClearingAndWorldChangesDoNotReuseOldDecodedContents() {
        synced.put(BACKPACK, contents(27, item(0, "minecraft:diamond", 1)));
        var before = backpacks.read(player.getItem(3), registries).orElseThrow();
        assertSame(before, backpacks.read(player.getItem(3), registries).orElseThrow());
        backpacks.clear();
        assertNotSame(before, backpacks.read(player.getItem(3), registries).orElseThrow());
        synced.clear();
        assertTrue(backpacks.read(player.getItem(3), registries).isEmpty());
    }

    @Test
    void optionalBridgeReadsExistingDataAndVanillaItemsAreNotMistakenForBackpacks() throws Exception {
        var storage = SophisticatedBackpackContents.storageReader(TestStorage.class);
        assertSame(TestStorage.CONTENTS, storage.apply(BACKPACK).orElseThrow());
        assertTrue(storage.apply(UUID.randomUUID()).isEmpty());
        assertEquals(BACKPACK, SophisticatedBackpackContents.storageId(player.getItem(3)).orElseThrow());
        assertTrue(SophisticatedBackpackContents.backpackId(player.getItem(3)).isEmpty());
        assertTrue(new SophisticatedBackpackContents().read(player.getItem(3), registries).isEmpty());
    }

    @Test
    void olderInstalledApiReadsExistingMapWithoutCreatingOrChangingBackpacks() throws Exception {
        var storage = SophisticatedBackpackContents.storageReader(LegacyStorage.class);
        assertSame(LegacyStorage.INSTANCE.backpackContents.get(BACKPACK), storage.apply(BACKPACK).orElseThrow());
        assertTrue(storage.apply(UUID.randomUUID()).isEmpty());
        assertEquals(1, LegacyStorage.INSTANCE.backpackContents.size());
    }

    @Test
    void backpackTypeWorksOutsideTheBaseNamespaceAndUuidAloneDoesNotMakeOtherItemsBackpacks() {
        var identifiers = SophisticatedBackpackContents.identifiersForType(BundleItem.class);
        assertNotEquals("sophisticatedbackpacks", BuiltInRegistries.ITEM.getKey(player.getItem(3).getItem()).getNamespace());
        assertEquals(BACKPACK, identifiers.apply(player.getItem(3)).orElseThrow());
        ItemStack unrelated = new ItemStack(Items.STICK);
        unrelated.set(DataComponents.CUSTOM_DATA, player.getItem(3).get(DataComponents.CUSTOM_DATA));
        assertEquals(BACKPACK, SophisticatedBackpackContents.storageId(unrelated).orElseThrow());
        assertTrue(identifiers.apply(unrelated).isEmpty());
    }

    @Test
    void backSlotContentsUpdateAndMovingToVanillaInventoryDoesNotDoubleCount() {
        player.setItem(3, ItemStack.EMPTY);
        ItemStackHandler back = new ItemStackHandler(1);
        back.setStackInSlot(0, backpack(BACKPACK));
        CuriosInventoryReader equipment = new CuriosInventoryReader(ignored -> Optional.of(Map.of("back", back)));
        synced.put(BACKPACK, contents(27, item(0, "minecraft:diamond", 7)));
        List<AccessorySlot> slots = equipment.read(null).orElseThrow();
        var before = fingerprints.calculate(menu, partition, registries, slots);
        InventorySnapshot first = equippedSnapshot(slots, 0);
        var equipped = first.slots().getFirst();
        assertEquals(41, equipped.slot());
        assertEquals("back:0", first.container().metadata().get("accessorySlot.41"));
        ItemKey diamond = equipped.nestedContainer().orElseThrow().slots().getFirst().stack().item();
        InMemoryStorageIndex index = new InMemoryStorageIndex();
        index.update(first);
        assertEquals(7, index.findExact(diamond).getFirst().stack().count());

        synced.put(BACKPACK, contents(27, item(0, "minecraft:diamond", 12)));
        assertNotEquals(before, fingerprints.calculate(menu, partition, registries, slots));
        index.update(equippedSnapshot(slots, 1));
        assertEquals(12, index.findExact(diamond).getFirst().stack().count());

        player.setItem(3, back.getStackInSlot(0));
        back.setStackInSlot(0, ItemStack.EMPTY);
        slots = equipment.read(null).orElseThrow();
        index.update(equippedSnapshot(slots, 2));
        assertEquals(1, index.findExact(diamond).size());
        assertEquals(3, index.rootSnapshots().getFirst().slots().getFirst().slot());

        player.setItem(3, ItemStack.EMPTY);
        index.update(equippedSnapshot(slots, 3));
        assertTrue(index.findExact(diamond).isEmpty());
    }

    private InventorySnapshot equippedSnapshot(List<AccessorySlot> slots, int seconds) {
        return mapper.map(new MenuCaptureRequest(
                ContainerIdentityFactory.playerInventory("test-world", BACKPACK), menu, partition,
                fingerprints.calculate(menu, partition, registries, slots), CaptureReason.CONTENT_STABLE, slots
        ), registries, CAPTURED_AT.plusSeconds(seconds));
    }

    private InventorySnapshot snapshot(int seconds) {
        return mapper.map(new MenuCaptureRequest(
                ContainerIdentityFactory.playerInventory("test-world", BACKPACK), menu, partition,
                fingerprints.calculate(menu, partition, registries), CaptureReason.CONTENT_STABLE
        ), registries, CAPTURED_AT.plusSeconds(seconds));
    }

    private static ItemStack backpack(UUID uuid) {
        // A vanilla carrier avoids requiring the optional mod in the unit-test runtime.
        ItemStack stack = new ItemStack(Items.BUNDLE);
        CompoundTag data = new CompoundTag();
        data.putUUID("contentsUuid", uuid);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static CompoundTag contents(int size, CompoundTag... items) {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", size);
        ListTag entries = new ListTag();
        for (CompoundTag item : items) {
            entries.add(item);
        }
        inventory.put("Items", entries);
        CompoundTag contents = new CompoundTag();
        contents.put("inventory", inventory);
        return contents;
    }

    private static CompoundTag item(int slot, String id, int count) {
        CompoundTag item = new CompoundTag();
        item.putInt("Slot", slot);
        item.putString("id", id);
        item.putInt("count", count);
        return item;
    }

    public static final class TestStorage {
        private static final TestStorage INSTANCE = new TestStorage();
        private static final CompoundTag CONTENTS = contents(27);

        public static TestStorage get() {
            return INSTANCE;
        }

        public Optional<CompoundTag> getBackpackContents(UUID uuid) {
            return uuid.equals(BACKPACK) ? Optional.of(CONTENTS) : Optional.empty();
        }
    }

    public static final class LegacyStorage {
        private static final LegacyStorage INSTANCE = new LegacyStorage();
        private final Map<UUID, CompoundTag> backpackContents = new HashMap<>(Map.of(BACKPACK, contents(27)));

        public static LegacyStorage get() { return INSTANCE; }

        public CompoundTag getOrCreateBackpackContents(UUID uuid) {
            throw new AssertionError("read-only indexing must never create backpack contents");
        }
    }
}
