package dev.litemfinder.neoforge.api.inventory;

import java.util.Objects;
import java.util.Optional;

/** Distinguishes a nonmatching reader from a matching storage whose data has not arrived yet. */
public record InventoryReadResult<T>(State state, Optional<T> value) {
    public enum State { UNSUPPORTED, UNAVAILABLE, AVAILABLE }

    public InventoryReadResult {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if ((state == State.AVAILABLE) != value.isPresent()) {
            throw new IllegalArgumentException("only AVAILABLE must contain a value");
        }
    }

    public static <T> InventoryReadResult<T> unsupported() {
        return new InventoryReadResult<>(State.UNSUPPORTED, Optional.empty());
    }

    /** Claims this storage and prevents lower-priority readers from committing unsynchronized defaults. */
    public static <T> InventoryReadResult<T> unavailable() {
        return new InventoryReadResult<>(State.UNAVAILABLE, Optional.empty());
    }

    /** A confirmed empty inventory is still AVAILABLE, with an empty inventory value. */
    public static <T> InventoryReadResult<T> available(T value) {
        return new InventoryReadResult<>(State.AVAILABLE, Optional.of(value));
    }
}
