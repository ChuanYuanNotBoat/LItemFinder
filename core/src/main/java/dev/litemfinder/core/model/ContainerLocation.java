package dev.litemfinder.core.model;

/**
 * Location of a container without exposing a game object.
 *
 * <p>World containers use {@link WorldLocation}; inventories and other coordinate-free storage use
 * {@link LogicalLocation}.</p>
 */
public sealed interface ContainerLocation permits WorldLocation, LogicalLocation {

    /** Adapter-defined world, save, or server scope used to prevent cross-world collisions. */
    String scope();
}
