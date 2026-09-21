package dev.litemfinder.core.classification;

import dev.litemfinder.core.model.ItemKey;

/** Persistable classification predicate for an item identity. */
public sealed interface ItemRule permits ExactItemRule, NamespaceRule, TagRule, AnyOfRule {

    boolean matches(ItemKey item, ItemTagResolver tags);
}
