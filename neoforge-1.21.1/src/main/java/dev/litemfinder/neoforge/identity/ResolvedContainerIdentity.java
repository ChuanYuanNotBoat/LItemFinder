package dev.litemfinder.neoforge.identity;

import dev.litemfinder.core.model.ContainerRecord;

import java.util.Objects;

/** Core container record plus adapter-only persistence confidence. */
public record ResolvedContainerIdentity(ContainerRecord container, IdentityConfidence confidence) {

    public ResolvedContainerIdentity {
        Objects.requireNonNull(container, "container must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
    }

    public boolean persistable() {
        return confidence != IdentityConfidence.SESSION_ONLY;
    }
}
