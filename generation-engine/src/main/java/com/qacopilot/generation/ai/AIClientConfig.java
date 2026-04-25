package com.qacopilot.generation.ai;

import com.qacopilot.generation.openai.DotEnvLoader;

import java.util.Map;
import java.util.Objects;

/**
 * Provider-agnostic configuration for any {@link AIClient}.
 *
 * <p>Environment variable resolution order for each field (first non-blank wins):
 * <pre>
 *   provider     : AI_PROVIDER         → "openai"
 *   apiKey       : AI_API_KEY          → OPENAI_API_KEY / ANTHROPIC_API_KEY (provider-specific)
 *   model        : AI_MODEL            → provider default
 *   baseUrl      : AI_BASE_URL         → provider default
 *   apiVersion   : AI_API_VERSION      → provider default (Azure only)
 *   maxTokens    : AI_MAX_TOKENS       → 16384
 *   temperature  : AI_TEMPERATURE      → 0.38
 *   timeoutSec   : AI_TIMEOUT_SECONDS  → 120
 *   maxRetries   : AI_MAX_RETRIES      → 3
 *   rateDelayMs  : AI_RATE_LIMIT_DELAY_MS → 350
 * </pre>
 * </p>
 */
public record AIClientConfig(
        AIProvider provider,
        String apiKey,
        String model,
        String baseUrl,
        String apiVersion,
        Map<String, String> extraHeaders,
        double temperature,
        int maxTokens,
        int timeoutSeconds,
        int maxRetries,
        long rateLimitDelayMs,
        boolean fallbackToTemplate) {

    public AIClientConfig {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? "" : baseUrl;
        apiVersion = apiVersion == null ? "" : apiVersion;
        extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
        if (maxTokens <= 0) maxTokens = 16_384;
        if (timeoutSeconds <= 0) timeoutSeconds = 120;
        if (maxRetries < 0) maxRetries = 3;
        if (rateLimitDelayMs < 0) rateLimitDelayMs = 350;
    }

    /** {@code true} if an API key is present (not required for LOCAL_LLM). */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Resolve from environment variables + .env file + CLI overrides.
     *
     * @param overrideApiKey    CLI / programmatic key override; may be blank.
     * @param overrideModel     CLI / programmatic model override; may be blank.
     * @param overrideProvider  CLI / programmatic provider override; may be blank.
     * @param fallback          Whether to fall back to template on AI failure.
     */
    public static AIClientConfig resolveFromEnv(
            String overrideApiKey,
            String overrideModel,
            String overrideProvider,
            boolean fallback) {

        AIProvider provider = resolveProvider(overrideProvider);

        String key = firstNonBlank(
                overrideApiKey,
                env("AI_API_KEY"),
                dotenv("AI_API_KEY"),
                providerSpecificKey(provider));

        String model = firstNonBlank(
                overrideModel,
                env("AI_MODEL"),
                dotenv("AI_MODEL"),
                defaultModel(provider));

        String baseUrl = firstNonBlank(env("AI_BASE_URL"), dotenv("AI_BASE_URL"), env("OPENAI_BASE_URL"));
        String apiVersion = firstNonBlank(env("AI_API_VERSION"), dotenv("AI_API_VERSION"));

        double temperature = parseDouble(firstNonBlank(env("AI_TEMPERATURE")), 0.38);
        int maxTokens = parseInt(firstNonBlank(env("AI_MAX_TOKENS")), 16_384);
        int timeout = parseInt(firstNonBlank(env("AI_TIMEOUT_SECONDS")), 120);
        int retries = parseInt(firstNonBlank(env("AI_MAX_RETRIES")), 3);
        long delay = parseLong(firstNonBlank(env("AI_RATE_LIMIT_DELAY_MS")), 350L);

        return new AIClientConfig(
                provider, key, model, baseUrl, apiVersion,
                Map.of(), temperature, maxTokens, timeout, retries, delay, fallback);
    }

    /**
     * Minimal factory for legacy OpenAI config (backward compat with existing CLI flags).
     */
    public static AIClientConfig forOpenAI(String apiKey, String model, boolean fallback) {
        String resolvedKey = firstNonBlank(
                apiKey,
                env("OPENAI_API_KEY"),
                dotenv("OPENAI_API_KEY"));
        String resolvedModel = firstNonBlank(model, env("OPENAI_MODEL"), dotenv("OPENAI_MODEL"), "gpt-4o-mini");
        return new AIClientConfig(
                AIProvider.OPENAI, resolvedKey, resolvedModel, "", "", Map.of(),
                0.38, 16_384, 120, 3, 350, fallback);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AIProvider resolveProvider(String override) {
        String v = firstNonBlank(override, env("AI_PROVIDER"), dotenv("AI_PROVIDER"));
        return v == null ? AIProvider.OPENAI : AIProvider.fromId(v);
    }

    private static String providerSpecificKey(AIProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> env("ANTHROPIC_API_KEY");
            case AZURE_OPENAI -> env("AZURE_OPENAI_API_KEY");
            default -> null;
        };
    }

    private static String defaultModel(AIProvider provider) {
        return switch (provider) {
            case OPENAI -> "gpt-4o-mini";
            case ANTHROPIC -> "claude-3-5-sonnet-20241022";
            case AZURE_OPENAI -> "gpt-4o";
            case LOCAL_LLM -> "llama3.2";
            case HUGGING_FACE -> "meta-llama/Meta-Llama-3-8B-Instruct";
        };
    }

    private static String env(String name) {
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) return v.strip();
        v = System.getProperty("qacopilot." + name.toLowerCase().replace('_', '.'));
        return (v != null && !v.isBlank()) ? v.strip() : null;
    }

    private static String dotenv(String name) {
        return DotEnvLoader.loadProperty(name).orElse(null);
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.strip();
        }
        return null;
    }

    private static double parseDouble(String s, double def) {
        try {
            return s != null ? Double.parseDouble(s) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return s != null ? Integer.parseInt(s) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseLong(String s, long def) {
        try {
            return s != null ? Long.parseLong(s) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
