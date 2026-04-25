package com.qacopilot.generation.evolution;

/** The nature of a change to a single endpoint between two contract versions. */
public enum ChangeType {

    /** A new endpoint that did not exist in the previous snapshot. */
    ADDED,

    /** An existing endpoint whose signature changed (path, method, params, auth, body). */
    MODIFIED,

    /** An endpoint present in the previous snapshot that was removed from the codebase. */
    DELETED,

    /** An endpoint whose signature is identical to the previous snapshot. */
    UNCHANGED
}
