package com.qacopilot.generation.scenario;

import com.qacopilot.analysis.ir.EndpointContract;
import com.qacopilot.generation.ai.AIClient;
import com.qacopilot.generation.ai.AIClientException;
import com.qacopilot.generation.ai.AIResponse;
import com.qacopilot.generation.state.EndpointFingerprints;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Provider-agnostic {@link EndpointBlockSource} that delegates to any {@link AIClient}.
 *
 * <p>This replaces the OpenAI-specific {@link OpenAiEndpointBlockSource} and supports every
 * provider in {@link com.qacopilot.generation.ai.AIProvider}: OpenAI, Anthropic, Azure OpenAI,
 * local LLMs, etc.</p>
 *
 * <p>The prompt format ({@link GherkinBlocksProtocol}) is kept provider-agnostic so that
 * switching providers does not change the output format.</p>
 *
 * <p>Batching: endpoints are sent in chunks of {@link #CHUNK} to stay well within context limits.
 * A configurable per-chunk fallback to the template generator is available for resilience.</p>
 */
public final class MultiModelEndpointBlockSource implements EndpointBlockSource {

    private static final Logger LOG = Logger.getLogger(MultiModelEndpointBlockSource.class.getName());
    private static final int CHUNK = 8;

    private final AIClient aiClient;
    private final boolean fallbackToTemplate;
    private final TemplateEndpointBlockSource templateFallback = new TemplateEndpointBlockSource();

    /**
     * @param aiClient          Fully-decorated AI client (retry + rate-limiting already applied).
     * @param fallbackToTemplate If {@code true}, chunks that fail AI generation fall back to
     *                           deterministic template scenarios instead of throwing.
     */
    public MultiModelEndpointBlockSource(AIClient aiClient, boolean fallbackToTemplate) {
        this.aiClient = aiClient;
        this.fallbackToTemplate = fallbackToTemplate;
    }

    @Override
    public String id() {
        return "ai-" + aiClient.provider().getId();
    }

    @Override
    public Map<String, String> blocksFor(List<EndpointContract> endpoints) throws Exception {
        if (!aiClient.isAvailable()) {
            if (fallbackToTemplate) {
                LOG.warning("[MultiModelEndpointBlockSource] " + aiClient.id()
                        + " is not available — using template fallback for all endpoints.");
                return templateFallback.blocksFor(endpoints);
            }
            throw new IllegalStateException(
                    "AI client '" + aiClient.id() + "' is not available. "
                            + "Check that the API key and provider are configured correctly, "
                            + "or set --ai-fallback-template to use deterministic scenarios.");
        }

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
        String userContent = GherkinBlocksProtocol.buildUserContent(chunk);

        try {
            AIResponse response = aiClient.generateResponse(systemPrompt, userContent);
            logTokenUsage(response);

            Map<String, String> got = GherkinBlocksProtocol.parseBlocksJson(response.content());
            for (var entry : got.entrySet()) {
                sink.put(entry.getKey(), AiGherkinSanitizer.sanitize(entry.getValue()));
            }
        } catch (Exception ex) {
            if (fallbackToTemplate) {
                LOG.warning("[MultiModelEndpointBlockSource] Chunk failed (" + ex.getMessage()
                        + ") — falling back to template for " + chunk.size() + " endpoint(s).");
                sink.putAll(templateFallback.blocksFor(chunk));
                return;
            }
            throw ex;
        }

        // Fill any endpoints the model forgot to cover
        List<EndpointContract> missing = new ArrayList<>();
        for (EndpointContract e : chunk) {
            String sid = EndpointFingerprints.stableId(e);
            String block = sink.get(sid);
            if (block == null || block.isBlank()) {
                missing.add(e);
            }
        }
        if (!missing.isEmpty()) {
            LOG.warning("[MultiModelEndpointBlockSource] AI omitted " + missing.size()
                    + " endpoint(s) — using template for them.");
            sink.putAll(templateFallback.blocksFor(missing));
        }
    }

    private void logTokenUsage(AIResponse response) {
        if (response.totalTokens() > 0) {
            LOG.fine(String.format("[%s] tokens: prompt=%d completion=%d total=%d",
                    aiClient.id(), response.promptTokens(), response.completionTokens(), response.totalTokens()));
        }
    }
}
