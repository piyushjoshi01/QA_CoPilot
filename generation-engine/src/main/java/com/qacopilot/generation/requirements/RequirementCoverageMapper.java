package com.qacopilot.generation.requirements;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps business requirements to API endpoints using keyword heuristics.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Tokenise requirement title + description + acceptance criteria into normalised keywords.</li>
 *   <li>Tokenise endpoint path + controller name + handler name.</li>
 *   <li>Score each (requirement, endpoint) pair by keyword overlap.</li>
 *   <li>Any pair above the {@link #MATCH_THRESHOLD} is recorded as a mapping.</li>
 * </ol>
 *
 * <p>Coverage report is written to {@code .qacopilot/requirements/coverage-map.json}:
 * <pre>{@code
 * {
 *   "unmappedRequirements": ["REQ-002", "REQ-007"],
 *   "coverage": [
 *     {
 *       "requirementId": "REQ-001",
 *       "title": "User Registration",
 *       "endpoints": ["POST /api/users", "POST /api/auth/register"]
 *     }
 *   ]
 * }
 * }</pre>
 * </p>
 */
public final class RequirementCoverageMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double MATCH_THRESHOLD = 0.12; // minimum Jaccard similarity

    private RequirementCoverageMapper() {
    }

    /**
     * Compute coverage mappings, annotate requirements with covered endpoint ids,
     * and write the coverage report.
     *
     * @param requirements  Extracted requirements to map.
     * @param endpoints     Parsed API endpoints.
     * @param reportDir     Directory to write {@code coverage-map.json}; created if absent.
     * @return              The same requirements list with {@code coveredByEndpoints} populated.
     */
    public static List<Requirement> mapAndReport(
            List<Requirement> requirements,
            List<EndpointContract> endpoints,
            Path reportDir) throws IOException {

        for (Requirement req : requirements) {
            List<String> tokens = requirementTokens(req);
            List<String> matchedEndpoints = new ArrayList<>();

            for (EndpointContract ep : endpoints) {
                List<String> epTokens = endpointTokens(ep);
                double score = jaccard(tokens, epTokens);
                if (score >= MATCH_THRESHOLD) {
                    matchedEndpoints.add(ep.getHttpMethod() + " " + ep.getPath());
                }
            }
            req.coveredByEndpoints = matchedEndpoints;
        }

        writeCoverageReport(requirements, reportDir);
        return requirements;
    }

    // ── Report writing ────────────────────────────────────────────────────────

    private static void writeCoverageReport(List<Requirement> requirements, Path reportDir) throws IOException {
        Files.createDirectories(reportDir);

        List<Map<String, Object>> coverage = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();

        for (Requirement req : requirements) {
            if (req.coveredByEndpoints.isEmpty()) {
                unmapped.add(req.requirementId);
            } else {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("requirementId", req.requirementId);
                entry.put("title", req.title);
                entry.put("endpoints", req.coveredByEndpoints);
                coverage.add(entry);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalRequirements", requirements.size());
        report.put("mappedRequirements", coverage.size());
        report.put("unmappedRequirements", unmapped);
        report.put("coverage", coverage);

        Path reportFile = reportDir.resolve("coverage-map.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), report);
    }

    // ── Tokenisation + similarity ─────────────────────────────────────────────

    private static List<String> requirementTokens(Requirement req) {
        StringBuilder sb = new StringBuilder();
        if (req.title != null) sb.append(req.title).append(' ');
        if (req.description != null) sb.append(req.description).append(' ');
        req.acceptanceCriteria.forEach(c -> sb.append(c).append(' '));
        return tokenise(sb.toString());
    }

    private static List<String> endpointTokens(EndpointContract ep) {
        StringBuilder sb = new StringBuilder();
        sb.append(ep.getPath().replace('/', ' ')).append(' ');
        sb.append(ep.getControllerClassName()).append(' ');
        sb.append(ep.getHandlerMethodName()).append(' ');
        if (ep.getOpenApiSummary() != null) sb.append(ep.getOpenApiSummary()).append(' ');
        return tokenise(sb.toString());
    }

    private static List<String> tokenise(String text) {
        List<String> tokens = new ArrayList<>();
        for (String raw : text.split("[^a-zA-Z0-9]+")) {
            String t = raw.strip().toLowerCase(Locale.ROOT);
            if (t.length() >= 3 && !STOP_WORDS.contains(t)) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private static double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        long intersection = a.stream().filter(b::contains).count();
        long union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "the", "and", "for", "are", "not", "with", "this", "that", "from",
            "have", "has", "will", "should", "must", "can", "may", "when", "all",
            "any", "each", "been", "which", "into", "then", "able", "api", "http",
            "get", "post", "put", "patch", "delete", "request", "response", "data");
}
