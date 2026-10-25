package dev.litemfinder.neoforge.compat;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.mapping.NestedItemContents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/** Optional, read-only access to contents already synced by Sophisticated Backpacks. */
public final class SophisticatedBackpackContents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_CACHED_BACKPACKS = 256;
    private static final int MAX_SLOTS = 4096;
    private static final ResourceLocation STORAGE_UUID = ResourceLocation.fromNamespaceAndPath(
            "sophisticatedcore", "storage_uuid"
    );
    private static final Function<ItemStack, Optional<UUID>> BACKPACK_IDENTIFIERS = optionalIdentifiers();

    private final Function<ItemStack, Optional<UUID>> identifiers;
    private final Function<UUID, Optional<CompoundTag>> storage;
    private final Map<UUID, CachedContents> decoded = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<UUID, CachedTanks> decodedTanks = new LinkedHashMap<>(16, 0.75f, true);
    private final TankDecoder tankDecoder;

    public SophisticatedBackpackContents() {
        this(SophisticatedBackpackContents::backpackId, optionalStorage());
    }

    SophisticatedBackpackContents(
            Function<ItemStack, Optional<UUID>> identifiers,
            Function<UUID, Optional<CompoundTag>> storage
    ) {
        this(identifiers, storage, SophisticatedBackpackTankContents::readSynced);
    }

    SophisticatedBackpackContents(Function<ItemStack, Optional<UUID>> identifiers,
            Function<UUID, Optional<CompoundTag>> storage, TankDecoder tankDecoder) {
        this.identifiers = identifiers;
        this.storage = storage;
        this.tankDecoder = tankDecoder;
    }

    public boolean accepts(ItemStack stack) { return identifiers.apply(stack).isPresent(); }
    public static boolean isBackpackItem(ItemStack stack) {
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem")) return true;
        }
        return false;
    }
    public Optional<dev.litemfinder.neoforge.api.inventory.FluidInventory> readFluids(ItemStack stack, HolderLookup.Provider registries) {
        return identifiers.apply(stack).flatMap(uuid -> storage.apply(uuid).flatMap(contents -> {
            if (!contents.contains("upgradeInventory", Tag.TAG_COMPOUND) || !contents.contains("inventory", Tag.TAG_COMPOUND)) return Optional.empty();
            int size = contents.getCompound("inventory").getInt("Size");
            if (size < 1 || size > MAX_SLOTS) return Optional.empty();
            var upgrades = contents.getCompound("upgradeInventory");
            var cached = decodedTanks.get(uuid);
            if (cached != null && cached.size() == size && cached.registries() == registries && cached.source().equals(upgrades)) return cached.tanks();
            var tanks = tankDecoder.read(stack, contents, registries);
            decodedTanks.put(uuid, new CachedTanks(upgrades.copy(), size, registries, tanks));
            if (decodedTanks.size() > MAX_CACHED_BACKPACKS) decodedTanks.remove(decodedTanks.keySet().iterator().next());
            return tanks;
        }));
    }

    public Optional<NestedItemContents> read(ItemStack stack, HolderLookup.Provider registries) {
        return identifiers.apply(stack).flatMap(uuid -> {
            Optional<CompoundTag> contents = storage.apply(uuid);
            // Settings-only syncs do not establish an empty inventory.
            if (contents.isEmpty() || !contents.orElseThrow().contains("inventory", Tag.TAG_COMPOUND)) {
                decoded.remove(uuid);
                return Optional.empty();
            }
            CompoundTag inventory = contents.orElseThrow().getCompound("inventory");
            CachedContents cached = decoded.get(uuid);
            if (cached != null && cached.registries() == registries && cached.source().equals(inventory)) {
                return cached.contents();
            }
            Optional<NestedItemContents> result = decode(inventory, registries);
            decoded.put(uuid, new CachedContents(inventory.copy(), registries, result));
            if (decoded.size() > MAX_CACHED_BACKPACKS) {
                decoded.remove(decoded.keySet().iterator().next());
            }
            return result;
        });
    }

    public void clear() {
        decoded.clear();
        decodedTanks.clear();
    }

    static Optional<UUID> backpackId(ItemStack stack) {
        return BACKPACK_IDENTIFIERS.apply(stack);
    }

    /** Add-ons such as tfcsbu register BackpackItem instances in their own namespace. */
    static Function<ItemStack, Optional<UUID>> identifiersForType(Class<?> backpackItem) {
        return stack -> backpackItem.isInstance(stack.getItem()) ? storageId(stack) : Optional.empty();
    }

    private static Function<ItemStack, Optional<UUID>> optionalIdentifiers() {
        try {
            return identifiersForType(Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem",
                    false, SophisticatedBackpackContents.class.getClassLoader()));
        } catch (ClassNotFoundException absent) {
            return stack -> Optional.empty();
        } catch (LinkageError exception) {
            LOGGER.warn("Sophisticated Backpacks item type is incompatible", exception);
            return stack -> Optional.empty();
        }
    }

    static Optional<UUID> storageId(ItemStack stack) {
        DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE.get(STORAGE_UUID);
        if (component != null && stack.get(component) instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        // Older 1.21.1 releases keep the identifier in minecraft:custom_data.
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.hasUUID("contentsUuid")) {
                return Optional.of(tag.getUUID("contentsUuid"));
            }
        }
        return Optional.empty();
    }

    private static Optional<NestedItemContents> decode(CompoundTag inventory, HolderLookup.Provider registries) {
        int size = inventory.getInt("Size");
        if (size < 1 || size > MAX_SLOTS) {
            return Optional.empty();
        }
        Map<Integer, ItemStack> stacks = new TreeMap<>();
        for (Tag tag : inventory.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) tag;
            if (!item.contains("Slot", Tag.TAG_INT)) {
                continue;
            }
            int slot = item.getInt("Slot");
            int count = item.contains("count", Tag.TAG_INT) ? item.getInt("count") : 1;
            if (slot < 0 || slot >= size || count < 1) {
                continue;
            }
            // Stack upgrades permit counts above the vanilla ItemStack codec's limit.
            CompoundTag single = item.copy();
            single.putInt("count", 1);
            ItemStack.parse(registries, single).ifPresent(parsed -> {
                parsed.setCount(count);
                stacks.put(slot, parsed);
            });
        }
        return Optional.of(new NestedItemContents(size, stacks));
    }

    private static Function<UUID, Optional<CompoundTag>> optionalStorage() {
        try {
            Class<?> storageClass = Class.forName(
                    "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage",
                    false,
                    SophisticatedBackpackContents.class.getClassLoader()
            );
            return storageReader(storageClass);
        } catch (ClassNotFoundException ignored) {
            return uuid -> Optional.empty();
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Sophisticated Backpacks contents integration is unavailable", exception);
            return uuid -> Optional.empty();
        }
    }

    static Function<UUID, Optional<CompoundTag>> storageReader(Class<?> storageClass)
            throws ReflectiveOperationException {
        Method get = storageClass.getMethod("get");
        Function<Object, Function<UUID, Object>> lookup;
        try {
            Method getContents = storageClass.getMethod("getBackpackContents", UUID.class);
            lookup = instance -> uuid -> {
                try {
                    return getContents.invoke(instance, uuid);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            };
        } catch (NoSuchMethodException olderVersion) {
            // 3.25.74 exposes only getOrCreateBackpackContents. Read its existing map instead of creating data.
            Field contents = storageClass.getDeclaredField("backpackContents");
            if (!contents.trySetAccessible()) {
                throw new IllegalAccessException("Synced backpack contents map is inaccessible");
            }
            lookup = instance -> uuid -> {
                try {
                    Object value = contents.get(instance);
                    return value instanceof Map<?, ?> map ? map.get(uuid) : null;
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException(exception);
                }
            };
        }
        var read = lookup;
        return new Function<>() {
            private boolean failed;

            @Override
            public Optional<CompoundTag> apply(UUID uuid) {
                if (failed) {
                    return Optional.empty();
                }
                try {
                    Object result = read.apply(get.invoke(null)).apply(uuid);
                    if (result instanceof CompoundTag tag) {
                        return Optional.of(tag);
                    }
                    if (result instanceof Optional<?> optional && optional.orElse(null) instanceof CompoundTag tag) {
                        return Optional.of(tag);
                    }
                } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                    failed = true;
                    LOGGER.warn("Unable to read synced Sophisticated Backpacks contents", exception);
                }
                return Optional.empty();
            }
        };
    }

    private record CachedContents(
            CompoundTag source,
            HolderLookup.Provider registries,
            Optional<NestedItemContents> contents
    ) {
    }
    @FunctionalInterface interface TankDecoder {
        Optional<dev.litemfinder.neoforge.api.inventory.FluidInventory> read(ItemStack backpack, CompoundTag contents, HolderLookup.Provider registries);
    }
    private record CachedTanks(CompoundTag source, int size, HolderLookup.Provider registries,
                               Optional<dev.litemfinder.neoforge.api.inventory.FluidInventory> tanks) { }
}
