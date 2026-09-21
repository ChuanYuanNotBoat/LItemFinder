package dev.litemfinder.core.persistence;

/** Unchecked failure raised by a snapshot repository implementation. */
public class SnapshotRepositoryException extends RuntimeException {

    public SnapshotRepositoryException(String message) {
        super(message);
    }

    public SnapshotRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
