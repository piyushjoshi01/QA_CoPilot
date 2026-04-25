package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;

import java.util.List;

/** Deterministic Gherkin from {@link EndpointContract}; steps live in {@code com.qacopilot.stepdefs.common.CommonSteps}. */
public final class TemplateScenarioGenerator implements ScenarioGenerator {

    @Override
    public String id() {
        return "template";
    }

    @Override
    public List<FeatureArtifact> generateFeatures(List<EndpointContract> endpoints) {
        try {
            return FeatureSynthesis.buildFresh(endpoints, new TemplateEndpointBlockSource());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
