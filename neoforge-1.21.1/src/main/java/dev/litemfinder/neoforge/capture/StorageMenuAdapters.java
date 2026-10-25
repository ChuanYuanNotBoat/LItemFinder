package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.MenuStorage;
import dev.litemfinder.neoforge.api.inventory.StorageMenuAdapter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.Optional;

/** Finds explicit backing storage contracts instead of guessing a mod menu's slot layout. */
public final class StorageMenuAdapters {
    private static final ClassValue<java.util.concurrent.ConcurrentMap<String, Optional<Method>>> ACCESSORS = new ClassValue<>() {
        @Override protected java.util.concurrent.ConcurrentMap<String, Optional<Method>> computeValue(Class<?> type) {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }
    };
    private static final java.util.concurrent.CopyOnWriteArrayList<StorageMenuAdapter> ADAPTERS =
            new java.util.concurrent.CopyOnWriteArrayList<>(java.util.List.of(
                    new dev.litemfinder.neoforge.compat.ImmersiveEngineeringCrateAdapter()));

    private StorageMenuAdapters() {
    }

    /** Registered adapters take precedence over the common public accessor contracts. */
    public static AutoCloseable register(StorageMenuAdapter adapter) {
        java.util.Objects.requireNonNull(adapter);
        ADAPTERS.add(0, adapter);
        return () -> ADAPTERS.remove(adapter);
    }

    public static Optional<MenuStorage> resolve(AbstractContainerMenu menu) {
        for (StorageMenuAdapter adapter : ADAPTERS) {
            try {
                Optional<MenuStorage> storage = adapter.resolve(menu);
                if (storage.isPresent()) return storage;
            } catch (RuntimeException | LinkageError ignored) {
                // A broken optional adapter must not break the player's container screen.
            }
        }
        return commonStorage(menu);
    }

    private static Optional<MenuStorage> commonStorage(AbstractContainerMenu menu) {
        Object owner = accessor(menu, "getBlockEntity").orElseGet(() -> accessor(menu, "getTileEntity").orElse(null));
        if (owner instanceof Optional<?> optional) owner = optional.orElse(null);
        if (owner instanceof BlockEntity block) {
            Object inventory = inventory(block);
            if (inventory != null) {
                return Optional.of(new MenuStorage(inventory, Optional.of(block), block.getBlockPos()));
            }
        }
        // Sophisticated Storage exposes a wrapper and a confirmed block position. Carried backpacks
        // remain nested under the player, so opening one never creates a duplicate root inventory.
        Object position = accessor(menu, "getBlockPosition").orElse(null);
        if (position instanceof Optional<?> optional) position = optional.orElse(null);
        if (position instanceof BlockPos blockPosition) {
            Object wrapper = accessor(menu, "getStorageWrapper").orElse(null);
            Object inventory = wrapper == null ? null : accessor(wrapper, "getInventoryHandler").orElse(null);
            if (inventory instanceof IItemHandler || inventory instanceof Container) {
                return Optional.of(new MenuStorage(inventory, Optional.empty(), blockPosition.immutable()));
            }
        }
        return Optional.empty();
    }

    private static Object inventory(BlockEntity owner) {
        if (owner instanceof Container) return owner;
        for (String name : new String[]{"getInventory", "getItemHandler", "getInventoryHandler"}) {
            Object value = accessor(owner, name).orElse(null);
            if (value instanceof IItemHandler || value instanceof Container) return value;
        }
        return null;
    }

    private static Optional<Object> accessor(Object owner, String name) {
        try {
            var resolved = ACCESSORS.get(owner.getClass()).computeIfAbsent(name, key -> {
                try {
                    Method candidate = owner.getClass().getMethod(key);
                    return candidate.trySetAccessible() ? Optional.of(candidate) : Optional.empty();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return Optional.empty(); }
            });
            if (resolved.isEmpty()) return Optional.empty();
            Method method = resolved.orElseThrow();
            return Optional.ofNullable(method.invoke(owner));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

}
