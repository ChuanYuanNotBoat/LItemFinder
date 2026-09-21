package dev.litemfinder.neoforge.capture;

import com.mojang.logging.LogUtils;
import dev.litemfinder.neoforge.diagnostics.CaptureDiagnostics;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.identity.IdentityConfidence;
import dev.litemfinder.neoforge.identity.ResolvedContainerIdentity;
import dev.litemfinder.neoforge.lifecycle.DebouncedCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Owns one open supported menu and emits capture requests only after stable changes. */
public final class ClientMenuCaptureCoordinator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int OPEN_DELAY_TICKS = 1;
    private static final int CHANGE_DEBOUNCE_TICKS = 5;

    private final MinecraftScopeResolver scopes;
    private final RecentInteractionTracker interactions;
    private final VanillaMenuSlotPartitioner partitions;
    private final MenuFingerprintCalculator fingerprints;
    private final CaptureDiagnostics diagnostics;
    private final Consumer<MenuCaptureRequest> captureSink;

    private ActiveCapture active;

    public ClientMenuCaptureCoordinator(
            MinecraftScopeResolver scopes,
            RecentInteractionTracker interactions,
            VanillaMenuSlotPartitioner partitions,
            MenuFingerprintCalculator fingerprints,
            CaptureDiagnostics diagnostics,
            Consumer<MenuCaptureRequest> captureSink
    ) {
        this.scopes = Objects.requireNonNull(scopes, "scopes must not be null");
        this.interactions = Objects.requireNonNull(interactions, "interactions must not be null");
        this.partitions = Objects.requireNonNull(partitions, "partitions must not be null");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        this.captureSink = Objects.requireNonNull(captureSink, "captureSink must not be null");
    }

    public void screenOpened(Screen screen, Minecraft minecraft) {
        Objects.requireNonNull(screen, "screen must not be null");
        Objects.requireNonNull(minecraft, "minecraft must not be null");
        closeActive();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || minecraft.player == null) {
            return;
        }

        Optional<String> scope = scopes.resolve(minecraft);
        if (scope.isEmpty()) {
            diagnostics.skipped("no_active_scope");
            LOGGER.debug("Skipping menu capture: no active world/server scope");
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        if (menu instanceof InventoryMenu
                || menu instanceof CreativeModeInventoryScreen.ItemPickerMenu) {
            return;
        }
        MenuPartition partition = partitions.partition(menu, minecraft.player.getInventory());
        if (!partition.supported()) {
            diagnostics.skipped(partition.kind());
            LOGGER.debug("Skipping menu capture: {}", partition.kind());
            return;
        }

        ResolvedContainerIdentity identity = interactions.resolve(scope.orElseThrow(), minecraft.player, menu);
        if (identity.confidence() == IdentityConfidence.SESSION_ONLY) {
            diagnostics.degraded("session_only_identity:" + partition.kind());
        }
        MenuFingerprint initial = fingerprints.calculate(menu, partition);
        DebouncedCapture<MenuFingerprint> debounce = new DebouncedCapture<>(
                OPEN_DELAY_TICKS,
                CHANGE_DEBOUNCE_TICKS
        );
        debounce.opened(initial);
        active = new ActiveCapture(screen, menu, partition, identity, debounce, false);
    }

    public void clientTick(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft must not be null");
        interactions.advanceTick();
        ActiveCapture current = active;
        if (current == null) {
            return;
        }
        if (minecraft.screen != current.screen()) {
            closeActive();
            return;
        }

        MenuFingerprint fingerprint = fingerprints.calculate(current.menu(), current.partition());
        current.debounce().tick(fingerprint).ifPresent(stable -> {
            CaptureReason reason = current.captured() ? CaptureReason.CONTENT_STABLE : CaptureReason.OPEN_STABLE;
            active = current.withCaptured(true);
            emit(current, stable, reason);
        });
    }

    public void screenClosed(Screen screen) {
        if (active != null && active.screen() == screen) {
            closeActive();
        }
    }

    public void reset() {
        active = null;
        interactions.clear();
    }

    public void close() {
        closeActive();
    }

    private void closeActive() {
        ActiveCapture current = active;
        active = null;
        if (current == null || !current.debounce().isOpen()) {
            return;
        }
        MenuFingerprint fingerprint = fingerprints.calculate(current.menu(), current.partition());
        current.debounce().closed(fingerprint).ifPresent(finalValue -> emit(
                current,
                finalValue,
                CaptureReason.CLOSE_FINAL
        ));
    }

    private void emit(ActiveCapture current, MenuFingerprint fingerprint, CaptureReason reason) {
        diagnostics.captured(reason);
        captureSink.accept(new MenuCaptureRequest(
                current.identity(),
                current.menu(),
                current.partition(),
                fingerprint,
                reason
        ));
    }

    private record ActiveCapture(
            Screen screen,
            AbstractContainerMenu menu,
            MenuPartition partition,
            ResolvedContainerIdentity identity,
            DebouncedCapture<MenuFingerprint> debounce,
            boolean captured
    ) {

        private ActiveCapture withCaptured(boolean value) {
            return new ActiveCapture(screen, menu, partition, identity, debounce, value);
        }
    }
}
