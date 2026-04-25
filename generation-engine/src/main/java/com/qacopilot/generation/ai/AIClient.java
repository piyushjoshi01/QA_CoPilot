package com.qacopilot.generation.ai;

/**
 * Unified interface for all AI model providers.
 *
 * <p>Implementations must be thread-safe. The recommended composition order is:
 * <pre>
 *   AIResponseCache
 *     └── RateLimitedAIClient
 *           └── RetryingAIClient
 *                 └── ProviderClient (OpenAIClient / AnthropicClient / ...)
 * </pre>
 * Use {@link AIClientFactory} to get a correctly-decorated client.</p>
 */
public interface AIClient {

    /** Stable identifier for this client instance (used in cache keys and logs). */
    String id();

    /** Provider this client connects to. */
    AIProvider provider();

    /**
     * Send a prompt to the AI model and return the normalized response.
     *
     * @param systemPrompt  System-level instructions that define model behaviour.
     * @param userContent   The actual task / user message.
     * @return              Normalized {@link AIResponse}.
     * @throws AIClientException on API errors, auth failures, rate limits, or timeouts.
     */
    AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException;

    /** Returns {@code true} if this client is configured and ready to make calls. */
    boolean isAvailable();
}
