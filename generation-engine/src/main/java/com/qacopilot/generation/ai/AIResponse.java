package com.qacopilot.generation.ai;

import java.util.Map;

/**
 * Normalized, provider-agnostic response from any {@link AIClient}.
 *
 * <p>The {@code content} field always contains the raw text returned by the model
 * (the Gherkin blocks JSON in the QA CoPilot workflow).
 * Token counts are best-effort — some providers or cache layers may return 0.</p>
 */
public record AIResponse(
        String content,
        String modelUsed,
        int promptTokens,
        int completionTokens,
        Map<String, Object> rawMetadata) {

    public AIResponse {
        if (content == null) content = "";
        if (modelUsed == null) modelUsed = "unknown";
        rawMetadata = rawMetadata == null ? Map.of() : Map.copyOf(rawMetadata);
    }

    /** Total token consumption for cost tracking / observability. */
    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    /** {@code true} if the response content is empty or blank. */
    public boolean isEmpty() {
        return content.isBlank();
    }
}
