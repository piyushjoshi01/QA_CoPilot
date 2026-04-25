package com.qacopilot.generation.evolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the change status of a single endpoint between two contract versions.
 *
 * <pre>{@code
 * {
 *   "stableId":      "users_post",
 *   "changeType":    "MODIFIED",
 *   "httpMethod":    "POST",
 *   "path":          "/api/users",
 *   "previousHash":  "abc123",
 *   "currentHash":   "def456",
 *   "changeReason":  "requestBody.fields changed"
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointChange {

    public String stableId;
    public ChangeType changeType;
    public String httpMethod;
    public String path;
    /** SHA-256 of the endpoint's signature in the previous snapshot (null if ADDED). */
    public String previousHash;
    /** SHA-256 of the endpoint's signature in the current snapshot (null if DELETED). */
    public String currentHash;
    /** Human-readable summary of what changed. */
    public String changeReason;

    public EndpointChange() {
    }

    public EndpointChange(
            String stableId,
            ChangeType changeType,
            String httpMethod,
            String path,
            String previousHash,
            String currentHash,
            String changeReason) {
        this.stableId = stableId;
        this.changeType = changeType;
        this.httpMethod = httpMethod;
        this.path = path;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
        this.changeReason = changeReason;
    }

    @Override
    public String toString() {
        return changeType + " " + httpMethod + " " + path + " [" + stableId + "]";
    }
}
