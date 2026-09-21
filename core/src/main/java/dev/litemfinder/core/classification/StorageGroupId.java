package dev.litemfinder.core.classification;

import java.util.Objects;

/** Stable identity of a user-defined storage group. */
public record StorageGroupId(String value) implements Comparable<StorageGroupId> {

    public StorageGroupId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("value must be non-blank without surrounding whitespace");
        }
    }

    @Override
    public int compareTo(StorageGroupId other) {
        Objects.requireNonNull(other, "other must not be null");
        return value.compareTo(other.value);
    }
}
