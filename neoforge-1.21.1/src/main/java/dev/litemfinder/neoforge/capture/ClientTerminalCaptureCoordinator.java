package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.StorageTerminalAdapter;
import dev.litemfinder.neoforge.api.inventory.StorageTerminalView;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.mapping.MenuSnapshotMapper;
import dev.litemfinder.neoforge.persistence.SnapshotStorageCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;

/** Polls only the open terminal's supplied visible windows. No packets, clicks, automatic paging or chunk scans. */
public final class ClientTerminalCaptureCoordinator {
    private final MinecraftScopeResolver scopes = new MinecraftScopeResolver();
    private final MenuSnapshotMapper mapper;
    private final SnapshotStorageCoordinator storage;
    private Active active;
    private int ticks;
    private String last;
    private volatile boolean retry;

    public ClientTerminalCaptureCoordinator(MenuSnapshotMapper mapper, SnapshotStorageCoordinator storage) {
        this.mapper = mapper;
        this.storage = storage;
    }
    public void screenOpened(Screen screen, Minecraft minecraft) {
        close();
        if (!(screen instanceof AbstractContainerScreen<?> container) || minecraft.level == null) return;
        var scope = scopes.resolve(minecraft);
        var adapter = TerminalMenuAdapters.find(container.getMenu());
        if (scope.isPresent() && adapter.isPresent()) {
            active = new Active(screen, container.getMenu(), adapter.orElseThrow(), scope.orElseThrow(), minecraft.level.registryAccess());
            ticks = 0;
            last = null;
        }
    }
    public void clientTick(Minecraft minecraft) {
        if (active == null) return;
        if (minecraft.screen != active.screen() || minecraft.level == null || minecraft.player == null) { close(); return; }
        if (ticks++ % 5 == 0) poll();
    }
    public void screenClosed(Screen screen) { if (active != null && active.screen() == screen) close(); }
    public void close() { if (active != null) poll(); active = null; last = null; }

    private void poll() {
        try {
            var view = active.adapter().read(active.menu());
            if (view.isEmpty()) return;
            String signature = signature(view.orElseThrow());
            if (signature.equals(last) && !retry) return;
            retry = false;
            Instant now = Instant.now();
            for (var window : view.orElseThrow().windows()) {
                if (!window.identity().container().location().scope().equals(active.scope())) continue;
                if (!window.complete() && window.slots().isEmpty() && window.fluids().isEmpty()) continue;
                var snapshot = mapper.map(view.orElseThrow(), window, active.registries(), now);
                storage.submitPartial(snapshot, window.slots().keySet(), window.complete()).thenAccept(accepted -> {
                    if (!accepted) retry = true;
                }).exceptionally(failure -> {
                    com.mojang.logging.LogUtils.getLogger().warn("Unable to update visible terminal storage", failure);
                    retry = true;
                    return null;
                });
            }
            last = signature;
        } catch (RuntimeException | LinkageError optionalFailure) {
            // A temporarily loading view is not proof that its invisible or formerly visible contents vanished.
        }
    }

    static String signature(StorageTerminalView view) {
        StringBuilder signature = new StringBuilder(view.networkId()).append('|').append(view.viewKey()).append('|').append(view.name());
        for (var window : view.windows()) {
            signature.append('|').append(window.identity().container().id()).append(':').append(window.slotCount())
                    .append(':').append(window.complete());
            window.slots().forEach((slot, stack) -> signature.append(':').append(slot).append('=')
                    .append(ItemStack.hashItemAndComponents(stack)).append('/').append(stack.getCount()));
            window.fluids().ifPresent(fluids -> signature.append("/f=").append(fluids.fingerprint()));
        }
        return signature.toString();
    }
    private record Active(Screen screen, AbstractContainerMenu menu, StorageTerminalAdapter adapter,
                          String scope, HolderLookup.Provider registries) { }
}
