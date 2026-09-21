package dev.litemfinder.neoforge.lifecycle;

import java.util.Objects;
import java.util.Optional;

/** Pure state machine deciding when a stable menu fingerprint should become a full snapshot. */
public final class DebouncedCapture<T> {

    private final int openDelayTicks;
    private final int debounceTicks;

    private boolean open;
    private boolean emitted;
    private int ticksSinceOpen;
    private int stableTicks;
    private T candidate;
    private T lastEmitted;

    public DebouncedCapture(int openDelayTicks, int debounceTicks) {
        if (openDelayTicks < 1) {
            throw new IllegalArgumentException("openDelayTicks must be at least 1");
        }
        if (debounceTicks < 1) {
            throw new IllegalArgumentException("debounceTicks must be at least 1");
        }
        this.openDelayTicks = openDelayTicks;
        this.debounceTicks = debounceTicks;
    }

    public void opened(T initialFingerprint) {
        candidate = Objects.requireNonNull(initialFingerprint, "initialFingerprint must not be null");
        lastEmitted = null;
        ticksSinceOpen = 0;
        stableTicks = 0;
        emitted = false;
        open = true;
    }

    public Optional<T> tick(T fingerprint) {
        ensureOpen();
        observe(fingerprint);
        ticksSinceOpen++;

        if (!emitted && ticksSinceOpen >= openDelayTicks) {
            return emit();
        }
        if (emitted && !Objects.equals(candidate, lastEmitted) && stableTicks >= debounceTicks) {
            return emit();
        }
        return Optional.empty();
    }

    public Optional<T> closed(T finalFingerprint) {
        ensureOpen();
        observe(finalFingerprint);
        open = false;
        if (!emitted || !Objects.equals(candidate, lastEmitted)) {
            return emit();
        }
        return Optional.empty();
    }

    public boolean isOpen() {
        return open;
    }

    private void observe(T fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        if (Objects.equals(candidate, fingerprint)) {
            stableTicks++;
        } else {
            candidate = fingerprint;
            stableTicks = 1;
        }
    }

    private Optional<T> emit() {
        lastEmitted = candidate;
        emitted = true;
        return Optional.of(candidate);
    }

    private void ensureOpen() {
        if (!open) {
            throw new IllegalStateException("capture is not open");
        }
    }
}
