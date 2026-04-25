package com.qacopilot.generation.ai;

/**
 * Supported AI model providers.
 *
 * <p>Pass the {@link #getId()} string as the value of the {@code AI_PROVIDER} environment variable
 * or the {@code --ai-provider} CLI flag.</p>
 *
 * <pre>
 * Provider         CLI / env id      Notes
 * ───────────────  ────────────────  ──────────────────────────────────────────
 * OPENAI           openai            api.openai.com — chat completions
 * ANTHROPIC        anthropic         api.anthropic.com — Messages API
 * AZURE_OPENAI     azure-openai      Requires baseUrl + apiVersion
 * LOCAL_LLM        local-llm         Ollama / vLLM / LM Studio (OpenAI-compat)
 * HUGGING_FACE     hugging-face      Inference Endpoints (OpenAI-compat mode)
 * </pre>
 */
public enum AIProvider {

    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    AZURE_OPENAI("azure-openai"),
    LOCAL_LLM("local-llm"),
    HUGGING_FACE("hugging-face");

    private final String id;

    AIProvider(String id) {
        this.id = id;
    }

    /** The stable, lowercase string identifier used in config / CLI. */
    public String getId() {
        return id;
    }

    /**
     * Resolve a provider from its string id (case-insensitive).
     *
     * @throws IllegalArgumentException for unknown ids.
     */
    public static AIProvider fromId(String id) {
        if (id == null || id.isBlank()) return OPENAI;
        for (AIProvider p : values()) {
            if (p.id.equalsIgnoreCase(id.strip())) return p;
        }
        throw new IllegalArgumentException(
                "Unknown AI provider: '" + id + "'. Valid values: openai, anthropic, azure-openai, local-llm, hugging-face");
    }
}
