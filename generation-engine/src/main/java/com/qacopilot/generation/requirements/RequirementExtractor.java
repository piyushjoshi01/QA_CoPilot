package com.qacopilot.generation.requirements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qacopilot.generation.ai.AIClient;
import com.qacopilot.generation.ai.AIClientException;
import com.qacopilot.generation.ai.AIResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Uses an {@link AIClient} to extract structured {@link Requirement} objects from a
 * {@link RequirementDocument}.
 *
 * <h3>Prompt Engineering Strategy</h3>
 * <p>The system prompt instructs the model to act as a business analyst and output a JSON array
 * of requirement objects. The document text is sent as the user message. The model is given a
 * strict JSON schema to follow, which guarantees machine-parseable output.</p>
 *
 * <h3>Chunking</h3>
 * <p>Large documents are split into chunks of {@link #MAX_CHUNK_CHARS} characters with a
 * 500-character overlap so that requirements that span chunk boundaries are not lost.
 * Each chunk is processed independently and the results are merged.</p>
 */
public final class RequirementExtractor {

    private static final Logger LOG = Logger.getLogger(RequirementExtractor.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CHUNK_CHARS = 12_000;
    private static final int CHUNK_OVERLAP = 500;

    private final AIClient aiClient;

    public RequirementExtractor(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    /**
     * Extract structured requirements from a parsed document.
     *
     * @param document Parsed document (text content already extracted from raw file).
     * @return         List of {@link Requirement}s; may be empty if none are found.
     */
    public List<Requirement> extract(RequirementDocument document) throws AIClientException {
        if (!document.hasContent()) {
            LOG.warning("[RequirementExtractor] Document has no text content: " + document.fileName());
            return List.of();
        }

        String systemPrompt = buildSystemPrompt();
        List<String> chunks = splitIntoChunks(document.textContent());
        List<Requirement> all = new ArrayList<>();
        int reqCounter = 1;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String userContent = buildUserContent(document, chunk, i + 1, chunks.size());

            LOG.info(String.format("[RequirementExtractor] Processing chunk %d/%d of %s (%d chars)",
                    i + 1, chunks.size(), document.fileName(), chunk.length()));

            try {
                AIResponse response = aiClient.generateResponse(systemPrompt, userContent);
                List<Requirement> extracted = parseRequirements(response.content(), reqCounter);
                reqCounter += extracted.size();
                all.addAll(extracted);
            } catch (AIClientException e) {
                LOG.warning(String.format(
                        "[RequirementExtractor] AI failed on chunk %d/%d: %s — skipping chunk",
                        i + 1, chunks.size(), e.getMessage()));
            }
        }

        LOG.info("[RequirementExtractor] Extracted " + all.size() + " requirements from " + document.fileName());
        return all;
    }

    // ── Prompt engineering ────────────────────────────────────────────────────

    private static String buildSystemPrompt() {
        return """
                You are an expert business analyst and QA architect.

                Your task: Extract all business requirements from the provided document text.

                Output a single JSON object (no markdown):
                {
                  "requirements": [
                    {
                      "title": "<short title>",
                      "description": "<full description of the requirement>",
                      "type": "<FUNCTIONAL|NON_FUNCTIONAL|CONSTRAINT|BUSINESS_RULE>",
                      "priority": "<CRITICAL|HIGH|MEDIUM|LOW>",
                      "acceptanceCriteria": ["<criterion 1>", "<criterion 2>"],
                      "edgeCases": ["<edge case 1>", "<edge case 2>"],
                      "constraints": ["<constraint 1>"],
                      "sourceSection": "<section or heading where found>"
                    }
                  ]
                }

                Rules:
                - Extract EVERY distinct requirement, no matter how small.
                - acceptanceCriteria: What must be true for this requirement to be considered done?
                - edgeCases: What unusual inputs or states could cause this to fail?
                - constraints: Technical or business limits (size, format, time, auth, etc.)
                - Do NOT invent requirements not present in the text.
                - Do NOT merge multiple distinct requirements into one.
                - If no requirements are found, return {"requirements": []}
                - type FUNCTIONAL = feature behaviour; NON_FUNCTIONAL = performance/security/scalability;
                  CONSTRAINT = limits/rules; BUSINESS_RULE = domain policy.
                """.stripIndent();
    }

    private static String buildUserContent(RequirementDocument doc, String chunk, int part, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append("Document: ").append(doc.fileName()).append('\n');
        if (!doc.sections().isEmpty()) {
            sb.append("Sections found: ").append(String.join(", ", doc.sections().subList(0, Math.min(10, doc.sections().size())))).append('\n');
        }
        if (total > 1) {
            sb.append("(Chunk ").append(part).append(" of ").append(total).append(")\n");
        }
        sb.append('\n').append(chunk);
        return sb.toString();
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private static List<Requirement> parseRequirements(String content, int startIdCounter)
            throws AIClientException {
        List<Requirement> result = new ArrayList<>();
        try {
            // Strip possible markdown fences
            String json = content.strip();
            if (json.startsWith("```")) {
                int first = json.indexOf('\n');
                int last = json.lastIndexOf("```");
                if (first >= 0 && last > first) {
                    json = json.substring(first + 1, last).strip();
                }
            }

            JsonNode root = MAPPER.readTree(json);
            JsonNode reqs = root.path("requirements");
            if (!reqs.isArray()) return result;

            int counter = startIdCounter;
            for (JsonNode r : reqs) {
                Requirement req = new Requirement();
                req.requirementId = "REQ-" + String.format("%03d", counter++);
                req.title = r.path("title").asText("Untitled");
                req.description = r.path("description").asText("");
                req.sourceSection = r.path("sourceSection").asText(null);

                String type = r.path("type").asText("FUNCTIONAL");
                try {
                    req.type = Requirement.Type.valueOf(type);
                } catch (IllegalArgumentException ignore) {
                    req.type = Requirement.Type.FUNCTIONAL;
                }

                String priority = r.path("priority").asText("MEDIUM");
                try {
                    req.priority = Requirement.Priority.valueOf(priority);
                } catch (IllegalArgumentException ignore) {
                    req.priority = Requirement.Priority.MEDIUM;
                }

                req.acceptanceCriteria = jsonArrayToList(r.path("acceptanceCriteria"));
                req.edgeCases = jsonArrayToList(r.path("edgeCases"));
                req.constraints = jsonArrayToList(r.path("constraints"));

                if (!req.title.isBlank()) {
                    result.add(req);
                }
            }
        } catch (Exception e) {
            throw new AIClientException("Failed to parse requirements JSON: " + e.getMessage(), e);
        }
        return result;
    }

    private static List<String> jsonArrayToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText("").strip();
                if (!text.isEmpty()) list.add(text);
            }
        }
        return list;
    }

    // ── Chunking ──────────────────────────────────────────────────────────────

    private static List<String> splitIntoChunks(String text) {
        if (text.length() <= MAX_CHUNK_CHARS) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, text.length());
            // Try to split at a paragraph boundary (double newline)
            if (end < text.length()) {
                int boundary = text.lastIndexOf("\n\n", end);
                if (boundary > start + MAX_CHUNK_CHARS / 2) {
                    end = boundary;
                }
            }
            chunks.add(text.substring(start, end));
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }
}
