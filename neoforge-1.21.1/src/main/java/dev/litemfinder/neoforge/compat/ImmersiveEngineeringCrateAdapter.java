package dev.litemfinder.neoforge.compat;

import dev.litemfinder.neoforge.api.inventory.MenuStorage;
import dev.litemfinder.neoforge.api.inventory.StorageMenuAdapter;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.lang.reflect.Field;
import java.util.Optional;

/** IE's crate uses a client SimpleContainer and has no owner getter; its real slots are still authoritative. */
public final class ImmersiveEngineeringCrateAdapter implements StorageMenuAdapter {
    private final Optional<Access> access;
    public ImmersiveEngineeringCrateAdapter() {
        Optional<Access> found;
        try {
            found = Optional.of(accessFor(Class.forName("blusunrize.immersiveengineering.common.gui.CrateMenu", false,
                    ImmersiveEngineeringCrateAdapter.class.getClassLoader())));
        } catch (ReflectiveOperationException | LinkageError absent) { found = Optional.empty(); }
        access = found;
    }
    ImmersiveEngineeringCrateAdapter(Class<?> type) throws ReflectiveOperationException { access = Optional.of(accessFor(type)); }
    private static Access accessFor(Class<?> type) throws ReflectiveOperationException {
        Field container = type.getDeclaredField("container");
        if (!container.trySetAccessible()) throw new IllegalAccessException("crate container is inaccessible");
        return new Access(type, container);
    }
    @Override public Optional<MenuStorage> resolve(AbstractContainerMenu menu) {
        if (access.isEmpty() || !access.orElseThrow().type().isInstance(menu)) return Optional.empty();
        try {
            Object value = access.orElseThrow().container().get(menu);
            return value instanceof Container inventory && inventory.getContainerSize() == 27
                    ? Optional.of(MenuStorage.withoutPosition(inventory)) : Optional.empty();
        } catch (IllegalAccessException ignored) { return Optional.empty(); }
    }
    private record Access(Class<?> type, Field container) { }
}
