package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;

import java.util.List;

/**
 * Produces Cucumber feature files from extracted Spring endpoints. A future {@code ai} implementation
 * can call an LLM using the same {@link EndpointContract} inputs (and optional extra project context)
 * without changing the rest of the local pipeline.
 */
public interface ScenarioGenerator {

    /** Stable id for CLI ({@code template}, {@code ai}, …). */
    String id();

    List<FeatureArtifact> generateFeatures(List<EndpointContract> endpoints);
}
