package com.qacopilot.generation.evolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The computed difference between two consecutive {@link ContractSnapshot}s.
 *
 * <p>Stored as {@code .qacopilot/evolution/delta-v{from}-to-v{to}.json}.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "fromVersion": 2,
 *   "toVersion": 3,
 *   "timestamp": "2026-04-25T10:30:00Z",
 *   "summary": {
 *     "added": 2,
 *     "modified": 1,
 *     "deleted": 0,
 *     "unchanged": 9
 *   },
 *   "changes": [
 *     { "stableId": "orders_post_api_orders", "changeType": "ADDED", ... },
 *     { "stableId": "users_put_api_users_id", "changeType": "MODIFIED", ... }
 *   ],
 *   "impactAnalysis": {
 *     "scenariosToRegenerate": ["orders_post_api_orders", "users_put_api_users_id"],
 *     "estimatedAiTokensSaved": 4200
 *   }
 * }
 * }</pre>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractDelta {

    public int fromVersion;
    public int toVersion;
    public String timestamp;

    public DeltaSummary summary = new DeltaSummary();
    public List<EndpointChange> changes = List.of();
    public ImpactAnalysis impactAnalysis;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DeltaSummary {
        public int added;
        public int modified;
        public int deleted;
        public int unchanged;

        public int totalChanges() {
            return added + modified + deleted;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImpactAnalysis {
        /** stableIds of endpoints that need their Gherkin regenerated. */
        public List<String> scenariosToRegenerate = List.of();
        /**
         * Rough estimate of tokens saved by incremental vs full regeneration.
         * Assumes ~350 tokens per endpoint prompt.
         */
        public int estimatedAiTokensSaved;
        public String regenerationReason;
    }
}
