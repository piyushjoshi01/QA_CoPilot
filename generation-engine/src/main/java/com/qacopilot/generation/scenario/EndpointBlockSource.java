package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;

import java.util.List;
import java.util.Map;

/**
 * Produces one Gherkin fragment per endpoint (multiple {@code Scenario} lines inside), keyed by
 * {@link com.qacopilot.generation.state.EndpointFingerprints#stableId}.
 */
public interface EndpointBlockSource {

    String id();

    Map<String, String> blocksFor(List<EndpointContract> endpoints) throws Exception;
}
