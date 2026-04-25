package com.qacopilot.generation.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.analysis.ir.FieldContract;
import com.qacopilot.analysis.ir.ParamBinding;
import com.qacopilot.analysis.ir.RequestBodyContract;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-agnostic helpers for the QA CoPilot Gherkin blocks protocol.
 *
 * <p>Centralises the system prompt, endpoint serialisation, and response parsing so that every
 * {@link EndpointBlockSource} implementation produces identical prompts regardless of which AI
 * provider backs them.</p>
 *
 * <p>Response contract: the AI must return a single JSON object:
 * <pre>{"blocks":[{"stableId":"<id>","gherkin":"<fragment>"}]}</pre>
 * </p>
 */
public final class GherkinBlocksProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GherkinBlocksProtocol() {
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    /**
     * Returns the canonical system prompt sent to every AI provider.
     * All constraints (step patterns, coverage requirements, status codes) live here.
     */
    public static String systemPrompt() {
        return """
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
                HTTP STATUS ASSERTIONS (CRITICAL)
                ----------------------
                Every scenario MUST end with an explicit line (exact text):
                  Then response status should be <integer>
                using ONLY these integers where applicable: 200, 201, 400, 401, 404.

                For EACH endpoint's "gherkin" fragment you MUST satisfy ALL of the following:
                - Include EXACTLY one or two scenarios whose final status line is 200 or 201 (happy path).
                - Include AT LEAST TWO scenarios whose final status line is Then response status should be 400
                  with DIFFERENT Scenario titles.
                - If the endpoint JSON has a non-empty pathParams array: include AT LEAST ONE scenario whose
                  final status line is Then response status should be 404.
                - If the endpoint JSON has auth.required true OR auth.type is not the string "none": include
                  AT LEAST ONE scenario whose final status line is Then response status should be 401.
                - It is INVALID to output a fragment where every scenario uses only 200 or only 201.
                - When openApiResponseCodes is present in the endpoint JSON, align expectations with those codes.

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
                """.stripIndent();
    }

    /**
     * Suffix appended to every user message as a mandatory reminder.
     */
    public static String mandatoryUserSuffix() {
        return "\n\nMANDATORY: For every stableId, the gherkin fragment must include multiple scenarios "
                + "with NOT ONLY 200/201. Each fragment needs at least two Then response status should be 400 "
                + "lines (different scenarios). If pathParams is non-empty, add Then response status should be 404. "
                + "If auth is required or auth.type is not the string none, add Then response status should be 401.";
    }

    // ── Endpoint serialisation ─────────────────────────────────────────────────

    /** Serialise a batch of endpoints into the JSON array sent as the user message. */
    public static String buildUserContent(List<EndpointContract> endpoints) throws Exception {
        return "Endpoints JSON:\n"
                + MAPPER.writeValueAsString(promptArray(endpoints))
                + mandatoryUserSuffix();
    }

    /** Build the prompt array for additional context (requirements enrichment etc.). */
    public static List<Map<String, Object>> promptArray(List<EndpointContract> endpoints) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EndpointContract e : endpoints) {
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
            row.put("auth", Map.of("type", e.getAuth().getType(), "required", e.getAuth().isRequired()));
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
            out.add(row);
        }
        return out;
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /**
     * Parse {@code {"blocks":[{"stableId":"...","gherkin":"..."}]}} from model output.
     *
     * @throws Exception if the JSON is missing the {@code blocks} array or any entry is malformed.
     */
    public static Map<String, String> parseBlocksJson(String content) throws Exception {
        JsonNode tree = MAPPER.readTree(content);
        JsonNode blocks = tree.path("blocks");
        if (!blocks.isArray()) {
            throw new IllegalStateException("AI response JSON is missing the 'blocks' array: " + truncate(content, 200));
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (JsonNode n : blocks) {
            String sid = n.path("stableId").asText();
            String gherkin = n.path("gherkin").asText();
            if (sid.isEmpty() || gherkin.isBlank()) {
                throw new IllegalStateException("Invalid block entry (empty stableId or gherkin): " + n);
            }
            map.put(sid, gherkin);
        }
        return map;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
                if (vals.size() >= 5) break;
                vals.add(v.length() > 160 ? v.substring(0, 157) + "..." : v);
            }
            if (!vals.isEmpty()) {
                m.put("validations", vals);
            }
            rows.add(m);
        }
        if (fields.size() > cap) {
            rows.add(Map.of("note", "… and " + (fields.size() - cap) + " more fields omitted"));
        }
        return rows;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
