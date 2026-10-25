package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.diagnostics.CaptureDiagnostics;
import dev.litemfinder.neoforge.identity.MinecraftContainerIdentityResolver;
import dev.litemfinder.neoforge.identity.MinecraftScopeResolver;
import dev.litemfinder.neoforge.identity.ResolvedContainerIdentity;
import dev.litemfinder.neoforge.lifecycle.DebouncedCapture;
import dev.litemfinder.neoforge.api.inventory.EquipmentInventorySource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.function.Consumer;

/** Observes the player's real inventory even when no inventory screen is open. */
public final class ClientPlayerInventoryCaptureCoordinator {

    private static final int INITIAL_DELAY_TICKS = 1;
    private static final int CHANGE_DEBOUNCE_TICKS = 5;

    private final MinecraftScopeResolver scopes;
    private final MinecraftContainerIdentityResolver identities;
    private final VanillaMenuSlotPartitioner partitions;
    private final MenuFingerprintCalculator fingerprints;
    private final CaptureDiagnostics diagnostics;
    private final Consumer<MenuCaptureRequest> captureSink;
    private final EquipmentInventorySource accessories;
    private List<AccessorySlot> observedAccessories = List.of();

    private ActiveCapture active;
    private String lastSkipReason;

    public ClientPlayerInventoryCaptureCoordinator(
            MinecraftScopeResolver scopes,
            MinecraftContainerIdentityResolver identities,
            VanillaMenuSlotPartitioner partitions,
            MenuFingerprintCalculator fingerprints,
            CaptureDiagnostics diagnostics,
            Consumer<MenuCaptureRequest> captureSink
    ) {
        this(scopes, identities, partitions, fingerprints, diagnostics, captureSink, new EquipmentInventorySources());
    }

    public ClientPlayerInventoryCaptureCoordinator(
            MinecraftScopeResolver scopes, MinecraftContainerIdentityResolver identities,
            VanillaMenuSlotPartitioner partitions, MenuFingerprintCalculator fingerprints,
            CaptureDiagnostics diagnostics, Consumer<MenuCaptureRequest> captureSink, EquipmentInventorySource accessories
    ) {
        this.scopes = Objects.requireNonNull(scopes, "scopes must not be null");
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.partitions = Objects.requireNonNull(partitions, "partitions must not be null");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        this.captureSink = Objects.requireNonNull(captureSink, "captureSink must not be null");
        this.accessories = Objects.requireNonNull(accessories);
    }

    public void clientTick(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft must not be null");
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }

        InventoryMenu menu = minecraft.player.inventoryMenu;
        ActiveCapture current = active;
        if (current == null || current.menu() != menu) observedAccessories = List.of();
        accessories.read(minecraft.player).ifPresent(value -> observedAccessories = value);
        if (current == null || current.menu() != menu) {
            Optional<String> resolvedScope = scopes.resolve(minecraft);
            if (resolvedScope.isEmpty()) {
                skipOnce("player_inventory:no_active_scope");
                return;
            }
            String scope = resolvedScope.orElseThrow();
            MenuPartition partition = partitions.partition(menu, minecraft.player.getInventory());
            if (!partition.supported()) {
                skipOnce("player_inventory:" + partition.kind());
                active = null;
                return;
            }
            HolderLookup.Provider registries = minecraft.level.registryAccess();
            MenuFingerprint initial = fingerprints.calculate(menu, partition, registries, observedAccessories);
            DebouncedCapture<MenuFingerprint> debounce = new DebouncedCapture<>(
                    INITIAL_DELAY_TICKS,
                    CHANGE_DEBOUNCE_TICKS
            );
            debounce.opened(initial);
            ResolvedContainerIdentity identity = identities.playerInventory(scope, minecraft.player);
            current = new ActiveCapture(scope, menu, partition, identity, registries, debounce, false);
            active = current;
        }

        lastSkipReason = null;
        MenuFingerprint fingerprint = fingerprints.calculate(current.menu(), current.partition(), current.registries(), observedAccessories);
        ActiveCapture capture = current;
        current.debounce().tick(fingerprint).ifPresent(stable -> {
            CaptureReason reason = capture.captured() ? CaptureReason.CONTENT_STABLE : CaptureReason.OPEN_STABLE;
            active = capture.withCaptured(true);
            emit(capture, stable, reason);
        });
    }

    /** Emits an outstanding final change before a normal disconnect or game shutdown. */
    public void close() {
        ActiveCapture current = active;
        active = null;
        if (current == null || !current.debounce().isOpen()) {
            return;
        }
        MenuFingerprint fingerprint = fingerprints.calculate(current.menu(), current.partition(), current.registries(), observedAccessories);
        current.debounce().closed(fingerprint).ifPresent(finalValue -> emit(
                current,
                finalValue,
                CaptureReason.CLOSE_FINAL
        ));
    }

    public void reset() {
        active = null;
        lastSkipReason = null;
        observedAccessories = List.of();
        if (accessories instanceof EquipmentInventorySources sources) sources.clear();
    }

    private void emit(ActiveCapture current, MenuFingerprint fingerprint, CaptureReason reason) {
        diagnostics.captured(reason);
        captureSink.accept(new MenuCaptureRequest(
                current.identity(),
                current.menu(),
                current.partition(),
                fingerprint,
                reason,
                observedAccessories
        ));
    }

    private void skipOnce(String reason) {
        if (!reason.equals(lastSkipReason)) {
            diagnostics.skipped(reason);
            lastSkipReason = reason;
        }
    }

    private record ActiveCapture(
            String scope,
            InventoryMenu menu,
            MenuPartition partition,
            ResolvedContainerIdentity identity,
            HolderLookup.Provider registries,
            DebouncedCapture<MenuFingerprint> debounce,
            boolean captured
    ) {

        private ActiveCapture withCaptured(boolean value) {
            return new ActiveCapture(scope, menu, partition, identity, registries, debounce, value);
        }
    }
}
