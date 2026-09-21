package dev.litemfinder.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Path from a root container through zero or more nested containers. */
public record ContainerPath(ContainerId root, List<Hop> hops) {

    public ContainerPath {
        Objects.requireNonNull(root, "root must not be null");
        hops = List.copyOf(Objects.requireNonNull(hops, "hops must not be null"));
    }

    public static ContainerPath root(ContainerId root) {
        return new ContainerPath(root, List.of());
    }

    public ContainerPath append(int parentSlot, ContainerId nestedContainer) {
        List<Hop> extended = new ArrayList<>(hops);
        extended.add(new Hop(parentSlot, nestedContainer));
        return new ContainerPath(root, extended);
    }

    public ContainerId leaf() {
        return hops.isEmpty() ? root : hops.getLast().container();
    }

    /** A nested container reached through one slot in its parent container. */
    public record Hop(int parentSlot, ContainerId container) {

        public Hop {
            if (parentSlot < 0) {
                throw new IllegalArgumentException("parentSlot must not be negative");
            }
            Objects.requireNonNull(container, "container must not be null");
        }
    }
}
