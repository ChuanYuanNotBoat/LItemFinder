package dev.litemfinder.core.persistence;

/** Outcome of writing one root snapshot to persistent storage. */
public enum SnapshotWriteResult {
    ADDED,
    REPLACED,
    IGNORED_STALE
}
