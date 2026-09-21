package dev.litemfinder.core.model;

/** Adapter-assigned stable identity of a container. */
public record ContainerId(String value) implements Comparable<ContainerId> {

    public ContainerId {
        value = ModelValidation.nonBlank(value, "container id");
    }

    @Override
    public int compareTo(ContainerId other) {
        if (other == null) {
            throw new NullPointerException("other must not be null");
        }
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
