package dev.litemfinder.core.model;

import java.util.Objects;

/** Extensible container kind; modded container types are not collapsed into an enum. */
public record ContainerType(NamespacedId id) implements Comparable<ContainerType> {

    public static final ContainerType CHEST = parse("minecraft:chest");
    public static final ContainerType PLAYER_INVENTORY = parse("minecraft:player_inventory");
    public static final ContainerType ENDER_CHEST = parse("minecraft:ender_chest");
    public static final ContainerType SHULKER_BOX = parse("minecraft:shulker_box");

    public ContainerType {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static ContainerType parse(String value) {
        return new ContainerType(NamespacedId.parse(value));
    }

    @Override
    public int compareTo(ContainerType other) {
        Objects.requireNonNull(other, "other must not be null");
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
