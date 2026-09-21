package dev.litemfinder.core.model;

import java.util.Objects;

/**
 * A loader-independent resource identifier such as {@code minecraft:diamond}.
 */
public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {

    public NamespacedId {
        namespace = ModelValidation.namespace(namespace);
        path = ModelValidation.path(path);
    }

    public static NamespacedId parse(String value) {
        Objects.requireNonNull(value, "value must not be null");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("resource identifier must have the form namespace:path: " + value);
        }
        return new NamespacedId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public int compareTo(NamespacedId other) {
        Objects.requireNonNull(other, "other must not be null");
        int namespaceOrder = namespace.compareTo(other.namespace);
        return namespaceOrder != 0 ? namespaceOrder : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
