package com.qacopilot.generation.ai;

import java.util.logging.Logger;

/**
 * Decorator that adds exponential-backoff retry logic to any {@link AIClient}.
 *
 * <p>Retry rules:
 * <ul>
 *   <li>Auth errors (401, 403) are <em>never</em> retried — they require config changes.</li>
 *   <li>Rate limit errors (429) use a longer minimum delay of 60 seconds.</li>
 *   <li>All other errors use exponential backoff: {@code 1s, 2s, 4s, 8s, …} capped at 30s.</li>
 * </ul>
 * </p>
 */
public final class RetryingAIClient implements AIClient {

    private static final Logger LOG = Logger.getLogger(RetryingAIClient.class.getName());

    private static final long BASE_DELAY_MS = 1_000L;
    private static final long MAX_DELAY_MS = 30_000L;
    private static final long RATE_LIMIT_MIN_DELAY_MS = 60_000L;

    private final AIClient delegate;
    private final int maxRetries;

    /**
     * @param delegate   The inner client to wrap.
     * @param maxRetries Maximum number of additional attempts (0 = no retry, 1 = one retry, etc.).
     */
    public RetryingAIClient(AIClient delegate, int maxRetries) {
        this.delegate = delegate;
        this.maxRetries = Math.max(0, maxRetries);
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public AIProvider provider() {
        return delegate.provider();
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException {
        AIClientException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return delegate.generateResponse(systemPrompt, userContent);
            } catch (AIClientException e) {
                lastException = e;

                if (e.isAuthError()) {
                    throw e; // never retryable
                }

                if (attempt < maxRetries) {
                    long backoff = Math.min(BASE_DELAY_MS * (1L << attempt), MAX_DELAY_MS);
                    long delay = e.isRateLimitError() ? Math.max(backoff, RATE_LIMIT_MIN_DELAY_MS) : backoff;

                    LOG.warning(String.format(
                            "[RetryingAIClient:%s] Attempt %d/%d failed — %s. Retrying in %d ms...",
                            id(), attempt + 1, maxRetries + 1, e.getMessage(), delay));

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }

        throw lastException;
    }
}
