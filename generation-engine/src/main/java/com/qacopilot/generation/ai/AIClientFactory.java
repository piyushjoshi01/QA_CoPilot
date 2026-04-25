package com.qacopilot.generation.ai;

import com.qacopilot.generation.ai.cache.AIResponseCache;
import com.qacopilot.generation.ai.impl.AnthropicClient;
import com.qacopilot.generation.ai.impl.AzureOpenAIClient;
import com.qacopilot.generation.ai.impl.LocalLLMClient;
import com.qacopilot.generation.ai.impl.OpenAIClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating fully-decorated {@link AIClient} instances.
 *
 * <p><strong>Decoration order</strong> (inner → outer):
 * <ol>
 *   <li>Provider implementation ({@code OpenAIClient}, {@code AnthropicClient}, …)</li>
 *   <li>{@link RetryingAIClient} — exponential backoff on transient failures</li>
 *   <li>{@link RateLimitedAIClient} — minimum delay between consecutive calls</li>
 *   <li>{@link AIResponseCache} — disk cache (only if {@code cacheDir} is non-null)</li>
 * </ol>
 *
 * <p>Example — single provider with caching:
 * <pre>{@code
 *   AIClientConfig config = AIClientConfig.resolveFromEnv("", "", "anthropic", true);
 *   AIClient client = AIClientFactory.build(config, qacopilotDir.resolve("ai-response-cache"));
 * }</pre>
 *
 * <p>Example — fallback chain:
 * <pre>{@code
 *   AIClient client = AIClientFactory.buildWithFallback(
 *       primaryConfig,
 *       List.of(secondaryConfig, tertiaryConfig),
 *       cacheDir);
 * }</pre>
 */
public final class AIClientFactory {

    private AIClientFactory() {
    }

    // ── Public factory methods ────────────────────────────────────────────────

    /**
     * Build a single, fully-decorated AI client.
     *
     * @param config   Provider configuration.
     * @param cacheDir Directory for the disk response cache; {@code null} disables caching.
     */
    public static AIClient build(AIClientConfig config, Path cacheDir) {
        AIClient core = buildCore(config);
        AIClient retrying = new RetryingAIClient(core, config.maxRetries());
        AIClient rateLimited = new RateLimitedAIClient(retrying, config.rateLimitDelayMs());
        if (cacheDir != null) {
            return new AIResponseCache(rateLimited, cacheDir);
        }
        return rateLimited;
    }

    /**
     * Build a {@link FallbackAIClient} chain: primary is tried first, then each secondary in order.
     * Each client in the chain is independently decorated with retry + rate-limiting.
     *
     * @param primary     Primary provider configuration.
     * @param secondaries Additional providers to try if the primary fails; may be empty.
     * @param cacheDir    Shared disk cache directory; {@code null} disables caching.
     */
    public static AIClient buildWithFallback(
            AIClientConfig primary,
            List<AIClientConfig> secondaries,
            Path cacheDir) {

        AIClient primaryClient = build(primary, null); // don't double-cache chain members
        if (secondaries == null || secondaries.isEmpty()) {
            if (cacheDir != null) {
                return new AIResponseCache(primaryClient, cacheDir);
            }
            return primaryClient;
        }

        List<AIClient> chain = new ArrayList<>();
        chain.add(primaryClient);
        for (AIClientConfig sec : secondaries) {
            chain.add(build(sec, null));
        }

        AIClient fallback = new FallbackAIClient(chain);
        if (cacheDir != null) {
            return new AIResponseCache(fallback, cacheDir);
        }
        return fallback;
    }

    // ── Core provider wiring ──────────────────────────────────────────────────

    private static AIClient buildCore(AIClientConfig config) {
        return switch (config.provider()) {
            case OPENAI -> new OpenAIClient(config);
            case ANTHROPIC -> new AnthropicClient(config);
            case AZURE_OPENAI -> new AzureOpenAIClient(config);
            case LOCAL_LLM -> new LocalLLMClient(config);
            case HUGGING_FACE ->
                    throw new UnsupportedOperationException(
                            "HuggingFace Inference Endpoints with OpenAI-compatible mode: set AI_PROVIDER=local-llm "
                                    + "and AI_BASE_URL=https://api-inference.huggingface.co/models/<model>/v1");
        };
    }
}
