package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.identity.ResolvedContainerIdentity;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Objects;

/** Client-thread-only request for M3 to turn a trusted live menu into an immutable snapshot. */
public record MenuCaptureRequest(
        ResolvedContainerIdentity identity,
        AbstractContainerMenu menu,
        MenuPartition partition,
        MenuFingerprint fingerprint,
        CaptureReason reason
) {

    public MenuCaptureRequest {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(menu, "menu must not be null");
        Objects.requireNonNull(partition, "partition must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (!partition.supported()) {
            throw new IllegalArgumentException("capture requests require a supported partition");
        }
    }
}
