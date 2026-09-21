package dev.litemfinder.core.search;

import dev.litemfinder.core.model.ItemKey;

/** Read-only item search operations over a storage index. */
public interface SearchEngine {

    SearchResult findExact(ItemKey item);
}
