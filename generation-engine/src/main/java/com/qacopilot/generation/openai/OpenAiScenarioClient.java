package com.qacopilot.generation.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;
import com.qacopilot.generation.scenario.AiGherkinSanitizer;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI chat completions and expects a JSON object {@code {"blocks":[{ "stableId", "gherkin" }]}}.
 */
public final class OpenAiScenarioClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final URI CHAT = URI.create("https://api.openai.com/v1/chat/completions");

    private final OpenAiConfig config;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(45))
            .build();

    public OpenAiScenarioClient(OpenAiConfig config) {
        this.config = config;
    }

    public Map<String, String> generateBlocks(List<EndpointContract> chunk) throws Exception {
        if (!config.hasApiKey()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set (or pass --openai-api-key).");
        }
        String userPayload = MAPPER.writeValueAsString(promptArray(chunk));
        String system =
                """
                        You are an expert API QA engineer focused on exhaustive test coverage.

                        Output a single JSON object only (no markdown) with this shape:
                        {"blocks":[{"stableId":"<id>","gherkin":"<fragment>"}]}

                        For each input endpoint:
                        - Produce exactly one "blocks" entry
                        - The stableId MUST match exactly the input stableId
                        - The gherkin value must contain multiple scenarios (no Feature: line)

                        ----------------------
                        STRICT GHERKIN FORMAT
                        ----------------------
                        Each scenario MUST follow EXACTLY:
                        1. First line: @e_<stableId>
                        2. Second line: Scenario: <unique descriptive title>
                        3. Steps indented with exactly 4 spaces

                        Never place Given/When/Then directly under the tag without a Scenario line.

                        ----------------------
                        ALLOWED STEP PATTERNS (DO NOT MODIFY TEXT)
                        ----------------------
                          Given API base URL is configured
                          And request body matches <singleCamelCaseWord>
                          And request body is:
                            \"""
                            {JSON}
                            \"""
                          When I send a <GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS> request to "<path exactly as given>"
                          Then response status should be <integer>
                          Then response body as text contains "<substring>"
                          And the endpoint contract is logged for reporting

                        ----------------------
                        DOC STRING RULES (STRICT)
                        ----------------------
                        - Doc-string delimiter lines must contain only three double-quote characters each
                        - Opening and closing lines indented with 6 spaces
                        - JSON inside must be properly formatted and indented further

                        ----------------------
                        SCENARIO COVERAGE REQUIREMENTS (MANDATORY)
                        ----------------------
                        For EACH endpoint, you MUST generate scenarios covering:

                        1. HAPPY PATH
                           - Valid request -> 200 or 201

                        2. MISSING REQUIRED FIELDS
                           - Omit each required field -> 400

                        3. NULL VALUES
                           - Explicit nulls for required fields -> 400

                        4. INVALID DATA TYPES
                           - Wrong types (string instead of number, etc.) -> 400

                        5. FIELD-LEVEL VALIDATION
                           - Empty strings
                           - Min/max boundary values
                           - Very large inputs
                           - Special characters (if applicable)

                        6. FORMAT VALIDATION (when applicable)
                           - Invalid email / date / UUID formats -> 400

                        7. PATH VARIABLE VALIDATION (if endpoint has path params)
                           - Valid ID -> success
                           - Non-existent ID -> 404
                           - Invalid format ID -> 400

                        8. PUT vs PATCH BEHAVIOR
                           - PUT: missing required fields -> 400
                           - PATCH: partial update success
                           - PATCH: invalid partial payload -> 400

                        9. UNAUTHORIZED ACCESS (if plausible)
                           - Missing/invalid auth -> 401

                        10. EDGE CASES
                            - Empty body
                            - Duplicate data (if meaningful)
                            - Boundary numeric values (0, negative, max)

                        ----------------------
                        HTTP STATUS ASSERTIONS (CRITICAL — MODELS OFTEN DEFAULT TO 200 ONLY; DO NOT)
                        ----------------------
                        Every scenario MUST end with an explicit line (exact text):
                          Then response status should be <integer>
                        using ONLY these integers where applicable: 200, 201, 400, 401, 404.

                        For EACH endpoint's "gherkin" fragment you MUST satisfy ALL of the following:
                        - Include EXACTLY one or two scenarios whose final status line is 200 or 201 (happy path).
                        - Include AT LEAST TWO scenarios whose final status line is Then response status should be 400
                          with DIFFERENT Scenario titles (e.g. missing required field vs wrong JSON type vs empty body).
                        - If the endpoint JSON has a non-empty pathParams array: include AT LEAST ONE scenario whose
                          final status line is Then response status should be 404 (e.g. non-existent id in path).
                        - If the endpoint JSON has auth.required true OR auth.type is not the string "none": include
                          AT LEAST ONE scenario whose final status line is Then response status should be 401.
                        - It is INVALID to output a fragment where every scenario uses only 200 or only 201.
                        - When openApiResponseCodes is present in the endpoint JSON, align expectations with those
                          codes when you choose status integers (still only use allowed step patterns).

                        Negative and error scenarios MUST still use valid JSON bodies in doc strings when a body is
                        sent; the difference is the payload content and the asserted status code, not the step wording.

                        ----------------------
                        IMPORTANT CONSTRAINTS
                        ----------------------
                        - Do NOT add explanations or comments
                        - Do NOT add Feature:
                        - Do NOT invent new step patterns
                        - Always end EVERY scenario with:
                          And the endpoint contract is logged for reporting
                        - Paths must include {pathVariables} exactly as provided
                        - Keep @e_<stableId> tag on EVERY scenario

                        ----------------------
                        GOAL
                        ----------------------
                        Generate production-quality, automation-ready Gherkin scenarios
                        with maximum real-world API test coverage.
                        """
                        .stripIndent();

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", config.model());
        payload.set(
                "messages",
                MAPPER.createArrayNode()
                        .add(MAPPER.createObjectNode().put("role", "system").put("content", system))
                        .add(
                                MAPPER.createObjectNode()
                                        .put("role", "user")
                                        .put(
                                                "content",
                                                "Endpoints JSON:\n"
                                                        + userPayload
                                                        + "\n\n"
                                                        + "MANDATORY: For every stableId, the gherkin fragment must "
                                                        + "include multiple scenarios with NOT ONLY 200/201. Each "
                                                        + "fragment needs at least two Then response status should be 400 "
                                                        + "lines (different scenarios). If pathParams is non-empty, add "
                                                        + "Then response status should be 404. If auth is required or "
                                                        + "auth.type is not the string none, add Then response status should be 401."
                                        )
                        )
        );
        payload.put("temperature", 0.38);
        payload.put("max_tokens", 16_384);
        payload.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
        String body = payload.toString();

        HttpRequest req = HttpRequest.newBuilder(CHAT)
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI HTTP " + res.statusCode() + ": " + res.body());
        }
        JsonNode root = MAPPER.readTree(res.body());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (content.isEmpty()) {
            throw new IllegalStateException("OpenAI returned empty content: " + res.body());
        }
        Map<String, String> raw = parseBlocksJson(content);
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            sanitized.put(e.getKey(), AiGherkinSanitizer.sanitize(e.getValue()));
        }
        return sanitized;
    }

    private static List<Map<String, Object>> promptArray(List<EndpointContract> chunk) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EndpointContract e : chunk) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stableId", EndpointFingerprints.stableId(e));
            row.put("httpMethod", e.getHttpMethod());
            row.put("path", e.getPath());
            row.put("controller", e.getControllerClassName());
            row.put("handler", e.getHandlerMethodName());
            row.put("pathParams", paramRows(e.getPathParams()));
            row.put("queryParams", paramRows(e.getQueryParams()));
            RequestBodyContract rb = e.getRequestBody();
            row.put("requestBody", bodyRow(rb));
            List<Map<String, Object>> bodyFields = compactBodyFields(rb);
            if (!bodyFields.isEmpty()) {
                row.put("requestBodyFields", bodyFields);
            }
            row.put(
                    "auth",
                    Map.of("type", e.getAuth().getType(), "required", e.getAuth().isRequired())
            );
            row.put("consumes", e.getConsumes());
            row.put("produces", e.getProduces());
            if (!e.getWarnings().isEmpty()) {
                row.put("warnings", e.getWarnings());
            }
            if (e.getOpenApiSummary() != null && !e.getOpenApiSummary().isBlank()) {
                row.put("openApiSummary", e.getOpenApiSummary());
            }
            if (!e.getOpenApiTags().isEmpty()) {
                row.put("openApiTags", e.getOpenApiTags());
            }
            if (!e.getOpenApiResponseCodes().isEmpty()) {
                row.put("openApiResponseCodes", e.getOpenApiResponseCodes());
            }
            if (e.getOpenApiSpecOperationId() != null && !e.getOpenApiSpecOperationId().isBlank()) {
                row.put("openApiOperationId", e.getOpenApiSpecOperationId());
            }
            if (e.getOpenApiSpecDescription() != null && !e.getOpenApiSpecDescription().isBlank()) {
                row.put("openApiDescriptionFromSpec", e.getOpenApiSpecDescription());
            }
            if (!e.getOpenApiSpecSources().isEmpty()) {
                row.put("openApiMergedSpecSources", e.getOpenApiSpecSources());
            }
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> paramRows(List<ParamBinding> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ParamBinding p : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.getName());
            m.put("javaType", p.getJavaType());
            m.put("required", p.isRequired());
            if (p.getDefaultValue() != null && !p.getDefaultValue().isEmpty()) {
                m.put("defaultValue", p.getDefaultValue());
            }
            rows.add(m);
        }
        return rows;
    }

    private static Map<String, Object> bodyRow(RequestBodyContract b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("present", b.isPresent());
        m.put("required", b.isRequired());
        if (b.getDtoJavaType() != null) {
            m.put("dtoJavaType", b.getDtoJavaType());
        }
        return m;
    }

    /**
     * Compact DTO shape for the model (avoids megabyte-long {@code signature} strings with every Bean Validation
     * message repeated in the prompt).
     */
    private static List<Map<String, Object>> compactBodyFields(RequestBodyContract rb) {
        if (rb == null || !rb.isPresent() || rb.getFields().isEmpty()) {
            return List.of();
        }
        List<FieldContract> fields = rb.getFields();
        List<Map<String, Object>> rows = new ArrayList<>();
        int cap = Math.min(fields.size(), 64);
        for (int i = 0; i < cap; i++) {
            FieldContract f = fields.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getName());
            m.put("javaType", f.getJavaType());
            m.put("required", f.isRequired());
            List<String> vals = new ArrayList<>();
            for (String v : f.getValidations()) {
                if (vals.size() >= 5) {
                    break;
                }
                String t = v.length() > 160 ? v.substring(0, 157) + "..." : v;
                vals.add(t);
            }
            if (!vals.isEmpty()) {
                m.put("validations", vals);
            }
            rows.add(m);
        }
        if (fields.size() > cap) {
            rows.add(
                    Map.of(
                            "note",
                            "... and " + (fields.size() - cap) + " more fields omitted from this summary"
                    )
            );
        }
        return rows;
    }

    /**
     * Parse the AI's JSON response into a {@code {stableId → gherkinBlock}} map.
     * Delegated to {@link com.qacopilot.generation.scenario.GherkinBlocksProtocol#parseBlocksJson}
     * so that all providers share the same parser.
     */
    public static Map<String, String> parseBlocksJson(String content) throws Exception {
        return com.qacopilot.generation.scenario.GherkinBlocksProtocol.parseBlocksJson(content);
    }
}
