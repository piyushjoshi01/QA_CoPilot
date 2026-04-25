package com.qacopilot.generation.openai;

import java.util.Objects;

public record OpenAiConfig(String apiKey, String model, boolean fallbackToTemplateOnError) {

    /**
     * <p><b>Local testing only.</b> Paste your {@code sk-...} key between the quotes to run {@code sync}
     * without {@code .env} or env vars. Set back to {@code ""} before any {@code git commit} or push —
     * never commit a real key.</p>
     */
    private static final String LOCAL_TEST_OPENAI_API_KEY = "";

    public OpenAiConfig {
        Objects.requireNonNull(model, "model");
    }

    public static OpenAiConfig resolve(String cliApiKey, String cliModel, boolean fallback) {
        String key = firstNonBlank(
                cliApiKey,
                LOCAL_TEST_OPENAI_API_KEY,
                System.getenv("OPENAI_API_KEY"),
                System.getProperty("qacopilot.openai.apiKey"));
        if (key == null || key.isBlank()) {
            key = DotEnvLoader.loadProperty("OPENAI_API_KEY").orElse(null);
        }
        String model = firstNonBlank(cliModel, System.getenv("OPENAI_MODEL"));
        if (model == null || model.isBlank()) {
            model = DotEnvLoader.loadProperty("OPENAI_MODEL").orElse("gpt-4o-mini");
        }
        return new OpenAiConfig(key, model, fallback);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return null;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
