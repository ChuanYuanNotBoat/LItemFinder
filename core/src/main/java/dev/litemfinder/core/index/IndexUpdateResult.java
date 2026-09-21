package dev.litemfinder.core.index;

/** Outcome of applying a root container snapshot to an index. */
public enum IndexUpdateResult {
    ADDED,
    REPLACED,
    IGNORED_STALE
}
