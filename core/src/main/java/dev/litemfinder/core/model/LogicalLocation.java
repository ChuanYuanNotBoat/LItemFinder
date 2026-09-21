package dev.litemfinder.core.model;

/** Coordinate-free location such as a player's inventory or an ender chest. */
public record LogicalLocation(String scope, String key) implements ContainerLocation {

    public LogicalLocation {
        scope = ModelValidation.nonBlank(scope, "scope");
        key = ModelValidation.nonBlank(key, "key");
    }
}
