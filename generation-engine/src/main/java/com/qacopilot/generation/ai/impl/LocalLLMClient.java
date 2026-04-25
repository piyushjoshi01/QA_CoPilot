package com.qacopilot.generation.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qacopilot.generation.ai.AIClient;
import com.qacopilot.generation.ai.AIClientConfig;
import com.qacopilot.generation.ai.AIClientException;
import com.qacopilot.generation.ai.AIProvider;
import com.qacopilot.generation.ai.AIResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Client for OpenAI-compatible local LLM servers: Ollama, vLLM, LM Studio, llama.cpp, etc.
 *
 * <p>Default base URL: {@code http://localhost:11434/v1} (Ollama's OpenAI-compat endpoint).
 * Override with {@code AI_BASE_URL}.
 *
 * <p>Notes:
 * <ul>
 *   <li>No API key required by default (pass one if your server uses it).</li>
 *   <li>{@code response_format: json_object} is only sent for models that support it;
 *       for llama / mistral the model is instructed via the system prompt to output JSON.</li>
 *   <li>Very long generation timeouts (Ollama can be slow on CPU) — set {@code AI_TIMEOUT_SECONDS}
 *       to 300+ for large models.</li>
 * </ul>
 * </p>
 */
public final class LocalLLMClient implements AIClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE = "http://localhost:11434/v1";

    /** Models that natively support json_object response format. */
    private static final Set<String> JSON_FORMAT_CAPABLE = Set.of(
            "mistral-nemo", "mixtral", "qwen2", "qwen2.5", "phi3", "phi3.5",
            "deepseek-r1", "deepseek-v2");

    private final AIClientConfig config;
    private final HttpClient http;
    private final URI chatUri;

    public LocalLLMClient(AIClientConfig config) {
        this.config = config;
        // Use a short connect timeout for local servers; the transfer timeout governs generation time
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String base = config.baseUrl().isBlank() ? DEFAULT_BASE : stripTrailingSlash(config.baseUrl());
        this.chatUri = URI.create(base + "/chat/completions");
    }

    @Override
    public String id() {
        return "local-llm:" + config.model();
    }

    @Override
    public AIProvider provider() {
        return AIProvider.LOCAL_LLM;
    }

    @Override
    public boolean isAvailable() {
        return true; // local LLMs don't require an API key
    }

    @Override
    public AIResponse generateResponse(String systemPrompt, String userContent) throws AIClientException {
        ObjectNode payload = buildPayload(systemPrompt, userContent);

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder(chatUri)
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json");
            if (config.hasApiKey()) {
                rb.header("Authorization", "Bearer " + config.apiKey());
            }
            config.extraHeaders().forEach(rb::header);

            HttpRequest req = rb.POST(
                    HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new AIClientException(
                        "LocalLLM returned HTTP " + res.statusCode() + ": " + truncate(res.body(), 400),
                        res.statusCode());
            }

            JsonNode root = MAPPER.readTree(res.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content.isEmpty()) {
                throw new AIClientException("LocalLLM returned empty content: " + truncate(res.body(), 400));
            }

            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            return new AIResponse(content, config.model(), promptTokens, completionTokens, Map.of());

        } catch (AIClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AIClientException(
                    "LocalLLM request failed (is the server running at " + chatUri + "?): " + e.getMessage(), e);
        }
    }

    private ObjectNode buildPayload(String systemPrompt, String userContent) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("model", config.model());
        payload.put("temperature", config.temperature());
        payload.put("max_tokens", config.maxTokens());
        payload.put("stream", false);

        // Only send response_format for models known to support it
        boolean supportsJsonFormat = JSON_FORMAT_CAPABLE.stream()
                .anyMatch(m -> config.model().toLowerCase().contains(m));
        if (supportsJsonFormat) {
            payload.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
        }

        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(MAPPER.createObjectNode().put("role", "system").put("content", systemPrompt));
        messages.add(MAPPER.createObjectNode().put("role", "user").put("content", userContent));
        payload.set("messages", messages);
        return payload;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
