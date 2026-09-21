package dev.litemfinder.neoforge.identity;

/** Whether a resolved container identity is safe to reuse across sessions. */
public enum IdentityConfidence {
    EXACT,
    LOGICAL,
    SESSION_ONLY
}
