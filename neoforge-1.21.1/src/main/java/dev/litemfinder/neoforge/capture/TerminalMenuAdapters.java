package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.StorageTerminalAdapter;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TerminalMenuAdapters {
    private static final CopyOnWriteArrayList<StorageTerminalAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private TerminalMenuAdapters() { }
    public static AutoCloseable register(StorageTerminalAdapter adapter) {
        ADAPTERS.add(0, java.util.Objects.requireNonNull(adapter));
        return () -> ADAPTERS.remove(adapter);
    }
    public static Optional<StorageTerminalAdapter> find(AbstractContainerMenu menu) {
        for (var adapter : ADAPTERS) {
            try { if (adapter.supports(menu)) return Optional.of(adapter); }
            catch (RuntimeException | LinkageError ignored) { }
        }
        return Optional.empty();
    }
}
