package com.qacopilot.generation.ai;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decorator that enforces a minimum delay between consecutive AI calls.
 *
 * <p>Calls are serialised (one at a time) using a semaphore so that batch processing
 * from multiple threads does not violate the provider's rate limits.
 * The {@code delayBetweenCallsMs} is measured from when the <em>previous</em> call returned,
 * not when it started — this is a "cooldown" model, not a fixed request-per-second window.</p>
 */
public final class RateLimitedAIClient implements AIClient {

    private final AIClient delegate;
    private final long delayBetweenCallsMs;
    private final AtomicLong lastCallCompletedAt = new AtomicLong(0);
    /** Ensures only one in-flight call at a time across threads. */
    private final Semaphore serialise = new Semaphore(1);

    public RateLimitedAIClient(AIClient delegate, long delayBetweenCallsMs) {
        this.delegate = delegate;
        this.delayBetweenCallsMs = Math.max(0, delayBetweenCallsMs);
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
        try {
            serialise.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIClientException("Rate limiter interrupted while waiting for permit", e);
        }

        try {
            enforceDelay();
            AIResponse response = delegate.generateResponse(systemPrompt, userContent);
            lastCallCompletedAt.set(System.currentTimeMillis());
            return response;
        } finally {
            serialise.release();
        }
    }

    private void enforceDelay() {
        long last = lastCallCompletedAt.get();
        if (last == 0 || delayBetweenCallsMs == 0) return;
        long elapsed = System.currentTimeMillis() - last;
        long remaining = delayBetweenCallsMs - elapsed;
        if (remaining > 0) {
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
