package dev.litemfinder.core.model;

import java.util.Map;
import java.util.Objects;

/** Identity and descriptive data for a storage unit. */
public record ContainerRecord(
        ContainerId id,
        ContainerType type,
        ContainerLocation location,
        Map<String, String> metadata
) {

    public ContainerRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(location, "location must not be null");
        metadata = ModelValidation.immutableMetadata(metadata);
    }

    public ContainerRecord(ContainerId id, ContainerType type, ContainerLocation location) {
        this(id, type, location, Map.of());
    }
}
