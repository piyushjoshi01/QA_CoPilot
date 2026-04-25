package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Several deterministic scenarios per endpoint; steps are implemented in {@code com.qacopilot.stepdefs.common.CommonSteps}. */
public final class TemplateGherkin {

    private TemplateGherkin() {
    }

    public static Map<String, String> blocksForAll(List<EndpointContract> endpoints) {
        Map<String, String> out = new LinkedHashMap<>();
        for (EndpointContract e : endpoints) {
            out.put(EndpointFingerprints.stableId(e), blockFor(e));
        }
        return out;
    }

    public static String blockFor(EndpointContract e) {
        String tag = EndpointFingerprints.endpointTag(e);
        StringBuilder sb = new StringBuilder();
        appendSuccessScenario(sb, e, tag, "success (2xx)");
        if (needsBodyVariants(e)) {
            sb.append("\n");
            appendDocStringBodyScenario(sb, e, tag, "minimal JSON body variant", "{\"_qacopilot\":\"minimal\"}");
            sb.append("\n");
            appendDocStringBodyScenario(sb, e, tag, "empty object body", "{}");
        }
        return sb.toString();
    }

    private static void appendSuccessScenario(StringBuilder sb, EndpointContract e, String tag, String label) {
        sb.append("  ").append(tag).append("\n");
        sb.append("  Scenario: ")
                .append(e.getHttpMethod())
                .append(" ")
                .append(e.getPath())
                .append(" — ")
                .append(label)
                .append("\n");
        sb.append("    Given API base URL is configured\n");
        if (needsRequestBodyStep(e)) {
            sb.append("    And request body matches ")
                    .append(e.getHandlerMethodName())
                    .append("\n");
        }
        sb.append("    When I send a ")
                .append(e.getHttpMethod())
                .append(" request to \"")
                .append(escapePath(e.getPath()))
                .append("\"\n");
        sb.append("    Then response status should be 200\n");
        sb.append("    And the endpoint contract is logged for reporting\n");
    }

    private static void appendDocStringBodyScenario(StringBuilder sb, EndpointContract e, String tag, String label, String json) {
        sb.append("  ").append(tag).append("\n");
        sb.append("  Scenario: ")
                .append(e.getHttpMethod())
                .append(" ")
                .append(e.getPath())
                .append(" — ")
                .append(label)
                .append("\n");
        sb.append("    Given API base URL is configured\n");
        sb.append("    And request body is:\n");
        sb.append("      \"\"\"\n");
        sb.append("      ").append(json).append("\n");
        sb.append("      \"\"\"\n");
        sb.append("    When I send a ")
                .append(e.getHttpMethod())
                .append(" request to \"")
                .append(escapePath(e.getPath()))
                .append("\"\n");
        sb.append("    Then response status should be 200\n");
        sb.append("    And the endpoint contract is logged for reporting\n");
    }

    private static boolean needsRequestBodyStep(EndpointContract e) {
        if (e.getRequestBody().isPresent()) {
            return true;
        }
        String m = e.getHttpMethod();
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m);
    }

    private static boolean needsBodyVariants(EndpointContract e) {
        return needsRequestBodyStep(e);
    }

    private static String escapePath(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
