package com.qacopilot.generation.evolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A versioned snapshot of the full API contract.
 *
 * <p>Stored as {@code .qacopilot/evolution/snapshot-v{N}.json}.
 * Each snapshot contains a map of {@code {stableId → signatureHash}} for all endpoints
 * active at the time of the snapshot. The full contract JSON is stored separately in
 * {@code .qacopilot/api-contract.json} — this snapshot is intentionally compact.</p>
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "version": 3,
 *   "timestamp": "2026-04-25T10:30:00Z",
 *   "projectName": "my-api",
 *   "endpointCount": 12,
 *   "endpoints": {
 *     "users_get_api_users": "sha256:abc123...",
 *     "users_post_api_users": "sha256:def456..."
 *   }
 * }
 * }</pre>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractSnapshot {

    public int version;
    public String timestamp;
    public String projectName;
    public int endpointCount;
    /** Map of stableId → SHA-256 signature hash. */
    public Map<String, String> endpoints = new LinkedHashMap<>();

    public ContractSnapshot() {
    }

    public ContractSnapshot(int version, String timestamp, String projectName, Map<String, String> endpoints) {
        this.version = version;
        this.timestamp = timestamp;
        this.projectName = projectName;
        this.endpoints = new LinkedHashMap<>(endpoints);
        this.endpointCount = endpoints.size();
    }
}
