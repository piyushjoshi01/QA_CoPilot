package com.qacopilot.generation.pipeline;

import com.qacopilot.generation.evolution.ContractDelta;

import java.nio.file.Path;

/** Outcome of a successful {@link QaCopilotPipeline#runSync(PipelineSyncRequest)} run. */
public record PipelineSyncResult(
        int endpointCount,
        String generatorId,
        String scenarioModeLabel,
        Path testFrameworkOutputDirectory,
        Path qacopilotStateDirectory,
        Path parseCacheFileOrNull,
        /** Number of requirements extracted from the ingested document (0 if Feature 2 not used). */
        int requirementsExtracted,
        /**
         * Contract evolution delta from this run (null if Feature 3 not enabled).
         * Contains counts of added/modified/deleted endpoints and AI token savings estimate.
         */
        ContractDelta contractDeltaOrNull
) {
    /** Backward-compat constructor for callers that predate Feature 2/3 fields. */
    public PipelineSyncResult(
            int endpointCount,
            String generatorId,
            String scenarioModeLabel,
            Path testFrameworkOutputDirectory,
            Path qacopilotStateDirectory,
            Path parseCacheFileOrNull) {
        this(endpointCount, generatorId, scenarioModeLabel,
                testFrameworkOutputDirectory, qacopilotStateDirectory,
                parseCacheFileOrNull, 0, null);
    }
}
