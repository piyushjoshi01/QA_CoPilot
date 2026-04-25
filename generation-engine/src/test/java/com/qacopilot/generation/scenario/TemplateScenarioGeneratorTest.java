package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateScenarioGeneratorTest {

    @Test
    void generatesFeatureWithMultipleScenariosPerEndpoint() {
        var gen = new TemplateScenarioGenerator();
        List<EndpointContract> endpoints = List.of(
                EndpointContract.synthetic("GET", "/api/x", "XController", "getX"),
                EndpointContract.synthetic("POST", "/api/x", "XController", "createX")
        );

        List<FeatureArtifact> out = gen.generateFeatures(endpoints);

        assertEquals(1, out.size());
        assertEquals("features/x/x.feature", out.get(0).relativeToTestResources());
        String content = out.get(0).content();
        assertTrue(content.contains("@domain_x"));
        assertTrue(content.contains("Feature: X API"));
        assertTrue(content.contains("When I send a GET request to \"/api/x\""));
        assertTrue(content.contains("When I send a POST request to \"/api/x\""));
        assertTrue(content.contains("request body is:"));
        assertTrue(content.contains("the endpoint contract is logged for reporting"));
        assertTrue(content.contains("@e_"));
    }
}
