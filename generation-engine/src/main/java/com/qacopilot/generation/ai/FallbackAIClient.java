package com.qacopilot.generation.ai;

import java.util.List;
import java.util.logging.Logger;

/**
 * Tries a chain of {@link AIClient}s in order, returning the first successful response.
 *
 * <p>Use this to define primary → secondary → tertiary provider fallbacks, e.g.:</p>
 * <pre>
 *   new FallbackAIClient(List.of(anthropicClient, openAiClient, localLlmClient))
 * </pre>
 * <p>Auth errors from the primary are still propagated immediately (they won't be fixed
 * by switching provider).  All other errors cause the next provider in the chain to be tried.</p>
 */
public final class FallbackAIClient implements AIClient {

    private static final Logger LOG = Logger.getLogger(FallbackAIClient.class.getName());

    private final List<AIClient> chain;
    private final String compositeId;

    public FallbackAIClient(List<AIClient> chain) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("Fallback chain must have at least one client");
        }
        this.chain = List.copyOf(chain);
        this.compositeId = "fallback[" + chain.stream()
                .map(AIClient::id)
                .reduce((a, b) -> a + "→" + b)
                .orElse("") + "]";
    }

    @Override
    public String id() {
        return compositeId;
    }

    @Override
    public AIProvider provider() {
        return chain.get(0).provider();
    }

    @Override
    public boolean isAvailable() {
        return chain.stream().anyMatch(AIClient::isAvailable);
    }

    @Override
    public AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException {
        AIClientException lastException = null;

        for (int i = 0; i < chain.size(); i++) {
            AIClient client = chain.get(i);
            if (!client.isAvailable()) {
                LOG.fine(String.format("[FallbackAIClient] Skipping unavailable provider: %s", client.id()));
                continue;
            }
            try {
                AIResponse response = client.generateResponse(systemPrompt, userContent);
                if (i > 0) {
                    LOG.info(String.format("[FallbackAIClient] Succeeded with fallback provider #%d: %s", i + 1, client.id()));
                }
                return response;
            } catch (AIClientException e) {
                LOG.warning(String.format(
                        "[FallbackAIClient] Provider %s failed (HTTP %d): %s",
                        client.id(), e.getHttpStatusCode(), e.getMessage()));
                lastException = e;
                // Auth errors from a configured provider should be surfaced
                if (e.isAuthError() && i == 0) {
                    throw e;
                }
            }
        }

        if (lastException != null) throw lastException;
        throw new AIClientException("All providers in the fallback chain are unavailable");
    }
}
