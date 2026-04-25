package com.qacopilot.generation.requirements;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.ai.AIClient;
import com.qacopilot.generation.ai.AIClientException;
import com.qacopilot.generation.ai.AIResponse;
import com.qacopilot.generation.scenario.AiGherkinSanitizer;
import com.qacopilot.generation.scenario.EndpointBlockSource;
import com.qacopilot.generation.scenario.GherkinBlocksProtocol;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Decorator over any {@link EndpointBlockSource} that enriches the AI prompt with
 * business requirements context.
 *
 * <p>For each endpoint, it finds requirements whose {@code coveredByEndpoints} list
 * contains this endpoint's HTTP method + path, then injects the requirement text
 * (title, description, acceptance criteria, edge cases) into the user prompt.
 * This allows the AI to generate Gherkin scenarios that are explicitly aligned with
 * business intent rather than just the code structure.</p>
 *
 * <p>If no requirements are provided, this decorator falls through to the inner block source
 * without any additional prompt enrichment — so it is safe to wrap any source unconditionally.</p>
 *
 * <h3>Merged prompt structure:</h3>
 * <pre>
 * Endpoints JSON:
 * [{ "stableId": "...", "httpMethod": "POST", "path": "/api/users", ... }]
 *
 * Business Requirements for these endpoints:
 * REQ-001 [HIGH] User Registration
 *   Description: The system shall allow new users to register...
 *   Acceptance Criteria:
 *     - User receives a confirmation email
 *     - Duplicate email returns 409
 *   Edge Cases:
 *     - Email with + and . variants
 *
 * MANDATORY: ...
 * </pre>
 */
public final class RequirementAwareBlockSource implements EndpointBlockSource {

    private static final Logger LOG = Logger.getLogger(RequirementAwareBlockSource.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CHUNK = 8;

    private final AIClient aiClient;
    private final List<Requirement> requirements;
    private final boolean fallbackToTemplate;
    private final com.qacopilot.generation.scenario.TemplateEndpointBlockSource templateFallback
            = new com.qacopilot.generation.scenario.TemplateEndpointBlockSource();

    /**
     * @param aiClient       Fully-decorated AI client.
     * @param requirements   Requirements to inject; may be empty.
     * @param fallbackToTemplate Fall back to template generation on AI failure.
     */
    public RequirementAwareBlockSource(
            AIClient aiClient,
            List<Requirement> requirements,
            boolean fallbackToTemplate) {
        this.aiClient = aiClient;
        this.requirements = List.copyOf(requirements);
        this.fallbackToTemplate = fallbackToTemplate;
    }

    @Override
    public String id() {
        return "ai-requirements-" + aiClient.provider().getId();
    }

    @Override
    public Map<String, String> blocksFor(List<EndpointContract> endpoints) throws Exception {
        Map<String, String> all = new LinkedHashMap<>();
        List<EndpointContract> batch = new ArrayList<>();

        for (EndpointContract e : endpoints) {
            batch.add(e);
            if (batch.size() >= CHUNK) {
                flushChunk(batch, all);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            flushChunk(batch, all);
        }
        return all;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void flushChunk(List<EndpointContract> chunk, Map<String, String> sink) throws Exception {
        String systemPrompt = GherkinBlocksProtocol.systemPrompt();
        String userContent = buildEnrichedUserContent(chunk);

        try {
            AIResponse response = aiClient.generateResponse(systemPrompt, userContent);
            Map<String, String> got = GherkinBlocksProtocol.parseBlocksJson(response.content());
            for (var entry : got.entrySet()) {
                sink.put(entry.getKey(), AiGherkinSanitizer.sanitize(entry.getValue()));
            }
        } catch (Exception ex) {
            if (fallbackToTemplate) {
                LOG.warning("[RequirementAwareBlockSource] Chunk failed (" + ex.getMessage()
                        + ") — falling back to template.");
                sink.putAll(templateFallback.blocksFor(chunk));
                return;
            }
            throw ex;
        }

        // Fill any endpoints the model forgot
        List<EndpointContract> missing = new ArrayList<>();
        for (EndpointContract e : chunk) {
            String sid = EndpointFingerprints.stableId(e);
            if (!sink.containsKey(sid) || sink.get(sid).isBlank()) {
                missing.add(e);
            }
        }
        if (!missing.isEmpty()) {
            sink.putAll(templateFallback.blocksFor(missing));
        }
    }

    private String buildEnrichedUserContent(List<EndpointContract> chunk) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Endpoints JSON:\n");
        sb.append(MAPPER.writeValueAsString(GherkinBlocksProtocol.promptArray(chunk)));

        // Inject relevant requirements
        List<Requirement> relevant = findRelevantRequirements(chunk);
        if (!relevant.isEmpty()) {
            sb.append("\n\nBusiness Requirements for these endpoints:\n");
            for (Requirement req : relevant) {
                sb.append('\n');
                sb.append(req.requirementId).append(" [").append(req.priority).append("] ").append(req.title).append('\n');
                if (req.description != null && !req.description.isBlank()) {
                    sb.append("  Description: ").append(req.description).append('\n');
                }
                if (!req.acceptanceCriteria.isEmpty()) {
                    sb.append("  Acceptance Criteria:\n");
                    req.acceptanceCriteria.forEach(c -> sb.append("    - ").append(c).append('\n'));
                }
                if (!req.edgeCases.isEmpty()) {
                    sb.append("  Edge Cases:\n");
                    req.edgeCases.forEach(e -> sb.append("    - ").append(e).append('\n'));
                }
                if (!req.constraints.isEmpty()) {
                    sb.append("  Constraints:\n");
                    req.constraints.forEach(c -> sb.append("    - ").append(c).append('\n'));
                }
            }
            sb.append("\nIMPORTANT: Generate Gherkin scenarios that explicitly cover the above acceptance criteria and edge cases.\n");
        }

        sb.append(GherkinBlocksProtocol.mandatoryUserSuffix());
        return sb.toString();
    }

    private List<Requirement> findRelevantRequirements(List<EndpointContract> chunk) {
        if (requirements.isEmpty()) return List.of();

        java.util.Set<String> endpointKeys = chunk.stream()
                .map(e -> e.getHttpMethod() + " " + e.getPath())
                .collect(Collectors.toSet());

        return requirements.stream()
                .filter(req -> req.coveredByEndpoints.stream()
                        .anyMatch(endpointKeys::contains))
                .collect(Collectors.toList());
    }
}
