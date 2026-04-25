package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;

import java.util.List;
import java.util.Map;

public final class TemplateEndpointBlockSource implements EndpointBlockSource {

    @Override
    public String id() {
        return "template";
    }

    @Override
    public Map<String, String> blocksFor(List<EndpointContract> endpoints) {
        return TemplateGherkin.blocksForAll(endpoints);
    }
}
