package dev.litemfinder.core.search;

import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldPosition;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Compound item query over identity text, namespace, tags, quantity, and optional origin. */
public record SearchQuery(
        String text,
        String namespace,
        Set<NamespacedId> requiredTags,
        long minimumTotalCount,
        Optional<WorldPosition> origin
) {

    public SearchQuery {
        Objects.requireNonNull(text, "text must not be null");
        text = text.trim().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(namespace, "namespace must not be null");
        namespace = namespace.trim().toLowerCase(Locale.ROOT);
        if (!namespace.isEmpty()) {
            namespace = new NamespacedId(namespace, "validation").namespace();
        }
        requiredTags = Set.copyOf(Objects.requireNonNull(requiredTags, "requiredTags must not be null"));
        if (minimumTotalCount < 0) {
            throw new IllegalArgumentException("minimumTotalCount must not be negative");
        }
        Objects.requireNonNull(origin, "origin must not be null");
    }

    public static SearchQuery all() {
        return new SearchQuery("", "", Set.of(), 0, Optional.empty());
    }

    public SearchQuery withText(String newText) {
        return new SearchQuery(newText, namespace, requiredTags, minimumTotalCount, origin);
    }

    public SearchQuery withNamespace(String newNamespace) {
        return new SearchQuery(text, newNamespace, requiredTags, minimumTotalCount, origin);
    }

    public SearchQuery withRequiredTags(Set<NamespacedId> newTags) {
        return new SearchQuery(text, namespace, newTags, minimumTotalCount, origin);
    }

    public SearchQuery withMinimumTotalCount(long newMinimum) {
        return new SearchQuery(text, namespace, requiredTags, newMinimum, origin);
    }

    public SearchQuery withOrigin(WorldPosition newOrigin) {
        return new SearchQuery(text, namespace, requiredTags, minimumTotalCount, Optional.of(newOrigin));
    }
}
