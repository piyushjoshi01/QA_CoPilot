package com.qacopilot.generation.ai;

/**
 * Checked exception thrown by {@link AIClient} implementations.
 *
 * <p>Callers can inspect {@link #getHttpStatusCode()} and the convenience predicates
 * ({@link #isRateLimitError()}, {@link #isAuthError()}, {@link #isServerError()}) to decide
 * whether to retry, fall back to another provider, or surface the error to the user.</p>
 */
public class AIClientException extends Exception {

    private final int httpStatusCode;

    public AIClientException(String message) {
        super(message);
        this.httpStatusCode = -1;
    }

    public AIClientException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = -1;
    }

    public AIClientException(String message, int httpStatusCode) {
        super(message);
        this.httpStatusCode = httpStatusCode;
    }

    public AIClientException(String message, int httpStatusCode, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
    }

    /** HTTP status from the provider API, or {@code -1} if not applicable. */
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /** {@code true} if the provider returned 429 (too many requests). */
    public boolean isRateLimitError() {
        return httpStatusCode == 429;
    }

    /** {@code true} if the provider returned 401 or 403 (bad/missing credentials). */
    public boolean isAuthError() {
        return httpStatusCode == 401 || httpStatusCode == 403;
    }

    /** {@code true} if the provider returned 5xx (server-side failure). */
    public boolean isServerError() {
        return httpStatusCode >= 500;
    }
}
