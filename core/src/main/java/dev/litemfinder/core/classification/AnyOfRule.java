package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ItemKey;

import java.util.List;
import java.util.Objects;

/** Matches when at least one child rule matches. */
public record AnyOfRule(List<ItemRule> rules) implements ItemRule {

    public AnyOfRule {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
    }

    @Override
    public boolean matches(ItemKey item, ItemTagResolver tags) {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(tags, "tags must not be null");
        return rules.stream().anyMatch(rule -> rule.matches(item, tags));
    }
}
