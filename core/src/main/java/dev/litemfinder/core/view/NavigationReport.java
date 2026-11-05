package dev.litemfinder.core.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Optional route evidence. Missing provider data stays unspecified rather than acquiring invented certainty. */
public record NavigationReport(Reason reason, Estimate estimate, int verifiedStops,
                               Optional<Instant> checkedAt, Confidence confidence, Source source) {
    public enum Reason { UNSPECIFIED, NONE, NO_WORLD, OTHER_DIMENSION, UNOBSERVED, BUDGET, WORLD_CHANGED, UNSUPPORTED, PROVIDER_FAILED }
    public enum Estimate { UNSPECIFIED, BASE_SPEED, PROVIDER_ESTIMATE, MEASURED }
    public enum Confidence { UNSPECIFIED, PROVIDER_REPORTED, LOCALLY_CHECKED }
    public enum Source { UNSPECIFIED, CLIENT_COLLISION, TRAJECTORY, PROVIDER }

    public NavigationReport {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(estimate);
        Objects.requireNonNull(checkedAt);
        Objects.requireNonNull(confidence);
        Objects.requireNonNull(source);
        if (verifiedStops < -1) throw new IllegalArgumentException("verified stop count is unknown or non-negative");
    }

    public static NavigationReport unspecified() {
        return new NavigationReport(Reason.UNSPECIFIED, Estimate.UNSPECIFIED, -1, Optional.empty(), Confidence.UNSPECIFIED, Source.UNSPECIFIED);
    }
}
