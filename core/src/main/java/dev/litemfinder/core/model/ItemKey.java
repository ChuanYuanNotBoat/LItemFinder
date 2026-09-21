package dev.litemfinder.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable identity of an item as understood by Core.
 *
 * <p>The optional variant is an opaque, adapter-produced discriminator. Core does not interpret
 * Minecraft NBT or data components; adapters may later place a normalized digest or other stable
 * representation in this field.</p>
 */
public record ItemKey(NamespacedId itemId, String variant) implements Comparable<ItemKey> {

    public ItemKey {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(variant, "variant must not be null");
        if (!variant.equals(variant.trim())) {
            throw new IllegalArgumentException("variant must not have surrounding whitespace");
        }
    }

    public ItemKey(NamespacedId itemId) {
        this(itemId, "");
    }

    public ItemKey(String namespace, String id) {
        this(new NamespacedId(namespace, id));
    }

    public static ItemKey parse(String value) {
        return new ItemKey(NamespacedId.parse(value));
    }

    public ItemKey withVariant(String newVariant) {
        return new ItemKey(itemId, newVariant);
    }

    public String namespace() {
        return itemId.namespace();
    }

    public String id() {
        return itemId.path();
    }

    public boolean hasVariant() {
        return !variant.isEmpty();
    }

    public Optional<String> variantValue() {
        return hasVariant() ? Optional.of(variant) : Optional.empty();
    }

    @Override
    public int compareTo(ItemKey other) {
        Objects.requireNonNull(other, "other must not be null");
        int itemOrder = itemId.compareTo(other.itemId);
        return itemOrder != 0 ? itemOrder : variant.compareTo(other.variant);
    }

    @Override
    public String toString() {
        return hasVariant() ? itemId + "[" + variant + "]" : itemId.toString();
    }
}
